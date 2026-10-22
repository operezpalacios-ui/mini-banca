import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Solo deja pasar a /admin si el token dice rol = ADMIN.
 * Recuerda: es COMODIDAD (no enseñar pantallas inútiles). La seguridad real es el 403 del backend.
 */
export const adminGuard: CanActivateFn = () => {
  return inject(AuthService).usuario()?.rol === 'ADMIN' ? true : inject(Router).createUrlTree(['/cuentas']);
};
