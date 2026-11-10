package com.example.e_commerce.user.controller;

import com.example.e_commerce.user.dto.request.ResetPasswordRequestDto;
import com.example.e_commerce.user.dto.request.UserLoginRequestDto;
import com.example.e_commerce.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<String> authenticate(@Valid @RequestBody UserLoginRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(authService.authenticate(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        authService.sendResetPasswordOtp(email);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body("If the email exists, an OTP has been sent to your registered email address");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(authService.resetPassword(request));
    }
}
