import { Component, OnInit, OnDestroy } from '@angular/core';
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
import { ReportService } from '../../../core/services/report.service';
import { FeedbackService } from '../../../core/services/feedback.service';
import { RealtimeService } from '../../../core/services/realtime.service';
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
    MarkdownPipe
  ],
  templateUrl: './single-post.html',
  styleUrl: './single-post.css'
})
export class SinglePostComponent implements OnInit, OnDestroy {
  post: PostResponse | null = null;
  loading = true;
  error = '';
  isAuthor = false;
  isAdminOrSuperAdmin = false;
  currentUsername = '';

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
    private realtimeService: RealtimeService
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
        const role = this.authService.getRole();
        this.isAdminOrSuperAdmin = role === 'ADMIN' || role === 'SUPER_ADMIN';
        this.loadComments(id);
        this.loading = false;
        this.setupRealtime(id);
      },
      error: () => {
        this.error = 'Post not found.';
        this.loading = false;
      }
    });
  }

  private setupRealtime(postId: number): void {
    // 1. Subscribe to new comments for this post
    this.commentsSubscription = this.realtimeService.comments$.subscribe({
      next: (payload) => {
        if (payload.postId === postId) {
          const newComment = payload.comment;
          if (newComment.authorUsername !== this.currentUsername) {
            if (!this.comments.some(c => c.id === newComment.id)) {
              this.comments.push(newComment);
              if (this.post) {
                this.post.commentCount++;
              }
            }
          }
        }
      }
    });

    // 2. Subscribe to like count updates
    this.likesSubscription = this.realtimeService.likes$.subscribe({
      next: (event) => {
        if (event.type === 'POST_LIKE' && event.postId === postId) {
          if (this.post) {
            this.post.likeCount = event.likeCount;
          }
        } else if (event.type === 'COMMENT_LIKE' && event.postId === postId) {
          const targetComment = this.comments.find(c => c.id === event.commentId);
          if (targetComment) {
            targetComment.likeCount = event.likeCount;
          }
        }
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
        this.feedback.showToast('Comment media uploaded!', 'success');
      },
      error: () => {
        this.feedback.showToast('Failed to upload comment media', 'error');
        this.uploadingCommentMedia = false;
      }
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
      mediaUrl: this.commentMediaUrl
    };

    this.postService.addComment(this.post.id, request).subscribe({
      next: (comment) => {
        this.comments.push(comment);
        this.post!.commentCount++;
        this.commentForm.reset();
        this.commentMediaUrl = null;
        this.feedback.showToast('Comment posted!', 'success');
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
        this.feedback.showToast('Comment updated!', 'success');
      },
      error: () => this.feedback.showToast('Failed to update comment', 'error')
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
            this.comments = this.comments.filter(c => c.id !== commentId);
            if (this.post) this.post.commentCount--;
            this.feedback.showToast('Comment deleted successfully!', 'success');
          },
          error: () => this.feedback.showToast('Failed to delete comment', 'error')
        });
      }
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
        this.feedback.showToast('Failed to update post like status.', 'error');
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
        this.feedback.showToast('Failed to update comment like status.', 'error');
      }
    });
  }

  reportPost(): void {
    if (!this.post) return;
    const reason = prompt(`Why are you reporting this post by ${this.post.authorUsername}?`);
    if (reason) {
      this.reportService.reportPost(this.post.id, reason).subscribe({
        next: () => this.feedback.showToast('Post reported successfully.', 'success'),
        error: () => this.feedback.showToast('Failed to report post.', 'error')
      });
    }
  }

  reportComment(comment: CommentResponse): void {
    const reason = prompt(`Why are you reporting this comment by ${comment.authorUsername}?`);
    if (reason) {
      this.reportService.reportComment(comment.id, reason).subscribe({
        next: () => this.feedback.showToast('Comment reported successfully.', 'success'),
        error: () => this.feedback.showToast('Failed to report comment.', 'error')
      });
    }
  }

  edit(): void {
    this.router.navigate(['/posts', this.post!.id, 'edit']);
  }

  goToProfile(username: string): void {
    this.router.navigate(['/profile', username]);
  }

  delete(): void {
    this.feedback.askConfirmation({
      title: 'DELETE POST',
      message: 'Delete this post? This action cannot be undone and will permanently remove associated media files.',
      confirmText: 'Delete',
      onConfirm: () => {
        this.postService.delete(this.post!.id).subscribe({
          next: () => {
            this.feedback.showToast('Post deleted successfully!', 'success');
            this.router.navigate(['/feed']);
          },
          error: () => {
            this.feedback.showToast('Failed to delete post.', 'error');
            this.error = 'Failed to delete post. Please try again.';
          }
        });
      }
    });
  }

  back(): void {
    this.router.navigate(['/feed']);
  }

  ngOnDestroy(): void {
    this.commentsSubscription?.unsubscribe();
    this.likesSubscription?.unsubscribe();
  }
}