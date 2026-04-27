import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PostService } from '../../../core/services/post.service';

@Component({
  selector: 'app-create-post',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatInputModule,
    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatTooltipModule
  ],
  templateUrl: './create-post.html',
  styleUrl: './create-post.css',
})
export class CreatePostComponent {
  postForm: FormGroup;
  error = '';
  submitting = false;

  constructor(
    private fb: FormBuilder,
    private postService: PostService,
    private router: Router
  ) {
    this.postForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(100)]],
      content: ['', [Validators.required, Validators.maxLength(5000)]]
    });
  }

  onSubmit() {
    if (this.postForm.invalid) {
      return;
    }

    this.submitting = true;
    this.error = '';

    this.postService.create(this.postForm.value).subscribe({
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
