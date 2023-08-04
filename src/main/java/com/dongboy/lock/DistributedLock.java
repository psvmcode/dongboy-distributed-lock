package com.dongboy.lock;

/**
 * @Author dongboy
 * @what time    2023/7/31 19:33
 */
public interface DistributedLock {

    String getLockKey();

    void lock(Long waitTimeout, Long lockTime);

    boolean tryLock(Long waitTimeout, Long lockTime);

    void unlock();

    boolean isLocked();

    Thread getHoldingThread();

    boolean isHeldByThread(Thread thread);

    boolean isHeldByCurrentThread();

    void update();

    void markExpire(Thread holdingThread);

    boolean isExpire();

    void clearExpirationFlag();

}
