package soys.soysoceanbox.command.sub.admin;

import org.bukkit.command.CommandSender;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.util.Arrays;
import java.util.List;

/**
 * /soceanboxadmin sync —— 将主存储全量数据覆盖同步到所有辅助存储。
 */
public class AdminSyncSub extends SubCommand {

    public AdminSyncSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "sync";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("tb", "tongbu", "同步");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.sync";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (plugin.getStorageManager().getSecondaries().isEmpty()) {
            msg(sender, "admin.sync.no-secondary", null);
            return;
        }
        Text.send(sender, plugin.getMessageManager().get("admin.sync.start", null));
        plugin.getStorageManager().submit(() -> {
            try {
                int count = plugin.getStorageManager().syncToSecondaries();
                Text.send(sender, plugin.getMessageManager().get("admin.sync.done",
                        Placeholders.of("count", count).build()));
            } catch (Exception e) {
                plugin.getLogger().severe("同步失败: " + e.getMessage());
                Text.send(sender, plugin.getMessageManager().get("admin.sync.failed",
                        Placeholders.of("error", e.getMessage()).build()));
            }
        });
    }
}
