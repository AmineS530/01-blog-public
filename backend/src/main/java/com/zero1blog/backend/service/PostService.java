package com.zero1blog.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.zero1blog.backend.config.PostCreatedEvent;
import com.zero1blog.backend.dto.PostRequest;
import com.zero1blog.backend.dto.PostResponse;
import com.zero1blog.backend.exception.BadRequestException;
import com.zero1blog.backend.exception.ResourceNotFoundException;
import com.zero1blog.backend.exception.UnauthorizedActionException;
import com.zero1blog.backend.model.Post;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.CommentRepository;
import com.zero1blog.backend.repository.PostLikeRepository;
import com.zero1blog.backend.repository.PostRepository;
import com.zero1blog.backend.repository.SubscriptionRepository;
import com.zero1blog.backend.repository.UserBlockRepository;
import com.zero1blog.backend.repository.UserRepository;

/**
 * Service managing the life cycle and feed aggregation of blog posts.
 * <p>
 * Handles creation, updates, and secure deletion of blog articles. It compiles
 * global feeds
 * and custom following feeds, enforcing strict mutual block privacy rules to
 * ensure users never see
 * content authored by individuals they blocked, or who have blocked them. It
 * also triggers
 * file deletions on disk to clean up physical media uploads on post removal.
 * </p>
 */
