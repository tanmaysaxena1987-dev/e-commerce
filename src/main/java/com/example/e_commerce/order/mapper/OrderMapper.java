package com.example.e_commerce.order.mapper;

import com.example.e_commerce.order.dto.response.OrderItemResponse;
import com.example.e_commerce.order.dto.response.OrderResponse;
import com.example.e_commerce.order.dto.response.OrderTrackingResponse;
import com.example.e_commerce.order.model.Order;
import com.example.e_commerce.order.model.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .trackingNumber(order.getTrackingNumber())
                .userId(order.getUser().getId())
                .items(itemResponses)
                .status(order.getStatus())
                .returnReason(order.getReturnReason())
                .returnComment(order.getReturnComment())
                .totalAmount(order.getTotalAmount())
                .shippingCost(order.getShippingCost())
                .tax(order.getTax())
                .orderTotal(order.getOrderTotal())
                .shippingAddress(order.getShippingAddress())
                .city(order.getCity())
                .state(order.getState())
                .zipCode(order.getZipCode())
                .country(order.getCountry())
                .phoneNumber(order.getPhoneNumber())
                .deliveryNotes(order.getDeliveryNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .deliveredAt(order.getDeliveredAt())
                .cancelledAt(order.getCancelledAt())
                .returnedAt(order.getReturnedAt())
                .build();
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productImageUrl(item.getProduct().getImageUrl())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getSubtotal())
                .build();
    }

    public OrderTrackingResponse toTrackingResponse(Order order) {
        return OrderTrackingResponse.builder()
                .id(order.getId())
                .trackingNumber(order.getTrackingNumber())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .shippingAddress(order.getShippingAddress())
                .city(order.getCity())
                .state(order.getState())
                .zipCode(order.getZipCode())
                .country(order.getCountry())
                .build();
    }
}