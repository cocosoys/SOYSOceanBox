package soys.soysoceanbox.lottery;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.config.LotteryConfig;
import soys.soysoceanbox.util.NbtHelper;
import soys.soysoceanbox.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽奖核心逻辑。
 * <p>
 * 负责：抽奖冷却、每日/每周上限、消耗校验、按权重（含保底）随机抽取奖项、
 * 把奖励落入「待领取」列表、以及将待领取奖励实际发放给玩家
 * （经济 / 点券 / 物品 / 指令）。支持多奖池切换。
 * </p>
 */
public class LotteryManager {

    private final SOYSOceanBox plugin;
    private final Map<UUID, LotteryPlayer> cache = new ConcurrentHashMap<>();

    private Economy economy;
    private PlayerPointsAPI pointsApi;

    public LotteryManager(SOYSOceanBox plugin) {
        this.plugin = plugin;
    }

    public void setEconomy(Economy economy) {
        this.economy = economy;
    }

    public void setPointsApi(PlayerPointsAPI pointsApi) {
        this.pointsApi = pointsApi;
    }

    public Economy getEconomy() {
        return economy;
    }

    public PlayerPointsAPI getPointsApi() {
        return pointsApi;
    }

    // ================================================================
    //  缓存与持久化
    // ================================================================

