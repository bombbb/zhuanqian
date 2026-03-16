package com.zq.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ticker数据模型
 * 
 * 存储Binance WebSocket推送的Ticker（价格快照）数据
 * 包括最佳买价、最佳卖价、最新成交价等信息
 */
public class TickerData {
    /** 交易对符号，如 "USDCUSDT", "TUSDUSDT" */
    private String symbol;
    
    /** 最佳买价（Best Bid Price） */
    private BigDecimal bestBidPrice;
    
    /** 最佳买量（Best Bid Quantity） */
    private BigDecimal bestBidQty;
    
    /** 最佳卖价（Best Ask Price） */
    private BigDecimal bestAskPrice;
    
    /** 最佳卖量（Best Ask Quantity） */
    private BigDecimal bestAskQty;
    
    /** 最新成交价（Last Price） */
    private BigDecimal lastPrice;
    
    /** 24小时成交量 */
    private BigDecimal volume;
    
    /** 数据接收时间 */
    private LocalDateTime receiveTime;

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getBestBidPrice() {
        return bestBidPrice;
    }

    public void setBestBidPrice(BigDecimal bestBidPrice) {
        this.bestBidPrice = bestBidPrice;
    }

    public BigDecimal getBestBidQty() {
        return bestBidQty;
    }

    public void setBestBidQty(BigDecimal bestBidQty) {
        this.bestBidQty = bestBidQty;
    }

    public BigDecimal getBestAskPrice() {
        return bestAskPrice;
    }

    public void setBestAskPrice(BigDecimal bestAskPrice) {
        this.bestAskPrice = bestAskPrice;
    }

    public BigDecimal getBestAskQty() {
        return bestAskQty;
    }

    public void setBestAskQty(BigDecimal bestAskQty) {
        this.bestAskQty = bestAskQty;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public LocalDateTime getReceiveTime() {
        return receiveTime;
    }

    public void setReceiveTime(LocalDateTime receiveTime) {
        this.receiveTime = receiveTime;
    }
}

