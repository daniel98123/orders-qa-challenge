# Evidencias

Esta carpeta guarda las capturas que el README enlaza. Se generan al ejecutar el proyecto:

| Archivo esperado | Cómo obtenerlo |
|---|---|
| `coverage.png` | `mvn test` → abrir `target/site/jacoco/index.html` y capturar el resumen |
| `mutation.png` | `mvn test-compile org.pitest:pitest-maven:mutationCoverage` → abrir `target/pit-reports/index.html` |
| `karate.png` | `mvn verify` → abrir `target/karate-reports/karate-summary.html` |
| `playwright.png` | `docker compose up -d --build` y `cd e2e && npx playwright test && npx playwright show-report` |
| `pipeline.png` | Pestaña **Actions** del repositorio en GitHub → ejecución en verde con el resumen de Quality Gates |
| `traceability.md` | `python ai-tools/traceability/traceability.py --out docs/evidence` |

Los reportes completos de cada ejecución del pipeline también quedan como *artifacts* descargables
(`api-reports`, `traceability`, `e2e-reports`).
