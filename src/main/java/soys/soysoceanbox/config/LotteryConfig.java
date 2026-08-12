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
import java.util.*;

/**
 * 抽奖核心规则配置（lottery.yml）访问器。
 * <p>与 config.yml 完全分离，集中收敛抽奖冷却、消耗、奖池、上限、保底等读取，
 * 便于单独维护奖池与消耗策略，且支持热重载。</p>
 */
public class LotteryConfig {

    private final SOYSOceanBox plugin;
    private FileConfiguration config;

    /** lottery.yml 当前期望的 config-version，升级时合并到此版本。 */
    private static final int EXPECTED_VERSION = 2;

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
        if (defaults != null && ConfigUpgrader.mergeDefaults(user, defaults, EXPECTED_VERSION)) {
            try {
                user.save(file);
                plugin.getLogger().info("[lottery.yml] 已自动合并新增配置项并升级到 config-version "
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

    /** 按名称读取奖池；名称无效时回退到默认奖池 */
    public List<RewardDef> getRewardPool(String name) {
        if (name == null || name.isEmpty()) {
            name = getDefaultPoolName();
        }
        ConfigurationSection section = config.getConfigurationSection("pools." + name);
        if (section == null) {
            if (!getDefaultPoolName().equals(name)) {
                section = config.getConfigurationSection("pools." + getDefaultPoolName());
            }
            if (section == null) {
                return Collections.emptyList();
            }
        }
        List<RewardDef> pool = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            RewardDef def = RewardDef.fromSection(id, section.getConfigurationSection(id));
            if (def != null) {
                pool.add(def);
            }
        }
        return pool;
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
    //  抽奖算法（委托给纯逻辑工具，便于测试）
    // ================================================================

    /**
     * 依据权重随机抽取一个奖项；奖池为空时返回 null。
     */
    public RewardDef rollReward(List<RewardDef> pool) {
        return LotteryMath.roll(pool, new Random());
    }
}
