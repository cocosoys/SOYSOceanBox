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
}
