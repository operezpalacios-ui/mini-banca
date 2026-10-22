import { Routes } from '@angular/router';
import { adminGuard } from './core/admin.guard';
import { authGuard } from './core/auth.guard';
import { Cuentas } from './cuentas/cuentas';
import { Login } from './login/login';

/** Qué pantalla se ve en cada URL. Se revisan EN ORDEN y gana la primera que encaja. */
export const routes: Routes = [
  { path: 'login', component: Login, title: 'Entrar · Mini banca' },
  { path: 'cuentas', component: Cuentas, canActivate: [authGuard], title: 'Mis cuentas · Mini banca' },
  {
    path: 'cuentas/:id',
    // CARGA PEREZOSA (lazy): el código de esta pantalla NO se descarga al abrir la web,
    // solo la primera vez que alguien entra en el detalle de una cuenta. La web arranca más rápido.
    loadComponent: () => import('./cuenta-detalle/cuenta-detalle').then((m) => m.CuentaDetalle),
    canActivate: [authGuard],
    title: 'Cuenta · Mini banca',
  },
  {
    path: 'transferencias',
    loadComponent: () => import('./transferencias/transferencias').then((m) => m.Transferencias),
    canActivate: [authGuard],
    title: 'Transferencias · Mini banca',
  },
  {
    path: 'admin',
    // loadChildren: carga perezosa de un GRUPO de rutas (todo el panel de administración)
    loadChildren: () => import('./admin/admin.routes').then((m) => m.ADMIN_ROUTES),
    canActivate: [authGuard, adminGuard], // los guards se comprueban en orden
  },
  { path: '', pathMatch: 'full', redirectTo: 'cuentas' },
  { path: '**', redirectTo: 'cuentas' }, // cualquier URL desconocida
];
