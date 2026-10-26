package com.minibanca.core;

import com.minibanca.core.service.CacheCuentas;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * El test más básico: "¿la aplicación ARRANCA?" (beans bien conectados, Flyway aplica las
 * migraciones, Hibernate valida que las entidades encajan con las tablas...).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CoreApplicationTests {

	@MockitoBean
	CacheCuentas cache;   // sin Redis en los tests

	@Test
	void contextLoads() {
	}

}
