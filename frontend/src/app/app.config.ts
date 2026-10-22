import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEs from '@angular/common/locales/es';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';

// Formatos españoles: 1.234,56 €  y fechas  22/10/2026
registerLocaleData(localeEs);

/** La "@Configuration" de la app: qué piezas globales se activan. */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding: el :id de la URL llega al componente como un input()
    provideRouter(routes, withComponentInputBinding()),
    // HttpClient para llamar a la API, con nuestro interceptor de autenticación
    provideHttpClient(withInterceptors([authInterceptor])),
    // Antes de pintar nada: si había sesión (F5), la recuperamos con el refresh token
    provideAppInitializer(() => inject(AuthService).restaurarSesion()),
    { provide: LOCALE_ID, useValue: 'es-ES' },
  ],
};
