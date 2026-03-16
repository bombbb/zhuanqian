package com.zq.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 余额实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Balance {
    private String asset;      // 资产名称，如 USDT, USDC
    private double free;       // 可用余额
    private double locked;     // 锁定余额
    
    /**
     * 获取总余额
     */
    public double getTotal() {
        return free + locked;
    }
}

