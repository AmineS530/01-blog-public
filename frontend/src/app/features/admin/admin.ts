import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatChipsModule,
    MatTooltipModule
  ],
  template: `
    <div class="admin-container">
      <div class="admin-header">
        <h1>Admin Dashboard</h1>
        <p class="muted">Monitor and manage your community.</p>
      </div>

      <div class="stats-grid">
        <mat-card *ngFor="let stat of statCards" class="stat-card">
          <mat-card-content>
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
            <div class="stat-sub" *ngIf="stat.sub">{{ stat.sub }}</div>
          </mat-card-content>
        </mat-card>
      </div>

      <mat-card class="table-card">
        <mat-tab-group class="admin-tabs">
          <!-- Users Tab -->
          <mat-tab label="Users">
            <div class="tab-content">
              <div class="table-actions">
                <mat-form-field appearance="outline" class="search-field">
                  <mat-label>Search users</mat-label>
                  <input matInput [(ngModel)]="userSearchQuery" (keyup.enter)="fetchUsers()" placeholder="Username or email...">
                  <mat-icon matPrefix>search</mat-icon>
                </mat-form-field>
              </div>

              <table mat-table [dataSource]="users" class="w-full">
                <ng-container matColumnDef="username">
                  <th mat-header-cell *matHeaderCellDef> User </th>
                  <td mat-cell *matCellDef="let u"> 
                    <div class="user-cell">
                      <div class="user-info">
                        <span class="username">{{u.username}}</span>
                        <span class="email muted">{{u.email}}</span>
                      </div>
                    </div>
                  </td>
                </ng-container>

                <ng-container matColumnDef="role">
                  <th mat-header-cell *matHeaderCellDef> Role </th>
                  <td mat-cell *matCellDef="let u"> 
                    <mat-chip [class]="u.role.toLowerCase()">{{u.role}}</mat-chip>
                  </td>
                </ng-container>

                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef> Status </th>
                  <td mat-cell *matCellDef="let u"> 
                    <span class="status-badge" [class.banned]="u.isBanned">
                      {{u.isBanned ? 'Banned' : 'Active'}}
                    </span>
                  </td>
                </ng-container>

                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef> Actions </th>
                  <td mat-cell *matCellDef="let u">
                    <div class="action-buttons">
                      <button mat-icon-button (click)="toggleBan(u)" [matTooltip]="u.isBanned ? 'Unban User' : 'Ban User'" [color]="u.isBanned ? 'primary' : 'warn'">
                        <mat-icon>{{u.isBanned ? 'person_add' : 'person_off'}}</mat-icon>
                      </button>
                      <button mat-icon-button (click)="promoteUser(u)" *ngIf="u.role === 'USER'" matTooltip="Promote to Admin">
                        <mat-icon>admin_panel_settings</mat-icon>
                      </button>
                    </div>
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="['username', 'role', 'status', 'actions']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['username', 'role', 'status', 'actions'];"></tr>
              </table>

              <mat-paginator [length]="totalUsers" [pageSize]="pageSize" [pageSizeOptions]="[5, 10, 20]" (page)="onUserPageChange($event)"></mat-paginator>
            </div>
          </mat-tab>

          <!-- Reports Tab -->
          <mat-tab label="Reports">
            <div class="tab-content">
              <table mat-table [dataSource]="reports" class="w-full">
                <ng-container matColumnDef="reason">
                  <th mat-header-cell *matHeaderCellDef> Reason </th>
                  <td mat-cell *matCellDef="let r" class="reason-cell"> {{r.reason}} </td>
                </ng-container>
                <ng-container matColumnDef="target">
                  <th mat-header-cell *matHeaderCellDef> Target </th>
                  <td mat-cell *matCellDef="let r"> 
                    <div class="target-info">
                      <span class="target-type">{{r.targetType}}</span>
                      <span class="target-name">{{r.targetUsername}}</span>
                    </div>
                  </td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef> Status </th>
                  <td mat-cell *matCellDef="let r"> 
                    <mat-chip [class]="r.status">{{r.status}}</mat-chip>
                  </td>
                </ng-container>
                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef> Actions </th>
                  <td mat-cell *matCellDef="let r">
                    <div class="action-buttons">
                      <button mat-button color="primary" *ngIf="r.status === 'pending'" (click)="resolve(r, 'resolve')">Resolve</button>
                      <button mat-button color="warn" *ngIf="r.status === 'pending'" (click)="resolve(r, 'dismiss')">Dismiss</button>
                    </div>
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="['reason', 'target', 'status', 'actions']"></tr>
                <tr mat-row *matRowDef="let row; columns: ['reason', 'target', 'status', 'actions'];"></tr>
              </table>
            </div>
          </mat-tab>
        </mat-tab-group>
      </mat-card>
    </div>
  `,
  styles: [`
    .admin-container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 24px 0;
    }
    .admin-header {
      margin-bottom: 32px;
    }
    .admin-header h1 {
      font-size: 36px;
      font-weight: 800;
    }
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      gap: 20px;
      margin-bottom: 32px;
    }
    .stat-card {
      border-radius: 20px;
      border: 1px solid var(--mat-sys-outline-variant);
      background: var(--mat-sys-surface-container-low);
    }
    .stat-value {
      font-size: 32px;
      font-weight: 800;
      color: var(--mat-sys-primary);
    }
    .stat-label {
      font-weight: 600;
      color: var(--mat-sys-on-surface);
    }
    .stat-sub {
      font-size: 12px;
      color: var(--mat-sys-tertiary);
      margin-top: 4px;
    }
    .table-card {
      border-radius: 24px;
      overflow: hidden;
      border: 1px solid var(--mat-sys-outline-variant);
    }
    .tab-content {
      padding: 16px;
    }
    .table-actions {
      display: flex;
      justify-content: space-between;
      margin-bottom: 16px;
    }
    .search-field {
      width: 100%;
      max-width: 400px;
    }
    .w-full {
      width: 100%;
    }
    .user-cell {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .username {
      font-weight: 700;
      display: block;
    }
    .email {
      font-size: 12px;
    }
    .status-badge {
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 700;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .status-badge.banned {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    mat-chip.admin {
      background-color: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .target-info {
      display: flex;
      flex-direction: column;
    }
    .target-type {
      font-size: 10px;
      font-weight: 800;
      text-transform: uppercase;
      color: var(--mat-sys-outline);
    }
    .reason-cell {
      max-width: 300px;
    }
    .action-buttons {
      display: flex;
      gap: 4px;
    }
  `]
})
export class AdminComponent implements OnInit {
  stats: any = {};
  users: any[] = [];
  reports: any[] = [];
  statCards: any[] = [];
  
