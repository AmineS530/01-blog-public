import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSortModule, Sort } from '@angular/material/sort';
import { FeedbackService } from '../../core/services/feedback.service';
import { AuthService } from '../../core/services/auth.service';

/**
 * Controller class managing the administrative Control Center.
 * Handles community metrics telemetry, user account bans/promotions,
 * post moderation, and content report resolutions.
 */
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
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatSortModule,
  ],
  templateUrl: './admin.html',
  styleUrl: './admin.css'
})
export class AdminComponent implements OnInit {
  // ==========================================
  // Component State Properties
  // ==========================================

  /** Telemetry object containing database statistics. */
  stats: any = {};

  /** Array of users fetched for the current paginated view. */
  users: any[] = [];

  /** Total cached list of community posts. */
  posts: any[] = [];

  /** Subset of community posts filtered locally by postSearchQuery. */
  filteredPosts: any[] = [];

  /** Cache of reported items submitted by users. */
  reports: any[] = [];

  /** Formatted telemetry cards displayed at the top of the portal. */
  statCards: any[] = [];

  // ==========================================
  // Telemetry Pagination & Query States
  // ==========================================

  /** Total count of all user accounts across the platform. */
  totalUsers = 0;

  /** Standard limit of rows per page in the users table. */
  pageSize = 10;

  /** Current zero-indexed active page for users pagination. */
  pageIndex = 0;

  /** String query utilized to filter user lists by username or email. */
  userSearchQuery = '';

  /** String query utilized to filter posts locally by title, author or content. */
  postSearchQuery = '';

  // Sorting properties
  userSortBy = 'username';
  userSortDirection = 'asc';

  // Custom edit modal states
  showEditModal = false;
  editModalTitle = '';
  editModalDescription = '';
  editModalLabel = '';
  editModalPlaceholder = '';
  editModalValue = '';
  editModalError = '';
  editModalMode: 'username' | 'displayName' = 'username';
  editingUser: any = null;

  // Custom ban modal states
  showBanModal = false;
  banningUser: any = null;
  banReason = '';
  banValue = 1;
  banUnit = 'days';

  constructor(
    private http: HttpClient,
    private router: Router,
    private feedback: FeedbackService,
    private authService: AuthService,
  ) {}

  // ==========================================
  // Lifecycle & Synchronization Methods
  // ==========================================

  /**
   * Initializes the administrator component.
   * Automatically executes global data synchronization.
   */
  ngOnInit(): void {
    this.refreshAll();
  }

  /**
   * Orchestrates a complete reload of all dashboard sub-data.
   * Refreshes stats, user arrays, content tables, and active reports.
   */
  refreshAll(): void {
    this.fetchStats();
    this.fetchUsers();
    this.fetchReports();
    this.fetchPosts();
  }

  // ==========================================
  // API Fetching Operations
  // ==========================================

  /**
   * Telemeters global community statistics from the backend service.
   * Builds statCards with icons, labels, totals, and daily increments.
   */
  fetchStats(): void {
    this.http.get('http://localhost:8080/api/admin/stats').subscribe({
      next: (res: any) => {
        this.stats = res;
        this.statCards = [
          {
            label: 'Total Users',
            value: res.totalUsers,
            sub: `+${res.newUsersToday} today`,
            icon: 'people',
            class: 'users-card',
          },
          {
            label: 'Total Posts',
            value: res.totalPosts,
            sub: `+${res.newPostsToday} today`,
            icon: 'article',
            class: 'posts-card',
          },
          {
            label: 'Pending Reports',
            value: res.pendingReports,
            icon: 'warning',
            class: 'reports-card',
          },
          { 
            label: 'Banned Users', 
            value: res.bannedUsers, 
            icon: 'block', 
            class: 'banned-card' 
          },
        ];
      },
      error: (err) => console.error('Failed to fetch stats', err),
    });
  }

