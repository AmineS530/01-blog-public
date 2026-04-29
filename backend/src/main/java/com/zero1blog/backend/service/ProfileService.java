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

import java.util.Optional;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserBlockRepository userBlockRepository;

    public ProfileService(UserRepository userRepository, UserProfileRepository userProfileRepository,
                          SubscriptionRepository subscriptionRepository, UserBlockRepository userBlockRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userBlockRepository = userBlockRepository;
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
                .username(targetUser.getUsername())
                .fullName(profile.getFullName())
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

        profile.setFullName(request.getFullName());
        profile.setBio(request.getBio());
        profile.setAvatarUrl(request.getAvatarUrl());

        userProfileRepository.save(profile);

        return getProfile(currentUser.getUsername(), currentUserPublicId);
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
}
