package com.zero1blog.backend.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zero1blog.backend.dto.PostRequest;
import com.zero1blog.backend.dto.PostResponse;
import com.zero1blog.backend.model.Post;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.PostRepository;
import com.zero1blog.backend.repository.UserRepository;
import com.zero1blog.backend.repository.CommentRepository;
import com.zero1blog.backend.repository.PostLikeRepository;
import com.zero1blog.backend.repository.UserBlockRepository;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserBlockRepository userBlockRepository;

    public PostService(PostRepository postRepository, UserRepository userRepository,
                       CommentRepository commentRepository, PostLikeRepository postLikeRepository,
                       UserBlockRepository userBlockRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.userBlockRepository = userBlockRepository;
    }

    public PostResponse createPost(PostRequest request, String authorPublicId) {
        User author = userRepository.findByPublicId(authorPublicId)
        .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = new Post();
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setAuthor(author);

        Post saved = postRepository.save(post);
        return toResponse(saved, authorPublicId);
    }

    public List<PostResponse> getAllPosts(String currentUserPublicId) {
        List<Post> allPosts = postRepository.findAllByOrderByCreatedAtDesc();
        
        if (currentUserPublicId == null) {
            return allPosts.stream().map(post -> toResponse(post, null)).collect(Collectors.toList());
        }

        User currentUser = userRepository.findByPublicId(currentUserPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Long> blockedIds = userBlockRepository.findByBlocker(currentUser).stream()
                .map(ub -> ub.getBlocked().getId()).collect(Collectors.toSet());
        Set<Long> blockingIds = userBlockRepository.findByBlocked(currentUser).stream()
                .map(ub -> ub.getBlocker().getId()).collect(Collectors.toSet());

        return allPosts.stream()
                .filter(post -> !blockedIds.contains(post.getAuthor().getId()) && !blockingIds.contains(post.getAuthor().getId()))
                .map(post -> toResponse(post, currentUserPublicId))
                .collect(Collectors.toList());
    }

    public List<PostResponse> getPostsByUsername(String username, String currentUserPublicId) {
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

        return postRepository.findByAuthorIdOrderByCreatedAtDesc(author.getId())
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
                post.getCreatedAt(),
                post.getUpdatedAt(),
                commentCount,
                likeCount,
                isLiked);
    }
}