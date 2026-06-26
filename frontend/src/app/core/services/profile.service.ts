import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProfileResponse, ProfileUpdateRequest } from '../../shared/models/profile.models';

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly api = 'http://localhost:8080/api/profiles';

  constructor(private http: HttpClient) {}

  getProfile(username: string): Observable<ProfileResponse> {
    return this.http.get<ProfileResponse>(`${this.api}/${username}`);
  }

  getRecommendedProfiles(): Observable<ProfileResponse[]> {
    return this.http.get<ProfileResponse[]>(`${this.api}/recommended`);
  }

  updateProfile(request: ProfileUpdateRequest): Observable<ProfileResponse> {
    return this.http.put<ProfileResponse>(`${this.api}/me`, request);
  }

  updateProfileByUsername(
    username: string,
    request: ProfileUpdateRequest,
  ): Observable<ProfileResponse> {
    return this.http.put<ProfileResponse>(`${this.api}/${username}`, request);
  }

  toggleFollow(username: string): Observable<void> {
    return this.http.post<void>(`${this.api}/${username}/follow`, {});
  }

  toggleBlock(username: string): Observable<void> {
    return this.http.post<void>(`${this.api}/${username}/block`, {});
  }

  getFollowers(username: string, page: number = 0, size: number = 20): Observable<ProfileResponse[]> {
    return this.http.get<ProfileResponse[]>(`${this.api}/${username}/followers?page=${page}&size=${size}`);
  }

  getFollowing(username: string, page: number = 0, size: number = 20): Observable<ProfileResponse[]> {
    return this.http.get<ProfileResponse[]>(`${this.api}/${username}/following?page=${page}&size=${size}`);
  }

  searchProfiles(query: string, page: number = 0, size: number = 20): Observable<ProfileResponse[]> {
    return this.http.get<ProfileResponse[]>(`${this.api}/search`, { params: { query, page: page.toString(), size: size.toString() } });
  }
}
