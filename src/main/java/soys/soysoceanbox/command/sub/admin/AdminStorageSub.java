package soys.soysoceanbox.command.sub.admin;

import org.bukkit.command.CommandSender;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.storage.DataStorage;
import soys.soysoceanbox.storage.StorageType;
import soys.soysoceanbox.util.Text;

import java.util.Arrays;
import java.util.List;

/**
 * /soceanboxadmin storage —— 展示各存储后端的启用状态、主/辅角色与玩家数量。
 */
public class AdminStorageSub extends SubCommand {

    public AdminStorageSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "storage";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("store", "cunchu", "存储");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.storage";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        Text.send(sender, plugin.getMessageManager().get("admin.storage.header"));
        DataStorage primary = plugin.getStorageManager().getPrimary();
        Text.send(sender, plugin.getMessageManager().get("admin.storage.primary",
                java.util.Collections.singletonMap("backend",
                        primary == null ? "无" : primary.getType().getDisplayName())));
        for (StorageType type : StorageType.values()) {
            DataStorage storage = plugin.getStorageManager().getStorage(type);
            if (storage == null) {
                Text.send(sender, plugin.getMessageManager().get("admin.storage.line",
                        java.util.Collections.singletonMap("line",
                                type.getDisplayName() + " &7- 未启用")));
                continue;
            }
            String role = storage == primary ? "&a主存储" : "&7辅助存储";
            int count;
            try {
                count = storage.countPlayers();
            } catch (Exception e) {
                count = -1;
            }
            Text.send(sender, plugin.getMessageManager().get("admin.storage.line",
                    java.util.Collections.singletonMap("line",
                            type.getDisplayName() + " &7(" + storage.describe() + ") &f- "
                                    + role + " &7- 玩家 " + count)));
        }
        Text.send(sender, plugin.getMessageManager().get("admin.storage.footer"));
    }
}
