package soys.soysoceanbox.storage.impl;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.lottery.PendingReward;
import soys.soysoceanbox.storage.DataStorage;
import soys.soysoceanbox.storage.StorageType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * YAML 文件存储后端。
 * <p>
 * 零外部依赖，默认启用。所有玩家写在同一个 players.yml 中，
 * 通过对象锁保证并发安全。适用于中小型服务器。
 * </p>
 */
public class YamlStorage implements DataStorage {

    private static final String ROOT = "players";

    private final SOYSOceanBox plugin;
    private final Object lock = new Object();

    private File file;
    private YamlConfiguration config;
    private boolean available = false;
    private boolean backupOnSave = false;

    public YamlStorage(SOYSOceanBox plugin) {
        this.plugin = plugin;
    }

    @Override
    public StorageType getType() {
        return StorageType.YAML;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfigManager().getBackendSection("yaml");
        String path = section == null ? "data/players.yml" : section.getString("file", "data/players.yml");
        this.backupOnSave = section != null && section.getBoolean("backup-on-save", false);

        this.file = new File(plugin.getDataFolder(), path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建数据目录: " + parent.getAbsolutePath());
        }
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("无法创建数据文件: " + file.getAbsolutePath());
        }
        synchronized (lock) {
            this.config = YamlConfiguration.loadConfiguration(file);
            if (!config.isConfigurationSection(ROOT)) {
                config.createSection(ROOT);
            }
        }
        this.available = true;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            try {
                if (config != null && file != null) {
                    config.save(file);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[YAML] 关闭时保存失败: " + e.getMessage());
            }
            available = false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String describe() {
        return file == null ? "未初始化" : file.getPath().replace('\\', '/');
    }

    // ================================================================
    //  读
    // ================================================================

    @Override
    public LotteryPlayer loadPlayer(UUID uuid) {
        synchronized (lock) {
            ConfigurationSection section = config.getConfigurationSection(ROOT + "." + uuid);
            return section == null ? null : deserialize(uuid, section);
        }
    }

    @Override
    public Collection<LotteryPlayer> loadAllPlayers() {
        synchronized (lock) {
            Collection<LotteryPlayer> players = new ArrayList<>();
            ConfigurationSection root = config.getConfigurationSection(ROOT);
            if (root == null) {
                return players;
            }
            for (String key : root.getKeys(false)) {
                UUID id = parseUuid(key);
                if (id == null) {
                    continue;
                }
                ConfigurationSection section = root.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                LotteryPlayer player = deserialize(id, section);
                if (player != null) {
                    players.add(player);
                }
            }
            return players;
        }
    }

    @Override
    public int countPlayers() {
        synchronized (lock) {
            ConfigurationSection root = config.getConfigurationSection(ROOT);
            return root == null ? 0 : root.getKeys(false).size();
        }
    }

    // ================================================================
    //  写
    // ================================================================

    @Override
    public void savePlayer(LotteryPlayer player) throws Exception {
        synchronized (lock) {
            serialize(player);
            flush();
        }
    }

    @Override
    public void savePlayers(Collection<LotteryPlayer> players) throws Exception {
        synchronized (lock) {
            for (LotteryPlayer player : players) {
                serialize(player);
            }
            flush();
        }
    }

    @Override
    public void deletePlayer(UUID uuid) throws Exception {
        synchronized (lock) {
            config.set(ROOT + "." + uuid, null);
            flush();
        }
    }

    @Override
    public void clear() throws Exception {
        synchronized (lock) {
            config.set(ROOT, null);
            config.createSection(ROOT);
            flush();
        }
    }

    // ================================================================
    //  内部
    // ================================================================

    private void flush() throws IOException {
        if (backupOnSave && file.exists()) {
            File backup = new File(file.getParentFile(), file.getName() + ".bak");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("[YAML] 备份失败: " + e.getMessage());
            }
        }
        config.save(file);
    }

    private void serialize(LotteryPlayer player) {
        String base = ROOT + "." + player.getUuid();
        config.set(base + ".name", player.getName());
        config.set(base + ".last_draw", player.getLastDrawTime());
        config.set(base + ".total_draws", player.getTotalDraws());
        config.set(base + ".daily_key", player.getDailyKey());
        config.set(base + ".daily_count", player.getDailyCount());
        config.set(base + ".weekly_key", player.getWeeklyKey());
        config.set(base + ".weekly_count", player.getWeeklyCount());
        config.set(base + ".draws_since_big_win", player.getDrawsSinceBigWin());
        config.set(base + ".active_pool", player.getActivePool());

        config.set(base + ".pending", null);
        for (PendingReward reward : player.getPending()) {
            reward.write(config.createSection(base + ".pending." + reward.getClaimId()));
        }

        config.set(base + ".history", null);
        if (!player.getHistory().isEmpty()) {
            List<String> encoded = new ArrayList<>();
            for (LotteryPlayer.WinRecord rec : player.getHistory()) {
                encoded.add(rec.encode());
            }
            config.set(base + ".history", encoded);
        }
    }

    private LotteryPlayer deserialize(UUID id, ConfigurationSection section) {
        String name = section.getString("name", "未知");
        LotteryPlayer player = new LotteryPlayer(id, name);
        player.setLastDrawTime(section.getLong("last_draw", 0L));
        player.setTotalDraws(section.getInt("total_draws", 0));
        player.setDailyKey(section.getString("daily_key", ""));
        player.setDailyCount(section.getInt("daily_count", 0));
        player.setWeeklyKey(section.getString("weekly_key", ""));
        player.setWeeklyCount(section.getInt("weekly_count", 0));
        player.setDrawsSinceBigWin(section.getInt("draws_since_big_win", 0));
        player.setActivePool(section.getString("active_pool", ""));

        ConfigurationSection pending = section.getConfigurationSection("pending");
        if (pending != null) {
            for (String key : pending.getKeys(false)) {
                ConfigurationSection rewardSection = pending.getConfigurationSection(key);
                if (rewardSection != null) {
                    player.addPending(PendingReward.read(rewardSection));
                }
            }
        }

        List<String> history = section.getStringList("history");
        if (history != null) {
            for (String encoded : history) {
                LotteryPlayer.WinRecord rec = LotteryPlayer.WinRecord.decode(encoded);
                if (rec != null) {
                    player.getHistory().add(rec);
                }
            }
        }
        return player;
    }

    private UUID parseUuid(String input) {
        if (input == null) {
            return null;
        }
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
