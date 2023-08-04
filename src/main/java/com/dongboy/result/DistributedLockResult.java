package com.dongboy.result;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author dongboy
 * @what time    2023/7/31 17:04
 */
@Data
@AllArgsConstructor
public class DistributedLockResult {

    // 获取锁是否成功，非阻塞方式加锁返回true(成功)或false(失败)，阻塞方式加锁返回null
    private Boolean success;

}
