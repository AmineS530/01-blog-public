package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
    long countByPostId(Long postId);
    long countByCreatedAtAfter(java.time.LocalDateTime date);

    @org.springframework.data.jpa.repository.Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIdIn(@org.springframework.data.repository.query.Param("postIds") List<Long> postIds);
}
