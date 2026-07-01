import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError, BehaviorSubject } from 'rxjs';
import { filter, switchMap, take } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { FeedbackService } from '../services/feedback.service';

let isRefreshing = false;
const refreshTokenSubject = new BehaviorSubject<string | null>(null);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const feedback = inject(FeedbackService);
  const token = authService.getToken();

  let clonedReq = req;
  if (token) {
    clonedReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(clonedReq).pipe(
    catchError((error: any) => {
      if (error instanceof HttpErrorResponse) {
        if (error.status === 401) {
          // If the refresh token endpoint itself or login/register fails with 401, log out and do not retry
          if (
            req.url.includes('/api/auth/refresh') ||
            req.url.includes('/api/auth/login') ||
            req.url.includes('/api/auth/register')
          ) {
            authService.logout();
            router.navigate(['/login']);
            feedback.showToast('Session expired or invalid credentials. Please log in again.', 'error');
            return throwError(() => error);
          }

          if (!isRefreshing) {
            isRefreshing = true;
            refreshTokenSubject.next(null);

            return authService.refreshToken().pipe(
              switchMap((res) => {
                isRefreshing = false;
                refreshTokenSubject.next(res.token);
                
                // Retry the original request with the new access token
                return next(req.clone({
                  setHeaders: {
                    Authorization: `Bearer ${res.token}`
                  }
                }));
              }),
              catchError((refreshError) => {
                isRefreshing = false;
                authService.logout();
                router.navigate(['/login']);
                feedback.showToast('Session expired. Please log in again.', 'error');
                return throwError(() => refreshError);
              })
            );
          } else {
            // Queue this request while refresh is in progress
            return refreshTokenSubject.pipe(
              filter((token) => token !== null),
              take(1),
              switchMap((newToken) => {
                return next(req.clone({
                  setHeaders: {
                    Authorization: `Bearer ${newToken}`
                  }
                }));
              })
            );
          }
        } else if (error.status === 403) {
          // Do not redirect on standard login/registration attempts
          if (!req.url.includes('/api/auth/login') && !req.url.includes('/api/auth/register')) {
            authService.logout();
            router.navigate(['/login']);
            feedback.showToast('Access denied. Please log in again.', 'error');
          }
        } else if (error.status === 429) {
          const errMsg = error.error?.error || 'Too many requests. Please try again later.';
          feedback.showToast(errMsg, 'error');
        }
      }
      return throwError(() => error);
    })
  );
};

