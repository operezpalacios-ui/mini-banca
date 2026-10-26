import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from './api.config';
import { EstadoSistema, Pagina, RegistroAuditoria, Transferencia, UsuarioAdmin } from './modelos';

/** Llamadas a /api/admin/**. Si quien llama no es ADMIN, el backend responde 403 (aunque se salte el guard). */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/api/admin`;

  usuarios(page: number, size: number): Observable<Pagina<UsuarioAdmin>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<UsuarioAdmin>>(`${this.base}/usuarios`, { params });
  }

  cambiarBloqueo(id: number, bloqueado: boolean): Observable<UsuarioAdmin> {
    return this.http.put<UsuarioAdmin>(`${this.base}/usuarios/${id}/bloqueo`, { bloqueado });
  }

  transferencias(filtro: { usuarioId?: number | null; estado?: string }, page: number, size: number) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.usuarioId) params = params.set('usuarioId', filtro.usuarioId);
    if (filtro.estado) params = params.set('estado', filtro.estado);
    return this.http.get<Pagina<Transferencia>>(`${this.base}/transferencias`, { params });
  }

  anular(id: number, motivo: string): Observable<Transferencia> {
    return this.http.post<Transferencia>(`${this.base}/transferencias/${id}/anulacion`, { motivo });
  }

  /** desde/hasta en formato ISO con hora y zona: 2026-10-21T22:00:00.000Z */
  auditoria(
    filtro: { usuarioId?: number | null; accion?: string; desde?: string; hasta?: string },
    page: number,
    size: number,
  ): Observable<Pagina<RegistroAuditoria>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'fecha,desc');
    if (filtro.usuarioId) params = params.set('usuarioId', filtro.usuarioId);
    if (filtro.accion) params = params.set('accion', filtro.accion);
    if (filtro.desde) params = params.set('desde', filtro.desde);
    if (filtro.hasta) params = params.set('hasta', filtro.hasta);
    return this.http.get<Pagina<RegistroAuditoria>>(`${this.base}/auditoria`, { params });
  }

  /** Lección 13: salud del core, circuit breaker y métricas de transferencias */
  sistema(): Observable<EstadoSistema> {
    return this.http.get<EstadoSistema>(`${this.base}/sistema`);
  }

  demoAislamiento(nivel: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.base}/demo/aislamiento`, null, {
      params: new HttpParams().set('nivel', nivel),
    });
  }
}
