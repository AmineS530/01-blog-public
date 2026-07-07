import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ProfileService } from '../../core/services/profile.service';
import { AuthService } from '../../core/services/auth.service';
import { FeedbackService } from '../../core/services/feedback.service';
import { ProfileResponse } from '../../shared/models/profile.models';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './users.html',
  styleUrl: './users.css',
})
export class UsersComponent implements OnInit {
  users: ProfileResponse[] = [];
  searchQuery = '';
  loading = false;
  error = '';
  currentPage = 0;
  pageSize = 12;
  hasMore = true;
  loadingMore = false;
  currentUsername = '';

  constructor(
    private profileService: ProfileService,
    private authService: AuthService,
    private feedback: FeedbackService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.currentUsername = this.authService.getUsername() ?? '';
    this.loadUsers();
  }

  loadUsers(append = false): void {
    if (!append) {
      this.loading = true;
      this.currentPage = 0;
      this.users = [];
      this.hasMore = true;
    } else {
      this.loadingMore = true;
    }
    this.error = '';

    this.profileService
      .searchProfiles(this.searchQuery, this.currentPage, this.pageSize)
      .subscribe({
        next: (list) => {
          if (append) {
            const newUsers = list.filter(
              (u) => !this.users.some((existing) => existing.publicId === u.publicId)
            );
            this.users = [...this.users, ...newUsers];
            this.hasMore = list.length >= this.pageSize;
          } else {
            this.users = list;
            this.hasMore = list.length >= this.pageSize;
          }
          this.loading = false;
          this.loadingMore = false;
        },
        error: () => {
          this.error = 'Failed to load creators list.';
          this.loading = false;
          this.loadingMore = false;
        },
      });
  }

  onSearch(): void {
    this.loadUsers();
  }

  loadNextPage(): void {
    if (this.loadingMore || !this.hasMore) return;
    this.currentPage++;
    this.loadUsers(true);
  }

  toggleFollow(event: Event, user: ProfileResponse): void {
    event.stopPropagation();
    const wasFollowing = user.isFollowing;
    user.isFollowing = !wasFollowing;
    user.followerCount += user.isFollowing ? 1 : -1;

    this.profileService.toggleFollow(user.username).subscribe({
      next: () => {
        this.feedback.showToast(
          user.isFollowing ? `You followed @${user.username}` : `You unfollowed @${user.username}`,
          'success'
        );
      },
      error: (err) => {
        user.isFollowing = wasFollowing;
        user.followerCount += wasFollowing ? 1 : -1;
        if (err?.status !== 429) {
          this.feedback.showToast('Failed to update follow status.', 'error');
        }
      },
    });
  }

  toggleBlock(event: Event, user: ProfileResponse): void {
    event.stopPropagation();
    const isBlocked = user.isBlocked;
    this.feedback.askConfirmation({
      title: isBlocked ? `Unblock @${user.username}?` : `Block @${user.username}?`,
      message: isBlocked
        ? `Are you sure you want to unblock @${user.username}? They will be able to follow you and message you again.`
        : `Are you sure you want to block @${user.username}? You will not see their posts anymore, and they won't be able to message you or follow you.`,
      confirmText: isBlocked ? 'Unblock' : 'Block',
      onConfirm: () => {
        this.profileService.toggleBlock(user.username).subscribe({
          next: () => {
            if (isBlocked) {
              user.isBlocked = false;
              this.feedback.showToast(`Unblocked @${user.username}`, 'success');
            } else {
              this.feedback.showToast(`Blocked @${user.username}`, 'success');
              this.users = this.users.filter((u) => u.username !== user.username);
            }
          },
          error: () => {
            this.feedback.showToast(isBlocked ? 'Failed to unblock user.' : 'Failed to block user.', 'error');
          },
        });
      },
    });
  }

  showMiniProfile(username: string): void {
    this.feedback.showMiniProfile(username, this.profileService);
  }

  goToProfile(username: string): void {
    this.router.navigate(['/profile', username]);
  }

  startChat(event: Event, user: ProfileResponse): void {
    event.stopPropagation();
    this.router.navigate(['/chat'], {
      queryParams: {
        chatWith: user.publicId,
        username: user.username,
        displayName: user.displayName || user.username,
        avatarUrl: user.avatarUrl,
      },
    });
  }
}
