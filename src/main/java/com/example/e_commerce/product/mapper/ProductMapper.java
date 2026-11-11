package com.example.e_commerce.product.mapper;

import com.example.e_commerce.product.dto.request.ProductRequestDto;
import com.example.e_commerce.product.dto.response.ProductResponseDto;
import com.example.e_commerce.product.enums.Status;
import com.example.e_commerce.product.model.Product;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProductMapper {

    public Product toEntity(ProductRequestDto request, UUID sellerId) {
        return Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .brand(request.getBrand())
                .price(request.getPrice())
                .category(request.getCategory())
                .stockQuantity(request.getStockQuantity())
                .sellerId(sellerId)
                .status(request.getStockQuantity() > 0 ? Status.AVAILABLE : Status.OUT_OF_STOCK)
                .imageUrl(request.getImageUrl())
                .build();
    }

    public void updateEntity(Product product, ProductRequestDto request) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBrand(request.getBrand());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStockQuantity(request.getStockQuantity());
        product.setStatus(request.getStockQuantity() > 0 ? Status.AVAILABLE : Status.OUT_OF_STOCK);
        product.setImageUrl(request.getImageUrl());
    }

    public ProductResponseDto toResponse(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .brand(product.getBrand())
                .price(product.getPrice())
                .category(product.getCategory())
                .stockQuantity(product.getStockQuantity())
                .sellerId(product.getSellerId())
                .status(product.getStatus())
                .imageUrl(product.getImageUrl())
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
