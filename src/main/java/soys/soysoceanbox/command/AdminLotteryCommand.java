package soys.soysoceanbox.command;

import org.bukkit.command.CommandSender;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.sub.admin.AdminReloadSub;
import soys.soysoceanbox.command.sub.admin.AdminGiveSub;
import soys.soysoceanbox.command.sub.admin.AdminResetSub;
import soys.soysoceanbox.command.sub.admin.AdminDeleteSub;
import soys.soysoceanbox.command.sub.admin.AdminStorageSub;
import soys.soysoceanbox.command.sub.admin.AdminMigrateSub;
import soys.soysoceanbox.command.sub.admin.AdminSyncSub;
import soys.soysoceanbox.command.sub.admin.AdminSetPoolSub;
import soys.soysoceanbox.command.sub.admin.AdminRatesSub;
import soys.soysoceanbox.command.sub.admin.AdminRankSub;

/**
 * 管理指令入口：/soceanboxadmin
 * <p>子指令：reload / give / reset / delete / storage / migrate / sync / setpool / rates / rank。</p>
 */
public class AdminLotteryCommand extends CommandDispatcher {

    public AdminLotteryCommand(SOYSOceanBox plugin) {
        super(plugin);
        register(new AdminReloadSub(plugin));
        register(new AdminGiveSub(plugin));
        register(new AdminResetSub(plugin));
        register(new AdminDeleteSub(plugin));
        register(new AdminStorageSub(plugin));
        register(new AdminMigrateSub(plugin));
        register(new AdminSyncSub(plugin));
        register(new AdminSetPoolSub(plugin));
        register(new AdminRatesSub(plugin));
        register(new AdminRankSub(plugin));
    }

    @Override
    protected void showDefault(CommandSender sender, String label) {
        msgList(sender, "help.admin", null);
    }
}
