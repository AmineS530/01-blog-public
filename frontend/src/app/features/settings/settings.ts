import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { FeedbackService } from '../../core/services/feedback.service';
import { CooldownResponse } from '../../shared/models/auth.models';

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
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
})
export class SettingsComponent implements OnInit, OnDestroy {
  // Username state
  currentUsername = '';
  newUsername = '';
  updatingUsername = false;

  // Cooldown state — drives the countdown UI and button disable
  cooldown: CooldownResponse | null = null;
  cooldownActive = false;
  cooldownDaysRemaining = 0;
  cooldownHoursRemaining = 0;
  cooldownNextAllowedAt = '';
  private cooldownSubscription?: Subscription;
  private cooldownTick?: ReturnType<typeof setInterval>;

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
    // Mirror the current username from the cached profile (already loaded by
    // app.ts / feed — no extra HTTP call).
    const profile = this.authService.getCachedProfile();
    if (profile) {
      this.currentUsername = profile.username;
      this.newUsername = profile.username;
    } else {
      this.currentUsername = this.authService.getUsername() ?? '';
      this.newUsername = this.currentUsername;
    }

    // Subscribe to the cooldown observable so the UI updates reactively
    // whenever the cache is populated or refreshed (e.g. after a rename).
    this.cooldownSubscription = this.authService.cooldown$.subscribe((c) => {
      if (c) this.applyCooldown(c);
    });

    // Kick a one-time load if the cache is cold.
    if (!this.authService.getCachedCooldown()) {
      this.authService.loadUsernameCooldown().subscribe({
        next: (c) => { if (c) this.applyCooldown(c); },
        error: (err) => console.error('Failed to load username cooldown', err),
      });
    }

    // Tick every minute to keep the countdown accurate.
    this.cooldownTick = setInterval(() => {
      if (this.cooldown) this.applyCooldown(this.cooldown);
    }, 60_000);
  }

  ngOnDestroy(): void {
    this.cooldownSubscription?.unsubscribe();
    if (this.cooldownTick) clearInterval(this.cooldownTick);
  }

  /**
   * Recomputes cooldownActive / daysRemaining / hoursRemaining /
   * nextAllowedAt from the backend response. Called on every emission
   * of cooldown$ and on a 1-minute timer tick.
   */
  private applyCooldown(c: CooldownResponse): void {
    this.cooldown = c;

    if (!c.nextAllowedAt) {
      // User has never changed their username — first change is free.
      this.cooldownActive = false;
      this.cooldownDaysRemaining = 0;
      this.cooldownHoursRemaining = 0;
      this.cooldownNextAllowedAt = '';
      return;
    }

    const now = Date.now();
    const next = new Date(c.nextAllowedAt).getTime();
    const remainingMs = next - now;

    if (remainingMs <= 0) {
      this.cooldownActive = false;
      this.cooldownDaysRemaining = 0;
      this.cooldownHoursRemaining = 0;
      this.cooldownNextAllowedAt = '';
    } else {
      this.cooldownActive = true;
      const totalMinutes = Math.ceil(remainingMs / 60_000);
      this.cooldownDaysRemaining = Math.floor(totalMinutes / (60 * 24));
      this.cooldownHoursRemaining = Math.floor((totalMinutes / 60) % 24);
      this.cooldownNextAllowedAt = new Date(c.nextAllowedAt).toLocaleDateString(undefined, {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      });
    }
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

    if (this.cooldownActive) {
      this.feedback.showToast(
        `You can change your username again on ${this.cooldownNextAllowedAt}`,
        'error'
      );
      return;
    }

    const usernamePattern = /^[a-zA-Z0-9_-]{3,30}$/;
    if (!usernamePattern.test(trimmedUsername)) {
      this.feedback.showToast(
        'Username must be between 3 and 30 characters and contain only letters, numbers, underscores, and hyphens',
        'error'
      );
      return;
    }

    this.updatingUsername = true;
    this.authService.changeUsername({ newUsername: trimmedUsername }).subscribe({
      next: (res) => {
        this.updatingUsername = false;
        this.currentUsername = res.username;
        this.newUsername = res.username;
        // Cooldown cache is automatically refreshed by AuthService.changeUsername tap.
        this.feedback.showToast('Username updated successfully!', 'success');
      },
      error: (err) => {
        this.updatingUsername = false;
        // The backend returns { error, status, timestamp, nextAllowedAt? }.
        // Prefer nextAllowedAt for a richer cooldown message; fall back to
        // the generic error key (not "message" — the backend key is "error").
        const nextAllowed = err.error?.nextAllowedAt;
        const backendMsg = err.error?.error || err.error?.message;
        if (nextAllowed) {
          const date = new Date(nextAllowed).toLocaleDateString(undefined, {
            year: 'numeric', month: 'long', day: 'numeric',
          });
          this.feedback.showToast(
            `Username change on cooldown — try again on ${date}`,
            'error'
          );
        } else {
          this.feedback.showToast(backendMsg || 'Failed to update username', 'error');
        }
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
        // Backend uses key "error", not "message"
        const errMsg = err.error?.error || err.error?.message || 'Failed to update password';
        this.feedback.showToast(errMsg, 'error');
      },
    });
  }
}
