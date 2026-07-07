import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { PostService } from '../../core/services/post.service';
import { AuthService } from '../../core/services/auth.service';
import { ReportService } from '../../core/services/report.service';
import { ProfileService } from '../../core/services/profile.service';
import { MediaService } from '../../core/services/media.service';
import { FeedbackService } from '../../core/services/feedback.service';
import { RealtimeService } from '../../core/services/realtime.service';
import { PostResponse } from '../../shared/models/post.models';
import { ProfileResponse } from '../../shared/models/profile.models';
import { MarkdownPipe } from '../../shared/pipes/markdown.pipe';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-feed',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatInputModule,
    MatFormFieldModule,
    MarkdownPipe,
  ],
  templateUrl: './feed.html',
  styleUrl: './feed.css',
})
export class FeedComponent implements OnInit, OnDestroy {
  posts: PostResponse[] = [];
  recommendedUsers: ProfileResponse[] = [];
  loading = true;
  loadingRecommended = false;
  error = '';
  currentUsername = '';
  currentUserAvatarUrl: string | null = null;
  currentUserDisplayName = '';
  activeTab: 'global' | 'following' = 'global';

  currentPage = 0;
  pageSize = 10;
  hasMorePosts = true;
  loadingMore = false;

  newIncomingPosts: PostResponse[] = [];
  private postsSubscription?: Subscription;

  // Quick Post fields
  quickPostTitle = '';
  quickPostContent = '';
  quickPostMediaUrl: string | null = null;
  uploadingMedia = false;
  submittingPost = false;

  constructor(
    private postService: PostService,
    private authService: AuthService,
    private reportService: ReportService,
    private profileService: ProfileService,
    private mediaService: MediaService,
    private router: Router,
    private feedback: FeedbackService,
    private realtimeService: RealtimeService,
  ) {}

  ngOnInit(): void {
    // Reuse the cached profile populated by AuthService. The result also
    // gives us the username, which components elsewhere still read via
    // authService.getUsername() — we mirror it locally for this view's
    // isAuthor checks.
    this.authService.ensureProfileLoaded().subscribe({
      next: (profile) => {
        if (profile) {
          this.currentUsername = profile.username;
          this.currentUserAvatarUrl = profile.avatarUrl;
          this.currentUserDisplayName = profile.displayName || profile.username;
        }
      },
    });

    this.loadPosts();
    this.loadRecommendedUsers();

    this.postsSubscription = this.realtimeService.posts$.subscribe({
      next: (newPost: PostResponse) => {
        // If the post matches current tab filter (if we follow the user, or if on global feed)
        const isFromFollowed = this.recommendedUsers.some(
          (u) => u.username === newPost.authorUsername && u.isFollowing,
        );
        const isAuthor = newPost.authorUsername === this.currentUsername;

        if (
          this.activeTab === 'global' ||
          (this.activeTab === 'following' && (isFromFollowed || isAuthor))
        ) {
          if (
            !this.posts.some((p) => p.publicId === newPost.publicId) &&
            !this.newIncomingPosts.some((p) => p.publicId === newPost.publicId)
          ) {
            this.newIncomingPosts = [newPost, ...this.newIncomingPosts];
          }
        }
      },
    });
  }

  loadPosts(): void {
    this.loading = true;
    this.error = '';
    this.currentPage = 0;
    this.hasMorePosts = true;

    const request =
      this.activeTab === 'global'
        ? this.postService.getAll(this.currentPage, this.pageSize)
        : this.postService.getFollowingFeed(this.currentPage, this.pageSize);

    request.subscribe({
      next: (posts) => {
        this.posts = posts;
        this.hasMorePosts = posts.length >= this.pageSize;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load posts.';
        this.loading = false;
      },
    });
  }

