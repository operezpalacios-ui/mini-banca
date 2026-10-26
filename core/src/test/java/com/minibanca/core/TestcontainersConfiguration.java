package com.minibanca.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Lección 12: TESTCONTAINERS = para cada ejecución de tests se arranca un PostgreSQL de verdad
 * dentro de Docker, vacío y desechable. Flyway le aplica V1..V7 y los tests trabajan sobre él.
 * Nunca se toca tu base de datos de Neon.
 *
 * @ServiceConnection: Spring lee del contenedor la URL, usuario y contraseña y se conecta solo.
 *
 * ⚠️ Necesita Docker. En tu ordenador no lo hay, así que estos tests se SALTAN (ver
 * @Testcontainers(disabledWithoutDocker = true) en cada test). En GitHub Actions sí hay Docker:
 * allí se ejecutarán (lección de CI/CD).
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Versión fija (no "latest"): los tests deben dar lo mismo hoy que dentro de un año.
		// La 18, como Neon.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

}
