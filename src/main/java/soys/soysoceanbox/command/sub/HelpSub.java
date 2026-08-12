package soys.soysoceanbox.command.sub;

import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;

import java.util.List;

/**
 * /soceanbox help —— 显示玩家帮助。
 */
public class HelpSub extends SubCommand {

    public HelpSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public List<String> getAliases() {
        return java.util.Arrays.asList("?", "h");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
        msgList(sender, "help.player", null);
    }
}
