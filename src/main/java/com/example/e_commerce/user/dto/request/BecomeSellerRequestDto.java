package com.example.e_commerce.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BecomeSellerRequestDto {

    @NotBlank(message = "Store name is required")
    private String storeName;

    private String businessAddress;

    private String storeLogoUrl;
}