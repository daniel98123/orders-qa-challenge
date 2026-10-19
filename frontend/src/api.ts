import type { Estado, NuevaOrden, Orden, Problema } from './types';

const BASE = '/api/orders';

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly problem: Problema) {
    super(ApiError.describe(problem, status));
  }

  private static describe(problem: Problema, status: number): string {
    const detalle = problem.errors?.length
      ? problem.errors.map((e) => e.mensaje).join(' · ')
      : problem.detail ?? `Error HTTP ${status}`;
    return problem.code ? `[${problem.code}] ${detalle}` : detalle;
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(response.status, body as Problema);
  }
  return body as T;
}

export const api = {
  listar: (estado?: Estado) => request<Orden[]>(estado ? `${BASE}?estado=${estado}` : BASE),
  consultar: (id: string) => request<Orden>(`${BASE}/${encodeURIComponent(id)}`),
  crear: (orden: NuevaOrden) => request<Orden>(BASE, { method: 'POST', body: JSON.stringify(orden) }),
  cambiarEstado: (id: string, estado: Estado) =>
    request<Orden>(`${BASE}/${id}/status`, { method: 'PATCH', body: JSON.stringify({ estado }) }),
  cancelar: (id: string) => request<Orden>(`${BASE}/${id}`, { method: 'DELETE' }),
};
