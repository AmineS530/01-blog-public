import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PostRequest, PostResponse } from '../../shared/models/post.models';

@Injectable({ providedIn: 'root' })
export class PostService {
  private readonly api = 'http://localhost:8080/api/posts';

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
}