  /**
   * Telemeters a paginated slice of registered users.
   * Integrates search filters if userSearchQuery is defined.
   */
  fetchUsers(): void {
    let url = `http://localhost:8080/api/admin/users?page=${this.pageIndex}&limit=${this.pageSize}&sortBy=${this.userSortBy}&direction=${this.userSortDirection}`;
    if (this.userSearchQuery) {
      url += `&query=${this.userSearchQuery}`;
    }
    this.http.get(url).subscribe({
      next: (res: any) => {
        this.users = res.content;
        this.totalUsers = res.totalElements;
      },
      error: (err) => console.error('Failed to fetch users', err),
    });
  }

  onSortChange(sort: Sort): void {
    this.userSortBy = sort.active;
    this.userSortDirection = sort.direction || 'asc';
    this.pageIndex = 0; // Reset pagination when sorting changes
    this.fetchUsers();
  }

  /**
   * Telemeters all pending and resolved moderation reports.
   */
  fetchReports(): void {
    this.http.get('http://localhost:8080/api/admin/reports').subscribe({
      next: (res: any) => {
        this.reports = res;
      },
      error: (err) => console.error('Failed to fetch reports', err),
    });
  }

  /**
   * Telemeters all community posts and applies local search filters.
   */
  fetchPosts(): void {
    this.http.get('http://localhost:8080/api/posts').subscribe({
      next: (res: any) => {
        this.posts = res;
        this.filterPostsLocal();
      },
      error: (err) => console.error('Failed to fetch posts', err),
    });
  }

  // ==========================================
  // Local Filtering & Pagination Helpers
  // ==========================================

  /**
   * Filters the total cached posts list locally.
   * Matches post title, author username, or content strings against search queries.
   */
  filterPostsLocal(): void {
    if (!this.postSearchQuery) {
      this.filteredPosts = this.posts;
    } else {
      const q = this.postSearchQuery.toLowerCase();
      this.filteredPosts = this.posts.filter(
        (p) =>
          p.title?.toLowerCase().includes(q) ||
          p.content?.toLowerCase().includes(q) ||
          p.authorUsername?.toLowerCase().includes(q),
      );
    }
  }

