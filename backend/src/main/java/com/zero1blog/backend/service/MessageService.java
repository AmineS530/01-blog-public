package com.zero1blog.backend.service;

import com.zero1blog.backend.dto.MessageRequest;
import com.zero1blog.backend.dto.MessageResponse;
import com.zero1blog.backend.model.Message;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.MessageRepository;
import com.zero1blog.backend.repository.UserBlockRepository;
import com.zero1blog.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository,
                          UserBlockRepository userBlockRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.userBlockRepository = userBlockRepository;
    }

    @Transactional
    public MessageResponse sendMessage(String senderPublicId, MessageRequest request) {
        User sender = userRepository.findByPublicId(senderPublicId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User recipient = userRepository.findByPublicId(request.getRecipientPublicId())
                .orElseThrow(() -> new RuntimeException("Recipient not found"));

        if (sender.isBanned()) {
            throw new RuntimeException("You are banned and cannot send messages");
        }
        if (recipient.isBanned()) {
            throw new RuntimeException("Recipient account has been suspended");
        }

        // Check blocks
        if (userBlockRepository.existsByBlockerAndBlocked(sender, recipient) ||
                userBlockRepository.existsByBlockerAndBlocked(recipient, sender)) {
            throw new RuntimeException("You cannot exchange messages with this user");
        }

        Message message = new Message();
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(request.getContent());
        message.setMediaUrl(request.getMediaUrl());
        message.setRead(false);

        Message saved = messageRepository.save(message);
        MessageResponse response = toResponse(saved);
        com.zero1blog.backend.config.GlobalWebSocketHandler.sendToUser(recipient.getPublicId(), "NEW_MESSAGE", response);
        com.zero1blog.backend.config.GlobalWebSocketHandler.sendToUser(sender.getPublicId(), "NEW_MESSAGE", response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getConversation(String user1PublicId, String user2PublicId) {
        User user1 = userRepository.findByPublicId(user1PublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User user2 = userRepository.findByPublicId(user2PublicId)
                .orElseThrow(() -> new RuntimeException("Chat partner not found"));

        return messageRepository.findConversation(user1.getId(), user2.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getInbox(String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return messageRepository.findInbox(user.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(Long messageId, String recipientPublicId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!message.getRecipient().getPublicId().equals(recipientPublicId)) {
            throw new RuntimeException("Unauthorized");
        }

        message.setRead(true);
        messageRepository.save(message);
    }

    @Transactional
    public void markConversationAsRead(String currentUserId, String partnerId) {
        User me = userRepository.findByPublicId(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User partner = userRepository.findByPublicId(partnerId)
                .orElseThrow(() -> new RuntimeException("Partner not found"));

        List<Message> conversation = messageRepository.findConversation(me.getId(), partner.getId());
        boolean changed = false;
        for (Message m : conversation) {
            if (m.getRecipient().getId().equals(me.getId()) && !m.isRead()) {
                m.setRead(true);
                changed = true;
            }
        }
        if (changed) {
            messageRepository.saveAll(conversation);
        }
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return messageRepository.countByRecipientIdAndIsReadFalse(user.getId());
    }

    private MessageResponse toResponse(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getSender().getPublicId(),
                m.getSender().getUsername(),
                m.getSender().getProfile() != null ? m.getSender().getProfile().getAvatarUrl() : null,
                m.getRecipient().getPublicId(),
                m.getRecipient().getUsername(),
                m.getRecipient().getProfile() != null ? m.getRecipient().getProfile().getAvatarUrl() : null,
                m.getContent(),
                m.getMediaUrl(),
                m.isRead(),
                m.getCreatedAt()
        );
    }
}
