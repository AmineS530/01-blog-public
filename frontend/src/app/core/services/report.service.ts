import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ReportRequest {
  reason: string;
  targetUserId?: number;
  targetPostId?: string;
  targetCommentId?: number;
}

@Injectable({
  providedIn: 'root'
})
export class ReportService {
  private apiUrl = 'http://localhost:8080/api/reports';

  constructor(private http: HttpClient) {}

  reportUser(userId: number, reason: string): Observable<any> {
    return this.http.post(this.apiUrl, { targetUserId: userId, reason });
  }

  reportPost(postId: string, reason: string): Observable<any> {
    return this.http.post(this.apiUrl, { targetPostId: postId, reason });
  }

  reportComment(commentId: number, reason: string): Observable<any> {
    return this.http.post(this.apiUrl, { targetCommentId: commentId, reason });
  }
}
