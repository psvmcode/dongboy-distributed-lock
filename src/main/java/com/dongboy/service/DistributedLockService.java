package com.dongboy.service;

import com.dongboy.lock.DistributedLock;
import com.dongboy.lock.DistributedMultiLock;
import com.dongboy.lock.DistributedReentrantLock;
import com.dongboy.request.DistributedLockItemRequest;
import com.dongboy.request.DistributedLockItemsRequest;
import com.dongboy.result.DistributedLockResult;
import com.dongboy.util.LockUpdateTaskScheduler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @Author dongboy
 * @what time    2023/7/31 15:27
 */
@Slf4j
@Service
public class DistributedLockService {

    private static final String CLIENT_ID = UUID.randomUUID().toString();

    @Getter
    @Resource
    protected DistributedLockCache lockCache;

    @Getter
    @Resource
    protected LockUpdateTaskScheduler updateTaskScheduler = new LockUpdateTaskScheduler();

    public static String getClientId() {
        return CLIENT_ID;
    }

    public DistributedLockResult lock(DistributedLockItemRequest request) {
        String lockKey = request.getKey();
        checkLockKeyLegal(lockKey);
        Long waitTimeout = request.getWaitTimeout();
        Long lockTime = request.getLockTime();
        boolean blockable = request.isBlockable();
        Boolean lockResult = lockInner(lockKey, waitTimeout, lockTime, blockable);
        return new DistributedLockResult(lockResult);
    }

    protected Boolean lockInner(String lockKey, Long waitTimeout, Long lockTime, boolean blockable) {
        DistributedLock lock = DistributedReentrantLock.get(lockKey, this);
        if (blockable) {
            lock.lock(waitTimeout, lockTime);
            return null;
        } else {
            return lock.tryLock(waitTimeout, lockTime);
        }
    }

    public DistributedLockResult lockArr(DistributedLockItemsRequest request) {
        List<String> lockKeys = request.getKeys();
        checkLockKeyLegal(lockKeys);
        Long waitTimeout = request.getWaitTimeout();
        Long lockTime = request.getLockTime();
        boolean blockable = request.isBlockable();
        Boolean lockResult = lockArrInner(lockKeys, waitTimeout, lockTime, blockable);
        return new DistributedLockResult(lockResult);
    }

    protected Boolean lockArrInner(List<String> lockKeys, Long waitTimeout, Long lockTime, boolean blockable) {
        DistributedLock lock = DistributedMultiLock.get(lockKeys, this);
        if (blockable) {
            lock.lock(waitTimeout, lockTime);
            return null;
        } else {
            return lock.tryLock(waitTimeout, lockTime);
        }
    }

    private void checkLockKeyLegal(List<String> lockKeys) {
        if (lockKeys == null || lockKeys.isEmpty()) {
            throw new IllegalArgumentException("Lock keys are empty!");
        }
        for (String lockKey : lockKeys) {
            if (!StringUtils.hasText(lockKey)) {
                throw new IllegalArgumentException("Invalid lock key:" + lockKey);
            }
        }
    }

    public void checkLockKeyLegal(String lockKey) {
        if (!StringUtils.hasText(lockKey)) {
            throw new IllegalArgumentException("invalid lock key:" + lockKey);
        }
    }

    public void checkLockKeyLegal(String[] lockKeys) {
        checkLockKeyLegal(Arrays.asList(lockKeys));
    }

}
