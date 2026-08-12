package soys.soysoceanbox.command.sub.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.util.Arrays;
import java.util.List;

/**
 * /soceanboxadmin reset &lt;玩家&gt; —— 清空该玩家的待领取奖励与计数（保留档案）。
 */
public class AdminResetSub extends SubCommand {

    public AdminResetSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "reset";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("clear", "qingkong", "清空");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.reset";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            msg(sender, "admin.reset.usage", Placeholders.of("label", label).build());
            return;
        }
        LotteryPlayer lp = loadOrCached(args[0]);
        if (lp == null) {
            msg(sender, "admin.player-offline", Placeholders.of("player", args[0]).build());
            return;
        }
        lp.getPending().clear();
        lp.setTotalDraws(0);
        lp.setLastDrawTime(0L);
        lp.setDailyCount(0);
        lp.setWeeklyCount(0);
        lp.setDrawsSinceBigWin(0);
        plugin.getLotteryManager().save(lp);
        Text.send(sender, plugin.getMessageManager().get("admin.reset.success",
                Placeholders.of("player", lp.getName()).build()));
    }

    private LotteryPlayer loadOrCached(String name) {
        org.bukkit.entity.Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return plugin.getLotteryManager().getOrLoad(online);
        }
        // 离线玩家从主存储加载
        try {
            java.util.UUID uuid = resolveUuid(name);
            if (uuid == null) {
                return null;
            }
            return plugin.getStorageManager().loadPlayer(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    private java.util.UUID resolveUuid(String name) {
        for (LotteryPlayer p : plugin.getStorageManager().getAllPlayersSafe()) {
            if (p.getName() != null && p.getName().equalsIgnoreCase(name)) {
                return p.getUuid();
            }
        }
        return null;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? onlinePlayerNames(args[0]) : java.util.Collections.emptyList();
    }
}
