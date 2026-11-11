package com.example.e_commerce.admin.dto.response;

import com.example.e_commerce.user.enums.SellerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerResponseDto {
    private UUID id;
    private String storeName;
    private String businessAddress;
    private String storeLogoUrl;
    private SellerStatus status;
    private String suspensionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
