import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_URL } from './api.config';
import { Cuenta, FiltroMovimientos, Movimiento, Pagina } from './modelos';

/** Las llamadas a la API de cuentas. Los componentes NO usan HttpClient directamente: usan esto. */
@Injectable({ providedIn: 'root' })
export class CuentasService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/api/v1/cuentas`;

  listar(page = 0, size = 10): Observable<Pagina<Cuenta>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Pagina<Cuenta>>(this.base, { params });
  }

  detalle(id: number): Observable<Cuenta> {
    return this.http.get<Cuenta>(`${this.base}/${id}`);
  }

  crear(alias: string, divisa: string): Observable<Cuenta> {
    return this.http.post<Cuenta>(this.base, { alias: alias || null, divisa });
  }

  cerrar(id: number): Observable<Cuenta> {
    return this.http.post<Cuenta>(`${this.base}/${id}/cerrar`, null);
  }

  movimientos(id: number, filtro: FiltroMovimientos, page: number, size: number): Observable<Pagina<Movimiento>> {
    const params = parametros(filtro).set('page', page).set('size', size);
    return this.http.get<Pagina<Movimiento>>(`${this.base}/${id}/movimientos`, { params });
  }

  /**
   * Descarga el CSV como "Blob" (un fichero binario en memoria).
   * observe: 'response' -> queremos la respuesta ENTERA, no solo el cuerpo, para leer la cabecera
   * Content-Disposition (el nombre del fichero). Por eso la "expusimos" en la config de CORS:
   * sin eso, el navegador se la escondería a Angular.
   */
  exportarCsv(id: number, filtro: FiltroMovimientos): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/${id}/movimientos/export`, {
      params: parametros(filtro),
      responseType: 'blob',
      observe: 'response',
    });
  }
}

/** Solo añadimos a la URL los filtros que vienen rellenos. HttpParams codifica bien espacios, & y +. */
function parametros(f: FiltroMovimientos): HttpParams {
  let p = new HttpParams();
  if (f.desde) p = p.set('desde', f.desde);
  if (f.hasta) p = p.set('hasta', f.hasta);
  if (f.tipo) p = p.set('tipo', f.tipo);
  if (f.importeMin != null) p = p.set('importeMin', f.importeMin);
  if (f.importeMax != null) p = p.set('importeMax', f.importeMax);
  if (f.texto?.trim()) p = p.set('texto', f.texto.trim());
  return p;
}
