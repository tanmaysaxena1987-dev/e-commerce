package com.example.e_commerce.user.mapper;

import com.example.e_commerce.user.dto.request.BecomeSellerRequestDto;
import com.example.e_commerce.user.dto.response.SellerResponseDto;
import com.example.e_commerce.user.model.Seller;
import com.example.e_commerce.user.model.User;
import org.springframework.stereotype.Component;

@Component
public class SellerMapper {

    public Seller toEntity(BecomeSellerRequestDto request, User user) {
        return Seller.builder()
                .id(user.getId())
                .user(user)
                .storeName(request.getStoreName())
                .businessAddress(request.getBusinessAddress())
                .storeLogoUrl(request.getStoreLogoUrl())
                .build();
    }

    public SellerResponseDto toResponse(Seller seller) {
        return SellerResponseDto.builder()
                .id(seller.getId())
                .storeName(seller.getStoreName())
                .businessAddress(seller.getBusinessAddress())
                .storeLogoUrl(seller.getStoreLogoUrl())
                .status(seller.getStatus())
                .createdAt(seller.getCreatedAt())
                .updatedAt(seller.getUpdatedAt())
                .build();
    }
}