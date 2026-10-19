package com.reto.orders;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: el contexto completo arranca, el health check responde y la documentación OpenAPI se
 * publica. Excluido de PIT (lento y no aporta a matar mutantes).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Smoke test de la aplicación")
class OrdersApplicationSmokeTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("El contexto arranca con un reloj UTC")
    void contextLoads() {
        assertThat(clock.getZone().getId()).isEqualTo("Z");
    }

    @Test
    @DisplayName("Health check en UP")
    void healthIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("La especificación OpenAPI está publicada")
    void openApiIsPublished() {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("/orders/{id}/status");
    }
}
