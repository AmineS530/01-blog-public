import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatTabsModule
  ],
  template: `
    <div class="admin-container">
      <h1>Admin Dashboard</h1>

      <div class="stats-grid">
        <mat-card *ngFor="let stat of statCards" class="stat-card">
          <mat-card-header>
            <mat-card-title>{{ stat.value }}</mat-card-title>
            <mat-card-subtitle>{{ stat.label }}</mat-card-subtitle>
          </mat-card-header>
        </mat-card>
      </div>

      <mat-tab-group class="admin-tabs">
        <mat-tab label="Reports">
          <table mat-table [dataSource]="reports" class="mat-elevation-z1 w-full">
            <ng-container matColumnDef="reason">
              <th mat-header-cell *matHeaderCellDef> Reason </th>
              <td mat-cell *matCellDef="let r"> {{r.reason}} </td>
            </ng-container>
            <ng-container matColumnDef="target">
              <th mat-header-cell *matHeaderCellDef> Target </th>
              <td mat-cell *matCellDef="let r"> {{r.targetUsername}} </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef> Status </th>
              <td mat-cell *matCellDef="let r"> {{r.status}} </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef> Actions </th>
              <td mat-cell *matCellDef="let r">
                <button mat-button color="primary" *ngIf="r.status === 'pending'" (click)="resolve(r, 'resolve')">Resolve</button>
                <button mat-button color="warn" *ngIf="r.status === 'pending'" (click)="resolve(r, 'dismiss')">Dismiss</button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="['reason', 'target', 'status', 'actions']"></tr>
            <tr mat-row *matRowDef="let row; columns: ['reason', 'target', 'status', 'actions'];"></tr>
          </table>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .admin-container {
      max-width: 1000px;
      margin: 20px auto;
      padding: 0 16px;
    }
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
    }
    .stat-card {
      text-align: center;
    }
    .admin-tabs {
      margin-top: 24px;
    }
    .w-full {
      width: 100%;
    }
  `]
})
export class AdminComponent implements OnInit {
  stats: any = {};
  reports: any[] = [];
  statCards: any[] = [];

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchStats();
    this.fetchReports();
  }

  fetchStats(): void {
    this.http.get('http://localhost:8080/api/admin/stats').subscribe((res: any) => {
      this.stats = res;
      this.statCards = [
        { label: 'Total Users', value: res.totalUsers },
        { label: 'Total Posts', value: res.totalPosts },
        { label: 'Pending Reports', value: res.pendingReports },
        { label: 'Banned Users', value: res.bannedUsers }
      ];
    });
  }

  fetchReports(): void {
    this.http.get('http://localhost:8080/api/admin/reports').subscribe((res: any) => {
      this.reports = res;
    });
  }

  resolve(report: any, action: string): void {
    this.http.post(`http://localhost:8080/api/admin/reports/${report.id}/resolve`, { action, note: 'Processed by admin' })
      .subscribe(() => {
        report.status = action === 'resolve' ? 'resolved' : 'dismissed';
      });
  }
}
