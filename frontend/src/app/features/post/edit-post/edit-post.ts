import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PostService } from '../../../core/services/post.service';

@Component({
  selector: 'app-edit-post',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatInputModule,
    MatButtonModule,
    MatToolbarModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './edit-post.html',
  styleUrl: './edit-post.css'
})
export class EditPostComponent implements OnInit {
  postForm: FormGroup;
  loading = true;
  submitting = false;
  error = '';
  postId!: number;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private postService: PostService
  ) {
    this.postForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(100)]],
      content: ['', [Validators.required, Validators.maxLength(5000)]]
    });
  }

  ngOnInit(): void {
    this.postId = Number(this.route.snapshot.paramMap.get('id'));
    if (isNaN(this.postId)) {
      this.router.navigate(['/feed']);
      return;
    }

    this.postService.getById(this.postId).subscribe({
      next: (post) => {
        this.postForm.patchValue({
          title: post.title,
          content: post.content
        });
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load post.';
        this.loading = false;
      }
    });
  }

  onSubmit(): void {
    if (this.postForm.invalid) return;

    this.submitting = true;
    this.error = '';

    this.postService.update(this.postId, this.postForm.value).subscribe({
      next: () => this.router.navigate(['/posts', this.postId]),
      error: () => {
        this.error = 'Failed to update post. Please try again.';
        this.submitting = false;
      }
    });
  }

  cancel(): void {
    this.router.navigate(['/posts', this.postId]);
  }
}