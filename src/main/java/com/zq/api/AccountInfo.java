package com.zq.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 账户信息实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountInfo {
    private int makerCommission;      // Maker手续费率
    private int takerCommission;      // Taker手续费率
    private int buyerCommission;      // 买方手续费率
    private int sellerCommission;     // 卖方手续费率
    private boolean canTrade;         // 是否可以交易
    private boolean canWithdraw;      // 是否可以提现
    private boolean canDeposit;       // 是否可以充值
    private long updateTime;          // 更新时间
    private List<Balance> balances;   // 余额列表
    
    /**
     * 获取指定资产的余额
     */
    public Balance getBalance(String asset) {
        if (balances == null) {
            return null;
        }
        return balances.stream()
            .filter(b -> asset.equals(b.getAsset()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 获取所有非零余额
     */
    public List<Balance> getNonZeroBalances() {
        if (balances == null) {
            return List.of();
        }
        return balances.stream()
            .filter(b -> b.getTotal() > 0)
            .collect(Collectors.toList());
    }
}

