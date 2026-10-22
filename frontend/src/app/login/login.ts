import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { ErrorMostrable, mensajeDeError } from '../core/errores';

/**
 * COMPONENTE = un trozo de pantalla. Tiene 3 partes:
 *   login.ts   -> la lógica (este fichero)
 *   login.html -> lo que se ve
 *   login.css  -> cómo se ve (estos estilos SOLO afectan a este componente)
 */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** El formulario y sus validaciones (como las @NotBlank / @Email de Java, pero en el navegador). */
  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected readonly enviando = signal(false);
  protected readonly error = signal<ErrorMostrable | null>(null);
  protected readonly caducada = this.route.snapshot.queryParamMap.has('caducada');

  entrar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched(); // para que se vean los mensajes de error
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();

    this.auth.login(email, password).subscribe({
      next: () => this.router.navigateByUrl(this.destino()),
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }

  /** Solo rutas internas ("/algo"): nunca dejamos que ?volver= nos lleve a otra web. */
  private destino(): string {
    const volver = this.route.snapshot.queryParamMap.get('volver');
    return volver && volver.startsWith('/') && !volver.startsWith('//') ? volver : '/cuentas';
  }
}
