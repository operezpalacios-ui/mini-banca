package com.mini_banca.orquestador.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioDto(Long id, String email, String nombre, String rol, Boolean activo) {
}
