package com.minibanca.core.api;

import com.minibanca.core.service.DemoAislamientoService;
import com.minibanca.core.service.NivelAislamiento;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Lección 9 (RF-7.5). POST porque escribe (en la tabla de juguete demo_contador). */
@RestController
@RequestMapping("/internal/v1/demo")
public class DemoController {

    private final DemoAislamientoService demo;

    public DemoController(DemoAislamientoService demo) {
        this.demo = demo;
    }

    // POST /internal/v1/demo/aislamiento?nivel=READ_COMMITTED   (o REPEATABLE_READ, SERIALIZABLE)
    @PostMapping("/aislamiento")
    public Map<String, Object> aislamiento(@RequestParam(defaultValue = "READ_COMMITTED") NivelAislamiento nivel) {
        return demo.ejecutar(nivel);
    }
}