  /**
   * Handles paginator clicks in the users table.
   * Updates state coordinates and triggers active API re-fetching.
   * 
   * @param event Emitted paginator event carrying index and size.
   */
  onUserPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.fetchUsers();
  }

  // ==========================================
  // User Moderation Actions
  // ==========================================

  /**
   * Toggles the ban status of a specified user account.
   * Requires confirmation; handles error alerts if attempting to self-ban.
   * 
   * @param user Target user object containing username and status.
   */
  toggleBan(user: any): void {
    if (user.isBanned) {
      // Unban flow: simple confirmation
      this.feedback.askConfirmation({
        title: 'UNBAN USER',
        message: `Are you sure you want to unban user "${user.username}"?`,
        confirmText: 'UNBAN',
        onConfirm: () => {
          this.http
            .post(`http://localhost:8080/api/admin/users/${user.username}/toggle-ban`, {})
            .subscribe({
              next: () => {
                user.isBanned = false;
                user.banReason = null;
                user.bannedUntil = null;
                this.feedback.showToast(
                  `User ${user.username} has been unbanned successfully!`,
                  'success',
                );
                this.fetchStats();
              },
              error: (err) => {
                this.feedback.showToast(
                  'Failed to unban user.',
                  'error',
                );
                console.error(err);
              },
            });
        },
      });
    } else {
      // Ban flow: open custom ban modal
      this.banningUser = user;
      this.banReason = '';
      this.banValue = 1;
      this.banUnit = 'days';
      this.showBanModal = true;
    }
  }

  closeBanModal(): void {
    this.showBanModal = false;
    this.banningUser = null;
    this.banReason = '';
    this.banValue = 1;
    this.banUnit = 'days';
  }

  confirmBan(): void {
    if (!this.banningUser) return;
    const trimmedReason = this.banReason.trim();
    if (!trimmedReason) {
      this.feedback.showToast('Please specify a ban reason.', 'error');
      return;
    }

    let durationMinutes = -1;
    if (this.banUnit !== 'permanent') {
      const val = Math.max(1, this.banValue);
      if (this.banUnit === 'hours') {
        durationMinutes = val * 60;
      } else if (this.banUnit === 'days') {
        durationMinutes = val * 60 * 24;
      } else if (this.banUnit === 'months') {
        durationMinutes = val * 60 * 24 * 30;
      }
    }

    this.http
      .post(`http://localhost:8080/api/admin/users/${this.banningUser.username}/ban`, {
        reason: trimmedReason,
        durationMinutes: durationMinutes
      })
      .subscribe({
        next: () => {
          this.feedback.showToast(`User ${this.banningUser.username} has been banned successfully!`, 'success');
          this.refreshAll();
          this.closeBanModal();
        },
        error: (err: any) => {
          const errorMsg = err.error?.error || 'Failed to ban user. Note: You cannot ban yourself.';
          this.feedback.showToast(errorMsg, 'error');
          console.error('Failed to ban user', err);
        }
      });
  }

  /**
   * Promotes a regular USER account to the ADMIN role.
   * Requires confirmation; elevates permissions globally.
   * 
   * @param user Target user account.
   */
  promoteUser(user: any): void {
    this.feedback.askConfirmation({
      title: 'PROMOTE TO ADMIN',
      message: `Are you sure you want to promote ${user.username} to ADMIN? This will grant full system permissions.`,
      confirmText: 'Promote',
      onConfirm: () => {
        this.http
          .post(`http://localhost:8080/api/admin/users/${user.username}/role`, { role: 'ADMIN' })
          .subscribe({
            next: () => {
              user.role = 'ADMIN';
              this.feedback.showToast(
                `User ${user.username} has been promoted to ADMIN!`,
                'success',
              );
              this.fetchStats();
            },
            error: (err) => {
              this.feedback.showToast('Failed to promote user.', 'error');
              console.error('Failed to promote user', err);
            },
          });
      },
    });
  }

  /**
   * Demotes an ADMIN account back to the basic USER role.
   * Requires confirmation; revokes all administrative privileges.
   * 
   * @param user Target admin account.
   */
  demoteUser(user: any): void {
    this.feedback.askConfirmation({
      title: 'DEMOTE TO USER',
      message: `Are you sure you want to demote ${user.username} to USER? This will revoke all administrative permissions.`,
      confirmText: 'Demote',
      onConfirm: () => {
        this.http
          .post(`http://localhost:8080/api/admin/users/${user.username}/role`, { role: 'USER' })
          .subscribe({
            next: () => {
              user.role = 'USER';
              this.feedback.showToast(`User ${user.username} has been demoted to USER!`, 'success');
              this.fetchStats();
            },
            error: (err) => {
              this.feedback.showToast('Failed to demote user.', 'error');
              console.error('Failed to demote user', err);
            },
          });
      },
    });
  }

  /** Retrieves the active user's system role name. */
  getCurrentUserRole(): string {
    return this.authService.getRole() ?? '';
  }

  /**
   * Checks if the active administrator possesses sufficient hierarchy to ban a user.
   * Admins cannot be banned by other admins; only SUPER_ADMIN holds that permission.
   * 
   * @param user Target user row being checked.
   */
  canBan(user: any): boolean {
    if (user.role === 'SUPER_ADMIN') return false;
    if (user.role === 'ADMIN') {
      return this.getCurrentUserRole() === 'SUPER_ADMIN';
    }
    return true;
  }

  // ==========================================
  // Moderation Report Handlers
  // ==========================================

  /**
   * Resolves or dismisses an active user report.
   * 
   * @param report Active report payload.
   * @param action Chosen resolution path: 'resolve' or 'dismiss'.
   */
  resolve(report: any, action: string): void {
    const actionText = action === 'resolve' ? 'resolve' : 'dismiss';
    this.feedback.askConfirmation({
      title: `${actionText.toUpperCase()} REPORT`,
      message: `Are you sure you want to ${actionText} report #${report.id}?`,
      confirmText: actionText.toUpperCase(),
      onConfirm: () => {
        this.http
          .post(`http://localhost:8080/api/admin/reports/${report.id}/resolve`, {
            action,
            note: 'Processed by admin',
          })
          .subscribe({
            next: () => {
              report.status = action === 'resolve' ? 'resolved' : 'dismissed';
              this.feedback.showToast(`Report #${report.id} marked as ${actionText}ed!`, 'success');
              this.fetchStats();
            },
            error: (err) => {
              this.feedback.showToast('Failed to resolve report.', 'error');
              console.error('Failed to resolve report', err);
            },
          });
      },
    });
  }

  /**
   * Deletes a post directly from the dashboard view without an open report context.
   * Removes all content and physically purges associated media from storage disk.
   * 
   * @param post Target post instance.
   */
  deletePostDirect(post: any): void {
    this.feedback.askConfirmation({
      title: 'DELETE POST',
      message: `Are you sure you want to delete post "${post.title}" by ${post.authorUsername}? This is permanent and removes associated media files from physical disk.`,
      confirmText: 'Delete',
      onConfirm: () => {
        this.http.delete(`http://localhost:8080/api/admin/posts/${post.publicId}`).subscribe({
          next: () => {
            this.posts = this.posts.filter((p) => p.publicId !== post.publicId);
            this.filterPostsLocal();
            this.feedback.showToast(
              'Post deleted and disk media cleaned up successfully!',
              'success',
            );
            this.fetchStats();
          },
          error: (err) => this.feedback.showToast('Failed to delete post.', 'error'),
        });
      },
    });
  }

  /**
   * Moderates and deletes a post flagged by a user report.
   * Automatically resolves the reporting ticket upon successful backend deletion.
   * 
   * @param report Target user report context.
   */
  deleteReportedPost(report: any): void {
    this.feedback.askConfirmation({
      title: 'DELETE REPORTED POST',
      message: `Are you sure you want to delete the reported post (ID: ${report.targetPostId}) by ${report.targetUsername}? This action is permanent and will resolve the report.`,
      confirmText: 'Delete Post',
      onConfirm: () => {
        this.http.delete(`http://localhost:8080/api/admin/posts/${report.targetPostId}`).subscribe({
          next: () => {
            this.http
              .post(`http://localhost:8080/api/admin/reports/${report.id}/resolve`, {
                action: 'resolve',
                note: 'Post deleted by admin',
              })
              .subscribe(() => {
                report.status = 'resolved';
                this.feedback.showToast(
                  'Reported post deleted and report resolved successfully!',
                  'success',
                );
                this.refreshAll();
              });
          },
          error: (err) => this.feedback.showToast('Failed to delete reported post.', 'error'),
        });
      },
    });
  }

  /**
   * Moderates and deletes a comment flagged by a user report.
   * Automatically resolves the reporting ticket upon successful backend deletion.
   * 
   * @param report Target user report context.
   */
  deleteReportedComment(report: any): void {
    this.feedback.askConfirmation({
      title: 'DELETE REPORTED COMMENT',
      message: `Are you sure you want to delete the reported comment (ID: ${report.targetCommentId})? This action is permanent and will resolve the report.`,
      confirmText: 'Delete Comment',
      onConfirm: () => {
        this.http
          .delete(`http://localhost:8080/api/admin/comments/${report.targetCommentId}`)
          .subscribe({
            next: () => {
              this.http
                .post(`http://localhost:8080/api/admin/reports/${report.id}/resolve`, {
                  action: 'resolve',
                  note: 'Comment deleted by admin',
                })
                .subscribe(() => {
                  report.status = 'resolved';
                  this.feedback.showToast(
                    'Reported comment deleted and report resolved successfully!',
                    'success',
                  );
                  this.refreshAll();
                });
            },
            error: (err) => this.feedback.showToast('Failed to delete reported comment.', 'error'),
          });
      },
    });
  }

  /**
   * Moderates and bans a user flagged by a user report.
   * Automatically resolves the reporting ticket upon successful backend execution.
   * 
   * @param report Target user report context.
   */
  banReportedUser(report: any): void {
    this.feedback.askConfirmation({
      title: 'BAN REPORTED USER',
      message: `Are you sure you want to ban reported user ${report.targetUsername}? This will resolve the report.`,
      confirmText: 'Ban User',
      onConfirm: () => {
        this.http
          .post(`http://localhost:8080/api/admin/users/${report.targetUsername}/toggle-ban`, {})
          .subscribe({
            next: () => {
              this.http
                .post(`http://localhost:8080/api/admin/reports/${report.id}/resolve`, {
                  action: 'resolve',
                  note: 'User banned by admin',
                })
                .subscribe(() => {
                  report.status = 'resolved';
                  this.feedback.showToast(
                    `User ${report.targetUsername} banned and report resolved!`,
                    'success',
                  );
                  this.refreshAll();
                });
            },
            error: (err) =>
              this.feedback.showToast(
                'Failed to ban reported user. Note: You cannot ban yourself.',
                'error',
              ),
          });
      },
    });
  }

  /**
   * Prompts the administrator to edit a user's display name, updates it on the backend, and updates the local user object.
   */
  editDisplayName(user: any): void {
    this.editingUser = user;
    this.editModalMode = 'displayName';
    this.editModalTitle = 'Edit Display Name';
    this.editModalDescription = `Enter a new display name for user ${user.username}.`;
    this.editModalLabel = 'Display Name';
    this.editModalPlaceholder = 'e.g. John Doe';
    this.editModalValue = user.displayName || user.username || '';
    this.editModalError = '';
    this.showEditModal = true;
  }

  /**
   * Prompts the administrator to edit a user's username, updates it on the backend, and refreshes the data.
   */
  editUsername(user: any): void {
    this.editingUser = user;
    this.editModalMode = 'username';
    this.editModalTitle = 'Edit Username';
    this.editModalDescription = `Enter a new username for user ${user.username}. This will update their login username.`;
    this.editModalLabel = 'Username';
    this.editModalPlaceholder = 'e.g. johndoe';
    this.editModalValue = user.username || '';
    this.editModalError = '';
    this.showEditModal = true;
  }

  validateEditModalInput(): void {
    const val = this.editModalValue.trim();
    if (this.editModalMode === 'displayName') {
      if (!val) {
        this.editModalError = 'Display name cannot be empty.';
      } else if (val.length > 50) {
        this.editModalError = 'Display name cannot exceed 50 characters.';
      } else {
        this.editModalError = '';
      }
    } else {
      if (!val) {
        this.editModalError = 'Username cannot be empty.';
      } else if (val.length < 3 || val.length > 30) {
        this.editModalError = 'Username must be between 3 and 30 characters.';
      } else if (!/^[a-zA-Z0-9_-]+$/.test(val)) {
        this.editModalError = 'Username can only contain letters, numbers, underscores, and hyphens.';
      } else {
        this.editModalError = '';
      }
    }
  }

  closeEditModal(): void {
    this.showEditModal = false;
    this.editingUser = null;
    this.editModalValue = '';
    this.editModalError = '';
  }

  confirmEditModal(): void {
    this.validateEditModalInput();
    if (this.editModalError) return;

    const val = this.editModalValue.trim();
    if (this.editModalMode === 'displayName') {
      this.http
        .post(`http://localhost:8080/api/admin/users/${this.editingUser.username}/display-name`, { displayName: val })
        .subscribe({
          next: () => {
            this.editingUser.displayName = val;
            this.feedback.showToast(`Display name updated successfully!`, 'success');
            this.closeEditModal();
          },
          error: (err: any) => {
            const errorMsg = err.error?.error || 'Failed to update display name.';
            this.feedback.showToast(errorMsg, 'error');
            console.error('Failed to update display name', err);
          },
        });
    } else {
      this.http
        .post(`http://localhost:8080/api/admin/users/${this.editingUser.username}/username`, { username: val })
        .subscribe({
          next: () => {
            this.feedback.showToast(`Username updated successfully!`, 'success');
            this.refreshAll();
            this.closeEditModal();
          },
          error: (err: any) => {
            const errorMsg = err.error?.error || 'Failed to update username.';
            this.feedback.showToast(errorMsg, 'error');
            console.error('Failed to update username', err);
          },
        });
    }
  }

  // ==========================================
  // Router Handlers
  // ==========================================

  /** Navigates to a single post display view. */
  viewPost(publicId: string): void {
    this.router.navigate(['/posts', publicId]);
  }

  /** Navigates to a user's personal profile view. */
  viewProfile(username: string): void {
    this.router.navigate(['/profile', username]);
  }
}
