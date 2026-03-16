package com.zq.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 深度区间统计表
 * 统计不同价格区间和支撑比率区间的深度数据
 * 用于分析深度分布，优化minSupportRatio参数
 */
@Data
@Document(collection = "depth_stats")
@AllArgsConstructor
@NoArgsConstructor
public class DepthStats {
    
    @Id
    private String id;  // 复合ID: USDCUSDT_2025-01-18_0.9995-1.0000_0.6-0.7
    
    private String symbol;
    private LocalDate date;
    private String priceRangeBucket;      // 价格区间桶，如 "0.9995-1.0000"
    private String supportRatioBucket;    // 支撑比率桶，如 "0.6-0.7"
    private Integer count;                // 出现次数
    private Double avgVolume;             // 平均深度量（累计求平均）
    
    private LocalDateTime updateTime;
    
    /**
     * 生成价格区间桶
     * 按0.0005区间分桶
     * 例如：价格1.0003 -> "1.0000-1.0005"
     */
    public static String generatePriceRangeBucket(double price) {
        double bucketSize = 0.0005;
        double bucket = Math.floor(price / bucketSize) * bucketSize;
        return String.format("%.4f-%.4f", bucket, bucket + bucketSize);
    }
    
    /**
     * 生成支撑比率区间桶
     * 按0.1区间分桶
     * 例如：支撑比率0.65 -> "0.6-0.7"
     */
    public static String generateSupportRatioBucket(double ratio) {
        double bucketSize = 0.1;
        double bucket = Math.floor(ratio / bucketSize) * bucketSize;
        return String.format("%.1f-%.1f", bucket, bucket + bucketSize);
    }
    
    /**
     * 生成复合ID
     */
    public static String generateId(String symbol, LocalDate date, String priceRangeBucket, String supportRatioBucket) {
        return String.format("%s_%s_%s_%s", symbol, date, priceRangeBucket, supportRatioBucket);
    }
}

