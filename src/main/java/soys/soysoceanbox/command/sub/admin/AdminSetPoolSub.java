package soys.soysoceanbox.command.sub.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.util.Arrays;
import java.util.List;

/**
 * /soceanboxadmin setpool &lt;玩家&gt; &lt;奖池名&gt; —— 为玩家指定其使用的奖池。
 */
public class AdminSetPoolSub extends SubCommand {

    public AdminSetPoolSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "setpool";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("poolset", "zhiding", "指定奖池");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.setpool";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msg(sender, "admin.setpool.usage", Placeholders.of("label", label).build());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            msg(sender, "admin.player-offline", Placeholders.of("player", args[0]).build());
            return;
        }
        String pool = args[1];
        if (!plugin.getLotteryConfig().getPoolNames().contains(pool)) {
            msg(sender, "admin.setpool.unknown",
                    Placeholders.of("pool", pool).and("pools",
                            String.join(", ", plugin.getLotteryConfig().getPoolNames())).build());
            return;
        }
        LotteryPlayer lp = plugin.getLotteryManager().getOrLoad(target);
        lp.setActivePool(pool);
        plugin.getLotteryManager().save(lp);
        Text.send(sender, plugin.getMessageManager().get("admin.setpool.success",
                Placeholders.of("player", target.getName()).and("pool", pool).build()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return onlinePlayerNames(args[0]);
        }
        if (args.length == 2) {
            return filter(plugin.getLotteryConfig().getPoolNames(), args[1].toLowerCase());
        }
        return java.util.Collections.emptyList();
    }
}
