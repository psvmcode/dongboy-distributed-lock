package com.dongboy.lock;

import com.dongboy.exception.DistributeLockException;
import com.dongboy.exception.DistributedLockResponseCode;
import com.dongboy.service.DistributedLockService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @Author dongboy
 * @what time    2023/7/31 20:15
 */
@Slf4j
public class DistributedReentrantLock extends DistributedBaseLock {

    private final DistributedLockService lockService;

    private volatile boolean enableAutoUpdate;

    private volatile long lockExpireTimeoutMills;

    private static final Map<String, DistributedReentrantLock> LOCK_POOL = new ConcurrentHashMap<>();

    private final ThreadLocal<Integer> reentrantCounter = ThreadLocal.withInitial(() -> 0);

    private DistributedReentrantLock(String lockKey, DistributedLockService lockService) {
        super(lockKey);
        this.lockService = lockService;
    }

    public static DistributedReentrantLock get(String lockKey, DistributedLockService lockService) {
        if (!LOCK_POOL.containsKey(lockKey)) {
            synchronized (LOCK_POOL) {
                if (!LOCK_POOL.containsKey(lockKey)) {
                    LOCK_POOL.put(lockKey, new DistributedReentrantLock(lockKey, lockService));
                }
            }
        }
        return LOCK_POOL.get(lockKey);
    }

    @Override
    public void lock(Long waitTimeout, Long lockTime) {
        long waitDeadline = isPositive(waitTimeout) ? System.currentTimeMillis() + waitTimeout : -1;
        boolean locked = tryLock(waitTimeout, lockTime);
        // 自旋
        while (!locked) {
            if (waitDeadline > 0 && waitDeadline < System.currentTimeMillis()) {
                // 加锁等到超时
                throw new DistributeLockException(DistributedLockResponseCode.WAIT_TIMEOUT_EXCEEDED);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(LOCK_SPIN_WAIT_TIME_MILLIS);
            } catch (InterruptedException ignored) {
                log.info("Thread interrupted when waiting lock:" + getLockKey());
                // 设置线程中断标识
                Thread.currentThread().interrupt();
            }
            locked = tryLock(waitDeadline, lockTime);
        }
    }

    @Override
    public boolean tryLock(Long waitTimeout, Long lockTime) {
        // 如果当前线程已经获得锁，那么直接将重入数+1
        if (isHeldByCurrentThread()) {
            reentrantCounter.set(reentrantCounter.get() + 1);
            log.info("Reentered lock:" + this);
            return true;
        }
        boolean autoUpdate = !isPositive(lockTime);
        long lockExpireTimeOut = autoUpdate ? DEFAULT_LOCK_TIMEOUT : lockTime;
        boolean locked = tryAcquire(lockExpireTimeOut);
        if (locked) {
            // 当前线程获得了锁
            heldBy.set(Thread.currentThread());
            redisKeyExpired.put(heldBy.get(), false);
            enableAutoUpdate = autoUpdate;
            lockExpireTimeoutMills = lockExpireTimeOut;
            // 设置重入计数
            reentrantCounter.set(1);
            if (enableAutoUpdate) {
                // 开启自动锁续期
                lockService.getUpdateTaskScheduler().newTask(this);
            } else {
                // 注册过期事件监听
                lockService.getLockCache().registerLockExpirationEvent(
                        getLockKey(), () -> this.markExpire(heldBy.get())
                );
            }
        }
        return locked;
    }

    protected boolean tryAcquire(Long expireTimeout) {
        return lockService.getLockCache().setIfAbsent(getLockKey(), getLockHolderID(), expireTimeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void unlock() {
        if (isExpire()) {
            reentrantCounter.remove();
            clearExpirationFlag();
            throw new DistributeLockException(DistributedLockResponseCode.LOCK_EXPIRED);
        }
        if (!isLocked()) {
            return;
        }
        if (isHeldByCurrentThread()) {
            if (reentrantCounter.get() == 1) {
                if (enableAutoUpdate) {
                    lockService.getUpdateTaskScheduler().cancelTask(this);
                } else {
                    lockService.getLockCache().unregisterLockExpirationEvent(getLockKey());
                }
                reentrantCounter.remove();
                heldBy.set(null);
                trRelease();
            } else {
                reentrantCounter.set(reentrantCounter.get() - 1);
                log.debug("exit reentrant lock:" + this);
            }
        } else {
            throw new DistributeLockException(DistributedLockResponseCode.CANNOT_UNLOCK_OTHERS);
        }
    }

    protected void trRelease() {
        lockService.getLockCache().delete(getLockKey(), getLockHolderID());
    }

    @Override
    public void update() {
        lockService.getLockCache().expire(lockKey, lockExpireTimeoutMills, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isExpire() {
        if (isHeldByCurrentThread() && enableAutoUpdate) {
            return false;
        }
        return redisKeyExpired.getOrDefault(Thread.currentThread(), false);
    }

    @Override
    public void markExpire(Thread holdingThread) {
        redisKeyExpired.put(holdingThread, true);
        heldBy.compareAndSet(holdingThread, null);
    }

    @Override
    public void clearExpirationFlag() {
        redisKeyExpired.remove(Thread.currentThread());
    }
}
