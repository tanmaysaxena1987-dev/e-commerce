package com.example.e_commerce.user.mapper;

import com.example.e_commerce.user.dto.request.UpdateUserProfileRequestDto;
import com.example.e_commerce.user.dto.request.UserRegisterRequestDto;
import com.example.e_commerce.user.dto.response.UserProfileResponseDto;
import com.example.e_commerce.user.dto.response.UserRegisterResponseDto;
import com.example.e_commerce.user.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    private final PasswordEncoder passwordEncoder;

    public UserMapper(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public User toEntity(UserRegisterRequestDto request) {
        return User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .Password(passwordEncoder.encode(request.getPassword()))
                .gender(request.getGender())
                .age(request.getAge())
                .build();
    }

    public UserRegisterResponseDto toRegisterResponse(User user) {
        return UserRegisterResponseDto.builder()
                .id(user.getId())
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

    public UserProfileResponseDto toProfileResponse(User user) {
        return UserProfileResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .gender(user.getGender())
                .age(user.getAge())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    public void updateProfile(User user, UpdateUserProfileRequestDto request) {
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getAge() != null) {
            user.setAge(request.getAge());
        }
    }
}
