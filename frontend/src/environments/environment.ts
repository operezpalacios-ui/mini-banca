/**
 * Lección 15: configuración del frontend en PRODUCCIÓN (lo que se usa en "npm run build" y en Docker).
 *
 * apiUrl vacío = "la API está en el MISMO sitio que la web": el navegador pide /api/v1/cuentas
 * a nginx, y nginx se lo pasa al orquestador por dentro de Docker. Ventajas:
 *  - no hace falta CORS (para el navegador es la misma web),
 *  - el orquestador no tiene que estar publicado hacia fuera,
 *  - la misma imagen sirve en cualquier dominio sin recompilar.
 */
export const environment = {
  production: true,
  apiUrl: '',
};
