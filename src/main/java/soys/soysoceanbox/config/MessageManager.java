package soys.soysoceanbox.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.util.Text;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息文件（messages.yml）管理器。
 * <p>负责消息读取、占位符替换与发送。缺失的键会回退到 jar 内的默认值。</p>
 */
public class MessageManager {

    private static final String PREFIX_KEY = "prefix";

    private final SOYSOceanBox plugin;
    private FileConfiguration messages;
    private FileConfiguration defaults;
    private String prefix = "";

    public MessageManager(SOYSOceanBox plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
        this.defaults = loadInternalDefaults();
        if (defaults != null) {
            messages.setDefaults(defaults);
        }

        // 多语言叠加：若 general.language 指定了非默认语言，叠加 messages_<lang>.yml
        // （当前仅提供 zh_CN；其余语言文件不存在时自动回退到默认 messages.yml）
        overlayLanguageFile();

        this.prefix = messages.getString(PREFIX_KEY, "");
    }

    /**
     * 根据 config.yml 的 general.language 叠加对应语言文件。
     * 语言文件中的键会覆盖默认 messages.yml 的同名键；缺失键继续沿用默认。
     * <p>叠加范围同时覆盖字符串与列表（如 help 文本）。语言文件优先读取数据目录下
     * 的管理员可改副本，若不存在则回退到 jar 内内置副本（便于开箱即用）。</p>
     */
    private void overlayLanguageFile() {
        String lang = plugin.getConfigManager().getLanguage();
        if (lang == null || lang.isEmpty() || "zh_CN".equalsIgnoreCase(lang)) {
            return;
        }
        String fileName = "messages_" + lang + ".yml";
        FileConfiguration overlay = null;
        File langFile = new File(plugin.getDataFolder(), fileName);
        if (langFile.exists()) {
            overlay = YamlConfiguration.loadConfiguration(langFile);
        } else {
            try (InputStream in = plugin.getResource(fileName)) {
                if (in != null) {
                    overlay = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    plugin.getLogger().info("[i18n] 使用内置语言文件: " + fileName);
                }
            } catch (IOException ignored) {
                // 读取内置资源失败，忽略
            }
        }
        if (overlay == null) {
            plugin.getLogger().info("[i18n] 未找到语言文件 " + fileName + "，使用默认 zh_CN 消息。");
            return;
        }
        try {
            for (String key : overlay.getKeys(true)) {
                if (overlay.isConfigurationSection(key)) {
                    continue;
                }
                if (overlay.isList(key)) {
                    messages.set(key, overlay.getList(key));
                } else if (overlay.isString(key)) {
                    messages.set(key, overlay.getString(key));
                }
            }
            plugin.getLogger().info("[i18n] 已叠加语言: " + lang);
        } catch (Exception e) {
            plugin.getLogger().warning("[i18n] 加载语言文件失败: " + e.getMessage());
        }
    }

    private FileConfiguration loadInternalDefaults() {
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    // ================================================================
    //  读取
    // ================================================================

    /**
     * 读取一条消息（已完成前缀与占位符替换，未翻译颜色）。
     */
    public String get(String key, Map<String, String> placeholders) {
        String raw = messages.getString(key);
        if (raw == null) {
            return "&c[缺失消息: " + key + "]";
        }
        return apply(raw, placeholders);
    }

    public String get(String key) {
        return get(key, null);
    }

    /**
     * 读取一个消息列表。若该键实际是单条字符串，则包装为单元素列表。
     */
    public List<String> getList(String key, Map<String, String> placeholders) {
        List<String> raw = messages.getStringList(key);
        if (raw.isEmpty()) {
            String single = messages.getString(key);
            if (single == null) {
                List<String> missing = new ArrayList<>();
                missing.add("&c[缺失消息: " + key + "]");
                return missing;
            }
            raw = new ArrayList<>();
            raw.add(single);
        }
        List<String> result = new ArrayList<>(raw.size());
        for (String line : raw) {
            result.add(apply(line, placeholders));
        }
        return result;
    }

    public List<String> getList(String key) {
        return getList(key, null);
    }

    private String apply(String raw, Map<String, String> placeholders) {
        Map<String, String> merged = new HashMap<>();
        merged.put("prefix", prefix);
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        return Text.replace(raw, merged);
    }

    // ================================================================
    //  发送
    // ================================================================

    public void send(CommandSender sender, String key) {
        send(sender, key, null);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender == null) {
            return;
        }
        Text.send(sender, get(key, placeholders));
    }

    public void sendList(CommandSender sender, String key) {
        sendList(sender, key, null);
    }

    public void sendList(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender == null) {
            return;
        }
        Text.send(sender, getList(key, placeholders));
    }

    public String getPrefix() {
        return prefix;
    }
}
