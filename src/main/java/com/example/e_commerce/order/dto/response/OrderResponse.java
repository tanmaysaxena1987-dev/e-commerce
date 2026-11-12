package com.example.e_commerce.order.dto.response;

import com.example.e_commerce.order.enums.OrderStatus;
import com.example.e_commerce.order.enums.ReturnReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private UUID id;
    private String trackingNumber;
    private UUID userId;
    private List<OrderItemResponse> items;
    private OrderStatus status;
    private ReturnReason returnReason;
    private String returnComment;
    private BigDecimal totalAmount;
    private BigDecimal shippingCost;
    private BigDecimal tax;
    private BigDecimal orderTotal;
    private String shippingAddress;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String phoneNumber;
    private String deliveryNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime returnedAt;
}