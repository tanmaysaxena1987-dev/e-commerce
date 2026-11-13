package com.example.e_commerce.cart.mapper;

import com.example.e_commerce.cart.dto.request.AddToCartRequest;
import com.example.e_commerce.cart.dto.response.CartItemResponse;
import com.example.e_commerce.cart.dto.response.CartResponse;
import com.example.e_commerce.cart.model.Cart;
import com.example.e_commerce.cart.model.CartItem;
import com.example.e_commerce.product.model.Product;
import com.example.e_commerce.user.model.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CartMapper {

    public CartResponse toResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        int totalItems = cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        return CartResponse.builder()
                .id(cart.getId())
                .userId(cart.getUser().getId())
                .items(itemResponses)
                .total(cart.getTotal())
                .totalItems(totalItems)
                .build();
    }

    public Cart newCart(User user) {
        return Cart.builder()
                .user(user)
                .items(new ArrayList<>())
                .build();
    }

    public CartItem toCartItem(Cart cart, Product product, AddToCartRequest request) {
        return CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(request.getQuantity())
                .build();
    }

    public CartItemResponse toItemResponse(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productImageUrl(item.getProduct().getImageUrl())
                .price(item.getProduct().getPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .build();
    }
}