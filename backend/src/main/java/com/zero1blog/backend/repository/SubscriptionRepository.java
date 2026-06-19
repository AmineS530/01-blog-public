package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Subscription;
import com.zero1blog.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    long countByFollowed(User followed);
    long countByFollower(User follower);
    boolean existsByFollowerAndFollowed(User follower, User followed);
    Optional<Subscription> findByFollowerAndFollowed(User follower, User followed);
    List<Subscription> findByFollower(User follower);
    List<Subscription> findByFollowed(User followed);

    @org.springframework.data.jpa.repository.Query("SELECT s.followed.id, COUNT(s) FROM Subscription s WHERE s.followed.id IN :followedIds GROUP BY s.followed.id")
    java.util.List<Object[]> countFollowersByFollowedIds(@org.springframework.data.repository.query.Param("followedIds") java.util.List<Long> followedIds);

    @org.springframework.data.jpa.repository.Query("SELECT s.follower.id, COUNT(s) FROM Subscription s WHERE s.follower.id IN :followerIds GROUP BY s.follower.id")
    java.util.List<Object[]> countFollowingByFollowerIds(@org.springframework.data.repository.query.Param("followerIds") java.util.List<Long> followerIds);
}
