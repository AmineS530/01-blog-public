import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';
import { RegisterRequest, LoginRequest, AuthResponse } from '../../shared/models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';

  private loggedInSubject = new BehaviorSubject<boolean>(this.isLoggedIn());
  public loggedIn$ = this.loggedInSubject.asObservable();

  private usernameSubject = new BehaviorSubject<string | null>(null);
  public username$ = this.usernameSubject.asObservable();

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
    this.usernameSubject.next(null);
    this.loggedInSubject.next(false);
  }

  getUsername(): string | null {
    return this.usernameSubject.getValue();
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  getPublicId(): string | null {
    return this.getDecodedToken()?.sub ?? null;
  }

  getRole(): string | null {
    return this.getDecodedToken()?.role ?? null;
  }

  isLoggedIn(): boolean {
    return !!this.getToken() && !this.isTokenExpired();
  }

  isTokenExpired(): boolean {
    const token = this.getDecodedToken();
    if (!token) return true;
    if (!token.exp) return false;
    return Math.floor(Date.now() / 1000) > token.exp;
  }

  getDecodedToken(): any {
    const token = this.getToken();
    if (!token) return null;
    try {
      let payload = token.split('.')[1];
      payload = payload.replace(/-/g, '+').replace(/_/g, '/');
      const pad = payload.length % 4;
      if (pad) {
        if (pad === 1) throw new Error('Invalid base64url length');
        payload += new Array(5 - pad).join('=');
      }
      return JSON.parse(atob(payload));
    } catch (e) {
      console.error('Failed to decode token:', e);
      return null;
    }
  }

  refreshToken(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.apiUrl}/refresh`, {}, { withCredentials: true })
      .pipe(tap((res) => localStorage.setItem('token', res.token)));
  }

  /**
   * Rehydrates the username after a page refresh by calling /me.
   * Must be called from outside AuthService (e.g. app.ts ngOnInit) -
   * calling it from this service's own constructor causes a circular
   * DI error (NG0200) because the auth interceptor injects AuthService.
   */
  loadCurrentUser(): Observable<{ username: string }> {
    return this.http.get<{ username: string }>('http://localhost:8080/api/profiles/me').pipe(
      tap((res) => this.usernameSubject.next(res.username)),
    );
  }

  private saveSession(res: AuthResponse): void {
    localStorage.setItem('token', res.token);
    this.usernameSubject.next(res.username);
    this.loggedInSubject.next(true);
  }
}