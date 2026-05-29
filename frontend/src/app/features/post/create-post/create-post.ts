import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
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
import { ProfileService } from '../../../core/services/profile.service';

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
    MatTooltipModule
  ],
  templateUrl: './create-post.html',
  styleUrl: './create-post.css',
})
export class CreatePostComponent implements OnInit {
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
    private profileService: ProfileService
  ) {
    this.postForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(100)]],
      content: ['', [Validators.required, Validators.maxLength(5000)]]
    });
  }

  ngOnInit(): void {
    const username = this.authService.getUsername();
    if (username) {
      this.profileService.getProfile(username).subscribe({
        next: (profile) => {
          this.currentUserAvatarUrl = profile.avatarUrl;
          this.currentUserDisplayName = profile.displayName || profile.username;
        },
        error: (err) => console.error('Failed to load profile for creator avatar', err)
      });
    }
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
        this.router.navigate(['/feed']);
      },
      error: (err) => {
        this.error = 'Failed to create post. Please try again.';
        this.submitting = false;
        console.error(err);
      }
    });
  }

  cancel() {
    this.router.navigate(['/feed']);
  }
}
