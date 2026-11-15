package org.example.pr9.service;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.example.pr9.grpc.BulkOrderSummary;
import org.example.pr9.grpc.CreateOrderRequest;
import org.example.pr9.grpc.GetOrderRequest;
import org.example.pr9.grpc.ListOrdersRequest;
import org.example.pr9.grpc.OrderResponse;
import org.example.pr9.grpc.OrderServiceGrpc;
import org.example.pr9.grpc.OrderStatus;
import org.example.pr9.grpc.OrderStatusUpdate;
import org.example.pr9.grpc.TrackOrderRequest;
import org.example.pr9.mapper.OrderProtoMapper;
import org.example.pr9.model.OrderEntity;
import org.example.pr9.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(OrderGrpcService.class);

    private final OrderRepository orderRepository;
    private final OrderProtoMapper orderProtoMapper;

    @Override
    public void getOrder(GetOrderRequest request, StreamObserver<OrderResponse> responseObserver) {
        request.getOrderId();
        if (request.getOrderId().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Order ID must not be empty")
                    .asRuntimeException());
            return;
        }

        Optional<OrderEntity> orderOptional = orderRepository.findOrderById(request.getOrderId());
        if (orderOptional.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Order with ID " + request.getOrderId() + " not found")
                    .asRuntimeException());
            return;
        }

        OrderResponse response = orderProtoMapper.toProtoOrderResponse(orderOptional.get());
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void listOrdersByCustomer(ListOrdersRequest request, StreamObserver<OrderResponse> responseObserver) {
        request.getCustomerId();
        List<OrderEntity> orders = request.getCustomerId().isBlank()
                ? orderRepository.findAllOrders()
                : orderRepository.findOrdersByCustomerId(request.getCustomerId());

        for (OrderEntity order : orders) {
            if (Context.current().isCancelled()) {
                log.warn("Listing orders cancelled by client");
                return;
            }
            responseObserver.onNext(orderProtoMapper.toProtoOrderResponse(order));
        }
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<CreateOrderRequest> createBulkOrders(StreamObserver<BulkOrderSummary> responseObserver) {
        return new StreamObserver<>() {
            private final List<String> createdOrderIds = new ArrayList<>();
            private double accumulatedRevenue = 0.0;

            @Override
            public void onNext(CreateOrderRequest request) {
                String generatedOrderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                String creationTimestamp = Instant.now().toString();

                OrderEntity entity = orderProtoMapper.toDomainOrderEntity(request, generatedOrderId, creationTimestamp);
                orderRepository.saveOrder(entity);

                createdOrderIds.add(generatedOrderId);
                accumulatedRevenue += entity.totalAmount();
            }

            @Override
            public void onError(Throwable throwable) {
                log.warn("Bulk order client stream error: {}", throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                BulkOrderSummary summary = BulkOrderSummary.newBuilder()
                        .setTotalOrders(createdOrderIds.size())
                        .setTotalRevenue(accumulatedRevenue)
                        .addAllCreatedOrderIds(createdOrderIds)
                        .setSummaryMessage("Successfully processed " + createdOrderIds.size() + " orders")
                        .build();

                responseObserver.onNext(summary);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<TrackOrderRequest> trackOrders(StreamObserver<OrderStatusUpdate> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(TrackOrderRequest request) {
                Optional<OrderEntity> orderOptional = orderRepository.findOrderById(request.getOrderId());
                if (orderOptional.isEmpty()) {
                    responseObserver.onNext(OrderStatusUpdate.newBuilder()
                            .setOrderId(request.getOrderId())
                            .setStatus(OrderStatus.ORDER_STATUS_UNSPECIFIED)
                            .setStatusDescription("Order not found")
                            .setTimestamp(Instant.now().toString())
                            .build());
                    return;
                }

                OrderEntity order = orderOptional.get();
                OrderStatus nextStatus = advanceOrderStatus(order.status());
                orderRepository.updateOrderStatus(order.orderId(), nextStatus);

                responseObserver.onNext(OrderStatusUpdate.newBuilder()
                        .setOrderId(order.orderId())
                        .setStatus(nextStatus)
                        .setStatusDescription("Status transitioned to: " + nextStatus.name())
                        .setTimestamp(Instant.now().toString())
                        .build());
            }

            @Override
            public void onError(Throwable throwable) {
                log.warn("Track orders stream cancelled: {}", throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    private OrderStatus advanceOrderStatus(OrderStatus currentStatus) {
        return switch (currentStatus) {
            case ORDER_STATUS_CREATED -> OrderStatus.ORDER_STATUS_PROCESSING;
            case ORDER_STATUS_PROCESSING -> OrderStatus.ORDER_STATUS_SHIPPED;
            case ORDER_STATUS_SHIPPED -> OrderStatus.ORDER_STATUS_DELIVERED;
            default -> currentStatus;
        };
    }
}
