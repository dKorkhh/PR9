package org.example.pr9.mapper;

import org.example.pr9.grpc.CreateOrderRequest;
import org.example.pr9.grpc.OrderItem;
import org.example.pr9.grpc.OrderResponse;
import org.example.pr9.grpc.OrderStatus;
import org.example.pr9.model.OrderEntity;
import org.example.pr9.model.OrderItemEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderProtoMapper {

    public OrderResponse toProtoOrderResponse(OrderEntity entity) {
        List<OrderItem> protoItems = entity.items().stream()
                .map(this::toProtoOrderItem)
                .toList();

        return OrderResponse.newBuilder()
                .setOrderId(entity.orderId())
                .setCustomerId(entity.customerId())
                .addAllItems(protoItems)
                .setTotalAmount(entity.totalAmount())
                .setStatus(entity.status())
                .setDeliveryAddress(entity.deliveryAddress())
                .setCreatedAt(entity.createdAt())
                .build();
    }

    public OrderItem toProtoOrderItem(OrderItemEntity entity) {
        return OrderItem.newBuilder()
                .setProductId(entity.productId())
                .setProductName(entity.productName())
                .setQuantity(entity.quantity())
                .setUnitPrice(entity.unitPrice())
                .build();
    }

    public OrderItemEntity toDomainOrderItemEntity(OrderItem protoItem) {
        return new OrderItemEntity(
                protoItem.getProductId(),
                protoItem.getProductName(),
                protoItem.getQuantity(),
                protoItem.getUnitPrice()
        );
    }

    public OrderEntity toDomainOrderEntity(CreateOrderRequest request, String orderId, String timestamp) {
        List<OrderItemEntity> domainItems = request.getItemsList().stream()
                .map(this::toDomainOrderItemEntity)
                .toList();

        double calculatedTotalAmount = domainItems.stream()
                .mapToDouble(item -> item.unitPrice() * item.quantity())
                .sum();

        return new OrderEntity(
                orderId,
                request.getCustomerId(),
                domainItems,
                calculatedTotalAmount,
                OrderStatus.ORDER_STATUS_CREATED,
                request.getDeliveryAddress(),
                timestamp
        );
    }
}
