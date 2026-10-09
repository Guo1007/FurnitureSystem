package gcy.system.utils;


import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis 缓存数据包装类。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
public class RedisData {

    /**
     * 缓存的数据对象
     */
    private Object data;

    /**
     * 缓存数据的过期时间
     */
    private LocalDateTime expireTime;

}
