import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PostRequest, PostResponse, CommentRequest, CommentResponse } from '../../shared/models/post.models';

@Injectable({ providedIn: 'root' })
export class PostService {
  private readonly api = 'http://localhost:8080/api/posts';
  private readonly interactionApi = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  getAll(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(this.api);
  }

  getById(id: number): Observable<PostResponse> {
    return this.http.get<PostResponse>(`${this.api}/${id}`);
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

  togglePostLike(postId: number): Observable<void> {
    return this.http.post<void>(`${this.interactionApi}/posts/${postId}/likes`, {});
  }

  toggleCommentLike(commentId: number): Observable<void> {
    return this.http.post<void>(`${this.interactionApi}/comments/${commentId}/likes`, {});
  }
}