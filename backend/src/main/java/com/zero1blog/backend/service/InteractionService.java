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
import com.zero1blog.backend.exception.*;

/**
 * Service facilitating user engagement activities such as commenting and liking.
 * <p>
 * This component acts as the backend engine for community interactions. It manages post/comment
 * validations, ensures that commenters/likers are not blocked by the content author (and vice-versa),
 * dispatches notifications, and triggers system-wide real-time WebSocket broadcasts on likes and comments.
 * </p>
 */
@Service
public class InteractionService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final UserBlockRepository userBlockRepository;
    private final NotificationService notificationService;

    public InteractionService(CommentRepository commentRepository, PostRepository postRepository,
                               UserRepository userRepository, PostLikeRepository postLikeRepository,
                               CommentLikeRepository commentLikeRepository,
                               UserBlockRepository userBlockRepository,
                               NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.userBlockRepository = userBlockRepository;
        this.notificationService = notificationService;
    }

    /**
     * Publishes a new comment under a blog post.
     * <p>
     * Validation checks:
     * 1. Confirms the commenter and the target post exist in the system.
     * 2. Asserts that no active block boundary exists between the commenter and the post author.
     * Actions:
     * 1. Saves the new comment in the database.
     * 2. Dispatches an automated push notification to the post author.
     * 3. Broadcasts a `NEW_COMMENT` real-time event frame to sync all open client web browsers.
     * </p>
     *
     * @param postId       the ID of the target blog post.
     * @param request      the content of the comment.
     * @param userPublicId the public ID of the authoring commenter.
     * @return structured CommentResponse mapping the published comment.
     */
    public CommentResponse addComment(Long postId, CommentRequest request, String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        
        if (userBlockRepository.existsByBlockerAndBlocked(post.getAuthor(), user) || 
            userBlockRepository.existsByBlockerAndBlocked(user, post.getAuthor())) {
            throw new BadRequestException("Cannot comment on this post");
        }

        Comment comment = new Comment();
        comment.setContent(request.getContent());
        comment.setMediaUrl(request.getMediaUrl());
        comment.setAuthor(user);
        comment.setPost(post);

        Comment saved = commentRepository.save(comment);

        notificationService.createNotification(
            "COMMENT",
            user.getUsername() + " commented on your post: " + post.getTitle(),
            post.getAuthor(),
            user,
            post
        );

        CommentResponse response = toCommentResponse(saved, userPublicId);
        return response;
    }

    /**
     * Compiles a list of comments published under a post.
     * <p>
     * Implements strict mutual-blocking boundaries:
     * If a caller is authenticated, the returned list is filtered to completely exclude comments
     * written by users who blocked the caller, or whom the caller has blocked, maintaining clean
     * privacy barriers.
     * </p>
     *
     * @param postId       the target blog post ID.
     * @param userPublicId the public ID of the requesting user (can be null for anonymous guests).
     * @return a list of active comment response structures.
     */
    public List<CommentResponse> getCommentsForPost(Long postId, String userPublicId) {
        List<Comment> allComments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        
        if (userPublicId == null) {
            return toCommentResponses(allComments, null);
        }

        User currentUser = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Compile block sets for the current user to enforce privacy boundaries
        Set<Long> blockedIds = userBlockRepository.findByBlocker(currentUser).stream()
                .map(ub -> ub.getBlocked().getId()).collect(Collectors.toSet());
        Set<Long> blockingIds = userBlockRepository.findByBlocked(currentUser).stream()
                .map(ub -> ub.getBlocker().getId()).collect(Collectors.toSet());

        List<Comment> filtered = allComments.stream()
                .filter(c -> !blockedIds.contains(c.getAuthor().getId()) && !blockingIds.contains(c.getAuthor().getId()))
                .collect(Collectors.toList());

        return toCommentResponses(filtered, currentUser);
    }

    /**
     * Updates comment contents.
     * Enforces strict authorization ensuring that only the original author can edit the comment.
     */
    public CommentResponse updateComment(Long commentId, CommentRequest request, String userPublicId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("Not authorized to edit this comment");
        }

        comment.setContent(request.getContent());
        Comment saved = commentRepository.save(comment);
        return toCommentResponse(saved, userPublicId);
    }

    /**
     * Deletes comment.
     * Enforces strict authorization ensuring that only the original author can delete the comment.
     */
    public void deleteComment(Long commentId, String userPublicId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new UnauthorizedActionException("Not authorized to delete this comment");
        }

        commentRepository.delete(comment);
    }

    /**
     * Toggles a user's 'Like' status on a blog post.
     * <p>
     * Performs mutual block checks before allowing a like. If a like is added, an automated push
     * notification is triggered for the post author.
     * In either case, the updated aggregate post likes count is broadcasted to all active browsers.
     * </p>
     *
     * @param postId       the target blog post ID.
     * @param userPublicId the public ID of the toggling user.
     */
    public void togglePostLike(Long postId, String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (userBlockRepository.existsByBlockerAndBlocked(post.getAuthor(), user) || 
            userBlockRepository.existsByBlockerAndBlocked(user, post.getAuthor())) {
            throw new BadRequestException("Cannot like this post");
        }

        postLikeRepository.findByPostIdAndUserId(post.getId(), user.getId()).ifPresentOrElse(
            postLikeRepository::delete,
            () -> {
                PostLike like = new PostLike();
                like.setPost(post);
                like.setUser(user);
                postLikeRepository.save(like);

                notificationService.createNotification(
                    "LIKE",
                    user.getUsername() + " liked your post: " + post.getTitle(),
                    post.getAuthor(),
                    user,
                    post
                );
            }
        );

        long likeCount = postLikeRepository.countByPostId(post.getId());
        com.zero1blog.backend.config.GlobalWebSocketHandler.broadcast("POST_LIKE", java.util.Map.of("postId", postId, "likeCount", likeCount));
    }

    /**
     * Toggles a user's 'Like' status on a specific comment.
     * <p>
     * Performs mutual block checks before allowing a like. Updates the database and broadcasts the
     * comment's new like count via WebSockets.
     * </p>
     *
     * @param commentId    the target comment ID.
     * @param userPublicId the public ID of the toggling user.
     */
    public void toggleCommentLike(Long commentId, String userPublicId) {
        User user = userRepository.findByPublicId(userPublicId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

        if (userBlockRepository.existsByBlockerAndBlocked(comment.getAuthor(), user) || 
            userBlockRepository.existsByBlockerAndBlocked(user, comment.getAuthor())) {
            throw new BadRequestException("Cannot like this comment");
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

        long likeCount = commentLikeRepository.countByCommentId(comment.getId());
        com.zero1blog.backend.config.GlobalWebSocketHandler.broadcast("COMMENT_LIKE", java.util.Map.of("commentId", commentId, "postId", comment.getPost().getId(), "likeCount", likeCount));
    }

    /**
     * Maps an internal Comment model to its clean structural DTO.
     * Determines whether the current authenticated user has liked this comment.
     */
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
                comment.getAuthor().getDisplayName(),
                comment.getAuthor().getProfile() != null ? comment.getAuthor().getProfile().getAvatarUrl() : null,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                likeCount,
                isLiked
        );
    }

    private List<CommentResponse> toCommentResponses(List<Comment> comments, User currentUser) {
        if (comments.isEmpty()) {
            return List.of();
        }
        List<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toList());

        // Batch fetch comment like counts
        java.util.Map<Long, Long> likeCounts = commentLikeRepository.countByCommentIdIn(commentIds).stream()
                .collect(Collectors.toMap(
                        arr -> (Long) arr[0],
                        arr -> (Long) arr[1],
                        (v1, v2) -> v1
                ));

        // Batch fetch if current user liked
        Set<Long> likedCommentIds = new java.util.HashSet<>();
        if (currentUser != null) {
            likedCommentIds.addAll(commentLikeRepository.findLikedCommentIdsByUserIdAndCommentIdIn(currentUser.getId(), commentIds));
        }

        return comments.stream().map(comment -> {
            long likeCount = likeCounts.getOrDefault(comment.getId(), 0L);
            boolean isLiked = likedCommentIds.contains(comment.getId());

            return new CommentResponse(
                    comment.getId(),
                    comment.getContent(),
                    comment.getMediaUrl(),
                    comment.getAuthor().getUsername(),
                    comment.getAuthor().getDisplayName(),
                    comment.getAuthor().getProfile() != null ? comment.getAuthor().getProfile().getAvatarUrl() : null,
                    comment.getCreatedAt(),
                    comment.getUpdatedAt(),
                    likeCount,
                    isLiked
            );
        }).collect(Collectors.toList());
    }
}