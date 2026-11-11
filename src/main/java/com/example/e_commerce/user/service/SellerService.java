package com.example.e_commerce.user.service;

import com.example.e_commerce.user.dto.request.BecomeSellerRequestDto;
import com.example.e_commerce.user.dto.response.SellerResponseDto;
import com.example.e_commerce.user.enums.Role;
import com.example.e_commerce.user.mapper.SellerMapper;
import com.example.e_commerce.user.model.Seller;
import com.example.e_commerce.user.model.User;
import com.example.e_commerce.user.repo.SellerRepo;
import com.example.e_commerce.user.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerService {

    private final SellerRepo sellerRepo;
    private final UserRepo userRepo;
    private final SellerMapper sellerMapper;

    @Transactional
    public SellerResponseDto becomeSeller(BecomeSellerRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepo.findByEmail(email);
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        if (user.getSeller() != null) {
            throw new RuntimeException("User is already a seller");
        }

        Seller seller = sellerMapper.toEntity(request, user);
        user.setRole(Role.SELLER);
        Seller saved = sellerRepo.save(seller);
        userRepo.save(user);

        log.info("User {} became a seller with store name {}", email, request.getStoreName());
        return sellerMapper.toResponse(saved);
    }
}