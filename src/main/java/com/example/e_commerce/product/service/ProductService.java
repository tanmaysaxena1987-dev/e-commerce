package com.example.e_commerce.product.service;

import com.example.e_commerce.product.dto.request.ProductRequestDto;
import com.example.e_commerce.product.dto.response.ProductResponseDto;
import java.util.List;
import com.example.e_commerce.product.exception.ProductNotFoundException;
import com.example.e_commerce.product.exception.UnauthorizedProductAccessException;
import com.example.e_commerce.product.mapper.ProductMapper;
import com.example.e_commerce.product.model.Product;
import com.example.e_commerce.product.repo.ProductRepository;
import com.example.e_commerce.user.enums.SellerStatus;
import com.example.e_commerce.user.model.User;
import com.example.e_commerce.user.repo.UserRepo;
import tools.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepo userRepo;
    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public ProductResponseDto getProduct(UUID productId) {
        String cacheKey = "product:" + productId;

        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                log.info("Returning cached product: {}", productId);
                return objectMapper.readValue(cached, ProductResponseDto.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached product {}, fetching from DB", productId);
            }
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));

        ProductResponseDto response = productMapper.toResponse(product);

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), 10, TimeUnit.MINUTES);
            stringRedisTemplate.opsForValue().set("product:price:" + productId, product.getPrice().toPlainString(), 10, TimeUnit.MINUTES);
            log.info("Cached product {} in Redis", productId);
        } catch (Exception e) {
            log.warn("Failed to cache product {} in Redis: {}", productId, e.getMessage());
        }

        return response;
    }

    private void evictProductCache(UUID productId) {
        try {
            stringRedisTemplate.delete("product:" + productId);
            stringRedisTemplate.delete("product:price:" + productId);
            log.info("Evicted Redis cache for product {}", productId);
        } catch (Exception e) {
            log.warn("Failed to evict cache for product {}: {}", productId, e.getMessage());
        }
    }

    @Transactional
    public ProductResponseDto addProduct(ProductRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UUID sellerId = getApprovedSellerId();

        Product product = productMapper.toEntity(
                request,
                sellerId
        );
        Product saved = productRepository.save(product);

        log.info("Seller {} added new product: {} (ID: {})", email, saved.getName(), saved.getId());

        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponseDto updateProduct(UUID productId, ProductRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UUID sellerId = getApprovedSellerId();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));

        if (!product.getSellerId().equals(sellerId)) {
            throw new UnauthorizedProductAccessException("You do not own this product");
        }

        productMapper.updateEntity(product, request);

        Product saved = productRepository.save(product);

        evictProductCache(productId);

        log.info("Seller {} updated product: {} (ID: {})", email, saved.getName(), productId);

        return productMapper.toResponse(saved);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        UUID sellerId = getApprovedSellerId();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));

        if (!product.getSellerId().equals(sellerId)) {
            throw new UnauthorizedProductAccessException("You do not own this product");
        }

        productRepository.delete(product);

        // Evict cache
        evictProductCache(productId);

        log.info("Seller {} deleted product: {} (ID: {})", email, product.getName(), productId);
    }

    public List<ProductResponseDto> searchProduct(String keyword) {
        return productRepository.searchProduct(keyword).stream()
                .map(productMapper::toResponse)
                .toList();
    }

    private UUID getApprovedSellerId() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        User user = userRepo.findByEmail(email);

        if (user == null ||
                user.isSuspended() ||
                user.getSeller() == null ||
                user.getSeller().getStatus() != SellerStatus.APPROVED) {

            throw new UnauthorizedProductAccessException(
                    "Your seller account is not approved"
            );
        }

        return user.getSeller().getId();
    }
}
