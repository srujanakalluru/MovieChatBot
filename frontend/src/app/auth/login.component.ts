import {
  AfterViewInit,
  Component,
  ElementRef,
  NgZone,
  ViewChild,
  inject,
  signal,
  ChangeDetectionStrategy
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './auth.service';
import { GOOGLE_CLIENT_ID } from './auth.config';

declare const google: any;

@Component({
  selector: 'app-login',
  imports: [FormsModule, TranslatePipe],
  template: `
    <div class="login-shell">
      <div class="login-card">
        <div class="logo">🎬</div>
        <h1>{{ 'app.title' | translate }}</h1>
        <p>{{ 'auth.subtitle' | translate }}</p>
        <div #googleBtn class="google-btn"></div>
        @if (error()) {
          <div class="login-error">{{ error()! | translate }}</div>
        }

        <button type="button" class="admin-link" (click)="showAdmin.set(!showAdmin())">
          {{ 'auth.adminToggle' | translate }}
        </button>

        @if (showAdmin()) {
          <form class="admin-form" (ngSubmit)="adminLogin()">
            <input name="username" autocomplete="username"
                   [ngModel]="username()" (ngModelChange)="username.set($event)"
                   [placeholder]="'auth.username' | translate" [disabled]="submitting()" />
            <input name="password" type="password" autocomplete="current-password"
                   [ngModel]="password()" (ngModelChange)="password.set($event)"
                   [placeholder]="'auth.password' | translate" [disabled]="submitting()" />
            <button type="submit" [disabled]="submitting() || !username() || !password()">
              {{ 'auth.signIn' | translate }}
            </button>
          </form>
        }
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
    styles: [`
    .login-shell {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--bg);
    }
    .login-card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow);
      padding: 48px 56px;
      text-align: center;
      max-width: 360px;
    }
    .logo { font-size: 44px; margin-bottom: 12px; }
    h1 { color: var(--text); font-size: 22px; margin: 0 0 8px; }
    p { color: var(--text-dim); font-size: 14px; margin: 0 0 28px; }
    .google-btn { display: flex; justify-content: center; min-height: 44px; }
    .login-error { margin-top: 16px; color: #ff453a; font-size: 13px; }
    .admin-link {
      margin-top: 20px;
      background: none;
      border: none;
      color: var(--text-dim);
      font-size: 13px;
      cursor: pointer;
      text-decoration: underline;
    }
    .admin-form { display: flex; flex-direction: column; gap: 10px; margin-top: 14px; }
    .admin-form input {
      padding: 10px 12px;
      border: 1px solid var(--border);
      border-radius: var(--radius);
      background: var(--bg);
      color: var(--text);
      font-size: 14px;
    }
    .admin-form button {
      padding: 10px 12px;
      border: none;
      border-radius: var(--radius);
      background: var(--accent, #0a84ff);
      color: #fff;
      font-size: 14px;
      cursor: pointer;
    }
    .admin-form button:disabled { opacity: 0.5; cursor: default; }
  `]
})
export class LoginComponent implements AfterViewInit {
  private readonly auth = inject(AuthService);
  private readonly zone = inject(NgZone);

  @ViewChild('googleBtn') private googleBtn!: ElementRef<HTMLDivElement>;

  error = signal<string | null>(null);
  showAdmin = signal(false);
  username = signal('');
  password = signal('');
  submitting = signal(false);

  ngAfterViewInit(): void {
    this.whenGisReady(0);
  }

  private whenGisReady(attempt: number): void {
    if (typeof google === 'undefined' || !google.accounts?.id) {
      if (attempt >= 50) {
        this.zone.run(() => this.error.set('auth.scriptError'));
        return;
      }
      setTimeout(() => this.whenGisReady(attempt + 1), 100);
      return;
    }
    this.renderGoogleButton();
  }

  private renderGoogleButton(): void {
    google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      callback: (response: { credential: string }) =>
        this.zone.run(() => this.onCredential(response.credential))
    });

    google.accounts.id.renderButton(this.googleBtn.nativeElement, {
      theme: document.documentElement.getAttribute('data-theme') === 'dark'
        ? 'filled_black'
        : 'outline',
      size: 'large',
      shape: 'pill',
      width: 260
    });
  }

  private onCredential(googleIdToken: string): void {
    this.error.set(null);
    this.auth.loginWithGoogle(googleIdToken).subscribe({
      error: () => this.error.set('auth.loginError')
    });
  }

  adminLogin(): void {
    if (this.submitting() || !this.username() || !this.password()) return;
    this.error.set(null);
    this.submitting.set(true);
    this.auth.loginWithCredentials(this.username(), this.password()).subscribe({
      error: () => {
        this.error.set('auth.adminError');
        this.submitting.set(false);
      }
    });
  }
}
