package com.dongboy.result;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author dongboy
 * @what time    2023/7/31 17:10
 */
@Data
@AllArgsConstructor
public class DistributedLockExprieResult {

    // 锁是否超时，批量加锁时，任意锁过期都会导致批量过期
    private Boolean expired;

}
