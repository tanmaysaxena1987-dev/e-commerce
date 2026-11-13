package com.example.e_commerce.order.mapper;

import com.example.e_commerce.cart.model.CartItem;
import com.example.e_commerce.order.dto.request.PlaceOrderRequest;
import com.example.e_commerce.order.dto.request.ReviewRequest;
import com.example.e_commerce.order.dto.response.OrderItemResponse;
import com.example.e_commerce.order.dto.response.OrderResponse;
import com.example.e_commerce.order.dto.response.OrderTrackingResponse;
import com.example.e_commerce.order.dto.response.ReviewResponse;
import com.example.e_commerce.order.enums.OrderStatus;
import com.example.e_commerce.order.model.Order;
import com.example.e_commerce.order.model.OrderItem;
import com.example.e_commerce.order.model.Review;
import com.example.e_commerce.product.model.Product;
import com.example.e_commerce.user.model.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .trackingNumber(order.getTrackingNumber())
                .userId(order.getUser().getId())
                .items(itemResponses)
                .status(order.getStatus())
                .returnReason(order.getReturnReason())
                .returnComment(order.getReturnComment())
                .totalAmount(order.getTotalAmount())
                .shippingCost(order.getShippingCost())
                .tax(order.getTax())
                .orderTotal(order.getOrderTotal())
                .shippingAddress(order.getShippingAddress())
                .city(order.getCity())
                .state(order.getState())
                .zipCode(order.getZipCode())
                .country(order.getCountry())
                .phoneNumber(order.getPhoneNumber())
                .deliveryNotes(order.getDeliveryNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .deliveredAt(order.getDeliveredAt())
                .cancelledAt(order.getCancelledAt())
                .returnedAt(order.getReturnedAt())
                .build();
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productImageUrl(item.getProduct().getImageUrl())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .subtotal(item.getSubtotal())
                .build();
    }

    public Order toOrder(User user, String trackingNumber, PlaceOrderRequest request, BigDecimal totalAmount) {
        return Order.builder()
                .user(user)
                .trackingNumber(trackingNumber)
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
                .totalAmount(totalAmount)
                .build();
    }

    public OrderItem toOrderItem(Order order, CartItem cartItem, Product product) {
        return OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(cartItem.getQuantity())
                .price(product.getPrice())
                .subtotal(cartItem.getSubtotal())
                .build();
    }

    public Review toReview(OrderItem orderItem, Product product, User user, ReviewRequest request) {
        return Review.builder()
                .orderItem(orderItem)
                .product(product)
                .user(user)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();
    }

    public ReviewResponse toReviewResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .orderItemId(review.getOrderItem().getId())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    public OrderTrackingResponse toTrackingResponse(Order order) {
        return OrderTrackingResponse.builder()
                .id(order.getId())
                .trackingNumber(order.getTrackingNumber())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .shippingAddress(order.getShippingAddress())
                .city(order.getCity())
                .state(order.getState())
                .zipCode(order.getZipCode())
                .country(order.getCountry())
                .build();
    }
}