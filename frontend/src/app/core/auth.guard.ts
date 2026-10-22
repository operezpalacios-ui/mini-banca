import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * GUARD: decide si se puede ENTRAR en una ruta. Si no hay sesión, redirige al login
 * recordando a dónde ibas (?volver=/cuentas) para llevarte allí después.
 *
 * ⚠️ Esto es solo COMODIDAD para el usuario, NO seguridad: cualquiera puede saltarse el
 * JavaScript de su navegador. La seguridad de verdad está en el backend (JWT + roles).
 */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).autenticado()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login'], { queryParams: { volver: state.url } });
};
