package com.zq.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 价差统计表
 * 统计每天不同bid/ask价格组合出现的次数
 * 用于分析价差分布，优化minProfitTick参数
 */
@Data
@Document(collection = "spread_stats")
@AllArgsConstructor
@NoArgsConstructor
public class SpreadStats {
    
    @Id
    private String id;  // 复合ID: USDCUSDT_2025-01-18_1.0005_1.0006
    
    private String symbol;
    private LocalDate date;            // 统计日期
    private Double bidPrice;           // 买价（4位小数）
    private Double askPrice;           // 卖价（4位小数）
    private Integer count;             // 出现次数
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    /**
     * 生成复合ID
     * 格式：USDCUSDT_2025-01-18_1.0005_1.0006
     */
    public static String generateId(String symbol, LocalDate date, double bid, double ask) {
        return String.format("%s_%s_%.4f_%.4f", symbol, date, bid, ask);
    }
    
    /**
     * 获取价差
     */
    public double getSpread() {
        if (bidPrice == null || askPrice == null) {
            return 0.0;
        }
        return askPrice - bidPrice;
    }
}

