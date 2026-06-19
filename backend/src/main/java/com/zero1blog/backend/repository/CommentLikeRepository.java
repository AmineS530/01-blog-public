package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    long countByCommentId(Long commentId);
    Optional<CommentLike> findByCommentIdAndUserId(Long commentId, Long userId);
    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT l.comment.id, COUNT(l) FROM CommentLike l WHERE l.comment.id IN :commentIds GROUP BY l.comment.id")
    java.util.List<Object[]> countByCommentIdIn(@org.springframework.data.repository.query.Param("commentIds") java.util.List<Long> commentIds);

    @org.springframework.data.jpa.repository.Query("SELECT l.comment.id FROM CommentLike l WHERE l.user.id = :userId AND l.comment.id IN :commentIds")
    java.util.List<Long> findLikedCommentIdsByUserIdAndCommentIdIn(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("commentIds") java.util.List<Long> commentIds);
}
