package com.zero1blog.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zero1blog.backend.dto.PostRequest;
import com.zero1blog.backend.dto.PostResponse;
import com.zero1blog.backend.service.PostService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody PostRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(postService.createPost(request, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<PostResponse>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(postService.getAllPosts(userDetails != null ? userDetails.getUsername() : null, page, limit));
    }

    @GetMapping("/following")
    public ResponseEntity<List<PostResponse>> getFollowingFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(postService.getFollowingFeed(userDetails != null ? userDetails.getUsername() : null, page, limit));
    }

    @GetMapping("/user/{username}")
    public ResponseEntity<List<PostResponse>> getPostsByUsername(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(postService.getPostsByUsername(username, userDetails != null ? userDetails.getUsername() : null, page, limit));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<PostResponse> getPostById(@PathVariable String publicId, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(postService.getPostById(publicId, userDetails != null ? userDetails.getUsername() : null));
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable String publicId,
            @Valid @RequestBody PostRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(postService.updatePost(publicId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable String publicId,
            @AuthenticationPrincipal UserDetails userDetails) {
        postService.deletePost(publicId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}