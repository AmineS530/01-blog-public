package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByCreatedAtDesc();
    List<Post> findByAuthorIdOrderByCreatedAtDesc(Long authorId);
    List<Post> findByAuthorIdInOrderByCreatedAtDesc(List<Long> authorIds);
    long countByCreatedAtAfter(java.time.LocalDateTime date);

    Page<Post> findAll(Pageable pageable);
    Page<Post> findByAuthorId(Long authorId, Pageable pageable);
    Page<Post> findByAuthorIdIn(List<Long> authorIds, Pageable pageable);
    Page<Post> findByAuthorIdNotIn(List<Long> authorIds, Pageable pageable);
}