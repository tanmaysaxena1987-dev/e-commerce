package com.example.e_commerce.user.controller;

import com.example.e_commerce.user.dto.request.BecomeSellerRequestDto;
import com.example.e_commerce.user.dto.response.SellerResponseDto;
import com.example.e_commerce.user.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @PostMapping("/become")
    public ResponseEntity<SellerResponseDto> becomeSeller(@Valid @RequestBody BecomeSellerRequestDto request) {
        SellerResponseDto response = sellerService.becomeSeller(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping("/me")
    public ResponseEntity<SellerResponseDto> getMySellerApplication() {

        return ResponseEntity.ok(
                sellerService.getMySellerApplication()
        );
    }
}