    public LotteryPlayer getCached(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * 取得玩家档案：优先缓存，否则从主存储同步加载；不存在则新建。
     */
    public LotteryPlayer getOrLoad(Player player) {
        LotteryPlayer lp = cache.get(player.getUniqueId());
        if (lp != null) {
            lp.setName(player.getName());
            return lp;
        }
        LotteryPlayer loaded = null;
        try {
            loaded = plugin.getStorageManager().loadPlayer(player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("加载玩家 " + player.getName() + " 档案失败: " + e.getMessage());
        }
        if (loaded == null) {
            loaded = new LotteryPlayer(player.getUniqueId(), player.getName());
        } else {
            loaded.setName(player.getName());
        }
        cache.put(player.getUniqueId(), loaded);
        return loaded;
    }

    /**
     * 玩家上线时异步装入缓存，避免首次指令时的同步阻塞。
     */
    public void loadOnJoin(Player player) {
        plugin.getStorageManager().loadPlayerAsync(player.getUniqueId(), loaded -> {
            LotteryPlayer lp = loaded;
            if (lp == null) {
                lp = new LotteryPlayer(player.getUniqueId(), player.getName());
            } else {
                lp.setName(player.getName());
            }
            cache.put(player.getUniqueId(), lp);
        });
    }

    /**
     * 异步保存单个玩家档案。
     */
    public void save(LotteryPlayer player) {
        plugin.getStorageManager().savePlayerAsync(player);
    }

    /**
     * 关服时同步保存全部缓存（阻塞）。
     */
    public void saveAllBlocking() {
        if (cache.isEmpty()) {
            return;
        }
        plugin.getStorageManager().savePlayersBlocking(cache.values());
    }

    public void clearCache() {
        cache.clear();
    }

    /**
     * 从内存缓存中移除指定玩家（删除档案或重新加载前调用）。
     */
    public void forget(UUID uuid) {
        cache.remove(uuid);
    }

    // ================================================================
    //  抽奖
    // ================================================================

    public enum DrawStatus {
        OK,
        COOLDOWN,
        NO_MONEY,
        NO_POINTS,
        NO_VAULT,
        NO_PLAYERPOINTS,
        DAILY_LIMIT,
        WEEKLY_LIMIT,
        PREREQUISITE,
        NO_POOL,
        ERROR
    }

    public static class DrawResult {
        public DrawStatus status;
        public PendingReward reward;
        public long remainMillis;
        /** 前置条件校验失败时携带所需条件对象，供指令层提示。 */
        public LotteryConfig.Requirement requirement;

        DrawResult(DrawStatus status) {
            this.status = status;
        }
    }

    public DrawResult draw(Player player) {
        LotteryPlayer lp = getOrLoad(player);
        soys.soysoceanbox.config.LotteryConfig cfg = plugin.getLotteryConfig();

        // ---- 冷却 ----
        int cooldown = cfg.getCooldownSeconds();
        if (cooldown > 0) {
            long elapsed = System.currentTimeMillis() - lp.getLastDrawTime();
            long need = cooldown * 1000L;
            if (elapsed < need) {
                DrawResult r = new DrawResult(DrawStatus.COOLDOWN);
                r.remainMillis = need - elapsed;
                return r;
            }
        }

        // ---- 抽奖前置条件 ----
        LotteryConfig.Requirement req = cfg.getDrawRequirement();
        if (req != null) {
            LotteryConfig.Requirement failed = checkRequirement(player, req);
            if (failed != null) {
                DrawResult r = new DrawResult(DrawStatus.PREREQUISITE);
                r.requirement = failed;
                return r;
            }
        }

        // ---- 每日上限 ----
        if (cfg.isDailyLimitEnabled()) {
            rolloverDaily(lp);
            if (lp.getDailyCount() >= cfg.getDailyLimit()) {
                DrawResult r = new DrawResult(DrawStatus.DAILY_LIMIT);
                r.remainMillis = LotteryMath.millisUntilNextDay();
                return r;
            }
        }

        // ---- 每周上限 ----
        if (cfg.isWeeklyLimitEnabled()) {
            rolloverWeekly(lp);
            if (lp.getWeeklyCount() >= cfg.getWeeklyLimit()) {
                DrawResult r = new DrawResult(DrawStatus.WEEKLY_LIMIT);
                return r;
            }
        }

        // ---- 消耗 ----
        if (cfg.isCostEnabled()) {
            String type = cfg.getCostType();
            double amount = cfg.getCostAmount();
            if ("money".equals(type)) {
                if (economy == null) {
                    return new DrawResult(DrawStatus.NO_VAULT);
                }
                if (!economy.has(player, amount)) {
                    return new DrawResult(DrawStatus.NO_MONEY);
                }
                economy.withdrawPlayer(player, amount);
            } else {
                if (pointsApi == null) {
                    return new DrawResult(DrawStatus.NO_PLAYERPOINTS);
                }
                int need = (int) Math.floor(amount);
                if (pointsApi.look(player.getUniqueId()) < need) {
                    return new DrawResult(DrawStatus.NO_POINTS);
                }
                pointsApi.take(player.getUniqueId(), need);
            }
        }

        // ---- 选择奖池（仅限当前生效的限时奖池）----
        String poolName = lp.getActivePool();
        if (poolName == null || poolName.isEmpty()) {
            poolName = cfg.getDefaultPoolName();
        }
        List<String> activePools = cfg.getActivePoolNames();
        String effectivePool;
        if (activePools.contains(poolName)) {
            effectivePool = poolName;
        } else if (activePools.contains(cfg.getDefaultPoolName())) {
            effectivePool = cfg.getDefaultPoolName();
        } else if (!activePools.isEmpty()) {
            effectivePool = activePools.get(0);
        } else {
            effectivePool = null;
        }
        if (effectivePool == null) {
            return new DrawResult(DrawStatus.NO_POOL);
        }
        List<RewardDef> pool = cfg.getRewardPool(effectivePool);
        if (pool.isEmpty()) {
            return new DrawResult(DrawStatus.NO_POOL);
        }

        // ---- 保底：达到阈值则限制到保底池（按保底池独立权重抽取） ----
        List<RewardDef> eligible = pool;
        if (cfg.isPityEnabled()) {
            List<soys.soysoceanbox.config.LotteryConfig.PityEntry> pity = cfg.getPityPool();
            if (!pity.isEmpty() && lp.getDrawsSinceBigWin() >= cfg.getPityAfter()) {
                List<RewardDef> pityPool = new ArrayList<>();
                for (soys.soysoceanbox.config.LotteryConfig.PityEntry e : pity) {
                    RewardDef d = findInPool(pool, e.id);
                    if (d != null) {
                        pityPool.add(d.withWeight(e.weight));
                    }
                }
                if (!pityPool.isEmpty()) {
                    eligible = pityPool;
                }
            }
        }

        RewardDef def = cfg.rollReward(eligible);
        if (def == null) {
            return new DrawResult(DrawStatus.NO_POOL);
        }

        // ---- 记账 ----
        PendingReward reward = def.createPending();
        // 待领取奖励过期时间（TTL）：0 表示不过期
        int expireSec = cfg.getPendingExpireSeconds();
        if (expireSec > 0) {
            reward.setExpireAt(System.currentTimeMillis() + expireSec * 1000L);
        }
        lp.addPending(reward);
        // 个人中奖记录（history.keep > 0 时记录并封顶）
        int keep = cfg.getHistoryKeep();
        if (keep > 0) {
            lp.addWin(System.currentTimeMillis(), effectivePool, reward.getDisplay(), keep);
        }
        lp.addDraw();
        lp.setLastDrawTime(System.currentTimeMillis());
        if (cfg.isDailyLimitEnabled()) {
            rolloverDaily(lp);
            lp.setDailyCount(lp.getDailyCount() + 1);
        }
        if (cfg.isWeeklyLimitEnabled()) {
            rolloverWeekly(lp);
            lp.setWeeklyCount(lp.getWeeklyCount() + 1);
        }
        // 保底计数：中保底池则清零，否则累加
        if (cfg.isPityEnabled() && cfg.isPityReward(def.getId())) {
            lp.setDrawsSinceBigWin(0);
        } else {
            lp.setDrawsSinceBigWin(lp.getDrawsSinceBigWin() + 1);
        }

        save(lp);

        // ---- 抽奖成功音效与粒子 ----
        playDrawEffects(player);

        // ---- 全服广播大额中奖（含标题与专属音效）----
        if (cfg.isBroadcastBigWin()) {
            int below = cfg.getBroadcastBelowWeight();
            if (below <= 0 || def.getWeight() <= below) {
                broadcastBigWin(player, reward.getDisplay());
                playBigWinEffects(player, reward.getDisplay());
            }
        }

        DrawResult r = new DrawResult(DrawStatus.OK);
        r.reward = reward;
        return r;
    }

    private void rolloverDaily(LotteryPlayer lp) {
        String key = LotteryMath.dailyKey();
        if (LotteryMath.isNewDailyCycle(lp.getDailyKey(), key)) {
            lp.setDailyKey(key);
            lp.setDailyCount(0);
        }
    }

    private void rolloverWeekly(LotteryPlayer lp) {
        String key = LotteryMath.weeklyKey();
        if (LotteryMath.isNewWeeklyCycle(lp.getWeeklyKey(), key)) {
            lp.setWeeklyKey(key);
            lp.setWeeklyCount(0);
        }
    }

    /** 在主奖池中按 id 查找奖项定义。 */
    private RewardDef findInPool(List<RewardDef> pool, String id) {
        if (id == null) {
            return null;
        }
        for (RewardDef def : pool) {
            if (id.equals(def.getId())) {
                return def;
            }
        }
        return null;
    }

    // ================================================================
    //  前置条件校验（供 draw() 调用）
    // ================================================================

    /**
     * 校验玩家是否满足前置条件；不满足则返回所要求的条件对象，满足返回 null。
     */
    private LotteryConfig.Requirement checkRequirement(Player player, LotteryConfig.Requirement req) {
        if (req.permission != null && !req.permission.isEmpty()
                && !player.hasPermission(req.permission)) {
            return req;
        }
        if (req.level > 0 && player.getLevel() < req.level) {
            return req;
        }
        if (req.itemMaterial != null && !req.itemMaterial.isEmpty()) {
            if (countMaterial(player, req.itemMaterial) < req.itemAmount) {
                return req;
            }
        }
        return null;
    }

    /** 统计玩家背包中某材质物品的总数量。 */
    private int countMaterial(Player player, String material) {
        Material m = Material.matchMaterial(material);
        if (m == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == m) {
                total += it.getAmount();
            }
        }
        return total;
    }

    // ================================================================
    //  占位符辅助（只读，不修改玩家数据）
    // ================================================================

    /** 考虑跨日回滚后的当日已抽次数（跨日则视为 0）。 */
    public int getDailyCountNow(LotteryPlayer lp) {
        return LotteryMath.dailyKey().equals(lp.getDailyKey()) ? lp.getDailyCount() : 0;
    }

    /** 考虑跨周回滚后的本周已抽次数（跨周则视为 0）。 */
    public int getWeeklyCountNow(LotteryPlayer lp) {
        return LotteryMath.weeklyKey().equals(lp.getWeeklyKey()) ? lp.getWeeklyCount() : 0;
    }

    /**
     * 当日剩余可抽次数；未启用每日上限时返回 -1（表示无限）。
     */
    public int getDailyRemaining(LotteryPlayer lp) {
        LotteryConfig cfg = plugin.getLotteryConfig();
        if (!cfg.isDailyLimitEnabled()) {
            return -1;
        }
        return Math.max(0, cfg.getDailyLimit() - getDailyCountNow(lp));
    }

    /**
     * 本周剩余可抽次数；未启用每周上限时返回 -1（表示无限）。
     */
    public int getWeeklyRemaining(LotteryPlayer lp) {
        LotteryConfig cfg = plugin.getLotteryConfig();
        if (!cfg.isWeeklyLimitEnabled()) {
            return -1;
        }
        return Math.max(0, cfg.getWeeklyLimit() - getWeeklyCountNow(lp));
    }

    /**
     * 距触发保底还差多少次；未启用保底时返回 -1。
     */
    public int getPityRemaining(LotteryPlayer lp) {
        LotteryConfig cfg = plugin.getLotteryConfig();
        if (!cfg.isPityEnabled()) {
            return -1;
        }
        return Math.max(0, cfg.getPityAfter() - lp.getDrawsSinceBigWin());
    }

    /** 冷却剩余毫秒（无冷却或已过冷却则返回 0）。 */
    public long getCooldownRemainingMillis(LotteryPlayer lp) {
        int cooldown = plugin.getLotteryConfig().getCooldownSeconds();
        if (cooldown <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lp.getLastDrawTime();
        long need = cooldown * 1000L;
        return Math.max(0, need - elapsed);
    }

    private void broadcastBigWin(Player player, String display) {
        String msg = plugin.getMessageManager().get("lottery.broadcast.big-win",
                new HashMap<String, String>() {{
                    put("player", player.getName());
                    put("reward", display);
                }});
        for (Player p : Bukkit.getOnlinePlayers()) {
            Text.send(p, msg);
        }
    }

    // ================================================================
    //  音效与动画效果
    // ================================================================

    /**
     * 播放抽奖成功的音效与粒子效果。
     */
    private void playDrawEffects(Player player) {
        LotteryConfig.EffectConfig cfg = plugin.getLotteryConfig().getEffects();
        // 音效
        playSoundSafe(player, cfg.drawSound, cfg.drawSoundVolume, cfg.drawSoundPitch);
        // 粒子
        if (cfg.drawParticlesEnabled) {
            spawnParticlesSafe(player, cfg.drawParticleType, cfg.drawParticleCount,
                    cfg.drawParticleOffsetX, cfg.drawParticleOffsetY, cfg.drawParticleOffsetZ,
                    cfg.drawParticleExtra);
        }
    }

    /**
     * 播放领取成功的音效。
     */
    private void playClaimEffects(Player player) {
        LotteryConfig.EffectConfig cfg = plugin.getLotteryConfig().getEffects();
        playSoundSafe(player, cfg.claimSound, cfg.claimSoundVolume, cfg.claimSoundPitch);
    }

    /**
     * 播放大额中奖的标题消息与专属音效（触发广播时调用）。
     */
    private void playBigWinEffects(Player player, String rewardDisplay) {
        LotteryConfig.EffectConfig cfg = plugin.getLotteryConfig().getEffects();
        // 标题消息
        if (cfg.bigWinTitleEnabled) {
            String title = Text.color(cfg.bigWinTitle);
            String subtitle = Text.color(cfg.bigWinSubtitle.replace("{reward}", rewardDisplay));
            try {
                player.sendTitle(title, subtitle, cfg.bigWinFadeIn, cfg.bigWinStay, cfg.bigWinFadeOut);
            } catch (Throwable ignored) {
                // 低版本服务端不支持 sendTitle 时忽略
            }
        }
        // 专属音效（配置了则使用，否则回退到抽奖音效）
        String sound = (cfg.bigWinSound != null && !cfg.bigWinSound.isEmpty())
                ? cfg.bigWinSound : cfg.drawSound;
        playSoundSafe(player, sound, cfg.bigWinSoundVolume, cfg.bigWinSoundPitch);
    }

    /**
     * 安全播放音效：音效名无效或播放失败时静默忽略，不影响主流程。
     */
    private void playSoundSafe(Player player, String soundName, float volume, float pitch) {
        if (soundName == null || soundName.isEmpty()) {
            return;
        }
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[效果] 音效名称无效: " + soundName);
        } catch (Throwable ignored) {
            // 播放失败静默忽略
        }
    }

    /**
     * 安全生成粒子效果：粒子名无效或生成失败时静默忽略。
     */
    private void spawnParticlesSafe(Player player, String particleName, int count,
                                     double offsetX, double offsetY, double offsetZ, double extra) {
        if (particleName == null || particleName.isEmpty()) {
            return;
        }
        try {
            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleName);
            player.spawnParticle(particle, player.getLocation().add(0, 1, 0),
                    count, offsetX, offsetY, offsetZ, extra);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[效果] 粒子名称无效: " + particleName);
        } catch (Throwable ignored) {
            // 生成失败静默忽略
        }
    }

