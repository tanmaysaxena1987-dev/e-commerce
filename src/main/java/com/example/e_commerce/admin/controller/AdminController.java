package com.example.e_commerce.admin.controller;

import com.example.e_commerce.admin.dto.request.SuspendProductRequest;
import com.example.e_commerce.admin.dto.request.SuspendSellerRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @PutMapping("/product/{productId}/suspend")
    public ResponseEntity<ProductResponseDto> suspendProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody SuspendProductRequest request) {
        ProductResponseDto response = adminService.suspendProduct(productId, request.getReason());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/seller/{sellerId}/suspend")
    public ResponseEntity<SellerResponseDto> suspendSeller(
            @PathVariable UUID sellerId,
            @Valid @RequestBody SuspendSellerRequest request) {
        SellerResponseDto response = adminService.suspendSeller(sellerId, request.getReason());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserResponseDto>> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) Integer age,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UserResponseDto> users = adminService.listUsers(role, gender, age, pageable);
        return ResponseEntity.ok(users);
    }
}
