package com.zq.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket 连接状态服务
 * 跟踪 WebSocket 连接状态，为 StrategyEngine 提供连接状态查询
 */
@Slf4j
@Service
public class ConnectionStatusService {

    // 所有已注册的 WebSocket 客户端
    private final CopyOnWriteArrayList<BinanceWebSocketClient> clients = new CopyOnWriteArrayList<>();

    /**
     * 注册 WebSocket 客户端
     * @param client WebSocket 客户端
     */
    public void registerClient(BinanceWebSocketClient client) {
        clients.add(client);
        client.setStateListener(new BinanceWebSocketClient.ConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(boolean active) {
                log.info("WebSocket connection state changed: client={}, active={}",
                    client.getStreamName(), active);
            }
        });
        log.info("WebSocket client registered: {}", client.getStreamName());
    }

    /**
     * 注销 WebSocket 客户端
     * @param client WebSocket 客户端
     */
    public void unregisterClient(BinanceWebSocketClient client) {
        clients.remove(client);
        log.info("WebSocket client unregistered: {}", client.getStreamName());
    }

    /**
     * 检查是否所有连接都可用
     * @return true 表示所有连接都可用，false 表示至少有一个连接不可用
     */
    public boolean isAllConnectionsActive() {
        if (clients.isEmpty()) {
            // 没有注册的客户端，默认返回 true（避免阻塞）
            return true;
        }

        for (BinanceWebSocketClient client : clients) {
            if (!client.isConnectionActive()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取活跃连接数量
     * @return 活跃连接数
     */
    public int getActiveConnectionCount() {
        int count = 0;
        for (BinanceWebSocketClient client : clients) {
            if (client.isConnectionActive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取总连接数量
     * @return 总���接数
     */
    public int getTotalConnectionCount() {
        return clients.size();
    }
}
