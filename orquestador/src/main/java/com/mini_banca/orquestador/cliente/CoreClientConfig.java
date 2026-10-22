package com.mini_banca.orquestador.cliente;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Configuration
public class CoreClientConfig {

    private static final Logger log = LoggerFactory.getLogger(CoreClientConfig.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    /**
     * El "teléfono" para llamar al core: dirección base + TIMEOUTS (RF-0.4).
     * Sin timeouts, si el core se cuelga, el orquestador se quedaría esperando para siempre
     * y acabaría colgándose él también.
     */
    @Bean
    RestClient coreRestClient(@Value("${core.url}") String url,
                              @Value("${core.connect-timeout}") Duration connectTimeout,
                              @Value("${core.read-timeout}") Duration readTimeout,
                              @Value("${core.api-key}") String apiKey) {
        return crear(url, connectTimeout, readTimeout, apiKey);
    }

    /**
     * Lección 9: un segundo "teléfono", igual pero con MÁS paciencia, solo para descargas (CSV).
     * Una exportación grande puede tardar más de 5 s en llegar entera, y eso no es un fallo.
     */
    @Bean
    RestClient coreRestClientDescargas(@Value("${core.url}") String url,
                                       @Value("${core.connect-timeout}") Duration connectTimeout,
                                       @Value("${core.download-timeout:2m}") Duration downloadTimeout,
                                       @Value("${core.api-key}") String apiKey) {
        return crear(url, connectTimeout, downloadTimeout, apiKey);
    }

    private static RestClient crear(String url, Duration connectTimeout, Duration readTimeout, String apiKey) {
        if (apiKey == null || apiKey.length() < 32) {
            throw new IllegalStateException(
                    "Falta INTERNAL_API_KEY (mínimo 32 caracteres). Añádela al .env (instrucciones de la lección 8).");
        }
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                // Lección 8: TODAS las llamadas al core llevan la clave interna (RF-0.3)
                .defaultHeader("X-Internal-Key", apiKey)
                // Si el core responde 4xx/5xx, leemos su ProblemDetail y lanzamos CoreErrorException
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new CoreErrorException(response.getStatusCode(), leerProblema(response.getBody().readAllBytes()));
                })
                .build();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> leerProblema(byte[] cuerpo) {
        try {
            return (Map<String, Object>) JSON.readValue(cuerpo, Map.class);
        } catch (Exception e) {
            return Map.of();   // el core respondió algo que no es JSON
        }
    }

    /**
     * CIRCUIT BREAKER "core" (RF-0.4), el "diferencial" de la instalación:
     *  - Mira las últimas 10 llamadas. Si (con al menos 5) falla el 50 % o más -> se ABRE.
     *  - ABIERTO: durante 15 s ni siquiera llamamos al core; respondemos 503 al instante.
     *  - Pasados los 15 s -> MEDIO ABIERTO: deja pasar 2 llamadas de prueba.
     *    Si van bien, se CIERRA (todo normal). Si fallan, vuelve a abrirse.
     *  - Los 4xx (saldo insuficiente, no encontrado...) NO cuentan como fallo: el core está sano.
     */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerCore() {
        return factory -> {
            factory.configure(builder -> builder
                    .circuitBreakerConfig(CircuitBreakerConfig.custom()
                            .slidingWindowSize(10)
                            .minimumNumberOfCalls(5)
                            .failureRateThreshold(50)
                            .waitDurationInOpenState(Duration.ofSeconds(15))
                            .permittedNumberOfCallsInHalfOpenState(2)
                            .recordException(e -> !(e instanceof CoreErrorException ce && ce.esErrorDelCliente()))
                            .build())
                    // Límite de tiempo total por llamada: por encima del read-timeout,
                    // para que normalmente salte antes el timeout de lectura.
                    .timeLimiterConfig(TimeLimiterConfig.custom()
                            .timeoutDuration(Duration.ofSeconds(10))
                            .build()),
                    "core");

            // Que se vea en el log cada vez que el diferencial "salta" o se rearma
            factory.addCircuitBreakerCustomizer(cb -> cb.getEventPublisher()
                    .onStateTransition(evento -> log.warn("CIRCUIT BREAKER '{}': {}",
                            evento.getCircuitBreakerName(), evento.getStateTransition())), "core");
        };
    }
}
