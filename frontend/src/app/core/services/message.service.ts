import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MessageRequest {
  recipientPublicId: string;
  content: string;
  mediaUrl?: string | null;
}

export interface MessageResponse {
  id: number;
  senderPublicId: string;
  senderUsername: string;
  senderAvatarUrl?: string | null;
  recipientPublicId: string;
  recipientUsername: string;
  recipientAvatarUrl?: string | null;
  content: string;
  mediaUrl?: string | null;
  read: boolean;
  createdAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class MessageService {
  private apiUrl = 'http://localhost:8080/api/messages';

  constructor(private http: HttpClient) {}

  sendMessage(request: MessageRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(this.apiUrl, request);
  }

  getInbox(): Observable<MessageResponse[]> {
    return this.http.get<MessageResponse[]>(`${this.apiUrl}/inbox`);
  }

  getConversation(partnerPublicId: string): Observable<MessageResponse[]> {
    return this.http.get<MessageResponse[]>(`${this.apiUrl}/thread/${partnerPublicId}`);
  }

  markConversationAsRead(partnerPublicId: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/read-all/${partnerPublicId}`, {});
  }

  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.apiUrl}/unread-count`);
  }

  getOnlineUsers(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/online`);
  }
}
