import { expect, type APIRequestContext, type Page } from '@playwright/test';

/** Nombre de cliente único por prueba: aísla los datos aunque las pruebas corran en paralelo. */
export const uniqueCustomer = (prefix = 'Cliente E2E') =>
  `${prefix} ${Date.now()}-${Math.floor(Math.random() * 10_000)}`;

/** Arrange vía API (más rápido y estable que la UI); la UI se reserva para lo que se quiere verificar. */
export async function createOrderViaApi(request: APIRequestContext, cliente: string) {
  const response = await request.post('/api/orders', {
    data: { cliente, productos: [{ id: 'P001', cantidad: 2, valorUnitario: 100000 }] },
  });
  expect(response.status()).toBe(201);
  return (await response.json()) as { id: string; estado: string };
}

export const orderRow = (page: Page, cliente: string) =>
  page.getByTestId('order-row').filter({ hasText: cliente });
