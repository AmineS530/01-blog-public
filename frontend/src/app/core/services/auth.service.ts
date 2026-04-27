import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { RegisterRequest, LoginRequest, AuthResponse } from '../../shared/models/auth.models';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';

  constructor(private http: HttpClient) {}

  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/register`, data)
      .pipe(tap((res) => this.saveSession(res)));
  }

  login(data: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, data)
      .pipe(tap((res) => this.saveSession(res)));
  }

  logout(): void {
    localStorage.removeItem('token');
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }
  getDecodedToken(): any {
    const token = this.getToken();
    if (!token) return null;
    try {
      let payload = token.split('.')[1];
      payload = payload.replace(/-/g, '+').replace(/_/g, '/');
      const pad = payload.length % 4;
      if (pad) {
        if (pad === 1) {
          throw new Error('InvalidLengthError: Input base64url string is the wrong length to determine padding');
        }
        payload += new Array(5 - pad).join('=');
      }
      return JSON.parse(atob(payload));
    } catch (e) {
      console.error('Failed to decode token:', e);
      return null;
    }
  }
  getUsername(): string | null {
    return this.getDecodedToken()?.username ?? null;
  }

  getRole(): string | null {
    return this.getDecodedToken()?.role ?? null;
  }

  private saveSession(res: AuthResponse): void {
    localStorage.setItem('token', res.token);
  }
}
