package com.example.e_commerce.cart.service;

import com.example.e_commerce.cart.dto.request.AddToCartRequest;
import com.example.e_commerce.cart.dto.request.UpdateCartItemRequest;
import com.example.e_commerce.cart.dto.response.CartResponse;
import com.example.e_commerce.cart.mapper.CartMapper;
import com.example.e_commerce.cart.model.Cart;
import com.example.e_commerce.cart.model.CartItem;
import com.example.e_commerce.cart.repo.CartItemRepository;
import com.example.e_commerce.cart.repo.CartRepository;
import com.example.e_commerce.product.exception.ProductNotFoundException;
import com.example.e_commerce.product.model.Product;
import com.example.e_commerce.product.repo.ProductRepository;
import com.example.e_commerce.user.model.User;
import com.example.e_commerce.user.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepo userRepo;
    private final CartMapper cartMapper;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepo.findByEmail(email);
    }

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .user(user)
                            .items(new ArrayList<>())
                            .build();
                    Cart saved = cartRepository.save(newCart);
                    log.info("Created new cart for user: {}", user.getEmail());
                    return saved;
                });
    }

    @Transactional
    public CartResponse addToCart(AddToCartRequest request) {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + request.getProductId()));

        // Check if product already in cart
        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());

        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            cartItemRepository.save(item);
            log.info("Increased quantity for product {} in cart of user {}", product.getName(), user.getEmail());
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cart.getItems().add(newItem);
            cartItemRepository.save(newItem);
            log.info("Added product {} to cart of user {}", product.getName(), user.getEmail());
        }

        return cartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse removeFromCart(UUID productId) {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found in cart with id: " + productId));

        cart.getItems().remove(item);
        cartItemRepository.delete(item);

        log.info("Removed product {} from cart of user {}", productId, user.getEmail());

        return cartMapper.toResponse(cart);
    }

    @Transactional
    public CartResponse updateQuantity(UUID productId, UpdateCartItemRequest request) {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found in cart with id: " + productId));

        if (request.getQuantity() <= 0) {
            // Remove if quantity is 0 or negative
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
            log.info("Removed product {} from cart of user {} (quantity set to 0)", productId, user.getEmail());
        } else {
            item.setQuantity(request.getQuantity());
            cartItemRepository.save(item);
            log.info("Updated quantity for product {} in cart of user {} to {}", productId, user.getEmail(), request.getQuantity());
        }

        return cartMapper.toResponse(cart);
    }

    public CartResponse viewCart() {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);
        return cartMapper.toResponse(cart);
    }

    /**
     * View cart items in a pageable (paginated) form.
     * Each page contains a subset of the cart items.
     */
    public Page<CartItem> viewCartPageable(Pageable pageable) {
        User user = getCurrentUser();
        Cart cart = getOrCreateCart(user);

        // Manual pagination over the items list
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), cart.getItems().size());

        if (start > cart.getItems().size()) {
            return Page.empty(pageable);
        }

        java.util.List<CartItem> pageContent = cart.getItems().subList(start, end);
        return new PageImpl<>(pageContent, pageable, cart.getItems().size());
    }
}