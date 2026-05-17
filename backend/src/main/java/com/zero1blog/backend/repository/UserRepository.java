package com.zero1blog.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zero1blog.backend.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByPublicId(String publicId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    long countByIsBanned(boolean isBanned);

    org.springframework.data.domain.Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email, org.springframework.data.domain.Pageable pageable);

    long countByCreatedAtAfter(java.time.LocalDateTime date);
}