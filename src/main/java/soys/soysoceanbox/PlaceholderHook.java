package soys.soysoceanbox;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import soys.soysoceanbox.lottery.LotteryPlayer;

/**
 * PlaceholderAPI 变量扩展。
 * <p>可用变量：
 *   %soysoceanbox_pending%      待领取奖励数量
 *   %soysoceanbox_total_draws%  累计抽奖次数
 *   %soysoceanbox_last_draw%    上次抽奖时间戳（毫秒）
 * </p>
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final SOYSOceanBox plugin;

    public PlaceholderHook(SOYSOceanBox plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "soysoceanbox";
    }

    @Override
    public String getAuthor() {
        return "SOYS";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return handle(player, params);
    }

    @Deprecated
    public String onRequest(Player player, String params) {
        return handle(player, params);
    }

    private String handle(Player player, String params) {
        if (player == null || params == null) {
            return null;
        }
        LotteryPlayer lp = plugin.getLotteryManager().getCached(player.getUniqueId());
        if (lp == null) {
            return null;
        }
        switch (params.toLowerCase()) {
            case "pending":
                return String.valueOf(lp.getPending().size());
            case "total_draws":
                return String.valueOf(lp.getTotalDraws());
            case "last_draw":
                return String.valueOf(lp.getLastDrawTime());
            default:
                return null;
        }
    }

    /**
     * 注册变量扩展。仅当 PlaceholderAPI 已加载时调用。
     */
    public boolean register() {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return super.register();
        }
        return false;
    }
}
