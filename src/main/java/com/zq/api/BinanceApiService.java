package com.zq.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 币安API服务
 * 提供与币安交易所的API交互功能
 * 参考：/Users/bao/java/usdt/src/main/java/com/usdt/account/BinanceAccount.java
 */
@Slf4j
@Service
public class BinanceApiService {
    
    // API端点
    private static final String ACCOUNT_ENDPOINT = "/api/v3/account";
    private static final String ORDER_ENDPOINT = "/api/v3/order";
    private static final String OPEN_ORDERS_ENDPOINT = "/api/v3/openOrders";
    private static final String TICKER_PRICE_ENDPOINT = "/api/v3/ticker/price";
    private static final String TICKER_BOOK_ENDPOINT = "/api/v3/ticker/bookTicker";
    private static final String SERVER_TIME_ENDPOINT = "/api/v3/time";
    private static final String EXCHANGE_INFO_ENDPOINT = "/api/v3/exchangeInfo";
    
    private final String baseUrl;
    private final String apiKey;
    private final String secretKey;
    private final HttpClient httpClient;
    private final Gson gson;
    
    // 服务器时间偏移量（毫秒），用于同步时间戳
    private Long serverTimeOffset = null;
    
    // 交易规则缓存（symbol -> SymbolFilter）
    private final Map<String, SymbolFilter> symbolFilters = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * 构造函数
     */
    public BinanceApiService(String baseUrl, String apiKey, String secretKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        
        log.info("BinanceApiService initialized, baseUrl: {}", this.baseUrl);
    }
    
    // ==================== 账户信息相关 ====================
    
    /**
     * 获取服务器时间
     */
    public long getServerTime() throws IOException, InterruptedException {
        String response = sendPublicRequest(SERVER_TIME_ENDPOINT);
        JsonObject json = gson.fromJson(response, JsonObject.class);
        long serverTime = json.get("serverTime").getAsLong();
        
        // 计算并缓存时间偏移量
        long localTime = System.currentTimeMillis();
        serverTimeOffset = serverTime - localTime;
        log.debug("Server time offset: {} ms", serverTimeOffset);
        
        return serverTime;
    }
    
    /**
     * 获取同步的时间戳
     */
    private long getSyncedTimestamp() throws IOException, InterruptedException {
        if (serverTimeOffset == null) {
            getServerTime();
        }
        return System.currentTimeMillis() + serverTimeOffset;
    }
    
    /**
     * 获取账户信息
     */
    public AccountInfo getAccountInfo() throws IOException, InterruptedException {
        Map<String, String> params = new TreeMap<>();
        params.put("timestamp", String.valueOf(getSyncedTimestamp()));
        
        String response = sendSignedRequest("GET", ACCOUNT_ENDPOINT, params);
        JsonObject json = gson.fromJson(response, JsonObject.class);
        
        return parseAccountInfo(json);
    }
    
    /**
     * 获取特定资产的余额
     */
    public Balance getBalance(String asset) throws IOException, InterruptedException {
        AccountInfo accountInfo = getAccountInfo();
        return accountInfo.getBalance(asset);
    }
    
    // ==================== 订单相关 ====================
    
    /**
     * 下限价单
     */
    public OrderResult placeLimitOrder(String symbol, String side, double quantity, double price) 
            throws IOException, InterruptedException {
        Map<String, String> params = new TreeMap<>();
        params.put("symbol", symbol);
        params.put("side", side.toUpperCase());
        params.put("type", "LIMIT");
        params.put("quantity", formatQuantity(quantity));
        params.put("price", formatPrice(price));
        params.put("timeInForce", "GTC");
        params.put("timestamp", String.valueOf(getSyncedTimestamp()));
        
        String response = sendSignedRequest("POST", ORDER_ENDPOINT, params);
        JsonObject json = gson.fromJson(response, JsonObject.class);
        
        return parseOrderResult(json);
    }
    
    /**
     * 下市价单
     */
    public OrderResult placeMarketOrder(String symbol, String side, double quantity) 
            throws IOException, InterruptedException {
        Map<String, String> params = new TreeMap<>();
        params.put("symbol", symbol);
        params.put("side", side.toUpperCase());
        params.put("type", "MARKET");
        params.put("quantity", formatQuantity(quantity));
        params.put("timestamp", String.valueOf(getSyncedTimestamp()));
        
        String response = sendSignedRequest("POST", ORDER_ENDPOINT, params);
        JsonObject json = gson.fromJson(response, JsonObject.class);
        
        return parseOrderResult(json);
    }
    
