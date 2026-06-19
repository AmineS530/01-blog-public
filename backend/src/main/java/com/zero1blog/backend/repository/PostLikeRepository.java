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

    @org.springframework.data.jpa.repository.Query("SELECT l.post.id, COUNT(l) FROM PostLike l WHERE l.post.id IN :postIds GROUP BY l.post.id")
    java.util.List<Object[]> countByPostIdIn(@org.springframework.data.repository.query.Param("postIds") java.util.List<Long> postIds);

    @org.springframework.data.jpa.repository.Query("SELECT l.post.id FROM PostLike l WHERE l.user.id = :userId AND l.post.id IN :postIds")
    java.util.List<Long> findLikedPostIdsByUserIdAndPostIdIn(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("postIds") java.util.List<Long> postIds);
}
