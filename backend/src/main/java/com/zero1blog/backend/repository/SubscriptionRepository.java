package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Subscription;
import com.zero1blog.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    long countByFollowed(User followed);
    long countByFollower(User follower);
    boolean existsByFollowerAndFollowed(User follower, User followed);
    Optional<Subscription> findByFollowerAndFollowed(User follower, User followed);
}