    // ================================================================
    //  领取
    // ================================================================

    public static class ClaimResult {
        public int claimed;
        public int failed;
        /** 本次操作清理掉的已过期奖励数量 */
        public int expired;
        /** 部分成功的奖励数（物品因背包空间不足仅部分放入，剩余数量已写回并保留在待领取列表） */
        public int partial;
        public final List<String> failures = new ArrayList<>();
    }

    /**
     * 移除玩家已过期（TTL 失效）的待领取奖励。
     *
     * @return 实际清理掉的条数
     */
    public int pruneExpired(LotteryPlayer lp, long nowMillis) {
        if (lp.getPending().isEmpty()) {
            return 0;
        }
        int before = lp.getPending().size();
        lp.getPending().removeIf(reward -> reward.isExpired(nowMillis));
        return before - lp.getPending().size();
    }

    /**
     * 将毫秒时长格式化为中文短描述，用于展示奖励剩余有效时间。
     */
    public static String formatRemaining(long ms) {
        if (ms <= 0) {
            return "0秒";
        }
        long s = ms / 1000;
        long d = s / 86400;
        s %= 86400;
        long h = s / 3600;
        s %= 3600;
        long m = s / 60;
        s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("天");
        }
        if (h > 0) {
            sb.append(h).append("时");
        }
        if (m > 0) {
            sb.append(m).append("分");
        }
        if (s > 0 || sb.length() == 0) {
            sb.append(s).append("秒");
        }
        return sb.toString();
    }

    /**
     * 领取待领取奖励。
     *
     * @param target null / "all" 表示领取全部；否则按序号（从 1 开始）或 claimId 前缀匹配
     */
    public ClaimResult claim(Player player, String target) {
        LotteryPlayer lp = getOrLoad(player);
        ClaimResult result = new ClaimResult();
        // 先清理已过期奖励
        long now = System.currentTimeMillis();
        result.expired = pruneExpired(lp, now);
        if (result.expired > 0) {
            save(lp);
        }
        if (!lp.hasPending()) {
            return result;
        }

        List<PendingReward> toClaim = new ArrayList<>();
        if (target == null || target.equalsIgnoreCase("all")) {
            toClaim.addAll(new ArrayList<>(lp.getPending()));
        } else {
            PendingReward chosen = findByTarget(lp, target);
            if (chosen != null) {
                toClaim.add(chosen);
            }
        }

        for (PendingReward reward : toClaim) {
            int originalAmount = reward.getItemAmount();
            if (grant(player, reward)) {
                lp.removePending(reward.getClaimId());
                result.claimed++;
            } else {
                // 区分完全失败与部分成功：仅 ITEM 类型可能部分发放
                if (reward.getType() == PendingReward.Type.ITEM
                        && reward.getItemAmount() > 0
                        && reward.getItemAmount() < originalAmount) {
                    // 部分成功：grant() 已将剩余数量写回 reward，奖励保留在待领取列表
                    result.partial++;
                } else {
                    result.failed++;
                    result.failures.add(reward.getDisplay());
                }
            }
        }
        if (result.claimed > 0 || result.partial > 0) {
            save(lp);
        }
        // 领取成功音效
        if (result.claimed > 0) {
            playClaimEffects(player);
        }
        return result;
    }

    private PendingReward findByTarget(LotteryPlayer lp, String target) {
        Integer idx = parseInt(target);
        if (idx != null && idx >= 1 && idx <= lp.getPending().size()) {
            return lp.getPending().get(idx - 1);
        }
        for (PendingReward reward : lp.getPending()) {
            if (reward.getClaimId().toString().startsWith(target)) {
                return reward;
            }
        }
        return null;
    }

    private boolean grant(Player player, PendingReward reward) {
        switch (reward.getType()) {
            case MONEY:
                if (economy == null) {
                    return false;
                }
                economy.depositPlayer(player, reward.getValue());
                return true;
            case POINTS:
                if (pointsApi == null) {
                    return false;
                }
                pointsApi.give(player.getUniqueId(), (int) Math.floor(reward.getValue()));
                return true;
            case ITEM:
                ItemStack item = buildItem(reward);
                if (item == null) {
                    return false;
                }
                int originalAmount = item.getAmount();
                Map<Integer, ItemStack> left = player.getInventory().addItem(item);
                if (left.isEmpty()) {
                    return true;
                }
                // 背包空间不足：统计未能放入的剩余数量
                int remaining = 0;
                for (ItemStack leftover : left.values()) {
                    if (leftover != null) {
                        remaining += leftover.getAmount();
                    }
                }
                if (remaining >= originalAmount) {
                    // 完全未放入，视为发放失败
                    return false;
                }
                // 部分成功：将剩余数量写回奖励，保留在待领取列表供下次领取
                // （避免玩家重复领取已放入背包的部分）
                reward.setItemAmount(remaining);
                return false;
            case COMMAND:
                String cmd = reward.getCommand()
                        .replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString());
                return plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
            default:
                return false;
        }
    }

    private ItemStack buildItem(PendingReward reward) {
        Material material = Material.matchMaterial(reward.getMaterial() == null ? "" : reward.getMaterial());
        if (material == null) {
            plugin.getLogger().warning("奖励物品材质无效: " + reward.getMaterial());
            return null;
        }
        ItemStack item = new ItemStack(material, Math.max(1, reward.getItemAmount()), (short) reward.getDurability());
        if (item.hasItemMeta()) {
            applyMeta(item, reward);
        }
        // 原始 NBT（尽力而为，1.12.2 仅当服务端提供 NMS 时生效）
        if (reward.getNbt() != null && !reward.getNbt().isEmpty()) {
            item = NbtHelper.applyNbt(item, reward.getNbt());
        }
        return item;
    }

    private void applyMeta(ItemStack item, PendingReward reward) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (reward.getItemName() != null) {
            meta.setDisplayName(Text.color(reward.getItemName()));
        }
        if (reward.getLore() != null && !reward.getLore().isEmpty()) {
            meta.setLore(Text.color(reward.getLore()));
        }
        if (reward.getEnchants() != null) {
            for (Map.Entry<String, Integer> entry : reward.getEnchants().entrySet()) {
                org.bukkit.enchantments.Enchantment enchantment =
                        org.bukkit.enchantments.Enchantment.getByName(entry.getKey());
                if (enchantment != null) {
                    meta.addEnchant(enchantment, entry.getValue(), true);
                }
            }
        }
        NbtHelper.applyCustomModelData(meta, reward.getCustomModelData());
        item.setItemMeta(meta);
    }

    private Integer parseInt(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ================================================================
    //  网页后台专用操作：直接发放 / 重置 / 切换奖池
    // ================================================================

    /**
     * 绕过抽奖与待领取，直接把一个奖项定义发放给在线玩家（后台「直接发放」）。
     *
     * @return null 表示成功；否则为失败原因（如背包空间不足、经济插件缺失）
     */
    public String giveDirect(Player player, RewardDef def) {
        if (def == null) {
            return "奖项不存在或配置无效";
        }
        PendingReward reward = def.createPending();
        int original = reward.getItemAmount();
        if (grant(player, reward)) {
            return null;
        }
        // 物品部分放入：grant 已把剩余数量写回 reward，明确提示剩余数量
        if (reward.getType() == PendingReward.Type.ITEM && reward.getItemAmount() > 0
                && reward.getItemAmount() < original) {
            return "背包空间不足，尚有 " + reward.getItemAmount() + " 个未发放: " + def.getDisplay();
        }
        return "发放失败: " + def.getDisplay();
    }

    /**
     * 重置目标玩家的指定数据（后台「重置领取」）。
     *
     * @param scopes 待重置项：pending / pity / limits / pool / cooldown / all
     * @return 实际被重置的项中文描述（用于回执）
     */
    public List<String> resetPlayer(Player target, List<String> scopes) {
        LotteryPlayer lp = getOrLoad(target);
        boolean all = scopes.contains("all");
        List<String> done = new ArrayList<>();
        if (all || scopes.contains("pending")) {
            lp.getPending().clear();
            done.add("待领取奖励");
        }
        if (all || scopes.contains("pity")) {
            lp.setDrawsSinceBigWin(0);
            done.add("保底计数");
        }
        if (all || scopes.contains("limits")) {
            lp.setDailyCount(0);
            lp.setWeeklyCount(0);
            done.add("每日/每周计数");
        }
        if (all || scopes.contains("pool")) {
            lp.setActivePool("");
            done.add("当前奖池");
        }
        if (all || scopes.contains("cooldown")) {
            lp.setLastDrawTime(0L);
            done.add("抽奖冷却");
        }
        save(lp);
        return done;
    }

    /**
     * 切换玩家当前生效奖池。
     *
     * @param poolName 奖池名；空串表示恢复默认
     * @param force    后台操作传 true（可切到未生效/禁用奖池）；玩家自助传 false
     * @return null 表示成功；否则为失败原因
     */
    public String switchPool(Player player, String poolName, boolean force) {
        soys.soysoceanbox.config.LotteryConfig cfg = plugin.getLotteryConfig();
        LotteryPlayer lp = getOrLoad(player);
        if (poolName == null || poolName.isEmpty()) {
            lp.setActivePool("");
            save(lp);
            return null;
        }
        if (!cfg.getPoolNames().contains(poolName)) {
            return "奖池不存在: " + poolName;
        }
        if (!force) {
            if (!cfg.isPoolEnabled(poolName)) {
                return "奖池已被禁用: " + poolName;
            }
            if (!cfg.isPoolActive(poolName, System.currentTimeMillis())) {
                return "奖池当前不在生效窗口内: " + poolName;
            }
        }
        lp.setActivePool(poolName);
        save(lp);
        return null;
    }
}
