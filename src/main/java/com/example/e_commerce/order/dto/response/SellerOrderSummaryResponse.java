package com.example.e_commerce.order.dto.response;

import com.example.e_commerce.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerOrderSummaryResponse {

    private UUID sellerId;
    private String storeName;

    // Overall stats
    private long totalOrders;
    private long totalItemsSold;
    private BigDecimal totalRevenue;

    // Order status breakdown
    private Map<OrderStatus, Long> ordersByStatus;

    // Ratings
    private double averageRating;
    private long totalReviews;
    private long totalProducts;

    // Recent orders (paginated)
    private List<SellerOrderResponse> recentOrders;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}