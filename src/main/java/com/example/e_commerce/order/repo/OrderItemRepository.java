package com.example.e_commerce.order.repo;

import com.example.e_commerce.order.enums.OrderStatus;
import com.example.e_commerce.order.model.OrderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product p JOIN FETCH oi.order o WHERE p.sellerId = :sellerId ORDER BY o.createdAt DESC")
    Page<OrderItem> findByProductSellerId(@Param("sellerId") UUID sellerId, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi WHERE oi.product.sellerId = :sellerId")
    long countDistinctOrdersBySellerId(@Param("sellerId") UUID sellerId);

    @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi WHERE oi.product.sellerId = :sellerId AND oi.order.status = :status")
    long countDistinctOrdersBySellerIdAndStatus(@Param("sellerId") UUID sellerId, @Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi WHERE oi.product.sellerId = :sellerId")
    long sumQuantityBySellerId(@Param("sellerId") UUID sellerId);

    @Query("SELECT COALESCE(SUM(oi.subtotal), 0) FROM OrderItem oi WHERE oi.product.sellerId = :sellerId AND oi.order.status = 'DELIVERED'")
    BigDecimal sumRevenueBySellerId(@Param("sellerId") UUID sellerId);
}