package soys.soysoceanbox.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 配置文件自动升级工具（纯逻辑，不依赖 Bukkit/插件，便于单元测试）。
 * <p>
 * 当磁盘上的用户配置版本低于目标版本时，将 jar 内置默认配置中「用户缺失」的键
 * 合并进用户配置，并升级 {@code config-version}；用户已存在的键与值一律保留，
 * 不会覆盖。列表型键（如奖池）仅在用户完全缺失该键时整体复制，不做逐项拼接，
 * 以避免破坏用户已有的列表结构。
 * </p>
 */
public final class ConfigUpgrader {

    private ConfigUpgrader() {
    }

    /**
     * 将默认配置中用户缺失的键合并到用户配置，并把版本号提升到 targetVersion。
     *
     * @param user         用户当前配置（会被就地修改）
     * @param defaults     jar 内置的默认配置（目标版本）
     * @param targetVersion 期望的配置版本号
     * @return 是否发生了任何变更（调用方据此决定是否落盘）
     */
    public static boolean mergeDefaults(FileConfiguration user, FileConfiguration defaults, int targetVersion) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            // 列表型键：用户缺失时整体复制；存在则保留用户版本
            if (defaults.isList(key)) {
                if (!user.contains(key)) {
                    user.set(key, defaults.get(key));
                    changed = true;
                }
                continue;
            }
            // 列表内部的叶子键由上面的整体复制处理，跳过以免逐条注入破坏结构
            String parent = parentPath(key);
            if (parent != null && (defaults.isList(parent) || user.isList(parent))) {
                continue;
            }
            // 标量键：用户缺失时补默认值
            if (!user.contains(key)) {
                user.set(key, defaults.get(key));
                changed = true;
            }
        }
        if (user.getInt("config-version", 0) < targetVersion) {
            user.set("config-version", targetVersion);
            changed = true;
        }
        return changed;
    }

    private static String parentPath(String key) {
        int idx = key.lastIndexOf('.');
        return idx <= 0 ? null : key.substring(0, idx);
    }
}
