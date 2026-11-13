package com.example.e_commerce.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCommentDto {

    private UUID id;
    private UUID userId;
    private String userFirstName;
    private String userLastName;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;
}