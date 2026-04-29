import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PostService } from '../../core/services/post.service';
import { AuthService } from '../../core/services/auth.service';
import { PostResponse } from '../../shared/models/post.models';

@Component({
  selector: 'app-feed',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './feed.html',
  styleUrl: './feed.css',
})
export class FeedComponent implements OnInit {
  posts: PostResponse[] = [];
  loading = true;
  error = '';
  currentUsername = '';

  constructor(
    private postService: PostService,
    private authService: AuthService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.currentUsername = this.authService.getUsername() ?? '';
    this.loadPosts();
  }

  loadPosts(): void {
    this.loading = true;
    this.postService.getAll().subscribe({
      next: (posts) => {
        this.posts = posts;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load posts.';
        this.loading = false;
      },
    });
  }

  goToCreate(): void {
    this.router.navigate(['/posts/create']);
  }

  goToPost(id: number): void {
    this.router.navigate(['/posts', id]);
  }

  goToProfile(event: Event, username: string): void {
    event.stopPropagation();
    this.router.navigate(['/profile', username]);
  }

  toggleLike(event: Event, post: PostResponse): void {
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

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
