package com.example.e_commerce.user.controller;

import com.example.e_commerce.user.dto.request.AddressRequestDto;
import com.example.e_commerce.user.dto.request.EmailVerificationRequestDto;
import com.example.e_commerce.user.dto.request.UpdateAddressRequestDto;
import com.example.e_commerce.user.dto.request.UpdateUserProfileRequestDto;
import com.example.e_commerce.user.dto.request.UserRegisterRequestDto;
import com.example.e_commerce.user.dto.response.AddressResponseDto;
import com.example.e_commerce.user.dto.response.UserProfileResponseDto;
import com.example.e_commerce.user.dto.response.UserRegisterResponseDto;
import com.example.e_commerce.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

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

    @GetMapping("/addresses")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<List<AddressResponseDto>> getAddresses() {
        List<AddressResponseDto> addresses = userService.getAddresses();
        return ResponseEntity.ok(addresses);
    }

    @PostMapping("/addresses")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<AddressResponseDto> addAddress(@Valid @RequestBody AddressRequestDto request) {
        AddressResponseDto response = userService.addAddress(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/addresses/{addressId}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<AddressResponseDto> updateAddress(@PathVariable UUID addressId,
                                                             @Valid @RequestBody UpdateAddressRequestDto request) {
        AddressResponseDto response = userService.updateAddress(addressId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/addresses/{addressId}")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID addressId) {
        userService.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDto> getCurrentUserProfile() {
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponseDto> updateCurrentUserProfile(
            @Valid @RequestBody UpdateUserProfileRequestDto request) {
        return ResponseEntity.ok(userService.updateCurrentUserProfile(request));
    }

    @GetMapping("/email-status")
    public ResponseEntity<Boolean> getEmailStatus(){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(userService.getEmailStatus());
    }
}