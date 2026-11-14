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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerService {

    private final SellerRepo sellerRepo;
    private final UserRepo userRepo;
    private final SellerMapper sellerMapper;

    @Transactional
    public SellerResponseDto becomeSeller(BecomeSellerRequestDto request) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        User user = userRepo.findByEmail(email);

        if (user == null) {
            throw new IllegalStateException("User not found");
        }

        if (user.getRole() != Role.BUYER) {
            throw new IllegalStateException(
                    "Only buyers can submit seller applications"
            );
        }

        if (user.getSeller() != null) {
            throw new IllegalStateException(
                    "Seller application already exists"
            );
        }

        Seller seller = sellerMapper.toEntity(request, user);

        user.setSeller(seller);

        Seller savedSeller = sellerRepo.saveAndFlush(seller);

        log.info(
                "Seller application submitted by {} for store {}",
                email,
                request.getStoreName()
        );

        return sellerMapper.toResponse(savedSeller);
    }
    @Transactional
    public SellerResponseDto getMySellerApplication() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = userRepo.findByEmail(authentication.getName());

        if (user == null || user.getSeller() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No seller application found"
            );
        }

        return sellerMapper.toResponse(user.getSeller());
    }
}