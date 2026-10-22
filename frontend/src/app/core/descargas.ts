import { HttpResponse } from '@angular/common/http';

/**
 * Guarda en el ordenador del usuario un fichero que hemos recibido por HTTP.
 *
 * ¿Por qué no un simple <a href="/export">? Porque un enlace normal NO puede llevar la cabecera
 * Authorization: el backend respondería 401. Así que lo pedimos con HttpClient (que sí lleva el
 * token gracias al interceptor), y después "fingimos" un clic en un enlace que apunta a ese
 * fichero que ya tenemos en memoria.
 */
export function guardarFichero(respuesta: HttpResponse<Blob>, nombrePorDefecto: string): void {
  const blob = respuesta.body;
  if (!blob) return;

  const disposicion = respuesta.headers.get('Content-Disposition') ?? '';
  const nombre = /filename="?([^";]+)"?/.exec(disposicion)?.[1] ?? nombrePorDefecto;

  const url = URL.createObjectURL(blob); // una URL temporal que apunta al fichero en memoria
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.click();
  URL.revokeObjectURL(url); // liberamos la memoria
}
