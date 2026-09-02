package soys.soysoceanbox.lottery;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 单个玩家的抽奖档案，是存储层持久化的领域对象。
 * <p>所有写操作均为整体覆盖（upsert），因此修改后只需调用
 * {@code StorageManager.savePlayerAsync} 即可落盘。</p>
 */
public class LotteryPlayer {

    private final UUID uuid;
    private String name;
    private long lastDrawTime;
    private int totalDraws;
    private final List<PendingReward> pending;

    // ============================================================
    //  个人中奖记录（最近 N 条）
    // ============================================================
    private final List<WinRecord> history;

    // ============================================================
    //  每日 / 每周抽奖上限
    // ============================================================
    private String dailyKey = "";
    private int dailyCount;
    private String weeklyKey = "";
    private int weeklyCount;

    // ============================================================
    //  概率保底（pity）
    // ============================================================
    private int drawsSinceBigWin;

    // ============================================================
    //  多奖池
    // ============================================================
    private String activePool = "";

    public LotteryPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.lastDrawTime = 0L;
        this.totalDraws = 0;
        this.pending = new ArrayList<>();
        this.history = new ArrayList<>();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getLastDrawTime() {
        return lastDrawTime;
    }

    public void setLastDrawTime(long lastDrawTime) {
        this.lastDrawTime = lastDrawTime;
    }

    public int getTotalDraws() {
        return totalDraws;
    }

    public void setTotalDraws(int totalDraws) {
        this.totalDraws = totalDraws;
    }

    public void addDraw() {
        this.totalDraws++;
    }

    public List<PendingReward> getPending() {
        return pending;
    }

    public void addPending(PendingReward reward) {
        pending.add(reward);
    }

    /**
     * 移除指定领取 id 的待领取奖励，返回是否移除成功。
     */
    public boolean removePending(UUID claimId) {
        return pending.removeIf(reward -> claimId.equals(reward.getClaimId()));
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    // ============================================================
    //  个人中奖记录
    // ============================================================

    public List<WinRecord> getHistory() {
        return history;
    }

    /**
     * 追加一条中奖记录，并按 keep 封顶（仅保留最近 keep 条）。
     * keep <= 0 时不封顶（调用方应自行保证 keep > 0 才调用）。
     */
    public void addWin(long time, String pool, String display, int keep) {
        history.add(new WinRecord(time, pool == null ? "" : pool, display == null ? "" : display));
        if (keep > 0 && history.size() > keep) {
            history.subList(0, history.size() - keep).clear();
        }
    }

    // ============================================================
    //  每日 / 每周上限
    // ============================================================

    public String getDailyKey() {
        return dailyKey;
    }

    public void setDailyKey(String dailyKey) {
        this.dailyKey = dailyKey;
    }

    public int getDailyCount() {
        return dailyCount;
    }

    public void setDailyCount(int dailyCount) {
        this.dailyCount = dailyCount;
    }

    public String getWeeklyKey() {
        return weeklyKey;
    }

    public void setWeeklyKey(String weeklyKey) {
        this.weeklyKey = weeklyKey;
    }

    public int getWeeklyCount() {
        return weeklyCount;
    }

    public void setWeeklyCount(int weeklyCount) {
        this.weeklyCount = weeklyCount;
    }

    // ============================================================
    //  保底
    // ============================================================

    public int getDrawsSinceBigWin() {
        return drawsSinceBigWin;
    }

    public void setDrawsSinceBigWin(int drawsSinceBigWin) {
        this.drawsSinceBigWin = drawsSinceBigWin;
    }

    // ============================================================
    //  多奖池
    // ============================================================

    public String getActivePool() {
        return activePool;
    }

    public void setActivePool(String activePool) {
        this.activePool = activePool == null ? "" : activePool;
    }

    // ============================================================
    //  中奖记录条目
    // ============================================================

    /**
     * 一条中奖记录：时间、所属奖池、奖励展示文本。
     * 通过 {@link #encode()} / {@link #decode(String)} 序列化为单行文本，
     * 便于同时写入 YAML 列表与 SQL 的 TEXT 列（以不可见分隔符 \u0001 连接）。
     */
    public static class WinRecord {
        public final long time;
        public final String pool;
        public final String display;

        private static final String SEP = "\u0001";

        public WinRecord(long time, String pool, String display) {
            this.time = time;
            this.pool = pool == null ? "" : pool;
            this.display = display == null ? "" : display;
        }

        public String encode() {
            return time + SEP + pool + SEP + display;
        }

        public static WinRecord decode(String s) {
            if (s == null || s.isEmpty()) {
                return null;
            }
            String[] parts = s.split(SEP, -1);
            if (parts.length < 3) {
                return null;
            }
            long t;
            try {
                t = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                return null;
            }
            return new WinRecord(t, parts[1], parts[2]);
        }
    }
}
