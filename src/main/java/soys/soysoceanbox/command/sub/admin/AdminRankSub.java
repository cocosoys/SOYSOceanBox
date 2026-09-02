package soys.soysoceanbox.command.sub.admin;

import org.bukkit.command.CommandSender;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryMath;
import soys.soysoceanbox.lottery.LotteryPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /soceanboxadmin rank [daily|weekly|all] [数量] —— 抽奖排行榜。
 * <p>all 按累计抽奖次数排序；daily/weekly 仅统计当前日/周窗口内的次数（跨日/跨周视为 0）。
 * 遍历主存储全量玩家，只读不写。</p>
 */
public class AdminRankSub extends SubCommand {

    public AdminRankSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "rank";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("leaderboard", "top", "paihang", "排行榜");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.rank";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        String scope = args.length >= 1 ? args[0].toLowerCase() : "all";
        if (!scope.equals("daily") && !scope.equals("weekly") && !scope.equals("all")) {
            msg(sender, "admin.rank.usage", Collections.singletonMap("label", label));
            return;
        }
        int top = 10;
        if (args.length >= 2) {
            try {
                top = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                // 解析失败则用默认值
            }
        }
        top = Math.max(1, Math.min(top, 100));

        List<LotteryPlayer> players = new ArrayList<>(plugin.getStorageManager().getAllPlayersSafe());
        final String todayKey = LotteryMath.dailyKey();
        final String weekKey = LotteryMath.weeklyKey();

        Comparator<LotteryPlayer> cmp;
        switch (scope) {
            case "daily":
                cmp = Comparator.comparingInt(
                        (LotteryPlayer lp) -> todayKey.equals(lp.getDailyKey()) ? lp.getDailyCount() : 0).reversed();
                break;
            case "weekly":
                cmp = Comparator.comparingInt(
                        (LotteryPlayer lp) -> weekKey.equals(lp.getWeeklyKey()) ? lp.getWeeklyCount() : 0).reversed();
                break;
            default:
                cmp = Comparator.comparingInt(LotteryPlayer::getTotalDraws).reversed();
        }
        players.sort(cmp);
        if (players.size() > top) {
            players = players.subList(0, top);
        }

        msg(sender, "admin.rank.header");
        if (players.isEmpty()) {
            msg(sender, "admin.rank.empty", null);
            return;
        }

        int rank = 1;
        for (LotteryPlayer lp : players) {
            int value;
            switch (scope) {
                case "daily":
                    value = todayKey.equals(lp.getDailyKey()) ? lp.getDailyCount() : 0;
                    break;
                case "weekly":
                    value = weekKey.equals(lp.getWeeklyKey()) ? lp.getWeeklyCount() : 0;
                    break;
                default:
                    value = lp.getTotalDraws();
            }
            Map<String, String> entry = new HashMap<>();
            entry.put("rank", String.valueOf(rank));
            entry.put("player", lp.getName() == null ? "?" : lp.getName());
            entry.put("value", String.valueOf(value));
            msg(sender, "admin.rank.entry", entry);
            rank++;
        }

        Map<String, String> footer = new HashMap<>();
        footer.put("scope", scope);
        footer.put("count", String.valueOf(players.size()));
        msg(sender, "admin.rank.footer", footer);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("daily", "weekly", "all"), args[0]);
        }
        return Collections.emptyList();
    }
}
