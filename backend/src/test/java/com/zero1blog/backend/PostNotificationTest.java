package com.zero1blog.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.zero1blog.backend.config.PostCreatedEvent;
import com.zero1blog.backend.dto.PostRequest;
import com.zero1blog.backend.dto.PostResponse;
import com.zero1blog.backend.model.Post;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.*;
import com.zero1blog.backend.service.MediaService;
import com.zero1blog.backend.service.NotificationService;
import com.zero1blog.backend.service.PostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class PostNotificationTest {

    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private MediaService mediaService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private PostService postService;

    @Test
    void createPostNotifiesFollowers() {
        User author = new User();
        author.setId(1L);
        author.setUsername("authorUser");
        author.setPublicId("author-public-id");

        User follower = new User();
        follower.setId(2L);
        follower.setUsername("followerUser");
        follower.setPublicId("follower-public-id");

        PostRequest request = new PostRequest();
        request.setTitle("New Post Title");
        request.setContent("New Post Content");

        Post savedPost = new Post();
        savedPost.setId(10L);
        savedPost.setTitle(request.getTitle());
        savedPost.setContent(request.getContent());
        savedPost.setAuthor(author);
        savedPost.setPublicId("post-public-id");

        when(userRepository.findByPublicId("author-public-id")).thenReturn(Optional.of(author));
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);
        when(subscriptionRepository.findFollowersByFollowedId(author.getId())).thenReturn(List.of(follower));

        PostResponse response = postService.createPost(request, "author-public-id");

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("New Post Title");

        // Verify that notificationService.createNotification is called with correct parameters
        verify(notificationService, times(1)).createNotification(
            eq("POST"),
            eq("authorUser published a new post: New Post Title"),
            eq(follower),
            eq(author),
            eq(savedPost)
        );

        // Verify that the event is published
        verify(eventPublisher, times(1)).publishEvent(any(PostCreatedEvent.class));
    }
}
