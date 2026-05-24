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

/**
 * Service orchestrating direct message exchange workflows.
 * <p>
 * This component acts as the transactional database bridge for chat messaging. It enforces 
 * complex business rules regarding user bans, mutual blocks, and reads/unread tracking.
 * It also triggers low-latency real-time WebSocket notifications to sync multiple client devices.
 * </p>
 */
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

    /**
     * Executes the direct message delivery transaction flow.
     * <p>
     * Performs strict validation gates under a write-enabled transaction:
     * 1. Confirms the existence of both sender and recipient in the system.
     * 2. Enforces access controls: throws if the sender is banned or if the recipient account is suspended.
     * 3. Resolves bi-directional user blocking relationships. If either party has blocked the other, 
     *    the communication is aborted immediately.
     * 4. Persists the message entity and triggers dual real-time targeted WebSocket transmissions 
     *    (to both recipient and sender) to synchronize chat windows on all active screens.
     * </p>
     *
     * @param senderPublicId the public ID of the message sender.
     * @param request        contains recipient coordinates and textual/media content.
     * @return the structured response payload mapping the saved message.
     */
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

        // Bi-directional block checks ensure a user cannot communicate with someone they blocked, or who blocked them
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
        
        // Notify both parties in real-time to support instantaneous multi-client views updates
        com.zero1blog.backend.config.GlobalWebSocketHandler.sendToUser(recipient.getPublicId(), "NEW_MESSAGE", response);
        com.zero1blog.backend.config.GlobalWebSocketHandler.sendToUser(sender.getPublicId(), "NEW_MESSAGE", response);
        return response;
    }

    /**
     * Retrieves the complete historical messaging conversation thread between two users.
     * Transaction is declared read-only to optimize DB locking profiles and boost lookup performance.
     *
     * @param user1PublicId user initiating the conversation view.
     * @param user2PublicId partner user ID.
     * @return chronologically sorted list of conversation message responses.
     */
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

    /**
     * Compiles the inbox list for a specific user, consolidating the latest message from all conversations.
     * Transaction is flagged read-only for database access optimizations.
     *
     * @param userPublicId user querying their inbox list.
     * @return collection of latest conversation snippets.
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getInbox(String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return messageRepository.findInbox(user.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Marks a singular message entity as read.
     * Enforces strict authorization validation to guarantee that only the actual recipient can update
     * the read state flag.
     *
     * @param messageId         database ID of the target message.
     * @param recipientPublicId authorization parameter verifying recipient identity.
     */
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

    /**
     * Batch-updates all unread messages received from a specific conversation partner to 'read'.
     * Optimizes performance by executing a bulk save query only if active modifications are made.
     *
     * @param currentUserId public ID of the recipient user viewing the chat.
     * @param partnerId     public ID of the sender partner.
     */
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

    /**
     * Calculates the aggregate sum of all unread incoming messages received by a user.
     * Marked read-only to leverage optimized performance under concurrent read loads.
     *
     * @param userPublicId the user querying their unread count.
     * @return the total number of unread direct messages.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return messageRepository.countByRecipientIdAndIsReadFalse(user.getId());
    }

    /**
     * Internal factory method mapping the internal Message model to the safe, structured MessageResponse DTO.
     */
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
