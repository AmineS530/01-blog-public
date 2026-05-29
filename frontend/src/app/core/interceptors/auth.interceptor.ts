import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { FeedbackService } from '../services/feedback.service';

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
        if (error.status === 401 || error.status === 403) {
          // Do not redirect on standard login/registration attempts
          if (!req.url.includes('/api/auth/login') && !req.url.includes('/api/auth/register')) {
            authService.logout();
            router.navigate(['/login']);
            feedback.showToast('Session expired or invalid. Please log in again.', 'error');
          }
        }
      }
      return throwError(() => error);
    })
  );
};
