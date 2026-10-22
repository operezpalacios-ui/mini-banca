import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Quita espacios y pasa a mayúsculas: "es12 3456 ..." -> "ES123456..." */
export function normalizarIban(valor: string): string {
  return valor.replace(/\s+/g, '').toUpperCase();
}

/** Mismo criterio que el backend (@Pattern "ES\\d{22}"), pero aceptando espacios al pegarlo. */
export const ibanEspanol: ValidatorFn = (c: AbstractControl): ValidationErrors | null => {
  const v = normalizarIban(String(c.value ?? ''));
  if (!v) return null; // de "obligatorio" se encarga Validators.required
  return /^ES\d{22}$/.test(v) ? null : { iban: true };
};

/** Mayor que 0 y como mucho 2 decimales (igual que @DecimalMin y @Digits del backend). */
export const importeValido: ValidatorFn = (c: AbstractControl): ValidationErrors | null => {
  const v = c.value;
  if (v === null || v === undefined || v === '') return null;
  if (Number(v) < 0.01) return { importe: 'debe ser mayor que 0' };
  if (!/^\d+(\.\d{1,2})?$/.test(String(v))) return { importe: 'máximo 2 decimales' };
  return null;
};
