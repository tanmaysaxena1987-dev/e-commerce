package com.example.e_commerce.admin.controller;

import com.example.e_commerce.admin.dto.request.AdminRegisterRequestDto;
import com.example.e_commerce.admin.dto.request.SuspendProductRequest;
import com.example.e_commerce.admin.dto.request.SuspendSellerRequest;
import com.example.e_commerce.admin.dto.response.AdminRegisterResponseDto;
import com.example.e_commerce.admin.dto.response.ProductResponseDto;
import com.example.e_commerce.admin.dto.response.SellerResponseDto;
import com.example.e_commerce.admin.dto.response.UserResponseDto;
import com.example.e_commerce.admin.service.AdminService;
import com.example.e_commerce.user.enums.Gender;
import com.example.e_commerce.user.enums.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/register")
    public ResponseEntity<AdminRegisterResponseDto> register(@Valid @RequestBody AdminRegisterRequestDto request) {
        AdminRegisterResponseDto response = adminService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/product/{productId}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponseDto> suspendProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody SuspendProductRequest request) {
        ProductResponseDto response = adminService.suspendProduct(productId, request.getReason());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/seller/{sellerId}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SellerResponseDto> suspendSeller(
            @PathVariable UUID sellerId,
            @Valid @RequestBody SuspendSellerRequest request) {
        SellerResponseDto response = adminService.suspendSeller(sellerId, request.getReason());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponseDto>> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) Integer age,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UserResponseDto> users = adminService.listUsers(role, gender, age, pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/sellers/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SellerResponseDto>> getPendingSellers() {

        return ResponseEntity.ok(
                adminService.getPendingSellers()
        );
    }

    @PutMapping("/seller/{sellerId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SellerResponseDto> approveSeller(
            @PathVariable UUID sellerId
    ) {

        return ResponseEntity.ok(
                adminService.approveSeller(sellerId)
        );
    }
}
