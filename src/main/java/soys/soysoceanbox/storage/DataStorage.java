package soys.soysoceanbox.storage;

import soys.soysoceanbox.lottery.LotteryPlayer;

import java.util.Collection;
import java.util.UUID;

/**
 * 数据存储后端抽象。
 * <p>
 * 所有实现必须满足：
 * <ul>
 *   <li>方法可能在异步线程被调用，实现需自行保证线程安全；</li>
 *   <li>任何失败都以抛出异常的形式上报，由 {@link StorageManager} 统一降级处理；</li>
 *   <li>{@link #savePlayer(LotteryPlayer)} 语义为 upsert，玩家不存在时插入，存在时整体覆盖。</li>
 * </ul>
 * 新增后端只需实现本接口并在 {@link StorageManager#buildStorage} 中注册。
 * </p>
 */
public interface DataStorage {

    /**
     * 后端类型。
     */
    StorageType getType();

    /**
     * 初始化连接 / 建表 / 创建数据文件。
     *
     * @throws Exception 初始化失败，该后端将被标记为不可用
     */
    void initialize() throws Exception;

    /**
     * 释放资源，关服或重载时调用。
     */
    void shutdown();

    /**
     * 后端当前是否可用。不可用的后端会被跳过而非导致插件崩溃。
     */
    boolean isAvailable();

    /**
     * 供 /soceanboxadmin storage 展示的简要描述，如文件路径或数据库地址。
     */
    String describe();

    // ================================================================
    //  读
    // ================================================================

    /**
     * 按 UUID 读取单个玩家档案。
     *
     * @return 玩家档案，不存在时返回 null
     */
    LotteryPlayer loadPlayer(UUID uuid) throws Exception;

    /**
     * 读取全部玩家档案。仅用于迁移、同步与管理指令，常规流程不应调用。
     */
    Collection<LotteryPlayer> loadAllPlayers() throws Exception;

    /**
     * 统计玩家档案总数。
     */
    int countPlayers() throws Exception;

    // ================================================================
    //  写
    // ================================================================

    /**
     * 保存（upsert）单个玩家档案。
     */
    void savePlayer(LotteryPlayer player) throws Exception;

    /**
     * 批量保存。实现应尽可能使用事务或单次落盘以提升性能。
     */
    void savePlayers(Collection<LotteryPlayer> players) throws Exception;

    /**
     * 删除玩家档案。
     */
    void deletePlayer(UUID uuid) throws Exception;

    /**
     * 清空全部数据。仅由迁移覆盖流程调用。
     */
    void clear() throws Exception;
}
