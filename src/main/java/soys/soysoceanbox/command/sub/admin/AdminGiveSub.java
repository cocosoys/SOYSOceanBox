package soys.soysoceanbox.command.sub.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.lottery.PendingReward;
import soys.soysoceanbox.lottery.RewardDef;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.util.Arrays;
import java.util.List;

/**
 * /soceanboxadmin give &lt;玩家&gt; &lt;奖池id|money|points|item&gt; [数量] [材质]
 * <p>直接向目标玩家发放一条待领取奖励。</p>
 */
public class AdminGiveSub extends SubCommand {

    public AdminGiveSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("fa", "发放");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.give";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msg(sender, "admin.give.usage", Placeholders.of("label", label).build());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            msg(sender, "admin.player-offline", Placeholders.of("player", args[0]).build());
            return;
        }

        String kind = args[1].toLowerCase();
        PendingReward reward = buildReward(kind, args);
        if (reward == null) {
            msg(sender, "admin.give.unknown", Placeholders.of("kind", args[1]).build());
            return;
        }

        LotteryPlayer lp = plugin.getLotteryManager().getOrLoad(target);
        lp.addPending(reward);
        plugin.getLotteryManager().save(lp);

        Text.send(sender, plugin.getMessageManager().get("admin.give.success",
                Placeholders.of("player", target.getName())
                        .and("reward", reward.getDisplay()).build()));
        if (target != sender) {
            Text.send(target, plugin.getMessageManager().get("lottery.claim.given",
                    Placeholders.of("reward", reward.getDisplay()).build()));
        }
    }

    private PendingReward buildReward(String kind, String[] args) {
        PendingReward reward = new PendingReward();
        switch (kind) {
            case "money":
            case "coins": {
                double amount = args.length > 2 ? Text.parseInt(args[2], 0) : 0;
                if (amount <= 0) {
                    return null;
                }
                reward.setType(PendingReward.Type.MONEY);
                reward.setValue(amount);
                reward.setDisplay("&e" + amount + " 金币");
                break;
            }
            case "points": {
                int amount = args.length > 2 ? Text.parseInt(args[2], 0) : 0;
                if (amount <= 0) {
                    return null;
                }
                reward.setType(PendingReward.Type.POINTS);
                reward.setValue(amount);
                reward.setDisplay("&b" + amount + " 点券");
                break;
            }
            case "item": {
                String material = args.length > 2 ? args[2] : null;
                if (material == null) {
                    return null;
                }
                int amount = args.length > 3 ? Text.parseInt(args[3], 1) : 1;
                reward.setType(PendingReward.Type.ITEM);
                reward.setMaterial(material);
                reward.setItemAmount(amount);
                reward.setDisplay("&f" + material + " x" + amount);
                break;
            }
            default: {
                // 尝试按奖池中的奖项 id 发放
                for (RewardDef def : plugin.getLotteryConfig().getRewardPool()) {
                    if (def.getId().equalsIgnoreCase(kind)) {
                        return def.createPending();
                    }
                }
                return null;
            }
        }
        return reward;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return onlinePlayerNames(args[0]);
        }
        if (args.length == 2) {
            List<String> kinds = Arrays.asList("money", "points", "item");
            for (RewardDef def : plugin.getLotteryConfig().getRewardPool()) {
                kinds.add(def.getId());
            }
            return filter(kinds, args[1].toLowerCase());
        }
        return java.util.Collections.emptyList();
    }
}
