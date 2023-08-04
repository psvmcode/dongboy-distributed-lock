package com.dongboy.service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁缓存接口
 *
 * @Author dongboy
 * @what time    2023/7/31 15:23
 */
public interface DistributedLockCache {

    boolean setIfAbsent(String key, String value, long ttl, TimeUnit timeUnit);

    void expire(String key, long ttl, TimeUnit timeUnit);

    void delete(String key, String value);

    void registerLockExpirationEvent(String key, Runnable eventCallback);

    void unregisterLockExpirationEvent(String key);

}
