import { HttpErrorResponse } from '@angular/common/http';
import { mensajeDeError } from './errores';

describe('mensajeDeError', () => {
  it('status 0 = no hay conexión con el servidor', () => {
    const e = new HttpErrorResponse({ status: 0 });
    expect(mensajeDeError(e).mensaje).toContain('No se puede conectar');
  });

  it('usa el "detail" del ProblemDetail y conserva el traceId', () => {
    const e = new HttpErrorResponse({
      status: 422,
      error: { title: 'Saldo insuficiente', detail: 'No hay saldo en ES12...', traceId: 'abc123' },
    });
    expect(mensajeDeError(e)).toEqual({ mensaje: 'No hay saldo en ES12...', traceId: 'abc123' });
  });

  it('añade la lista de campos con error de validación', () => {
    const e = new HttpErrorResponse({
      status: 400,
      error: { detail: 'Revisa los campos marcados', errores: [{ campo: 'importe', mensaje: 'debe ser mayor que 0' }] },
    });
    expect(mensajeDeError(e).mensaje).toBe('Revisa los campos marcados: importe debe ser mayor que 0');
  });

  it('cualquier otra cosa: mensaje genérico', () => {
    expect(mensajeDeError(new Error('boom')).mensaje).toBe('Error inesperado');
  });
});
