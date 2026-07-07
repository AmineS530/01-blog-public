import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  NotificationService,
  NotificationResponse,
} from '../../core/services/notification.service';
import { Router } from '@angular/router';
import { FeedbackService } from '../../core/services/feedback.service';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [
    CommonModule, 
    MatListModule, 
    MatIconModule, 
    MatButtonModule, 
    MatDividerModule,
    MatTooltipModule
  ],
  template: `
    <div class="notifications-container">
      <div class="header">
        <h1>Notifications</h1>
        <div class="header-actions">
          <button mat-button color="primary" *ngIf="hasUnread()" (click)="markAllAsRead()">Mark all as read</button>
          <button mat-button color="warn" *ngIf="notifications.length > 0" (click)="clearAll()">Clear All</button>
          <button mat-icon-button class="close-btn" (click)="close.emit()" matTooltip="Close">
            <mat-icon>close</mat-icon>
          </button>
        </div>
      </div>

      <div class="filter-tabs" *ngIf="notifications.length > 0">
        <button class="filter-tab" [class.active]="filter === 'all'" (click)="filter = 'all'">
          All ({{ notifications.length }})
        </button>
        <button class="filter-tab" [class.active]="filter === 'unread'" (click)="filter = 'unread'">
          Unread ({{ unreadCount }})
        </button>
      </div>

      <div class="notifications-scrollable-list">
        <mat-list>
          <div *ngFor="let n of getFilteredNotifications()">
            <mat-list-item [class.unread]="!n.isRead" (click)="onNotificationClick(n)" style="cursor: pointer;">
              <mat-icon matListItemIcon color="accent">{{ getIcon(n.type) }}</mat-icon>
              <div matListItemTitle style="font-weight: 500; font-size: 14px;">{{ n.message }}</div>
              <div matListItemLine style="font-size: 12px; color: var(--mat-sys-on-surface-variant);">{{ n.createdAt | date: 'MMM d, y, h:mm a' }}</div>
              
              <button 
                mat-icon-button 
                matListItemMeta 
                (click)="toggleRead(n, $event)" 
                [matTooltip]="n.isRead ? 'Mark as unread' : 'Mark as read'"
              >
                <mat-icon class="toggle-read-icon" [class.is-unread]="!n.isRead">
                  {{ n.isRead ? 'radio_button_unchecked' : 'lens' }}
                </mat-icon>
              </button>
            </mat-list-item>
            <mat-divider></mat-divider>
          </div>
        </mat-list>

        <div *ngIf="getFilteredNotifications().length === 0" class="empty-state">
          <mat-icon>notifications_none</mat-icon>
          <p>No {{ filter === 'unread' ? 'unread' : '' }} notifications yet</p>
        </div>
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
        margin-bottom: 12px;
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
      .filter-tabs {
        display: flex;
        gap: 8px;
        margin-bottom: 16px;
        border-bottom: 1px solid var(--mat-sys-outline-variant);
        padding-bottom: 8px;
      }
      .filter-tab {
        background: transparent;
        border: none;
        color: var(--mat-sys-on-surface-variant);
        font-weight: 700;
        font-size: 13px;
        padding: 6px 12px;
        border-radius: 6px;
        cursor: pointer;
        font-family: var(--font-display);
        transition: all 0.2s ease;
      }
      .filter-tab:hover {
        background: var(--mat-sys-surface-container-high);
        color: var(--mat-sys-on-surface);
      }
      .filter-tab.active {
        background: var(--mat-sys-primary-container);
        color: var(--mat-sys-on-primary-container);
      }
      .unread {
        background-color: rgba(var(--mat-sys-primary-rgb), 0.04);
        border-left: 3px solid var(--mat-sys-primary);
      }
      .notifications-scrollable-list {
        max-height: 420px;
        overflow-y: auto;
        padding-right: 4px;
      }
      /* Custom Scrollbar for premium aesthetic */
      .notifications-scrollable-list::-webkit-scrollbar {
        width: 6px;
      }
      .notifications-scrollable-list::-webkit-scrollbar-track {
        background: transparent;
      }
      .notifications-scrollable-list::-webkit-scrollbar-thumb {
        background: var(--mat-sys-outline-variant);
        border-radius: 3px;
      }
      .notifications-scrollable-list::-webkit-scrollbar-thumb:hover {
        background: var(--mat-sys-outline);
      }
      .toggle-read-icon {
        color: var(--mat-sys-outline);
        font-size: 18px;
        width: 18px;
        height: 18px;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .toggle-read-icon.is-unread {
        color: var(--mat-sys-primary);
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
  notifications: NotificationResponse[] = [];
  filter: 'all' | 'unread' = 'all';

  @Output() close = new EventEmitter<void>();

  constructor(
    private notificationService: NotificationService,
    private router: Router,
    private feedback: FeedbackService,
  ) {}

  ngOnInit(): void {
    this.notificationService.notifications$.subscribe((res) => {
      this.notifications = res;
    });
  }

  loadNotifications(): void {
    this.notificationService.getNotifications().subscribe({
      next: (res) => {
        this.notifications = res;
      },
      error: (err) => console.error('Failed to load notifications', err),
    });
  }

  get unreadCount(): number {
    return this.notifications.filter((n) => !n.isRead).length;
  }

  hasUnread(): boolean {
    return this.notifications.some((n) => !n.isRead);
  }

  getFilteredNotifications(): NotificationResponse[] {
    if (this.filter === 'unread') {
      return this.notifications.filter((n) => !n.isRead);
    }
    return this.notifications;
  }

  markAllAsRead(): void {
    this.notificationService.markAllAsRead().subscribe({
      next: () => {
        this.notifications.forEach((n) => (n.isRead = true));
        this.feedback.showToast('All notifications marked as read', 'success');
      },
      error: (err) => console.error('Failed to mark all notifications as read', err),
    });
  }

  toggleRead(n: NotificationResponse, event: Event): void {
    event.stopPropagation();
    this.notificationService.toggleReadStatus(n.id).subscribe({
      next: () => {
        n.isRead = !n.isRead;
        this.feedback.showToast(
          n.isRead ? 'Notification marked as read' : 'Notification marked as unread',
          'success'
        );
      },
      error: (err) => console.error('Failed to toggle notification read status', err)
    });
  }

  clearAll(): void {
    this.feedback.askConfirmation({
      title: 'Clear all notifications?',
      message: 'Are you sure you want to delete all notifications? This action cannot be undone.',
      confirmText: 'Clear All',
      onConfirm: () => {
        this.notificationService.clearAllNotifications().subscribe({
          next: () => {
            this.notifications = [];
            this.feedback.showToast('All notifications cleared.', 'success');
          },
          error: (err) => {
            this.feedback.showToast('Failed to clear notifications.', 'error');
            console.error(err);
          }
        });
      }
    });
  }

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
