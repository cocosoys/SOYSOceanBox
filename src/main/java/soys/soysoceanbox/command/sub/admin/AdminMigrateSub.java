package soys.soysoceanbox.command.sub.admin;

import org.bukkit.command.CommandSender;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.storage.StorageType;
import soys.soysoceanbox.util.Placeholders;
import soys.soysoceanbox.util.Text;

import java.util.Arrays;
import java.util.List;

/**
 * /soceanboxadmin migrate &lt;来源&gt; &lt;目标&gt; [overwrite] —— 在两后端间迁移全量数据。
 */
public class AdminMigrateSub extends SubCommand {

    public AdminMigrateSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "migrate";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("move", "qianyi", "迁移");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.migrate";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msg(sender, "admin.migrate.usage", Placeholders.of("label", label).build());
            return;
        }
        StorageType from = StorageType.fromId(args[0]);
        StorageType to = StorageType.fromId(args[1]);
        if (from == null || to == null) {
            msg(sender, "admin.migrate.unknown", null);
            return;
        }
        if (from == to) {
            msg(sender, "admin.migrate.same", null);
            return;
        }
        boolean overwrite = args.length > 2 && "overwrite".equalsIgnoreCase(args[2]);
        Text.send(sender, plugin.getMessageManager().get("admin.migrate.start",
                Placeholders.of("from", from.getDisplayName()).and("to", to.getDisplayName()).build()));
        plugin.getStorageManager().submit(() -> {
            try {
                int count = plugin.getStorageManager().migrate(from, to, overwrite);
                Text.send(sender, plugin.getMessageManager().get("admin.migrate.done",
                        Placeholders.of("count", count).build()));
            } catch (Exception e) {
                plugin.getLogger().severe("迁移失败: " + e.getMessage());
                Text.send(sender, plugin.getMessageManager().get("admin.migrate.failed",
                        Placeholders.of("error", e.getMessage()).build()));
            }
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> backends = Arrays.asList("yaml", "sqlite", "mysql");
        if (args.length == 1) {
            return filter(backends, args[0].toLowerCase());
        }
        if (args.length == 2) {
            return filter(backends, args[1].toLowerCase());
        }
        return java.util.Collections.emptyList();
    }
}
