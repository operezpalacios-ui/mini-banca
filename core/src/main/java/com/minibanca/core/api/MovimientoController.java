package com.minibanca.core.api;

import com.minibanca.core.api.dto.MovimientoResponse;
import com.minibanca.core.domain.TipoMovimiento;
import com.minibanca.core.service.MovimientoFiltro;
import com.minibanca.core.service.MovimientoService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/internal/v1/cuentas/{cuentaId}/movimientos")
public class MovimientoController {

    private static final DateTimeFormatter FECHA_CSV =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Europe/Madrid"));

    private final MovimientoService movimientoService;

    public MovimientoController(MovimientoService movimientoService) {
        this.movimientoService = movimientoService;
    }

    /**
     * RF-4.1 + RF-4.2. Todos los filtros son opcionales, p. ej.:
     *   ?desde=2026-10-01&hasta=2026-10-31&tipo=CARGO&importeMin=10&texto=nomina
     * Spring convierte cada parámetro a su tipo (LocalDate, enum, BigDecimal). Si no puede
     * (tipo=COSA), responde 400 él solo, en formato ProblemDetail.
     */
    @GetMapping
    public PagedModel<MovimientoResponse> historico(
            @RequestHeader("X-User-Id") Long usuarioId,
            @PathVariable Long cuentaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) BigDecimal importeMin,
            @RequestParam(required = false) BigDecimal importeMax,
            @RequestParam(required = false) String texto,
            @PageableDefault(size = 20, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        MovimientoFiltro filtro = new MovimientoFiltro(desde, hasta, tipo, importeMin, importeMax, texto);
        return new PagedModel<>(movimientoService.historico(usuarioId, cuentaId, filtro, pageable));
    }

    /**
     * RF-4.3: exportar a CSV en STREAMING.
     *
     * StreamingResponseBody = "te doy una función que escribe la respuesta poco a poco".
     * Spring devuelve el hilo de la petición y ejecuta la función en otro hilo, escribiendo
     * directamente en la conexión. El fichero NUNCA está entero en memoria.
     *
     * Las comprobaciones (¿es tu cuenta?, ¿filtros válidos?) se hacen ANTES, en este hilo:
     * una vez empezado a enviar el CSV ya no se puede cambiar el código 200 por un 404.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportar(
            @RequestHeader("X-User-Id") Long usuarioId,
            @PathVariable Long cuentaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) BigDecimal importeMin,
            @RequestParam(required = false) BigDecimal importeMax,
            @RequestParam(required = false) String texto) {

        MovimientoFiltro filtro = new MovimientoFiltro(desde, hasta, tipo, importeMin, importeMax, texto);
        String iban = movimientoService.prepararExportacion(usuarioId, cuentaId, filtro);   // 404 / 422 aquí

        StreamingResponseBody cuerpo = salida -> {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(salida, StandardCharsets.UTF_8));
            w.write('\uFEFF');   // "BOM": así Excel sabe que es UTF-8 y muestra bien las tildes
            w.write("id;fecha;tipo;importe;concepto\r\n");
            movimientoService.exportar(usuarioId, cuentaId, filtro, lote -> {
                try {
                    for (MovimientoResponse m : lote) {
                        w.write(m.id() + ";" + FECHA_CSV.format(m.fecha()) + ";" + m.tipo() + ";"
                                + m.importe().toPlainString().replace('.', ',') + ";"
                                + celda(m.concepto()) + "\r\n");
                    }
                    w.flush();   // cada lote sale ya por la red
                } catch (IOException e) {
                    throw new UncheckedIOException(e);   // p. ej. el cliente canceló la descarga
                }
            });
            w.flush();
        };

        // Content-Disposition: attachment -> el navegador lo DESCARGA con este nombre en vez de mostrarlo
        String fichero = "movimientos-" + iban + "-" + LocalDate.now(ZoneId.of("Europe/Madrid")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fichero).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(cuerpo);
    }

    /**
     * Una celda de CSV segura:
     *  - Si lleva ; comillas o saltos de línea, va entre comillas (y las comillas se duplican).
     *  - "Inyección CSV": si empieza por = + - @, Excel la ejecutaría como FÓRMULA
     *    (=HYPERLINK("http://malo"...)). Le ponemos un apóstrofo delante para que sea texto.
     */
    static String celda(String valor) {
        if (valor == null) return "";
        String v = valor;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
