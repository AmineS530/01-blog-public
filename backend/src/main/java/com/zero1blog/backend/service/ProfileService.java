package com.zero1blog.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.zero1blog.backend.dto.ProfileResponse;
import com.zero1blog.backend.dto.ProfileUpdateRequest;
import com.zero1blog.backend.exception.BadRequestException;
import com.zero1blog.backend.exception.ResourceNotFoundException;
import com.zero1blog.backend.exception.UnauthorizedActionException;
import com.zero1blog.backend.model.Subscription;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.model.UserBlock;
import com.zero1blog.backend.model.UserProfile;
import com.zero1blog.backend.repository.SubscriptionRepository;
import com.zero1blog.backend.repository.UserBlockRepository;
import com.zero1blog.backend.repository.UserProfileRepository;
import com.zero1blog.backend.repository.UserRepository;

@Service
public class ProfileService {

        private final UserRepository userRepository;
        private final UserProfileRepository userProfileRepository;
        private final SubscriptionRepository subscriptionRepository;
        private final UserBlockRepository userBlockRepository;
        private final NotificationService notificationService;

        public ProfileService(UserRepository userRepository, UserProfileRepository userProfileRepository,
                        SubscriptionRepository subscriptionRepository, UserBlockRepository userBlockRepository,
                        NotificationService notificationService) {
                this.userRepository = userRepository;
                this.userProfileRepository = userProfileRepository;
                this.subscriptionRepository = subscriptionRepository;
                this.userBlockRepository = userBlockRepository;
                this.notificationService = notificationService;
        }

