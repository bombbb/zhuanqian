package com.zq.api;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Binance WebSocket客户端
 * 
 * 负责与Binance WebSocket服务器建立连接，接收实时市场数据
 * 具备自动重连机制，确保连接稳定性
 * 支持单流和组合流两种模式
 * 
 * 功能：
 * - 建立和维护WebSocket连接
 * - 接收服务器推送的市场数据（支持组合流格式）
 * - 自动重连（最多10次）
 * - 将接收到的数据传递给MarketDataHandler处理
 * 
 * 组合流格式：
 * - URL: wss://stream.binance.com:9443/stream?streams=usdcusdt@ticker/tusdusdt@ticker
 * - 消息格式: {"stream":"usdcusdt@ticker","data":{...}}
 */
public class BinanceWebSocketClient extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketClient.class);
    
    /** 市场数据处理器 */
    private final MarketDataHandler dataHandler;
    
    /** 流名称，用于标识连接的数据流类型（单流）或组合流标识（组合流） */
    private final String streamName;
    
    /** 是否为组合流 */
    private final boolean isCombinedStream;
    
    /** 是否应该自动重连 */
    private volatile boolean shouldReconnect = true;
    
    /** 当前重连尝试次数 */
    private volatile int reconnectAttempts = 0;
    
    /** 最大重连尝试次数 */
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    
    /** 重连延迟时间（毫秒） */
    private static final long RECONNECT_DELAY_MS = 5000;
    
    /** 重连线程池（用于在单独线程中执行重连） */
    private final ScheduledExecutorService reconnectExecutor;

    /**
     * 构造函数
     * 
     * @param serverUri WebSocket服务器URI（可以是单流或组合流URL）
     * @param streamName 流名称，如 "fdusdusdt@ticker" 或组合流标识 "combined"
     * @param dataHandler 数据处理器，用于处理接收到的消息
     */
    public BinanceWebSocketClient(URI serverUri, String streamName, MarketDataHandler dataHandler) {
        super(serverUri);
        this.streamName = streamName;
        this.dataHandler = dataHandler;
        
        // 判断是否为组合流（URL包含/stream?streams=）
        this.isCombinedStream = serverUri.toString().contains("/stream?streams=");
        
        // 初始化重连线程池
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WebSocketReconnect-" + streamName);
            t.setDaemon(true);
            return t;
        });
        
        // 设置连接丢失超时（90秒）
        // Binance服务器每20秒发送ping，客户端需要在1分钟内回复pong
        // 设置90秒超时，确保在服务器断开连接前能够检测到连接问题
        // 如果90秒内没有收到任何数据（包括ping/pong），将触发关闭
        this.setConnectionLostTimeout(90);
        
        if (isCombinedStream) {
            logger.info("BinanceWebSocketClient initialized for combined stream: {}", serverUri);
        } else {
            logger.info("BinanceWebSocketClient initialized for stream: {}", streamName);
        }
    }

    /**
     * WebSocket连接成功回调
     * 
     * 当连接建立成功时调用，重置重连计数器
     * 
     * @param handshake 服务器握手信息
     */
    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("WebSocket connection established successfully: {}", streamName);
        reconnectAttempts = 0;
        logger.info("Connection ready, waiting for market data...");
    }

    /**
     * 接收到消息回调
     * 
     * 当服务器推送数据时调用，将消息传递给数据处理器
     * 支持单流和组合流两种消息格式
     * 
     * @param message JSON格式的消息字符串
     */
    @Override
    public void onMessage(String message) {
        try {
            // 组合流消息格式：{"stream":"usdcusdt@ticker","data":{...}}
            // 单流消息格式：直接是ticker数据
            // MarketDataHandler会处理两种格式
            dataHandler.handleMessage(streamName, message);
        } catch (Exception e) {
            logger.error("Error processing message from stream: {}", streamName, e);
        }
    }

    /**
     * WebSocket连接关闭回调
     * 
     * 当连接关闭时调用，如果允许重连且未达到最大重连次数，则自动重连
     * 
     * 注意：reconnect() 方法不能在 WebSocket 线程中调用，必须在单独的线程中执行
     * 
     * @param code 关闭代码
     * @param reason 关闭原因
     * @param remote 是否由远程端关闭
     */
    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("WebSocket connection closed: {} (code: {}, reason: {}, remote: {})", 
                streamName, code, reason, remote);
        
        // 错误代码 1006 表示异常关闭（abnormal closure），可能是：
        // 1. 网络中断（如电脑熄屏导致）
        // 2. 服务器端主动关闭连接
        // 3. 超时
        if (code == 1006) {
            logger.warn("Abnormal closure detected (code 1006), possible causes: network interruption, server-side close, or timeout");
        }
        
        if (shouldReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            logger.info("Scheduling reconnect attempt ({}/{}): {} (delay: {}ms)", 
                    reconnectAttempts, MAX_RECONNECT_ATTEMPTS, streamName, RECONNECT_DELAY_MS);
            
            // 在单独的线程中执行重连，避免在 WebSocket 线程中调用 reconnect()
            reconnectExecutor.schedule(() -> {
                try {
                    logger.info("Executing reconnect: {}", streamName);
                    reconnect();
                } catch (Exception e) {
                    logger.error("Reconnect failed: {}", streamName, e);
                }
            }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
        } else if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            logger.error("Max reconnect attempts ({}) reached, stopping reconnect: {}", 
                    MAX_RECONNECT_ATTEMPTS, streamName);
        }
    }

    /**
     * WebSocket错误回调
     * 
     * 当连接发生错误时调用
     * 
     * @param ex 异常信息
     */
    @Override
    public void onError(Exception ex) {
        logger.error("WebSocket error occurred for stream: {}", streamName, ex);
    }

    /**
     * 处理WebSocket Ping帧
     * 
     * 根据Binance文档要求：
     * - WebSocket服务器每20秒发送一个ping帧
     * - 客户端必须在1分钟内回复pong帧
     * - pong帧的payload应该是ping帧payload的副本
     * 
     * Java-WebSocket库会自动回复pong，但为了确保符合Binance要求，
     * 我们显式处理ping帧并立即回复pong
     * 
     * @param conn WebSocket连接
     * @param f Ping帧数据
     */
    @Override
    public void onWebsocketPing(org.java_websocket.WebSocket conn, Framedata f) {
        // 记录ping接收（TRACE级别，避免日志过多，但可以通过日志配置启用）
        logger.trace("Received ping frame from Binance server for stream: {}", streamName);
        
        // 调用父类方法，父类会自动回复pong帧（payload与ping相同）
        // 这符合Binance的要求：pong的payload应该是ping的payload的副本
        super.onWebsocketPing(conn, f);
    }

    /**
     * 处理WebSocket Pong帧
     * 
     * 当收到pong帧时调用（通常是对我们发送的ping的回复）
     * Binance服务器会主动发送ping，我们回复pong，所以这里主要用于日志记录
     * 
     * @param conn WebSocket连接
     * @param f Pong帧数据
     */
    @Override
    public void onWebsocketPong(org.java_websocket.WebSocket conn, Framedata f) {
        // 记录pong接收（TRACE级别，避免日志过多，但可以通过日志配置启用）
        logger.trace("Received pong frame from Binance server for stream: {}", streamName);
        
        // 调用父类方法
        super.onWebsocketPong(conn, f);
    }

    /**
     * 停止自动重连
     * 
     * 在程序关闭时调用，防止继续尝试重连
     */
    public void stopReconnect() {
        logger.info("Stopping reconnect mechanism for stream: {}", streamName);
        this.shouldReconnect = false;
        
        // 优雅关闭线程池
        reconnectExecutor.shutdown();
        try {
            // 等待正在执行的任务完成，最多等待5秒
            if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Reconnect executor did not terminate gracefully, forcing shutdown");
                reconnectExecutor.shutdownNow();
                // 再等待一下，确保强制关闭完成
                if (!reconnectExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.error("Reconnect executor did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for reconnect executor to terminate");
            reconnectExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Reconnect mechanism stopped for stream: {}", streamName);
    }

    /**
     * 获取流名称
     * 
     * @return 流名称
     */
    public String getStreamName() {
        return streamName;
    }

    /**
     * 主程序入口
     * 
     * 启动Binance WebSocket连接，实时接收FDUSDUSDT交易对的市场数据
     * 打印最佳买价（Best Bid）和最佳卖价（Best Ask）
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("USDT Market Data Monitor Starting...");
        logger.info("========================================");
        
        /** Binance WebSocket基础URL */
        final String BINANCE_WS_BASE_URL = "wss://stream.binance.com:9443/ws/";
        
        /** FDUSDUSDT Ticker流名称 */
        final String FDUSDUSDT_TICKER_STREAM = "fdusdusdt@ticker";
        
        /** WebSocket客户端实例 */
        BinanceWebSocketClient webSocketClient = null;
        
        try {
            // 创建市场数据处理器
            MarketDataHandler dataHandler = new MarketDataHandler();
            logger.info("MarketDataHandler created");
            
            // 构建WebSocket URI
            String streamUrl = BINANCE_WS_BASE_URL + FDUSDUSDT_TICKER_STREAM;
            URI serverUri = new URI(streamUrl);
            logger.info("Connecting to Binance WebSocket: {}", streamUrl);
            
            // 创建WebSocket客户端
            webSocketClient = new BinanceWebSocketClient(serverUri, FDUSDUSDT_TICKER_STREAM, dataHandler);
            
            // 建立连接
            webSocketClient.connect();
            logger.info("WebSocket connection initiated, waiting for connection...");
            
            // 注册关闭钩子，确保程序退出时优雅关闭连接
            final BinanceWebSocketClient finalClient = webSocketClient;
            Thread shutdownHook = new Thread(() -> {
                logger.info("Shutting down application...");
                if (finalClient != null) {
                    try {
                        finalClient.stopReconnect();
                        finalClient.close();
                        logger.info("WebSocket connection closed");
                    } catch (Exception e) {
                        logger.error("Error closing WebSocket connection", e);
                    }
                }
                logger.info("Application shutdown complete");
            }, "ShutdownHook");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            
            // 保持主线程运行
            logger.info("Application running, monitoring FDUSDUSDT market data...");
            logger.info("Press Ctrl+C to stop");
            
            // 等待连接建立（最多等待10秒）
            int waitCount = 0;
            while (!webSocketClient.isOpen() && waitCount < 20) {
                Thread.sleep(500);
                waitCount++;
            }
            
            if (webSocketClient.isOpen()) {
                logger.info("WebSocket connection established successfully!");
            } else {
                logger.warn("WebSocket connection not established after 10 seconds, but will continue trying...");
            }
            
            // 使用 Object.wait() 替代 Thread.sleep(Long.MAX_VALUE)，更优雅地保持程序运行
            Object lock = new Object();
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.info("Main thread interrupted, shutting down...");
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            System.exit(1);
        }
    }
}

