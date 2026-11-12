package com.example.e_commerce.cart.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    private UUID id;
    private UUID productId;
    private String productName;
    private String productImageUrl;
    private BigDecimal price;
    private int quantity;
    private BigDecimal subtotal;
}