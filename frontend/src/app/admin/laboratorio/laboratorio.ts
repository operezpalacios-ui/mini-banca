import { JsonPipe, KeyValuePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AdminService } from '../../core/admin.service';
import { ErrorMostrable, mensajeDeError } from '../../core/errores';

/** La demo de niveles de aislamiento de la lección 9, ahora con botones. */
@Component({
  selector: 'app-laboratorio',
  imports: [KeyValuePipe, JsonPipe],
  template: `
    <p>
      Dos transacciones (A y B) tocan el mismo dato a la vez sobre una tabla de juguete.
      Compara qué pasa con cada nivel de aislamiento.
    </p>
    <div class="botones">
      @for (n of niveles; track n) {
        <button [disabled]="cargando()" (click)="probar(n)">{{ n }}</button>
      }
    </div>

    @if (error(); as e) {
      <p class="error">{{ e.mensaje }}</p>
    }

    @if (resultado(); as r) {
      <table>
        <tbody>
          <!-- keyvalue convierte un objeto { a: 1, b: 2 } en una lista que @for puede recorrer.
               El comparador "null" mantiene el orden en el que llegan las claves. -->
          @for (par of r | keyvalue: sinOrdenar; track par.key) {
            <tr>
              <th>{{ par.key }}</th>
              <td>{{ par.value | json }}</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: `
    .botones { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 1rem; }
    th { width: 35%; font-family: ui-monospace, Consolas, monospace; font-size: 0.85rem; }
  `,
})
export class Laboratorio {
  private readonly api = inject(AdminService);
  protected readonly niveles = ['READ_COMMITTED', 'REPEATABLE_READ', 'SERIALIZABLE'];
  protected readonly resultado = signal<Record<string, unknown> | null>(null);
  protected readonly cargando = signal(false);
  protected readonly error = signal<ErrorMostrable | null>(null);
  protected readonly sinOrdenar = () => 0;

  probar(nivel: string): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.demoAislamiento(nivel).subscribe({
      next: (r) => {
        this.resultado.set(r);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }
}
