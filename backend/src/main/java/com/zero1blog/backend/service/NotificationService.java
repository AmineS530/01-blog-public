package com.zero1blog.backend.service;

import com.zero1blog.backend.dto.NotificationResponse;
import com.zero1blog.backend.model.Notification;
import com.zero1blog.backend.model.Post;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.NotificationRepository;
import com.zero1blog.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void createNotification(String type, String message, User recipient, User actor, Post post) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }

        Notification notification = Notification.builder()
                .type(type)
                .message(message)
                .user(recipient)
                .actor(actor)
                .post(post)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Transactional
    public void markAsRead(Long id, String userPublicId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        if (!notification.getUser().getPublicId().equals(userPublicId)) {
            throw new RuntimeException("Unauthorized");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        notificationRepository.markAllAsRead(user);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .message(n.getMessage())
                .isRead(n.isRead())
                .actorUsername(n.getActor() != null ? n.getActor().getUsername() : null)
                .postId(n.getPost() != null ? n.getPost().getId() : null)
                .createdAt(n.getCreatedAt())
                .build();
    }
}
