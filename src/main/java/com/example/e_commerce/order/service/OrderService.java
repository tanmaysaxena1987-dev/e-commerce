package com.example.e_commerce.order.service;

import com.example.e_commerce.cart.model.Cart;
import com.example.e_commerce.cart.model.CartItem;
import com.example.e_commerce.cart.repo.CartItemRepository;
import com.example.e_commerce.cart.repo.CartRepository;
import com.example.e_commerce.order.dto.request.PlaceOrderRequest;
import com.example.e_commerce.order.dto.request.ReturnOrderRequest;
import com.example.e_commerce.order.dto.request.ReviewRequest;
import com.example.e_commerce.order.dto.response.OrderResponse;
import com.example.e_commerce.order.dto.response.OrderTrackingResponse;
import com.example.e_commerce.order.dto.response.ReviewResponse;
import com.example.e_commerce.order.dto.response.SellerOrderItemResponse;
import com.example.e_commerce.order.dto.response.SellerOrderResponse;
import com.example.e_commerce.order.dto.response.SellerOrderSummaryResponse;
import com.example.e_commerce.order.enums.OrderStatus;
import com.example.e_commerce.order.exception.OrderNotFoundException;
import com.example.e_commerce.order.mapper.OrderMapper;
import com.example.e_commerce.order.model.Order;
import com.example.e_commerce.order.model.OrderItem;
import com.example.e_commerce.order.model.Review;
import com.example.e_commerce.order.repo.OrderItemRepository;
import com.example.e_commerce.order.repo.OrderRepository;
import com.example.e_commerce.order.repo.ReviewRepository;
import com.example.e_commerce.product.exception.InsufficientStockException;
import com.example.e_commerce.product.model.Product;
import com.example.e_commerce.product.repo.ProductRepository;
import com.example.e_commerce.user.enums.SellerStatus;
import com.example.e_commerce.user.model.Seller;
import com.example.e_commerce.user.model.User;
import com.example.e_commerce.user.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepo userRepo;
    private final OrderMapper orderMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepo.findByEmail(email);
    }

    private UUID getCurrentSellerUserId() {
        User user = getCurrentUser();
        if (user.getSeller() == null) {
            throw new IllegalStateException("User is not a seller");
        }
        return user.getSeller().getId();
    }

    private Seller getCurrentApprovedSeller() {
        User user = getCurrentUser();
        Seller seller = user.getSeller();
        if (seller == null || seller.getStatus() != SellerStatus.APPROVED) {
            throw new IllegalStateException("Your seller account is not approved");
        }
        return seller;
    }

    private String generateTrackingNumber() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void evictProductCache(UUID productId) {
        try {
            stringRedisTemplate.delete("product:" + productId);
            stringRedisTemplate.delete("product:price:" + productId);
            log.info("Evicted Redis cache for product {}", productId);
        } catch (Exception e) {
            log.warn("Failed to evict Redis cache for product {}: {}", productId, e.getMessage());
        }
    }

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        User user = getCurrentUser();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Cart is empty. Add items before placing an order."));

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty. Add items before placing an order.");
        }

        Order order = orderMapper.toOrder(user, generateTrackingNumber(), request, cart.getTotal());
        final Order finalOrder = order;

        List<OrderItem> orderItems = cart.getItems().stream()
                .map(cartItem -> {
                    Product product = cartItem.getProduct();

                    if (product.getStockQuantity() < cartItem.getQuantity()) {
                        throw new InsufficientStockException(
                                "Insufficient stock for product: " + product.getName() +
                                        ". Available: " + product.getStockQuantity() +
                                        ", requested: " + cartItem.getQuantity());
                    }

                    // --- Decrement stock ---
                    product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
                    productRepository.save(product);

                    // Evict Redis cache so the updated stock is fetched fresh
                    evictProductCache(product.getId());

                    return orderMapper.toOrderItem(finalOrder, cartItem, product);
                })
                .toList();

        order.setItems(orderItems);
        order = orderRepository.save(order);

        // Clear the cart
        cartItemRepository.deleteAll(cart.getItems());
        cart.getItems().clear();
        cartRepository.save(cart);

        log.info("Order placed successfully. Order ID: {}, Tracking: {}",
                order.getId(), order.getTrackingNumber());

        return orderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() == OrderStatus.SHIPPED ||
                order.getStatus() == OrderStatus.DELIVERED ||
                order.getStatus() == OrderStatus.CANCELLED ||
                order.getStatus() == OrderStatus.RETURNED) {
            throw new IllegalStateException("Order cannot be cancelled. Current status: " + order.getStatus());
        }

        // Restore stock
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);

            // Evict Redis cache so the restored stock is fetched fresh
            evictProductCache(product.getId());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order = orderRepository.save(order);

        log.info("Order cancelled. Order ID: {}", order.getId());
        return orderMapper.toResponse(order);
    }

    public OrderTrackingResponse trackOrder(UUID orderId) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        log.info("Tracking order. Order ID: {}, Status: {}", order.getId(), order.getStatus());
        return orderMapper.toTrackingResponse(order);
    }

    public OrderTrackingResponse trackOrderByTrackingNumber(String trackingNumber) {
        User user = getCurrentUser();
        Order order = orderRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with tracking number: " + trackingNumber));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new OrderNotFoundException("Order not found with tracking number: " + trackingNumber);
        }

        return orderMapper.toTrackingResponse(order);
    }

    @Transactional
    public OrderResponse returnOrder(UUID orderId, ReturnOrderRequest request) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Only delivered orders can be returned. Current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.RETURN_REQUESTED);
        order.setReturnReason(request.getReturnReason());
        order.setReturnComment(request.getComment());
        order = orderRepository.save(order);

        log.info("Return requested for order. Order ID: {}, Reason: {}",
                order.getId(), request.getReturnReason());

        return orderMapper.toResponse(order);
    }

    @Transactional
    public ReviewResponse reviewOrderItem(UUID orderItemId, ReviewRequest request) {
        User user = getCurrentUser();

        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new OrderNotFoundException("Order item not found with id: " + orderItemId));

        Order order = orderItem.getOrder();

        if (!order.getUser().getId().equals(user.getId())) {
            throw new OrderNotFoundException("Order item not found with id: " + orderItemId);
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "You can only review items from delivered orders. Current order status: " + order.getStatus());
        }

        if (reviewRepository.existsByOrderItemIdAndUserId(orderItemId, user.getId())) {
            throw new IllegalStateException("You have already reviewed this item");
        }

        Product product = orderItem.getProduct();

        Review review = orderMapper.toReview(orderItem, product, user, request);
        reviewRepository.save(review);

        Double avgRating = reviewRepository.averageRatingByProductId(product.getId());
        Long reviewCount = reviewRepository.countByProductId(product.getId());

        if (avgRating != null) {
            product.setAverageRating(BigDecimal.valueOf(Math.round(avgRating * 100.0) / 100.0));
        }
        product.setReviewCount(BigDecimal.valueOf(reviewCount));
        productRepository.save(product);

        log.info("User {} reviewed order item {} for product {} with {} stars",
                user.getEmail(), orderItemId, product.getName(), request.getRating());

        return orderMapper.toReviewResponse(review);
    }

    public OrderResponse getOrder(UUID orderId) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        return orderMapper.toResponse(order);
    }

    public Page<OrderResponse> getUserOrders(Pageable pageable) {
        User user = getCurrentUser();
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        return orders.map(orderMapper::toResponse);
    }

    public Page<OrderResponse> getUserOrdersByStatus(OrderStatus status, Pageable pageable) {
        User user = getCurrentUser();
        Page<Order> orders = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), status, pageable);
        return orders.map(orderMapper::toResponse);
    }

    // ──────────────────────────────────────────────
    //  Seller order summary / dashboard
    // ──────────────────────────────────────────────

    public SellerOrderSummaryResponse getSellerOrderSummary(Pageable pageable) {
        Seller seller = getCurrentApprovedSeller();
        UUID sellerId = seller.getId();

        // Overall stats
        long totalOrders = orderItemRepository.countDistinctOrdersBySellerId(sellerId);
        long totalItemsSold = orderItemRepository.sumQuantityBySellerId(sellerId);
        BigDecimal totalRevenue = orderItemRepository.sumRevenueBySellerId(sellerId);

        // Status breakdown
        Map<OrderStatus, Long> ordersByStatus = new EnumMap<>(OrderStatus.class);
        Arrays.stream(OrderStatus.values()).forEach(status ->
            ordersByStatus.put(status, orderItemRepository.countDistinctOrdersBySellerIdAndStatus(sellerId, status))
        );

        // Ratings
        double averageRating = reviewRepository.averageRatingBySellerId(sellerId);
        long totalReviews = reviewRepository.countBySellerId(sellerId);
        long totalProducts = productRepository.countBySellerId(sellerId);

        // Recent orders (seller's items grouped by order)
        Page<OrderItem> sellerItemsPage = orderItemRepository.findByProductSellerId(sellerId, pageable);

        // Group items by order and build SellerOrderResponse list
        Map<UUID, List<OrderItem>> grouped = sellerItemsPage.getContent().stream()
                .collect(Collectors.groupingBy(oi -> oi.getOrder().getId()));

        List<SellerOrderResponse> recentOrders = grouped.values().stream()
                .map(items -> {
                    Order order = items.get(0).getOrder();
                    List<SellerOrderItemResponse> itemResponses = items.stream()
                            .map(this::toSellerOrderItemResponse)
                            .toList();
                    BigDecimal sellerTotal = itemResponses.stream()
                            .map(SellerOrderItemResponse::getSubtotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return SellerOrderResponse.builder()
                            .orderId(order.getId())
                            .trackingNumber(order.getTrackingNumber())
                            .status(order.getStatus())
                            .createdAt(order.getCreatedAt())
                            .shippingAddress(order.getShippingAddress())
                            .city(order.getCity())
                            .state(order.getState())
                            .zipCode(order.getZipCode())
                            .country(order.getCountry())
                            .phoneNumber(order.getPhoneNumber())
                            .items(itemResponses)
                            .sellerTotal(sellerTotal)
                            .build();
                })
                .toList();

        return SellerOrderSummaryResponse.builder()
                .sellerId(sellerId)
                .storeName(seller.getStoreName())
                .totalOrders(totalOrders)
                .totalItemsSold(totalItemsSold)
                .totalRevenue(totalRevenue)
                .ordersByStatus(ordersByStatus)
                .averageRating(Math.round(averageRating * 100.0) / 100.0)
                .totalReviews(totalReviews)
                .totalProducts(totalProducts)
                .recentOrders(recentOrders)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(sellerItemsPage.getTotalElements())
                .totalPages(sellerItemsPage.getTotalPages())
                .build();
    }

    private SellerOrderItemResponse toSellerOrderItemResponse(OrderItem oi) {
        return SellerOrderItemResponse.builder()
                .orderItemId(oi.getId())
                .productId(oi.getProduct().getId())
                .productName(oi.getProduct().getName())
                .productImageUrl(oi.getProduct().getImageUrl())
                .quantity(oi.getQuantity())
                .price(oi.getPrice())
                .subtotal(oi.getSubtotal())
                .build();
    }
}