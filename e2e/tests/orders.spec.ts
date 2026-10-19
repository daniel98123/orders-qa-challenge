import { expect, test } from '@playwright/test';
import { createOrderViaApi, orderRow, uniqueCustomer } from './helpers';

test.describe('Gestión de órdenes de compra (E2E)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Órdenes de compra' })).toBeVisible();
  });

  test('Crear orden: el total lo calcula la API @RN-03', async ({ page }) => {
    const cliente = uniqueCustomer();

    await page.getByTestId('customer-input').fill(cliente);
    await page.getByTestId('product-id-0').fill('P001');
    await page.getByTestId('product-qty-0').fill('2');
    await page.getByTestId('product-price-0').fill('100000');
    await page.getByTestId('add-product').click();
    await page.getByTestId('product-id-1').fill('P002');
    await page.getByTestId('product-qty-1').fill('3');
    await page.getByTestId('product-price-1').fill('2500');
    await page.getByTestId('create-order').click();

    await expect(page.getByTestId('success-message')).toContainText('Orden creada');
    const row = orderRow(page, cliente);
    await expect(row).toHaveCount(1);
    await expect(row.getByTestId('order-status')).toHaveText('CREADA');
    await expect(row.getByTestId('row-total')).toContainText('207.500');
  });

  test('Consultar orden por id', async ({ page, request }) => {
    const cliente = uniqueCustomer();
    const orden = await createOrderViaApi(request, cliente);

    await page.getByTestId('search-input').fill(orden.id);
    await page.getByTestId('search-button').click();

    const detalle = page.getByTestId('order-detail');
    await expect(detalle).toBeVisible();
    await expect(detalle.getByTestId('detail-customer')).toHaveText(cliente);
    await expect(detalle.getByTestId('order-status')).toHaveText('CREADA');
    await expect(detalle.getByTestId('detail-total')).toContainText('200.000');
  });

  test('Consultar una orden inexistente muestra el error de la API', async ({ page }) => {
    await page.getByTestId('search-input').fill('00000000-0000-0000-0000-000000000000');
    await page.getByTestId('search-button').click();

    await expect(page.getByTestId('error-message')).toContainText('ORDER_NOT_FOUND');
  });

  test('Actualizar estado: ciclo de vida completo hasta ENTREGADA @RN-06 @RN-04', async ({ page, request }) => {
    const cliente = uniqueCustomer();
    await createOrderViaApi(request, cliente);
    await page.reload();
    const row = orderRow(page, cliente);

    for (const [accion, estado] of [
      ['PAGADA', 'PAGADA'],
      ['ENVIADA', 'ENVIADA'],
      ['ENTREGADA', 'ENTREGADA'],
    ] as const) {
      await row.getByTestId(`action-${accion}`).click();
      await expect(row.getByTestId('order-status')).toHaveText(estado);
    }

    // RN-04: una orden entregada ya no ofrece la acción de cancelar
    await expect(row.getByTestId('action-CANCELADA')).toHaveCount(0);
    await expect(row).toContainText('Sin acciones');
  });

  test('Una orden sin pagar no ofrece la acción Enviar @RN-05', async ({ page, request }) => {
    const cliente = uniqueCustomer();
    await createOrderViaApi(request, cliente);
    await page.reload();
    const row = orderRow(page, cliente);

    await expect(row.getByTestId('action-PAGADA')).toBeVisible();
    await expect(row.getByTestId('action-ENVIADA')).toHaveCount(0);
  });

  test('Cancelar orden', async ({ page, request }) => {
    const cliente = uniqueCustomer();
    await createOrderViaApi(request, cliente);
    await page.reload();
    const row = orderRow(page, cliente);

    await row.getByTestId('action-CANCELADA').click();

    await expect(row.getByTestId('order-status')).toHaveText('CANCELADA');
    await expect(row).toContainText('Sin acciones');
  });

  test('Filtrar la lista por estado', async ({ page, request }) => {
    const cliente = uniqueCustomer();
    await createOrderViaApi(request, cliente);
    await page.reload();

    await page.getByTestId('status-filter').selectOption('ENTREGADA');
    await expect(orderRow(page, cliente)).toHaveCount(0);

    await page.getByTestId('status-filter').selectOption('CREADA');
    await expect(orderRow(page, cliente)).toHaveCount(1);
  });
});
