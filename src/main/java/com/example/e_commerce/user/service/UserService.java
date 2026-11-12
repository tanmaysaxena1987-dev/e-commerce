package com.example.e_commerce.user.service;

import com.example.e_commerce.user.dto.request.AddressRequestDto;
import com.example.e_commerce.user.dto.request.UpdateAddressRequestDto;
import com.example.e_commerce.user.dto.response.AddressResponseDto;
import com.example.e_commerce.user.exception.AddressNotFoundException;
import com.example.e_commerce.user.exception.IncorrectOtp;
import com.example.e_commerce.user.mapper.AddressMapper;
import com.example.e_commerce.user.repo.AddressRepo;
import com.example.e_commerce.user.repo.UserRepo;
import com.example.e_commerce.user.dto.request.UserRegisterRequestDto;
import com.example.e_commerce.user.dto.response.UserRegisterResponseDto;
import com.example.e_commerce.user.mapper.UserMapper;
import com.example.e_commerce.user.model.Address;
import com.example.e_commerce.user.model.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepo userRepo;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final OtpService otpService;
    private final AddressRepo addressRepo;
    private final AddressMapper addressMapper;
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

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepo.findByEmail(email);
    }

    public List<AddressResponseDto> getAddresses() {
        User user = getCurrentUser();
        return user.getAddresses().stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Transactional
    public AddressResponseDto addAddress(AddressRequestDto request) {
        User user = getCurrentUser();

        // If this is the first address or marked as default, ensure only one default
        if (request.isDefault() || user.getAddresses().isEmpty()) {
            user.getAddresses().forEach(a -> a.setDefault(false));
            request.setDefault(true);
        }

        Address address = addressMapper.toEntity(request, user);
        user.getAddresses().add(address);
        Address saved = addressRepo.save(address);

        log.info("User {} added new address (ID: {})", user.getEmail(), saved.getId());

        return addressMapper.toResponse(saved);
    }

    @Transactional
    public AddressResponseDto updateAddress(UUID addressId, UpdateAddressRequestDto request) {
        User user = getCurrentUser();

        Address address = addressRepo.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new AddressNotFoundException("Address not found with id: " + addressId));

        // If marking as default, unset all other defaults
        if (request.isDefault()) {
            user.getAddresses().forEach(a -> a.setDefault(false));
        }

        addressMapper.updateEntity(address, request);

        Address saved = addressRepo.save(address);

        log.info("User {} updated address (ID: {})", user.getEmail(), addressId);

        return addressMapper.toResponse(saved);
    }

    @Transactional
    public void deleteAddress(UUID addressId) {
        User user = getCurrentUser();

        Address address = addressRepo.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new AddressNotFoundException("Address not found with id: " + addressId));

        user.getAddresses().remove(address);
        addressRepo.delete(address);

        log.info("User {} deleted address (ID: {})", user.getEmail(), addressId);
    }
}
