import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <section class="tarjeta">
      <h1>Administración</h1>
      <nav class="pestanas">
        <a routerLink="usuarios" routerLinkActive="activa">Usuarios</a>
        <a routerLink="transferencias" routerLinkActive="activa">Transferencias</a>
        <a routerLink="auditoria" routerLinkActive="activa">Auditoría</a>
        <a routerLink="sistema" routerLinkActive="activa">Sistema</a>
        <a routerLink="laboratorio" routerLinkActive="activa">Laboratorio</a>
      </nav>
      <router-outlet />
    </section>
  `,
  styles: `
    .pestanas {
      display: flex;
      gap: 0.25rem;
      border-bottom: 2px solid var(--borde);
      margin-bottom: 1.25rem;
      flex-wrap: wrap;
    }
    .pestanas a {
      padding: 0.5rem 0.9rem;
      text-decoration: none;
      color: var(--texto-suave);
      border-bottom: 2px solid transparent;
      margin-bottom: -2px;
    }
    .pestanas a.activa {
      color: var(--primario);
      border-bottom-color: var(--primario);
      font-weight: 600;
    }
  `,
})
export class AdminLayout {}
