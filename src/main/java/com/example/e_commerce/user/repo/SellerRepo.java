package com.example.e_commerce.user.repo;

import com.example.e_commerce.user.enums.SellerStatus;
import com.example.e_commerce.user.model.Seller;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SellerRepo extends JpaRepository<Seller, UUID> {

    List<Seller> findAllByStatus(SellerStatus status);
}