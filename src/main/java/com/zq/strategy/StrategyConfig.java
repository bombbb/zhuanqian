package com.zq.strategy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "strategy_config")
@AllArgsConstructor
@NoArgsConstructor
public class StrategyConfig {

    public enum Mode {
        SIMULATION,
        TESTNET,
        PRODUCTION
    }

    @Id
    private String id;   // 例如：USDCUSDT_TESTNET

    private String symbol;
    private Mode mode;
    private boolean enabled;

    // ========== 交易参数 ==========
    
    // 参考价格（锚定价）- 仅作为参考，用于计算偏离度
    // 对于稳定币，通常设置为锚定价格（如1.0）
    private double referencePrice;
    
    // 最高买入价 - 真正的买入价格阈值
    // 只有当市场价格低于此值时才考虑买入
    // 例如：设置为1.0005，表示价格高于1.0005时不买
    private double maxBuyPrice;
    
    // 最大单次买入金额（USDT）- 每次下单的最大金额
    // 实际下单金额会根据价格偏离程度动态计算，但不超过此值
    private double maxBuyAmountUsdt;

    // 最高买入价安全上限（硬阈值）
    // 即使动态调整，也不会超过此值
    private double maxBuyPriceCeiling = 1.0004;
    
    // 最小利润空间（价格差）
    private double minProfitTick;
    
    // 最大持仓时间（秒）
    private int maxHoldSeconds;

    // 买单最大挂单时间（秒）
    private int maxBuyOpenSeconds = 120;
    
    // 最大总投入金额限制
    private double maxTotalInvestUsdt;
    
    // ========== 深度分析参数 ==========
    
    // 最小支撑比率（0-1）
    private double minSupportRatio;
    
    // 支撑范围 - 近/中/远
    private double supportRangeNear;
    private double supportRangeMid;
    private double supportRangeFar;

    private String testnetApiUrl;
    private String testnetApiKey;
    private String testnetSecretKey;

    private String productionApiUrl;
    private String productionApiKey;
    private String productionSecretKey;
    
    // WebSocket URL配置
    // 注意：测试网和正式网的行情都使用正式网的WebSocket
    private String marketDataWsUrl;  // 行情WebSocket URL，默认使用正式网
    
    // 价格日志配置
    private long priceLogIntervalSeconds = 5;  // 价格日志打印间隔（秒），默认5秒

    // ========== 近期价格区间动态更新 ==========
    // 是否基于最近统计数据动态调整最高买入价
    private boolean enableRecentPriceBandAdjust = true;
    // 回看天数
    private int recentPriceLookbackDays = 30;
    // 使用的分位数（0-1）
    private double recentPricePercentile = 0.85;
    // 安全边际（从分位数价格下调）
    private double recentPriceSafetyMargin = 0.00005;
    
    // ========== 趋势分析和动态调价配置 ==========
    
    // 是否启用动态价格调整
    private boolean enableDynamicPriceAdjustment = true;
    
    // 趋势分析间隔（秒）- 每隔多久分析一次趋势并调整价格
    private long trendAnalysisIntervalSeconds = 3600;  // 默认1小时
    
    // 趋势分析回看天数
    private int trendAnalysisLookbackDays = 7;  // 默认分析最近7天数据
    
    // 动态调价的最小置信度阈值
    private double minConfidenceForAdjustment = 0.6;  // 置信度 < 0.6 不调整
}
