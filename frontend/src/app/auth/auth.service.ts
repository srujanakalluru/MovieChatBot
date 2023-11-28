import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface AuthUser {
  email: string;
  name?: string;
  pictureUrl?: string;
}

interface AuthResponse extends AuthUser {
  token: string;
}

const TOKEN_KEY = 'auth_token';
const USER_KEY = 'auth_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  readonly user = signal<AuthUser | null>(this.restoreUser());
  readonly authenticated = signal<boolean>(this.hasValidToken());
  readonly isAdmin = signal<boolean>(this.hasRole('ROLE_ADMIN'));

  loginWithGoogle(googleIdToken: string): Observable<AuthUser> {
    return this.http
      .post<AuthResponse>('/auth/google', { idToken: googleIdToken })
      .pipe(tap((res) => this.applyAuth(res)));
  }

  loginWithCredentials(username: string, password: string): Observable<AuthUser> {
    return this.http
      .post<AuthResponse>('/auth/login', { username, password })
      .pipe(tap((res) => this.applyAuth(res)));
  }

  private applyAuth(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    const user: AuthUser = { email: res.email, name: res.name, pictureUrl: res.pictureUrl };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.user.set(user);
    this.authenticated.set(true);
    this.isAdmin.set(this.hasRole('ROLE_ADMIN'));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.user.set(null);
    this.authenticated.set(false);
    this.isAdmin.set(false);
  }

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  private decodeToken(): { exp?: number; roles?: string[] } | null {
    const token = this.token;
    if (!token) return null;
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(base64));
    } catch {
      return null;
    }
  }

  private hasValidToken(): boolean {
    const payload = this.decodeToken();
    return !!payload && typeof payload.exp === 'number' && payload.exp * 1000 > Date.now();
  }

  private hasRole(role: string): boolean {
    if (!this.hasValidToken()) return false;
    const roles = this.decodeToken()?.roles;
    return Array.isArray(roles) && roles.includes(role);
  }

  private restoreUser(): AuthUser | null {
    if (!this.hasValidToken()) return null;
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) ?? 'null');
    } catch {
      return null;
    }
  }
}
