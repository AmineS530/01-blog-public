package com.zero1blog.backend.service;

import com.zero1blog.backend.dto.ProfileResponse;
import com.zero1blog.backend.dto.ProfileUpdateRequest;
import com.zero1blog.backend.model.Subscription;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.model.UserBlock;
import com.zero1blog.backend.model.UserProfile;
import com.zero1blog.backend.repository.SubscriptionRepository;
import com.zero1blog.backend.repository.UserBlockRepository;
import com.zero1blog.backend.repository.UserProfileRepository;
import com.zero1blog.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public ProfileResponse getProfile(String targetUsername, String currentUserPublicId) {
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = userProfileRepository.findByUser(targetUser).orElse(new UserProfile());

        long followerCount = subscriptionRepository.countByFollowed(targetUser);
        long followingCount = subscriptionRepository.countByFollower(targetUser);

        boolean isFollowing = false;
        boolean isBlocked = false;
        boolean isBlockingMe = false;

        if (currentUserPublicId != null) {
            User currentUser = userRepository.findByPublicId(currentUserPublicId).orElse(null);
            if (currentUser != null) {
                isFollowing = subscriptionRepository.existsByFollowerAndFollowed(currentUser, targetUser);
                isBlocked = userBlockRepository.existsByBlockerAndBlocked(currentUser, targetUser);
                isBlockingMe = userBlockRepository.existsByBlockerAndBlocked(targetUser, currentUser);
            }
        }

        return ProfileResponse.builder()
                .id(targetUser.getId())
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
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = userProfileRepository.findByUser(currentUser)
                .orElse(UserProfile.builder().user(currentUser).build());

        profile.setDisplayName(request.getDisplayName());
        profile.setBio(request.getBio());
        profile.setAvatarUrl(request.getAvatarUrl());

        userProfileRepository.save(profile);

        return getProfile(currentUser.getUsername(), currentUserPublicId);
    }

    public ProfileResponse updateProfileByUsername(String targetUsername, ProfileUpdateRequest request, String callerPublicId) {
        User caller = userRepository.findByPublicId(callerPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        boolean isSelf = targetUser.getPublicId().equals(callerPublicId);
        boolean isAdmin = caller.getRole() == User.Role.ADMIN || caller.getRole() == User.Role.SUPER_ADMIN;

        if (!isSelf && !isAdmin) {
            throw new RuntimeException("Not authorized to update this profile");
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
                .orElseThrow(() -> new RuntimeException("User not found"));
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        if (currentUser.getId().equals(targetUser.getId())) {
            throw new RuntimeException("Cannot follow yourself");
        }
        
        if (userBlockRepository.existsByBlockerAndBlocked(targetUser, currentUser)) {
            throw new RuntimeException("You are blocked by this user");
        }

        Optional<Subscription> existing = subscriptionRepository.findByFollowerAndFollowed(currentUser, targetUser);
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
                null
            );
        }
    }

    public void toggleBlock(String targetUsername, String currentUserPublicId) {
        User currentUser = userRepository.findByPublicId(currentUserPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User targetUser = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        if (currentUser.getId().equals(targetUser.getId())) {
            throw new RuntimeException("Cannot block yourself");
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
            subscriptionRepository.findByFollowerAndFollowed(currentUser, targetUser).ifPresent(subscriptionRepository::delete);
            subscriptionRepository.findByFollowerAndFollowed(targetUser, currentUser).ifPresent(subscriptionRepository::delete);
        }
    }

    public List<ProfileResponse> getRecommendedProfiles(String currentUserPublicId) {
        User currentUser = userRepository.findByPublicId(currentUserPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Long> followedIds = subscriptionRepository.findByFollower(currentUser)
                .stream()
                .map(sub -> sub.getFollowed().getId())
                .collect(Collectors.toSet());

        Set<Long> blockedIds = userBlockRepository.findByBlocker(currentUser)
                .stream()
                .map(ub -> ub.getBlocked().getId())
                .collect(Collectors.toSet());

        Set<Long> blockingMeIds = userBlockRepository.findByBlocked(currentUser)
                .stream()
                .map(ub -> ub.getBlocker().getId())
                .collect(Collectors.toSet());

        List<User> allUsers = userRepository.findAll();

        return allUsers.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .filter(u -> !followedIds.contains(u.getId()))
                .filter(u -> !blockedIds.contains(u.getId()))
                .filter(u -> !blockingMeIds.contains(u.getId()))
                .limit(5)
                .map(u -> getProfile(u.getUsername(), currentUserPublicId))
                .collect(Collectors.toList());
    }

    public List<ProfileResponse> getFollowers(String username, String currentUserPublicId) {
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (currentUserPublicId != null) {
            User currentUser = userRepository.findByPublicId(currentUserPublicId).orElse(null);
            if (currentUser != null && (userBlockRepository.existsByBlockerAndBlocked(currentUser, targetUser) ||
                    userBlockRepository.existsByBlockerAndBlocked(targetUser, currentUser))) {
                return List.of();
            }
        }

        List<Subscription> subs = subscriptionRepository.findByFollowed(targetUser);
        return subs.stream()
                .map(sub -> getProfile(sub.getFollower().getUsername(), currentUserPublicId))
                .collect(Collectors.toList());
    }

    public List<ProfileResponse> getFollowing(String username, String currentUserPublicId) {
        User targetUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (currentUserPublicId != null) {
            User currentUser = userRepository.findByPublicId(currentUserPublicId).orElse(null);
            if (currentUser != null && (userBlockRepository.existsByBlockerAndBlocked(currentUser, targetUser) ||
                    userBlockRepository.existsByBlockerAndBlocked(targetUser, currentUser))) {
                return List.of();
            }
        }

        List<Subscription> subs = subscriptionRepository.findByFollower(targetUser);
        return subs.stream()
                .map(sub -> getProfile(sub.getFollowed().getUsername(), currentUserPublicId))
                .collect(Collectors.toList());
    }

    public List<ProfileResponse> searchProfiles(String query, String currentUserPublicId) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        User currentUser = userRepository.findByPublicId(currentUserPublicId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Long> blockedIds = userBlockRepository.findByBlocker(currentUser)
                .stream()
                .map(ub -> ub.getBlocked().getId())
                .collect(Collectors.toSet());

        Set<Long> blockingMeIds = userBlockRepository.findByBlocked(currentUser)
                .stream()
                .map(ub -> ub.getBlocker().getId())
                .collect(Collectors.toSet());

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        org.springframework.data.domain.Page<User> usersPage = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query, pageable);

        return usersPage.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .filter(u -> !u.isBanned())
                .filter(u -> !blockedIds.contains(u.getId()))
                .filter(u -> !blockingMeIds.contains(u.getId()))
                .map(u -> getProfile(u.getUsername(), currentUserPublicId))
                .collect(Collectors.toList());
    }
}

