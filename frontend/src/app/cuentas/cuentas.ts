import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CuentasService } from '../core/cuentas.service';
import { ErrorMostrable, mensajeDeError } from '../core/errores';
import { Cuenta } from '../core/modelos';

@Component({
  selector: 'app-cuentas',
  imports: [CurrencyPipe, RouterLink, ReactiveFormsModule],
  templateUrl: './cuentas.html',
  styleUrl: './cuentas.css',
})
export class Cuentas {
  private readonly api = inject(CuentasService);

  // Los 3 estados de toda pantalla que carga datos: cargando, error o datos
  protected readonly cuentas = signal<Cuenta[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<ErrorMostrable | null>(null);

  // Formulario "nueva cuenta"
  protected readonly nueva = inject(NonNullableFormBuilder).group({
    alias: ['', Validators.maxLength(100)],
    divisa: ['EUR', Validators.required],
  });
  protected readonly creando = signal(false);
  protected readonly errorCrear = signal<ErrorMostrable | null>(null);

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);
    // subscribe = "cuando llegue la respuesta, haz esto". La petición NO sale hasta que alguien se suscribe.
    this.api.listar(0, 50).subscribe({
      next: (pagina) => {
        this.cuentas.set(pagina.content);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }

  crear(): void {
    if (this.nueva.invalid) return;
    this.creando.set(true);
    this.errorCrear.set(null);
    const { alias, divisa } = this.nueva.getRawValue();
    this.api.crear(alias.trim(), divisa).subscribe({
      next: () => {
        this.nueva.reset({ alias: '', divisa: 'EUR' });
        this.creando.set(false);
        this.cargar(); // volvemos a pedir la lista: así sale con su IBAN real
      },
      error: (e) => {
        this.errorCrear.set(mensajeDeError(e));
        this.creando.set(false);
      },
    });
  }
}
