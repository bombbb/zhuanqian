package com.zq.order;

import com.zq.strategy.StrategyConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * 订单管理服务
 * 使用Java 21虚拟线程异步处理MongoDB写操作
 */
@Service
@Slf4j
public class OrderService {
    
    @Resource
    private MongoTemplate mongoTemplate;
    
    /**
     * 创建订单
     * 使用虚拟线程异步插入数据库
     */
    public Order createOrder(Order order) {
        order.setCreateTime(LocalDateTime.now());
        if (order.getStatus() == null) {
            order.setStatus(Order.OrderStatus.NEW);
        }
        
        // 使用虚拟线程异步插入
        Thread.startVirtualThread(() -> {
            try {
                mongoTemplate.insert(order);
                log.info("Order created: id={}, symbol={}, side={}, price={}", 
                    order.getId(), order.getSymbol(), order.getSide(), order.getPrice());
            } catch (Exception e) {
                log.error("Failed to create order: {}", order.getId(), e);
            }
        });
        
        return order;
    }
    
    /**
     * 更新订单状态
     * 使用虚拟线程异步更新
     */
    public void updateOrderStatus(String orderId, Order.OrderStatus status) {
        Thread.startVirtualThread(() -> {
            try {
                Query query = query(where("_id").is(orderId));
                Update update = new Update()
                    .set("status", status);
                
                mongoTemplate.updateFirst(query, update, Order.class);
                log.info("Order status updated: orderId={}, status={}", orderId, status);
            } catch (Exception e) {
                log.error("Failed to update order status: orderId={}", orderId, e);
            }
        });
    }
    
    /**
     * 记录订单成交信息
     * 使用虚拟线程异步更新
     */
    public void recordFill(String orderId, double executedPrice, double executedQty, double slippage) {
        Thread.startVirtualThread(() -> {
            try {
                Query query = query(where("_id").is(orderId));
                Update update = new Update()
                    .set("status", Order.OrderStatus.FILLED)
                    .set("executedPrice", executedPrice)
                    .set("executedQty", executedQty)
                    .set("slippage", slippage)
                    .set("fillTime", LocalDateTime.now());
                
                mongoTemplate.updateFirst(query, update, Order.class);
                log.info("Order filled: orderId={}, executedPrice={}, executedQty={}, slippage={}", 
                    orderId, executedPrice, executedQty, slippage);
            } catch (Exception e) {
                log.error("Failed to record fill: orderId={}", orderId, e);
            }
        });
    }
    
    /**
     * 查询挂单（状态为NEW或SUBMITTED）
     */
    public List<Order> getOpenOrders(String symbol) {
        Query query = query(
            where("symbol").is(symbol)
            .and("status").in(Order.OrderStatus.NEW, Order.OrderStatus.SUBMITTED)
        );
        return mongoTemplate.find(query, Order.class);
    }
    
    /**
     * 查询最旧的挂单（按创建时间排序）
     * @param symbol 交易对
     * @param limit 返回数量
     * @return 最旧的挂单列表
     */
    public List<Order> getOldestOpenOrders(String symbol, int limit) {
        Query query = query(
            where("symbol").is(symbol)
            .and("status").in(Order.OrderStatus.NEW, Order.OrderStatus.SUBMITTED)
        );
        query.with(org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.ASC, "createTime"));
        query.limit(limit);
        
        return mongoTemplate.find(query, Order.class);
    }
    
    /**
     * 查询挂单数量
     * @param symbol 交易对
     * @return 挂单数量
     */
    public long countOpenOrders(String symbol) {
        Query query = query(
            where("symbol").is(symbol)
            .and("status").in(Order.OrderStatus.NEW, Order.OrderStatus.SUBMITTED)
        );
        return mongoTemplate.count(query, Order.class);
    }
    
    /**
     * 关联买卖单
     * 使用虚拟线程异步更新双向关联
     */
    public void linkOrders(String buyOrderId, String sellOrderId) {
        Thread.startVirtualThread(() -> {
            try {
                // 更新买单，关联卖单
                Query buyQuery = query(where("_id").is(buyOrderId));
                Update buyUpdate = new Update().set("relatedOrderId", sellOrderId);
                mongoTemplate.updateFirst(buyQuery, buyUpdate, Order.class);
                
                // 更新卖单，关联买单
                Query sellQuery = query(where("_id").is(sellOrderId));
                Update sellUpdate = new Update().set("relatedOrderId", buyOrderId);
                mongoTemplate.updateFirst(sellQuery, sellUpdate, Order.class);
                
                log.info("Orders linked: buyOrderId={}, sellOrderId={}", buyOrderId, sellOrderId);
            } catch (Exception e) {
                log.error("Failed to link orders: buyOrderId={}, sellOrderId={}", 
                    buyOrderId, sellOrderId, e);
            }
        });
    }
    
    /**
     * 根据ID查询订单
     */
    public Order getOrderById(String orderId) {
        return mongoTemplate.findById(orderId, Order.class);
    }
    
    /**
     * 查询指定symbol的所有订单（包括历史订单）
     */
    public List<Order> getAllOrders(String symbol) {
        Query query = query(where("symbol").is(symbol));
        return mongoTemplate.find(query, Order.class);
    }
    
    /**
     * 查询指定symbol和mode的所有订单
     */
    public List<Order> getAllOrders(String symbol, StrategyConfig.Mode mode) {
        Query query = query(
            where("symbol").is(symbol)
            .and("mode").is(mode)
        );
        return mongoTemplate.find(query, Order.class);
    }
    
    /**
     * 更新订单的币安订单ID
     */
    public void updateBinanceOrderId(String localOrderId, long binanceOrderId) {
        Thread.startVirtualThread(() -> {
            try {
                Query query = query(where("_id").is(localOrderId));
                Update update = new Update().set("orderId", binanceOrderId);
                
                mongoTemplate.updateFirst(query, update, Order.class);
                log.info("Binance orderId updated: localOrderId={}, binanceOrderId={}", 
                    localOrderId, binanceOrderId);
            } catch (Exception e) {
                log.error("Failed to update binance orderId: localOrderId={}", localOrderId, e);
            }
        });
    }
    
    /**
     * 标记订单为已撤销
     */
    public void markOrderCanceled(String orderId) {
        Thread.startVirtualThread(() -> {
            try {
                Query query = query(where("_id").is(orderId));
                Update update = new Update()
                    .set("status", Order.OrderStatus.CANCELED)
                    .set("cancelTime", LocalDateTime.now());
                
                mongoTemplate.updateFirst(query, update, Order.class);
                log.info("Order marked as canceled: orderId={}", orderId);
            } catch (Exception e) {
                log.error("Failed to mark order as canceled: orderId={}", orderId, e);
            }
        });
    }
    
    /**
     * 标记订单为已过期
     */
    public void markOrderExpired(String orderId) {
        Thread.startVirtualThread(() -> {
            try {
                Query query = query(where("_id").is(orderId));
                Update update = new Update()
                    .set("status", Order.OrderStatus.EXPIRED)
                    .set("cancelTime", LocalDateTime.now());
                
                mongoTemplate.updateFirst(query, update, Order.class);
                log.info("Order marked as expired: orderId={}", orderId);
            } catch (Exception e) {
                log.error("Failed to mark order as expired: orderId={}", orderId, e);
            }
        });
    }
}

