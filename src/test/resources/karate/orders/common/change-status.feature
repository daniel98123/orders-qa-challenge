@ignore
Feature: Helper reutilizable - mover una orden a un estado válido

  Scenario: Cambiar estado y verificar
    Given url baseUrl
    And path 'orders', id, 'status'
    And request { estado: '#(estado)' }
    When method patch
    Then status 200
    And match response.estado == estado
