package gcy.system.utils;

/**
 * Redis Key 统一管理常量类。
 * <p>
 * 命名约定：前缀:业务:子业务 → 实际 key = 常量 + 业务ID。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public final class RedisConstants {

    private RedisConstants() {
    }

    // ==================== 验证码 ====================

    /**
     * 注册验证码，后接邮箱/手机号
     */
    public static final String REGISTER_CODE_KEY = "register:code:";
    public static final Long REGISTER_CODE_TTL = 5L;

    /**
     * 登录验证码，后接邮箱/手机号
     */
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 5L;

    /**
     * 重置密码验证码，后接邮箱
     */
    public static final String RESET_PASSWORD_CODE_KEY = "reset:code:";

    /**
     * 修改邮箱验证码，后接新邮箱
     */
    public static final String UPDATE_EMAIL_CODE_KEY = "update:code:";

    // ==================== 登录/改密失败锁定 ====================

    /**
     * 认证失败计数 key，后接账号（邮箱/手机号）
     */
    public static final String LOGIN_FAIL_KEY = "login:fail:";

    /**
     * 认证失败锁定 key，后接账号（邮箱/手机号），存在即锁定
     */
    public static final String LOGIN_LOCK_KEY = "login:lock:";

    /**
     * 认证失败次数上限，超过则锁定
     */
    public static final Long LOGIN_FAIL_LIMIT = 5L;

    /**
     * 锁定时间（秒），5 分钟
     */
    public static final Long LOGIN_LOCK_TTL = 300L;

    // ==================== 验证码 IP 级限流 ====================

    /**
     * 验证码发送 IP 限流 key，后接 类型:IP。
     * <p>
     * 验证码接口匿名，只按账号节流可换账号绕过，遍历邮箱会耗尽 SMTP 配额，故补 IP 维度限流。
     */
    public static final String CODE_IP_LIMIT_KEY = "code:ip:limit:";

    /**
     * IP 限流统计窗口（秒），1 小时
     */
    public static final Long CODE_IP_LIMIT_TTL = 3600L;

    /**
     * 单个 IP 在统计窗口内允许的发送次数
     */
    public static final Long CODE_IP_LIMIT_COUNT = 20L;

    // ==================== 登录 Token ====================

    /**
     * Redis Hash: 用户登录态，后接 token
     */
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    /**
     * 用户所有存活 Token 的 Set，后接 userId；用于管理员改密/删用户时批量定位。
     */
    public static final String LOGIN_USER_TOKENS_SET = "login:user:tokens:set:";

    /**
     * 登录态 Hash 中的端类型字段（值：PC / MOBILE），用于同端互踢
     */
    public static final String LOGIN_CLIENT_TYPE_FIELD = "clientType";

    /**
     * 登录态 Hash 中的「首次登录时间」字段（值：毫秒时间戳），用于实现绝对过期。
     * <p>
     * 滑动续期下 Token 只要持续访问就能无限存活，泄露后攻击者可长期持有；
     * 续期时超过 {@link #LOGIN_USER_ABSOLUTE_TTL} 即强制失效。
     * 用 putIfAbsent 写入，保证「修改资料刷新登录态」不会重置该时间。
     */
    public static final String LOGIN_TIME_FIELD = "loginTime";

    /**
     * 登录态绝对有效期上限（秒），7 天。
     * 滑动续期最多续到这个上限，超过即强制重新登录。
     */
    public static final Long LOGIN_USER_ABSOLUTE_TTL = 7 * 24 * 3600L;

    /**
     * 被踢下线标记 key，后接 token。存在即表示该会话已被同类型端登录挤下线，
     * 用于前端区分「登录已过期」与「被强制下线」。
     */
    public static final String LOGIN_KICKED_KEY = "login:kick:";

    /**
     * 被踢标记有效期（秒），2 分钟，足够前端收到 401 后识别
     */
    public static final Long LOGIN_KICKED_TTL = 120L;

    // ==================== 缓存 ====================

    /**
     * 家具详情缓存，后接 furnitureId
     */
    public static final String CACHE_FURNITURE_KEY = "cache:furniture:";

    /**
     * 家具缓存逻辑过期时长（分钟）。
     * 逻辑过期由 RedisData.expireTime 控制，用于触发缓存重建；
     * 数值应大于普通热点数据的访问间隔，避免频繁重建。
     */
    public static final Long CACHE_FURNITURE_TTL = 120L;

    /**
     * 家具缓存物理 TTL（分钟），作为逻辑过期的兜底。
     * 防止缓存 key 长期驻留 Redis 导致脏数据，24 小时未访问即物理淘汰。
     */
    public static final Long CACHE_FURNITURE_PHYSICAL_TTL = 1440L;

    /**
     * 家具分类列表缓存
     */
    public static final String CACHE_FURNITURE_TYPE_KEY = "cache:furnitureTypeList:";

    /**
     * 缓存穿透空值 TTL（分钟）
     */
    public static final Long CACHE_NULL_TTL = 2L;

    // ==================== 分布式锁 ====================

    /**
     * 家具缓存重建锁，后接 furnitureId
     */
    public static final String LOCK_FURNITURE_KEY = "lock:furniture:";

    /**
     * 家具分类缓存重建锁
     */
    public static final String LOCK_FURNITURE_TYPE_KEY = "lock:furnitureTypeList";

    // ==================== 订单锁 ====================

    /**
     * 下单锁（防用户双击），后接 userId
     */
    public static final String ORDER_CREATE_KEY = "lock:order:create:";

    /**
     * 超时取消定时任务锁（全局单实例执行）
     */
    public static final String ORDER_TIMEOUT_TASK_KEY = "lock:order:timeout:task";

    /**
     * 库存预警定时任务锁（全局单实例执行）
     */
    public static final String STOCK_ALERT_TASK_KEY = "lock:stock:alert:task";

    /**
     * 自动确认收货定时任务锁（全局单实例执行）
     */
    public static final String ORDER_AUTO_RECEIVE_TASK_KEY = "lock:order:autoReceive:task";

    // ==================== 优惠券 ====================

    /**
     * 优惠券已领取计数，后接 couponId。用于并发领券的 Lua 原子扣减与已领数量展示。
     */
    public static final String COUPON_COUNT_KEY = "coupon:count:";

    /**
     * 用户已领某券数量，后接 couponId:userId
     */
    public static final String COUPON_USER_COUNT_KEY = "coupon:user:";

    // ==================== AI / 向量 ====================

    /**
     * AI 对话记忆，后接 userId:conversationId
     */
    public static final String AI_CHAT_MEMORY_KEY = "ai:chat:";

    /**
     * 知识库向量已摄入标记（幂等控制）
     */
    public static final String EMBEDDING_INGESTED_KEY = "ai:embedding:ingested";

    /**
     * Redis 向量 key 通配符（用于批量 TTL 设置）
     */
    public static final String EMBEDDING_WILDCARD_KEY = "embedding:*";

}
