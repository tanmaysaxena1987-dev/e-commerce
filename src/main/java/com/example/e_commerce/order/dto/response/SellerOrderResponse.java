package com.example.e_commerce.order.dto.response;

import com.example.e_commerce.order.enums.OrderStatus;
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
public class SellerOrderResponse {

    private UUID orderId;
    private String trackingNumber;
    private OrderStatus status;
    private LocalDateTime createdAt;

    private String shippingAddress;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String phoneNumber;

    private List<SellerOrderItemResponse> items;
    private BigDecimal sellerTotal;
}