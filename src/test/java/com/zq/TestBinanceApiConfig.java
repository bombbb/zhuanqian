package com.zq;

import com.zq.api.BinanceApiService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestBinanceApiConfig {

    @Bean
    public BinanceApiService binanceApiService() {
        return new BinanceApiService("https://testnet.binance.vision", "test", "test");
    }
}
