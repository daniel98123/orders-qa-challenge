package com.reto.orders.acceptance;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de aceptación Karate (caja negra sobre HTTP real).
 * <ul>
 *   <li>Por defecto levanta la aplicación en un puerto aleatorio (ejecución local / CI).</li>
 *   <li>Con {@code -Dkarate.baseUrl=http://host:puerto} apunta a un ambiente ya desplegado
 *       (p. ej. el contenedor Docker en el pipeline).</li>
 *   <li>Filtrado por tags: {@code -Dkarate.options="--tags @obligatorio"}.</li>
 * </ul>
 * Reportes: target/karate-reports (HTML + Cucumber JSON + JUnit XML).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Aceptación Karate")
class OrdersAcceptanceIT {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Todos los escenarios Karate pasan")
    void runKarateSuite() {
        String baseUrl = System.getProperty("karate.baseUrl", "http://localhost:" + port);

        Results results = Runner.path("classpath:karate")
                .tags("~@ignore")
                .systemProperty("baseUrl", baseUrl)
                .outputCucumberJson(true)
                .outputJunitXml(true)
                .parallel(4);

        assertThat(results.getFailCount())
                .as("Escenarios fallidos:%n%s", results.getErrorMessages())
                .isZero();
        assertThat(results.getScenariosTotal()).as("Se ejecutó al menos un escenario").isPositive();
    }
}
