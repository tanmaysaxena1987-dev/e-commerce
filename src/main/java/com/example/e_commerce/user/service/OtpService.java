package com.example.e_commerce.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {
    private final SecureRandom secureRandom=new SecureRandom();
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;
    public String generateOtpCode() {return String.format("%08d", secureRandom.nextInt(100_000_000));}
    public void saveOtpCOde(String otp,String email){
        String key="Email Verification Code: "+email;
        log.info("Saving OTP for email verification code: "+key);
        String hashedOtp= passwordEncoder.encode(otp);
        stringRedisTemplate.opsForValue().set(key, hashedOtp, Duration.ofMinutes(5));
    }
    public boolean verifyOtp(String otp,String email){
        String key="Email Verification Code: "+email;
        log.info("Verifying OTP for email verification code: "+key);
        String hashedOtp=stringRedisTemplate.opsForValue().get(key);
        Boolean matches=passwordEncoder.matches(otp,hashedOtp);
        if(matches){
            stringRedisTemplate.delete(key);
        }
        return matches;
    }
    public void saveResetPasswordOtp(String otp, String email){
        String key="Reset Password: "+email;
        log.info("Saving OTP for reset password: "+key);
        String hashedOtp= passwordEncoder.encode(otp);
        stringRedisTemplate.opsForValue().set(key, hashedOtp, Duration.ofMinutes(5));
    }
    public boolean verifyResetPasswordOtp(String otp, String email){
        String key="Reset Password: "+email;
        log.info("Verifying OTP for reset password: "+key);
        String hashedOtp=stringRedisTemplate.opsForValue().get(key);
        Boolean matches=passwordEncoder.matches(otp,hashedOtp);
        if(matches){
            stringRedisTemplate.delete(key);
        }
        return matches;
    }
}