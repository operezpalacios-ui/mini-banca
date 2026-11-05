/*
 * Mini banca — modelo C4 como código (Structurizr DSL).
 *
 * Cómo verlo:
 *   - Online: pega este fichero en https://structurizr.com/dsl y pulsa "Render".
 *   - Con Docker (desde la carpeta docs/c4):
 *       docker run -it --rm -p 8080:8080 -v ${PWD}:/usr/local/structurizr structurizr/lite
 *     y abre http://localhost:8080
 *
 * Un solo modelo -> muchas vistas: si cambias algo aquí, cambian todos los diagramas a la vez.
 */
workspace "Mini banca" "Banco de práctica: Angular + API Gateway + 2 microservicios Spring Boot + PostgreSQL + Redis" {

    !identifiers hierarchical

    model {
        cliente = person "Cliente" "Abre cuentas, consulta movimientos, descarga CSV y hace transferencias."
        admin = person "Administrador" "Gestiona usuarios, revisa auditoría, anula transferencias y vigila el sistema."

        notificaciones = softwareSystem "Servicio de notificaciones" "Email/SMS/push. En Mini banca está SIMULADO: solo escribe en el log." {
            tags "Externo"
        }

        minibanca = softwareSystem "Mini banca" "Cuentas, movimientos y transferencias seguras." {

            web = container "Aplicación web" "Pantallas del cliente y del panel de administración." "Angular 22 (SPA), servida por nginx" {
                tags "Web"
                paginas = component "Pantallas" "login, cuentas, cuenta-detalle, transferencias y admin (usuarios, transferencias, auditoría, sistema, laboratorio)." "Componentes standalone"
                guards = component "Guards" "Impiden entrar en rutas sin sesión o sin rol ADMIN." "authGuard, adminGuard"
                interceptor = component "Interceptor de autenticación" "Añade el JWT a cada petición y, ante un 401, hace UN refresh compartido y reintenta." "HttpInterceptorFn"
                serviciosWeb = component "Servicios de datos" "Llaman a la API: cuentas, transferencias, admin." "AuthService, CuentasService, TransferenciasService, AdminService"
            }

            gateway = container "API Gateway" "Puerta de entrada de la API: enruta al orquestador, limita peticiones por IP, circuit breaker y traceId. No valida el JWT ni tiene lógica de negocio." "Java 21, Spring Cloud Gateway (WebFlux)" {
                tags "Api"
                traza = component "TraceIdFilter" "Da a cada petición su X-Trace-Id (o respeta uno válido) y lo pasa al orquestador." "GlobalFilter"
                limitador = component "Limitador de peticiones" "Cubo de fichas por IP (X-Real-IP de nginx): general para /api/** y estricto para login/registro/refresh. Si Redis falla, deja pasar." "RedisRateLimiter, KeyResolver"
                rutas = component "Rutas" "auth, descargas (CSV, 150 s) y api; reenvían /api/** sin reescribir y dejan pasar las X-Forwarded-* de nginx." "RouteLocator"
                cbGateway = component "Circuit breaker" "Si el orquestador no conecta o tarda, responde 503/504 en formato ProblemDetail." "Resilience4j reactivo, FallbackController"
            }

            orquestador = container "Orquestador (BFF)" "API para la web (detrás del gateway): seguridad, saga de transferencias y adaptación para la web." "Java 21, Spring Boot 4" {
                tags "Api"
                seguridad = component "Seguridad" "Valida el JWT, reglas por rol, CORS y cabeceras X-Forwarded." "SecurityConfig, TokenService, JwtConfig"
                controllers = component "Controllers REST" "Endpoints /api/v1/... para la web." "AuthController, CuentaController, TransferenciaController, AdminController"
                saga = component "Saga de transferencias" "Límite diario, transferencia en el core, auditoría y notificación; mide la métrica minibanca.transferencias." "TransferenciaSagaService"
                notif = component "Notificaciones" "Envía (simula) el aviso de transferencia." "NotificacionService"
                estado = component "Estado del sistema" "Junta salud del core y estado del circuit breaker para el panel Admin." "EstadoSistemaService, EstadoCircuito"
                coreClient = component "Cliente del core" "Llama al core con timeouts, clave interna y circuit breaker." "CoreClient (RestClient + Resilience4j)"
                erroresOrq = component "Errores y traza" "Errores en formato ProblemDetail y código de soporte (traceId)." "GlobalExceptionHandler, TraceIdFilter"
            }

            core = container "Core" "Dominio y reglas del dinero: usuarios, cuentas, movimientos, transferencias, auditoría." "Java 21, Spring Boot 4, JPA/Hibernate" {
                tags "Api"
                claveInterna = component "Filtro de clave interna" "Solo acepta llamadas que traen la clave compartida con el orquestador." "ClaveInternaFilter"
                controllersCore = component "Controllers internos" "Endpoints /internal/v1/... (no públicos)." "Usuario, Sesion, Cuenta, Movimiento, Transferencia, Auditoria, Demo"
                usuarios = component "Usuarios y sesiones" "Registro, credenciales con BCrypt, refresh tokens con rotación." "UsuarioService, RefreshTokenService"
                cuentas = component "Cuentas" "Abrir, cerrar, cuentas ligeras y resumen mensual (con caché)." "CuentaService, GeneradorIban"
                movimientos = component "Movimientos" "Histórico con filtros y exportación CSV por lotes (keyset)." "MovimientoService"
                transferencias = component "Transferencias" "Transferir con bloqueo pesimista e idempotencia; anular con movimientos compensatorios." "TransferenciaService"
                auditoria = component "Auditoría" "Registra quién hizo qué y cuándo." "AuditoriaService"
                demo = component "Laboratorio de aislamiento" "Demuestra niveles de aislamiento sobre una tabla de juguete." "DemoAislamientoService"
                cache = component "Caché" "@Cacheable con TTL; borra tras el commit; si Redis falla, sigue sin caché." "CacheConfig, CacheCuentas"
                repos = component "Repositorios" "Acceso a datos: Specifications, proyecciones, consultas nativas, SELECT FOR UPDATE." "Spring Data JPA"
            }

            bd = container "Base de datos" "Usuarios, cuentas, movimientos, transferencias, auditoría. Esquema con Flyway (V1-V7)." "PostgreSQL 18" {
                tags "BaseDeDatos"
            }

            redis = container "Caché" "Respuestas de lectura frecuentes con TTL de 5 y 10 minutos." "Redis 8 (Upstash en la nube)" {
                tags "BaseDeDatos"
            }
        }

        # ---- Relaciones de nivel 1 y 2 ----
        cliente -> minibanca.web "Usa" "HTTPS"
        admin -> minibanca.web "Administra" "HTTPS"
        minibanca.web -> minibanca.gateway "Llama a la API /api/v1" "JSON/HTTPS, JWT (vía nginx)"
        minibanca.gateway -> minibanca.orquestador "Reenvía /api/**" "JSON/HTTP, JWT + X-Trace-Id"
        minibanca.gateway -> minibanca.redis "Cubos del limitador" "RESP"
        minibanca.orquestador -> minibanca.core "Llama a /internal/v1" "JSON/HTTP + clave interna"
        minibanca.orquestador -> notificaciones "Envía avisos" "Simulado (log)"
        minibanca.core -> minibanca.bd "Lee y escribe" "JDBC"
        minibanca.core -> minibanca.redis "Cachea lecturas" "RESP (rediss:// con TLS)"

        # ---- Relaciones de nivel 3: web ----
        minibanca.web.paginas -> minibanca.web.serviciosWeb "Pide datos" "Llamada Java"
        minibanca.web.guards -> minibanca.web.serviciosWeb "Consulta la sesión" "Llamada Java"
        minibanca.web.serviciosWeb -> minibanca.web.interceptor "Sus peticiones pasan por" "Llamada Java"
        minibanca.web.interceptor -> minibanca.gateway.traza "Llama a" "JSON/HTTPS"

        # ---- Relaciones de nivel 3: gateway ----
        minibanca.gateway.traza -> minibanca.gateway.rutas "Pasa la petición con su traceId" "Llamada Java"
        minibanca.gateway.rutas -> minibanca.gateway.limitador "¿Le quedan fichas a esta IP?" "Llamada Java"
        minibanca.gateway.limitador -> minibanca.redis "Script Lua del cubo de fichas" "RESP"
        minibanca.gateway.rutas -> minibanca.gateway.cbGateway "Llama a través de" "Llamada Java"
        minibanca.gateway.cbGateway -> minibanca.orquestador.seguridad "Reenvía" "JSON/HTTP"

        # ---- Relaciones de nivel 3: orquestador ----
        minibanca.orquestador.seguridad -> minibanca.orquestador.controllers "Deja pasar con usuario y rol" "Llamada Java"
        minibanca.orquestador.controllers -> minibanca.orquestador.saga "Transferir" "Llamada Java"
        minibanca.orquestador.controllers -> minibanca.orquestador.coreClient "Resto de operaciones" "Llamada Java"
        minibanca.orquestador.controllers -> minibanca.orquestador.estado "Panel Sistema" "Llamada Java"
        minibanca.orquestador.saga -> minibanca.orquestador.coreClient "Límite, transferencia, auditoría" "Llamada Java"
        minibanca.orquestador.saga -> minibanca.orquestador.notif "Avisa" "Llamada Java"
        minibanca.orquestador.estado -> minibanca.orquestador.coreClient "Salud del core" "Llamada Java"
        minibanca.orquestador.coreClient -> minibanca.core.claveInterna "Llama a" "JSON/HTTP + clave interna"
        minibanca.orquestador.controllers -> minibanca.orquestador.erroresOrq "Sus excepciones se convierten en ProblemDetail" "Llamada Java"
        minibanca.orquestador.notif -> notificaciones "Envía" "Simulado (log)"

        # ---- Relaciones de nivel 3: core ----
        minibanca.core.claveInterna -> minibanca.core.controllersCore "Deja pasar" "Llamada Java"
        minibanca.core.controllersCore -> minibanca.core.usuarios "Usa" "Llamada Java"
        minibanca.core.controllersCore -> minibanca.core.cuentas "Usa" "Llamada Java"
        minibanca.core.controllersCore -> minibanca.core.movimientos "Usa" "Llamada Java"
        minibanca.core.controllersCore -> minibanca.core.transferencias "Usa" "Llamada Java"
        minibanca.core.controllersCore -> minibanca.core.auditoria "Usa" "Llamada Java"
        minibanca.core.controllersCore -> minibanca.core.demo "Usa" "Llamada Java"
        minibanca.core.cuentas -> minibanca.core.cache "Cachea y borra" "Llamada Java"
        minibanca.core.transferencias -> minibanca.core.cache "Borra tras el commit" "Llamada Java"
        minibanca.core.usuarios -> minibanca.core.repos "Usa" "Llamada Java"
        minibanca.core.cuentas -> minibanca.core.repos "Usa" "Llamada Java"
        minibanca.core.movimientos -> minibanca.core.repos "Usa" "Llamada Java"
        minibanca.core.transferencias -> minibanca.core.repos "Usa" "Llamada Java"
        minibanca.core.transferencias -> minibanca.core.auditoria "Audita anulaciones" "Llamada Java"
        minibanca.core.auditoria -> minibanca.core.repos "Usa" "Llamada Java"
        minibanca.core.repos -> minibanca.bd "SQL" "JDBC"
        minibanca.core.demo -> minibanca.bd "SET TRANSACTION ISOLATION LEVEL" "JDBC"
        minibanca.core.cache -> minibanca.redis "GET/SET con TTL" "RESP"

        # ---- Despliegue con Docker Compose ----
        local = deploymentEnvironment "Docker Compose" {
            deploymentNode "Máquina con Docker" "PC, servidor o GitHub Codespaces" "Linux + Docker Engine" {
                deploymentNode "Red interna mini-banca_default" "Los contenedores se ven por su nombre de servicio" "Red bridge de Docker" {
                    deploymentNode "frontend" "Único puerto publicado: 4200 -> 80 (pruebas 8091, producción 80)" "nginx 1.31-alpine" {
                        containerInstance minibanca.web
                    }
                    deploymentNode "gateway" "Sin puerto publicado. Solo nginx le habla. Perfil prod." "eclipse-temurin 21 JRE" {
                        containerInstance minibanca.gateway
                    }
                    deploymentNode "orquestador" "Sin puerto publicado. Perfil prod." "eclipse-temurin 21 JRE" {
                        containerInstance minibanca.orquestador
                    }
                    deploymentNode "core" "Sin puerto publicado. Perfil prod." "eclipse-temurin 21 JRE" {
                        containerInstance minibanca.core
                    }
                    deploymentNode "postgres" "Volumen pgdata" "postgres:18-alpine" {
                        containerInstance minibanca.bd
                    }
                    deploymentNode "redis" "Caché local; en la nube se usa Upstash" "redis:8-alpine" {
                        containerInstance minibanca.redis
                    }
                }
            }
        }
    }

    configuration {
        scope softwaresystem
    }

    views {
        systemContext minibanca "Contexto" "Nivel 1: quién usa Mini banca y con qué habla." {
            include *
            autolayout lr
        }
        container minibanca "Contenedores" "Nivel 2: las piezas que se despliegan." {
            include *
            autolayout lr
        }
        component minibanca.web "ComponentesWeb" "Nivel 3: dentro de la aplicación web." {
            include *
            autolayout lr
        }
        component minibanca.gateway "ComponentesGateway" "Nivel 3: dentro del API Gateway." {
            include *
            autolayout lr
        }
        component minibanca.orquestador "ComponentesOrquestador" "Nivel 3: dentro del orquestador." {
            include *
            autolayout lr
        }
        component minibanca.core "ComponentesCore" "Nivel 3: dentro del core." {
            include *
            autolayout lr
        }
        dynamic minibanca "Transferencia" "Vista dinámica: una transferencia de principio a fin." {
            cliente -> minibanca.web "Rellena el formulario"
            minibanca.web -> minibanca.gateway "POST /api/v1/transferencias + JWT + Idempotency-Key"
            minibanca.gateway -> minibanca.orquestador "Limitador OK: reenvía con X-Trace-Id"
            minibanca.orquestador -> minibanca.core "¿Cuánto ha transferido hoy?"
            minibanca.orquestador -> minibanca.core "Transferir (misma Idempotency-Key)"
            minibanca.core -> minibanca.bd "Transacción: bloquear cuentas, mover saldo, guardar"
            minibanca.core -> minibanca.redis "Borrar la caché de los dos titulares (tras el commit)"
            minibanca.orquestador -> minibanca.core "Auditar la transferencia"
            minibanca.orquestador -> notificaciones "Notificar (si falla, se audita; el dinero no vuelve)"
            autolayout lr
        }
        deployment minibanca local "Despliegue" "Docker Compose: solo nginx da al exterior." {
            include *
            autolayout lr
        }

        styles {
            element "Person" {
                shape Person
                background #08427b
                color #ffffff
            }
            element "Software System" {
                background #1168bd
                color #ffffff
            }
            element "Externo" {
                background #999999
                color #ffffff
            }
            element "Container" {
                background #438dd5
                color #ffffff
            }
            element "Web" {
                shape WebBrowser
            }
            element "BaseDeDatos" {
                shape Cylinder
            }
            element "Component" {
                background #85bbf0
                color #000000
            }
        }
    }
}
