package com.example.e_commerce.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequestDto {
    @Email
    @NotBlank
    private String email;
    @Size(min=6,max=72)
    @NotBlank
    private String password;
}
