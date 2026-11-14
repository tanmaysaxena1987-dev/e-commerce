package com.example.e_commerce.user.dto.request;

import com.example.e_commerce.user.enums.Gender;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateUserProfileRequestDto {

    private String firstName;

    private String lastName;

    @Size(min = 10, max = 10, message = "Phone number must be exactly 10 digits")
    private String phone;

    private Gender gender;

    private Integer age;
}