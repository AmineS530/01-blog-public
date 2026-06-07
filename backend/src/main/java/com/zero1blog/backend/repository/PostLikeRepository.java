package com.zero1blog.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.zero1blog.backend.model.PostLike;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    long countByPostId(Long postId);
    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);
    boolean existsByPostIdAndUserId(Long postId, Long userId);
}