  totalUsers = 0;
  pageSize = 10;
  pageIndex = 0;
  userSearchQuery = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchStats();
    this.fetchUsers();
    this.fetchReports();
  }

  fetchStats(): void {
    this.http.get('http://localhost:8080/api/admin/stats').subscribe((res: any) => {
      this.stats = res;
      this.statCards = [
        { label: 'Total Users', value: res.totalUsers, sub: `+${res.newUsersToday} today` },
        { label: 'Total Posts', value: res.totalPosts, sub: `+${res.newPostsToday} today` },
        { label: 'Pending Reports', value: res.pendingReports },
        { label: 'Banned Users', value: res.bannedUsers }
      ];
    });
  }

  fetchUsers(): void {
    let url = `http://localhost:8080/api/admin/users?page=${this.pageIndex}&limit=${this.pageSize}`;
    if (this.userSearchQuery) {
      url += `&query=${this.userSearchQuery}`;
    }
    this.http.get(url).subscribe((res: any) => {
      this.users = res.content;
      this.totalUsers = res.totalElements;
    });
  }

  fetchReports(): void {
    this.http.get('http://localhost:8080/api/admin/reports').subscribe((res: any) => {
      this.reports = res;
    });
  }

  onUserPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.fetchUsers();
  }

  toggleBan(user: any): void {
    this.http.post(`http://localhost:8080/api/admin/users/${user.username}/toggle-ban`, {}).subscribe(() => {
      user.isBanned = !user.isBanned;
      this.fetchStats();
    });
  }

  promoteUser(user: any): void {
    if (confirm(`Promote ${user.username} to ADMIN?`)) {
      this.http.post(`http://localhost:8080/api/admin/users/${user.username}/role`, { role: 'ADMIN' }).subscribe(() => {
        user.role = 'ADMIN';
      });
    }
  }

  resolve(report: any, action: string): void {
    this.http.post(`http://localhost:8080/api/admin/reports/${report.id}/resolve`, { action, note: 'Processed by admin' })
      .subscribe(() => {
        report.status = action === 'resolve' ? 'resolved' : 'dismissed';
        this.fetchStats();
      });
  }
}
