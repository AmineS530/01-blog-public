package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // Paginated conversation with JOIN FETCH to avoid N+1 on sender/recipient/profile
    @Query(value = "SELECT m FROM Message m JOIN FETCH m.sender s LEFT JOIN FETCH s.profile " +
                   "JOIN FETCH m.recipient r LEFT JOIN FETCH r.profile " +
                   "WHERE (m.sender.id = :u1 AND m.recipient.id = :u2) OR (m.sender.id = :u2 AND m.recipient.id = :u1) " +
                   "ORDER BY m.createdAt ASC",
           countQuery = "SELECT COUNT(m) FROM Message m WHERE (m.sender.id = :u1 AND m.recipient.id = :u2) OR (m.sender.id = :u2 AND m.recipient.id = :u1)")
    Page<Message> findConversationWithUsers(@Param("u1") Long u1, @Param("u2") Long u2, Pageable pageable);

    // Paginated inbox with JOIN FETCH
    @Query(value = "SELECT m FROM Message m JOIN FETCH m.sender s LEFT JOIN FETCH s.profile " +
                   "JOIN FETCH m.recipient r LEFT JOIN FETCH r.profile " +
                   "WHERE m.id IN (" +
                   "  SELECT MAX(m2.id) FROM Message m2 WHERE m2.sender.id = :userId OR m2.recipient.id = :userId " +
                   "  GROUP BY CASE WHEN m2.sender.id = :userId THEN m2.recipient.id ELSE m2.sender.id END" +
                   ") ORDER BY m.createdAt DESC",
           countQuery = "SELECT COUNT(m) FROM Message m WHERE m.id IN (" +
                   "  SELECT MAX(m2.id) FROM Message m2 WHERE m2.sender.id = :userId OR m2.recipient.id = :userId " +
                   "  GROUP BY CASE WHEN m2.sender.id = :userId THEN m2.recipient.id ELSE m2.sender.id END" +
                   ")")
    Page<Message> findInboxWithUsers(@Param("userId") Long userId, Pageable pageable);

    // Legacy unpaginated methods (kept for read-marking which loads all messages)
    @Query("SELECT m FROM Message m WHERE (m.sender.id = :u1 AND m.recipient.id = :u2) OR (m.sender.id = :u2 AND m.recipient.id = :u1) ORDER BY m.createdAt ASC")
    List<Message> findConversation(@Param("u1") Long u1, @Param("u2") Long u2);

    @Query("SELECT m FROM Message m WHERE m.id IN (" +
           "  SELECT MAX(m2.id) FROM Message m2 WHERE m2.sender.id = :userId OR m2.recipient.id = :userId " +
           "  GROUP BY CASE WHEN m2.sender.id = :userId THEN m2.recipient.id ELSE m2.sender.id END" +
           ") ORDER BY m.createdAt DESC")
    List<Message> findInbox(@Param("userId") Long userId);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.sender.id = :senderId AND m.recipient.id = :recipientId AND m.isRead = false")
    long countUnreadFromSender(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.sender.id = :senderId AND m.recipient.id = :recipientId AND m.isRead = false")
    void markAllFromSenderAsRead(@Param("senderId") Long senderId, @Param("recipientId") Long recipientId);
}
