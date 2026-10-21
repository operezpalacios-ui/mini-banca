package com.minibanca.core.domain;

public enum AccionAuditoria {
    LOGIN_OK,
    LOGIN_FALLIDO,
    LOGIN_BLOQUEADO,
    LOGOUT,
    REFRESH_REUTILIZADO,      // alguien usó un refresh token ya gastado: posible robo
    TRANSFERENCIA,
    TRANSFERENCIA_FALLIDA,
    NOTIFICACION_FALLIDA,
    BLOQUEO_USUARIO,
    DESBLOQUEO_USUARIO
}
