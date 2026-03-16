package com.zq.order;

import com.zq.strategy.StrategyConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 订单实体
 * 记录所有买卖订单的详细信息，包括币安订单ID、成交价格、滑点等
 */
@Data
@Document(collection = "orders")
@AllArgsConstructor
@NoArgsConstructor
public class Order {
    
    @Id
    private String id;
    
    private String symbol;          // 交易对，如 USDCUSDT
    private StrategyConfig.Mode mode;              // 运行模式（使用StrategyConfig.Mode统一）
    private String side;            // 买卖方向：BUY/SELL
    private OrderStatus status;     // 订单状态
    
    private Double price;           // 下单价格
    private Double quantity;        // 数量
    private Double executedPrice;   // 实际成交价格
    private Double executedQty;     // 实际成交数量
    private Double slippage;        // 滑点（executedPrice - price）
    
    private Long orderId;           // 币安订单ID
    private String clientOrderId;   // 客户端订单ID
    
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime fillTime;     // 成交时间
    private LocalDateTime cancelTime;   // 撤销时间
    
    private String relatedOrderId;  // 关联订单ID（买单关联卖单，卖单关联买单）
    
    /**
     * 订单状态枚举
     */
    public enum OrderStatus {
        NEW,           // 新建（本地创建，未提交到交易所）
        SUBMITTED,     // 已提交到交易所
        FILLED,        // 已成交
        CANCELED,      // 已撤销
        EXPIRED        // 已过期（超过最大持仓时间）
    }
}

