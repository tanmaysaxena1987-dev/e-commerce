package com.example.e_commerce.product.model;

import com.example.e_commerce.product.enums.Category;
import com.example.e_commerce.product.enums.Status;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private String brand;
    @Column(nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(nullable = false)
    private String imageUrl;
    @Builder.Default
    private BigDecimal averageRating= BigDecimal.valueOf(0);
    @Builder.Default
    private BigDecimal reviewCount= BigDecimal.valueOf( 0);

    private String suspensionReason;

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
