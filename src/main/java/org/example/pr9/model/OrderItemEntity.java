package org.example.pr9.model;

public record OrderItemEntity(
        String productId,
        String productName,
        int quantity,
        double unitPrice
) {}
