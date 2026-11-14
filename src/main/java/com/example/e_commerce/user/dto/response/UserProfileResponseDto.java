package com.example.e_commerce.user.dto.response;

import com.example.e_commerce.user.enums.Gender;
import com.example.e_commerce.user.enums.Role;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserProfileResponseDto {

    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Role role;
    private Gender gender;
    private Integer age;
    private boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
}