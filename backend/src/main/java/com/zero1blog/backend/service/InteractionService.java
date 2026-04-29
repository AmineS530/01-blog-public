package com.zero1blog.backend.service;

import java.util.List;
import java.util.Set;
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
import com.zero1blog.backend.repository.UserBlockRepository;

@Service
public class InteractionService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final UserBlockRepository userBlockRepository;

    public InteractionService(CommentRepository commentRepository, PostRepository postRepository,
                              UserRepository userRepository, PostLikeRepository postLikeRepository,
                              CommentLikeRepository commentLikeRepository,
                              UserBlockRepository userBlockRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.userBlockRepository = userBlockRepository;
    }

    public CommentResponse addComment(Long postId, CommentRequest request, String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new RuntimeException("User not found"));
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));
        
        if (userBlockRepository.existsByBlockerAndBlocked(post.getAuthor(), user) || 
            userBlockRepository.existsByBlockerAndBlocked(user, post.getAuthor())) {
            throw new RuntimeException("Cannot comment on this post");
        }

        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setAuthor(user);
        comment.setPost(post);

        Comment saved = commentRepository.save(comment);
        return toCommentResponse(saved, userPublicId);
    }

    public List<CommentResponse> getCommentsForPost(Long postId, String userPublicId) {
        List<Comment> allComments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        
        if (userPublicId == null) {
            return allComments.stream().map(c -> toCommentResponse(c, null)).collect(Collectors.toList());
        }

        User currentUser = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Long> blockedIds = userBlockRepository.findByBlocker(currentUser).stream()
                .map(ub -> ub.getBlocked().getId()).collect(Collectors.toSet());
        Set<Long> blockingIds = userBlockRepository.findByBlocked(currentUser).stream()
                .map(ub -> ub.getBlocker().getId()).collect(Collectors.toSet());

        return allComments.stream()
                .filter(c -> !blockedIds.contains(c.getAuthor().getId()) && !blockingIds.contains(c.getAuthor().getId()))
                .map(c -> toCommentResponse(c, userPublicId))
                .collect(Collectors.toList());
    }

    public CommentResponse updateComment(Long commentId, CommentRequest request, String userPublicId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new RuntimeException("Comment not found"));
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new RuntimeException("User not found"));

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized to edit this comment");
        }

        comment.setContent(request.getContent());
        Comment saved = commentRepository.save(comment);
        return toCommentResponse(saved, userPublicId);
    }

    public void deleteComment(Long commentId, String userPublicId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new RuntimeException("Comment not found"));
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new RuntimeException("User not found"));

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized to delete this comment");
        }

        commentRepository.delete(comment);
    }

    public void togglePostLike(Long postId, String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new RuntimeException("User not found"));
        Post post = postRepository.findById(postId).orElseThrow(() -> new RuntimeException("Post not found"));

        if (userBlockRepository.existsByBlockerAndBlocked(post.getAuthor(), user) || 
            userBlockRepository.existsByBlockerAndBlocked(user, post.getAuthor())) {
            throw new RuntimeException("Cannot like this post");
        }

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

        if (userBlockRepository.existsByBlockerAndBlocked(comment.getAuthor(), user) || 
            userBlockRepository.existsByBlockerAndBlocked(user, comment.getAuthor())) {
            throw new RuntimeException("Cannot like this comment");
        }

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
                comment.getMediaUrl(),
                comment.getAuthor().getUsername(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                likeCount,
                isLiked
        );
    }
}