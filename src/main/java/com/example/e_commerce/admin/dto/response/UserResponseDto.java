package com.example.e_commerce.admin.dto.response;

import com.example.e_commerce.user.enums.Gender;
import com.example.e_commerce.user.enums.Role;
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
public class UserResponseDto {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private boolean emailVerified;
    private String phone;
    private boolean suspended;
    private Role role;
    private Gender gender;
    private Integer age;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
}
