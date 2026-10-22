/**
 * "Interfaces" de TypeScript: describen la FORMA del JSON que devuelve la API.
 * Son el equivalente a los records DTO de Java. No generan código: solo sirven para que
 * el editor te avise si escribes cuenta.ibna en vez de cuenta.iban.
 */

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
}

/** Lo que sacamos del propio JWT (ver AuthService.leerUsuario). */
export interface Usuario {
  id: number;
  nombre: string;
  rol: 'USER' | 'ADMIN';
}

/** Mismo formato de página que devuelve Spring: { content: [...], page: {...} } */
export interface Pagina<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface Cuenta {
  id: number;
  iban: string;
  alias: string | null;
  divisa: string;
  saldo: number;
  estado: 'ACTIVA' | 'CERRADA';
  creadoEn: string;
  cerradaEn: string | null;
}

export interface Movimiento {
  id: number;
  tipo: 'CARGO' | 'ABONO';
  importe: number;
  concepto: string | null;
  fecha: string;
}

/** Filtros del histórico (RF-4.2). Todos opcionales: vacío = "no filtrar por esto". */
export interface FiltroMovimientos {
  desde?: string; // '2026-10-01'
  hasta?: string;
  tipo?: '' | 'CARGO' | 'ABONO';
  importeMin?: number | null;
  importeMax?: number | null;
  texto?: string;
}

/** Versión "ligera" de una cuenta (proyección de la lección 9): para desplegables. */
export interface CuentaLigera {
  id: number;
  iban: string;
  alias: string | null;
  saldo: number;
}

export interface TransferenciaRequest {
  cuentaOrigenId: number;
  ibanDestino: string;
  importe: number;
  concepto: string | null;
}

export interface Transferencia {
  id: number;
  cuentaOrigenId: number;
  ibanOrigen: string;
  ibanDestino: string;
  importe: number;
  concepto: string | null;
  fecha: string;
  estado: 'COMPLETADA' | 'ANULADA';
  anuladaEn: string | null;
  motivoAnulacion: string | null;
}

// ---------- Administración ----------

export interface UsuarioAdmin {
  id: number;
  email: string;
  nombre: string;
  rol: 'USER' | 'ADMIN';
  activo: boolean;
}

export interface RegistroAuditoria {
  id: number;
  fecha: string;
  usuarioId: number | null;
  accion: string;
  detalle: string | null;
  traceId: string | null;
}

/** Las acciones que registra el backend (enum AccionAuditoria del core). */
export const ACCIONES_AUDITORIA = [
  'LOGIN_OK',
  'LOGIN_FALLIDO',
  'LOGIN_BLOQUEADO',
  'LOGOUT',
  'REFRESH_REUTILIZADO',
  'TRANSFERENCIA',
  'TRANSFERENCIA_FALLIDA',
  'NOTIFICACION_FALLIDA',
  'BLOQUEO_USUARIO',
  'DESBLOQUEO_USUARIO',
  'CUENTA_CERRADA',
  'TRANSFERENCIA_ANULADA',
] as const;
