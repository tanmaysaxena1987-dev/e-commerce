package com.example.e_commerce.user.controller;

import com.example.e_commerce.user.dto.request.EmailVerificationRequestDto;
import com.example.e_commerce.user.dto.request.UserRegisterRequestDto;
import com.example.e_commerce.user.dto.response.UserRegisterResponseDto;
import com.example.e_commerce.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;;

    @PostMapping("/register")
    public ResponseEntity<UserRegisterResponseDto> register(@Valid @RequestBody UserRegisterRequestDto request) {
        UserRegisterResponseDto response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping("/email-verification")
    public ResponseEntity<String> emailVerification() {
        userService.sendOtp();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Please Check Your Inbox for Completing Email Verification");
    }
    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@Valid @RequestBody EmailVerificationRequestDto emailVerificationRequestDto) {
        userService.verifyOtp(emailVerificationRequestDto.getOtp());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Your Email Has Been Verified");
    }
}