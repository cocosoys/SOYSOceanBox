package soys.soysoceanbox;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import soys.soysoceanbox.lottery.LotteryManager;

/**
 * 玩家上线时异步装入抽奖档案到内存缓存。
 */
public class PlayerListener implements Listener {

    private final SOYSOceanBox plugin;
    private final LotteryManager lotteryManager;

    public PlayerListener(SOYSOceanBox plugin) {
        this.plugin = plugin;
        this.lotteryManager = plugin.getLotteryManager();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lotteryManager.loadOnJoin(event.getPlayer());
    }
}
