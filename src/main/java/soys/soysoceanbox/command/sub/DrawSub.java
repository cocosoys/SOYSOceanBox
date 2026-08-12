package soys.soysoceanbox.command.sub;

import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryManager;
import soys.soysoceanbox.util.Placeholders;

import java.util.List;

/**
 * /soceanbox draw —— 消耗指定货币进行抽奖，结果进入待领取列表。
 */
public class DrawSub extends SubCommand {

    public DrawSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "draw";
    }

    @Override
    public List<String> getAliases() {
        return java.util.Arrays.asList("roll", "chou", "抽奖");
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.draw";
    }

    @Override
    public void execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        LotteryManager.DrawResult result = plugin.getLotteryManager().draw(player);

        switch (result.status) {
            case OK:
                msg(player, "lottery.draw.success",
                        Placeholders.of("reward", result.reward.getDisplay()).build());
                break;
            case COOLDOWN:
                long seconds = (long) Math.ceil(result.remainMillis / 1000.0);
                msg(player, "lottery.draw.cooldown", Placeholders.of("seconds", seconds).build());
                break;
            case NO_MONEY:
                msg(player, "lottery.draw.no-money",
                        Placeholders.of("amount",
                                String.valueOf(plugin.getLotteryConfig().getCostAmount())).build());
                break;
            case NO_POINTS:
                msg(player, "lottery.draw.no-points",
                        Placeholders.of("amount",
                                String.valueOf((int) Math.floor(plugin.getLotteryConfig().getCostAmount()))).build());
                break;
            case NO_VAULT:
                msg(player, "lottery.draw.no-vault", null);
                break;
            case NO_PLAYERPOINTS:
                msg(player, "lottery.draw.no-playerpoints", null);
                break;
            case DAILY_LIMIT:
                long leftSec = (long) Math.ceil(result.remainMillis / 1000.0);
                msg(player, "lottery.draw.daily-limit",
                        Placeholders.of("limit", plugin.getLotteryConfig().getDailyLimit())
                                .and("seconds", leftSec).build());
                break;
            case WEEKLY_LIMIT:
                msg(player, "lottery.draw.weekly-limit",
                        Placeholders.of("limit", plugin.getLotteryConfig().getWeeklyLimit()).build());
                break;
            case NO_POOL:
                msg(player, "lottery.draw.no-pool", null);
                break;
            case ERROR:
            default:
                msg(player, "lottery.draw.error", null);
                break;
        }
    }
}
