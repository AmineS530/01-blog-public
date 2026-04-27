import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PostService } from '../../../core/services/post.service';
import { AuthService } from '../../../core/services/auth.service';
import { PostResponse } from '../../../shared/models/post.models';

@Component({
  selector: 'app-single-post',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './single-post.html',
  styleUrl: './single-post.css'
})
export class SinglePostComponent implements OnInit {
  post: PostResponse | null = null;
  loading = true;
  error = '';
  isAuthor = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private postService: PostService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (isNaN(id)) {
      this.router.navigate(['/feed']);
      return;
    }

    this.postService.getById(id).subscribe({
      next: (post) => {
        this.post = post;
        this.isAuthor = this.authService.getUsername() === post.authorUsername;
        this.loading = false;
      },
      error: () => {
        this.error = 'Post not found.';
        this.loading = false;
      }
    });
  }

  edit(): void {
    this.router.navigate(['/posts', this.post!.id, 'edit']);
  }

  delete(): void {
    if (!confirm('Delete this post? This cannot be undone.')) return;

    this.postService.delete(this.post!.id).subscribe({
      next: () => this.router.navigate(['/feed']),
      error: () => this.error = 'Failed to delete post. Please try again.'
    });
  }

  back(): void {
    this.router.navigate(['/feed']);
  }
}