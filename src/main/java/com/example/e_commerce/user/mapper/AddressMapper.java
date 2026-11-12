package com.example.e_commerce.user.mapper;

import com.example.e_commerce.user.dto.request.AddressRequestDto;
import com.example.e_commerce.user.dto.response.AddressResponseDto;
import com.example.e_commerce.user.model.Address;
import com.example.e_commerce.user.model.User;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public Address toEntity(AddressRequestDto request, User user) {
        return Address.builder()
                .user(user)
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .landmark(request.getLandmark())
                .isDefault(request.isDefault())
                .build();
    }

    public void updateEntity(Address address, com.example.e_commerce.user.dto.request.UpdateAddressRequestDto request) {
        address.setFullName(request.getFullName());
        address.setPhone(request.getPhone());
        address.setStreet(request.getStreet());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setZipCode(request.getZipCode());
        address.setLandmark(request.getLandmark());
        address.setDefault(request.isDefault());
    }

    public AddressResponseDto toResponse(Address address) {
        return AddressResponseDto.builder()
                .id(address.getId())
                .userId(address.getUser().getId())
                .fullName(address.getFullName())
                .phone(address.getPhone())
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .landmark(address.getLandmark())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}