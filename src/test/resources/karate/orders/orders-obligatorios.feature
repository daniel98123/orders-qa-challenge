# Escenarios mínimos exigidos por el reto (sección 5).
# Tags: @obligatorio = exigido | @RN-xx = regla de negocio cubierta | @transition=ORIGEN_DESTINO = trazabilidad
# de la máquina de estados (los lee ai-tools/traceability para la matriz de cobertura).
Feature: Órdenes de compra - escenarios obligatorios

  Background:
    * url baseUrl
    * def validOrder = read('classpath:karate/orders/data/valid-order.json')
    * def orderSchema = read('classpath:karate/orders/data/order-schema.json')
    * def errorSchema = read('classpath:karate/orders/data/error-schema.json')
    * def createOrder = function(order){ return karate.call('classpath:karate/orders/common/create-order.feature', { order: order }).orderId }
    * def moveTo = function(id, estados){ for (var i = 0; i < estados.length; i++) karate.call('classpath:karate/orders/common/change-status.feature', { id: id, estado: estados[i] }) }

  @obligatorio @smoke @RN-03
  Scenario: Crear una orden válida
    Given path 'orders'
    And request validOrder
    When method post
    Then status 201
    And match response == orderSchema
    And match response.estado == 'CREADA'
    And match response.total == 200000
    And match response.productos[0] == { id: 'P001', cantidad: 2, valorUnitario: 100000 }
    And match karate.response.header('Location') contains response.id

  @obligatorio @smoke
  Scenario: Consultar una orden existente
    * def orderId = createOrder(validOrder)
    Given path 'orders', orderId
    When method get
    Then status 200
    And match response == orderSchema
    And match response.id == orderId
    And match response.cliente == validOrder.cliente

  @obligatorio @RN-05 @transition=CREADA_ENVIADA
  Scenario: Intentar enviar una orden no pagada
    * def orderId = createOrder(validOrder)
    Given path 'orders', orderId, 'status'
    And request { estado: 'ENVIADA' }
    When method patch
    Then status 400
    And match response == errorSchema
    And match response.code == 'RN-05'
    # La regla no debe dejar efectos secundarios: la orden sigue en CREADA
    Given path 'orders', orderId
    When method get
    Then status 200
    And match response.estado == 'CREADA'

  @obligatorio @RN-04 @transition=ENTREGADA_CANCELADA
  Scenario: Intentar cancelar una orden entregada
    * def orderId = createOrder(validOrder)
    * eval moveTo(orderId, ['PAGADA', 'ENVIADA', 'ENTREGADA'])
    Given path 'orders', orderId
    When method delete
    Then status 409
    And match response == errorSchema
    And match response.code == 'RN-04'
    Given path 'orders', orderId
    When method get
    Then match response.estado == 'ENTREGADA'