  loadNextPage(): void {
    if (this.loadingMore || !this.hasMorePosts) return;
    this.loadingMore = true;
    const nextPage = this.currentPage + 1;

    const request =
      this.activeTab === 'global'
        ? this.postService.getAll(nextPage, this.pageSize)
        : this.postService.getFollowingFeed(nextPage, this.pageSize);

    request.subscribe({
      next: (posts) => {
        if (posts.length > 0) {
          // Filter out any posts we already have to prevent duplicates (e.g. from live updates)
          const newPosts = posts.filter(
            (p) => !this.posts.some((existing) => existing.publicId === p.publicId),
          );
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
      },
    });
  }

  loadRecommendedUsers(): void {
    this.loadingRecommended = true;
    this.profileService.getRecommendedProfiles().subscribe({
      next: (users) => {
        this.recommendedUsers = users;
        this.loadingRecommended = false;
      },
      error: () => {
        this.loadingRecommended = false;
      },
    });
  }

  switchTab(tab: 'global' | 'following'): void {
    if (this.activeTab === tab) return;
    this.activeTab = tab;
    this.loadPosts();
  }

  toggleFollow(event: Event, user: ProfileResponse): void {
    event.stopPropagation();

    // Optimistic toggle
    user.isFollowing = !user.isFollowing;
    user.followerCount += user.isFollowing ? 1 : -1;

    this.profileService.toggleFollow(user.username).subscribe({
      next: () => {
        // Refresh following feed and recommended list to keep state clean
        this.loadRecommendedUsers();
        if (this.activeTab === 'following') {
          this.loadPosts();
        }
      },
      error: () => {
        // Revert on failure
        user.isFollowing = !user.isFollowing;
        user.followerCount += user.isFollowing ? 1 : -1;
      },
    });
  }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;

    this.uploadingMedia = true;
    this.mediaService.upload(file).subscribe({
      next: (response) => {
        this.quickPostMediaUrl = response.url;
        this.uploadingMedia = false;
        this.feedback.showToast('Media uploaded successfully!', 'success');
      },
      error: () => {
        this.feedback.showToast('Failed to upload media. Please try again.', 'error');
        this.uploadingMedia = false;
      },
    });
  }

  removeMedia(): void {
    this.quickPostMediaUrl = null;
    this.feedback.showToast('Media attachment removed.', 'info');
  }

  publishPost(): void {
    if (!this.quickPostTitle.trim() || !this.quickPostContent.trim() || this.uploadingMedia) {
      return;
    }

    this.submittingPost = true;
    const postData = {
      title: this.quickPostTitle,
      content: this.quickPostContent,
      mediaUrl: this.quickPostMediaUrl ?? undefined,
    };

    this.postService.create(postData).subscribe({
      next: (newPost) => {
        // Reset fields
        this.quickPostTitle = '';
        this.quickPostContent = '';
        this.quickPostMediaUrl = null;
        this.submittingPost = false;

        // Add new post to feed dynamically at the top
        this.posts = [newPost, ...this.posts];
        this.feedback.showToast('Post published successfully!', 'success');
      },
      error: () => {
        this.feedback.showToast('Failed to publish post. Please try again.', 'error');
        this.submittingPost = false;
      },
    });
  }

  goToCreate(): void {
    this.router.navigate(['/posts/create']);
  }

  goToPost(publicId: string): void {
    this.router.navigate(['/posts', publicId]);
  }

  goToProfile(event: Event, username: string): void {
    event.stopPropagation();
    this.feedback.showMiniProfile(username, this.profileService);
  }

  toggleLike(event: Event, post: PostResponse): void {
    event.stopPropagation();
    post.isLikedByCurrentUser = !post.isLikedByCurrentUser;
    post.likeCount += post.isLikedByCurrentUser ? 1 : -1;
    this.postService.togglePostLike(post.publicId).subscribe({
      error: (err) => {
        post.isLikedByCurrentUser = !post.isLikedByCurrentUser;
        post.likeCount += post.isLikedByCurrentUser ? 1 : -1;
        if (err?.status !== 429) {
          this.feedback.showToast('Failed to update like status.', 'error');
        }
      },
    });
  }

  reportPost(event: Event, post: PostResponse): void {
    event.stopPropagation();
    this.feedback.askPrompt({
      title: 'Report Post',
      message: `Why are you reporting this post by ${post.authorDisplayName || post.authorUsername}?`,
      placeholder: 'Reason for reporting',
      confirmText: 'Report',
      onConfirm: (reason) => {
        this.reportService.reportPost(post.publicId, reason).subscribe({
          next: () => this.feedback.showToast('Post reported successfully.', 'success'),
          error: () => this.feedback.showToast('Failed to report post.', 'error'),
        });
      },
    });
  }

  isImage(url: string | null | undefined): boolean {
    if (!url) return false;
    const lowercase = url.toLowerCase();
    return lowercase.includes('.jpg') || 
           lowercase.includes('.jpeg') || 
           lowercase.includes('.png') || 
           lowercase.includes('.gif') || 
           lowercase.includes('.webp') ||
           lowercase.startsWith('data:image/');
  }

  isVideo(url: string | null | undefined): boolean {
    if (!url) return false;
    const lowercase = url.toLowerCase();
    return lowercase.includes('.mp4') || 
           lowercase.includes('.webm') || 
           lowercase.includes('.ogg') ||
           lowercase.startsWith('data:video/');
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  applyNewPosts(): void {
    if (this.newIncomingPosts.length === 0) return;
    this.posts = [...this.newIncomingPosts, ...this.posts];
    this.newIncomingPosts = [];
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  ngOnDestroy(): void {
    this.postsSubscription?.unsubscribe();
  }
}
