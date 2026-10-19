# Escenarios adicionales (el reto permite incluirlos con justificación).
# Cada escenario indica el RIESGO que mitiga; la priorización completa está en docs/TEST_STRATEGY.md.
Feature: Órdenes de compra - escenarios adicionales basados en riesgo

  Background:
    * url baseUrl
    * def validOrder = read('classpath:karate/orders/data/valid-order.json')
    * def orderSchema = read('classpath:karate/orders/data/order-schema.json')
    * def errorSchema = read('classpath:karate/orders/data/error-schema.json')
    * def createOrder = function(order){ return karate.call('classpath:karate/orders/common/create-order.feature', { order: order }).orderId }
    * def moveTo = function(id, estados){ for (var i = 0; i < estados.length; i++) karate.call('classpath:karate/orders/common/change-status.feature', { id: id, estado: estados[i] }) }

  # RIESGO ALTO (impacto económico): un cliente podría manipular el total o el estado inicial.
  @adicional @RN-03
  Scenario: El total lo calcula el servidor e ignora total/estado enviados por el cliente
    Given path 'orders'
    And request
      """
      {
        "cliente": "Ana Gómez",
        "productos": [
          { "id": "P001", "cantidad": 2, "valorUnitario": 100000 },
          { "id": "P002", "cantidad": 3, "valorUnitario": 2500 }
        ],
        "total": 1,
        "estado": "ENTREGADA"
      }
      """
    When method post
    Then status 201
    And match response.total == 207500
    And match response.estado == 'CREADA'

  # RIESGO ALTO: órdenes vacías o con cantidades no positivas generan cobros/inventario incorrectos.
  @adicional
  Scenario Outline: Rechaza órdenes inválidas - <caso>
    Given path 'orders'
    And request { cliente: 'Juan', productos: '#(productos)' }
    When method post
    Then status 400
    And match response == errorSchema
    And match response.code == 'VALIDATION'
    And match response.errors[0].mensaje contains '<regla>'

    @RN-01
    Examples:
      | caso             | productos! | regla |
      | lista vacía      | []         | RN-01 |

    @RN-02
    Examples:
      | caso              | productos!                                         | regla |
      | cantidad cero     | [{ id: 'P001', cantidad: 0, valorUnitario: 10 }]  | RN-02 |
      | cantidad negativa | [{ id: 'P001', cantidad: -5, valorUnitario: 10 }] | RN-02 |

  # RIESGO MEDIO: el camino feliz completo debe seguir funcionando tras cualquier cambio (regresión).
  @adicional @smoke @RN-06 @transition=CREADA_PAGADA,PAGADA_ENVIADA,ENVIADA_ENTREGADA
  Scenario: Ciclo de vida completo CREADA -> PAGADA -> ENVIADA -> ENTREGADA
    * def orderId = createOrder(validOrder)
    * eval moveTo(orderId, ['PAGADA', 'ENVIADA', 'ENTREGADA'])
    Given path 'orders', orderId
    When method get
    Then status 200
    And match response.estado == 'ENTREGADA'
    And match response.transicionesPermitidas == []

  # RIESGO ALTO: transiciones ilegales corrompen el flujo logístico/financiero (RN-06).
  # Se priorizan saltos de estado y "resurrección" de estados terminales (ENTREGADA/CANCELADA).
  # Las 6 últimas filas (CANCELADA/ENTREGADA -> X) las señaló ai-tools/traceability como
  # brechas de prioridad ALTA. Las de prioridad MEDIA quedan cubiertas en unit (OrderStatusTest, 25/25).
  @adicional @RN-06
  Scenario Outline: Rechaza la transición inválida <origen> -> <destino>
    * def orderId = createOrder(validOrder)
    * eval moveTo(orderId, camino)
    Given path 'orders', orderId, 'status'
    And request { estado: '<destino>' }
    When method patch
    Then status 409
    And match response == errorSchema
    And match response.code == 'RN-06'

    Examples:
      | origen    | camino!                             | destino   |
      | CREADA    | []                                  | ENTREGADA |
      | PAGADA    | ['PAGADA']                          | CREADA    |
      | ENVIADA   | ['PAGADA', 'ENVIADA']               | PAGADA    |
      | ENTREGADA | ['PAGADA', 'ENVIADA', 'ENTREGADA']  | ENVIADA   |
      | CANCELADA | ['CANCELADA']                       | PAGADA    |
      | CANCELADA | ['CANCELADA']                       | CREADA    |
      | CANCELADA | ['CANCELADA']                       | ENVIADA   |
      | CANCELADA | ['CANCELADA']                       | ENTREGADA |
      | ENTREGADA | ['PAGADA', 'ENVIADA', 'ENTREGADA']  | CREADA    |
      | ENTREGADA | ['PAGADA', 'ENVIADA', 'ENTREGADA']  | PAGADA    |
      | ENTREGADA | ['PAGADA', 'ENVIADA', 'ENTREGADA']  | ENTREGADA |

  # RIESGO MEDIO: la cancelación es la operación de compensación; debe funcionar en todo estado no final.
  @adicional @RN-04 @transition=CREADA_CANCELADA,PAGADA_CANCELADA,ENVIADA_CANCELADA
  Scenario Outline: Cancela (DELETE) una orden en estado <origen>
    * def orderId = createOrder(validOrder)
    * eval moveTo(orderId, camino)
    Given path 'orders', orderId
    When method delete
    Then status 200
    And match response.estado == 'CANCELADA'

    Examples:
      | origen  | camino!               |
      | CREADA  | []                    |
      | PAGADA  | ['PAGADA']            |
      | ENVIADA | ['PAGADA', 'ENVIADA'] |

  # RIESGO MEDIO: doble cancelación (reintentos del cliente) no debe producir efectos repetidos.
  @adicional @RN-06 @transition=CANCELADA_CANCELADA
  Scenario: Cancelar dos veces la misma orden responde 409
    * def orderId = createOrder(validOrder)
    Given path 'orders', orderId
    When method delete
    Then status 200
    Given path 'orders', orderId
    When method delete
    Then status 409
    And match response.code == 'RN-06'

  # RIESGO MEDIO: robustez del contrato ante ids inexistentes o mal formados.
  @adicional @errores
  Scenario: Operaciones sobre una orden inexistente responden 404
    * def missingId = java.util.UUID.randomUUID() + ''
    Given path 'orders', missingId
    When method get
    Then status 404
    And match response.code == 'ORDER_NOT_FOUND'
    Given path 'orders', missingId, 'status'
    And request { estado: 'PAGADA' }
    When method patch
    Then status 404
    Given path 'orders', missingId
    When method delete
    Then status 404

  @adicional @errores
  Scenario: Un id que no es UUID responde 400
    Given path 'orders', 'no-es-un-uuid'
    When method get
    Then status 400

  @adicional @errores
  Scenario: Un estado desconocido en el PATCH responde 400
    * def orderId = createOrder(validOrder)
    Given path 'orders', orderId, 'status'
    And request { estado: 'PERDIDA' }
    When method patch
    Then status 400
    And match response.code == 'MALFORMED_REQUEST'

  # RIESGO BAJO-MEDIO: el listado y su filtro alimentan el frontend.
  @adicional
  Scenario: Listar órdenes y filtrar por estado
    * def orderId = createOrder(validOrder)
    * eval moveTo(orderId, ['PAGADA'])
    Given path 'orders'
    When method get
    Then status 200
    And match response[*].id contains orderId
    Given path 'orders'
    And param estado = 'PAGADA'
    When method get
    Then status 200
    And match response[*].id contains orderId
    And match each response contains { estado: 'PAGADA' }
    Given path 'orders'
    And param estado = 'CREADA'
    When method get
    Then status 200
    And match response[*].id !contains orderId

  # Observabilidad: el correlation id permite cruzar una respuesta con los logs del servidor.
  @adicional @observabilidad
  Scenario: La API devuelve el X-Correlation-Id recibido
    # configure headers (no "header") porque karate-config.js ya fija un X-Correlation-Id global que tiene prioridad
    * configure headers = { 'X-Correlation-Id': 'karate-trace-001' }
    Given path 'orders'
    When method get
    Then status 200
    And match karate.response.header('X-Correlation-Id') == 'karate-trace-001'
