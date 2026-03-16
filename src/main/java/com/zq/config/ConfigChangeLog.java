package com.zq.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 配置变更日志表
 * 记录每次配置变更的详细信息，用于审计和回溯
 */
@Data
@Document(collection = "config_change_logs")
@AllArgsConstructor
@NoArgsConstructor
public class ConfigChangeLog {
    
    @Id
    private String id;
    
    private String configId;         // 配置ID，如 USDCUSDT_TESTNET
    private String symbol;
    private String mode;
    
    private ChangeSource source;     // 变更来源
    private String reason;           // 变更原因描述
    
    private Map<String, Object> oldValues;  // 变更前的值
    private Map<String, Object> newValues;  // 变更后的值
    
    private LocalDateTime changeTime;       // 变更时间
    private String operator;                // 操作者（如果是手动变更）
    
    /**
     * 变更来源
     */
    public enum ChangeSource {
        MANUAL,              // 手动变更（通过脚本或管理接口）
        AUTO_TREND,          // 自动变更（趋势分析触发）
        AUTO_SPREAD_STATS,   // 自动变更（价差统计触发）
        AUTO_DEPTH_STATS,    // 自动变更（深度统计触发）
        AUTO_TRADE_STATS     // 自动变更（交易统计触发）
    }
}

