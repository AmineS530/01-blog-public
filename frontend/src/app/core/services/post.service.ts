import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PostRequest, PostResponse, CommentRequest, CommentResponse } from '../../shared/models/post.models';

@Injectable({ providedIn: 'root' })
export class PostService {
  private readonly api = 'http://localhost:8080/api/posts';
  private readonly interactionApi = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  getAll(page: number = 0, limit: number = 10): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.api}?page=${page}&limit=${limit}`);
  }

  getFollowingFeed(page: number = 0, limit: number = 10): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.api}/following?page=${page}&limit=${limit}`);
  }

  getById(id: number): Observable<PostResponse> {
    return this.http.get<PostResponse>(`${this.api}/${id}`);
  }

  getByUsername(username: string, page: number = 0, limit: number = 10): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.api}/user/${username}?page=${page}&limit=${limit}`);
  }

  create(post: PostRequest): Observable<PostResponse> {
    return this.http.post<PostResponse>(this.api, post);
  }

  update(id: number, post: PostRequest): Observable<PostResponse> {
    return this.http.put<PostResponse>(`${this.api}/${id}`, post);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`);
  }

  addComment(postId: number, request: CommentRequest): Observable<CommentResponse> {
    return this.http.post<CommentResponse>(`${this.interactionApi}/posts/${postId}/comments`, request);
  }

  getComments(postId: number): Observable<CommentResponse[]> {
    return this.http.get<CommentResponse[]>(`${this.interactionApi}/posts/${postId}/comments`);
  }

  updateComment(commentId: number, request: CommentRequest): Observable<CommentResponse> {
    return this.http.put<CommentResponse>(`${this.interactionApi}/comments/${commentId}`, request);
  }

  deleteComment(commentId: number): Observable<void> {
    return this.http.delete<void>(`${this.interactionApi}/comments/${commentId}`);
  }

  togglePostLike(postId: number): Observable<void> {
    return this.http.post<void>(`${this.interactionApi}/posts/${postId}/likes`, {});
  }

  toggleCommentLike(commentId: number): Observable<void> {
    return this.http.post<void>(`${this.interactionApi}/comments/${commentId}/likes`, {});
  }
}