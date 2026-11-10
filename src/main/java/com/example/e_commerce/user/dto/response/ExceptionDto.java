package com.example.e_commerce.user.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExceptionDto {
    private String message;
    private String name;
    private int code;
    private String path;
    private LocalDateTime timestamp;

    public ExceptionDto(String message, String name, int code, String path) {
        this.message = message;
        this.name = name;
        this.code = code;
        this.path = path;
        this.timestamp = LocalDateTime.now();
    }
}
