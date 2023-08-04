package com.dongboy.exception;

import lombok.Getter;

/**
 * @Author dongboy
 * @what time    2023/7/31 20:21
 */
public class DistributeLockException extends RuntimeException {

    @Getter
    private final DistributedLockResponseCode code;

    public DistributeLockException(DistributedLockResponseCode code) {
        super(code.name());
        this.code = code;
    }

}
