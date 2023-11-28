import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  const skip = req.url.startsWith('/auth/') || req.url.includes('/assets/');
  const token = auth.token;
  const request = !skip && token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((err) => {
      if (err?.status === 401 && !skip) {
        auth.logout();
      }
      return throwError(() => err);
    })
  );
};
