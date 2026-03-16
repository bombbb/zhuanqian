package com.zq.position;

import com.zq.strategy.StrategyConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 持仓实体
 * 记录当前持有的USDC数量、平均买入价格、未实现盈亏等信息
 * 每个symbol+mode组合只有一条记录
 */
@Data
@Document(collection = "positions")
@AllArgsConstructor
@NoArgsConstructor
public class Position {
    
    @Id
    private String id;
    
    private String symbol;              // 交易对，如 USDCUSDT
    private StrategyConfig.Mode mode;   // 运行模式（使用StrategyConfig.Mode统一）
    
    private Double quantity;            // 持仓数量（USDC）
    private Double avgBuyPrice;         // 平均买入价格
    private Double currentPrice;        // 当前价格
    private Double unrealizedPnl;       // 未实现盈亏（USDT）
    private Double totalInvestedUsdt;   // 总投入USDT金额
    
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
    
    /**
     * 获取持仓市值（当前价格 * 持仓数量）
     */
    public double getPositionValue() {
        if (quantity == null || currentPrice == null) {
            return 0.0;
        }
        return quantity * currentPrice;
    }
    
    /**
     * 获取未实现盈亏百分比
     */
    public double getUnrealizedPnlPercent() {
        if (totalInvestedUsdt == null || totalInvestedUsdt == 0) {
            return 0.0;
        }
        if (unrealizedPnl == null) {
            return 0.0;
        }
        return (unrealizedPnl / totalInvestedUsdt) * 100;
    }
}

