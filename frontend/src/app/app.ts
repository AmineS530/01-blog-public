import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatBadgeModule } from '@angular/material/badge';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService } from './core/services/auth.service';
import { NotificationService } from './core/services/notification.service';
import { ThemeService } from './core/services/theme.service';
import { FeedbackService } from './core/services/feedback.service';
import { MessageService } from './core/services/message.service';
import { RealtimeService } from './core/services/realtime.service';
import { NotificationsComponent } from './features/notifications/notifications';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, 
    RouterOutlet, 
    MatToolbarModule, 
    MatButtonModule, 
    MatIconModule, 
    MatTooltipModule,
    MatBadgeModule,
    MatMenuModule,
    MatDividerModule,
    NotificationsComponent
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  // ==========================================
  // State Properties
  // ==========================================
  
  /** Total count of unread user notifications. */
  unreadCount: number = 0;
  
  /** Total count of unread chat messages. */
  unreadMessagesCount: number = 0;
  
  /** Controls the visibility of the modal notifications overlay. */
  showNotificationsOverlay: boolean = false;

  constructor(
    public authService: AuthService, 
    private notificationService: NotificationService,
    public themeService: ThemeService,
    public feedbackService: FeedbackService,
    private messageService: MessageService,
    private realtimeService: RealtimeService,
    private router: Router
  ) {}

  // ==========================================
  // Lifecycle Hooks
  // ==========================================

  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.fetchUnreadCount();
      this.fetchUnreadMessagesCount();
      this.setupRealtimeMessages();
    }
  }

  // ==========================================
  // Counter & Realtime Streams Setup
  // ==========================================

  /**
   * Fetches the current unread notifications count from the backend service.
   */
  fetchUnreadCount(): void {
    this.notificationService.getUnreadCount().subscribe({
      next: (res) => this.unreadCount = res.count,
      error: (err) => console.error('Failed to fetch unread count', err)
    });
  }

  /**
   * Fetches the current unread direct chat messages count from the backend service.
   */
  fetchUnreadMessagesCount(): void {
    this.messageService.getUnreadCount().subscribe({
      next: (res) => this.unreadMessagesCount = res.count,
      error: (err) => console.error('Failed to fetch unread messages count', err)
    });
  }

  /**
   * Subscribes to the realtime message stream to increment unread count 
   * when a new message is received and the user is not actively viewing the chat view.
   */
  setupRealtimeMessages(): void {
    this.realtimeService.messages$.subscribe({
      next: (msg) => {
        const isRecipient = msg.recipientPublicId === this.authService.getPublicId();
        const isNotOnChatRoute = this.router.url !== '/chat';
        if (isRecipient && isNotOnChatRoute) {
          this.unreadMessagesCount++;
        }
      }
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
    this.router.navigate(["/admin"]);
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

  /** Terminates user session and redirects to the login screen. */
  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
