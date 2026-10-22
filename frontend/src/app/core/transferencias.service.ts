import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from './api.config';
import { CuentaLigera, Pagina, Transferencia, TransferenciaRequest } from './modelos';

@Injectable({ providedIn: 'root' })
export class TransferenciasService {
  private readonly http = inject(HttpClient);

  /** Cuentas activas del usuario, para el desplegable "desde qué cuenta". */
  cuentasOrigen(): Observable<CuentaLigera[]> {
    return this.http.get<CuentaLigera[]>(`${API_URL}/api/v1/cuentas/ligeras`);
  }

  /**
   * Respuesta ENTERA (observe: 'response') para leer la cabecera Idempotent-Replayed:
   * "true" significa "esta transferencia YA estaba hecha; te devuelvo la original, no he movido nada".
   */
  transferir(datos: TransferenciaRequest, idempotencyKey: string): Observable<HttpResponse<Transferencia>> {
    return this.http.post<Transferencia>(`${API_URL}/api/v1/transferencias`, datos, {
      headers: { 'Idempotency-Key': idempotencyKey },
      observe: 'response',
    });
  }

  mias(page = 0, size = 10): Observable<Pagina<Transferencia>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Transferencia>>(`${API_URL}/api/v1/transferencias`, { params });
  }
}

/**
 * Una clave ÚNICA por cada transferencia que el usuario QUIERE hacer (un UUID).
 * crypto.randomUUID() solo existe en webs "seguras" (https o localhost); si no, lo construimos
 * a mano con números aleatorios criptográficos.
 */
export function nuevaClaveIdempotencia(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40; // versión 4
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}
