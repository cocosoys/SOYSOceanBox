package soys.soysoceanbox.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysoceanbox.SOYSOceanBox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 主配置文件（config.yml）访问器。
 * <p>集中收敛所有配置读取，避免各模块散落硬编码的配置路径。</p>
 */
public class ConfigManager {

    private final SOYSOceanBox plugin;
    private FileConfiguration config;

    /** config.yml 当前期望的 config-version，升级时合并到此版本。 */
    private static final int EXPECTED_VERSION = 2;

    public ConfigManager(SOYSOceanBox plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        upgradeConfig();
    }

    /**
     * 若用户 config.yml 的 config-version 落后，则自动合并 jar 默认配置中缺失的键
     * 并升级版本号（保留用户已配置的值）。
     */
    private void upgradeConfig() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration defaults = loadDefaults();
        if (defaults != null && ConfigUpgrader.mergeDefaults(this.config, defaults, EXPECTED_VERSION)) {
            try {
                this.config.save(file);
                plugin.getLogger().info("[config.yml] 已自动合并新增配置项并升级到 config-version "
                        + EXPECTED_VERSION);
            } catch (Exception e) {
                plugin.getLogger().warning("[config.yml] 升级保存失败: " + e.getMessage());
            }
        }
    }

    private FileConfiguration loadDefaults() {
        try (InputStream in = plugin.getResource("config.yml")) {
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
    //  通用
    // ================================================================

    public String getLanguage() {
        return config.getString("general.language", "zh_CN");
    }

    public boolean isDebug() {
        return config.getBoolean("general.debug", false);
    }

    // ================================================================
    //  存储后端
    // ================================================================

    public boolean isBackendEnabled(String backendId) {
        return config.getBoolean("storage.backends." + backendId + ".enabled", false);
    }

    public ConfigurationSection getBackendSection(String backendId) {
        return config.getConfigurationSection("storage.backends." + backendId);
    }

    public boolean isMirrorEnabled() {
        return config.getBoolean("storage.mirror.enabled", true);
    }

    public boolean isMirrorAsync() {
        return config.getBoolean("storage.mirror.async", true);
    }

    public boolean isSyncOnStartup() {
        return config.getBoolean("storage.mirror.sync-on-startup", false);
    }

    /** 是否启用跨服数据同步（多实例共享同一 MySQL 主库）。 */
    public boolean isCrossServer() {
        return config.getBoolean("storage.cross-server", false);
    }

    // ================================================================
    //  指令
    // ================================================================

    public boolean isTabComplete() {
        return config.getBoolean("command.tab-complete", true);
    }

    // ================================================================
    //  占位符
    // ================================================================

    public String getPlaceholderNoPending() {
        return config.getString("placeholder.no-pending", "&7暂无待领取奖励");
    }

    public String getDateFormat() {
        return config.getString("placeholder.date-format", "yyyy-MM-dd HH:mm");
    }
}