@Service
public class PostService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PostService.class);
    private static final int MAX_PAGE_SIZE = 100; // Fix #10: cap unbounded page sizes

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserBlockRepository userBlockRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher; // Fix #12: decouple transport from service
    private final MediaService mediaService;

    public PostService(PostRepository postRepository, UserRepository userRepository,
            CommentRepository commentRepository, PostLikeRepository postLikeRepository,
            UserBlockRepository userBlockRepository, SubscriptionRepository subscriptionRepository,
            ApplicationEventPublisher eventPublisher, MediaService mediaService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.userBlockRepository = userBlockRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.eventPublisher = eventPublisher;
        this.mediaService = mediaService;
    }

    /** Fix #10: single place to build a Pageable with a capped limit. */
    private Pageable pageOf(int page, int limit) {
        int safeLimit = Math.min(limit, MAX_PAGE_SIZE);
        return PageRequest.of(page, safeLimit, Sort.by("createdAt").descending());
    }

    /**
     * Publishes a new blog post.
     * <p>
     * Saves the post details to the database and issues an instantaneous `NEW_POST`
     * event
     * frame via WebSockets to synchronize active client feeds.
     * </p>
     *
     * @param request        contains post title, text contents, and media links.
     * @param authorPublicId public ID of the publishing author user.
     * @return structured PostResponse mapping the new post.
     */
    public PostResponse createPost(PostRequest request, String authorPublicId) {
        User author = userRepository.findByPublicId(authorPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Post post = new Post();
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setMediaUrl(request.getMediaUrl());
        post.setAuthor(author);

        Post saved = postRepository.save(post);
        PostResponse response = toResponse(saved, author);
        // Fix #12: publish a domain event; the WebSocket handler listens independently
        eventPublisher.publishEvent(new PostCreatedEvent(this, response));
        return response;
    }

    /**
     * Compiles the global feed page of articles.
     * <p>
     * Privacy Filter Logic:
     * If a caller is logged in, this method queries all block constraints. It
     * collects the IDs of users
     * the caller has blocked and users who blocked the caller, combining them into
     * an exclusion set.
     * It then performs an optimized repository query (`findByAuthorIdNotIn`) to
     * compile the feed page,
     * completely excluding restricted articles.
     * </p>
     *
     * @param currentUserPublicId the logged-in viewer's public ID (can be null for
     *                            guests).
     * @param page                page index.
     * @param limit               articles per page.
     * @return list of visible post responses.
     */
    public List<PostResponse> getAllPosts(String currentUserPublicId, int page, int limit) {
        Pageable pageable = pageOf(page, limit); // Fix #10: capped, via helper

        User currentUser = null;
        if (currentUserPublicId != null) {
            currentUser = userRepository.findByPublicId(currentUserPublicId).orElse(null);
        }

        if (currentUser == null) {
            return toResponses(postRepository.findAllWithAuthor(pageable).getContent(), null);
        }

        // Retrieve mutual block IDs (ID-only queries — no N+1)
        final User finalCurrentUser = currentUser;
        Set<Long> blockedIds = new java.util.HashSet<>(userBlockRepository.findBlockedUserIdsByBlockerId(currentUser.getId()));
        Set<Long> blockingIds = new java.util.HashSet<>(userBlockRepository.findBlockerUserIdsByBlockedId(currentUser.getId()));

        Set<Long> excludeAuthorIds = new java.util.HashSet<>();
        excludeAuthorIds.addAll(blockedIds);
        excludeAuthorIds.addAll(blockingIds);

        org.springframework.data.domain.Page<Post> postsPage;
        if (excludeAuthorIds.isEmpty()) {
            postsPage = postRepository.findAllWithAuthor(pageable);
        } else {
            postsPage = postRepository.findByAuthorIdNotInWithAuthor(new java.util.ArrayList<>(excludeAuthorIds), pageable);
        }

        return toResponses(postsPage.getContent(), finalCurrentUser);
    }

    /**
     * Compiles a personalized blog feed containing only articles written by users
     * the caller follows.
     * <p>
     * Flow:
     * 1. Resolves all authors the caller follows using the
     * {@link SubscriptionRepository}.
     * 2. Resolves mutual block lists and filters out any followed authors involved
     * in active blocks.
     * 3. Executes an optimized repository fetch for posts written by the remaining
     * allowed author pool,
     * ordered chronologically.
     * </p>
     *
     * @param currentUserPublicId the logged-in viewer's public ID.
     * @param page                page index.
     * @param limit               articles per page.
     * @return collection of followed post responses.
     */
    public List<PostResponse> getFollowingFeed(String currentUserPublicId, int page, int limit) {
        if (currentUserPublicId == null) {
            return List.of();
        }

        User currentUser = userRepository.findByPublicId(currentUserPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Long> followedIds = subscriptionRepository.findFollowedUserIdsByFollowerId(currentUser.getId());

        if (followedIds.isEmpty()) {
            return List.of();
        }

        Set<Long> blockedIds = new java.util.HashSet<>(userBlockRepository.findBlockedUserIdsByBlockerId(currentUser.getId()));
        Set<Long> blockingIds = new java.util.HashSet<>(userBlockRepository.findBlockerUserIdsByBlockedId(currentUser.getId()));

        // Filter followed user IDs, discarding blocked users
        List<Long> allowedFollowedIds = followedIds.stream()
                .filter(id -> !blockedIds.contains(id) && !blockingIds.contains(id))
                .collect(Collectors.toList());

        if (allowedFollowedIds.isEmpty()) {
            return List.of();
        }

        Pageable pageable = pageOf(page, limit); // Fix #10: capped, via helper

        final User finalCurrentUser = currentUser;
        return toResponses(postRepository.findByAuthorIdInWithAuthor(allowedFollowedIds, pageable).getContent(),
                finalCurrentUser);
    }

    /**
     * Compiles a page of articles authored by a specific user.
     * Enforces bi-directional block assertions. If an active block exists, returns
     * an empty list.
     */
    public List<PostResponse> getPostsByUsername(String username, String currentUserPublicId, int page, int limit) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User currentUser = null;
        if (currentUserPublicId != null) {
            currentUser = userRepository.findByPublicId(currentUserPublicId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            if (userBlockRepository.existsByBlockerAndBlocked(currentUser, author) ||
                    userBlockRepository.existsByBlockerAndBlocked(author, currentUser)) {
                return List.of(); // Empty response ensures block boundaries are secure
            }
        }

        Pageable pageable = pageOf(page, limit); // Fix #10: capped, via helper

        final User finalCurrentUser = currentUser;
        return toResponses(postRepository.findByAuthorIdWithAuthor(author.getId(), pageable).getContent(), finalCurrentUser);
    }

    /**
     * Fetches a specific blog post by its public ID.
     * Enforces mutual block assertions, throwing an exception if block conditions
     * are met.
     */
    public PostResponse getPostById(String publicId, String currentUserPublicId) {
        Post post = postRepository.findByPublicIdWithAuthor(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        User currentUser = null;
        if (currentUserPublicId != null) {
            currentUser = userRepository.findByPublicId(currentUserPublicId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            if (userBlockRepository.existsByBlockerAndBlocked(currentUser, post.getAuthor()) ||
                    userBlockRepository.existsByBlockerAndBlocked(post.getAuthor(), currentUser)) {
                throw new BadRequestException("Post not available");
            }
        }

        return toResponse(post, currentUser);
    }

    /**
     * Updates an existing blog post by its public ID.
     * Enforces strict authorization, throwing if the request sender is not the
     * original post author.
     */
    public PostResponse updatePost(String publicId, PostRequest request, String authorPublicId) {
        Post post = postRepository.findByPublicIdWithAuthor(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        if (!post.getAuthor().getPublicId().equals(authorPublicId)) {
            throw new UnauthorizedActionException("Not authorized to edit this post");
        }

        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setMediaUrl(request.getMediaUrl());

        Post saved = postRepository.save(post);
        return toResponse(saved, post.getAuthor());
    }

    /**
     * Safely deletes a blog post by its public ID and its associated resource assets.
     * <p>
     * Execution Steps:
     * 1. Confirms post existence and verifies that the deletion request originates
     * from the post author.
     * 2. Checks if the post has a local media attachment. If it points to local
     * physical storage
     * (e.g., "/api/media/files/"), it parses the filename, maps to the "uploads"
     * directory, and deletes
     * the physical file from disk to prevent storage leaks.
     * 3. Deletes the database post record.
     * </p>
     *
     * @param publicId       public ID of the target blog post to delete.
     * @param authorPublicId public ID of the post author requesting deletion.
     */
    public void deletePost(String publicId, String requesterPublicId) {
        Post post = postRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

        User requester = userRepository.findByPublicId(requesterPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isAuthor = post.getAuthor().getPublicId().equals(requesterPublicId);
        boolean isAdmin = requester.getRole() == User.Role.ADMIN || requester.getRole() == User.Role.SUPER_ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new UnauthorizedActionException("Not authorized to delete this post");
        }

        // Physical media storage cleanup
        if (post.getMediaUrl() != null) {
            mediaService.cleanupMedia(post.getMediaUrl());
        }

        postRepository.delete(post);
    }

    /**
     * Maps an internal Post model to its structural response DTO.
     * Fetches associated comments count, likes count, and resolving the
     * authenticated user's like state.
     */
    private PostResponse toResponse(Post post, User currentUser) {
        long commentCount = commentRepository.countByPostId(post.getId());
        long likeCount = postLikeRepository.countByPostId(post.getId());
        boolean isLiked = false;
        if (currentUser != null) {
            isLiked = postLikeRepository.existsByPostIdAndUserId(post.getId(), currentUser.getId());
        }

        return new PostResponse(
                post.getPublicId(),
                post.getTitle(),
                post.getContent(),
                post.getMediaUrl(),
                post.getAuthor().getUsername(),
                post.getAuthor().getDisplayName(),
                post.getAuthor().getProfile() != null ? post.getAuthor().getProfile().getAvatarUrl() : null,
                post.getAuthor().getPublicId(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                commentCount,
                likeCount,
                isLiked);
    }

    private List<PostResponse> toResponses(List<Post> posts, User currentUser) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getId).collect(Collectors.toList());

        // Batch fetch comment counts
        Map<Long, Long> commentCounts = commentRepository.countByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(
                        arr -> (Long) arr[0],
                        arr -> (Long) arr[1],
                        (v1, v2) -> v1));

        // Batch fetch like counts
        Map<Long, Long> likeCounts = postLikeRepository.countByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(
                        arr -> (Long) arr[0],
                        arr -> (Long) arr[1],
                        (v1, v2) -> v1));

        // Batch fetch if current user liked
        Set<Long> likedPostIds = new java.util.HashSet<>();
        if (currentUser != null) {
            likedPostIds.addAll(postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(currentUser.getId(), postIds));
        }

        return posts.stream().map(post -> {
            long commentCount = commentCounts.getOrDefault(post.getId(), 0L);
            long likeCount = likeCounts.getOrDefault(post.getId(), 0L);
            boolean isLiked = likedPostIds.contains(post.getId());

            return new PostResponse(
                    post.getPublicId(),
                    post.getTitle(),
                    post.getContent(),
                    post.getMediaUrl(),
                    post.getAuthor().getUsername(),
                    post.getAuthor().getDisplayName(),
                    post.getAuthor().getProfile() != null ? post.getAuthor().getProfile().getAvatarUrl() : null,
                    post.getAuthor().getPublicId(),
                    post.getCreatedAt(),
                    post.getUpdatedAt(),
                    commentCount,
                    likeCount,
                    isLiked);
        }).collect(Collectors.toList());
    }
}