    /**
     * 查询订单状态
     */
    public BinanceOrder getOrder(String symbol, long orderId) throws IOException, InterruptedException {
        Map<String, String> params = new TreeMap<>();
        params.put("symbol", symbol);
        params.put("orderId", String.valueOf(orderId));
        params.put("timestamp", String.valueOf(getSyncedTimestamp()));
        
        String response = sendSignedRequest("GET", ORDER_ENDPOINT, params);
        JsonObject json = gson.fromJson(response, JsonObject.class);
        
        return parseBinanceOrder(json);
    }
    
    /**
     * 获取当前挂单
     */
    public List<BinanceOrder> getOpenOrders(String symbol) throws IOException, InterruptedException {
        Map<String, String> params = new TreeMap<>();
        params.put("timestamp", String.valueOf(getSyncedTimestamp()));
        if (symbol != null && !symbol.isEmpty()) {
            params.put("symbol", symbol);
        }
        
        String response = sendSignedRequest("GET", OPEN_ORDERS_ENDPOINT, params);
        JsonArray jsonArray = gson.fromJson(response, JsonArray.class);
        
        List<BinanceOrder> orders = new ArrayList<>();
        for (JsonElement element : jsonArray) {
            orders.add(parseBinanceOrder(element.getAsJsonObject()));
        }
        return orders;
    }
    
    /**
     * 撤销订单
     */
    public OrderResult cancelOrder(String symbol, long orderId) throws IOException, InterruptedException {
        Map<String, String> params = new TreeMap<>();
        params.put("symbol", symbol);
        params.put("orderId", String.valueOf(orderId));
        params.put("timestamp", String.valueOf(getSyncedTimestamp()));
        
        String response = sendSignedRequest("DELETE", ORDER_ENDPOINT, params);
        JsonObject json = gson.fromJson(response, JsonObject.class);
        
        return parseOrderResult(json);
    }
    
    /**
     * 批量撤销订单
     * @param symbol 交易对
     * @param orderIds 订单ID列表
     * @return 成功撤销的订单数量
     */
    public int cancelOrders(String symbol, List<Long> orderIds) {
        int successCount = 0;
        int failCount = 0;
        
        for (Long orderId : orderIds) {
            try {
                cancelOrder(symbol, orderId);
                successCount++;
                log.info("Canceled order: symbol={}, orderId={}", symbol, orderId);
            } catch (Exception e) {
                failCount++;
                log.warn("Failed to cancel order: symbol={}, orderId={}, error={}", 
                    symbol, orderId, e.getMessage());
            }
        }
        
        log.info("Batch cancel completed: symbol={}, success={}, failed={}, total={}", 
            symbol, successCount, failCount, orderIds.size());
        
        return successCount;
    }
    
    /**
     * 检查挂单数量是否超过阈值
     * @param symbol 交易对
     * @param threshold 阈值（建议150，币安限制200）
     * @return 当前挂单数量
     */
    public int checkOpenOrdersCount(String symbol) throws IOException, InterruptedException {
        List<BinanceOrder> openOrders = getOpenOrders(symbol);
        int count = openOrders.size();
        log.info("Current open orders count: symbol={}, count={}", symbol, count);
        return count;
    }
    
    // ==================== 市场数据相关 ====================
    
    /**
     * 获取当前价格
     */
    public double getCurrentPrice(String symbol) throws IOException, InterruptedException {
        String url = TICKER_PRICE_ENDPOINT + "?symbol=" + symbol;
        String response = sendPublicRequest(url);
        JsonObject json = gson.fromJson(response, JsonObject.class);
        return json.get("price").getAsDouble();
    }
    
    /**
     * 获取Book Ticker数据（包含最佳买价和卖价）
     * 这是公开API，不需要签名
     * 
     * @param symbol 交易对，如 "USDCUSDT"
     * @return TickerData对象，包含bid、ask、lastPrice等信息
     */
    public TickerData getBookTicker(String symbol) throws IOException, InterruptedException {
        String url = TICKER_BOOK_ENDPOINT + "?symbol=" + symbol;
        String response = sendPublicRequest(url);
        JsonObject json = gson.fromJson(response, JsonObject.class);
        
        TickerData ticker = new TickerData();
        ticker.setSymbol(json.get("symbol").getAsString());
        ticker.setBestBidPrice(new java.math.BigDecimal(json.get("bidPrice").getAsString()));
        ticker.setBestBidQty(new java.math.BigDecimal(json.get("bidQty").getAsString()));
        ticker.setBestAskPrice(new java.math.BigDecimal(json.get("askPrice").getAsString()));
        ticker.setBestAskQty(new java.math.BigDecimal(json.get("askQty").getAsString()));
        // Book Ticker 没有 lastPrice，使用 bid 和 ask 的中间价
        double midPrice = (ticker.getBestBidPrice().doubleValue() + ticker.getBestAskPrice().doubleValue()) / 2.0;
        ticker.setLastPrice(new java.math.BigDecimal(String.valueOf(midPrice)));
        ticker.setReceiveTime(java.time.LocalDateTime.now());
        
        return ticker;
    }
    
