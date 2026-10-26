import { environment } from '../../environments/environment';

/**
 * Dónde está la API (el ORQUESTADOR, nunca el core: el core es interno).
 * Lección 15: ya no está escrita a mano; sale del "environment" de cada entorno:
 *   npm start            -> 'http://localhost:8081'
 *   npm run build/Docker -> ''  (misma web: nginx reenvía /api al orquestador)
 */
export const API_URL = environment.apiUrl;
