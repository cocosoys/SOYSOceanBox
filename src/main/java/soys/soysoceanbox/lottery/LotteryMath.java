package soys.soysoceanbox.lottery;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 抽奖相关的纯计算逻辑，集中在此便于单元测试，不依赖 Bukkit。
 */
public final class LotteryMath {

    private LotteryMath() {
    }

    /**
     * 依据权重随机抽中一个奖项；奖池为空或总权重 &lt;= 0 时返回 null。
     *
     * @param pool    奖池（其中 weight &gt; 0 才参与）
     * @param random  随机数源（便于测试注入固定序列）
     */
    public static RewardDef roll(List<RewardDef> pool, Random random) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        int total = 0;
        for (RewardDef def : pool) {
            total += Math.max(0, def.getWeight());
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        int cursor = 0;
        for (RewardDef def : pool) {
            cursor += Math.max(0, def.getWeight());
            if (roll < cursor) {
                return def;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * 当日日期键（本地时区），例如 {@code 2026-08-11}。用于每日上限判定。
     */
    public static String dailyKey() {
        return LocalDate.now().toString();
    }

    /**
     * 当周键（本地时区，ISO 周，例如 {@code 2026-W33}）。用于每周上限判定。
     */
    public static String weeklyKey() {
        LocalDate now = LocalDate.now();
        int week = now.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear());
        int year = now.getYear();
        return year + "-W" + week;
    }

    /**
     * 判断是否需要按日期键重置计数器（key 变化即代表跨日）。
     */
    public static boolean isNewDailyCycle(String lastKey, String currentKey) {
        return lastKey == null || !lastKey.equals(currentKey);
    }

    /**
     * 判断是否需要按周键重置计数器（key 变化即代表跨周）。
     */
    public static boolean isNewWeeklyCycle(String lastKey, String currentKey) {
        return lastKey == null || !lastKey.equals(currentKey);
    }

    /**
     * 计算下次允许抽奖的时间（毫秒）。用于每日上限返回给玩家提示。
     */
    public static long millisUntilNextDay() {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.add(Calendar.DAY_OF_MONTH, 1);
        next.set(Calendar.HOUR_OF_DAY, 0);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        return next.getTimeInMillis() - now.getTimeInMillis();
    }
}
