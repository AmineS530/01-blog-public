package com.zero1blog.backend.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zero1blog.backend.dto.PostRequest;
import com.zero1blog.backend.dto.PostResponse;
import com.zero1blog.backend.model.Post;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.CommentRepository;
import com.zero1blog.backend.repository.PostLikeRepository;
import com.zero1blog.backend.repository.PostRepository;
import com.zero1blog.backend.model.Subscription;
import com.zero1blog.backend.repository.SubscriptionRepository;
import com.zero1blog.backend.repository.UserBlockRepository;
import com.zero1blog.backend.repository.UserRepository;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserBlockRepository userBlockRepository;
    private final SubscriptionRepository subscriptionRepository;

    public PostService(PostRepository postRepository, UserRepository userRepository,
                       CommentRepository commentRepository, PostLikeRepository postLikeRepository,
                       UserBlockRepository userBlockRepository, SubscriptionRepository subscriptionRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.userBlockRepository = userBlockRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    public PostResponse createPost(PostRequest request, String authorPublicId) {
        User author = userRepository.findByPublicId(authorPublicId)
        .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = new Post();
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setMediaUrl(request.getMediaUrl());
        post.setAuthor(author);

        Post saved = postRepository.save(post);
        PostResponse response = toResponse(saved, authorPublicId);
        com.zero1blog.backend.config.GlobalWebSocketHandler.broadcast("NEW_POST", response);
        return response;
    }

    public List<PostResponse> getAllPosts(String currentUserPublicId, int page, int limit) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, limit, org.springframework.data.domain.Sort.by("createdAt").descending());
        
        if (currentUserPublicId == null) {
            return postRepository.findAll(pageable).stream().map(post -> toResponse(post, null)).collect(Collectors.toList());
        }

        User currentUser = userRepository.findByPublicId(currentUserPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Long> blockedIds = userBlockRepository.findByBlocker(currentUser).stream()
                .map(ub -> ub.getBlocked().getId()).collect(Collectors.toSet());
        Set<Long> blockingIds = userBlockRepository.findByBlocked(currentUser).stream()
                .map(ub -> ub.getBlocker().getId()).collect(Collectors.toSet());

        Set<Long> excludeAuthorIds = new java.util.HashSet<>();
        excludeAuthorIds.addAll(blockedIds);
        excludeAuthorIds.addAll(blockingIds);

        org.springframework.data.domain.Page<Post> postsPage;
        if (excludeAuthorIds.isEmpty()) {
            postsPage = postRepository.findAll(pageable);
        } else {
            postsPage = postRepository.findByAuthorIdNotIn(new java.util.ArrayList<>(excludeAuthorIds), pageable);
        }

        return postsPage.stream()
                .map(post -> toResponse(post, currentUserPublicId))
                .collect(Collectors.toList());
    }

    public List<PostResponse> getFollowingFeed(String currentUserPublicId, int page, int limit) {
        if (currentUserPublicId == null) {
            return List.of();
        }

        User currentUser = userRepository.findByPublicId(currentUserPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<User> followedUsers = subscriptionRepository.findByFollower(currentUser).stream()
                .map(Subscription::getFollowed)
                .collect(Collectors.toList());

        if (followedUsers.isEmpty()) {
            return List.of();
        }

        List<Long> followedIds = followedUsers.stream().map(User::getId).collect(Collectors.toList());

        Set<Long> blockedIds = userBlockRepository.findByBlocker(currentUser).stream()
                .map(ub -> ub.getBlocked().getId()).collect(Collectors.toSet());
        Set<Long> blockingIds = userBlockRepository.findByBlocked(currentUser).stream()
                .map(ub -> ub.getBlocker().getId()).collect(Collectors.toSet());

        List<Long> allowedFollowedIds = followedIds.stream()
                .filter(id -> !blockedIds.contains(id) && !blockingIds.contains(id))
                .collect(Collectors.toList());

        if (allowedFollowedIds.isEmpty()) {
            return List.of();
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, limit, org.springframework.data.domain.Sort.by("createdAt").descending());

        return postRepository.findByAuthorIdIn(allowedFollowedIds, pageable).stream()
                .map(post -> toResponse(post, currentUserPublicId))
                .collect(Collectors.toList());
    }

    public List<PostResponse> getPostsByUsername(String username, String currentUserPublicId, int page, int limit) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (currentUserPublicId != null) {
            User currentUser = userRepository.findByPublicId(currentUserPublicId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (userBlockRepository.existsByBlockerAndBlocked(currentUser, author) || 
                userBlockRepository.existsByBlockerAndBlocked(author, currentUser)) {
                return List.of(); // Return empty if there is a block
            }
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, limit, org.springframework.data.domain.Sort.by("createdAt").descending());

        return postRepository.findByAuthorId(author.getId(), pageable)
                .stream()
                .map(post -> toResponse(post, currentUserPublicId))
                .collect(Collectors.toList());
    }

    public PostResponse getPostById(Long id, String currentUserPublicId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (currentUserPublicId != null) {
            User currentUser = userRepository.findByPublicId(currentUserPublicId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (userBlockRepository.existsByBlockerAndBlocked(currentUser, post.getAuthor()) || 
                userBlockRepository.existsByBlockerAndBlocked(post.getAuthor(), currentUser)) {
                throw new RuntimeException("Post not available");
            }
        }

        return toResponse(post, currentUserPublicId);
    }

    public PostResponse updatePost(Long id, PostRequest request, String authorPublicId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getAuthor().getPublicId().equals(authorPublicId)) {
            throw new RuntimeException("Not authorized to edit this post");
        }

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setMediaUrl(request.getMediaUrl());

        Post saved = postRepository.save(post);
        return toResponse(saved, authorPublicId);
    }

    public void deletePost(Long id, String authorPublicId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getAuthor().getPublicId().equals(authorPublicId)) {
            throw new RuntimeException("Not authorized to delete this post");
        }

        // BUG FIX: Delete physical media file if it exists
        if (post.getMediaUrl() != null && post.getMediaUrl().startsWith("/api/media/files/")) {
            try {
                String fileName = post.getMediaUrl().substring("/api/media/files/".length());
                Path filePath = Paths.get("uploads").resolve(fileName);
                Files.deleteIfExists(filePath);
            } catch (Exception e) {
                // Log and continue - don't block DB deletion if file delete fails
            }
        }

        postRepository.delete(post);
    }

    private PostResponse toResponse(Post post, String currentUserPublicId) {
        long commentCount = commentRepository.countByPostId(post.getId());
        long likeCount = postLikeRepository.countByPostId(post.getId());
        boolean isLiked = false;
        if (currentUserPublicId != null) {
            User user = userRepository.findByPublicId(currentUserPublicId).orElse(null);
            if (user != null) {
                isLiked = postLikeRepository.existsByPostIdAndUserId(post.getId(), user.getId());
            }
        }

        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMediaUrl(),
                post.getAuthor().getUsername(),
                post.getAuthor().getProfile() != null ? post.getAuthor().getProfile().getAvatarUrl() : null,
                post.getCreatedAt(),
                post.getUpdatedAt(),
                commentCount,
                likeCount,
                isLiked);
    }
}
