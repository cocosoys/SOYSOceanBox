package soys.soysoceanbox.command.sub.admin;

import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.util.Placeholders;

import java.util.List;

/**
 * /soceanboxadmin reload —— 重载配置与存储连接。
 */
public class AdminReloadSub extends SubCommand {

    public AdminReloadSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public List<String> getAliases() {
        return java.util.Arrays.asList("rl");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.reload";
    }

    @Override
    public void execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
        long start = System.currentTimeMillis();
        try {
            plugin.reloadConfiguration();
            long ms = System.currentTimeMillis() - start;
            msg(sender, "admin.reload.success", Placeholders.of("ms", ms).build());
        } catch (Exception e) {
            plugin.getLogger().severe("重载失败: " + e.getMessage());
            e.printStackTrace();
            msg(sender, "admin.reload.failed", null);
        }
    }
}
