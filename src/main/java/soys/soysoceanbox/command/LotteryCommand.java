package soys.soysoceanbox.command;

import org.bukkit.command.CommandSender;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.sub.ClaimSub;
import soys.soysoceanbox.command.sub.DrawSub;
import soys.soysoceanbox.command.sub.HelpSub;
import soys.soysoceanbox.command.sub.ListSub;
import soys.soysoceanbox.command.sub.PoolSub;

/**
 * 玩家抽奖指令主入口：/soceanbox
 * <p>子指令：draw（抽奖）、claim（领取）、list（待领取列表）、pool（奖池）、help（帮助）。</p>
 */
public class LotteryCommand extends CommandDispatcher {

    public LotteryCommand(SOYSOceanBox plugin) {
        super(plugin);
        register(new DrawSub(plugin));
        register(new ClaimSub(plugin));
        register(new ListSub(plugin));
        register(new PoolSub(plugin));
        register(new HelpSub(plugin));
    }

    @Override
    protected void showDefault(CommandSender sender, String label) {
        new HelpSub(plugin).execute(sender, label, new String[0]);
    }
}
