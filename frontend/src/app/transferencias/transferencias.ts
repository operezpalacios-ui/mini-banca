import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ErrorMostrable, mensajeDeError } from '../core/errores';
import { CuentaLigera, Pagina, Transferencia } from '../core/modelos';
import { TransferenciasService, nuevaClaveIdempotencia } from '../core/transferencias.service';
import { ibanEspanol, importeValido, normalizarIban } from '../core/validadores';

@Component({
  selector: 'app-transferencias',
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
  templateUrl: './transferencias.html',
  styleUrl: './transferencias.css',
})
export class Transferencias {
  private readonly api = inject(TransferenciasService);

  protected readonly origenes = signal<CuentaLigera[]>([]);
  protected readonly historial = signal<Pagina<Transferencia> | null>(null);

  protected readonly enviando = signal(false);
  protected readonly error = signal<ErrorMostrable | null>(null);
  protected readonly hecha = signal<{ t: Transferencia; repetida: boolean } | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    cuentaOrigenId: [null as number | null, Validators.required],
    ibanDestino: ['', [Validators.required, ibanEspanol]],
    importe: [null as number | null, [Validators.required, importeValido]],
    concepto: ['', Validators.maxLength(140)],
  });

  /**
   * ⭐ IDEMPOTENCIA desde el navegador (lección 5):
   *  - La clave se crea UNA vez por "intención" de transferir.
   *  - Si la petición falla por la RED (wifi caído, 503, 504...) y el usuario pulsa otra vez,
   *    se reenvía con la MISMA clave -> si la primera sí llegó al core, no se cobra dos veces.
   *  - Si el usuario CAMBIA algún dato, es otra transferencia distinta -> clave nueva.
   */
  private clave = nuevaClaveIdempotencia();

  constructor() {
    // takeUntilDestroyed: deja de escuchar cuando se sale de la pantalla (evita fugas de memoria)
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => (this.clave = nuevaClaveIdempotencia()));
    this.cargarOrigenes();
    this.cargarHistorial(0);
  }

  enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const origen = this.origenes().find((c) => c.id === Number(v.cuentaOrigenId));
    const destino = normalizarIban(v.ibanDestino);
    if (!confirm(`¿Enviar ${v.importe} € desde ${origen?.iban} a ${destino}?`)) return;

    this.enviando.set(true);
    this.error.set(null);
    this.hecha.set(null);

    this.api
      .transferir(
        {
          cuentaOrigenId: Number(v.cuentaOrigenId),
          ibanDestino: destino,
          importe: Number(v.importe),
          concepto: v.concepto.trim() || null,
        },
        this.clave,
      )
      .subscribe({
        next: (r) => {
          this.hecha.set({ t: r.body!, repetida: r.headers.get('Idempotent-Replayed') === 'true' });
          this.enviando.set(false);
          this.form.reset({ cuentaOrigenId: v.cuentaOrigenId, ibanDestino: '', importe: null, concepto: '' });
          this.clave = nuevaClaveIdempotencia(); // ésta ya está hecha: la siguiente será otra
          this.cargarOrigenes(); // los saldos han cambiado
          this.cargarHistorial(0);
        },
        error: (e) => {
          // NO cambiamos la clave: si el usuario reintenta tal cual, es la MISMA operación
          this.error.set(mensajeDeError(e));
          this.enviando.set(false);
        },
      });
  }

  cargarHistorial(numero: number): void {
    this.api.mias(numero, 10).subscribe({
      next: (p) => this.historial.set(p),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  private cargarOrigenes(): void {
    this.api.cuentasOrigen().subscribe({
      next: (cs) => {
        this.origenes.set(cs);
        // Si solo hay una cuenta, la elegimos por el usuario (sin disparar "clave nueva")
        if (cs.length === 1 && this.form.controls.cuentaOrigenId.value === null) {
          this.form.controls.cuentaOrigenId.setValue(cs[0].id, { emitEvent: false });
        }
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