    // ==================== HTTP请求方法 ====================
    
    /**
     * 发送公开请求（不需要签名）
     */
    private String sendPublicRequest(String endpoint) throws IOException, InterruptedException {
        String url = baseUrl + endpoint;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            log.error("API request failed: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("API request failed: " + response.statusCode() + " - " + response.body());
        }
        
        return response.body();
    }
    
    /**
     * 发送签名请求
     */
    private String sendSignedRequest(String method, String endpoint, Map<String, String> params) 
            throws IOException, InterruptedException {
        // 构建查询字符串
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (queryString.length() > 0) {
                queryString.append("&");
            }
            queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                      .append("=")
                      .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        
        // 生成签名
        String signature = sign(queryString.toString());
        queryString.append("&signature=").append(signature);
        
        String url = baseUrl + endpoint + "?" + queryString;
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-MBX-APIKEY", apiKey)
                .header("Content-Type", "application/json");
        
        HttpRequest request;
        switch (method.toUpperCase()) {
            case "POST":
                request = requestBuilder.POST(HttpRequest.BodyPublishers.noBody()).build();
                break;
            case "DELETE":
                request = requestBuilder.DELETE().build();
                break;
            default:
                request = requestBuilder.GET().build();
        }
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        log.debug("API Response [{}]: {} - {}", method, response.statusCode(), response.body());
        
        if (response.statusCode() != 200) {
            log.error("API request failed: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("API request failed: " + response.statusCode() + " - " + response.body());
        }
        
        return response.body();
    }
    
    /**
     * HMAC SHA256 签名
     */
    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign request", e);
        }
    }
    
    /**
     * 格式化数量
     */
    private String formatQuantity(double quantity) {
        return String.format("%.8f", quantity).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    
    /**
     * 格式化价格
     */
    private String formatPrice(double price) {
        return String.format("%.8f", price).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    
    // ==================== 解析方法 ====================
    
    /**
     * 解析账户信息
     */
    private AccountInfo parseAccountInfo(JsonObject json) {
        AccountInfo info = new AccountInfo();
        info.setMakerCommission(json.get("makerCommission").getAsInt());
        info.setTakerCommission(json.get("takerCommission").getAsInt());
        info.setBuyerCommission(json.get("buyerCommission").getAsInt());
        info.setSellerCommission(json.get("sellerCommission").getAsInt());
        info.setCanTrade(json.get("canTrade").getAsBoolean());
        info.setCanWithdraw(json.get("canWithdraw").getAsBoolean());
        info.setCanDeposit(json.get("canDeposit").getAsBoolean());
        info.setUpdateTime(json.get("updateTime").getAsLong());
        
        List<Balance> balances = new ArrayList<>();
        JsonArray balancesArray = json.getAsJsonArray("balances");
        for (JsonElement element : balancesArray) {
            JsonObject balanceObj = element.getAsJsonObject();
            Balance balance = new Balance();
            balance.setAsset(balanceObj.get("asset").getAsString());
            balance.setFree(balanceObj.get("free").getAsDouble());
            balance.setLocked(balanceObj.get("locked").getAsDouble());
            balances.add(balance);
        }
        info.setBalances(balances);
        
        return info;
    }
    
    /**
     * 解析订单结果
     */
    private OrderResult parseOrderResult(JsonObject json) {
        OrderResult result = new OrderResult();
        result.setSymbol(getStringOrNull(json, "symbol"));
        result.setOrderId(getLongOrZero(json, "orderId"));
        result.setClientOrderId(getStringOrNull(json, "clientOrderId"));
        result.setTransactTime(getLongOrZero(json, "transactTime"));
        result.setPrice(getDoubleOrZero(json, "price"));
        result.setOrigQty(getDoubleOrZero(json, "origQty"));
        result.setExecutedQty(getDoubleOrZero(json, "executedQty"));
        result.setCummulativeQuoteQty(getDoubleOrZero(json, "cummulativeQuoteQty"));
        result.setStatus(getStringOrNull(json, "status"));
        result.setTimeInForce(getStringOrNull(json, "timeInForce"));
        result.setType(getStringOrNull(json, "type"));
        result.setSide(getStringOrNull(json, "side"));
        return result;
    }
    
    /**
     * 解析币安订单
     */
    private BinanceOrder parseBinanceOrder(JsonObject json) {
        BinanceOrder order = new BinanceOrder();
        order.setSymbol(getStringOrNull(json, "symbol"));
        order.setOrderId(getLongOrZero(json, "orderId"));
        order.setClientOrderId(getStringOrNull(json, "clientOrderId"));
        order.setPrice(getDoubleOrZero(json, "price"));
        order.setOrigQty(getDoubleOrZero(json, "origQty"));
        order.setExecutedQty(getDoubleOrZero(json, "executedQty"));
        order.setCummulativeQuoteQty(getDoubleOrZero(json, "cummulativeQuoteQty"));
        order.setStatus(getStringOrNull(json, "status"));
        order.setTimeInForce(getStringOrNull(json, "timeInForce"));
        order.setType(getStringOrNull(json, "type"));
        order.setSide(getStringOrNull(json, "side"));
        order.setTime(getLongOrZero(json, "time"));
        order.setUpdateTime(getLongOrZero(json, "updateTime"));
        return order;
    }
    
    private String getStringOrNull(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }
    
    private double getDoubleOrZero(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : 0.0;
    }
    
    private long getLongOrZero(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : 0L;
    }
    
    // ==================== 交易规则相关 ====================
    
    /**
     * 获取交易对的交易规则
     */
    public SymbolFilter getSymbolFilter(String symbol) throws IOException, InterruptedException {
        // 先从缓存中查找
        if (symbolFilters.containsKey(symbol)) {
            return symbolFilters.get(symbol);
        }
        
        // 从API获取
        String url = EXCHANGE_INFO_ENDPOINT + "?symbol=" + symbol;
        String response = sendPublicRequest(url);
        JsonObject json = gson.fromJson(response, JsonObject.class);
        
        JsonArray symbols = json.getAsJsonArray("symbols");
        if (symbols.size() == 0) {
            throw new RuntimeException("Symbol not found: " + symbol);
        }
        
        JsonObject symbolInfo = symbols.get(0).getAsJsonObject();
        JsonArray filters = symbolInfo.getAsJsonArray("filters");
        
        SymbolFilter filter = new SymbolFilter();
        filter.setSymbol(symbol);
        
        // 解析过滤器
        for (JsonElement filterElement : filters) {
            JsonObject filterObj = filterElement.getAsJsonObject();
            String filterType = filterObj.get("filterType").getAsString();
            
            if ("LOT_SIZE".equals(filterType)) {
                filter.setMinQty(filterObj.get("minQty").getAsDouble());
                filter.setMaxQty(filterObj.get("maxQty").getAsDouble());
                filter.setStepSize(filterObj.get("stepSize").getAsDouble());
            } else if ("PRICE_FILTER".equals(filterType)) {
                filter.setMinPrice(filterObj.get("minPrice").getAsDouble());
                filter.setMaxPrice(filterObj.get("maxPrice").getAsDouble());
                filter.setTickSize(filterObj.get("tickSize").getAsDouble());
            } else if ("NOTIONAL".equals(filterType) || "MIN_NOTIONAL".equals(filterType)) {
                // NOTIONAL filter - 订单名义价值要求
                if (filterObj.has("minNotional")) {
                    filter.setMinNotional(filterObj.get("minNotional").getAsDouble());
                }
            }
        }
        
        // 缓存
        symbolFilters.put(symbol, filter);
        
        log.info("Loaded symbol filter for {}: minQty={}, stepSize={}, minNotional={}", 
            symbol, filter.getMinQty(), filter.getStepSize(), filter.getMinNotional());
        
        return filter;
    }
    
    /**
     * 根据交易规则调整数量
     * 
     * @param symbol 交易对
     * @param quantity 原始数量
     * @return 调整后的数量
     */
    public double adjustQuantity(String symbol, double quantity) throws IOException, InterruptedException {
        SymbolFilter filter = getSymbolFilter(symbol);
        
        double minQty = filter.getMinQty();
        double maxQty = filter.getMaxQty();
        double stepSize = filter.getStepSize();
        
        // 检查最小值
        if (quantity < minQty) {
            log.debug("Quantity {} is less than minQty {}, using minQty", quantity, minQty);
            return minQty;
        }
        
        // 检查最大值
        if (quantity > maxQty) {
            log.warn("Quantity {} exceeds maxQty {}, using maxQty", quantity, maxQty);
            return maxQty;
        }
        
        // 按 stepSize 向下舍入
        double adjusted = Math.floor(quantity / stepSize) * stepSize;
        
        // 确保不小于最小值
        if (adjusted < minQty) {
            adjusted = minQty;
        }
        
        // 处理精度问题，保留足够的小数位
        int decimalPlaces = getDecimalPlaces(stepSize);
        adjusted = roundToDecimalPlaces(adjusted, decimalPlaces);
        
        log.debug("Adjusted quantity: {} -> {} (stepSize={})", quantity, adjusted, stepSize);
        
        return adjusted;
    }
    
    /**
     * 按 stepSize 向下舍入数量（不做 minQty 上调）
     */
    public double adjustQuantityDown(String symbol, double quantity) throws IOException, InterruptedException {
        SymbolFilter filter = getSymbolFilter(symbol);
        double stepSize = filter.getStepSize();
        double adjusted = Math.floor(quantity / stepSize) * stepSize;
        
        int decimalPlaces = getDecimalPlaces(stepSize);
        adjusted = roundToDecimalPlaces(adjusted, decimalPlaces);
        return adjusted;
    }
    
    /**
     * 根据交易规则调整数量，同时验证 NOTIONAL 要求
     * 
     * @param symbol 交易对
     * @param quantity 原始数量
     * @param price 订单价格
     * @return 调整后的数量
     */
    public double adjustQuantityAndNotional(String symbol, double quantity, double price) 
            throws IOException, InterruptedException {
        SymbolFilter filter = getSymbolFilter(symbol);
        
        // 先按 LOT_SIZE 调整
        double adjusted = adjustQuantity(symbol, quantity);
        
        // 验证 NOTIONAL（订单名义价值）
        double minNotional = filter.getMinNotional();
        if (minNotional > 0) {
            double notional = adjusted * price;
            if (notional < minNotional) {
                // 订单金额不足，需要增加数量
                double requiredQty = minNotional / price;
                
                // 按 stepSize 向上舍入到最小满足金额
                double stepSize = filter.getStepSize();
                adjusted = Math.ceil(requiredQty / stepSize) * stepSize;
                
                // 处理精度
                int decimalPlaces = getDecimalPlaces(stepSize);
                adjusted = roundToDecimalPlaces(adjusted, decimalPlaces);
                
                log.info("Adjusted quantity to meet minNotional: {} -> {} (price={}, minNotional={}, notional={})", 
                    quantity, adjusted, price, minNotional, adjusted * price);
            }
        }
        
        return adjusted;
    }
    
    /**
     * 获取小数位数
     */
    private int getDecimalPlaces(double value) {
        String str = String.valueOf(value);
        int dotIndex = str.indexOf('.');
        if (dotIndex < 0) {
            return 0;
        }
        // 移除尾部的0
        str = str.replaceAll("0+$", "");
        dotIndex = str.indexOf('.');
        if (dotIndex < 0) {
            return 0;
        }
        return str.length() - dotIndex - 1;
    }
    
    /**
     * 四舍五入到指定小数位
     */
    private double roundToDecimalPlaces(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
    
    /**
     * 根据交易规则调整价格
     * 
     * @param symbol 交易对
     * @param price 原始价格
     * @return 调整后的价格
     */
    public double adjustPrice(String symbol, double price) throws IOException, InterruptedException {
        SymbolFilter filter = getSymbolFilter(symbol);
        
        double minPrice = filter.getMinPrice();
        double maxPrice = filter.getMaxPrice();
        double tickSize = filter.getTickSize();
        
        // 检查最小值
        if (price < minPrice) {
            log.debug("Price {} is less than minPrice {}, using minPrice", price, minPrice);
            return minPrice;
        }
        
        // 检查最大值
        if (price > maxPrice) {
            log.warn("Price {} exceeds maxPrice {}, using maxPrice", price, maxPrice);
            return maxPrice;
        }
        
        // 按 tickSize 向下舍入
        double adjusted = Math.floor(price / tickSize) * tickSize;
        
        // 确保不小于最小值
        if (adjusted < minPrice) {
            adjusted = minPrice;
        }
        
        // 处理精度问题，保留足够的小数位
        int decimalPlaces = getDecimalPlaces(tickSize);
        adjusted = roundToDecimalPlaces(adjusted, decimalPlaces);
        
        log.debug("Adjusted price: {} -> {} (tickSize={})", price, adjusted, tickSize);
        
        return adjusted;
    }
    
    /**
     * 交易规则过滤器
     */
    @lombok.Data
    public static class SymbolFilter {
        private String symbol;
        
        // LOT_SIZE
        private double minQty;
        private double maxQty;
        private double stepSize;
        
        // PRICE_FILTER
        private double minPrice;
        private double maxPrice;
        private double tickSize;
        
        // NOTIONAL (MIN_NOTIONAL or NOTIONAL)
        private double minNotional;
    }
}
