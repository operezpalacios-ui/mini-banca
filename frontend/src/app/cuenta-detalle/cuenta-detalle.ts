import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CuentasService } from '../core/cuentas.service';
import { guardarFichero } from '../core/descargas';
import { ErrorMostrable, mensajeDeError } from '../core/errores';
import { Cuenta, FiltroMovimientos, Movimiento, Pagina } from '../core/modelos';

const TAMANO_PAGINA = 10;

@Component({
  selector: 'app-cuenta-detalle',
  imports: [CurrencyPipe, DatePipe, RouterLink, ReactiveFormsModule],
  templateUrl: './cuenta-detalle.html',
  styleUrl: './cuenta-detalle.css',
})
export class CuentaDetalle implements OnInit {
  private readonly api = inject(CuentasService);

  /**
   * El ":id" de la URL /cuentas/:id llega aquí SOLO, como un "input" del componente
   * (lo activa withComponentInputBinding() en app.config.ts). Viene como texto.
   */
  readonly id = input.required<string>();

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly pagina = signal<Pagina<Movimiento> | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<ErrorMostrable | null>(null);
  protected readonly descargando = signal(false);

  /** Los filtros aplicados AHORA (los del formulario solo cuentan al pulsar "Buscar"). */
  private filtroActual: FiltroMovimientos = {};

  protected readonly filtros = inject(NonNullableFormBuilder).group({
    desde: [''],
    hasta: [''],
    tipo: ['' as '' | 'CARGO' | 'ABONO'],
    texto: [''],
    importeMin: [null as number | null],
    importeMax: [null as number | null],
  });

  // ngOnInit: se ejecuta cuando Angular ya ha rellenado los inputs (en el constructor, id() aún no existe)
  ngOnInit(): void {
    this.api.detalle(this.cuentaId()).subscribe({
      next: (c) => this.cuenta.set(c),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
    this.cargarMovimientos(0);
  }

  buscar(): void {
    this.filtroActual = this.filtros.getRawValue();
    this.cargarMovimientos(0);
  }

  limpiar(): void {
    this.filtros.reset();
    this.buscar();
  }

  irAPagina(numero: number): void {
    this.cargarMovimientos(numero);
  }

  descargarCsv(): void {
    this.descargando.set(true);
    this.api.exportarCsv(this.cuentaId(), this.filtroActual).subscribe({
      next: (respuesta) => {
        guardarFichero(respuesta, 'movimientos.csv');
        this.descargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.descargando.set(false);
      },
    });
  }

  cerrarCuenta(): void {
    // confirm() = la ventanita nativa del navegador "¿Aceptar / Cancelar?"
    if (!confirm('¿Seguro que quieres cerrar esta cuenta? No se puede deshacer.')) return;
    this.api.cerrar(this.cuentaId()).subscribe({
      next: (c) => this.cuenta.set(c),
      error: (e) => this.error.set(mensajeDeError(e)), // p. ej. 422 si tiene saldo
    });
  }

  private cargarMovimientos(numero: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.movimientos(this.cuentaId(), this.filtroActual, numero, TAMANO_PAGINA).subscribe({
      next: (p) => {
        this.pagina.set(p);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e)); // p. ej. 422 si "desde" > "hasta"
        this.cargando.set(false);
      },
    });
  }

  private cuentaId(): number {
    return Number(this.id());
  }
}
