package com.mini_banca.orquestador.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Mismo formato de página que devuelve el core: { "content": [...], "page": {...} } */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Pagina<T>(List<T> content, Metadatos page) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadatos(long size, long number, long totalElements, long totalPages) {
    }
}
