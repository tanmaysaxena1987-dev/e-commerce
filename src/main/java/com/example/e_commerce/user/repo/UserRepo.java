package com.example.e_commerce.user.repo;

import com.example.e_commerce.user.enums.Gender;
import com.example.e_commerce.user.enums.Role;
import com.example.e_commerce.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRepo extends JpaRepository<User, UUID> {
    User findByEmail(String username);
    Page<User> findByRole(Role role, Pageable pageable);
    Page<User> findByGender(Gender gender, Pageable pageable);
    Page<User> findByAge(Integer age, Pageable pageable);
    Page<User> findByRoleAndGender(Role role, Gender gender, Pageable pageable);
    Page<User> findByRoleAndAge(Role role, Integer age, Pageable pageable);
    Page<User> findByGenderAndAge(Gender gender, Integer age, Pageable pageable);
    Page<User> findByRoleAndGenderAndAge(Role role, Gender gender, Integer age, Pageable pageable);
}
