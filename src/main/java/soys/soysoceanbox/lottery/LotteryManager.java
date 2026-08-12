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
        NO_POOL,
        ERROR
    }

    public static class DrawResult {
        public DrawStatus status;
        public PendingReward reward;
        public long remainMillis;

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

        // ---- 选择奖池 ----
        String poolName = lp.getActivePool();
        if (poolName == null || poolName.isEmpty()) {
            poolName = cfg.getDefaultPoolName();
        }
        List<RewardDef> pool = cfg.getRewardPool(poolName);
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
        lp.addPending(reward);
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

        // ---- 全服广播大额中奖 ----
        if (cfg.isBroadcastBigWin()) {
            int below = cfg.getBroadcastBelowWeight();
            if (below <= 0 || def.getWeight() <= below) {
                broadcastBigWin(player, reward.getDisplay());
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
    //  领取
    // ================================================================

    public static class ClaimResult {
        public int claimed;
        public int failed;
        public final List<String> failures = new ArrayList<>();
    }

    /**
     * 领取待领取奖励。
     *
     * @param target null / "all" 表示领取全部；否则按序号（从 1 开始）或 claimId 前缀匹配
     */
    public ClaimResult claim(Player player, String target) {
        LotteryPlayer lp = getOrLoad(player);
        ClaimResult result = new ClaimResult();
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
            if (grant(player, reward)) {
                lp.removePending(reward.getClaimId());
                result.claimed++;
            } else {
                result.failed++;
                result.failures.add(reward.getDisplay());
            }
        }
        if (result.claimed > 0) {
            save(lp);
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
                Map<Integer, ItemStack> left = player.getInventory().addItem(item);
                return left.isEmpty();
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
}
