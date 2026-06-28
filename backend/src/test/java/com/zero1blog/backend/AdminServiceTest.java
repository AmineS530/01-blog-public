package com.zero1blog.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.zero1blog.backend.exception.BadRequestException;
import com.zero1blog.backend.exception.ResourceNotFoundException;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.*;
import com.zero1blog.backend.service.AdminService;
import com.zero1blog.backend.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private ReportService reportService;
    @Mock private UserProfileRepository userProfileRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void updateUsernameSuccess() {
        User user = new User();
        user.setUsername("bob");
        user.setRole(User.Role.USER);

        User caller = new User();
        caller.setRole(User.Role.ADMIN);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(userRepository.findByPublicId("admin_caller")).thenReturn(Optional.of(caller));
        when(userRepository.existsByUsername("alice")).thenReturn(false);

        adminService.updateUsername("bob", "alice", "admin_caller");

        assertThat(user.getUsername()).isEqualTo("alice");
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateUsernamePolicyViolation() {
        User user = new User();
        user.setUsername("bob");
        user.setRole(User.Role.USER);

        User caller = new User();
        caller.setRole(User.Role.ADMIN);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(userRepository.findByPublicId("admin_caller")).thenReturn(Optional.of(caller));

        // too short
        assertThatThrownBy(() -> adminService.updateUsername("bob", "ab", "admin_caller"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("between 3 and 30 characters");

        // invalid characters
        assertThatThrownBy(() -> adminService.updateUsername("bob", "ab#c", "admin_caller"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("letters, numbers, underscores, and hyphens");
    }

    @Test
    void updateUsernameAlreadyTaken() {
        User user = new User();
        user.setUsername("bob");
        user.setRole(User.Role.USER);

        User caller = new User();
        caller.setRole(User.Role.ADMIN);

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(userRepository.findByPublicId("admin_caller")).thenReturn(Optional.of(caller));
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> adminService.updateUsername("bob", "alice", "admin_caller"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Username is already taken");
    }

    @Test
    void updateUsernameUserNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateUsername("nonexistent", "newname", "admin_caller"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateUsernameAdminCallerTargetingSuperAdminThrowsUnauthorized() {
        User superAdmin = new User();
        superAdmin.setUsername("super");
        superAdmin.setRole(User.Role.SUPER_ADMIN);

        User adminCaller = new User();
        adminCaller.setRole(User.Role.ADMIN);

        when(userRepository.findByUsername("super")).thenReturn(Optional.of(superAdmin));
        when(userRepository.findByPublicId("admin_caller")).thenReturn(Optional.of(adminCaller));

        assertThatThrownBy(() -> adminService.updateUsername("super", "newname", "admin_caller"))
                .isInstanceOf(com.zero1blog.backend.exception.UnauthorizedActionException.class)
                .hasMessageContaining("Only SUPER_ADMIN can modify administrator profiles");
    }
}
