package com.zero1blog.backend.controller;

import com.zero1blog.backend.dto.MessageRequest;
import com.zero1blog.backend.dto.MessageResponse;
import com.zero1blog.backend.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.zero1blog.backend.config.ChatWebSocketHandler;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(@Valid @RequestBody MessageRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(messageService.sendMessage(authentication.getName(), request));
    }

    @GetMapping("/inbox")
    public ResponseEntity<List<MessageResponse>> getInbox(Authentication authentication) {
        return ResponseEntity.ok(messageService.getInbox(authentication.getName()));
    }

    @GetMapping("/thread/{partnerPublicId}")
    public ResponseEntity<List<MessageResponse>> getConversation(@PathVariable String partnerPublicId,
                                                                 Authentication authentication) {
        return ResponseEntity.ok(messageService.getConversation(authentication.getName(), partnerPublicId));
    }

    @PostMapping("/read-all/{partnerPublicId}")
    public ResponseEntity<Void> markConversationAsRead(@PathVariable String partnerPublicId,
                                                       Authentication authentication) {
        messageService.markConversationAsRead(authentication.getName(), partnerPublicId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", messageService.getUnreadCount(authentication.getName())));
    }

    @GetMapping("/online")
    public ResponseEntity<List<String>> getOnlineUsers() {
        return ResponseEntity.ok(List.copyOf(ChatWebSocketHandler.getOnlineUsers()));
    }
}
