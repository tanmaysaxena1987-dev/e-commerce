package com.example.e_commerce.user.service;

import com.example.e_commerce.user.dto.request.ResetPasswordRequestDto;
import com.example.e_commerce.user.exception.IncorrectOtp;
import com.example.e_commerce.user.repo.UserRepo;
import com.example.e_commerce.user.dto.request.UserLoginRequestDto;
import com.example.e_commerce.user.model.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepo userRepo;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    @Transactional
    public String authenticate(UserLoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user= userRepo.findByEmail(request.getEmail());
        user.setLastLoginAt(LocalDateTime.now());
        userRepo.save(user);
        return jwtService.generateToken(userDetails);
    }

    public void sendResetPasswordOtp(String email) {
        User user = userRepo.findByEmail(email);
        if (user == null) {
            log.warn("Reset password OTP requested for non-existent email: {}", email);
            return;
        }
        String otp = otpService.generateOtpCode();
        otpService.saveResetPasswordOtp(otp, email);
        emailService.sendOtp(email, "Your OTP for password reset: " + otp + ". It will expire in 5 minutes.");
        log.info("Reset password OTP sent to: {}", email);
    }

    @Transactional
    public String resetPassword(ResetPasswordRequestDto request) {
        boolean verified = otpService.verifyResetPasswordOtp(request.getOtp(), request.getEmail());
        if (!verified) {
            throw new IncorrectOtp("Invalid or expired OTP");
        }
        User user = userRepo.findByEmail(request.getEmail());
        if (user == null) {
            throw new RuntimeException("User not found with email: " + request.getEmail());
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepo.save(user);
        log.info("Password reset successfully for: {}", request.getEmail());
        return "Password has been successfully updated";
    }
}
