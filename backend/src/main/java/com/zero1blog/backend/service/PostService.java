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
import com.zero1blog.backend.model.Subscription;
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
 * Handles creation, updates, and secure deletion of blog articles. It compiles global feeds
 * and custom following feeds, enforcing strict mutual block privacy rules to ensure users never see 
 * content authored by individuals they blocked, or who have blocked them. It also triggers
 * file deletions on disk to clean up physical media uploads on post removal.
 * </p>
 */
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

    /**
     * Publishes a new blog post.
     * <p>
     * Saves the post details to the database and issues an instantaneous `NEW_POST` event 
     * frame via WebSockets to synchronize active client feeds.
     * </p>
     *
     * @param request        contains post title, text contents, and media links.
     * @param authorPublicId public ID of the publishing author user.
     * @return structured PostResponse mapping the new post.
     */
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

    /**
     * Compiles the global feed page of articles.
     * <p>
     * Privacy Filter Logic:
     * If a caller is logged in, this method queries all block constraints. It collects the IDs of users 
     * the caller has blocked and users who blocked the caller, combining them into an exclusion set.
     * It then performs an optimized repository query (`findByAuthorIdNotIn`) to compile the feed page,
     * completely excluding restricted articles.
     * </p>
     *
     * @param currentUserPublicId the logged-in viewer's public ID (can be null for guests).
     * @param page                 page index.
     * @param limit                articles per page.
     * @return list of visible post responses.
     */
    public List<PostResponse> getAllPosts(String currentUserPublicId, int page, int limit) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, limit, org.springframework.data.domain.Sort.by("createdAt").descending());
        
        if (currentUserPublicId == null) {
            return postRepository.findAll(pageable).stream().map(post -> toResponse(post, null)).collect(Collectors.toList());
        }

        User currentUser = userRepository.findByPublicId(currentUserPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Retrieve mutual block IDs
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

    /**
     * Compiles a personalized blog feed containing only articles written by users the caller follows.
     * <p>
     * Flow:
     * 1. Resolves all authors the caller follows using the {@link SubscriptionRepository}.
     * 2. Resolves mutual block lists and filters out any followed authors involved in active blocks.
     * 3. Executes an optimized repository fetch for posts written by the remaining allowed author pool,
     *    ordered chronologically.
     * </p>
     *
     * @param currentUserPublicId the logged-in viewer's public ID.
     * @param page                 page index.
     * @param limit                articles per page.
     * @return collection of followed post responses.
     */
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

        // Filter followed user IDs, discarding blocked users
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

    /**
     * Compiles a page of articles authored by a specific user.
     * Enforces bi-directional block assertions. If an active block exists, returns an empty list.
     */
    public List<PostResponse> getPostsByUsername(String username, String currentUserPublicId, int page, int limit) {
        User author = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (currentUserPublicId != null) {
            User currentUser = userRepository.findByPublicId(currentUserPublicId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            if (userBlockRepository.existsByBlockerAndBlocked(currentUser, author) || 
                userBlockRepository.existsByBlockerAndBlocked(author, currentUser)) {
                return List.of(); // Empty response ensures block boundaries are secure
            }
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, limit, org.springframework.data.domain.Sort.by("createdAt").descending());

        return postRepository.findByAuthorId(author.getId(), pageable)
                .stream()
                .map(post -> toResponse(post, currentUserPublicId))
                .collect(Collectors.toList());
    }

    /**
     * Fetches a specific blog post by its database ID.
     * Enforces mutual block assertions, throwing an exception if block conditions are met.
     */
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

    /**
     * Updates an existing blog post.
     * Enforces strict authorization, throwing if the request sender is not the original post author.
     */
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

    /**
     * Safely deletes a blog post and its associated resource assets.
     * <p>
     * Execution Steps:
     * 1. Confirms post existence and verifies that the deletion request originates from the post author.
     * 2. Checks if the post has a local media attachment. If it points to local physical storage
     *    (e.g., "/api/media/files/"), it parses the filename, maps to the "uploads" directory, and deletes 
     *    the physical file from disk to prevent storage leaks.
     * 3. Deletes the database post record.
     * </p>
     *
     * @param id             ID of the target blog post to delete.
     * @param authorPublicId public ID of the post author requesting deletion.
     */
    public void deletePost(Long id, String requesterPublicId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        User requester = userRepository.findByPublicId(requesterPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isAuthor = post.getAuthor().getPublicId().equals(requesterPublicId);
        boolean isAdmin = requester.getRole() == User.Role.ADMIN || requester.getRole() == User.Role.SUPER_ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new RuntimeException("Not authorized to delete this post");
        }

        // Physical media storage cleanup
        if (post.getMediaUrl() != null && post.getMediaUrl().startsWith("/api/media/files/")) {
            try {
                String fileName = post.getMediaUrl().substring("/api/media/files/".length());
                Path filePath = Paths.get("uploads").resolve(fileName);
                Files.deleteIfExists(filePath);
            } catch (Exception e) {
                // Log exception internally and proceed so database delete is not blocked on file system glitches
            }
        }

        postRepository.delete(post);
    }

    /**
     * Maps an internal Post model to its structural response DTO.
     * Fetches associated comments count, likes count, and resolving the authenticated user's like state.
     */
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
                post.getAuthor().getDisplayName(),
                post.getAuthor().getProfile() != null ? post.getAuthor().getProfile().getAvatarUrl() : null,
                post.getCreatedAt(),
                post.getUpdatedAt(),
                commentCount,
                likeCount,
                isLiked);
    }
}
