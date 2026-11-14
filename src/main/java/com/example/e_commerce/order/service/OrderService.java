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
import com.example.e_commerce.user.model.User;
import com.example.e_commerce.user.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    private String generateTrackingNumber() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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

    /**
     * Get orders containing the current seller's products.
     */
    public Page<SellerOrderResponse> getSellerOrders(Pageable pageable) {
        UUID sellerId = getCurrentSellerUserId();

        Page<OrderItem> items = orderItemRepository.findByProductSellerId(sellerId, pageable);

        // Group by order
        Map<Order, List<OrderItem>> grouped = items.getContent().stream()
                .collect(Collectors.groupingBy(OrderItem::getOrder, Collectors.toList()));

        List<SellerOrderResponse> responses = grouped.entrySet().stream()
                .map(entry -> {
                    Order order = entry.getKey();
                    List<OrderItem> sellerItems = entry.getValue();

                    BigDecimal sellerTotal = sellerItems.stream()
                            .map(OrderItem::getSubtotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    List<SellerOrderItemResponse> itemResponses = sellerItems.stream()
                            .map(item -> SellerOrderItemResponse.builder()
                                    .orderItemId(item.getId())
                                    .productId(item.getProduct().getId())
                                    .productName(item.getProduct().getName())
                                    .productImageUrl(item.getProduct().getImageUrl())
                                    .quantity(item.getQuantity())
                                    .price(item.getPrice())
                                    .subtotal(item.getSubtotal())
                                    .build())
                            .toList();

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

        return new Page<SellerOrderResponse>() {
            @Override public int getTotalPages() { return items.getTotalPages(); }
            @Override public long getTotalElements() { return items.getTotalElements(); }
            @Override public int getNumber() { return items.getNumber(); }
            @Override public int getSize() { return items.getSize(); }
            @Override public int getNumberOfElements() { return responses.size(); }
            @Override public List<SellerOrderResponse> getContent() { return responses; }
            @Override public boolean hasContent() { return !responses.isEmpty(); }
            @Override public org.springframework.data.domain.Sort getSort() { return items.getSort(); }
            @Override public boolean isFirst() { return items.isFirst(); }
            @Override public boolean isLast() { return items.isLast(); }
            @Override public boolean hasNext() { return items.hasNext(); }
            @Override public boolean hasPrevious() { return items.hasPrevious(); }
            @Override public org.springframework.data.domain.Pageable nextPageable() { return items.nextPageable(); }
            @Override public org.springframework.data.domain.Pageable previousPageable() { return items.previousPageable(); }
            @Override public org.springframework.data.domain.Pageable previousPageable() { return items.previousPageable(); }
            @Override public <U> org.springframework.data.domain.Page<U> map(java.util.function.Function<? super SellerOrderResponse, ? extends U> converter) {
                return items.map(oi -> converter.apply(grouped.entrySet().stream()
                        .filter(e -> e.getKey().equals(oi.getOrder()))
                        .findFirst()
                        .map(entry -> {
                            Order order = entry.getKey();
                            List<OrderItem> sellerItems = entry.getValue();
                            BigDecimal sellerTotal = sellerItems.stream()
                                    .map(OrderItem::getSubtotal)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                            List<SellerOrderItemResponse> itemResponses = sellerItems.stream()
                                    .map(ite -> SellerOrderItemResponse.builder()
                                            .orderItemId(ite.getId())
                                            .productId(ite.getProduct().getId())
                                            .productName(ite.getProduct().getName())
                                            .productImageUrl(ite.getProduct().getImageUrl())
                                            .quantity(ite.getQuantity())
                                            .price(ite.getPrice())
                                            .subtotal(ite.getSubtotal())
                                            .build())
                                    .toList();
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
                        .orElse(null));
            }
            @Override public org.springframework.data.domain.Pageable getPageable() { return items.getPageable(); }
        };
    }
}