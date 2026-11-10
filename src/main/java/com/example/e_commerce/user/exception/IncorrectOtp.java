package com.example.e_commerce.user.exception;

public class IncorrectOtp extends RuntimeException {
    public IncorrectOtp(String message) {
        super(message);
    }
}
