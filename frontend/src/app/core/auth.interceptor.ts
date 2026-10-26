import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { API_URL } from './api.config';
import { AuthService } from './auth.service';

// Estas rutas NO llevan token (son públicas)
const PUBLICAS = /\/api\/v1\/auth\/(login|registro|refresh|logout)$/;

/**
 * INTERCEPTOR: un "portero de salida". TODA petición HTTP de la app pasa por aquí antes de salir.
 *  1. Le pone la cabecera  Authorization: Bearer <jwt>  (así ningún componente tiene que acordarse).
 *  2. Si vuelve un 401 (JWT caducado), pide uno nuevo con el refresh token y REPITE la petición.
 *     El usuario ni se entera.
 *  3. Si tampoco se puede renovar, lo manda al login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // Solo a NUESTRA API: nunca mandamos el token a otras webs.
  // (Lección 15: comprobamos "<API_URL>/api/" y no solo API_URL, porque en producción API_URL es ''
  //  y cualquier dirección "empieza por ''": sin esto, el token se iría a TODAS las webs.)
  if (!req.url.startsWith(`${API_URL}/api/`) || PUBLICAS.test(req.url)) {
    return next(req);
  }

  const auth = inject(AuthService);
  const token = auth.token();

  return next(token ? conToken(req, token) : req).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401) {
        return throwError(() => err); // 403, 404, 422... no son cosa de la sesión
      }
      return auth.refrescar().pipe(
        catchError(() => {
          auth.sesionCaducada();
          return throwError(() => err);
        }),
        switchMap((nuevo) => next(conToken(req, nuevo))), // repetimos la petición original
      );
    }),
  );
};

/** Las peticiones son INMUTABLES: no se modifican, se clonan con el cambio. */
function conToken(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}
