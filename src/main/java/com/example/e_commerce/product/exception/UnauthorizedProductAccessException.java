package com.example.e_commerce.product.exception;

public class UnauthorizedProductAccessException extends RuntimeException {
    public UnauthorizedProductAccessException(String message) {
        super(message);
    }
}
