import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';
import { RegisterRequest, LoginRequest, AuthResponse } from '../../shared/models/auth.models';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';
  private loggedInSubject: BehaviorSubject<boolean>;
  public loggedIn$: Observable<boolean>;

  constructor(private http: HttpClient) {
    this.loggedInSubject = new BehaviorSubject<boolean>(this.isLoggedIn());
    this.loggedIn$ = this.loggedInSubject.asObservable();
  }

  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/register`, data, { withCredentials: true })
      .pipe(tap((res) => this.saveSession(res)));
  }

  login(data: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, data, { withCredentials: true })
      .pipe(tap((res) => this.saveSession(res)));
  }

  logout(): void {
    this.http.post(`${this.apiUrl}/logout`, {}, { withCredentials: true }).subscribe({
      next: () => {},
      error: () => {}
    });
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    this.loggedInSubject.next(false);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  isLoggedIn(): boolean {
    return !!this.getToken() && !this.isTokenExpired();
  }

  isTokenExpired(): boolean {
    const token = this.getDecodedToken();
    if (!token) return true;
    if (!token.exp) return false;
    const now = Math.floor(Date.now() / 1000);
    return token.exp < now;
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
          throw new Error(
            'InvalidLengthError: Input base64url string is the wrong length to determine padding',
          );
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

  getPublicId(): string | null {
    return this.getDecodedToken()?.sub ?? null;
  }

  getRole(): string | null {
    return this.getDecodedToken()?.role ?? null;
  }

  refreshToken(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((res) => {
          localStorage.setItem('token', res.token);
        })
      );
  }

  private saveSession(res: AuthResponse): void {
    localStorage.setItem('token', res.token);
    this.loggedInSubject.next(true);
  }
}
