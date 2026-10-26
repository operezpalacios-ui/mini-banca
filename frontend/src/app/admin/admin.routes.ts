import { Routes } from '@angular/router';
import { AdminLayout } from './admin-layout';

/**
 * RUTAS HIJAS: todo lo que cuelga de /admin. AdminLayout pinta las pestañas y, dentro de su
 * <router-outlet>, la pestaña elegida. Este fichero entero se carga de forma perezosa
 * (loadChildren en app.routes.ts): un usuario normal NUNCA descarga el código de administración.
 */
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayout,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'usuarios' },
      {
        path: 'usuarios',
        loadComponent: () => import('./usuarios/usuarios').then((m) => m.Usuarios),
        title: 'Usuarios · Admin',
      },
      {
        path: 'transferencias',
        loadComponent: () => import('./transferencias/admin-transferencias').then((m) => m.AdminTransferencias),
        title: 'Transferencias · Admin',
      },
      {
        path: 'auditoria',
        loadComponent: () => import('./auditoria/auditoria').then((m) => m.Auditoria),
        title: 'Auditoría · Admin',
      },
      {
        path: 'sistema',
        loadComponent: () => import('./sistema/sistema').then((m) => m.Sistema),
        title: 'Sistema · Admin',
      },
      {
        path: 'laboratorio',
        loadComponent: () => import('./laboratorio/laboratorio').then((m) => m.Laboratorio),
        title: 'Laboratorio · Admin',
      },
    ],
  },
];
