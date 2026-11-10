package com.example.e_commerce.common.Exception;

import com.example.e_commerce.user.dto.response.ExceptionDto;
import com.example.e_commerce.user.exception.IncorrectOtp;
import com.example.e_commerce.user.exception.UserAlreadyExists;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(UserAlreadyExists.class)
    public ResponseEntity<ExceptionDto> UserAlreadyExists(HttpServletRequest request,Exception ex) {
        ExceptionDto response=new ExceptionDto(ex.getMessage(),"UserAlreadyExists",409,request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
    @ExceptionHandler(IncorrectOtp.class)
    public ResponseEntity<ExceptionDto> IncorrectOtp(HttpServletRequest request,Exception ex) {
        ExceptionDto exceptionDto=new  ExceptionDto(ex.getMessage(),"BAD REQUEST",400,request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(exceptionDto);
    }
}
