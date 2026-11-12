package com.example.e_commerce.order.service;

import com.example.e_commerce.cart.model.Cart;
import com.example.e_commerce.cart.model.CartItem;
import com.example.e_commerce.cart.repo.CartItemRepository;
import com.example.e_commerce.cart.repo.CartRepository;
import com.example.e_commerce.order.dto.request.PlaceOrderRequest;
import com.example.e_commerce.order.dto.request.ReturnOrderRequest;
import com.example.e_commerce.order.dto.response.OrderResponse;
import com.example.e_commerce.order.dto.response.OrderTrackingResponse;
import com.example.e_commerce.order.enums.OrderStatus;
import com.example.e_commerce.order.exception.OrderNotFoundException;
import com.example.e_commerce.order.mapper.OrderMapper;
import com.example.e_commerce.order.model.Order;
import com.example.e_commerce.order.model.OrderItem;
import com.example.e_commerce.order.repo.OrderRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
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

    private String generateTrackingNumber() {
        return "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        User user = getCurrentUser();

        // Get user's cart
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("Cart is empty. Add items before placing an order."));

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty. Add items before placing an order.");
        }

        // Create the order
        Order order = Order.builder()
                .user(user)
                .trackingNumber(generateTrackingNumber())
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry())
                .phoneNumber(request.getPhoneNumber())
                .deliveryNotes(request.getDeliveryNotes())
                .shippingCost(request.getShippingCost())
                .tax(BigDecimal.ZERO)
                .totalAmount(cart.getTotal())
                .build();

        // Convert cart items to order items (use a local copy so order is effectively final in the lambda)
        final Order finalOrder = order;
        List<OrderItem> orderItems = cart.getItems().stream()
                .map(cartItem -> {
                    Product product = cartItem.getProduct();

                    // Decrease stock
                    if (product.getStockQuantity() < cartItem.getQuantity()) {
                        throw new IllegalStateException(
                                "Insufficient stock for product: " + product.getName() +
                                ". Available: " + product.getStockQuantity() +
                                ", requested: " + cartItem.getQuantity());
                    }
                    product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
                    productRepository.save(product);

                    return OrderItem.builder()
                            .order(finalOrder)
                            .product(product)
                            .quantity(cartItem.getQuantity())
                            .price(product.getPrice())
                            .subtotal(cartItem.getSubtotal())
                            .build();
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

    @Transactional
    public void deleteOrder(UUID orderId) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        // Only allow deletion of cancelled or returned orders
        if (order.getStatus() != OrderStatus.CANCELLED &&
                order.getStatus() != OrderStatus.RETURNED &&
                order.getStatus() != OrderStatus.REFUNDED) {
            throw new IllegalStateException(
                    "Only cancelled, returned or refunded orders can be deleted. Current status: " + order.getStatus());
        }

        orderRepository.delete(order);
        log.info("Order deleted. Order ID: {}", orderId);
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

        // Ensure the order belongs to the current user
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
}