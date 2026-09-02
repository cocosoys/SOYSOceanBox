package soys.soysoceanbox;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import soys.soysoceanbox.lottery.LotteryPlayer;

/**
 * PlaceholderAPI 变量扩展。
 * <p>可用变量：
 *   %soysoceanbox_pending%        待领取奖励数量
 *   %soysoceanbox_total_draws%    累计抽奖次数
 *   %soysoceanbox_last_draw%      上次抽奖时间戳（毫秒）
 *   %soysoceanbox_daily_left%     今日剩余可抽次数（未启用上限时为 ∞）
 *   %soysoceanbox_weekly_left%    本周剩余可抽次数（未启用上限时为 ∞）
 *   %soysoceanbox_daily_count%    今日已抽次数
 *   %soysoceanbox_weekly_count%   本周已抽次数
 *   %soysoceanbox_active_pool%    当前生效奖池名
 *   %soysoceanbox_pity_left%      距触发保底还差次数（未启用保底时为 ∞）
 *   %soysoceanbox_cooldown_left%  冷却剩余秒数（向上取整）
 *   %soysoceanbox_cooldown_left_ms% 冷却剩余毫秒
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
        soys.soysoceanbox.config.LotteryConfig cfg = plugin.getLotteryConfig();
        switch (params.toLowerCase()) {
            case "pending":
                return String.valueOf(lp.getPending().size());
            case "total_draws":
                return String.valueOf(lp.getTotalDraws());
            case "last_draw":
                return String.valueOf(lp.getLastDrawTime());
            case "daily_left": {
                int r = plugin.getLotteryManager().getDailyRemaining(lp);
                return r < 0 ? "∞" : String.valueOf(r);
            }
            case "weekly_left": {
                int r = plugin.getLotteryManager().getWeeklyRemaining(lp);
                return r < 0 ? "∞" : String.valueOf(r);
            }
            case "daily_count":
                return String.valueOf(plugin.getLotteryManager().getDailyCountNow(lp));
            case "weekly_count":
                return String.valueOf(plugin.getLotteryManager().getWeeklyCountNow(lp));
            case "active_pool":
                return lp.getActivePool().isEmpty() ? cfg.getDefaultPoolName() : lp.getActivePool();
            case "pity_left": {
                int r = plugin.getLotteryManager().getPityRemaining(lp);
                return r < 0 ? "∞" : String.valueOf(r);
            }
            case "cooldown_left": {
                long ms = plugin.getLotteryManager().getCooldownRemainingMillis(lp);
                return String.valueOf((long) Math.ceil(ms / 1000.0));
            }
            case "cooldown_left_ms":
                return String.valueOf(plugin.getLotteryManager().getCooldownRemainingMillis(lp));
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
