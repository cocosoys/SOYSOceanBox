package soys.soysoceanbox.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.lottery.LotteryMath;
import soys.soysoceanbox.lottery.RewardDef;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 抽奖核心规则配置（lottery.yml）访问器。
 * <p>与 config.yml 完全分离，集中收敛抽奖冷却、消耗、奖池、上限、保底等读取，
 * 便于单独维护奖池与消耗策略，且支持热重载。</p>
 */
public class LotteryConfig {

    private final SOYSOceanBox plugin;
    private FileConfiguration config;

    /** lottery.yml 当前期望的 config-version，升级时合并到此版本。 */
    private static final int EXPECTED_VERSION = 6;

    public LotteryConfig(SOYSOceanBox plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * 重载 lottery.yml：
     * 若数据目录下不存在则先从 jar 复制默认文件；若存在但 config-version 落后，
     * 则自动合并 jar 默认配置中缺失的键并升级版本号（保留用户已配置的值）。
     */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "lottery.yml");
        if (!file.exists()) {
            plugin.saveResource("lottery.yml", false);
            this.config = YamlConfiguration.loadConfiguration(file);
            return;
        }
        FileConfiguration user = YamlConfiguration.loadConfiguration(file);
        FileConfiguration defaults = loadDefaults();
        // 先做 v5→v6 奖池结构迁移（奖项裸列表 → 配置块），再合并缺失的默认键
        boolean migrated = ConfigUpgrader.migratePoolsV6(user);
        boolean merged = defaults != null
                && ConfigUpgrader.mergeDefaults(user, defaults, EXPECTED_VERSION);
        if (migrated || merged) {
            try {
                user.save(file);
                plugin.getLogger().info("[lottery.yml] 已自动升级到 config-version "
                        + EXPECTED_VERSION);
            } catch (Exception e) {
                plugin.getLogger().warning("[lottery.yml] 升级保存失败: " + e.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    /** 读取 jar 内置的 lottery.yml 作为升级基准。 */
    private FileConfiguration loadDefaults() {
        try (InputStream in = plugin.getResource("lottery.yml")) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    public FileConfiguration raw() {
        return config;
    }

    // ================================================================
    //  基础规则
    // ================================================================

    /** 两次抽奖之间的最小间隔（秒），0 = 无冷却 */
    public int getCooldownSeconds() {
        return Math.max(0, config.getInt("cooldown-seconds", 3));
    }

    /** 抽奖是否消耗货币/点券 */
    public boolean isCostEnabled() {
        return config.getBoolean("cost.enabled", true);
    }

    /** 消耗类型：money（Vault 经济）或 points（PlayerPoints） */
    public String getCostType() {
        return config.getString("cost.type", "points").toLowerCase();
    }

    /** 每次抽奖消耗的数量 */
    public double getCostAmount() {
        return config.getDouble("cost.amount", 100);
    }

    /** 中奖后是否全服广播（v1 预留，默认关闭） */
    public boolean isBroadcastBigWin() {
        return config.getBoolean("broadcast-big-win", false);
    }

    /** 触发全服广播所需的最高权重，0 = 不按权重筛选（即所有中奖都广播） */
    public int getBroadcastBelowWeight() {
        return config.getInt("broadcast-below-weight", 0);
    }

    // ================================================================
    //  待领取奖励过期（TTL）
    // ================================================================

    /**
     * 待领取奖励的过期时间（秒），0 = 永不过期。
     * 抽奖瞬间按「当前时间 + 该秒数」固化到每条奖励上；过期的奖励在
     * claim / list 时自动清理并落盘。
     */
    public int getPendingExpireSeconds() {
        return Math.max(0, config.getInt("pending.expire-after", 0));
    }

    // ================================================================
    //  个人中奖记录
    // ================================================================

    /**
     * 个人中奖记录保留条数（仅保留最近 N 条），0 = 不记录以节省空间。
     */
    public int getHistoryKeep() {
        return Math.max(0, config.getInt("history.keep", 50));
    }

    // ================================================================
    //  多奖池
    // ================================================================

    /** 默认奖池名称 */
    public String getDefaultPoolName() {
        return config.getString("default-pool", "default");
    }

    /** 是否允许玩家自行切换奖池 */
    public boolean isPlayerSwitchAllowed() {
        return config.getBoolean("allow-player-switch", true);
    }

    /** 所有已配置的奖池名称 */
    public List<String> getPoolNames() {
        ConfigurationSection section = config.getConfigurationSection("pools");
        if (section == null) {
            return Collections.singletonList(getDefaultPoolName());
        }
        return new ArrayList<>(section.getKeys(false));
    }

    /**
     * 按名称读取奖池奖项列表；名称无效时回退到默认奖池。
     * <p>v6 结构：奖项位于 {@code pools.<名称>.rewards} 列表。</p>
     */
    public List<RewardDef> getRewardPool(String name) {
        String resolved = resolvePoolName(name);
        ConfigurationSection block = config.getConfigurationSection("pools." + resolved);
        List<RewardDef> pool = new ArrayList<>();
        if (block == null) {
            return pool;
        }
        int autoIndex = 0;
        for (Map<?, ?> raw : block.getMapList("rewards")) {
            // getMapList 元素为 Map<?,?>，转为 String 键 Map
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                map.put(String.valueOf(e.getKey()), e.getValue());
            }
            // 奖项未显式 id 时用索引兜底，保证 id 非空
            Object idVal = map.get("id");
            if (idVal == null || idVal.toString().isEmpty()) {
                map.put("id", "reward_" + autoIndex);
            }
            RewardDef def = RewardDef.fromMap(map);
            if (def != null) {
                pool.add(def);
            }
            autoIndex++;
        }
        return pool;
    }

    /**
     * 奖池是否被管理员启用（enabled 开关）。配置块缺失时视为启用。
     */
    public boolean isPoolEnabled(String name) {
        ConfigurationSection block = config.getConfigurationSection(
                "pools." + resolvePoolName(name));
        return block == null || block.getBoolean("enabled", true);
    }

    /** 解析奖池名：空则默认；配置块不存在则回退默认。 */
    private String resolvePoolName(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultPoolName();
        }
        if (config.getConfigurationSection("pools." + name) == null
                && !getDefaultPoolName().equals(name)) {
            return getDefaultPoolName();
        }
        return name;
    }

    /** 读取默认奖池（向后兼容） */
    public List<RewardDef> getRewardPool() {
        return getRewardPool(getDefaultPoolName());
    }

    // ================================================================
    //  每日 / 每周上限
    // ================================================================

    public boolean isDailyLimitEnabled() {
        return config.getBoolean("limits.daily.enabled", false);
    }

    public int getDailyLimit() {
        return Math.max(0, config.getInt("limits.daily.amount", 0));
    }

    public boolean isWeeklyLimitEnabled() {
        return config.getBoolean("limits.weekly.enabled", false);
    }

    public int getWeeklyLimit() {
        return Math.max(0, config.getInt("limits.weekly.amount", 0));
    }

    // ================================================================
    //  概率保底（pity）
    // ================================================================

    public boolean isPityEnabled() {
        return config.getBoolean("pity.enabled", false);
    }

    /** 连续未抽中保底池奖项达到该次数后，下一次强制从保底池抽取 */
    public int getPityAfter() {
        return Math.max(1, config.getInt("pity.after", 50));
    }

    /**
     * 保底池奖项列表（带独立权重）。
     * <p>兼容两种写法：</p>
     * <pre>
     *   pool: [emerald_cmd, diamond]          # 旧式平铺列表，权重均为 1
     *   pool:                                 # 新式带权重
     *     - id: emerald_cmd
     *       weight: 3
     *     - id: diamond
     *       weight: 1
     * </pre>
     */
    public List<PityEntry> getPityPool() {
        List<PityEntry> result = new ArrayList<>();
        List<?> raw = config.getList("pity.pool");
        if (raw == null) {
            return result;
        }
        for (Object o : raw) {
            if (o instanceof String) {
                result.add(new PityEntry((String) o, 1));
            } else if (o instanceof ConfigurationSection) {
                ConfigurationSection s = (ConfigurationSection) o;
                String id = s.getString("id");
                if (id != null && !id.isEmpty()) {
                    result.add(new PityEntry(id, s.getInt("weight", 1)));
                }
            } else if (o instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) o;
                Object idObj = m.get("id");
                if (idObj != null && !String.valueOf(idObj).isEmpty()) {
                    int w = 1;
                    Object wObj = m.get("weight");
                    if (wObj instanceof Number) {
                        w = ((Number) wObj).intValue();
                    }
                    result.add(new PityEntry(String.valueOf(idObj), w));
                }
            }
        }
        return result;
    }

    /** 判断某奖项 id 是否属于保底池（用于保底计数重置判定）。 */
    public boolean isPityReward(String id) {
        if (id == null) {
            return false;
        }
        for (PityEntry e : getPityPool()) {
            if (e.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** 保底池条目：奖项 id + 独立权重。 */
    public static class PityEntry {
        public final String id;
        public final int weight;

        public PityEntry(String id, int weight) {
            this.id = id;
            this.weight = Math.max(1, weight);
        }
    }

    // ================================================================
    //  抽奖前置条件（require）
    // ================================================================

    /**
     * 解析全局抽奖前置条件。
     * <p>当 permission / level / item 全部为空或 0 时视为「无限制」，返回 null。</p>
     */
    public Requirement getDrawRequirement() {
        ConfigurationSection sec = config.getConfigurationSection("require");
        if (sec == null) {
            return null;
        }
        String perm = sec.getString("permission", "");
        int level = sec.getInt("level", 0);
        String mat = sec.getString("item.material", "");
        int amt = Math.max(1, sec.getInt("item.amount", 1));
        boolean hasPerm = perm != null && !perm.isEmpty();
        boolean hasLevel = level > 0;
        boolean hasItem = mat != null && !mat.isEmpty();
        if (!hasPerm && !hasLevel && !hasItem) {
            return null;
        }
        return new Requirement(hasPerm ? perm : "", level, hasItem ? mat : "", amt);
    }

    /**
     * 抽奖前置条件：需持有某权限、需达到指定经验等级、需在背包中持有指定物品。
     * 任意一项不满足即禁止抽奖。
     */
    public static class Requirement {
        public final String permission;
        public final int level;
        public final String itemMaterial;
        public final int itemAmount;

        public Requirement(String permission, int level, String itemMaterial, int itemAmount) {
            this.permission = permission == null ? "" : permission;
            this.level = level;
            this.itemMaterial = itemMaterial == null ? "" : itemMaterial;
            this.itemAmount = itemAmount;
        }

        /** 将条件拼接为可读文本，用于向玩家提示缺失项。 */
        public String describe() {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (permission != null && !permission.isEmpty()) {
                parts.add("权限 " + permission);
            }
            if (level > 0) {
                parts.add("等级 " + level);
            }
            if (itemMaterial != null && !itemMaterial.isEmpty()) {
                parts.add(itemMaterial + " x" + itemAmount);
            }
            return String.join(" / ", parts);
        }
    }

    // ================================================================
    //  限时 / 节日奖池
    // ================================================================

    /**
     * 判断指定奖池在给定时刻是否处于生效窗口内。
     * 未配置 start/end 的奖池视为常驻（始终生效）。
     */
    public boolean isPoolActive(String name, long nowMillis) {
        ConfigurationSection sec = config.getConfigurationSection(
                "pools." + resolvePoolName(name));
        if (sec == null) {
            return false;
        }
        // 管理员禁用的奖池不生效
        if (!sec.getBoolean("enabled", true)) {
            return false;
        }
        String start = sec.getString("start", "");
        String end = sec.getString("end", "");
        if ((start == null || start.isEmpty()) && (end == null || end.isEmpty())) {
            return true;
        }
        long startMs = parseWindow(start);
        long endMs = parseWindow(end);
        if (startMs > 0 && nowMillis < startMs) {
            return false;
        }
        if (endMs > 0 && nowMillis > endMs) {
            return false;
        }
        return true;
    }

    /** 当前生效（未被限时窗口排除）的全部奖池名称。 */
    public List<String> getActivePoolNames() {
        long now = System.currentTimeMillis();
        List<String> result = new ArrayList<>();
        for (String name : getPoolNames()) {
            if (isPoolActive(name, now)) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * 奖池限时状态：active（生效中）/ ended（已结束）/ upcoming（未开始）。
     * 未配置时间窗口的奖池恒为 active。
     */
    public String getPoolStatus(String name) {
        ConfigurationSection sec = config.getConfigurationSection("pools." + name);
        if (sec == null) {
            return "active";
        }
        String start = sec.getString("start", "");
        String end = sec.getString("end", "");
        if ((start == null || start.isEmpty()) && (end == null || end.isEmpty())) {
            return "active";
        }
        long now = System.currentTimeMillis();
        long startMs = parseWindow(start);
        long endMs = parseWindow(end);
        if (endMs > 0 && now > endMs) {
            return "ended";
        }
        if (startMs > 0 && now < startMs) {
            return "upcoming";
        }
        return "active";
    }

    /** 解析 yyyy-MM-dd HH:mm 时间窗口；解析失败返回 -1（视为不限制该端点）。 */
    private long parseWindow(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            fmt.setLenient(false);
            return fmt.parse(s.trim()).getTime();
        } catch (Exception e) {
            plugin.getLogger().warning("[lottery.yml] 限时奖池时间解析失败: " + s);
            return -1;
        }
    }

    // ================================================================
    //  抽奖算法（委托给纯逻辑工具，便于测试）
    // ================================================================

    /**
     * 依据权重随机抽取一个奖项；奖池为空时返回 null。
     */
    public RewardDef rollReward(List<RewardDef> pool) {
        return LotteryMath.roll(pool, new Random());
    }

    // ================================================================
    //  音效与动画效果
    // ================================================================

    /**
     * 抽奖/领取/大额中奖的音效、粒子、标题效果配置。
     */
    public static class EffectConfig {
        // 抽奖成功
        public String drawSound = "ENTITY_EXPERIENCE_ORB_PICKUP";
        public float drawSoundVolume = 1.0f;
        public float drawSoundPitch = 1.0f;
        public boolean drawParticlesEnabled = true;
        public String drawParticleType = "VILLAGER_HAPPY";
        public int drawParticleCount = 15;
        public double drawParticleOffsetX = 0.5;
        public double drawParticleOffsetY = 1.0;
        public double drawParticleOffsetZ = 0.5;
        public double drawParticleExtra = 0.1;

        // 领取成功
        public String claimSound = "ENTITY_ITEM_PICKUP";
        public float claimSoundVolume = 1.0f;
        public float claimSoundPitch = 1.2f;

        // 大额中奖（触发广播时）
        public boolean bigWinTitleEnabled = true;
        public String bigWinTitle = "&6&l恭喜中奖！";
        public String bigWinSubtitle = "&e获得 {reward}";
        public int bigWinFadeIn = 10;
        public int bigWinStay = 40;
        public int bigWinFadeOut = 10;
        public String bigWinSound = "ENTITY_ENDERDRAGON_GROWL";
        public float bigWinSoundVolume = 1.0f;
        public float bigWinSoundPitch = 0.8f;
    }

    /**
     * 读取音效与动画效果配置；配置缺失时返回默认值。
     */
    public EffectConfig getEffects() {
        EffectConfig cfg = new EffectConfig();
        ConfigurationSection effects = config.getConfigurationSection("effects");
        if (effects == null) {
            return cfg;
        }

        // 抽奖
        cfg.drawSound = effects.getString("draw.sound", cfg.drawSound);
        cfg.drawSoundVolume = (float) effects.getDouble("draw.volume", cfg.drawSoundVolume);
        cfg.drawSoundPitch = (float) effects.getDouble("draw.pitch", cfg.drawSoundPitch);
        cfg.drawParticlesEnabled = effects.getBoolean("draw.particles.enabled", cfg.drawParticlesEnabled);
        cfg.drawParticleType = effects.getString("draw.particles.type", cfg.drawParticleType);
        cfg.drawParticleCount = effects.getInt("draw.particles.count", cfg.drawParticleCount);
        cfg.drawParticleOffsetX = effects.getDouble("draw.particles.offset-x", cfg.drawParticleOffsetX);
        cfg.drawParticleOffsetY = effects.getDouble("draw.particles.offset-y", cfg.drawParticleOffsetY);
        cfg.drawParticleOffsetZ = effects.getDouble("draw.particles.offset-z", cfg.drawParticleOffsetZ);
        cfg.drawParticleExtra = effects.getDouble("draw.particles.extra", cfg.drawParticleExtra);

        // 领取
        cfg.claimSound = effects.getString("claim.sound", cfg.claimSound);
        cfg.claimSoundVolume = (float) effects.getDouble("claim.volume", cfg.claimSoundVolume);
        cfg.claimSoundPitch = (float) effects.getDouble("claim.pitch", cfg.claimSoundPitch);

        // 大额中奖
        cfg.bigWinTitleEnabled = effects.getBoolean("big-win.title-enabled", cfg.bigWinTitleEnabled);
        cfg.bigWinTitle = effects.getString("big-win.title", cfg.bigWinTitle);
        cfg.bigWinSubtitle = effects.getString("big-win.subtitle", cfg.bigWinSubtitle);
        cfg.bigWinFadeIn = effects.getInt("big-win.fade-in", cfg.bigWinFadeIn);
        cfg.bigWinStay = effects.getInt("big-win.stay", cfg.bigWinStay);
        cfg.bigWinFadeOut = effects.getInt("big-win.fade-out", cfg.bigWinFadeOut);
        cfg.bigWinSound = effects.getString("big-win.sound", cfg.bigWinSound);
        cfg.bigWinSoundVolume = (float) effects.getDouble("big-win.volume", cfg.bigWinSoundVolume);
        cfg.bigWinSoundPitch = (float) effects.getDouble("big-win.pitch", cfg.bigWinSoundPitch);

        return cfg;
    }
}
