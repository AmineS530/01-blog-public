import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PostService } from '../../../core/services/post.service';
import { AuthService } from '../../../core/services/auth.service';
import { MediaService } from '../../../core/services/media.service';
import { PostResponse, CommentResponse } from '../../../shared/models/post.models';

@Component({
  selector: 'app-single-post',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  templateUrl: './single-post.html',
  styleUrl: './single-post.css'
})
export class SinglePostComponent implements OnInit {
  post: PostResponse | null = null;
  loading = true;
  error = '';
  isAuthor = false;
  currentUsername = '';

  commentForm: FormGroup;
  comments: CommentResponse[] = [];

  editingCommentId: number | null = null;
  editCommentForm: FormGroup;

  commentMediaUrl: string | null = null;
  uploadingCommentMedia = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private postService: PostService,
    private authService: AuthService,
    private mediaService: MediaService,
    private fb: FormBuilder
  ) {
    this.commentForm = this.fb.group({
      content: ['', [Validators.required, Validators.maxLength(1000)]]
    });
    this.editCommentForm = this.fb.group({
      content: ['', [Validators.required, Validators.maxLength(1000)]]
    });
  }

  ngOnInit(): void {
    this.currentUsername = this.authService.getUsername() ?? '';
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (isNaN(id)) {
      this.router.navigate(['/feed']);
      return;
    }

    this.postService.getById(id).subscribe({
      next: (post) => {
        this.post = post;
        this.isAuthor = this.currentUsername === post.authorUsername;
        this.loadComments(id);
        this.loading = false;
      },
      error: () => {
        this.error = 'Post not found.';
        this.loading = false;
      }
    });
  }

  loadComments(postId: number): void {
    this.postService.getComments(postId).subscribe(comments => {
      this.comments = comments;
    });
  }

  onCommentFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;

    this.uploadingCommentMedia = true;
    this.mediaService.upload(file).subscribe({
      next: (res) => {
        this.commentMediaUrl = res.url;
        this.uploadingCommentMedia = false;
      },
      error: () => {
        alert('Failed to upload comment media');
        this.uploadingCommentMedia = false;
      }
    });
  }

  removeCommentMedia(): void {
    this.commentMediaUrl = null;
  }

  submitComment(): void {
    if (this.commentForm.invalid || !this.post || this.uploadingCommentMedia) return;

    const request = {
      ...this.commentForm.value,
      mediaUrl: this.commentMediaUrl
    };

    this.postService.addComment(this.post.id, request).subscribe({
      next: (comment) => {
        this.comments.push(comment);
        this.post!.commentCount++;
        this.commentForm.reset();
        this.commentMediaUrl = null;
      }
    });
  }

  startEditComment(comment: CommentResponse): void {
    this.editingCommentId = comment.id;
    this.editCommentForm.patchValue({ content: comment.content });
  }

  cancelEditComment(): void {
    this.editingCommentId = null;
    this.editCommentForm.reset();
  }

  saveEditComment(comment: CommentResponse): void {
    if (this.editCommentForm.invalid) return;

    this.postService.updateComment(comment.id, this.editCommentForm.value).subscribe({
      next: (updatedComment) => {
        const index = this.comments.findIndex(c => c.id === updatedComment.id);
        if (index !== -1) {
          this.comments[index] = updatedComment;
        }
        this.cancelEditComment();
      },
      error: () => alert('Failed to update comment')
    });
  }

  deleteComment(commentId: number): void {
    if (!confirm('Delete this comment?')) return;

    this.postService.deleteComment(commentId).subscribe({
      next: () => {
        this.comments = this.comments.filter(c => c.id !== commentId);
        if (this.post) this.post.commentCount--;
      },
      error: () => alert('Failed to delete comment')
    });
  }

  togglePostLike(): void {
    if (!this.post) return;
    this.post.isLikedByCurrentUser = !this.post.isLikedByCurrentUser;
    this.post.likeCount += this.post.isLikedByCurrentUser ? 1 : -1;
    this.postService.togglePostLike(this.post.id).subscribe({
      error: () => {
        this.post!.isLikedByCurrentUser = !this.post!.isLikedByCurrentUser;
        this.post!.likeCount += this.post!.isLikedByCurrentUser ? 1 : -1;
      }
    });
  }

  toggleCommentLike(comment: CommentResponse): void {
    comment.isLikedByCurrentUser = !comment.isLikedByCurrentUser;
    comment.likeCount += comment.isLikedByCurrentUser ? 1 : -1;
    this.postService.toggleCommentLike(comment.id).subscribe({
      error: () => {
        comment.isLikedByCurrentUser = !comment.isLikedByCurrentUser;
        comment.likeCount += comment.isLikedByCurrentUser ? 1 : -1;
      }
    });
  }

  edit(): void {
    this.router.navigate(['/posts', this.post!.id, 'edit']);
  }

  goToProfile(username: string): void {
    this.router.navigate(['/profile', username]);
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