import { HttpErrorResponse } from '@angular/common/http';

export interface ErrorMostrable {
  mensaje: string;
  traceId?: string;
}

/**
 * Convierte cualquier error HTTP en un texto para el usuario.
 * Nuestra API SIEMPRE responde errores en formato ProblemDetail (lección 5):
 *   { "title", "status", "detail", "traceId", "errores": [{ "campo", "mensaje" }] }
 * así que aquí solo tenemos que saber leer ESE formato.
 */
export function mensajeDeError(err: unknown): ErrorMostrable {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      // status 0 = ni siquiera hubo respuesta: servidor apagado, red caída... o CORS mal configurado
      return { mensaje: 'No se puede conectar con el servidor. ¿Está arrancado el orquestador?' };
    }
    const p = err.error;
    if (p && typeof p === 'object') {
      let mensaje: string = p.detail ?? p.title ?? `Error ${err.status}`;
      if (Array.isArray(p.errores) && p.errores.length > 0) {
        mensaje += ': ' + p.errores.map((e: { campo: string; mensaje: string }) => `${e.campo} ${e.mensaje}`).join(', ');
      }
      return { mensaje, traceId: p.traceId };
    }
    return { mensaje: `Error ${err.status}` };
  }
  return { mensaje: 'Error inesperado' };
}
