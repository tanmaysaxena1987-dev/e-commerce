package com.example.e_commerce.product.repo;

import com.example.e_commerce.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Query("SELECT p FROM Product AS p WHERE "+
    "LOWER(p.name) LIKE LOWER(CONCAT('%',:keyword,'%') ) OR " +
            "LOWER(p.brand) LIKE LOWER(CONCAT('%',:keyword,'%') ) OR " +
            "LOWER(p.description) LIKE LOWER(concat('%',:keyowrd,'%') ) ")
    List<Product> searchProduct(String keyword);
}
