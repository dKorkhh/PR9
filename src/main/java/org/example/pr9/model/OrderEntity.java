package org.example.pr9.model;

import org.example.pr9.grpc.OrderStatus;

import java.util.List;

public record OrderEntity(
        String orderId,
        String customerId,
        List<OrderItemEntity> items,
        double totalAmount,
        OrderStatus status,
        String deliveryAddress,
        String createdAt
) {
    public OrderEntity withStatus(OrderStatus newStatus) {
        return new OrderEntity(orderId, customerId, items, totalAmount, newStatus, deliveryAddress, createdAt);
    }
}
