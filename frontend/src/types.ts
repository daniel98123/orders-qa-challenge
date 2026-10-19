export const ESTADOS = ['CREADA', 'PAGADA', 'ENVIADA', 'ENTREGADA', 'CANCELADA'] as const;
export type Estado = (typeof ESTADOS)[number];

export interface Producto {
  id: string;
  cantidad: number;
  valorUnitario: number;
}

export interface Orden {
  id: string;
  cliente: string;
  productos: Producto[];
  total: number;
  estado: Estado;
  transicionesPermitidas: Estado[];
  fechaCreacion: string;
  fechaActualizacion: string;
}

export interface NuevaOrden {
  cliente: string;
  productos: Producto[];
}

/** Respuesta de error RFC 7807 de la API. */
export interface Problema {
  title?: string;
  detail?: string;
  code?: string;
  correlationId?: string;
  errors?: { campo: string; mensaje: string }[];
}
