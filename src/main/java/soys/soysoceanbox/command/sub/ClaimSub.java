package soys.soysoceanbox.command.sub;

import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryManager;
import soys.soysoceanbox.util.Placeholders;

import java.util.List;

/**
 * /soceanbox claim [all|序号|claimId前缀] —— 领取待领取奖励。
 */
public class ClaimSub extends SubCommand {

    public ClaimSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "claim";
    }

    @Override
    public List<String> getAliases() {
        return java.util.Arrays.asList("receive", "lingqu", "领取");
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.claim";
    }

    @Override
    public void execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        String target = args.length > 0 ? args[0] : null;
        LotteryManager.ClaimResult result = plugin.getLotteryManager().claim(player, target);

        if (result.claimed == 0 && result.failed == 0) {
            msg(player, "lottery.claim.empty", null);
            return;
        }

        if (result.claimed > 0) {
            msg(player, "lottery.claim.success", Placeholders.of("count", result.claimed).build());
        }
        if (result.failed > 0) {
            String items = String.join(", ", result.failures);
            msg(player, "lottery.claim.partial-failed",
                    Placeholders.of("count", result.failed).and("items", items).build());
        }
    }
}
