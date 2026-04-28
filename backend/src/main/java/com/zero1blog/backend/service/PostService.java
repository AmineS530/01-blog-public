package com.zero1blog.backend.service;

import java.util.List;
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

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;

    public PostService(PostRepository postRepository, UserRepository userRepository,
                       CommentRepository commentRepository, PostLikeRepository postLikeRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
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
        return postRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(post -> toResponse(post, currentUserPublicId))
                .collect(Collectors.toList());
    }

    public PostResponse getPostById(Long id, String currentUserPublicId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
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
                post.getAuthor().getUsername(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                commentCount,
                likeCount,
                isLiked);
    }
}