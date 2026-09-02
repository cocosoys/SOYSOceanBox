package soys.soysoceanbox.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryManager;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.util.Arrays;
import java.util.List;

/**
 * /soceanbox stats —— 查看个人抽奖档案（总次数、今日/本周、保底进度、冷却、待领取）。
 */
public class StatsSub extends SubCommand {

    public StatsSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("tongji", "统计", "me");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        LotteryPlayer lp = plugin.getLotteryManager().getOrLoad(player);
        LotteryManager mgr = plugin.getLotteryManager();

        int dailyLeft = mgr.getDailyRemaining(lp);
        int weeklyLeft = mgr.getWeeklyRemaining(lp);
        int pityLeft = mgr.getPityRemaining(lp);
        long cooldown = mgr.getCooldownRemainingMillis(lp);

        Text.send(player, plugin.getMessageManager().get("lottery.stats.header"));
        msg(player, "lottery.stats.total", Placeholders.of("total", lp.getTotalDraws()).build());
        msg(player, "lottery.stats.daily", Placeholders.of("daily", mgr.getDailyCountNow(lp))
                .and("daily_left", dailyLeft < 0 ? "∞" : String.valueOf(dailyLeft)).build());
        msg(player, "lottery.stats.weekly", Placeholders.of("weekly", mgr.getWeeklyCountNow(lp))
                .and("weekly_left", weeklyLeft < 0 ? "∞" : String.valueOf(weeklyLeft)).build());
        msg(player, "lottery.stats.pity", Placeholders.of("pity", pityLeft < 0 ? "∞" : String.valueOf(pityLeft)).build());
        msg(player, "lottery.stats.cooldown",
                Placeholders.of("cooldown", LotteryManager.formatRemaining(cooldown)).build());
        msg(player, "lottery.stats.pending", Placeholders.of("pending", lp.getPending().size()).build());
        msg(player, "lottery.stats.history", Placeholders.of("history", lp.getHistory().size()).build());
        Text.send(player, plugin.getMessageManager().get("lottery.stats.footer"));
    }
}
