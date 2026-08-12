package soys.soysoceanbox.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.util.Placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 子指令抽象基类。
 * <p>新增指令只需继承本类并在 {@link LotteryCommand} / {@link AdminLotteryCommand} 中注册。</p>
 */
public abstract class SubCommand {

    protected final SOYSOceanBox plugin;

    protected SubCommand(SOYSOceanBox plugin) {
        this.plugin = plugin;
    }

    /**
     * 子指令主名称。
     */
    public abstract String getName();

    /**
     * 子指令别名。
     */
    public List<String> getAliases() {
        return Collections.emptyList();
    }

    /**
     * 所需 Bukkit 权限节点，null 表示不校验。
     */
    public String getPermission() {
        return null;
    }

    /**
     * 是否只允许玩家执行。
     */
    public boolean isPlayerOnly() {
        return true;
    }

    /**
     * 执行指令。
     *
     * @param sender 执行者
     * @param label  指令标签（用于用法提示）
     * @param args   已剔除子指令名的参数数组
     */
    public abstract void execute(CommandSender sender, String label, String[] args);

    /**
     * Tab 补全。
     *
     * @param args 已剔除子指令名的参数数组
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    // ================================================================
    //  便捷方法
    // ================================================================

    protected void msg(CommandSender sender, String key) {
        plugin.getMessageManager().send(sender, key);
    }

    protected void msg(CommandSender sender, String key, Map<String, String> placeholders) {
        plugin.getMessageManager().send(sender, key, placeholders);
    }

    protected void msgList(CommandSender sender, String key, Map<String, String> placeholders) {
        plugin.getMessageManager().sendList(sender, key, placeholders);
    }

    /**
     * 按前缀过滤候选项，用于 Tab 补全。
     */
    protected List<String> filter(List<String> candidates, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return candidates;
        }
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase().startsWith(lower)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * 在线玩家名候选项，受 {@code command.tab-complete} 控制。
     */
    protected List<String> onlinePlayerNames(String prefix) {
        if (!plugin.getConfigManager().isTabComplete()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            names.add(player.getName());
        }
        return filter(names, prefix);
    }
}
