package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

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
}
