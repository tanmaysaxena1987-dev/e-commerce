package com.example.e_commerce.user.dto.response;

import com.example.e_commerce.user.enums.SellerStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SellerResponseDto {

    private UUID id;
    private String storeName;
    private String businessAddress;
    private String storeLogoUrl;
    private SellerStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}