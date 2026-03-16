package com.zq.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交易统计表
 * 统计每天的交易结果，包括盈亏、胜率、持仓时间、滑点等
 * 用于评估策略表现，优化maxHoldSeconds等参数
 */
@Data
@Document(collection = "trade_stats")
@AllArgsConstructor
@NoArgsConstructor
public class TradeStats {
    
    @Id
    private String id;  // 复合ID: USDCUSDT_2025-01-18
    
    private String symbol;
    private LocalDate date;
    
    private Integer totalTrades;       // 总交易次数（完整买卖对）
    private Integer profitTrades;      // 盈利次数
    private Integer lossTrades;        // 亏损次数
    
    private Double totalPnl;           // 总盈亏（USDT）
    private Double totalPnlPercent;    // 总盈亏百分比
    
    private Double avgHoldSeconds;     // 平均持仓时间（秒）
    private Double avgSlippage;        // 平均滑点
    
    private Double maxProfit;          // 最大单笔盈利
    private Double maxLoss;            // 最大单笔亏损
    
    private LocalDateTime updateTime;
    
    /**
     * 生成复合ID
     */
    public static String generateId(String symbol, LocalDate date) {
        return String.format("%s_%s", symbol, date);
    }
    
    /**
     * 获取胜率
     */
    public double getWinRate() {
        if (totalTrades == null || totalTrades == 0) {
            return 0.0;
        }
        if (profitTrades == null) {
            return 0.0;
        }
        return (profitTrades.doubleValue() / totalTrades) * 100;
    }
}

