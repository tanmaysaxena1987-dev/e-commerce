package com.example.e_commerce.common.Exception;

import com.example.e_commerce.order.exception.OrderNotFoundException;
import com.example.e_commerce.product.exception.InsufficientStockException;
import com.example.e_commerce.product.exception.ProductNotFoundException;
import com.example.e_commerce.product.exception.UnauthorizedProductAccessException;
import com.example.e_commerce.user.dto.response.ExceptionDto;
import com.example.e_commerce.user.exception.IncorrectOtp;
import com.example.e_commerce.user.exception.UserAlreadyExists;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExists.class)
    public ResponseEntity<ExceptionDto> userAlreadyExists(HttpServletRequest request, Exception ex) {
        ExceptionDto response = new ExceptionDto(ex.getMessage(), "UserAlreadyExists", 409, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(IncorrectOtp.class)
    public ResponseEntity<ExceptionDto> incorrectOtp(HttpServletRequest request, Exception ex) {
        ExceptionDto exceptionDto = new ExceptionDto(ex.getMessage(), "BAD REQUEST", 400, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exceptionDto);
    }

    // ========== Product Module Exceptions ==========

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ExceptionDto> productNotFound(HttpServletRequest request, Exception ex) {
        ExceptionDto response = new ExceptionDto(ex.getMessage(), "ProductNotFound", 404, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(UnauthorizedProductAccessException.class)
    public ResponseEntity<ExceptionDto> unauthorizedProductAccess(HttpServletRequest request, Exception ex) {
        ExceptionDto response = new ExceptionDto(ex.getMessage(), "UnauthorizedAccess", 403, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ExceptionDto> insufficientStock(HttpServletRequest request, Exception ex) {
        ExceptionDto response = new ExceptionDto(ex.getMessage(), "InsufficientStock", 409, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ExceptionDto> objectOptimisticLock(HttpServletRequest request, Exception ex) {
        ExceptionDto response = new ExceptionDto(
                "Concurrent modification detected. Please retry.",
                "OptimisticLock", 409, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // ========== Order Module Exceptions ==========

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ExceptionDto> orderNotFound(HttpServletRequest request, Exception ex) {
        ExceptionDto response = new ExceptionDto(ex.getMessage(), "OrderNotFound", 404, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ExceptionDto> illegalState(HttpServletRequest request, Exception ex) {
        ExceptionDto response = new ExceptionDto(ex.getMessage(), "BadRequest", 400, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExceptionDto> illegalArgument(HttpServletRequest request, Exception ex) {
        ExceptionDto response = new ExceptionDto(ex.getMessage(), "BadRequest", 400, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionDto> validationError(HttpServletRequest request, MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        ExceptionDto response = new ExceptionDto(message, "ValidationError", 400, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
