package soys.soysoceanbox.command.sub.admin;

import org.bukkit.command.CommandSender;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.command.SubCommand;
import soys.soysoceanbox.config.LotteryConfig;
import soys.soysoceanbox.lottery.LotteryMath;
import soys.soysoceanbox.lottery.RewardDef;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * /soceanboxadmin rates [奖池名] [样本数] —— 蒙特卡洛模拟各奖实际命中率（调试用）。
 * <p>不改写任何玩家数据，仅基于权重做纯计算模拟，输出实测占比与理论权重占比对照。</p>
 */
public class AdminRatesSub extends SubCommand {

    /** 单次模拟样本数安全上限，避免误输入超大值导致卡顿。 */
    private static final int MAX_SAMPLES = 50_000_000;

    public AdminRatesSub(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "rates";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("debug", "probability", "gailv", "概率");
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public String getPermission() {
        return "soysoceanbox.admin.rates";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        LotteryConfig cfg = plugin.getLotteryConfig();
        String poolName = args.length >= 1 ? args[0] : cfg.getDefaultPoolName();
        int samples = 100_000;
        if (args.length >= 2) {
            try {
                samples = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                // 解析失败则用默认样本数
            }
        }
        samples = Math.max(1, Math.min(samples, MAX_SAMPLES));

        List<RewardDef> pool = cfg.getRewardPool(poolName);
        if (pool.isEmpty()) {
            msg(sender, "admin.rates.unknown-pool",
                    Collections.singletonMap("pool", poolName));
            return;
        }

        int totalWeight = 0;
        for (RewardDef d : pool) {
            totalWeight += Math.max(0, d.getWeight());
        }

        Map<String, Integer> counts = new HashMap<>();
        long start = System.currentTimeMillis();
        Random random = new Random();
        for (int i = 0; i < samples; i++) {
            RewardDef d = LotteryMath.roll(pool, random);
            if (d == null) {
                continue;
            }
            counts.merge(d.getId(), 1, Integer::sum);
        }
        long ms = System.currentTimeMillis() - start;

        Map<String, String> summary = new HashMap<>();
        summary.put("pool", poolName);
        summary.put("samples", String.valueOf(samples));
        summary.put("total", String.valueOf(totalWeight));
        msg(sender, "admin.rates.header");
        msg(sender, "admin.rates.summary", summary);

        for (RewardDef d : pool) {
            int c = counts.getOrDefault(d.getId(), 0);
            double theory = totalWeight > 0
                    ? 100.0 * Math.max(0, d.getWeight()) / totalWeight : 0.0;
            double actual = samples > 0 ? 100.0 * c / samples : 0.0;
            Map<String, String> entry = new HashMap<>();
            entry.put("reward", d.getDisplay() == null ? d.getId() : d.getDisplay());
            entry.put("weight", String.valueOf(d.getWeight()));
            entry.put("theory", String.format("%.2f", theory));
            entry.put("actual", String.format("%.2f", actual));
            msg(sender, "admin.rates.entry", entry);
        }

        Map<String, String> footer = new HashMap<>();
        footer.put("ms", String.valueOf(ms));
        msg(sender, "admin.rates.footer", footer);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(plugin.getLotteryConfig().getPoolNames(), args[0]);
        }
        return Collections.emptyList();
    }
}
