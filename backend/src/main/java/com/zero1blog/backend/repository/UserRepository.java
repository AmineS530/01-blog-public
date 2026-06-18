package com.zero1blog.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email, Pageable pageable);

    long countByCreatedAtAfter(java.time.LocalDateTime date);

    @Query("SELECT u FROM User u WHERE u.id <> :currentUserId " +
            "AND u.isBanned = false " +
            "AND u.id NOT IN (SELECT s.followed.id FROM Subscription s WHERE s.follower.id = :currentUserId) " +
            "AND u.id NOT IN (SELECT ub.blocked.id FROM UserBlock ub WHERE ub.blocker.id = :currentUserId) " +
            "AND u.id NOT IN (SELECT ub.blocker.id FROM UserBlock ub WHERE ub.blocked.id = :currentUserId)")
    List<User> findRecommendedUsers(@Param("currentUserId") Long currentUserId, Pageable pageable);
}