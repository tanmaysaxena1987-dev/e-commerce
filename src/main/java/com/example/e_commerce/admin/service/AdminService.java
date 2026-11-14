package com.example.e_commerce.admin.service;

import com.example.e_commerce.admin.dto.request.AdminRegisterRequestDto;
import com.example.e_commerce.admin.dto.response.AdminRegisterResponseDto;
import com.example.e_commerce.admin.dto.response.ProductResponseDto;
import com.example.e_commerce.admin.dto.response.SellerResponseDto;
import com.example.e_commerce.admin.dto.response.UserResponseDto;
import com.example.e_commerce.admin.mapper.AdminMapper;
import com.example.e_commerce.product.enums.Status;
import com.example.e_commerce.product.exception.ProductNotFoundException;
import com.example.e_commerce.product.model.Product;
import com.example.e_commerce.product.repo.ProductRepository;
import com.example.e_commerce.user.enums.Gender;
import com.example.e_commerce.user.enums.Role;
import com.example.e_commerce.user.enums.SellerStatus;
import com.example.e_commerce.user.model.Admin;
import com.example.e_commerce.user.model.Seller;
import com.example.e_commerce.user.model.User;
import com.example.e_commerce.user.repo.AdminRepo;
import com.example.e_commerce.user.repo.SellerRepo;
import com.example.e_commerce.user.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepo userRepo;
    private final SellerRepo sellerRepo;
    private final ProductRepository productRepository;
    private final AdminMapper adminMapper;
    private final AdminRepo adminRepo;

    @Value("${admin.secret-key:admin-secret-key}")
    private String adminSecretKey;

    @Transactional
    public AdminRegisterResponseDto register(AdminRegisterRequestDto request) {
        if (!adminSecretKey.equals(request.getAdminSecretKey())) {
            throw new SecurityException("Invalid admin secret key");
        }

        if (userRepo.findByEmail(request.getEmail()) != null) {
            throw new RuntimeException("Email already registered");
        }

        User user = adminMapper.toUserEntity(request);
        User savedUser = userRepo.saveAndFlush(user);

        Admin admin = Admin.builder()
                .user(savedUser)
                .build();
        Admin savedAdmin = adminRepo.saveAndFlush(admin);

        log.info("New admin registered: {}", savedUser.getEmail());
        return adminMapper.toRegisterResponse(savedUser, savedAdmin);
    }

    @Transactional
    public ProductResponseDto suspendProduct(UUID productId, String reason) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));

        product.setStatus(Status.SUSPENDED);
        product.setSuspensionReason(reason);
        Product saved = productRepository.save(product);

        log.info("Admin suspended product {} (ID: {}). Reason: {}", saved.getName(), productId, reason);
        return adminMapper.productToResponse(saved);
    }

    @Transactional
    public SellerResponseDto suspendSeller(UUID sellerId, String reason) {
        Seller seller = sellerRepo.findById(sellerId)
                .orElseThrow(() -> new RuntimeException("Seller not found with id: " + sellerId));

        seller.setStatus(SellerStatus.SUSPENDED);
        seller.setSuspensionReason(reason);
        Seller saved = sellerRepo.save(seller);

        User user = saved.getUser();
        user.setSuspended(true);
        userRepo.save(user);

        log.info("Admin suspended seller {} (ID: {}). Reason: {}", saved.getStoreName(), sellerId, reason);
        return adminMapper.sellerToResponse(saved);
    }

    public Page<UserResponseDto> listUsers(Role role, Gender gender, Integer age, Pageable pageable) {
        Page<User> users;

        boolean hasRole = role != null;
        boolean hasGender = gender != null;
        boolean hasAge = age != null;

        if (hasRole && hasGender && hasAge) {
            users = userRepo.findByRoleAndGenderAndAge(role, gender, age, pageable);
        } else if (hasRole && hasGender) {
            users = userRepo.findByRoleAndGender(role, gender, pageable);
        } else if (hasRole && hasAge) {
            users = userRepo.findByRoleAndAge(role, age, pageable);
        } else if (hasGender && hasAge) {
            users = userRepo.findByGenderAndAge(gender, age, pageable);
        } else if (hasRole) {
            users = userRepo.findByRole(role, pageable);
        } else if (hasGender) {
            users = userRepo.findByGender(gender, pageable);
        } else if (hasAge) {
            users = userRepo.findByAge(age, pageable);
        } else {
            users = userRepo.findAll(pageable);
        }

        return users.map(adminMapper::userToResponse);
    }

    @Transactional
    public List<SellerResponseDto> getPendingSellers() {

        return sellerRepo.findAllByStatus(SellerStatus.PENDING)
                .stream()
                .map(adminMapper::sellerToResponse)
                .toList();
    }
    @Transactional
    public SellerResponseDto approveSeller(UUID sellerId) {

        Seller seller = sellerRepo.findById(sellerId)
                .orElseThrow(() ->
                        new IllegalStateException("Seller not found")
                );

        if (seller.getStatus() != SellerStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending applications can be approved"
            );
        }

        User user = seller.getUser();

        if (user.isSuspended()) {
            throw new IllegalStateException(
                    "Cannot approve a suspended user"
            );
        }

        seller.setStatus(SellerStatus.APPROVED);

        user.setRole(Role.SELLER);

        log.info(
                "Seller {} approved by admin",
                seller.getStoreName()
        );

        return adminMapper.sellerToResponse(seller);
    }
}
