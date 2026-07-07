import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { RealtimeService } from './realtime.service';
import { AuthService } from './auth.service';

export interface NotificationResponse {
  id: number;
  type: string;
  message: string;
  isRead: boolean;
  actorUsername: string;
  postId: string;
  createdAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private apiUrl = 'http://localhost:8080/api/notifications';

  // Cache so the panel renders instantly instead of waiting on an HTTP round trip every open.
  private notificationsSubject = new BehaviorSubject<NotificationResponse[]>([]);
  public notifications$ = this.notificationsSubject.asObservable();
  private loaded = false;

  constructor(
    private http: HttpClient,
    private realtimeService: RealtimeService,
    private authService: AuthService,
  ) {
    // Live push from the global websocket - prepend instantly, no panel-open fetch needed.
    this.realtimeService.notifications$.subscribe((n) => {
      this.notificationsSubject.next([n, ...this.notificationsSubject.value]);
    });

    this.authService.loggedIn$.subscribe((isLoggedIn) => {
      if (isLoggedIn) {
        this.preload();
      } else {
        this.loaded = false;
        this.notificationsSubject.next([]);
      }
    });
  }

  /** Call once (e.g. on login) to warm the cache so the panel never feels slow. */
  preload(): void {
    if (this.loaded) return;
    this.loaded = true;
    this.fetchPage(0, 20).subscribe();
  }

  getNotifications(page = 0, size = 20): Observable<NotificationResponse[]> {
    return this.fetchPage(page, size);
  }

  private fetchPage(page: number, size: number): Observable<NotificationResponse[]> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<NotificationResponse[]>(this.apiUrl, { params }).pipe(
      tap((res) => {
        if (page === 0) {
          this.notificationsSubject.next(res);
        }
      }),
    );
  }

  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/unread-count`);
  }

  markAsRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/read`, {});
  }

  toggleReadStatus(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/toggle-read`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/read-all`, {});
  }

  clearAllNotifications(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/clear`, {}).pipe(
      tap(() => {
        this.notificationsSubject.next([]);
      })
    );
  }
}