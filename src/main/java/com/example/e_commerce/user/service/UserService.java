package com.example.e_commerce.user.service;

import com.example.e_commerce.user.exception.IncorrectOtp;
import com.example.e_commerce.user.repo.UserRepo;
import com.example.e_commerce.user.dto.request.UserRegisterRequestDto;
import com.example.e_commerce.user.dto.response.UserRegisterResponseDto;
import com.example.e_commerce.user.mapper.UserMapper;
import com.example.e_commerce.user.model.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepo userRepo;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final OtpService otpService;
    @Transactional
    public UserRegisterResponseDto register(UserRegisterRequestDto request) {
        if (userRepo.findByEmail(request.getEmail()) != null) {
            throw new RuntimeException("Email already registered");
        }

        User user = userMapper.toEntity(request);
        User saved=userRepo.saveAndFlush(user);
        return userMapper.toRegisterResponse(saved);
    }
    public void sendOtp(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        String otp= otpService.generateOtpCode();
        otpService.saveOtpCOde(otp,email);
        emailService.sendOtp(email,"Your OTP for email verification: "+otp+" it'll expire in 5 minutes");
    }
    public void verifyOtp(String otp){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        boolean verified=otpService.verifyOtp(otp,email);
        if(verified){
            User user=userRepo.findByEmail(email);
            user.setEmailVerified(true);
            userRepo.saveAndFlush(user);
        }
        else
            throw new IncorrectOtp("Invalid OTP");
    }
}
