---
name: qa-test-designer
description: Diseña y genera pruebas basadas en riesgo para la API de órdenes cuando se agrega o cambia una regla de negocio, un estado o un endpoint. Úsalo para identificar escenarios faltantes, proponer casos por capa de la pirámide y mantener la matriz de trazabilidad.
---

# QA Test Designer (órdenes de compra)

Skill del repositorio para asistentes de IA (Claude Code u otros). Su objetivo es que la IA
**proponga** pruebas siguiendo la estrategia del proyecto; una persona revisa y decide qué se incorpora.

## Cuándo usarla
- Se agrega/modifica una regla `RN-xx`, un estado en `OrderStatus` o un endpoint.
- Se pide "¿qué pruebas faltan?" o "genera casos de prueba para ...".

## Pasos
1. **Contexto**: leer `docs/requirements.json`, `docs/TEST_STRATEGY.md` y
   `src/main/java/com/reto/orders/domain/OrderStatus.java` (fuente de verdad de transiciones).
2. **Diagnóstico determinista**: ejecutar
   `python ai-tools/traceability/traceability.py --out target/traceability`
   y leer `target/traceability/traceability.md` (brechas por regla y por transición).
3. **Riesgo**: para cada brecha calcular impacto x probabilidad (1-3 cada uno) y justificar en una línea.
4. **Elegir la capa más barata que detecta el defecto** (pirámide):
   - Lógica pura (cálculos, transiciones) → JUnit en `src/test/java/.../domain`, con `@Tag("RN-xx")`.
   - Contrato HTTP / códigos de error → `@WebMvcTest` en `OrderControllerTest`.
   - Flujo de negocio sobre HTTP real → Karate en `src/test/resources/karate/orders`,
     con tags `@RN-xx` y `@transition=ORIGEN_DESTINO`.
   - Solo lo que depende de la UI → Playwright en `e2e/tests`, con `@RN-xx` en el título.
5. **Escribir las pruebas** siguiendo los patrones existentes (helpers `createOrder`/`moveTo` en Karate,
   `TestData.orderIn(...)` en JUnit). Nunca debilitar aserciones existentes para que algo pase.
6. **Verificar**: `mvn verify` (incluye gate de cobertura y Karate), PIT
   (`mvn test-compile org.pitest:pitest-maven:mutationCoverage`) y volver a correr la trazabilidad.
7. **Reportar** al humano: pruebas agregadas, riesgo cubierto, mutantes sobrevivientes relevantes y
   lo que se decidió NO probar (con razón).

## Reglas
- No inventar reglas de negocio: si algo es ambiguo, preguntar y documentar la decisión en el README.
- No enviar código ni datos de clientes a servicios externos; la opción `--suggest` solo envía el
  resumen de brechas.
- Todo lo generado por IA se marca como borrador hasta que una persona lo revise.
