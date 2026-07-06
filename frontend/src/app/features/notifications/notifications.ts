import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import {
  NotificationService,
  NotificationResponse,
} from '../../core/services/notification.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule, MatListModule, MatIconModule, MatButtonModule, MatDividerModule],
  template: `
    <div class="notifications-container">
      <div class="header">
        <h1>Notifications</h1>
        <div class="header-actions">
          <button mat-button color="primary" (click)="markAllAsRead()">Mark all as read</button>
          <button mat-icon-button class="close-btn" (click)="close.emit()" matTooltip="Close">
            <mat-icon>close</mat-icon>
          </button>
        </div>
      </div>

      <mat-list>
        <div *ngFor="let n of notifications">
          <mat-list-item [class.unread]="!n.isRead" (click)="onNotificationClick(n)">
            <mat-icon matListItemIcon color="accent">{{ getIcon(n.type) }}</mat-icon>
            <div matListItemTitle>{{ n.message }}</div>
            <div matListItemLine>{{ n.createdAt | date: 'short' }}</div>
          </mat-list-item>
          <mat-divider></mat-divider>
        </div>
      </mat-list>

      <div *ngIf="notifications.length === 0" class="empty-state">
        <mat-icon>notifications_none</mat-icon>
        <p>No notifications yet</p>
      </div>
    </div>
  `,
  styles: [
    `
      .notifications-container {
        max-width: 600px;
        margin: 20px auto;
        background: var(--mat-sys-surface-container-low);
        border-radius: 8px;
        padding: 16px;
        box-shadow: none;
        border: 1px solid var(--mat-sys-outline-variant);
      }
      .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
      }
      .header h1 {
        margin: 0;
        font-size: 24px;
        font-weight: 700;
        color: var(--mat-sys-on-surface);
      }
      .header-actions {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .close-btn {
        color: var(--mat-sys-on-surface-variant);
      }
      .unread {
        background-color: rgba(var(--mat-sys-primary-rgb), 0.04);
        border-left: 3px solid var(--mat-sys-primary);
      }
      .empty-state {
        text-align: center;
        padding: 40px;
        color: var(--mat-sys-on-surface-variant);
      }
      .empty-state mat-icon {
        font-size: 48px;
        width: 48px;
        height: 48px;
        margin-bottom: 8px;
        opacity: 0.5;
      }
    `,
  ],
})
export class NotificationsComponent implements OnInit {
  // ==========================================
  // Properties
  // ==========================================

  /** List of loaded notifications for the logged-in user. */
  notifications: NotificationResponse[] = [];

  /** Emits an event requesting the parent component to dismiss/close the overlay panel. */
  @Output() close = new EventEmitter<void>();

  constructor(
    private notificationService: NotificationService,
    private router: Router,
  ) {}

  // ==========================================
  // Lifecycle Hooks
  // ==========================================

  ngOnInit(): void {
    this.notificationService.notifications$.subscribe((res) => {
      this.notifications = res;
    });
  }

  // ==========================================
  // Notification Retrieval & Actions
  // ==========================================

  /**
   * Loads all notifications from the database.
   */
  loadNotifications(): void {
    this.notificationService.getNotifications().subscribe({
      next: (res) => {
        this.notifications = res;
      },
      error: (err) => console.error('Failed to load notifications', err),
    });
  }

  /**
   * Marks all current notifications as read both in the local UI state and in the backend.
   */
  markAllAsRead(): void {
    this.notificationService.markAllAsRead().subscribe({
      next: () => {
        this.notifications.forEach((n) => (n.isRead = true));
      },
      error: (err) => console.error('Failed to mark all notifications as read', err),
    });
  }

  /**
   * Triggered upon selecting a notification row. Marks single item as read,
   * requests modal dismissal, and routes to associated post details if present.
   *
   * @param n Selected notification response payload.
   */
  onNotificationClick(n: NotificationResponse): void {
    if (!n.isRead) {
      this.notificationService.markAsRead(n.id).subscribe({
        next: () => {
          n.isRead = true;
        },
        error: (err) => console.error(`Failed to mark notification ${n.id} as read`, err),
      });
    }
    this.close.emit();
    if (n.postId) {
      this.router.navigate(['/posts', n.postId]);
    }
  }

  /**
   * Selects an appropriate Material Icon identifier based on notification action type.
   *
   * @param type Notification event type.
   */
  getIcon(type: string): string {
    switch (type) {
      case 'FOLLOW':
        return 'person_add';
      case 'LIKE':
        return 'favorite';
      case 'COMMENT':
        return 'comment';
      case 'POST':
        return 'post_add';
      default:
        return 'notifications';
    }
  }
}
