package com.zq.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单结果实体（下单/撤单响应）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResult {
    private String symbol;              // 交易对
    private long orderId;               // 订单ID
    private String clientOrderId;       // 客户端订单ID
    private long transactTime;          // 交易时间
    private double price;               // 价格
    private double origQty;             // 原始数量
    private double executedQty;         // 已成交数量
    private double cummulativeQuoteQty; // 累计成交金额
    private String status;              // 订单状态
    private String timeInForce;         // 有效期类型
    private String type;                // 订单类型
    private String side;                // 买卖方向
}

