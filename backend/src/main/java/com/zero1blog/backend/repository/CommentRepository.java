package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query(value = "SELECT c FROM Comment c JOIN FETCH c.author a LEFT JOIN FETCH a.profile WHERE c.post.id = :postId ORDER BY c.createdAt ASC",
           countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.post.id = :postId")
    Page<Comment> findByPostIdWithAuthor(@Param("postId") Long postId, Pageable pageable);

    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
    long countByPostId(Long postId);
    long countByCreatedAtAfter(java.time.LocalDateTime date);
    boolean existsByAuthorIdAndCreatedAtAfter(Long authorId, java.time.LocalDateTime date);

    @org.springframework.data.jpa.repository.Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIdIn(@org.springframework.data.repository.query.Param("postIds") List<Long> postIds);
}
