package soys.soysoceanbox.command.sub.admin;

import org.bukkit.command.CommandSender;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /soceanboxadmin delete &lt;玩家&gt; —— 从全部存储后端彻底删除该玩家的抽奖档案。
 */
public class AdminDeleteSub extends SubCommand {

    public AdminDeleteSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("del", "shanchu", "删除");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.delete";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            msg(sender, "admin.delete.usage", Placeholders.of("label", label).build());
            return;
        }
        UUID uuid = resolveUuid(args[0]);
        if (uuid == null) {
            msg(sender, "admin.player-offline", Placeholders.of("player", args[0]).build());
            return;
        }
        // 先移除在线缓存
        plugin.getLotteryManager().getCached(uuid);
        plugin.getLotteryManager().forget(uuid);
        plugin.getStorageManager().deletePlayerAsync(uuid);
        Text.send(sender, plugin.getMessageManager().get("admin.delete.success",
                Placeholders.of("player", args[0]).build()));
    }

    private UUID resolveUuid(String name) {
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
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
