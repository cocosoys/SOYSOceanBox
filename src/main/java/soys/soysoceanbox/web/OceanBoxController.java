package soys.soysoceanbox.web;

import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiName;
import com.github.cocosoys.mc.soyshttpovermc.annotations.ApiPublic;
import com.github.cocosoys.mc.soyshttpovermc.annotations.GetMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PathVariable;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PostMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.PutMapping;
import com.github.cocosoys.mc.soyshttpovermc.annotations.RequestBody;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.soyshttpovermc.util.JsonReader;
import com.github.cocosoys.mc.soyshttpovermc.web.ApiRequestContext;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.lottery.PendingReward;
import soys.soysoceanbox.lottery.RewardDef;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SOYSOceanBox 业务端点（接入 SOYSHTTPOverMC）。
 *
 * <p>统一挂 {@code /api/plugins/soysoceanbox} 前缀。领域映射：ERP「礼包」= 抽奖奖池 Pool；
 * 「礼品配置」= 奖池奖项 RewardDef 集合；「领取」= 代玩家抽奖（另提供直接发放）；
 * 「重置领取」= 重置保底 / 日周计数 / 待领取；「领取记录」= 中奖记录；「启用/禁用」= 奖池开关。</p>
 *
 * <b>用户侧（凭证玩家，Hypixel 风格页面）</b>
 * <ul>
 *   <li>{@code GET  /view/pools}            展示用奖池（仅启用且在生效窗口）</li>
 *   <li>{@code GET  /player/state}          当前玩家待领取 / 计数 / 保底 / 冷却</li>
 *   <li>{@code POST /player/draw/{pool}}    玩家抽奖（body: times 次数）</li>
 *   <li>{@code POST /player/claim}          领取待领取奖励（body: target，默认 all）</li>
 * </ul>
 *
 * <b>管理侧（仅在线 OP，ERP 后台）</b>
 * <ul>
 *   <li>{@code GET  /admin/overview}                       概览统计</li>
 *   <li>{@code GET  /admin/pools}                          奖池列表</li>
 *   <li>{@code GET  /admin/pools/{pool}}                   奖池完整配置（含奖项）</li>
 *   <li>{@code PUT  /admin/pools/{pool}}                   保存奖池与奖项（礼品配置）</li>
 *   <li>{@code GET|PUT /admin/pools/{pool}/config}         奖池规则（启停 / 限时窗口）</li>
 *   <li>{@code POST /admin/pools/{pool}/toggle}            启用 / 禁用</li>
 *   <li>{@code POST /admin/pools/{pool}/draw}              代在线玩家抽奖</li>
 *   <li>{@code POST /admin/pools/{pool}/give}              直接发放奖项（绕过待领取）</li>
 *   <li>{@code POST /admin/pools/{pool}/reset}             重置玩家数据</li>
 *   <li>{@code GET  /admin/pools/{pool}/records}           该奖池中奖记录</li>
 *   <li>{@code GET|PUT /admin/settings}                    全局抽奖规则设置</li>
 *   <li>{@code GET  /admin/logs}                           中奖汇总与服务器日志</li>
 *   <li>{@code GET  /admin/inventory}                      当前管理员背包（拷贝物品）</li>
 *   <li>{@code GET  /admin/players}                        在线玩家列表</li>
 * </ul>
 */
public class OceanBoxController {

    private final SOYSOceanBox plugin;

    public OceanBoxController(SOYSOceanBox plugin) {
        this.plugin = plugin;
    }

    // ================================================================
    //  用户侧
    // ================================================================

