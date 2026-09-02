package soys.soysoceanbox.command.sub;

import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryManager;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.lottery.PendingReward;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.util.List;

/**
 * /soceanbox list —— 查看待领取奖励列表。
 */
public class ListSub extends SubCommand {

    public ListSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public List<String> getAliases() {
        return java.util.Arrays.asList("pending", "daichong", "待领取");
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.use";
    }

    @Override
    public void execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        LotteryPlayer lp = plugin.getLotteryManager().getOrLoad(player);
        long now = System.currentTimeMillis();
        int pruned = plugin.getLotteryManager().pruneExpired(lp, now);
        if (pruned > 0) {
            plugin.getLotteryManager().save(lp);
            if (pruned > 0) {
                msg(player, "lottery.claim.expired", Placeholders.of("count", pruned).build());
            }
        }
        List<PendingReward> pending = lp.getPending();

        if (pending.isEmpty()) {
            msg(player, "lottery.list.empty", null);
            return;
        }

        Text.send(sender, plugin.getMessageManager().get("lottery.list.header"));
        int index = 1;
        for (PendingReward reward : pending) {
            String expire = "";
            if (reward.getExpireAt() > 0) {
                long left = reward.getExpireAt() - now;
                expire = LotteryManager.formatRemaining(Math.max(0, left));
                expire = " &7(&e剩余 " + expire + "&7)";
            }
            Text.send(sender, plugin.getMessageManager().get("lottery.list.entry",
                    Placeholders.of("index", index).and("reward", reward.getDisplay())
                            .and("expire", expire).build()));
            index++;
        }
        Text.send(sender, plugin.getMessageManager().get("lottery.list.footer",
                Placeholders.of("count", pending.size()).build()));
    }
}
