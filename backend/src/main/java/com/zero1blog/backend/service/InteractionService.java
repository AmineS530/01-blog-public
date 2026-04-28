package com.zero1blog.backend.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zero1blog.backend.dto.CommentRequest;
import com.zero1blog.backend.dto.CommentResponse;
import com.zero1blog.backend.model.Comment;
import com.zero1blog.backend.model.CommentLike;
import com.zero1blog.backend.model.Post;
import com.zero1blog.backend.model.PostLike;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.CommentLikeRepository;
import com.zero1blog.backend.repository.CommentRepository;
import com.zero1blog.backend.repository.PostLikeRepository;
import com.zero1blog.backend.repository.PostRepository;
import com.zero1blog.backend.repository.UserRepository;

@Service
public class InteractionService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;

    public InteractionService(CommentRepository commentRepository, PostRepository postRepository,
                              UserRepository userRepository, PostLikeRepository postLikeRepository,
                              CommentLikeRepository commentLikeRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentLikeRepository = commentLikeRepository;
    }

    public CommentResponse addComment(Long postId, CommentRequest request, String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new RuntimeException("User not found"));
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setAuthor(user);
        comment.setPost(post);

        Comment saved = commentRepository.save(comment);
        return toCommentResponse(saved, userPublicId);
    }

    public List<CommentResponse> getCommentsForPost(Long postId, String userPublicId) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(c -> toCommentResponse(c, userPublicId))
                .collect(Collectors.toList());
    }

    public void togglePostLike(Long postId, String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new RuntimeException("User not found"));
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        postLikeRepository.findByPostIdAndUserId(post.getId(), user.getId()).ifPresentOrElse(
            postLikeRepository::delete,
            () -> {
                PostLike like = new PostLike();
                like.setPost(post);
                like.setUser(user);
                postLikeRepository.save(like);
            }
        );
    }

    public void toggleCommentLike(Long commentId, String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new RuntimeException("User not found"));
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new RuntimeException("Comment not found"));

        commentLikeRepository.findByCommentIdAndUserId(comment.getId(), user.getId()).ifPresentOrElse(
            commentLikeRepository::delete,
            () -> {
                CommentLike like = new CommentLike();
                like.setComment(comment);
                like.setUser(user);
                commentLikeRepository.save(like);
            }
        );
    }

    private CommentResponse toCommentResponse(Comment comment, String userPublicId) {
        long likeCount = commentLikeRepository.countByCommentId(comment.getId());
        boolean isLiked = false;
        if (userPublicId != null) {
            User user = userRepository.findByPublicId(userPublicId).orElse(null);
            if (user != null) {
                isLiked = commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), user.getId());
            }
        }
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getUsername(),
                comment.getCreatedAt(),
                likeCount,
                isLiked
        );
    }
}