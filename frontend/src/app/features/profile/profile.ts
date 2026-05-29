import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ProfileService } from '../../core/services/profile.service';
import { PostService } from '../../core/services/post.service';
import { AuthService } from '../../core/services/auth.service';
import { ReportService } from '../../core/services/report.service';
import { FeedbackService } from '../../core/services/feedback.service';
import { MediaService } from '../../core/services/media.service';
import { ProfileResponse } from '../../shared/models/profile.models';
import { PostResponse } from '../../shared/models/post.models';
import { MarkdownPipe } from '../../shared/pipes/markdown.pipe';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MarkdownPipe
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.css'
})
export class ProfileComponent implements OnInit {
  profile: ProfileResponse | null = null;
  posts: PostResponse[] = [];
  
  loading = true;
  error = '';
  
  currentUsername = '';
  targetUsername = '';

  currentPage = 0;
  pageSize = 10;
  hasMorePosts = true;
  loadingMore = false;

  activeTab: 'posts' | 'followers' | 'following' = 'posts';
  followers: ProfileResponse[] = [];
  followingList: ProfileResponse[] = [];
  loadingFollowers = false;
  loadingFollowing = false;
  uploadingAvatar = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private profileService: ProfileService,
    private postService: PostService,
    private authService: AuthService,
    private mediaService: MediaService,
    private reportService: ReportService,
    private feedback: FeedbackService
  ) {}

  ngOnInit(): void {
    this.currentUsername = this.authService.getUsername() ?? '';
    
    this.route.paramMap.subscribe(params => {
      this.targetUsername = params.get('username') || '';
      if (!this.targetUsername) {
        this.router.navigate(['/feed']);
        return;
      }
      this.loadProfile();
    });
  }

  loadProfile(): void {
    this.loading = true;
    this.error = '';
    this.currentPage = 0;
    this.hasMorePosts = true;
    this.activeTab = 'posts';
    this.followers = [];
    this.followingList = [];

    this.profileService.getProfile(this.targetUsername).subscribe({
      next: (profile) => {
        this.profile = profile;
        this.loadPosts();
      },
      error: () => {
        this.error = 'Profile not found.';
        this.loading = false;
      }
    });
  }

  loadPosts(): void {
    this.postService.getByUsername(this.targetUsername, this.currentPage, this.pageSize).subscribe({
      next: (posts) => {
        this.posts = posts;
        this.hasMorePosts = posts.length >= this.pageSize;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  loadNextPage(): void {
    if (this.loadingMore || !this.hasMorePosts) return;
    this.loadingMore = true;
    const nextPage = this.currentPage + 1;

    this.postService.getByUsername(this.targetUsername, nextPage, this.pageSize).subscribe({
      next: (posts) => {
        if (posts.length > 0) {
          const newPosts = posts.filter(p => !this.posts.some(existing => existing.id === p.id));
          this.posts = [...this.posts, ...newPosts];
          this.currentPage = nextPage;
          this.hasMorePosts = posts.length >= this.pageSize;
        } else {
          this.hasMorePosts = false;
        }
        this.loadingMore = false;
      },
      error: () => {
        this.feedback.showToast('Failed to load more posts.', 'error');
        this.loadingMore = false;
      }
    });
  }

  toggleFollow(): void {
    if (!this.profile) return;
    const wasFollowing = this.profile.isFollowing;
    this.profile.isFollowing = !wasFollowing;
    this.profile.followerCount += this.profile.isFollowing ? 1 : -1;

    this.profileService.toggleFollow(this.targetUsername).subscribe({
      error: () => {
        this.profile!.isFollowing = wasFollowing;
        this.profile!.followerCount += wasFollowing ? 1 : -1;
      }
    });
  }

  toggleBlock(): void {
    if (!this.profile) return;
    const wasBlocked = this.profile.isBlocked;
    this.profile.isBlocked = !wasBlocked;
    
    // If we just blocked them, also reset following statuses locally since backend does it
    if (this.profile.isBlocked) {
      if (this.profile.isFollowing) {
        this.profile.isFollowing = false;
        this.profile.followerCount = Math.max(0, this.profile.followerCount - 1);
      }
    }

    this.profileService.toggleBlock(this.targetUsername).subscribe({
      next: () => {
        this.loadProfile(); // Reload to get fresh follower/following counts
      },
      error: () => {
        this.profile!.isBlocked = wasBlocked;
      }
    });
  }

  togglePostLike(event: Event, post: PostResponse): void {
    event.stopPropagation();
    post.isLikedByCurrentUser = !post.isLikedByCurrentUser;
    post.likeCount += post.isLikedByCurrentUser ? 1 : -1;
    this.postService.togglePostLike(post.id).subscribe({
      error: () => {
        post.isLikedByCurrentUser = !post.isLikedByCurrentUser;
        post.likeCount += post.isLikedByCurrentUser ? 1 : -1;
      }
    });
  }

  reportUser(): void {
    if (!this.profile) return;
    const reason = prompt(`Why are you reporting user ${this.profile.username}?`);
    if (reason) {
      this.reportService.reportUser(this.profile.id, reason).subscribe({
        next: () => this.feedback.showToast('User reported successfully.', 'success'),
        error: () => this.feedback.showToast('Failed to report user.', 'error')
      });
    }
  }

  reportPost(event: Event, post: PostResponse): void {
    event.stopPropagation();
    const reason = prompt(`Why are you reporting this post by ${post.authorUsername}?`);
    if (reason) {
      this.reportService.reportPost(post.id, reason).subscribe({
        next: () => this.feedback.showToast('Post reported successfully.', 'success'),
        error: () => this.feedback.showToast('Failed to report post.', 'error')
      });
    }
  }

  goToPost(id: number): void {
    this.router.navigate(['/posts', id]);
  }

  back(): void {
    this.router.navigate(['/feed']);
  }

  navigateToProfile(username: string): void {
    this.router.navigate(['/profile', username]);
  }

  setActiveTab(tab: 'posts' | 'followers' | 'following'): void {
    this.activeTab = tab;
    if (tab === 'followers') {
      this.loadFollowers();
    } else if (tab === 'following') {
      this.loadFollowing();
    }
  }

  loadFollowers(): void {
    if (!this.targetUsername) return;
    this.loadingFollowers = true;
    this.profileService.getFollowers(this.targetUsername).subscribe({
      next: (list) => {
        this.followers = list;
        this.loadingFollowers = false;
      },
      error: () => {
        this.feedback.showToast('Failed to load followers.', 'error');
        this.loadingFollowers = false;
      }
    });
  }

  loadFollowing(): void {
    if (!this.targetUsername) return;
    this.loadingFollowing = true;
    this.profileService.getFollowing(this.targetUsername).subscribe({
      next: (list) => {
        this.followingList = list;
        this.loadingFollowing = false;
      },
      error: () => {
        this.feedback.showToast('Failed to load following.', 'error');
        this.loadingFollowing = false;
      }
    });
  }

  toggleFollowUser(user: ProfileResponse, event: MouseEvent): void {
    event.stopPropagation();
    const wasFollowing = user.isFollowing;
    user.isFollowing = !wasFollowing;
    user.followerCount += user.isFollowing ? 1 : -1;

    // Reactively update current profile stats if we are on our own profile page
    if (this.currentUsername === this.targetUsername && this.profile) {
      this.profile.followingCount += user.isFollowing ? 1 : -1;
    }

    this.profileService.toggleFollow(user.username).subscribe({
      error: () => {
        user.isFollowing = wasFollowing;
        user.followerCount += wasFollowing ? 1 : -1;
        if (this.currentUsername === this.targetUsername && this.profile) {
          this.profile.followingCount += wasFollowing ? 1 : -1;
        }
      }
    });
  }

  onAvatarSelected(event: any): void {
    const file = event.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      this.feedback.showToast('Please select a valid image file.', 'error');
      return;
    }

    this.uploadingAvatar = true;
    this.mediaService.upload(file).subscribe({
      next: (res) => {
        if (this.profile) {
          const updateReq = {
            fullName: this.profile.fullName || '',
            bio: this.profile.bio || '',
            avatarUrl: res.url
          };
          this.profileService.updateProfile(updateReq).subscribe({
            next: (updatedProfile) => {
              this.profile = updatedProfile;
              this.uploadingAvatar = false;
              this.feedback.showToast('Profile picture updated successfully!', 'success');
            },
            error: () => {
              this.feedback.showToast('Failed to update profile picture.', 'error');
              this.uploadingAvatar = false;
            }
          });
        } else {
          this.uploadingAvatar = false;
        }
      },
      error: () => {
        this.feedback.showToast('Failed to upload image.', 'error');
        this.uploadingAvatar = false;
      }
    });
  }
}
