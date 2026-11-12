package com.example.e_commerce.user.dto.response;

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
public class AddressResponseDto {

    private UUID id;
    private UUID userId;
    private String fullName;
    private String phone;
    private String street;
    private String city;
    private String state;
    private String zipCode;
    private String landmark;
    private boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}