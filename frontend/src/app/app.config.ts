import { ApplicationConfig } from '@angular/core';
import { HttpInterceptorFn, provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { authInterceptor } from './auth/auth.interceptor';

const languageInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.clone({ setHeaders: { 'Accept-Language': localStorage.getItem('lang') || 'en' } }));

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(withXhr(), withInterceptors([languageInterceptor, authInterceptor])),
    provideTranslateService({
      lang: 'en',
      fallbackLang: 'en',
      loader: provideTranslateHttpLoader({ prefix: './assets/i18n/', suffix: '.json' })
    })
  ]
};
