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
import { ProfileResponse } from '../../shared/models/profile.models';
import { PostResponse } from '../../shared/models/post.models';

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
    MatTooltipModule
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

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private profileService: ProfileService,
    private postService: PostService,
    private authService: AuthService
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
    this.postService.getByUsername(this.targetUsername).subscribe({
      next: (posts) => {
        this.posts = posts;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
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

  goToPost(id: number): void {
    this.router.navigate(['/posts', id]);
  }

  back(): void {
    this.router.navigate(['/feed']);
  }
}
