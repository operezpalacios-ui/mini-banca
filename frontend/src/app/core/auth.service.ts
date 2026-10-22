import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, throwError } from 'rxjs';
import { API_URL } from './api.config';
import { TokenResponse, Usuario } from './modelos';

const CLAVE_REFRESH = 'minibanca.refresh';

/**
 * TODO lo relacionado con la sesión vive aquí. Es un SERVICIO: una sola instancia para toda la
 * app (providedIn: 'root'), igual que un @Service de Spring es un singleton.
 *
 * ¿Dónde guardamos los tokens?
 *  - accessToken (15 min): solo en MEMORIA (una variable). Si alguien consigue ejecutar JavaScript
 *    en nuestra página (ataque XSS), le costará más encontrarlo.
 *  - refreshToken (7 días): en sessionStorage, para que al pulsar F5 no tengas que volver a entrar.
 *    sessionStorage se borra al cerrar la pestaña. Es un equilibrio entre comodidad y seguridad;
 *    la opción más segura sería una cookie HttpOnly (JavaScript no puede leerla), pero complica
 *    el backend (CSRF, mismo dominio...). Lo dejamos así y lo sabemos.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /**
   * SIGNAL: una "caja" con un valor que avisa cuando cambia. Todo lo que dependa de ella
   * (computed, plantillas HTML) se recalcula solo. Es como una celda de Excel.
   */
  private readonly accessToken = signal<string | null>(null);

  /** computed = una celda con FÓRMULA: se recalcula sola cuando cambia accessToken. */
  readonly usuario = computed(() => leerUsuario(this.accessToken()));
  readonly autenticado = computed(() => this.accessToken() !== null);

  private refreshEnCurso: Observable<string> | null = null;

  token(): string | null {
    return this.accessToken();
  }

  login(email: string, password: string): Observable<void> {
    return this.http
      .post<TokenResponse>(`${API_URL}/api/v1/auth/login`, { email, password })
      .pipe(map((r) => this.guardar(r)));
  }

  /**
   * Pide un JWT nuevo con el refresh token.
   * ⚠️ Si 3 peticiones caducan A LA VEZ, NO podemos hacer 3 refresh: cada refresh token sirve
   * UNA sola vez y, si se reutiliza, el backend cree que es un robo y cierra TODAS las sesiones
   * (lección 8). Por eso, si ya hay uno en marcha, los demás se "enganchan" a ese (shareReplay).
   */
  refrescar(): Observable<string> {
    const refresh = sessionStorage.getItem(CLAVE_REFRESH);
    if (!refresh) {
      return throwError(() => new Error('No hay sesión que renovar'));
    }
    if (!this.refreshEnCurso) {
      this.refreshEnCurso = this.http
        .post<TokenResponse>(`${API_URL}/api/v1/auth/refresh`, { refreshToken: refresh })
        .pipe(
          map((r) => {
            this.guardar(r);
            return r.accessToken;
          }),
          finalize(() => (this.refreshEnCurso = null)),
          shareReplay(1),
        );
    }
    return this.refreshEnCurso;
  }

  /** Al arrancar la app (o pulsar F5): si había sesión, conseguimos un JWT nuevo. */
  restaurarSesion(): Promise<void> {
    if (!sessionStorage.getItem(CLAVE_REFRESH)) {
      return Promise.resolve();
    }
    return firstValueFrom(
      this.refrescar().pipe(
        map(() => undefined),
        catchError(() => {
          this.limpiar();
          return of(undefined);
        }),
      ),
    );
  }

  logout(): void {
    const refresh = sessionStorage.getItem(CLAVE_REFRESH);
    this.limpiar();
    if (refresh) {
      // Avisamos al backend para que invalide el refresh token. Si falla, da igual: ya hemos salido.
      this.http.post(`${API_URL}/api/v1/auth/logout`, { refreshToken: refresh }).subscribe({ error: () => {} });
    }
    this.router.navigate(['/login']);
  }

  /** La sesión ya no se puede renovar (refresh caducado o revocado): a la pantalla de login. */
  sesionCaducada(): void {
    this.limpiar();
    this.router.navigate(['/login'], { queryParams: { caducada: 1 } });
  }

  private guardar(r: TokenResponse): void {
    this.accessToken.set(r.accessToken);
    sessionStorage.setItem(CLAVE_REFRESH, r.refreshToken);
  }

  private limpiar(): void {
    this.accessToken.set(null);
    sessionStorage.removeItem(CLAVE_REFRESH);
  }
}

/**
 * Un JWT son 3 trozos separados por puntos: cabecera.DATOS.firma
 * Los DATOS van en Base64 (no cifrados), así que podemos LEERLOS para pintar el nombre.
 * Pero el navegador NO puede comprobar la firma (no tiene la clave): quien decide si el token
 * es válido es SIEMPRE el servidor. Esto es solo para mostrar cosas en pantalla.
 */
function leerUsuario(token: string | null): Usuario | null {
  if (!token) {
    return null;
  }
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const datos = JSON.parse(new TextDecoder().decode(bytes)); // TextDecoder: para que "Martínez" salga bien
    return { id: Number(datos.sub), nombre: datos.nombre, rol: datos.rol };
  } catch {
    return null;
  }
}
