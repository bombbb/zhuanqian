package com.zq.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 币安订单实体（查询订单响应）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BinanceOrder {
    private String symbol;              // 交易对
    private long orderId;               // 订单ID
    private String clientOrderId;       // 客户端订单ID
    private double price;               // 价格
    private double origQty;             // 原始数量
    private double executedQty;         // 已成交数量
    private double cummulativeQuoteQty; // 累计成交金额
    private String status;              // 订单状态
    private String timeInForce;         // 有效期类型
    private String type;                // 订单类型
    private String side;                // 买卖方向
    private long time;                  // 订单创建时间
    private long updateTime;            // 订单更新时间
    
    /**
     * 获取平均成交价格
     */
    public double getExecutedPrice() {
        if (executedQty == 0) {
            return 0;
        }
        return cummulativeQuoteQty / executedQty;
    }
}

