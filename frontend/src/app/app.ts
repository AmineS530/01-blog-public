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

  /** Total count of unread user notifications. */
  unreadCount: number = 0;

  /** Total count of unread chat messages. */
  unreadMessagesCount: number = 0;

  /** Controls the visibility of the modal notifications overlay. */
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
  ) {}

  // ==========================================
  // Lifecycle Hooks
  // ==========================================

  ngOnInit(): void {
    this.checkBackendConnection();
  }

  ngOnDestroy(): void {
    this.authSubscription?.unsubscribe();
    this.messageSubscription?.unsubscribe();
    this.profileSubscription?.unsubscribe();
    this.clearCountdown();
  }

  // ==========================================
  // Connection Checking Logic
  // ==========================================

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
          // If the backend responded with any HTTP status other than 0 (e.g. 401, 403, 404, 500),
          // it means the server is running and we are successfully connected!
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

    // Mirror the cached current profile into the navbar view, reactively,
    // without ever issuing a second HTTP call. Profile is loaded lazily by
    // the only call site (state below) which kicks fetchMyProfile exactly once.
    this.profileSubscription = this.authService.profile$.subscribe((profile) => {
      this.currentUserAvatarUrl = profile?.avatarUrl ?? null;
      this.currentUserDisplayName = profile?.displayName || profile?.username || '';
    });

    this.authSubscription = this.authService.loggedIn$.subscribe((loggedIn) => {
      if (loggedIn) {
        this.fetchUnreadCount();
        this.fetchUnreadMessagesCount();
        this.setupRealtimeMessages();
        // Make sure the cached profile is loaded exactly once. Will skip fetching
        // if a previous load (e.g. another component calling it) already populated
        // the cache. Either way: only one HTTP call to /api/profiles/me.
        // Cold-start note: at this point getUsername() may still be null because
        // the username only becomes known after /me resolves — that's fine,
        // loadCurrentUser has its own guard.
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

  // ==========================================
  // Counter & Realtime Streams Setup
  // ==========================================

  /**
   * Fetches the current unread notifications count from the backend service.
   */
  fetchUnreadCount(): void {
    this.notificationService.getUnreadCount().subscribe({
      next: (res) => (this.unreadCount = res.count),
      error: (err) => console.error('Failed to fetch unread count', err),
    });
  }

  /**
   * Fetches the current unread direct chat messages count from the backend service.
   */
  fetchUnreadMessagesCount(): void {
    this.messageService.getUnreadCount().subscribe({
      next: (res) => (this.unreadMessagesCount = res.count),
      error: (err) => console.error('Failed to fetch unread messages count', err),
    });
  }

  /**
   * Subscribes to the realtime message stream to increment unread count
   * when a new message is received and the user is not actively viewing the chat view.
   */
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

  /** Navigates to the main user feed. */
  goToFeed(): void {
    this.router.navigate(['/feed']);
  }

  /** Toggles the notifications modal overlay and refreshes unread counts. */
  goToNotifications(): void {
    this.showNotificationsOverlay = !this.showNotificationsOverlay;
    if (this.showNotificationsOverlay) {
      this.fetchUnreadCount();
    }
  }

  /** Navigates to the admin panel dashboard. */
  goToAdmin(): void {
    this.router.navigate(['/admin']);
  }

  /** Navigates to the active user's personal profile view. */
  goToMyProfile(): void {
    const username = this.authService.getUsername();
    if (username) {
      this.router.navigate(['/profile', username]);
    }
  }

  /** Navigates to the post creation editor. */
  goToCreate(): void {
    this.router.navigate(['/posts/create']);
  }

  /** Navigates to the chat view and clears the unread badge counter. */
  goToChat(): void {
    this.router.navigate(['/chat']);
    this.unreadMessagesCount = 0;
  }

  /** Navigates to the settings view. */
  goToSettings(): void {
    this.router.navigate(['/settings']);
  }

  /** Terminates user session and redirects to the login screen. */
  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
