package com.example.e_commerce.admin.mapper;

import com.example.e_commerce.admin.dto.request.AdminRegisterRequestDto;
import com.example.e_commerce.admin.dto.response.AdminRegisterResponseDto;
import com.example.e_commerce.admin.dto.response.ProductResponseDto;
import com.example.e_commerce.admin.dto.response.SellerResponseDto;
import com.example.e_commerce.admin.dto.response.UserResponseDto;
import com.example.e_commerce.product.model.Product;
import com.example.e_commerce.user.enums.Role;
import com.example.e_commerce.user.model.Admin;
import com.example.e_commerce.user.model.Seller;
import com.example.e_commerce.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminMapper {

    private final PasswordEncoder passwordEncoder;

    public UserResponseDto userToResponse(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .phone(user.getPhone())
                .suspended(user.isSuspended())
                .role(user.getRole())
                .gender(user.getGender())
                .age(user.getAge())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    public ProductResponseDto productToResponse(Product product) {
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
                .suspensionReason(product.getSuspensionReason())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public SellerResponseDto sellerToResponse(Seller seller) {
        return SellerResponseDto.builder()
                .id(seller.getId())
                .storeName(seller.getStoreName())
                .businessAddress(seller.getBusinessAddress())
                .storeLogoUrl(seller.getStoreLogoUrl())
                .status(seller.getStatus())
                .suspensionReason(seller.getSuspensionReason())
                .createdAt(seller.getCreatedAt())
                .updatedAt(seller.getUpdatedAt())
                .build();
    }

    public User toUserEntity(AdminRegisterRequestDto request) {
        return User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .Password(passwordEncoder.encode(request.getPassword()))
                .gender(request.getGender())
                .age(request.getAge())
                .role(Role.ADMIN)
                .build();
    }

    public AdminRegisterResponseDto toRegisterResponse(User user, Admin admin) {
        return AdminRegisterResponseDto.builder()
                .id(admin.getId())
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .gender(user.getGender())
                .age(user.getAge())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
