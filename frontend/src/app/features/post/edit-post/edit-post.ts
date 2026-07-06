import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PostService } from '../../../core/services/post.service';
import { MediaService } from '../../../core/services/media.service';
import { AuthService } from '../../../core/services/auth.service';
import { FeedbackService } from '../../../core/services/feedback.service';
import { MarkdownPipe } from '../../../shared/pipes/markdown.pipe';

@Component({
  selector: 'app-edit-post',
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
    MatProgressSpinnerModule,
    MarkdownPipe,
  ],
  templateUrl: './edit-post.html',
  styleUrl: './edit-post.css',
})
export class EditPostComponent implements OnInit {
  editorTab: 'write' | 'preview' = 'write';
  postForm: FormGroup;
  loading = true;
  submitting = false;
  uploadingMedia = false;
  error = '';
  publicId!: string;
  mediaUrl: string | null = null;
  
  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private postService: PostService,
    private mediaService: MediaService,
    private authService: AuthService,
    private feedback: FeedbackService,
    private location: Location
  ) {
    this.postForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(100)]],
      content: ['', [Validators.required, Validators.maxLength(5000)]],
    });
  }

  ngOnInit(): void {
    this.publicId = this.route.snapshot.paramMap.get('publicId') ?? '';
    if (!this.publicId) {
      this.router.navigate(['/feed']);
      return;
    }

    this.postService.getById(this.publicId).subscribe({
      next: (post) => {
        const currentUsername = this.authService.getUsername();
        if (!currentUsername || post.authorUsername !== currentUsername) {
          this.router.navigate(['/posts', this.publicId]);
          this.feedback.showToast('You are not authorized to edit this post.', 'error');
          return;
        }
        this.postForm.patchValue({
          title: post.title,
          content: post.content,
        });
        this.mediaUrl = post.mediaUrl || null;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load post.';
        this.loading = false;
      },
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
      },
    });
  }

  removeMedia(): void {
    this.mediaUrl = null;
  }

  onSubmit(): void {
    if (this.postForm.invalid || this.uploadingMedia) return;

    this.submitting = true;
    this.error = '';

    const postData = {
      ...this.postForm.value,
      mediaUrl: this.mediaUrl,
    };

    this.postService.update(this.publicId, postData).subscribe({
      next: () => this.router.navigate(['/posts', this.publicId]),
      error: () => {
        this.error = 'Failed to update post. Please try again.';
        this.submitting = false;
      },
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

  cancel(): void {
    this.location.back();
  }
}
