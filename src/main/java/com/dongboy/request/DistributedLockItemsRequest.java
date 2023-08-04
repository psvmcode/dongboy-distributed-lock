package com.dongboy.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author dongboy
 * @what time    2023/7/31 20:37
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DistributedLockItemsRequest {

    private List<String> keys;

    private Long waitTimeout = null;

    private Long lockTime = null;

    private boolean blockable = true;

    public DistributedLockItemsRequest(List<String> keys) {
        this.keys = keys;
    }

}
