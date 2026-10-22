import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { AdminService } from '../../core/admin.service';
import { ErrorMostrable, mensajeDeError } from '../../core/errores';
import { Pagina, Transferencia } from '../../core/modelos';

@Component({
  selector: 'app-admin-transferencias',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
  templateUrl: './admin-transferencias.html',
  styles: `
    .filtros { display: flex; gap: 0.75rem; align-items: flex-end; flex-wrap: wrap; margin-bottom: 1rem; }
    .filtros label { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.9rem; }
    .anulada td { color: var(--texto-suave); }
    .estado-anulada { color: var(--error); font-weight: 600; cursor: help; }
    .ok { color: var(--exito); font-weight: 600; }
    .paginacion { display: flex; justify-content: space-between; align-items: center; margin-top: 1rem; }
  `,
})
export class AdminTransferencias {
  private readonly api = inject(AdminService);

  protected readonly filtros = inject(NonNullableFormBuilder).group({
    usuarioId: [null as number | null],
    estado: [''],
  });

  protected readonly pagina = signal<Pagina<Transferencia> | null>(null);
  protected readonly error = signal<ErrorMostrable | null>(null);
  protected readonly aviso = signal<string | null>(null);

  constructor() {
    this.cargar(0);
  }

  cargar(numero: number): void {
    this.error.set(null);
    this.api.transferencias(this.filtros.getRawValue(), numero, 15).subscribe({
      next: (p) => this.pagina.set(p),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  anular(t: Transferencia): void {
    // prompt() = ventanita nativa que pide un texto. null = el admin pulsó Cancelar.
    const motivo = prompt(`Motivo para anular la transferencia nº ${t.id} (${t.importe} €):`);
    if (motivo === null) return;
    this.aviso.set(null);
    this.error.set(null);
    this.api.anular(t.id, motivo.trim()).subscribe({
      next: (anulada) => {
        this.aviso.set(`Transferencia nº ${anulada.id} anulada. El dinero ha vuelto a ${anulada.ibanOrigen}.`);
        this.pagina.update((p) =>
          p ? { ...p, content: p.content.map((x) => (x.id === anulada.id ? anulada : x)) } : p,
        );
      },
      // 400 (motivo corto), 409 (ya anulada), 422 (el destino ya no tiene saldo)...
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
