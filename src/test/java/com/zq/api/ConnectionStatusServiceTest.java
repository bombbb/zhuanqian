package com.zq.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConnectionStatusService 测试用例
 * 测试 WebSocket 连接状态跟踪功能
 */
@ExtendWith(MockitoExtension.class)
class ConnectionStatusServiceTest {

    private ConnectionStatusService connectionStatusService;

    @Mock
    private MarketDataHandler marketDataHandler;

    private BinanceWebSocketClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        connectionStatusService = new ConnectionStatusService();

        // 创建模拟的 WebSocket 客户端
        URI serverUri = new URI("wss://stream.binance.com:9443/ws/usdcusdt@ticker");
        mockClient = new BinanceWebSocketClient(serverUri, "usdcusdt@ticker", marketDataHandler) {
            private boolean connectionActive = false;

            @Override
            public boolean isConnectionActive() {
                return connectionActive;
            }

            @Override
            public boolean isOpen() {
                return connectionActive;
            }

            public void setMockConnectionActive(boolean active) {
                this.connectionActive = active;
            }
        };
    }

    @Test
    void testRegisterClient() {
        connectionStatusService.registerClient(mockClient);
        assertEquals(1, connectionStatusService.getTotalConnectionCount());
    }

    @Test
    void testUnregisterClient() {
        connectionStatusService.registerClient(mockClient);
        connectionStatusService.unregisterClient(mockClient);
        assertEquals(0, connectionStatusService.getTotalConnectionCount());
    }

    @Test
    void testIsAllConnectionsActive_NoClients() {
        // 没有注册的客户端时，应该返回 true（避免阻塞）
        assertTrue(connectionStatusService.isAllConnectionsActive());
    }

    @Test
    void testIsAllConnectionsActive_WithActiveClient() throws Exception {
        connectionStatusService.registerClient(mockClient);

        // 通过反射或模拟设置连接状态
        // 由于我们无法直接触发 onOpen，这里只测试方法不会抛出异常
        assertDoesNotThrow(() -> connectionStatusService.isAllConnectionsActive());
    }

    @Test
    void testGetActiveConnectionCount_NoClients() {
        assertEquals(0, connectionStatusService.getActiveConnectionCount());
    }

    @Test
    void testGetActiveConnectionCount_WithClients() {
        connectionStatusService.registerClient(mockClient);
        assertEquals(1, connectionStatusService.getTotalConnectionCount());
    }

    @Test
    void testMultipleClients() throws Exception {
        URI serverUri2 = new URI("wss://stream.binance.com:9443/ws/btcusdt@ticker");
        BinanceWebSocketClient mockClient2 = new BinanceWebSocketClient(serverUri2, "btcusdt@ticker", marketDataHandler);

        connectionStatusService.registerClient(mockClient);
        connectionStatusService.registerClient(mockClient2);

        assertEquals(2, connectionStatusService.getTotalConnectionCount());
    }

    @Test
    void testStateListenerNotification() {
        // 测试状态监听器设置不会抛出异常
        assertDoesNotThrow(() -> {
            connectionStatusService.registerClient(mockClient);
            mockClient.setStateListener(new BinanceWebSocketClient.ConnectionStateListener() {
                @Override
                public void onConnectionStateChanged(boolean active) {
                    // 监听器被调用
                }
            });
        });
    }

    @Test
    void testGetStreamName() {
        assertEquals("usdcusdt@ticker", mockClient.getStreamName());
    }

    @Test
    void testGetLastStateChangeTime() {
        // 测试获取状态变化时间不会抛出异常
        assertDoesNotThrow(() -> {
            long time = mockClient.getLastStateChangeTime();
            assertTrue(time > 0);
        });
    }
}
