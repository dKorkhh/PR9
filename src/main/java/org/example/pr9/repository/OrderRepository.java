package org.example.pr9.repository;

import jakarta.annotation.PostConstruct;
import org.example.pr9.grpc.OrderStatus;
import org.example.pr9.model.OrderEntity;
import org.example.pr9.model.OrderItemEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class OrderRepository {

    private final Map<String, OrderEntity> orderStore = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeDefaultOrders() {
        OrderItemEntity laptopItem = new OrderItemEntity("PROD-001", "Ultrabook Pro 15", 1, 1200.0);
        OrderItemEntity mouseItem = new OrderItemEntity("PROD-002", "Wireless Mouse", 2, 25.0);
        OrderItemEntity keyboardItem = new OrderItemEntity("PROD-003", "Mechanical Keyboard", 1, 150.0);

        OrderEntity orderOne = new OrderEntity(
                "ORD-1001",
                "CUST-10",
                List.of(laptopItem, mouseItem),
                1250.0,
                OrderStatus.ORDER_STATUS_CREATED,
                "Kyiv, Khreshchatyk 1",
                Instant.now().toString()
        );

        OrderEntity orderTwo = new OrderEntity(
                "ORD-1002",
                "CUST-10",
                List.of(keyboardItem),
                150.0,
                OrderStatus.ORDER_STATUS_PROCESSING,
                "Kyiv, Khreshchatyk 1",
                Instant.now().toString()
        );

        OrderEntity orderThree = new OrderEntity(
                "ORD-1003",
                "CUST-20",
                List.of(mouseItem),
                50.0,
                OrderStatus.ORDER_STATUS_SHIPPED,
                "Lviv, Svobody Ave 10",
                Instant.now().toString()
        );

        saveOrder(orderOne);
        saveOrder(orderTwo);
        saveOrder(orderThree);
    }

    public void saveOrder(OrderEntity orderEntity) {
        orderStore.put(orderEntity.orderId(), orderEntity);
    }

    public Optional<OrderEntity> findOrderById(String orderId) {
        return Optional.ofNullable(orderStore.get(orderId));
    }

    public List<OrderEntity> findOrdersByCustomerId(String customerId) {
        return orderStore.values().stream()
                .filter(order -> order.customerId().equalsIgnoreCase(customerId))
                .toList();
    }

    public List<OrderEntity> findAllOrders() {
        return List.copyOf(orderStore.values());
    }

    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        OrderEntity currentOrder = orderStore.get(orderId);
        if (currentOrder == null) {
            return;
        }
        OrderEntity updatedOrder = currentOrder.withStatus(newStatus);
        orderStore.put(orderId, updatedOrder);
    }
}
