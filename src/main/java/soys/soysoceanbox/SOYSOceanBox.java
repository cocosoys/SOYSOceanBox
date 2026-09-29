package soys.soysoceanbox;

import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import soys.soysoceanbox.command.AdminLotteryCommand;
import soys.soysoceanbox.command.CommandDispatcher;
import soys.soysoceanbox.command.LotteryCommand;
import soys.soysoceanbox.config.ConfigManager;
import soys.soysoceanbox.config.LotteryConfig;
import soys.soysoceanbox.config.MessageManager;
import soys.soysoceanbox.lottery.LotteryManager;
import soys.soysoceanbox.storage.StorageManager;
import soys.soysoceanbox.web.OceanBoxExpansion;

import java.util.logging.Level;

/**
 * SOYSOceanBox 主类。
 * <p>负责编排各模块的生命周期：配置、存储、抽奖逻辑、指令与 PlaceholderAPI 扩展。</p>
 */
public final class SOYSOceanBox extends JavaPlugin {

    private static SOYSOceanBox instance;

    private ConfigManager configManager;
    private LotteryConfig lotteryConfig;
    private MessageManager messageManager;
    private StorageManager storageManager;
    private LotteryManager lotteryManager;
    private PlaceholderHook placeholderHook;
    private OceanBoxExpansion webExpansion;

    public static SOYSOceanBox getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        try {
            configManager = new ConfigManager(this);
            lotteryConfig = new LotteryConfig(this);
            messageManager = new MessageManager(this);

            storageManager = new StorageManager(this);
            storageManager.initialize();

            lotteryManager = new LotteryManager(this);
            hookDependencies();

            registerCommands();
            registerListeners();
            registerPlaceholders();
            registerWebExpansion();

            getLogger().info("SOYSOceanBox 已启用，共加载 "
                    + storageManager.countPlayers() + " 名玩家档案。");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "SOYSOceanBox 启用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDisable() {
        if (webExpansion != null) {
            try {
                webExpansion.unregister();
            } catch (Throwable ignored) {
                // 卸载阶段忽略异常
            }
        }
        if (lotteryManager != null) {
            lotteryManager.saveAllBlocking();
        }
        if (storageManager != null) {
            storageManager.shutdown();
        }
        if (placeholderHook != null) {
            try {
                placeholderHook.unregister();
            } catch (Throwable ignored) {
                // 卸载阶段忽略异常
            }
        }
        getLogger().info("SOYSOceanBox 已停用。");
    }

    /**
     * 连接 Vault 经济与 PlayerPoints 点券 API。
     */
    private void hookDependencies() {
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null && rsp.getProvider() != null) {
            lotteryManager.setEconomy(rsp.getProvider());
            getLogger().info("已挂钩 Vault 经济提供者: " + rsp.getProvider().getName());
        } else {
            getLogger().warning("未检测到 Vault 经济提供者，涉及金币的奖励将无法发放。");
        }

        Plugin pp = (Plugin) getServer().getPluginManager().getPlugin("PlayerPoints");
        if (pp instanceof PlayerPoints) {
            PlayerPointsAPI api = ((PlayerPoints) pp).getAPI();
            lotteryManager.setPointsApi(api);
            getLogger().info("已挂钩 PlayerPoints 点券 API。");
        } else {
            getLogger().warning("未检测到 PlayerPoints，涉及点券的奖励将无法发放。");
        }
    }

    private void registerCommands() {
        registerCommand("soceanbox", new LotteryCommand(this));
        registerCommand("soceanboxadmin", new AdminLotteryCommand(this));
    }

    private void registerCommand(String name, CommandDispatcher dispatcher) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("未在 plugin.yml 中找到指令定义: " + name);
            return;
        }
        command.setExecutor(dispatcher);
        command.setTabCompleter(dispatcher);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    private void registerPlaceholders() {
        try {
            placeholderHook = new PlaceholderHook(this);
            placeholderHook.register();
            getLogger().info("PlaceholderAPI 变量已注册。");
        } catch (Throwable t) {
            getLogger().warning("PlaceholderAPI 变量注册失败: " + t.getMessage());
        }
    }

    /**
     * 注册 SOYSHTTPOverMC 网页扩展（管理 ERP + 用户中心）。
     * <p>框架插件不存在时静默跳过，游戏内功能不受影响。</p>
     */
    private void registerWebExpansion() {
        Plugin framework = getServer().getPluginManager().getPlugin("SOYSHTTPOverMC");
        if (framework == null) {
            getLogger().info("未检测到 SOYSHTTPOverMC，跳过网页扩展注册（游戏内功能不受影响）。");
            return;
        }
        try {
            webExpansion = new OceanBoxExpansion(this);
            if (webExpansion.register()) {
                getLogger().info("SOYSHTTPOverMC 网页扩展已注册（管理 ERP + 用户中心）。");
            } else {
                getLogger().warning("SOYSHTTPOverMC 网页扩展注册失败（identifier 冲突或框架未就绪）。");
            }
        } catch (Throwable t) {
            getLogger().warning("SOYSHTTPOverMC 网页扩展注册异常: " + t.getMessage());
        }
    }

    /**
     * 热重载配置与全部模块，供 /soceanboxadmin reload 调用。
     */
    public void reloadConfiguration() {
        // 边界处理：重载前先把内存中尚未落盘的玩家档案写回，避免丢失待领取奖励
        lotteryManager.saveAllBlocking();
        configManager.reload();
        lotteryConfig.reload();
        messageManager.reload();
        storageManager.initialize();
        lotteryManager.clearCache();
    }

    // ================================================================
    //  模块访问器
    // ================================================================

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LotteryConfig getLotteryConfig() {
        return lotteryConfig;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public LotteryManager getLotteryManager() {
        return lotteryManager;
    }
}
