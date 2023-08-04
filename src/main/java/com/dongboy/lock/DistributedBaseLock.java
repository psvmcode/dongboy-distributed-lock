package com.dongboy.lock;

import com.dongboy.service.DistributedLockService;
import lombok.Getter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @Author dongboy
 * @what time    2023/7/31 19:39
 */
public abstract class DistributedBaseLock implements DistributedLock {

    @Getter
    protected final String lockKey;

    //持有当前锁的线程，若没有线程持有当前锁，则为null
    protected final AtomicReference<Thread> heldBy = new AtomicReference<>(null);

    // 记录锁对某线程的过期状态
    // 锁过期后，其他线程可以获得锁，此时该锁对本线程而言已经过期，但是对其他线程而言没有过期，所以需要分别记录该锁对于每个线程的过期状态
    protected Map<Thread, Boolean> redisKeyExpired = new ConcurrentHashMap<>();

    // 默认锁续期时间 10s
    protected static final long DEFAULT_LOCK_TIMEOUT = 10000;

    protected static final long LOCK_SPIN_WAIT_TIME_MILLIS = 10;

    public DistributedBaseLock(String lockKey) {
        this.lockKey = lockKey;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this))
                + "{" + "lockKey='" + lockKey + '\''
                + (heldBy.get() != null ? (",heldBy=" + heldBy.get().getName() + ":" + heldBy.get().getId()) : ",heldBy=null")
                + ",redisKeyExpired=" + redisKeyExpired.getOrDefault(Thread.currentThread(), false)
                + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DistributedBaseLock that = (DistributedBaseLock) obj;
        return lockKey.equals(that.lockKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lockKey);
    }

    @Override
    public boolean isLocked() {
        return getHoldingThread() != null;
    }

    protected String getLockHolderID() {
        return DistributedLockService.getClientId() + ":" + Thread.currentThread().getId();
    }

    protected boolean isPositive(Long number) {
        return number != null && number > 0;
    }

    public Thread getHoldingThread() {
        return heldBy.get();
    }

    public boolean isHeldByThread(Thread thread) {
        return getHoldingThread() == thread;
    }

    public boolean isHeldByCurrentThread() {
        return isHeldByThread(Thread.currentThread());
    }

}
