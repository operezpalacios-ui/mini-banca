import { FormControl } from '@angular/forms';
import { ibanEspanol, importeValido, normalizarIban } from './validadores';

/**
 * Tests del FRONTEND (Vitest). Misma idea que JUnit:
 *   describe = un grupo de tests ("la clase"),  it = un test,  expect(...).toBe(...) = el assert.
 * Se ejecutan con:  npm test
 */
describe('normalizarIban', () => {
  it('quita espacios y pasa a mayúsculas', () => {
    expect(normalizarIban(' es12 3456 7890 ')).toBe('ES1234567890');
  });
});

describe('validador ibanEspanol', () => {
  const validar = (valor: string) => ibanEspanol(new FormControl(valor));

  it('acepta ES + 22 dígitos', () => {
    expect(validar('ES9873877867659746200015')).toBeNull(); // null = "sin errores"
  });

  it('acepta el IBAN pegado con espacios y en minúsculas', () => {
    expect(validar('es98 7387 7867 6597 4620 0015')).toBeNull();
  });

  it('rechaza un IBAN corto', () => {
    expect(validar('ES123')).toEqual({ iban: true });
  });

  it('rechaza un IBAN de otro país', () => {
    expect(validar('FR7630006000011234567890189')).toEqual({ iban: true });
  });

  it('un campo vacío no es "IBAN incorrecto" (de eso se encarga required)', () => {
    expect(validar('')).toBeNull();
  });
});

describe('validador importeValido', () => {
  const validar = (valor: unknown) => importeValido(new FormControl(valor));

  it.each([1, 0.01, 12.5, 999.99])('acepta %s', (importe) => {
    expect(validar(importe)).toBeNull();
  });

  it('rechaza 0 y negativos', () => {
    expect(validar(0)).toEqual({ importe: 'debe ser mayor que 0' });
    expect(validar(-5)).toEqual({ importe: 'debe ser mayor que 0' });
  });

  it('rechaza más de 2 decimales', () => {
    expect(validar(1.234)).toEqual({ importe: 'máximo 2 decimales' });
  });
});
