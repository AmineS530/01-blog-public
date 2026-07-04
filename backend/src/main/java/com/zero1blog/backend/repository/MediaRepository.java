package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByUrl(String url);
}

