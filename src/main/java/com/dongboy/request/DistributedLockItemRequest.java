package com.dongboy.request;

import com.dongboy.result.DistributedLockResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author dongboy
 * @what time    2023/7/31 17:12
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DistributedLockItemRequest {

    // 锁标识
    private String key;

    // 等待超时时间，为null或者负数时无限等待
    private Long waitTimeout = null;

    // 锁超时时间，为null或者负数时无限等待
    private Long lockTime = null;

    // 未获取到锁时是否可阻塞，直到等到锁或者等待超时
    private boolean blockable = true;

    public DistributedLockItemRequest(String key) {
        this.key = key;
    }

    public DistributedLockItemRequest(String key, Long waitTimeout, Long lockTime) {
        this.key = key;
        this.waitTimeout = waitTimeout;
        this.lockTime = lockTime;
    }

}
