package com.zq.strategy;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DepthAnalyzer {

    /**
     * @param bids List of [price, qty]
     * @param currentPrice 最新成交价
     */
    public static double calcSupportRatio(
            List<double[]> bids,
            double currentPrice,
            double supportRange
    ) {
        double supportVolume = 0;
        double totalVolume = 0;

        for (double[] bid : bids) {
            double price = bid[0];
            double qty = bid[1];

            if (price >= currentPrice - supportRange) {
                supportVolume += qty;
            }
            totalVolume += qty;
        }

        return totalVolume == 0 ? 0 : supportVolume / totalVolume;
    }
}
