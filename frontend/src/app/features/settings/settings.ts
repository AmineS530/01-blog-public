import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { FeedbackService } from '../../core/services/feedback.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class SettingsComponent implements OnInit {
  // Username state
  currentUsername = '';
  newUsername = '';
  updatingUsername = false;

  // Password state
  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  updatingPassword = false;

  constructor(
    private authService: AuthService,
    private feedback: FeedbackService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.currentUsername = this.authService.getUsername() ?? '';
    this.newUsername = this.currentUsername;
  }

  updateUsername(): void {
    const trimmedUsername = this.newUsername.trim();
    if (!trimmedUsername) {
      this.feedback.showToast('Username cannot be empty', 'error');
      return;
    }

    if (trimmedUsername === this.currentUsername) {
      this.feedback.showToast('New username is the same as the current username', 'info');
      return;
    }

    this.updatingUsername = true;
    this.authService.changeUsername({ newUsername: trimmedUsername }).subscribe({
      next: (res) => {
        this.updatingUsername = false;
        this.currentUsername = res.username;
        this.newUsername = res.username;
        this.feedback.showToast('Username updated successfully!', 'success');
      },
      error: (err) => {
        this.updatingUsername = false;
        const errMsg = err.error?.message || 'Failed to update username';
        this.feedback.showToast(errMsg, 'error');
      },
    });
  }

  updatePassword(): void {
    if (!this.currentPassword) {
      this.feedback.showToast('Please enter your current password', 'error');
      return;
    }

    if (!this.newPassword) {
      this.feedback.showToast('Please enter a new password', 'error');
      return;
    }

    if (this.newPassword.length < 6) {
      this.feedback.showToast('New password must be at least 6 characters long', 'error');
      return;
    }

    if (this.newPassword !== this.confirmPassword) {
      this.feedback.showToast('Passwords do not match', 'error');
      return;
    }

    this.updatingPassword = true;
    this.authService.changePassword({
      currentPassword: this.currentPassword,
      newPassword: this.newPassword
    }).subscribe({
      next: () => {
        this.updatingPassword = false;
        this.currentPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
        this.feedback.showToast('Password updated successfully!', 'success');
      },
      error: (err) => {
        this.updatingPassword = false;
        const errMsg = err.error?.message || 'Failed to update password';
        this.feedback.showToast(errMsg, 'error');
      },
    });
  }
}
