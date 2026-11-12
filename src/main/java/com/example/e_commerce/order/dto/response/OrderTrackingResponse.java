package com.example.e_commerce.order.dto.response;

import com.example.e_commerce.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTrackingResponse {

    private UUID id;
    private String trackingNumber;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime estimatedDelivery;
    private String shippingAddress;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String carrier;
    private String carrierTrackingNumber;
}