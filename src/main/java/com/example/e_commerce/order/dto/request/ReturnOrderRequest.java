package com.example.e_commerce.order.dto.request;

import com.example.e_commerce.order.enums.ReturnReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReturnOrderRequest {

    @NotNull(message = "Return reason is required")
    private ReturnReason returnReason;

    @Size(max = 1000, message = "Comment cannot exceed 1000 characters")
    private String comment;
}