import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, of, tap, shareReplay, throwError } from 'rxjs';
import { RegisterRequest, LoginRequest, AuthResponse, CooldownResponse } from '../../shared/models/auth.models';
import { ProfileResponse } from '../../shared/models/profile.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';

  private loggedInSubject = new BehaviorSubject<boolean>(this.isLoggedIn());
  public loggedIn$ = this.loggedInSubject.asObservable();

  private usernameSubject = new BehaviorSubject<string | null>(null);
  public username$ = this.usernameSubject.asObservable();

  // Cached ProfileResponse for the current user, populated exactly once
  // per session (refresh, login, or after register). Reset on logout.
  // All consumers with the same role should read from this rather than
  // calling /api/profiles/{username} — it's the same payload either way.
  private profileSubject = new BehaviorSubject<ProfileResponse | null>(null);
  public profile$ = this.profileSubject.asObservable();

  // In-flight request, if any, shareReplay'd so concurrent subscribers don't
  // kick off duplicate HTTP calls for the same user.
  private profileFetch$?: Observable<ProfileResponse>;

  /**
   * Tracks the active username-change cooldown for the current user. The
   * settings page subscribes to {@link cooldown$} to render its countdown
   * and gate the submit button. null = not yet loaded; populated by
   * {@link loadUsernameCooldown} once per session, refreshed after a
   * successful username change so the next eligible time is observable
   * without another network round-trip.
   */
  private cooldownSubject = new BehaviorSubject<CooldownResponse | null>(null);
  public cooldown$ = this.cooldownSubject.asObservable();

  // In-flight slot for /api/auth/username-cooldown, mirroring the profile
  // cache pattern so concurrent subscribers share one HTTP call.
  private cooldownFetch$?: Observable<CooldownResponse>;

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
    return !!this.getToken();
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

  changePassword(data: any): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/change-password`, data, { withCredentials: true });
  }

  changeUsername(data: any): Observable<{ username: string }> {
    return this.http
      .post<{ username: string }>(`${this.apiUrl}/change-username`, data, { withCredentials: true })
      .pipe(
        tap((res) => {
          this.usernameSubject.next(res.username);
          // After a successful change, refresh the cached cooldown so the
          // settings page's countdown reflects the new nextAllowedAt without
          // waiting for a manual refresh. Drop the in-flight slot first so
          // we don't replay a stale shareReplay'd value.
          this.cooldownFetch$ = undefined;
          this.loadUsernameCooldown().subscribe({
            error: (err) => console.error('Failed to refresh cooldown after rename', err),
          });
        })
      );
  }

  /**
   * Loads (and caches) the username-change cooldown for the current user.
   * The cache is mirrored to {@link cooldown$}; concurrent callers share
   * one HTTP request via the {@link cooldownFetch$} in-flight slot +
   * shareReplay.
   *
   * Returns {@code of(null)} when logged out so callers don't need to gate.
   */
  loadUsernameCooldown(): Observable<CooldownResponse | null> {
    if (this.cooldownFetch$) {
      return this.cooldownFetch$;
    }
    if (!this.isLoggedIn()) {
      return of(null);
    }
    const req = this.http.get<CooldownResponse>(`${this.apiUrl}/username-cooldown`);
    const shared = req.pipe(shareReplay({ bufferSize: 1, refCount: false }));
    this.cooldownFetch$ = shared;
    shared.subscribe({
      next: (r) => this.cooldownSubject.next(r),
      complete: () => (this.cooldownFetch$ = undefined),
      error: () => (this.cooldownFetch$ = undefined),
    });
    return shared;
  }

  /** Synchronous read of the cached cooldown (null if not yet loaded). */
  getCachedCooldown(): CooldownResponse | null {
    return this.cooldownSubject.getValue();
  }

  /**
   * Rehydrates the current user's profile after a page refresh / navigation
   * by calling /api/profiles/me. The endpoint derives identity purely from
   * the JWT (its `sub` claim holds the publicId), so we don't need to know
   * the username up front — that comes back in the response and is then
   * mirrored to usernameSubject.
   *
   * Must be called from outside AuthService (e.g. app.ts ngOnInit) — calling
   * it from this service's own constructor causes a circular DI error
   * (NG0200) because the auth interceptor injects AuthService.
   *
   * Concurrent callers share the same in-flight HTTP request (shareReplay in
   * fetchMyProfile), so even if 3 subscribers attach "simultaneously", only
   * one HTTP call goes out.
   */
  loadCurrentUser(): Observable<ProfileResponse> {
    if (!this.isLoggedIn() || this.isTokenExpired()) {
      // Defensive: log out if the token is gone or expired so cached state
      // doesn't drift. Callers should also drive initial load off isLoggedIn.
      this.loggedInSubject.next(false);
      return throwError(() => new Error('Cannot rehydrate: not logged in'));
    }
    return this.fetchMyProfile().pipe(
      tap((profile) => {
        this.usernameSubject.next(profile.username);
        this.profileSubject.next(profile);
      }),
    );
  }

  /**
   * Fetches the current user's ProfileResponse, cached so concurrent
   * subscribers share a single HTTP call. Used for cold-start rehydration
   * (post page refresh / navigation), where we only have the JWT-derived
   * publicId and not the username.
   *
   * The endpoint is /api/profiles/me, which the backend resolves via the
   * authenticated principal — so no {username} segment is required up front.
   */
  private fetchMyProfile(): Observable<ProfileResponse> {
    // In-flight request — shareReplay ensures all subscribers get the same
    // response and one HTTP call goes out, not N.
    if (this.profileFetch$) {
      return this.profileFetch$;
    }
    const req = this.http.get<ProfileResponse>(`${this.apiUrl.replace('/auth', '/profiles')}/me`);
    // shareReplay: subsequent subscribers get the cached result without a
    // new HTTP call. refCount:false so the slot stays hot while a request is
    // pending even if the first subscriber retires mid-flight.
    const shared = req.pipe(shareReplay({ bufferSize: 1, refCount: false }));
    this.profileFetch$ = shared;
    // Clear the in-flight slot when the request settles (success or error)
    shared.subscribe({
      complete: () => (this.profileFetch$ = undefined),
      error: () => (this.profileFetch$ = undefined),
    });
    return shared;
  }

  /** Synchronous read of the cached profile (null if not yet loaded). */
  getCachedProfile(): ProfileResponse | null {
    return this.profileSubject.getValue();
  }

  /**
   * Components call this when they want the current user's profile but don't
   * want to be the one responsible for caching it. Behavior:
   *   - Cache hit → emit cached value, no HTTP call.
   *   - Cache miss, logged in → fire one /api/profiles/me, cache, emit.
   *   - Cache miss, logged out → emit null.
   *
   * Uses /api/profiles/me (not /api/profiles/{username}) because we don't
   * necessarily know the username yet on a cold page-load — only the
   * JWT-derived publicId is available. Concurrent callers share the same
   * in-flight HTTP request (shareReplay in fetchMyProfile).
   */
  ensureProfileLoaded(): Observable<ProfileResponse | null> {
    const cached = this.profileSubject.getValue();
    if (cached) {
      return of(cached);
    }
    if (!this.isLoggedIn()) {
      return of(null);
    }
    // fetchMyProfile → /api/profiles/me; no username needed up front.
    // Mirror to profileSubject on success so future calls are cache hits.
    return this.fetchMyProfile().pipe(
      tap((p) => {
        this.profileSubject.next(p);
        if (!this.getUsername()) {
          this.usernameSubject.next(p.username);
        }
      }),
    );
  }

  /** Invalidate the cached profile + cooldown (call after mutations). */
  invalidateProfileCache(): void {
    this.profileSubject.next(null);
    this.profileFetch$ = undefined;
    this.cooldownSubject.next(null);
    this.cooldownFetch$ = undefined;
  }

  private saveSession(res: AuthResponse): void {
    localStorage.setItem('token', res.token);
    this.usernameSubject.next(res.username);
    this.loggedInSubject.next(true);
    // Profile will be lazily fetched by the first component that needs it.
  }

  checkConnection(): Observable<any> {
    return this.http.get('http://localhost:8080/api/posts?page=0&size=1');
  }
}