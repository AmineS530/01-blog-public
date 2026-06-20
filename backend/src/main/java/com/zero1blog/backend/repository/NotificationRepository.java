package com.zero1blog.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.zero1blog.backend.model.Notification;
import com.zero1blog.backend.model.User;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Fetch-joins actor and post so toResponse() doesn't trigger 2 lazy-load
    // queries per row (was the main cause of the slow load: N+1 -> O(n) round
    // trips).
    @Query("SELECT n FROM Notification n " +
            "LEFT JOIN FETCH n.actor " +
            "LEFT JOIN FETCH n.post " +
            "WHERE n.user = :user " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> findByUserWithActorAndPost(@Param("user") User user, Pageable pageable);

    long countByUserAndIsReadFalse(User user);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user = :user AND n.isRead = false")
    void markAllAsRead(@Param("user") User user);
}