    @ApiName("展示奖池")
    @ApiPublic
    @GetMapping("/view/pools")
    public AjaxResult viewPools() {
        List<Map<String, Object>> pools = new ArrayList<>();
        for (String name : plugin.getLotteryConfig().getActivePoolNames()) {
            pools.add(poolView(name));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("defaultPool", plugin.getLotteryConfig().getDefaultPoolName());
        data.put("costEnabled", plugin.getLotteryConfig().isCostEnabled());
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("type", plugin.getLotteryConfig().getCostType());
        cost.put("amount", plugin.getLotteryConfig().getCostAmount());
        data.put("cost", cost);
        data.put("pools", pools);
        return AjaxResult.success(data);
    }

    @ApiName("玩家抽奖状态")
    @ApiPublic
    @GetMapping("/player/state")
    public AjaxResult playerState(ApiRequestContext ctx) {
        Map<String, Object> data = new LinkedHashMap<>();
        String name = ctx.getPlayerName();
        data.put("name", name);
        Player online = name == null ? null : ctx.getSyncPlayer();
        if (online != null) {
            LotteryPlayer lp = plugin.getLotteryManager().getOrLoad(online);
            data.putAll(playerStateData(online, lp));
        }
        return AjaxResult.success(data);
    }

    @ApiName("玩家抽奖")
    @ApiPublic
    @PostMapping("/player/draw/{pool}")
    public AjaxResult playerDraw(ApiRequestContext ctx,
                                 @PathVariable(name = "pool") String pool,
                                 @RequestBody String body) {
        String name = ctx.getPlayerName();
        if (name == null || name.isEmpty()) {
            return AjaxResult.unauthorized("请先登录后再抽奖");
        }
        Player player = ctx.getSyncPlayer();
        if (player == null) {
            return AjaxResult.forbidden("请进入服务器后再抽奖（当前角色不在线）");
        }
        Map<String, Object> payload = parse(body);
        int times = Math.max(1, Math.min(10, toInt(payload.get("times"), 1)));

        List<Map<String, Object>> results = new ArrayList<>();
        try {
            Sync.run(plugin, () -> {
                if (pool != null && !pool.isEmpty()) {
                    String sw = plugin.getLotteryManager().switchPool(player, pool, false);
                    if (sw != null) {
                        results.add(simpleResult(false, sw));
                        return null;
                    }
                }
                for (int i = 0; i < times; i++) {
                    LotteryManagerHack.drawOnce(plugin, player, results);
                    Map<String, Object> last = results.get(results.size() - 1);
                    if (!Boolean.TRUE.equals(last.get("ok"))) {
                        break;
                    }
                }
                return null;
            });
        } catch (Exception e) {
            return AjaxResult.error("抽奖异常: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("results", results);
        return AjaxResult.success(data);
    }

    @ApiName("玩家领取奖励")
    @ApiPublic
    @PostMapping("/player/claim")
    public AjaxResult playerClaim(ApiRequestContext ctx, @RequestBody String body) {
        String name = ctx.getPlayerName();
        if (name == null || name.isEmpty()) {
            return AjaxResult.unauthorized("请先登录后再领取");
        }
        Player player = ctx.getSyncPlayer();
        if (player == null) {
            return AjaxResult.forbidden("请进入服务器后再领取（当前角色不在线）");
        }
        Map<String, Object> payload = parse(body);
        String target = str(payload.get("target"));
        if (target == null || target.isEmpty()) {
            target = "all";
        }
        final String fTarget = target;
        ClaimResultData data;
        try {
            data = Sync.run(plugin, () -> {
                soys.soysoceanbox.lottery.LotteryManager.ClaimResult cr =
                        plugin.getLotteryManager().claim(player, fTarget);
                return new ClaimResultData(cr);
            });
        } catch (Exception e) {
            return AjaxResult.error("领取异常: " + e.getMessage());
        }
        String msg = "已领取 " + data.claimed + " 项"
                + (data.partial > 0 ? "，" + data.partial + " 项因背包不足部分发放" : "")
                + (data.failed > 0 ? "，失败 " + data.failed + " 项" : "");
        return AjaxResult.success(msg, data.toMap());
    }

    // ================================================================
    //  管理侧：概览 / 列表
    // ================================================================

    @ApiName("概览统计")
    @ApiPublic
    @GetMapping("/admin/overview")
    public AjaxResult overview(ApiRequestContext ctx) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        int totalDraws = 0;
        int totalPending = 0;
        for (LotteryPlayer lp : plugin.getStorageManager().getAllPlayersSafe()) {
            totalDraws += lp.getTotalDraws();
            totalPending += lp.getPending().size();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
        data.put("recordedPlayers", plugin.getStorageManager().countPlayers());
        data.put("poolsCount", plugin.getLotteryConfig().getPoolNames().size());
        data.put("activePools", plugin.getLotteryConfig().getActivePoolNames().size());
        data.put("totalDraws", totalDraws);
        data.put("totalPending", totalPending);
        data.put("defaultPool", plugin.getLotteryConfig().getDefaultPoolName());
        data.put("primaryStorage",
                plugin.getStorageManager().getPrimary().getType().getDisplayName());
        return AjaxResult.success(data);
    }

    @ApiName("奖池列表")
    @ApiPublic
    @GetMapping("/admin/pools")
    public AjaxResult listPools(ApiRequestContext ctx) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : plugin.getLotteryConfig().getPoolNames()) {
            ConfigurationSection block = plugin.getLotteryConfig().raw()
                    .getConfigurationSection("pools." + name);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("enabled", plugin.getLotteryConfig().isPoolEnabled(name));
            row.put("status", plugin.getLotteryConfig().getPoolStatus(name));
            row.put("start", block == null ? "" : block.getString("start", ""));
            row.put("end", block == null ? "" : block.getString("end", ""));
            row.put("rewardsCount", plugin.getLotteryConfig().getRewardPool(name).size());
            out.add(row);
        }
        return AjaxResult.success(out);
    }

    // ================================================================
    //  管理侧：奖池详情 / 保存 / 规则 / 启停
    // ================================================================

    @ApiName("奖池完整配置")
    @ApiPublic
    @GetMapping("/admin/pools/{pool}")
    public AjaxResult getPool(ApiRequestContext ctx,
                              @PathVariable(name = "pool") String pool) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        if (!poolExists(pool)) {
            return AjaxResult.notFound("奖池不存在: " + pool);
        }
        return AjaxResult.success(poolDetail(pool));
    }

