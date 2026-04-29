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

  updateProfile(request: ProfileUpdateRequest): Observable<ProfileResponse> {
    return this.http.put<ProfileResponse>(`${this.api}/me`, request);
  }

  toggleFollow(username: string): Observable<void> {
    return this.http.post<void>(`${this.api}/${username}/follow`, {});
  }

  toggleBlock(username: string): Observable<void> {
    return this.http.post<void>(`${this.api}/${username}/block`, {});
  }
}
