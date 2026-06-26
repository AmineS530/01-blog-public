package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.UserBlock;
import com.zero1blog.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    boolean existsByBlockerAndBlocked(User blocker, User blocked);
    Optional<UserBlock> findByBlockerAndBlocked(User blocker, User blocked);

    // ID-only queries to avoid N+1 — return just the user IDs, not full entities
    @Query("SELECT ub.blocked.id FROM UserBlock ub WHERE ub.blocker.id = :blockerId")
    List<Long> findBlockedUserIdsByBlockerId(@Param("blockerId") Long blockerId);

    @Query("SELECT ub.blocker.id FROM UserBlock ub WHERE ub.blocked.id = :blockedId")
    List<Long> findBlockerUserIdsByBlockedId(@Param("blockedId") Long blockedId);

    // Keep legacy entity-returning methods for toggleBlock which needs the entity
    List<UserBlock> findByBlocker(User blocker);
    List<UserBlock> findByBlocked(User blocked);
}