        public ProfileResponse getMyProfile(String publicId) {
                User user = userRepository.findByPublicId(publicId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                return getProfile(user.getUsername(), publicId);
        }

        public ProfileResponse getProfile(String targetUsername, String currentUserPublicId) {
                User targetUser = userRepository.findByUsername(targetUsername)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                UserProfile profile = userProfileRepository.findByUser(targetUser).orElse(new UserProfile());

                long followerCount = subscriptionRepository.countByFollowed(targetUser);
                long followingCount = subscriptionRepository.countByFollower(targetUser);

                boolean isFollowing = false;
                boolean isBlocked = false;
                boolean isBlockingMe = false;

                if (currentUserPublicId != null) {
                        User currentUser = userRepository.findByPublicId(currentUserPublicId).orElse(null);
                        if (currentUser != null) {
                                isFollowing = subscriptionRepository.existsByFollowerAndFollowed(currentUser,
                                                targetUser);
                                isBlocked = userBlockRepository.existsByBlockerAndBlocked(currentUser, targetUser);
                                isBlockingMe = userBlockRepository.existsByBlockerAndBlocked(targetUser, currentUser);
                        }
                }

                return ProfileResponse.builder()
                                .publicId(targetUser.getPublicId())
                                .username(targetUser.getUsername())
                                .displayName(profile.getDisplayName())
                                .bio(profile.getBio())
                                .avatarUrl(profile.getAvatarUrl())
                                .followerCount(followerCount)
                                .followingCount(followingCount)
                                .isFollowing(isFollowing)
                                .isBlocked(isBlocked)
                                .isBlockingMe(isBlockingMe)
                                .build();
        }

        public ProfileResponse updateProfile(String currentUserPublicId, ProfileUpdateRequest request) {
                User currentUser = userRepository.findByPublicId(currentUserPublicId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                UserProfile profile = userProfileRepository.findByUser(currentUser)
                                .orElse(UserProfile.builder().user(currentUser).build());

                profile.setDisplayName(request.getDisplayName());
                profile.setBio(request.getBio());
                profile.setAvatarUrl(request.getAvatarUrl());

                userProfileRepository.save(profile);

                return getProfile(currentUser.getUsername(), currentUserPublicId);
        }

        public ProfileResponse updateProfileByUsername(String targetUsername, ProfileUpdateRequest request,
                        String callerPublicId) {
                User caller = userRepository.findByPublicId(callerPublicId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                User targetUser = userRepository.findByUsername(targetUsername)
                                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

                boolean isSelf = targetUser.getPublicId().equals(callerPublicId);
                boolean isAdmin = caller.getRole() == User.Role.ADMIN || caller.getRole() == User.Role.SUPER_ADMIN;

                if (!isSelf && !isAdmin) {
                        throw new UnauthorizedActionException("Not authorized to update this profile");
                }

                UserProfile profile = userProfileRepository.findByUser(targetUser)
                                .orElse(UserProfile.builder().user(targetUser).build());

                profile.setDisplayName(request.getDisplayName());
                profile.setBio(request.getBio());
                profile.setAvatarUrl(request.getAvatarUrl());

                userProfileRepository.save(profile);

                return getProfile(targetUser.getUsername(), callerPublicId);
        }

        public void toggleFollow(String targetUsername, String currentUserPublicId) {
                User currentUser = userRepository.findByPublicId(currentUserPublicId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                User targetUser = userRepository.findByUsername(targetUsername)
                                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

                if (currentUser.getId().equals(targetUser.getId())) {
                        throw new BadRequestException("Cannot follow yourself");
                }

                if (userBlockRepository.existsByBlockerAndBlocked(targetUser, currentUser)) {
                        throw new BadRequestException("You are blocked by this user");
                }

                Optional<Subscription> existing = subscriptionRepository.findByFollowerAndFollowed(currentUser,
                                targetUser);
                if (existing.isPresent()) {
                        subscriptionRepository.delete(existing.get());
                } else {
                        Subscription sub = Subscription.builder()
                                        .follower(currentUser)
                                        .followed(targetUser)
                                        .build();
                        subscriptionRepository.save(sub);

                        notificationService.createNotification(
                                        "FOLLOW",
                                        currentUser.getUsername() + " started following you",
                                        targetUser,
                                        currentUser,
                                        null);
                }
        }

        public void toggleBlock(String targetUsername, String currentUserPublicId) {
                User currentUser = userRepository.findByPublicId(currentUserPublicId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                User targetUser = userRepository.findByUsername(targetUsername)
                                .orElseThrow(() -> new ResourceNotFoundException("Target user not found"));

                if (currentUser.getId().equals(targetUser.getId())) {
                        throw new BadRequestException("Cannot block yourself");
                }

                Optional<UserBlock> existing = userBlockRepository.findByBlockerAndBlocked(currentUser, targetUser);
                if (existing.isPresent()) {
                        userBlockRepository.delete(existing.get());
                } else {
                        UserBlock block = UserBlock.builder()
                                        .blocker(currentUser)
                                        .blocked(targetUser)
                                        .build();
                        userBlockRepository.save(block);

                        // Remove any follow relationships between them
                        subscriptionRepository.findByFollowerAndFollowed(currentUser, targetUser)
                                        .ifPresent(subscriptionRepository::delete);
                        subscriptionRepository.findByFollowerAndFollowed(targetUser, currentUser)
                                        .ifPresent(subscriptionRepository::delete);
                }
        }

        public List<ProfileResponse> getRecommendedProfiles(String currentUserPublicId) {
                User currentUser = userRepository.findByPublicId(currentUserPublicId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                org.springframework.data.domain.Page<User> recommendedUsers = userRepository.findRecommendedUsers(
                                currentUser.getId(),
                                org.springframework.data.domain.PageRequest.of(0, 5));

                return getProfiles(recommendedUsers.getContent(), currentUser);
        }

        public List<ProfileResponse> getFollowers(String username, String currentUserPublicId, int page, int size) {
                User targetUser = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                User currentUser = null;
                if (currentUserPublicId != null) {
                        currentUser = userRepository.findByPublicId(currentUserPublicId).orElse(null);
                }

                int safeSize = Math.min(Math.max(size, 1), 100);
                org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                                safeSize);
                List<Long> followerIds = subscriptionRepository
                                .findFollowerUserIdsByFollowedIdPaged(targetUser.getId(), pageable);
                // Per-user block status (isBlocked / isBlockingMe) is computed below in
                // getProfiles — listing your followers is unconditional.
                List<User> followers = userRepository.findAllById(followerIds);
                return getProfiles(followers, currentUser);
        }

        public List<ProfileResponse> getFollowing(String username, String currentUserPublicId, int page, int size) {
                User targetUser = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                User currentUser = null;
                if (currentUserPublicId != null) {
                        currentUser = userRepository.findByPublicId(currentUserPublicId).orElse(null);
                }

                int safeSize = Math.min(Math.max(size, 1), 100);
                org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                                safeSize);
                List<Long> followedIds = subscriptionRepository
                                .findFollowedUserIdsByFollowerIdPaged(targetUser.getId(), pageable);
                // Per-user block status is computed in getProfiles below.
                List<User> followed = userRepository.findAllById(followedIds);
                return getProfiles(followed, currentUser);
        }

        public List<ProfileResponse> searchProfiles(String query, String currentUserPublicId, int page, int size) {
                if (query == null || query.trim().isEmpty()) {
                        return List.of();
                }

                User currentUser = userRepository.findByPublicId(currentUserPublicId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                Set<Long> blockedIds = new java.util.HashSet<>(userBlockRepository.findBlockedUserIdsByBlockerId(currentUser.getId()));

                Set<Long> blockingMeIds = new java.util.HashSet<>(userBlockRepository.findBlockerUserIdsByBlockedId(currentUser.getId()));

                int safeSize = Math.min(Math.max(size, 1), 50);
                org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                                safeSize);
                org.springframework.data.domain.Page<User> usersPage = userRepository
                                .findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query, pageable);

                List<User> matchedUsers = usersPage.stream()
                                .filter(u -> !u.getId().equals(currentUser.getId()))
                                .filter(u -> !u.isBanned())
                                .filter(u -> !blockedIds.contains(u.getId()))
                                .filter(u -> !blockingMeIds.contains(u.getId()))
                                .collect(Collectors.toList());

                return getProfiles(matchedUsers, currentUser);
        }

        private List<ProfileResponse> getProfiles(List<User> targetUsers, User currentUser) {
                if (targetUsers.isEmpty()) {
                        return List.of();
                }
                List<Long> targetUserIds = targetUsers.stream().map(User::getId).collect(Collectors.toList());

                // Batch fetch UserProfiles with JOIN FETCH on user to avoid N+1
                Map<Long, UserProfile> profilesMap = userProfileRepository.findByUserIdInWithUser(targetUserIds).stream()
                                .collect(Collectors.toMap(
                                                p -> p.getUser().getId(),
                                                p -> p,
                                                (v1, v2) -> v1));

                // Batch fetch follower counts
                Map<Long, Long> followerCounts = subscriptionRepository.countFollowersByFollowedIds(targetUserIds)
                                .stream()
                                .collect(Collectors.toMap(
                                                arr -> (Long) arr[0],
                                                arr -> (Long) arr[1],
                                                (v1, v2) -> v1));

                // Batch fetch following counts
                Map<Long, Long> followingCounts = subscriptionRepository.countFollowingByFollowerIds(targetUserIds)
                                .stream()
                                .collect(Collectors.toMap(
                                                arr -> (Long) arr[0],
                                                arr -> (Long) arr[1],
                                                (v1, v2) -> v1));

                // Batch fetch block and follow status
                Set<Long> blockedIds = new java.util.HashSet<>();
                Set<Long> blockingMeIds = new java.util.HashSet<>();
                Set<Long> followingIds = new java.util.HashSet<>();

                if (currentUser != null) {
                        blockedIds.addAll(userBlockRepository.findBlockedUserIdsByBlockerId(currentUser.getId()));

                        blockingMeIds.addAll(userBlockRepository.findBlockerUserIdsByBlockedId(currentUser.getId()));

                        followingIds.addAll(subscriptionRepository.findFollowedUserIdsByFollowerId(currentUser.getId()));
                }

                return targetUsers.stream().map(u -> {
                        UserProfile profile = profilesMap.getOrDefault(u.getId(), null);
                        long followerCount = followerCounts.getOrDefault(u.getId(), 0L);
                        long followingCount = followingCounts.getOrDefault(u.getId(), 0L);
                        boolean isFollowing = followingIds.contains(u.getId());
                        boolean isBlocked = blockedIds.contains(u.getId());
                        boolean isBlockingMe = blockingMeIds.contains(u.getId());

                        return ProfileResponse.builder()
                                        .publicId(u.getPublicId())
                                        .username(u.getUsername())
                                        .displayName(profile != null ? profile.getDisplayName() : null)
                                        .bio(profile != null ? profile.getBio() : null)
                                        .avatarUrl(profile != null ? profile.getAvatarUrl() : null)
                                        .followerCount(followerCount)
                                        .followingCount(followingCount)
                                        .isFollowing(isFollowing)
                                        .isBlocked(isBlocked)
                                        .isBlockingMe(isBlockingMe)
                                        .build();
                }).collect(Collectors.toList());
        }
}
