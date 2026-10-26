import { DatePipe, KeyValuePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { AdminService } from '../../core/admin.service';
import { ErrorMostrable, mensajeDeError } from '../../core/errores';
import { EstadoSistema } from '../../core/modelos';

/**
 * Lección 13: panel de OBSERVABILIDAD para humanos.
 * Se actualiza solo cada 10 segundos ("polling"): pide el estado, espera, vuelve a pedir...
 */
@Component({
  selector: 'app-sistema',
  imports: [DatePipe, KeyValuePipe],
  templateUrl: './sistema.html',
  styles: `
    .rejilla { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; margin: 1rem 0; }
    .caja { border: 1px solid var(--borde); border-radius: 8px; padding: 1rem; }
    .caja h3 { margin: 0 0 0.5rem; font-size: 0.95rem; color: var(--texto-suave); }
    .grande { font-size: 1.6rem; font-weight: 700; }
    .UP, .CLOSED { color: var(--exito); }
    .DOWN, .OPEN { color: var(--error); }
    .HALF_OPEN, .UNKNOWN, .OUT_OF_SERVICE { color: #b54708; }
    ul { margin: 0.5rem 0 0; padding-left: 1.1rem; }
    .pie { color: var(--texto-suave); font-size: 0.85rem; }
  `,
})
export class Sistema {
  private readonly api = inject(AdminService);

  protected readonly estado = signal<EstadoSistema | null>(null);
  protected readonly error = signal<ErrorMostrable | null>(null);

  constructor() {
    this.cargar();
    // setInterval = "ejecuta esto cada X ms". Al salir de la pantalla hay que PARARLO,
    // o seguiría pidiendo datos para siempre en segundo plano (fuga). Eso hace onDestroy.
    const temporizador = setInterval(() => this.cargar(), 10_000);
    inject(DestroyRef).onDestroy(() => clearInterval(temporizador));
  }

  cargar(): void {
    this.api.sistema().subscribe({
      next: (e) => {
        this.estado.set(e);
        this.error.set(null);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
