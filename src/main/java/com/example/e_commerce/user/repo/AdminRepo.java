package com.example.e_commerce.user.repo;

import com.example.e_commerce.user.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AdminRepo extends JpaRepository<Admin, UUID> {
}
