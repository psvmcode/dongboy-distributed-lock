package com.dongboy.lock;

import com.dongboy.exception.DistributeLockException;
import com.dongboy.exception.DistributedLockResponseCode;
import com.dongboy.service.DistributedLockService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author dongboy
 * @what time    2023/7/31 19:38
 */
@Slf4j
public class DistributedMultiLock extends DistributedBaseLock {

    private static final Map<String, DistributedMultiLock> LOCK_POOL = new ConcurrentHashMap<>();

    // 按照字典序升序排序
    private final List<DistributedLock> subLocks;

    public DistributedMultiLock(String multiLockKey, List<String> lockKeys, DistributedLockService lockService) {
        super(multiLockKey);
        subLocks = lockKeys.stream().map(subLockKey -> DistributedReentrantLock.get(subLockKey, lockService)).collect(Collectors.toList());
    }

    public static DistributedMultiLock get(List<String> lockKeys, DistributedLockService lockService) {
        Collections.sort(lockKeys);
        String multiLockKey = lockKeys.stream().collect(Collectors.joining(",", "MultiLock:[", "]"));
        if (!LOCK_POOL.containsKey(multiLockKey)) {
            synchronized (LOCK_POOL) {
                if (!LOCK_POOL.containsKey(multiLockKey)) {
                    LOCK_POOL.put(multiLockKey, new DistributedMultiLock(multiLockKey, lockKeys, lockService));
                }
            }
        }
        return LOCK_POOL.get(multiLockKey);
    }

    @Override
    public void lock(Long waitTimeout, Long lockTime) {
        long waitDeadline = isPositive(waitTimeout) ? System.currentTimeMillis() + waitTimeout : -1;
        long waitRemainTime;
        List<DistributedLock> acquireLocks = new ArrayList<>(subLocks.size());
        for (DistributedLock lock : subLocks) {
            log.debug("multiLock " + getLockKey() + " locking:" + lock);
            waitRemainTime = waitDeadline - System.currentTimeMillis();
            if (waitDeadline > 0 && waitRemainTime <= 0) {
                // 加锁等待超时
                releaseLocksIgnoreException(acquireLocks);
                throw new DistributeLockException(DistributedLockResponseCode.WAIT_TIMEOUT_EXCEEDED);
            }
            try {
                lock.lock(waitRemainTime, lockTime);
                acquireLocks.add(lock);
            } catch (Exception e) {
                releaseLocksIgnoreException(acquireLocks);
                throw e;
            }
        }
        heldBy.set(Thread.currentThread());
    }

    @Override
    public boolean tryLock(Long waitTimeout, Long lockTime) {
        long waitDeadline = isPositive(waitTimeout) ? System.currentTimeMillis() + waitTimeout : -1;
        boolean locked;
        long waitRemainTime;
        List<DistributedLock> acquireLocks = new ArrayList<>(subLocks.size());
        for (DistributedLock lock : subLocks) {
            log.debug("multiLock " + getLockKey() + " trying to lock:" + lock);
            waitRemainTime = waitDeadline - System.currentTimeMillis();
            if (waitDeadline > 0 && waitRemainTime <= 0) {
                // 加锁等待超时
                releaseLocksIgnoreException(acquireLocks);
                throw new DistributeLockException(DistributedLockResponseCode.WAIT_TIMEOUT_EXCEEDED);
            }
            locked = lock.tryLock(waitRemainTime, lockTime);
            if (locked) {
                acquireLocks.add(lock);
            } else {
                releaseLocksIgnoreException(acquireLocks);
                return false;
            }
        }
        heldBy.set(Thread.currentThread());
        return true;
    }

    @Override
    public void unlock() {
        if (!isLocked()) {
            return;
        }
        if (isHeldByCurrentThread()) {
            boolean expired = isExpire();
            releaseSubLocks();
            if (expired) {
                throw new DistributeLockException(DistributedLockResponseCode.LOCK_EXPIRED);
            }
        } else {
            throw new DistributeLockException(DistributedLockResponseCode.CANNOT_UNLOCK_OTHERS);
        }
    }

    protected void releaseSubLocks() {
        DistributeLockException exception = null;
        for (DistributedLock lock : subLocks) {
            try {
                lock.unlock();
            } catch (DistributeLockException e) {
                if (exception == null) {
                    exception = e;
                } else {
                    e.initCause(exception);
                    exception = e;
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    protected void releaseLocksIgnoreException(List<DistributedLock> locks) {
        for (DistributedLock lock : locks) {
            try {
                lock.unlock();
            } catch (DistributeLockException ignored) {

            }
        }
    }

    @Override
    public void update() {
        // 续期任务已经被子任务锁进行，本对象无需处理
    }

    @Override
    public boolean isExpire() {
        return subLocks.stream().anyMatch(DistributedLock::isExpire);
    }

    @Override
    public void clearExpirationFlag() {
        log.warn("clear multiLock expiration flag is useless since it is caculated from sub locks.");
    }

    @Override
    public void markExpire(Thread holdingThread) {
        log.warn("mark multiLock as a expired is useless it is calculated from sub locks.");
    }
}
