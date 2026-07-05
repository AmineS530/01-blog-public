import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
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
import { ReportService } from '../../../core/services/report.service';
import { FeedbackService } from '../../../core/services/feedback.service';
import { RealtimeService } from '../../../core/services/realtime.service';
import { ProfileService } from '../../../core/services/profile.service';
import { PostResponse, CommentResponse } from '../../../shared/models/post.models';
import { MarkdownPipe } from '../../../shared/pipes/markdown.pipe';
import { Subscription } from 'rxjs';

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
    MatTooltipModule,
    MarkdownPipe,
  ],
  templateUrl: './single-post.html',
  styleUrl: './single-post.css',
})
export class SinglePostComponent implements OnInit, OnDestroy {
  post: PostResponse | null = null;
  loading = true;
  error = '';
  isAuthor = false;
  isAdminOrSuperAdmin = false;
  currentUsername = '';
  currentUserAvatarUrl: string | null = null;
  currentUserDisplayName = '';

  commentForm: FormGroup;
  comments: CommentResponse[] = [];

  editingCommentId: number | null = null;
  editCommentForm: FormGroup;

  commentMediaUrl: string | null = null;
  uploadingCommentMedia = false;

  private commentsSubscription?: Subscription;
  private likesSubscription?: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private postService: PostService,
    private authService: AuthService,
    private mediaService: MediaService,
    private reportService: ReportService,
    private fb: FormBuilder,
    private feedback: FeedbackService,
    private realtimeService: RealtimeService,
    private location: Location,
    private profileService: ProfileService,
  ) {
    this.commentForm = this.fb.group({
      content: ['', [Validators.required, Validators.maxLength(1000)]],
    });
    this.editCommentForm = this.fb.group({
      content: ['', [Validators.required, Validators.maxLength(1000)]],
    });
  }

  ngOnInit(): void {
    // Reuse the cached profile populated by AuthService. The username is
    // resolved from the fetched profile (not the JWT) since the JWT's `sub`
    // claim holds the publicId, not the username.
    this.authService.ensureProfileLoaded().subscribe({
      next: (profile) => {
        if (profile) {
          this.currentUsername = profile.username;
          this.currentUserAvatarUrl = profile.avatarUrl;
          this.currentUserDisplayName = profile.displayName || profile.username;
          // If the post landed before the profile did, recompute isAuthor.
          // Without this, the edit/delete button stays hidden on cold-start.
          if (this.post) {
            this.isAuthor = profile.username === this.post.authorUsername;
          }
        }
      },
    });

    const publicId = this.route.snapshot.paramMap.get('publicId') ?? '';
    if (!publicId) {
      this.router.navigate(['/feed']);
      return;
    }

    this.postService.getById(publicId).subscribe({
      next: (post) => {
        this.post = post;
        this.isAuthor = this.currentUsername === post.authorUsername;
        const role = this.authService.getRole();
        this.isAdminOrSuperAdmin = role === 'ADMIN' || role === 'SUPER_ADMIN';
        this.loadComments(publicId);
        this.loading = false;
        this.setupRealtime(publicId);
      },
      error: () => {
        this.error = 'Post not found.';
        this.loading = false;
      },
    });
  }

  private setupRealtime(postId: string): void {
    // 1. Subscribe to like count updates
    this.likesSubscription = this.realtimeService.likes$.subscribe({
      next: (event) => {
        if (event.type === 'POST_LIKE' && event.postId === postId) {
          if (this.post) {
            this.post.likeCount = event.likeCount;
          }
        } else if (event.type === 'COMMENT_LIKE' && event.postId === postId) {
          const targetComment = this.comments.find((c) => c.id === event.commentId);
          if (targetComment) {
            targetComment.likeCount = event.likeCount;
          }
        }
      },
    });
  }

  loadComments(postId: string): void {
    this.postService.getComments(postId).subscribe((comments) => {
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
        this.feedback.showToast('Comment media uploaded!', 'success');
      },
      error: () => {
        this.feedback.showToast('Failed to upload comment media', 'error');
        this.uploadingCommentMedia = false;
      },
    });
  }

  removeCommentMedia(): void {
    this.commentMediaUrl = null;
    this.feedback.showToast('Comment media removed.', 'info');
  }

  submitComment(): void {
    if (this.commentForm.invalid || !this.post || this.uploadingCommentMedia) return;

    const request = {
      ...this.commentForm.value,
      mediaUrl: this.commentMediaUrl,
    };

    this.postService.addComment(this.post.publicId, request).subscribe({
      next: (comment) => {
        this.comments.push(comment);
        this.post!.commentCount++;
        this.commentForm.reset();
        this.commentMediaUrl = null;
        this.feedback.showToast('Comment posted!', 'success');
      },
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
        const index = this.comments.findIndex((c) => c.id === updatedComment.id);
        if (index !== -1) {
          this.comments[index] = updatedComment;
        }
        this.cancelEditComment();
        this.feedback.showToast('Comment updated!', 'success');
      },
      error: () => this.feedback.showToast('Failed to update comment', 'error'),
    });
  }

  deleteComment(commentId: number): void {
    this.feedback.askConfirmation({
      title: 'DELETE COMMENT',
      message: 'Are you sure you want to delete this comment?',
      confirmText: 'Delete',
      onConfirm: () => {
        this.postService.deleteComment(commentId).subscribe({
          next: () => {
            this.comments = this.comments.filter((c) => c.id !== commentId);
            if (this.post) this.post.commentCount--;
            this.feedback.showToast('Comment deleted successfully!', 'success');
          },
          error: () => this.feedback.showToast('Failed to delete comment', 'error'),
        });
      },
    });
  }

  togglePostLike(): void {
    if (!this.post) return;
    this.post.isLikedByCurrentUser = !this.post.isLikedByCurrentUser;
    this.post.likeCount += this.post.isLikedByCurrentUser ? 1 : -1;
    this.postService.togglePostLike(this.post.publicId).subscribe({
      error: (err) => {
        this.post!.isLikedByCurrentUser = !this.post!.isLikedByCurrentUser;
        this.post!.likeCount += this.post!.isLikedByCurrentUser ? 1 : -1;
        if (err?.status !== 429) {
          this.feedback.showToast('Failed to update post like status.', 'error');
        }
      },
    });
  }

  toggleCommentLike(comment: CommentResponse): void {
    comment.isLikedByCurrentUser = !comment.isLikedByCurrentUser;
    comment.likeCount += comment.isLikedByCurrentUser ? 1 : -1;
    this.postService.toggleCommentLike(comment.id).subscribe({
      error: (err) => {
        comment.isLikedByCurrentUser = !comment.isLikedByCurrentUser;
        comment.likeCount += comment.isLikedByCurrentUser ? 1 : -1;
        if (err?.status !== 429) {
          this.feedback.showToast('Failed to update comment like status.', 'error');
        }
      },
    });
  }

  reportPost(): void {
    if (!this.post) return;
    this.feedback.askPrompt({
      title: 'Report Post',
      message: `Why are you reporting this post by ${this.post.authorDisplayName || this.post.authorUsername}?`,
      placeholder: 'Reason for reporting',
      confirmText: 'Report',
      onConfirm: (reason) => {
        this.reportService.reportPost(this.post!.publicId, reason).subscribe({
          next: () => this.feedback.showToast('Post reported successfully.', 'success'),
          error: () => this.feedback.showToast('Failed to report post.', 'error'),
        });
      },
    });
  }

  reportComment(comment: CommentResponse): void {
    this.feedback.askPrompt({
      title: 'Report Comment',
      message: `Why are you reporting this comment by ${comment.authorDisplayName || comment.authorUsername}?`,
      placeholder: 'Reason for reporting',
      confirmText: 'Report',
      onConfirm: (reason) => {
        this.reportService.reportComment(comment.id, reason).subscribe({
          next: () => this.feedback.showToast('Comment reported successfully.', 'success'),
          error: () => this.feedback.showToast('Failed to report comment.', 'error'),
        });
      },
    });
  }

  edit(): void {
    this.router.navigate(['/posts', this.post!.publicId, 'edit']);
  }

  goToProfile(username: string): void {
    this.feedback.showMiniProfile(username, this.profileService);
  }

  delete(): void {
    this.feedback.askConfirmation({
      title: 'DELETE POST',
      message:
        'Delete this post? This action cannot be undone and will permanently remove associated media files.',
      confirmText: 'Delete',
      onConfirm: () => {
        this.postService.delete(this.post!.publicId).subscribe({
          next: () => {
            this.feedback.showToast('Post deleted successfully!', 'success');
            this.router.navigate(['/feed']);
          },
          error: () => {
            this.feedback.showToast('Failed to delete post.', 'error');
            this.error = 'Failed to delete post. Please try again.';
          },
        });
      },
    });
  }

  back(): void {
    this.location.back();
  }

  ngOnDestroy(): void {
    this.commentsSubscription?.unsubscribe();
    this.likesSubscription?.unsubscribe();
  }
}
