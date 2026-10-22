import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AdminService } from '../../core/admin.service';
import { ErrorMostrable, mensajeDeError } from '../../core/errores';
import { ACCIONES_AUDITORIA, Pagina, RegistroAuditoria } from '../../core/modelos';

@Component({
  selector: 'app-auditoria',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './auditoria.html',
  styles: `
    .filtros { display: flex; gap: 0.75rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1rem; }
    .filtros label { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.9rem; }
    .accion { font-family: ui-monospace, Consolas, monospace; font-size: 0.85rem; }
    .alerta { color: var(--error); font-weight: 600; }
    .traza { font-family: ui-monospace, Consolas, monospace; font-size: 0.75rem; color: var(--texto-suave); }
    .paginacion { display: flex; justify-content: space-between; align-items: center; margin-top: 1rem; }
  `,
})
export class Auditoria {
  private readonly api = inject(AdminService);
  protected readonly acciones = ACCIONES_AUDITORIA;
  /** Acciones que conviene que salten a la vista */
  private readonly alarmas = new Set(['LOGIN_BLOQUEADO', 'REFRESH_REUTILIZADO', 'TRANSFERENCIA_FALLIDA', 'NOTIFICACION_FALLIDA']);

  protected readonly filtros = inject(NonNullableFormBuilder).group({
    usuarioId: [null as number | null],
    accion: [''],
    desde: [''],
    hasta: [''],
  });

  protected readonly pagina = signal<Pagina<RegistroAuditoria> | null>(null);
  protected readonly error = signal<ErrorMostrable | null>(null);

  constructor() {
    this.cargar(0);
  }

  esAlarma(accion: string): boolean {
    return this.alarmas.has(accion);
  }

  cargar(numero: number): void {
    const f = this.filtros.getRawValue();
    this.error.set(null);
    this.api
      .auditoria(
        {
          usuarioId: f.usuarioId,
          accion: f.accion,
          // El backend espera un INSTANTE (fecha + hora + zona). Del calendario sacamos un día;
          // lo convertimos a "ese día a las 00:00 en TU zona horaria", expresado en UTC.
          desde: f.desde ? new Date(`${f.desde}T00:00:00`).toISOString() : undefined,
          // "hasta" incluye el día entero -> hasta las 00:00 del día SIGUIENTE
          hasta: f.hasta ? diaSiguiente(f.hasta) : undefined,
        },
        numero,
        20,
      )
      .subscribe({
        next: (p) => this.pagina.set(p),
        error: (e) => this.error.set(mensajeDeError(e)),
      });
  }
}

function diaSiguiente(dia: string): string {
  const d = new Date(`${dia}T00:00:00`);
  d.setDate(d.getDate() + 1);
  return d.toISOString();
}
