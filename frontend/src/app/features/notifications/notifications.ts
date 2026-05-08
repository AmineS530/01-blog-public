import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { NotificationService, NotificationResponse } from '../../core/services/notification.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [
    CommonModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatDividerModule
  ],
  template: `
    <div class="notifications-container">
      <div class="header">
        <h1>Notifications</h1>
        <button mat-button color="primary" (click)="markAllAsRead()">Mark all as read</button>
      </div>

      <mat-list>
        <div *ngFor="let n of notifications">
          <mat-list-item [class.unread]="!n.isRead" (click)="onNotificationClick(n)">
            <mat-icon matListItemIcon color="accent">{{ getIcon(n.type) }}</mat-icon>
            <div matListItemTitle>{{ n.message }}</div>
            <div matListItemLine>{{ n.createdAt | date:'short' }}</div>
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
  styles: [`
    .notifications-container {
      max-width: 600px;
      margin: 20px auto;
      background: white;
      border-radius: 8px;
      padding: 16px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
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
    }
    .unread {
      background-color: #f0f4ff;
    }
    .empty-state {
      text-align: center;
      padding: 40px;
      color: #666;
    }
    .empty-state mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      margin-bottom: 8px;
    }
  `]
})
export class NotificationsComponent implements OnInit {
  notifications: NotificationResponse[] = [];

  constructor(
    private notificationService: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadNotifications();
  }

  loadNotifications(): void {
    this.notificationService.getNotifications().subscribe((res) => {
      this.notifications = res;
    });
  }

  markAllAsRead(): void {
    this.notificationService.markAllAsRead().subscribe(() => {
      this.notifications.forEach(n => n.isRead = true);
    });
  }

  onNotificationClick(n: NotificationResponse): void {
    if (!n.isRead) {
      this.notificationService.markAsRead(n.id).subscribe();
    }
    if (n.postId) {
      this.router.navigate(['/posts', n.postId]);
    }
  }

  getIcon(type: string): string {
    switch (type) {
      case 'FOLLOW': return 'person_add';
      case 'LIKE': return 'favorite';
      case 'COMMENT': return 'comment';
      default: return 'notifications';
    }
  }
}
