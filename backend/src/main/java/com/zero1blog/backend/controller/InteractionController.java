package com.zero1blog.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.zero1blog.backend.dto.CommentRequest;
import com.zero1blog.backend.dto.CommentResponse;
import com.zero1blog.backend.service.InteractionService;

@RestController
@RequestMapping("/api")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long postId,
                                                      @RequestBody CommentRequest request,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(interactionService.addComment(postId, request, userDetails.getUsername()));
    }

    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long postId,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(interactionService.getCommentsForPost(postId, userDetails != null ? userDetails.getUsername() : null));
    }

    @PostMapping("/posts/{postId}/likes")
    public ResponseEntity<Void> togglePostLike(@PathVariable Long postId,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        interactionService.togglePostLike(postId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/comments/{commentId}/likes")
    public ResponseEntity<Void> toggleCommentLike(@PathVariable Long commentId,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        interactionService.toggleCommentLike(commentId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }
}