package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.UserBlock;
import com.zero1blog.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    boolean existsByBlockerAndBlocked(User blocker, User blocked);
    Optional<UserBlock> findByBlockerAndBlocked(User blocker, User blocked);
    List<UserBlock> findByBlocker(User blocker);
    List<UserBlock> findByBlocked(User blocked);
}
