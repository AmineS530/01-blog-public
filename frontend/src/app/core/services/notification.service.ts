import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface NotificationResponse {
  id: number;
  type: string;
  message: string;
  isRead: boolean;
  actorUsername: string;
  postId: number;
  createdAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private apiUrl = 'http://localhost:8080/api/notifications';

  constructor(private http: HttpClient) {}

  getNotifications(): Observable<NotificationResponse[]> {
    return this.http.get<NotificationResponse[]>(this.apiUrl);
  }

  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/unread-count`);
  }

  markAsRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/read`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/read-all`, {});
  }
}
