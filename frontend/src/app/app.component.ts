import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { ChatComponent } from './chat/chat.component';
import { LoginComponent } from './auth/login.component';
import { AuthService } from './auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [ChatComponent, LoginComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (auth.authenticated()) {
      <app-chat />
    } @else {
      <app-login />
    }
  `
})
export class AppComponent {
  protected readonly auth = inject(AuthService);
}
