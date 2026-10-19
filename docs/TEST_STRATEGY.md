# Estrategia de pruebas

> Cómo se decidió **qué** probar, **dónde** (capa de la pirámide) y **qué tan a fondo**, a partir del riesgo.

## 1. Enfoque

1. Cada regla de negocio tiene un id (`RN-01`…`RN-06`) que viaja por todo el ciclo: código de error de la API,
   tags de JUnit/Karate/Playwright, logs y matriz de trazabilidad.
2. El riesgo de cada regla (impacto × probabilidad) define la profundidad de prueba y en cuántas capas se valida.
3. Cada defecto se busca en la **capa más barata que puede detectarlo**; las capas altas solo verifican
   integración y flujo, no repiten combinatoria.
4. Los umbrales de calidad son automáticos (quality gates) y rompen el pipeline.

## 2. Análisis de riesgo

Escala 1 (bajo) – 3 (alto). Riesgo = impacto × probabilidad.

| Regla | Impacto | Prob. | Riesgo | Por qué | Mitigación |
|---|---|---|---|---|---|
| RN-03 Total automático | 3 | 3 | **9** | Error directo en el cobro; un cliente podría enviar su propio total | Unit (sumas, decimales, identidad) + Karate (total enviado por el cliente se ignora) + E2E (UI muestra el total de la API) |
| RN-05 Enviar sin pagar | 3 | 3 | **9** | Pérdida de mercancía sin pago | Unit (regla + caso positivo PAGADA→ENVIADA) + Karate obligatorio (400, estado sin cambios) + E2E (la UI no ofrece "Enviar") |
| RN-06 Transiciones inválidas | 3 | 3 | **9** | 25 combinaciones posibles; alta probabilidad de un hueco | Unit: matriz completa 25/25 + concurrencia. Karate: 20/25 priorizadas por riesgo |
| RN-04 Cancelar entregada | 3 | 2 | 6 | Reversos de dinero indebidos | Unit + Karate obligatorio (409, estado sin cambios) + E2E |
| RN-02 Cantidad > 0 | 3 | 2 | 6 | Cantidades negativas = totales negativos / inventario corrupto | Unit (0, −1, `Integer.MIN_VALUE`, límite 1) + contrato HTTP + Karate |
| RN-01 Al menos un producto | 2 | 2 | 4 | Órdenes vacías ensucian la operación | Unit (lista vacía y nula) + contrato HTTP + Karate |

Riesgos técnicos identificados y cómo se cubren:

| Riesgo | Mitigación | Prueba |
|---|---|---|
| Dos cambios de estado simultáneos sobre la misma orden (lost update) | Agregado inmutable + `ConcurrentHashMap.computeIfPresent` | `InMemoryOrderRepositoryTest.concurrentTransitionsAreSerialized` (32 hilos, solo 1 pago exitoso) |
| Fuga de información en errores 500 | Mensaje genérico + `correlationId`; stacktrace solo en logs | `OrderControllerTest.unexpectedErrorDoesNotLeakDetails` |
| Inyección de logs vía `X-Correlation-Id` (CWE-117) | Lista blanca `[A-Za-z0-9-]{1,64}` | `CorrelationIdFilterTest`, `OrderControllerTest.generatesCorrelationId` |
| Datos personales en logs | Los logs registran id de orden, nunca el nombre del cliente | Revisión de código |
| Validaciones solo en la capa web | El dominio valida de nuevo (defensa en profundidad) | `OrderItemTest`, `OrderTest`, `mapsDomainValidationTo400` |
| Regresión del camino feliz | Escenario `@smoke` de ciclo completo | Karate `Ciclo de vida completo` (también contra el contenedor en CI) |

## 3. Pirámide de pruebas

```
                 ▲   E2E Playwright ............ 7 flujos de UI (stack Docker completo)
                ▲▲▲  Aceptación Karate ......... 29 escenarios sobre HTTP real
             ▲▲▲▲▲▲▲ Componente (@WebMvcTest) .. 28 casos de contrato HTTP y errores
       ▲▲▲▲▲▲▲▲▲▲▲▲▲ Unitarias ................. 50 métodos (+ parametrizados ≈ 100 ejecuciones)
```

| Capa | Herramienta | Qué valida | Qué NO valida (a propósito) |
|---|---|---|---|
| Unitaria | JUnit 5, AssertJ, Mockito | Reglas de dominio, máquina de estados (25/25), cálculos, valores límite, servicio con repositorio simulado, concurrencia | HTTP, serialización |
| Componente | `@WebMvcTest` + MockMvc | Contrato JSON, validaciones Bean Validation, mapeo excepción → HTTP (400/404/405/409/415/500), correlation id | Lógica de negocio (servicio simulado) |
| Smoke | `@SpringBootTest` | Arranque del contexto, `/actuator/health`, OpenAPI publicado | — |
| Aceptación | Karate | Reglas de negocio de punta a punta sobre HTTP real, esquema de respuesta, efectos secundarios (el estado no cambia tras un error) | Combinatoria exhaustiva (ya está en unit) |
| E2E | Playwright | Flujos de usuario en la UI con API real; la UI solo ofrece acciones válidas | Validaciones de datos (ya están abajo) |

