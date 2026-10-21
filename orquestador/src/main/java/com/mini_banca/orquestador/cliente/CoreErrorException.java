package com.mini_banca.orquestador.cliente;

import org.springframework.http.HttpStatusCode;

import java.util.Map;

/**
 * El core HA RESPONDIDO, pero con un error (4xx o 5xx).
 * Guardamos su ProblemDetail (como un Map) para poder reenviarlo al navegador.
 */
public class CoreErrorException extends RuntimeException {

    private final HttpStatusCode status;
    private final Map<String, Object> problema;

    public CoreErrorException(HttpStatusCode status, Map<String, Object> problema) {
        super("El core respondió " + status.value());
        this.status = status;
        this.problema = problema;
    }

    public HttpStatusCode getStatus() { return status; }
    public Map<String, Object> getProblema() { return problema; }

    /** Un 4xx es "culpa del cliente" (saldo insuficiente, no encontrado...): el core está SANO. */
    public boolean esErrorDelCliente() {
        return status.is4xxClientError();
    }
}
