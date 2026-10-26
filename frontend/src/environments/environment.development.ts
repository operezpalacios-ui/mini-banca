/**
 * Lección 15: configuración en DESARROLLO ("npm start"). Angular sustituye environment.ts por este
 * fichero automáticamente (lo dice "fileReplacements" en angular.json).
 * Aquí la web (4200) y la API (8081) son "orígenes" distintos: por eso necesitamos CORS en dev.
 */
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8081',
};
