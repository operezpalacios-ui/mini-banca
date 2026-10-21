package com.mini_banca.orquestador.cliente;

import org.springframework.http.HttpStatus;

/** No hemos podido hablar con el core como es debido -> 502, 503 o 504 (RF-8.6). */
public class ComunicacionCoreException extends RuntimeException {

    private final HttpStatus status;
    private final String titulo;

    public ComunicacionCoreException(HttpStatus status, String titulo, String detalle, Throwable causa) {
        super(detalle, causa);
        this.status = status;
        this.titulo = titulo;
    }

    public HttpStatus getStatus() { return status; }
    public String getTitulo() { return titulo; }
}
