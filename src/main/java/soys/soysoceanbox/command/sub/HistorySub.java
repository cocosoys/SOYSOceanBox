package soys.soysoceanbox.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryManager;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * /soceanbox history [数量] —— 查看个人最近的中奖记录（最新在前）。
 */
public class HistorySub extends SubCommand {

    private static final int MAX_ENTRIES = 100;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public HistorySub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "history";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("jilu", "记录", "record");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        LotteryPlayer lp = plugin.getLotteryManager().getOrLoad(player);
        List<LotteryPlayer.WinRecord> all = lp.getHistory();
        if (all.isEmpty()) {
            msg(player, "lottery.history.empty", null);
            return;
        }

        int limit = 10;
        if (args.length >= 1) {
            try {
                limit = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                // 解析失败使用默认
            }
        }
        limit = Math.max(1, Math.min(limit, MAX_ENTRIES));
        // 最新在前
        int from = Math.max(0, all.size() - limit);
        List<LotteryPlayer.WinRecord> recent = all.subList(from, all.size());

        Text.send(player, plugin.getMessageManager().get("lottery.history.header"));
        int index = 1;
        for (int i = recent.size() - 1; i >= 0; i--) {
            LotteryPlayer.WinRecord rec = recent.get(i);
            String time = FMT.format(new java.util.Date(rec.time));
            Text.send(player, plugin.getMessageManager().get("lottery.history.entry",
                    Placeholders.of("index", index)
                            .and("time", time)
                            .and("reward", rec.display)
                            .and("pool", rec.pool).build()));
            index++;
        }
        Text.send(player, plugin.getMessageManager().get("lottery.history.footer",
                Placeholders.of("count", all.size()).build()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("5", "10", "20", "50"), args[0]);
        }
        return Collections.emptyList();
    }
}
