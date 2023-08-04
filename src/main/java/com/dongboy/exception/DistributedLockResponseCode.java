package com.dongboy.exception;

/**
 * @Author dongboy
 * @what time    2023/7/31 20:22
 */
public enum DistributedLockResponseCode {

    // 不能解锁其他线程持有的锁
    CANNOT_UNLOCK_OTHERS,

    // 锁超过过期时间
    LOCK_EXPIRED,

    // 加锁等待超时，只发生在使用规定了等待锁超时时间的阻塞方式加锁时
    WAIT_TIMEOUT_EXCEEDED

}
