import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PostService } from '../../../core/services/post.service';
import { MediaService } from '../../../core/services/media.service';
import { AuthService } from '../../../core/services/auth.service';
import { FeedbackService } from '../../../core/services/feedback.service';
import { MarkdownPipe } from '../../../shared/pipes/markdown.pipe';

@Component({
  selector: 'app-create-post',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatInputModule,
    MatFormFieldModule,
    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatTooltipModule,
    MarkdownPipe,
  ],
  templateUrl: './create-post.html',
  styleUrl: './create-post.css',
})
export class CreatePostComponent implements OnInit {
  editorTab: 'write' | 'preview' = 'write';
  postForm: FormGroup;
  error = '';
  submitting = false;
  mediaUrl: string | null = null;
  uploadingMedia = false;
  currentUserAvatarUrl: string | null = null;
  currentUserDisplayName = '';

  constructor(
    private fb: FormBuilder,
    private postService: PostService,
    private mediaService: MediaService,
    private router: Router,
    private authService: AuthService,
    private location: Location,
    private feedbackService: FeedbackService,
  ) {
    this.postForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(100)]],
      content: ['', [Validators.required, Validators.maxLength(5000)]]
    });
  }

  ngOnInit(): void {
    // Reuse the cached profile populated by AuthService — works for cold
    // starts too (post-refresh the JWT already proves identity, even when
    // getUsername() returns null until the profile fetch resolves).
    this.authService.ensureProfileLoaded().subscribe({
      next: (profile) => {
        if (profile) {
          this.currentUserAvatarUrl = profile.avatarUrl;
          this.currentUserDisplayName = profile.displayName || profile.username;
        }
      },
      error: (err) => console.error('Failed to load profile for creator avatar', err)
    });
  }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;

    this.uploadingMedia = true;
    this.error = '';

    this.mediaService.upload(file).subscribe({
      next: (response) => {
        this.mediaUrl = response.url;
        this.uploadingMedia = false;
      },
      error: () => {
        this.error = 'Failed to upload media. Please try again.';
        this.uploadingMedia = false;
      }
    });
  }

  removeMedia(): void {
    this.mediaUrl = null;
  }

  onSubmit() {
    if (this.postForm.invalid || this.uploadingMedia) {
      return;
    }

    this.submitting = true;
    this.error = '';

    const postData = {
      ...this.postForm.value,
      mediaUrl: this.mediaUrl
    };

    this.postService.create(postData).subscribe({
      next: () => {
        this.feedbackService.showToast('Post published successfully!', 'success');
        this.router.navigate(['/feed']);
      },
      error: (err) => {
        this.error = 'Failed to create post. Please try again.';
        this.submitting = false;
        console.error(err);
      }
    });
  }

  insertMarkdown(syntax: string, textarea: HTMLTextAreaElement): void {
    if (!textarea) return;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const text = textarea.value;
    const selectedText = text.substring(start, end);

    let replacement = '';
    let selectionOffsetStart = 0;
    let selectionOffsetEnd = 0;

    if (syntax === 'bold') {
      replacement = `**${selectedText || 'bold text'}**`;
      selectionOffsetStart = selectedText ? 0 : 2;
      selectionOffsetEnd = selectedText ? 0 : 2;
    } else if (syntax === 'italic') {
      replacement = `*${selectedText || 'italic text'}*`;
      selectionOffsetStart = selectedText ? 0 : 1;
      selectionOffsetEnd = selectedText ? 0 : 1;
    } else if (syntax === 'link') {
      replacement = `[${selectedText || 'link text'}](https://)`;
      selectionOffsetStart = selectedText ? 0 : 1;
      selectionOffsetEnd = selectedText ? 0 : 12;
    } else if (syntax === 'list') {
      replacement = `\n- ${selectedText || 'list item'}`;
      selectionOffsetStart = selectedText ? 0 : 3;
    } else if (syntax === 'numlist') {
      replacement = `\n1. ${selectedText || 'list item'}`;
      selectionOffsetStart = selectedText ? 0 : 4;
    } else if (syntax === 'code') {
      replacement = `\n\`\`\`\n${selectedText || 'code'}\n\`\`\`\n`;
      selectionOffsetStart = selectedText ? 0 : 5;
      selectionOffsetEnd = selectedText ? 0 : 4;
    }

    const newValue = text.substring(0, start) + replacement + text.substring(end);
    this.postForm.patchValue({ content: newValue });

    setTimeout(() => {
      textarea.focus();
      if (selectedText) {
        textarea.setSelectionRange(start + replacement.length, start + replacement.length);
      } else {
        textarea.setSelectionRange(start + selectionOffsetStart, start + replacement.length - selectionOffsetEnd);
      }
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

  cancel() {
    this.location.back();
  }
}
