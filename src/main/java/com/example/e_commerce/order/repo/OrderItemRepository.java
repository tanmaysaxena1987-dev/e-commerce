package com.example.e_commerce.order.repo;

import com.example.e_commerce.order.model.OrderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderId(UUID orderId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product p JOIN FETCH oi.order o WHERE p.sellerId = :sellerId ORDER BY o.createdAt DESC")
    Page<OrderItem> findByProductSellerId(@Param("sellerId") UUID sellerId, Pageable pageable);
}