Patrones usados: *Arrange* por API en E2E (rápido y estable), datos únicos por prueba (paralelismo seguro),
`TestData.orderIn(estado)` para construir órdenes en cualquier estado, helpers Karate `createOrder`/`moveTo`.

## 4. Quality gates

| Gate | Umbral | Dónde se aplica | Efecto |
|---|---|---|---|
| Pruebas fallidas | 0 | Surefire, Failsafe (Karate), Playwright, unittest | Rompe el pipeline |
| Cobertura de líneas | ≥ 80 % | `jacoco:check` (fase `test`) | Rompe el pipeline |
| Cobertura de ramas | ≥ 80 % | `jacoco:check` (fase `test`) | Rompe el pipeline |
| Mutation score | ≥ 70 % | PIT `mutationThreshold` | Rompe el pipeline |
| Trazabilidad | Toda RN probada en sus capas requeridas | `traceability.py --fail-on-gaps` | Rompe el pipeline |

La cobertura se mide **solo con pruebas unitarias/componente** (Karate corre sin agente JaCoCo): así el gate
no se infla con pruebas de alto nivel que ejecutan código sin verificarlo en detalle.
El mutation testing complementa la cobertura: una línea cubierta sin aserciones útiles deja mutantes vivos.

## 5. Decisiones sobre ambigüedades del enunciado

| Tema | Decisión | Justificación |
|---|---|---|
| ¿Se puede cancelar una orden ENVIADA? | Sí | RN-04 solo prohíbe cancelar ENTREGADA; aplicar la regla tal como está escrita (devolución en tránsito). |
| Código HTTP de RN-05 | 400 | Exigido por el reto. Se interpreta como "petición inválida para el estado actual". |
| Códigos de RN-04 y RN-06 | 409 Conflict | El recurso existe pero su estado actual impide la operación. |
| `DELETE /orders/{id}` | Cancelación lógica → 200 con la orden en CANCELADA | Las órdenes de compra no se borran (auditoría); devolver el cuerpo permite verificar el resultado. |
| Cancelar dos veces | 409 (RN-06) | CANCELADA es terminal; evita efectos repetidos silenciosos. |
| `total`/`estado` enviados al crear | Se ignoran | RN-03: el total lo calcula el servidor; una orden siempre nace CREADA. |
| Valor unitario | Debe ser > 0 | Regla implícita: un precio ≤ 0 produce totales inválidos. Documentada como validación adicional. |
| Cliente | Obligatorio, ≤ 120 caracteres | Integridad mínima del dato. |
| Persistencia | En memoria detrás de un puerto (`OrderRepository`) | El reto no exige BD; cambiarla es un adaptador nuevo sin tocar dominio ni pruebas de dominio. |

## 6. Riesgo residual aceptado

- 5 transiciones inválidas de prioridad MEDIA (auto-transiciones y saltos hacia atrás no terminales) no tienen
  escenario Karate; están cubiertas en unit (matriz 25/25) y el código que las rechaza es el mismo.
- Sin pruebas de carga ni de seguridad dinámica (DAST): fuera del alcance del reto; serían los siguientes pasos
  (k6/Gatling y OWASP ZAP en el pipeline).
- El almacenamiento en memoria se pierde al reiniciar: aceptable para el reto, no para producción.
- 2 mutantes sobrevivientes de PIT en `CorrelationIdFilter` (aritmética de `durationMs`): ese valor solo se
  escribe en el log de acceso y no afecta ninguna respuesta. Matarlos exigiría pruebas acopladas al reloj y al
  formato del log, con más costo de mantenimiento que valor. Cualquier otro mutante sobreviviente se revisa:
  el del `title` de los errores de validación reveló una aserción faltante y se cerró.

## 7. Cómo agregar una regla nueva

1. Agregar `RN-07` a `docs/requirements.json` con impacto, probabilidad y capas requeridas.
2. Correr `python ai-tools/traceability/traceability.py`: el gate falla hasta que existan pruebas.
3. Implementar con TDD en el dominio (`@Tag("RN-07")`), luego contrato HTTP y escenario Karate `@RN-07`.
4. Verificar cobertura y mutation score; revisar mutantes sobrevivientes antes de dar por cerrada la regla.
