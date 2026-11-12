package com.example.e_commerce.cart.controller;

import com.example.e_commerce.cart.dto.request.AddToCartRequest;
import com.example.e_commerce.cart.dto.request.UpdateCartItemRequest;
import com.example.e_commerce.cart.dto.response.CartItemResponse;
import com.example.e_commerce.cart.dto.response.CartResponse;
import com.example.e_commerce.cart.mapper.CartMapper;
import com.example.e_commerce.cart.service.CartService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CartMapper cartMapper;

    @PostMapping
    public ResponseEntity<CartResponse> addToCart(@Valid @RequestBody AddToCartRequest request) {
        CartResponse response = cartService.addToCart(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<CartResponse> removeFromCart(@PathVariable UUID productId) {
        CartResponse response = cartService.removeFromCart(productId);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<CartResponse> updateQuantity(@Valid @RequestBody UpdateCartItemRequest request) {
        CartResponse response = cartService.updateQuantity(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<CartResponse> viewCart() {
        CartResponse response = cartService.viewCart();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/items")
    public ResponseEntity<Page<CartItemResponse>> viewCartItems(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CartItemResponse> response = cartService.viewCartPageable(pageable)
                .map(cartMapper::toItemResponse);
        return ResponseEntity.ok(response);
    }
}