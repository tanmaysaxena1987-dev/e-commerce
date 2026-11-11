package com.example.e_commerce.product.dto.response;

import com.example.e_commerce.product.enums.Category;
import com.example.e_commerce.product.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDto {
    private UUID id;
    private String name;
    private String description;
    private String brand;
    private BigDecimal price;
    private Category category;
    private int stockQuantity;
    private UUID sellerId;
    private Status status;
    private String imageUrl;
    private BigDecimal averageRating;
    private BigDecimal reviewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
