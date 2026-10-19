@ignore
Feature: Helper reutilizable - crear una orden

  Scenario: Crear orden y retornar su id
    * def body = karate.get('order', read('classpath:karate/orders/data/valid-order.json'))
    Given url baseUrl
    And path 'orders'
    And request body
    When method post
    Then status 201
    * def orderId = response.id
