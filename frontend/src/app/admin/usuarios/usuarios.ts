import { Component, inject, signal } from '@angular/core';
import { AdminService } from '../../core/admin.service';
import { AuthService } from '../../core/auth.service';
import { ErrorMostrable, mensajeDeError } from '../../core/errores';
import { Pagina, UsuarioAdmin } from '../../core/modelos';

@Component({
  selector: 'app-usuarios',
  templateUrl: './usuarios.html',
  styles: `
    .inactivo td { color: var(--texto-suave); }
    .estado-off { color: var(--error); font-weight: 600; }
    .paginacion { display: flex; justify-content: space-between; align-items: center; margin-top: 1rem; }
  `,
})
export class Usuarios {
  private readonly api = inject(AdminService);
  protected readonly yo = inject(AuthService).usuario;

  protected readonly pagina = signal<Pagina<UsuarioAdmin> | null>(null);
  protected readonly error = signal<ErrorMostrable | null>(null);
  protected readonly ocupado = signal<number | null>(null); // id del usuario que se está actualizando

  constructor() {
    this.cargar(0);
  }

  cargar(numero: number): void {
    this.api.usuarios(numero, 20).subscribe({
      next: (p) => this.pagina.set(p),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  alternar(u: UsuarioAdmin): void {
    const bloquear = u.activo;
    if (!confirm(`¿${bloquear ? 'Desactivar' : 'Reactivar'} a ${u.nombre} (${u.email})?`)) return;
    this.ocupado.set(u.id);
    this.error.set(null);
    this.api.cambiarBloqueo(u.id, bloquear).subscribe({
      next: (actualizado) => {
        // Actualizamos SOLO esa fila, sin volver a pedir toda la lista.
        // update() recibe el valor actual y devuelve uno NUEVO (no se modifica el viejo).
        this.pagina.update((p) =>
          p ? { ...p, content: p.content.map((x) => (x.id === actualizado.id ? actualizado : x)) } : p,
        );
        this.ocupado.set(null);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.ocupado.set(null);
      },
    });
  }
}
