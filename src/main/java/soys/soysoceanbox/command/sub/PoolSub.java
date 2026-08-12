package soys.soysoceanbox.command.sub;

import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.util.List;

/**
 * /soceanbox pool [奖池名] —— 查看可用奖池，或在允许时切换到指定奖池。
 */
public class PoolSub extends SubCommand {

    public PoolSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "pool";
    }

    @Override
    public List<String> getAliases() {
        return java.util.Arrays.asList("pools", "jiangchi", "奖池");
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.pool";
    }

    @Override
    public void execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            showPools(player);
            return;
        }
        if (!plugin.getLotteryConfig().isPlayerSwitchAllowed()) {
            msg(player, "lottery.pool.locked", null);
            return;
        }
        String target = args[0];
        if (!plugin.getLotteryConfig().getPoolNames().contains(target)) {
            msg(player, "lottery.pool.unknown",
                    Placeholders.of("pool", target).and("pools",
                            String.join(", ", plugin.getLotteryConfig().getPoolNames())).build());
            return;
        }
        LotteryPlayer lp = plugin.getLotteryManager().getOrLoad(player);
        lp.setActivePool(target);
        plugin.getLotteryManager().save(lp);
        msg(player, "lottery.pool.switched", Placeholders.of("pool", target).build());
    }

    private void showPools(Player player) {
        LotteryPlayer lp = plugin.getLotteryManager().getOrLoad(player);
        Text.send(player, plugin.getMessageManager().get("lottery.pool.header"));
        for (String name : plugin.getLotteryConfig().getPoolNames()) {
            String mark = name.equals(lp.getActivePool())
                    || (lp.getActivePool().isEmpty() && name.equals(plugin.getLotteryConfig().getDefaultPoolName()))
                    ? "&a*" : "&7 ";
            int size = plugin.getLotteryConfig().getRewardPool(name).size();
            Text.send(player, plugin.getMessageManager().get("lottery.pool.entry",
                    Placeholders.of("mark", mark).and("pool", name).and("count", size).build()));
        }
        Text.send(player, plugin.getMessageManager().get("lottery.pool.footer",
                Placeholders.of("current",
                        lp.getActivePool().isEmpty()
                                ? plugin.getLotteryConfig().getDefaultPoolName() : lp.getActivePool()).build()));
    }
}
