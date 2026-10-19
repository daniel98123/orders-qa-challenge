## Matriz de trazabilidad (RN → pruebas)

| Regla | Riesgo | Unit | Aceptación (Karate) | E2E (Playwright) | Estado |
|---|---|---|---|---|---|
| **RN-03** El valor total de la orden debe calcularse automáticamente. | 9 | ✅ 6 | ✅ 2 | ✅ 1 | ✅ OK |
| **RN-05** Una orden no puede ser enviada si no ha sido pagada. | 9 | ✅ 4 | ✅ 1 | ✅ 1 | ✅ OK |
| **RN-06** No se deben permitir transiciones inválidas de estado. | 9 | ✅ 2 | ✅ 3 | ✅ 1 | ✅ OK |
| **RN-02** La cantidad de cada producto debe ser mayor que cero. | 6 | ✅ 2 | ✅ 1 | — | ✅ OK |
| **RN-04** Una orden entregada no puede ser cancelada. | 6 | ✅ 3 | ✅ 2 | ✅ 1 | ✅ OK |
| **RN-01** Una orden debe contener al menos un producto. | 4 | ✅ 5 | ✅ 1 | — | ✅ OK |

## Cobertura de la máquina de estados

- Transiciones probadas en unit: **25/25**
- Transiciones probadas en aceptación: **20/25**

| Origen \ Destino | CREADA | PAGADA | ENVIADA | ENTREGADA | CANCELADA |
|---|---|---|---|---|---|
| **CREADA** | ✖️ `U·` | ✔️ `UA` | ✖️ `UA` | ✖️ `UA` | ✔️ `UA` |
| **PAGADA** | ✖️ `UA` | ✖️ `U·` | ✔️ `UA` | ✖️ `U·` | ✔️ `UA` |
| **ENVIADA** | ✖️ `U·` | ✖️ `UA` | ✖️ `U·` | ✔️ `UA` | ✔️ `UA` |
| **ENTREGADA** | ✖️ `UA` | ✖️ `UA` | ✖️ `UA` | ✖️ `UA` | ✖️ `UA` |
| **CANCELADA** | ✖️ `UA` | ✖️ `UA` | ✖️ `UA` | ✖️ `UA` | ✖️ `UA` |

✔️ permitida · ✖️ inválida · `U` unit · `A` aceptación

## Escenarios de aceptación faltantes (priorizados por riesgo)

- [MEDIA] CREADA → CREADA (inválida)
- [MEDIA] ENVIADA → CREADA (inválida)
- [MEDIA] ENVIADA → ENVIADA (inválida)
- [MEDIA] PAGADA → ENTREGADA (inválida)
- [MEDIA] PAGADA → PAGADA (inválida)

Esqueletos Karate generados en `missing-scenarios.feature`.