    @ApiName("保存奖池与奖项")
    @ApiPublic
    @PutMapping("/admin/pools/{pool}")
    public AjaxResult savePool(ApiRequestContext ctx,
                               @PathVariable(name = "pool") String pool,
                               @RequestBody String body) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        if (!poolExists(pool)) {
            return AjaxResult.notFound("奖池不存在: " + pool);
        }
        Map<String, Object> payload = parse(body);
        try {
            YamlConfiguration y = lotteryYaml();
            String base = "pools." + pool;
            if (payload.containsKey("rewards")) {
                Object rewards = payload.get("rewards");
                y.set(base + ".rewards",
                        rewards instanceof List ? rewards : new ArrayList<>());
            }
            if (payload.containsKey("enabled")) {
                y.set(base + ".enabled", payload.get("enabled"));
            }
            if (payload.containsKey("start")) {
                y.set(base + ".start", str(payload.get("start")));
            }
            if (payload.containsKey("end")) {
                y.set(base + ".end", str(payload.get("end")));
            }
            saveLottery(y);
            return AjaxResult.success("已保存奖池 " + pool);
        } catch (Exception e) {
            return AjaxResult.error("保存失败: " + e.getMessage());
        }
    }

    @ApiName("读取奖池规则")
    @ApiPublic
    @GetMapping("/admin/pools/{pool}/config")
    public AjaxResult getPoolConfig(ApiRequestContext ctx,
                                    @PathVariable(name = "pool") String pool) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        if (!poolExists(pool)) {
            return AjaxResult.notFound("奖池不存在: " + pool);
        }
        ConfigurationSection block = plugin.getLotteryConfig().raw()
                .getConfigurationSection("pools." + pool);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", pool);
        data.put("enabled", plugin.getLotteryConfig().isPoolEnabled(pool));
        data.put("start", block == null ? "" : block.getString("start", ""));
        data.put("end", block == null ? "" : block.getString("end", ""));
        data.put("status", plugin.getLotteryConfig().getPoolStatus(pool));
        return AjaxResult.success(data);
    }

    @ApiName("保存奖池规则")
    @ApiPublic
    @PutMapping("/admin/pools/{pool}/config")
    public AjaxResult savePoolConfig(ApiRequestContext ctx,
                                     @PathVariable(name = "pool") String pool,
                                     @RequestBody String body) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        if (!poolExists(pool)) {
            return AjaxResult.notFound("奖池不存在: " + pool);
        }
        Map<String, Object> payload = parse(body);
        try {
            YamlConfiguration y = lotteryYaml();
            String base = "pools." + pool;
            if (payload.containsKey("enabled")) {
                y.set(base + ".enabled", payload.get("enabled"));
            }
            if (payload.containsKey("start")) {
                y.set(base + ".start", str(payload.get("start")));
            }
            if (payload.containsKey("end")) {
                y.set(base + ".end", str(payload.get("end")));
            }
            saveLottery(y);
            return AjaxResult.success("已保存奖池 " + pool + " 规则");
        } catch (Exception e) {
            return AjaxResult.error("保存规则失败: " + e.getMessage());
        }
    }

    @ApiName("启用/禁用奖池")
    @ApiPublic
    @PostMapping("/admin/pools/{pool}/toggle")
    public AjaxResult togglePool(ApiRequestContext ctx,
                                 @PathVariable(name = "pool") String pool) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        if (!poolExists(pool)) {
            return AjaxResult.notFound("奖池不存在: " + pool);
        }
        boolean now = !plugin.getLotteryConfig().isPoolEnabled(pool);
        try {
            YamlConfiguration y = lotteryYaml();
            y.set("pools." + pool + ".enabled", now);
            saveLottery(y);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("enabled", now);
            return AjaxResult.success(
                    now ? "已启用奖池 " + pool : "已禁用奖池 " + pool, data);
        } catch (Exception e) {
            return AjaxResult.error("切换失败: " + e.getMessage());
        }
    }

    // ================================================================
    //  管理侧：代抽 / 直接发放 / 重置 / 记录
    // ================================================================

    @ApiName("代玩家抽奖")
    @ApiPublic
    @PostMapping("/admin/pools/{pool}/draw")
    public AjaxResult drawFor(ApiRequestContext ctx,
                              @PathVariable(name = "pool") String pool,
                              @RequestBody String body) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        if (!poolExists(pool)) {
            return AjaxResult.notFound("奖池不存在: " + pool);
        }
        Map<String, Object> payload = parse(body);
        String target = str(payload.get("player"));
        if (target == null || target.isEmpty()) {
            return AjaxResult.error("请指定玩家名");
        }
        int times = Math.max(1, Math.min(10, toInt(payload.get("times"), 1)));
        final int fTimes = times;
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            Sync.run(plugin, () -> {
                Player t = Bukkit.getPlayerExact(target);
                if (t == null) {
                    results.add(simpleResult(false, "玩家不在线，无法代抽: " + target));
                    return null;
                }
                plugin.getLotteryManager().switchPool(t, pool, true);
                for (int i = 0; i < fTimes; i++) {
                    LotteryManagerHack.drawOnce(plugin, t, results);
                    if (!Boolean.TRUE.equals(results.get(results.size() - 1).get("ok"))) {
                        break;
                    }
                }
                return null;
            });
        } catch (Exception e) {
            return AjaxResult.error("代抽异常: " + e.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("player", target);
        data.put("results", results);
        return AjaxResult.success("已为 " + target + " 抽奖", data);
    }

    @ApiName("直接发放奖项")
    @ApiPublic
    @PostMapping("/admin/pools/{pool}/give")
    public AjaxResult giveFor(ApiRequestContext ctx,
                              @PathVariable(name = "pool") String pool,
                              @RequestBody String body) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        if (!poolExists(pool)) {
            return AjaxResult.notFound("奖池不存在: " + pool);
        }
        Map<String, Object> payload = parse(body);
        String target = str(payload.get("player"));
        if (target == null || target.isEmpty()) {
            return AjaxResult.error("请指定玩家名");
        }
        String rewardId = str(payload.get("rewardId"));
        Object rewardRaw = payload.get("reward");

        final RewardDef[] defHolder = new RewardDef[1];
        if (rewardId != null && !rewardId.isEmpty()) {
            defHolder[0] = findReward(pool, rewardId);
            if (defHolder[0] == null) {
                return AjaxResult.error("奖池 " + pool + " 中不存在奖项: " + rewardId);
            }
        } else if (rewardRaw instanceof Map) {
            defHolder[0] = buildRewardFromMap((Map<?, ?>) rewardRaw);
            if (defHolder[0] == null) {
                return AjaxResult.error("物品信息无效，无法发放");
            }
        } else {
            return AjaxResult.error("请指定 rewardId 或 reward 物品信息");
        }

        final String fTarget = target;
        String error;
        try {
            error = Sync.run(plugin, () -> {
                Player t = Bukkit.getPlayerExact(fTarget);
                if (t == null) {
                    return "玩家不在线，无法发放: " + fTarget;
                }
                return plugin.getLotteryManager().giveDirect(t, defHolder[0]);
            });
        } catch (Exception e) {
            return AjaxResult.error("发放异常: " + e.getMessage());
        }
        if (error != null) {
            return AjaxResult.error(error);
        }
        return AjaxResult.success("已向 " + target + " 直接发放: " + defHolder[0].getDisplay());
    }

    @ApiName("重置玩家数据")
    @ApiPublic
    @PostMapping("/admin/pools/{pool}/reset")
    public AjaxResult resetFor(ApiRequestContext ctx,
                               @PathVariable(name = "pool") String pool,
                               @RequestBody String body) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        if (!poolExists(pool)) {
            return AjaxResult.notFound("奖池不存在: " + pool);
        }
        Map<String, Object> payload = parse(body);
        String target = str(payload.get("player"));
        if (target == null || target.isEmpty()) {
            return AjaxResult.error("请指定玩家名");
        }
        List<String> scopes = toStringList(payload.get("scopes"));
        if (scopes.isEmpty()) {
            scopes = Collections.singletonList("all");
        }
        final String fTarget = target;
        final List<String> fScopes = scopes;
        List<String> done;
        try {
            done = Sync.run(plugin, () -> {
                Player t = Bukkit.getPlayerExact(fTarget);
                if (t == null) {
                    return null;
                }
                return plugin.getLotteryManager().resetPlayer(t, fScopes);
            });
        } catch (Exception e) {
            return AjaxResult.error("重置异常: " + e.getMessage());
        }
        if (done == null) {
            return AjaxResult.error("玩家不在线，无法重置: " + target);
        }
        return AjaxResult.success("已重置 " + target + " 的：" + String.join("、", done));
    }

    @ApiName("奖池中奖记录")
    @ApiPublic
    @GetMapping("/admin/pools/{pool}/records")
    public AjaxResult poolRecords(ApiRequestContext ctx,
                                  @PathVariable(name = "pool") String pool) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LotteryPlayer lp : plugin.getStorageManager().getAllPlayersSafe()) {
            for (LotteryPlayer.WinRecord w : lp.getHistory()) {
                if (pool.equals(w.pool)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("time", w.time);
                    row.put("name", lp.getName());
                    row.put("uuid", lp.getUuid().toString());
                    row.put("pool", w.pool);
                    row.put("reward", w.display);
                    rows.add(row);
                }
            }
        }
        rows.sort(Comparator.comparing(r -> toLong(r.get("time")),
                Comparator.nullsLast(Comparator.reverseOrder())));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pool", pool);
        data.put("total", rows.size());
        data.put("records", rows);
        return AjaxResult.success(data);
    }

    // ================================================================
    //  管理侧：设置
    // ================================================================

    @ApiName("读取全局设置")
    @ApiPublic
    @GetMapping("/admin/settings")
    public AjaxResult getSettings(ApiRequestContext ctx) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        FileConfiguration c = plugin.getLotteryConfig().raw();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cooldownSeconds", c.getInt("cooldown-seconds", 3));
        data.put("cost", sectionMap(c, "cost"));
        data.put("broadcastBigWin", c.getBoolean("broadcast-big-win", true));
        data.put("broadcastBelowWeight", c.getInt("broadcast-below-weight", 15));
        data.put("pending", sectionMap(c, "pending"));
        data.put("history", sectionMap(c, "history"));
        data.put("limits", sectionMap(c, "limits"));
        data.put("pityEnabled", c.getBoolean("pity.enabled", false));
        data.put("pityAfter", c.getInt("pity.after", 50));
        data.put("require", sectionMap(c, "require"));
        return AjaxResult.success(data);
    }

    @ApiName("保存全局设置")
    @ApiPublic
    @PutMapping("/admin/settings")
    public AjaxResult saveSettings(ApiRequestContext ctx, @RequestBody String body) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        Map<String, Object> p = parse(body);
        try {
            YamlConfiguration y = lotteryYaml();
            if (p.containsKey("cooldownSeconds")) {
                y.set("cooldown-seconds", Math.max(0, toInt(p.get("cooldownSeconds"), 0)));
            }
            if (p.get("cost") instanceof Map) {
                y.set("cost", flatten((Map<?, ?>) p.get("cost")));
            }
            if (p.containsKey("broadcastBigWin")) {
                y.set("broadcast-big-win", p.get("broadcastBigWin"));
            }
            if (p.containsKey("broadcastBelowWeight")) {
                y.set("broadcast-below-weight", Math.max(0, toInt(p.get("broadcastBelowWeight"), 0)));
            }
            if (p.get("pending") instanceof Map) {
                y.set("pending", flatten((Map<?, ?>) p.get("pending")));
            }
            if (p.get("history") instanceof Map) {
                y.set("history", flatten((Map<?, ?>) p.get("history")));
            }
            if (p.get("limits") instanceof Map) {
                y.set("limits", flatten((Map<?, ?>) p.get("limits")));
            }
            if (p.containsKey("pityEnabled")) {
                y.set("pity.enabled", p.get("pityEnabled"));
            }
            if (p.containsKey("pityAfter")) {
                y.set("pity.after", Math.max(1, toInt(p.get("pityAfter"), 50)));
            }
            if (p.get("require") instanceof Map) {
                y.set("require", flatten((Map<?, ?>) p.get("require")));
            }
            saveLottery(y);
            return AjaxResult.success("全局设置已保存");
        } catch (Exception e) {
            return AjaxResult.error("保存设置失败: " + e.getMessage());
        }
    }

    // ================================================================
    //  管理侧：日志 / 背包 / 玩家
    // ================================================================

    @ApiName("读取日志")
    @ApiPublic
    @GetMapping("/admin/logs")
    public AjaxResult getLogs(ApiRequestContext ctx) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        // 业务日志：全服最近中奖汇总
        List<Map<String, Object>> wins = new ArrayList<>();
        for (LotteryPlayer lp : plugin.getStorageManager().getAllPlayersSafe()) {
            for (LotteryPlayer.WinRecord w : lp.getHistory()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("time", w.time);
                row.put("name", lp.getName());
                row.put("pool", w.pool);
                row.put("reward", w.display);
                wins.add(row);
            }
        }
        wins.sort(Comparator.comparing(
                r -> toLong(r.get("time")),
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (wins.size() > 300) {
            wins = new ArrayList<>(wins.subList(0, 300));
        }

        // 服务器日志：latest.log 中与本插件相关的最近行
        List<String> serverLines = new ArrayList<>();
        try {
            File latest = new File(plugin.getDataFolder().getParentFile().getParentFile(),
                    "logs/latest.log");
            if (latest.exists()) {
                List<String> all = Files.readAllLines(latest.toPath(), StandardCharsets.UTF_8);
                for (String line : all) {
                    String lower = line.toLowerCase();
                    if (lower.contains("soceanbox") || lower.contains("soysoceanbox")) {
                        serverLines.add(line);
                    }
                }
                if (serverLines.size() > 200) {
                    serverLines = new ArrayList<>(
                            serverLines.subList(serverLines.size() - 200, serverLines.size()));
                }
            }
        } catch (Exception ignored) {
            // 服务器日志读取为尽力而为，不阻断
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wins", wins);
        data.put("serverLines", serverLines);
        return AjaxResult.success(data);
    }

    @ApiName("读取管理员背包")
    @ApiPublic
    @GetMapping("/admin/inventory")
    public AjaxResult getInventory(ApiRequestContext ctx) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        Player operator = ctx.getSyncPlayer();
        if (operator == null) {
            return AjaxResult.error("管理员不在线，无法读取背包");
        }
        List<Map<String, Object>> items = Sync.run(plugin,
                () -> ItemSerializer.serializeInventory(
                        operator.getInventory().getStorageContents()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operator", operator.getName());
        data.put("items", items);
        return AjaxResult.success(data);
    }

    @ApiName("在线玩家列表")
    @ApiPublic
    @GetMapping("/admin/players")
    public AjaxResult onlinePlayers(ApiRequestContext ctx) {
        AjaxResult deny = denyIfNotOp(ctx);
        if (deny != null) {
            return deny;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.getName());
            m.put("uuid", p.getUniqueId().toString());
            m.put("op", p.isOp());
            out.add(m);
        }
        return AjaxResult.success(out);
    }

    // ================================================================
    //  数据组装
    // ================================================================

    /** 用户侧奖池展示 DTO。 */
    private Map<String, Object> poolView(String name) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", name);
        dto.put("rewards", rewardList(name));
        return dto;
    }

    /** 管理侧奖池完整详情。 */
    private Map<String, Object> poolDetail(String name) {
        ConfigurationSection block = plugin.getLotteryConfig().raw()
                .getConfigurationSection("pools." + name);
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("name", name);
        dto.put("enabled", plugin.getLotteryConfig().isPoolEnabled(name));
        dto.put("start", block == null ? "" : block.getString("start", ""));
        dto.put("end", block == null ? "" : block.getString("end", ""));
        dto.put("status", plugin.getLotteryConfig().getPoolStatus(name));
        dto.put("rewards", rewardList(name));
        return dto;
    }

    private List<Map<String, Object>> rewardList(String name) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RewardDef def : plugin.getLotteryConfig().getRewardPool(name)) {
            out.add(ItemSerializer.fromRewardDef(def));
        }
        return out;
    }

    /** 玩家状态数据（待领取 / 计数 / 保底 / 冷却）。 */
    private Map<String, Object> playerStateData(Player online, LotteryPlayer lp) {
        Map<String, Object> data = new LinkedHashMap<>();
        String active = lp.getActivePool();
        data.put("activePool", active == null || active.isEmpty()
                ? plugin.getLotteryConfig().getDefaultPoolName() : active);
        data.put("totalDraws", lp.getTotalDraws());

        List<Map<String, Object>> pending = new ArrayList<>();
        for (PendingReward r : lp.getPending()) {
            pending.add(pendingToMap(r));
        }
        data.put("pending", pending);

        data.put("cooldownRemaining",
                plugin.getLotteryManager().getCooldownRemainingMillis(lp));

        Map<String, Object> daily = new LinkedHashMap<>();
        daily.put("enabled", plugin.getLotteryConfig().isDailyLimitEnabled());
        daily.put("remaining", plugin.getLotteryManager().getDailyRemaining(lp));
        data.put("daily", daily);

        Map<String, Object> weekly = new LinkedHashMap<>();
        weekly.put("enabled", plugin.getLotteryConfig().isWeeklyLimitEnabled());
        weekly.put("remaining", plugin.getLotteryManager().getWeeklyRemaining(lp));
        data.put("weekly", weekly);

        Map<String, Object> pity = new LinkedHashMap<>();
        pity.put("enabled", plugin.getLotteryConfig().isPityEnabled());
        pity.put("remaining", plugin.getLotteryManager().getPityRemaining(lp));
        data.put("pity", pity);
        return data;
    }

    private Map<String, Object> pendingToMap(PendingReward r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("claimId", r.getClaimId().toString());
        m.put("type", r.getType().name());
        m.put("display", r.getDisplay());
        m.put("expireAt", r.getExpireAt());
        switch (r.getType()) {
            case ITEM:
                m.put("material", r.getMaterial());
                m.put("amount", r.getItemAmount());
                break;
            case COMMAND:
                m.put("command", r.getCommand());
                break;
            default:
                m.put("amount", r.getValue());
                break;
        }
        return m;
    }

    // ================================================================
    //  辅助
    // ================================================================

    /** 校验当前请求来自在线 OP；通过返回 null，否则返回错误响应。 */
    private AjaxResult denyIfNotOp(ApiRequestContext ctx) {
        String name = ctx == null ? null : ctx.getPlayerName();
        if (name == null || name.isEmpty()) {
            return AjaxResult.unauthorized("未认证或凭证无效，无法识别操作者");
        }
        Player online = ctx.getSyncPlayer();
        if (online == null) {
            return AjaxResult.forbidden("仅限在线 OP：凭证玩家 " + name + " 当前不在服务器内");
        }
        if (!online.isOp()) {
            return AjaxResult.forbidden("无权限：仅 OP 可操作（当前: " + name + "）");
        }
        return null;
    }

    private boolean poolExists(String name) {
        return plugin.getLotteryConfig().getPoolNames().contains(name);
    }

    private RewardDef findReward(String pool, String id) {
        for (RewardDef def : plugin.getLotteryConfig().getRewardPool(pool)) {
            if (id.equals(def.getId())) {
                return def;
            }
        }
        return null;
    }

    /** 从前端物品 Map 构造奖项（背包拷贝 / 编辑器），补齐 type/weight/id 默认值。 */
    private static RewardDef buildRewardFromMap(Map<?, ?> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            m.put(String.valueOf(e.getKey()), e.getValue());
        }
        if (!m.containsKey("type")) {
            m.put("type", "ITEM");
        }
        if (!m.containsKey("weight")) {
            m.put("weight", 1);
        }
        if (!m.containsKey("id")) {
            m.put("id", "web_give_" + System.currentTimeMillis());
        }
        return RewardDef.fromMap(m);
    }

    private YamlConfiguration lotteryYaml() {
        FileConfiguration c = plugin.getLotteryConfig().raw();
        return c instanceof YamlConfiguration
                ? (YamlConfiguration) c : new YamlConfiguration();
    }

    /** 保存 lottery.yml 并重新加载，使抽奖逻辑立即使用新配置。 */
    private void saveLottery(YamlConfiguration y) throws Exception {
        y.save(new File(plugin.getDataFolder(), "lottery.yml"));
        plugin.getLotteryConfig().reload();
    }

    private static Map<String, Object> sectionMap(FileConfiguration c, String path) {
        ConfigurationSection s = c.getConfigurationSection(path);
        if (s == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : s.getValues(true).entrySet()) {
            if (e.getValue() instanceof ConfigurationSection) {
                continue;
            }
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** 把嵌套 Map 转为普通 LinkedHashMap（供 YamlConfiguration.set 写入 section）。 */
    private static Map<String, Object> flatten(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) {
                v = flatten((Map<?, ?>) v);
            }
            out.put(String.valueOf(e.getKey()), v);
        }
        return out;
    }

    private static Map<String, Object> simpleResult(boolean ok, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("message", message);
        return m;
    }

    private static Map<String, Object> parse(String body) {
        if (body == null || body.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> m = JsonReader.parseObject(body);
        return m == null ? new LinkedHashMap<>() : m;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int toInt(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Long toLong(Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o == null) {
            return null;
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object e : (List<Object>) o) {
                if (e != null) {
                    out.add(String.valueOf(e));
                }
            }
        }
        return out;
    }

    /** 领取结果传输对象（worker → handler）。 */
    private static final class ClaimResultData {
        private final int claimed;
        private final int failed;
        private final int expired;
        private final int partial;
        private final List<String> failures;

        private ClaimResultData(soys.soysoceanbox.lottery.LotteryManager.ClaimResult cr) {
            this.claimed = cr.claimed;
            this.failed = cr.failed;
            this.expired = cr.expired;
            this.partial = cr.partial;
            this.failures = cr.failures;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("claimed", claimed);
            m.put("failed", failed);
            m.put("expired", expired);
            m.put("partial", partial);
            m.put("failures", failures);
            return m;
        }
    }

    /**
     * 抽奖一次并把结果写入 results（必须在主线程调用）。
     * 独立为静态辅助以复用用户抽奖与后台代抽逻辑。
     */
    private static final class LotteryManagerHack {
        private static void drawOnce(SOYSOceanBox plugin, Player player,
                                     List<Map<String, Object>> results) {
            soys.soysoceanbox.lottery.LotteryManager.DrawResult dr =
                    plugin.getLotteryManager().draw(player);
            Map<String, Object> r = new LinkedHashMap<>();
            boolean ok = dr.status == soys.soysoceanbox.lottery.LotteryManager.DrawStatus.OK;
            r.put("ok", ok);
            r.put("status", dr.status.name());
            r.put("message", drawMessage(dr));
            if (dr.reward != null) {
                r.put("reward", ItemSerializer.fromRewardDef(
                        rewardDefView(plugin, dr)));
            }
            results.add(r);
        }

        /** DrawResult.reward 是 PendingReward，转成前端 Map。 */
        private static RewardDef rewardDefView(SOYSOceanBox plugin,
                                               soys.soysoceanbox.lottery.LotteryManager.DrawResult dr) {
            // PendingReward 无法直接还原 RewardDef，用其字段拼一个展示 DTO
            PendingReward p = dr.reward;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getClaimId().toString());
            m.put("type", p.getType().name());
            m.put("weight", 1);
            m.put("display", p.getDisplay());
            switch (p.getType()) {
                case ITEM:
                    m.put("material", p.getMaterial());
                    m.put("amount", p.getItemAmount());
                    m.put("durability", p.getDurability());
                    if (p.getItemName() != null) {
                        m.put("name", p.getItemName());
                    }
                    if (p.getLore() != null) {
                        m.put("lore", p.getLore());
                    }
                    if (p.getEnchants() != null) {
                        m.put("enchants", p.getEnchants());
                    }
                    if (p.getCustomModelData() != null) {
                        m.put("customModelData", p.getCustomModelData());
                    }
                    if (p.getNbt() != null) {
                        m.put("nbt", p.getNbt());
                    }
                    break;
                case COMMAND:
                    m.put("command", p.getCommand());
                    break;
                default:
                    m.put("amount", p.getValue());
                    break;
            }
            return RewardDef.fromMap(m);
        }

        private static String drawMessage(
                soys.soysoceanbox.lottery.LotteryManager.DrawResult dr) {
            switch (dr.status) {
                case OK:
                    return "抽奖成功";
                case COOLDOWN:
                    return "操作太快，请 "
                            + soys.soysoceanbox.lottery.LotteryManager
                            .formatRemaining(dr.remainMillis) + " 后再试";
                case NO_MONEY:
                    return "金币不足";
                case NO_POINTS:
                    return "点券不足";
                case NO_VAULT:
                    return "经济系统未就绪";
                case NO_PLAYERPOINTS:
                    return "点券系统未就绪";
                case DAILY_LIMIT:
                    return "今日抽奖次数已达上限";
                case WEEKLY_LIMIT:
                    return "本周抽奖次数已达上限";
                case PREREQUISITE:
                    return "不满足抽奖条件"
                            + (dr.requirement != null ? "：" + dr.requirement.describe() : "");
                case NO_POOL:
                    return "奖池暂不可用";
                default:
                    return "抽奖失败";
            }
        }
    }
}
