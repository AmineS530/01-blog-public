import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from './core/services/auth.service';
import { NotificationService } from './core/services/notification.service';
import { ThemeService } from './core/services/theme.service';
import { FeedbackService } from './core/services/feedback.service';
import { MessageService } from './core/services/message.service';
import { RealtimeService } from './core/services/realtime.service';
import { ProfileService } from './core/services/profile.service';
import { NotificationsComponent } from './features/notifications/notifications';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    FormsModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatBadgeModule,
    MatMenuModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    NotificationsComponent,
  ],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit, OnDestroy {
  // ==========================================
  // Connection state
  // ==========================================
  isBackendConnected = false;
  checkingConnection = true;
  retrying = false;
  reconnectCountdown = 3;
  private countdownTimer?: any;
  private hasInitialized = false;

  // ==========================================
  // State Properties
  // ==========================================
  unreadCount: number = 0;
  unreadMessagesCount: number = 0;
  showNotificationsOverlay: boolean = false;
  currentUserAvatarUrl: string | null = null;
  currentUserDisplayName = '';

  private authSubscription?: Subscription;
  private messageSubscription?: Subscription;
  private profileSubscription?: Subscription;

  constructor(
    public authService: AuthService,
    private notificationService: NotificationService,
    public themeService: ThemeService,
    public feedbackService: FeedbackService,
    private messageService: MessageService,
    private realtimeService: RealtimeService,
    private router: Router,
    private profileService: ProfileService,
  ) {}

  ngOnInit(): void {
    this.checkBackendConnection();
  }

  ngOnDestroy(): void {
    this.authSubscription?.unsubscribe();
    this.messageSubscription?.unsubscribe();
    this.profileSubscription?.unsubscribe();
    this.clearCountdown();
  }

  checkBackendConnection(isManual = false): void {
    if (isManual) {
      this.retrying = true;
    }
    this.authService.checkConnection().subscribe({
      next: () => {
        this.handleConnectionSuccess();
      },
      error: (err) => {
        if (err.status === 0) {
          this.handleConnectionFailure();
        } else {
          this.handleConnectionSuccess();
        }
      }
    });
  }

  private handleConnectionSuccess(): void {
    this.isBackendConnected = true;
    this.checkingConnection = false;
    this.retrying = false;
    this.clearCountdown();
    this.initializeAfterConnection();
  }

  private handleConnectionFailure(): void {
    this.isBackendConnected = false;
    this.checkingConnection = false;
    this.retrying = false;
    this.startCountdown();
  }

  private startCountdown(): void {
    if (this.countdownTimer) return;
    this.reconnectCountdown = 3;
    this.countdownTimer = setInterval(() => {
      this.reconnectCountdown--;
      if (this.reconnectCountdown <= 0) {
        this.clearCountdown();
        this.checkBackendConnection();
      }
    }, 1000);
  }

  private clearCountdown(): void {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = undefined;
    }
  }

  retryConnection(): void {
    this.clearCountdown();
    this.checkBackendConnection(true);
  }

  private initializeAfterConnection(): void {
    if (this.hasInitialized) return;
    this.hasInitialized = true;

    this.profileSubscription = this.authService.profile$.subscribe((profile) => {
      this.currentUserAvatarUrl = profile?.avatarUrl ?? null;
      this.currentUserDisplayName = profile?.displayName || profile?.username || '';
    });

    this.authSubscription = this.authService.loggedIn$.subscribe((loggedIn) => {
      if (loggedIn) {
        this.fetchUnreadCount();
        this.fetchUnreadMessagesCount();
        this.setupRealtimeMessages();
        if (!this.authService.getCachedProfile()) {
          this.authService.loadCurrentUser().subscribe({
            error: (err) => console.error('Failed to rehydrate current user', err),
          });
        }
      } else {
        this.unreadCount = 0;
        this.unreadMessagesCount = 0;
        this.currentUserAvatarUrl = null;
        this.currentUserDisplayName = '';
        if (this.messageSubscription) {
          this.messageSubscription.unsubscribe();
          this.messageSubscription = undefined;
        }
        this.authService.invalidateProfileCache();
      }
    });
  }

  fetchUnreadCount(): void {
    this.notificationService.getUnreadCount().subscribe({
      next: (res) => (this.unreadCount = res.count),
      error: (err) => console.error('Failed to fetch unread count', err),
    });
  }

  fetchUnreadMessagesCount(): void {
    this.messageService.getUnreadCount().subscribe({
      next: (res) => (this.unreadMessagesCount = res.count),
      error: (err) => console.error('Failed to fetch unread messages count', err),
    });
  }

  setupRealtimeMessages(): void {
    if (this.messageSubscription) {
      this.messageSubscription.unsubscribe();
    }
    this.messageSubscription = this.realtimeService.messages$.subscribe({
      next: (msg) => {
        const isRecipient = msg.recipientPublicId === this.authService.getPublicId();
        const isNotOnChatRoute = this.router.url !== '/chat';
        if (isRecipient && isNotOnChatRoute) {
          this.unreadMessagesCount++;
        }
      },
    });
  }

  // ==========================================
  // Navigation & Action Handlers
  // ==========================================

  goToFeed(): void {
    this.router.navigate(['/feed']);
  }

  goToNotifications(): void {
    this.showNotificationsOverlay = !this.showNotificationsOverlay;
    if (this.showNotificationsOverlay) {
      this.fetchUnreadCount();
    }
  }

  goToAdmin(): void {
    this.router.navigate(['/admin']);
  }

  goToMyProfile(): void {
    const username = this.authService.getUsername();
    if (username) {
      this.router.navigate(['/profile', username]);
    }
  }

  goToCreate(): void {
    this.router.navigate(['/posts/create']);
  }

  goToChat(): void {
    this.router.navigate(['/chat']);
    this.unreadMessagesCount = 0;
  }

  goToSettings(): void {
    this.router.navigate(['/settings']);
  }

  goToUsers(): void {
    this.router.navigate(['/users']);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // ==========================================
  // Mini Profile Interactive Actions
  // ==========================================

  viewFullProfile(username: string): void {
    this.feedbackService.closeMiniProfile();
    this.router.navigate(['/profile', username]);
  }

  toggleMiniFollow(): void {
    const state = this.feedbackService.miniProfileState;
    if (!state.profile) return;
    const originalFollowing = state.profile.isFollowing;
    
    state.profile.isFollowing = !originalFollowing;
    state.profile.followerCount += state.profile.isFollowing ? 1 : -1;

    this.profileService.toggleFollow(state.profile.username).subscribe({
      next: () => {
        this.feedbackService.showToast(
          state.profile.isFollowing ? `You followed @${state.profile.username}` : `You unfollowed @${state.profile.username}`,
          'success'
        );
      },
      error: (err) => {
        state.profile.isFollowing = originalFollowing;
        state.profile.followerCount += originalFollowing ? 1 : -1;
        if (err?.status !== 429) {
          this.feedbackService.showToast('Failed to update follow status.', 'error');
        }
      }
    });
  }

  toggleMiniBlock(): void {
    const state = this.feedbackService.miniProfileState;
    if (!state.profile) return;
    const originalBlocked = state.profile.isBlocked;
    
    state.profile.isBlocked = !originalBlocked;
    if (state.profile.isBlocked) {
      state.profile.isFollowing = false;
    }

    this.profileService.toggleBlock(state.profile.username).subscribe({
      next: () => {
        this.feedbackService.showToast(
          state.profile.isBlocked ? `You blocked @${state.profile.username}` : `You unblocked @${state.profile.username}`,
          'success'
        );
        this.feedbackService.closeMiniProfile();
      },
      error: () => {
        state.profile.isBlocked = originalBlocked;
        this.feedbackService.showToast('Failed to update block status.', 'error');
      }
    });
  }

  messageMiniUser(profile: any): void {
    this.feedbackService.closeMiniProfile();
    // Redirect to chat and select the user if ChatComponent supports it
    this.router.navigate(['/chat'], { queryParams: { user: profile.publicId, username: profile.username, displayName: profile.displayName || profile.username, avatarUrl: profile.avatarUrl } });
  }
}
