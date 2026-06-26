package com.zero1blog.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zero1blog.backend.model.Post;

public interface PostRepository extends JpaRepository<Post, Long> {
    Optional<Post> findByPublicId(String publicId);

    @Query("SELECT p FROM Post p JOIN FETCH p.author a LEFT JOIN FETCH a.profile WHERE p.id = :id")
    Optional<Post> findByIdWithAuthor(@Param("id") Long id);

    @Query("SELECT p FROM Post p JOIN FETCH p.author a LEFT JOIN FETCH a.profile WHERE p.publicId = :publicId")
    Optional<Post> findByPublicIdWithAuthor(@Param("publicId") String publicId);

    long countByCreatedAtAfter(java.time.LocalDateTime date);

    // Paginated queries with JOIN FETCH to avoid N+1 on author/profile
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author a LEFT JOIN FETCH a.profile",
           countQuery = "SELECT COUNT(p) FROM Post p")
    Page<Post> findAllWithAuthor(Pageable pageable);

    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author a LEFT JOIN FETCH a.profile WHERE p.author.id = :authorId",
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.id = :authorId")
    Page<Post> findByAuthorIdWithAuthor(@Param("authorId") Long authorId, Pageable pageable);

    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author a LEFT JOIN FETCH a.profile WHERE p.author.id IN :authorIds",
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.id IN :authorIds")
    Page<Post> findByAuthorIdInWithAuthor(@Param("authorIds") List<Long> authorIds, Pageable pageable);

    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author a LEFT JOIN FETCH a.profile WHERE p.author.id NOT IN :authorIds",
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.id NOT IN :authorIds")
    Page<Post> findByAuthorIdNotInWithAuthor(@Param("authorIds") List<Long> authorIds, Pageable pageable);
}