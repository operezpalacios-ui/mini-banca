package com.mini_banca.orquestador.api.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lección 9 (RF-4.2): filtros del histórico. El orquestador NO los interpreta: los reenvía tal cual
 * al core, que es quien los valida (si tipo=COSA, el core responde 400 y nosotros lo reenviamos).
 * Así la regla vive en UN solo sitio.
 */
public record FiltroMovimientos(String desde, String hasta, String tipo,
                                String importeMin, String importeMax, String texto) {

    /** Solo los filtros que vienen rellenos, listos para añadirlos a la URL. */
    public Map<String, String> comoParametros() {
        Map<String, String> p = new LinkedHashMap<>();
        if (desde != null) p.put("desde", desde);
        if (hasta != null) p.put("hasta", hasta);
        if (tipo != null) p.put("tipo", tipo);
        if (importeMin != null) p.put("importeMin", importeMin);
        if (importeMax != null) p.put("importeMax", importeMax);
        if (texto != null) p.put("texto", texto);
        return p;
    }
}
