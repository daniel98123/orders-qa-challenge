import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { api } from './api';
import { ESTADOS, type Estado, type Orden, type Producto } from './types';

const ACCIONES: Record<Estado, string> = {
  CREADA: 'Crear',
  PAGADA: 'Pagar',
  ENVIADA: 'Enviar',
  ENTREGADA: 'Entregar',
  CANCELADA: 'Cancelar',
};

const moneda = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 2 });

type Aviso = { tipo: 'ok' | 'error'; texto: string } | null;

const productoVacio = (): Producto => ({ id: '', cantidad: 1, valorUnitario: 0 });

function mensajeDeError(error: unknown): string {
  return error instanceof Error ? error.message : 'Error inesperado';
}

export default function App() {
  const [ordenes, setOrdenes] = useState<Orden[]>([]);
  const [filtro, setFiltro] = useState<Estado | ''>('');
  const [aviso, setAviso] = useState<Aviso>(null);
  const [cargando, setCargando] = useState(false);
  // "vez" cambia en cada clic para volver a consultar aunque se elija la misma orden
  const [seleccion, setSeleccion] = useState<{ id: string; vez: number } | null>(null);

  const mostrarError = useCallback((texto: string) => setAviso({ tipo: 'error', texto }), []);

  const recargar = useCallback(async () => {
    try {
      setOrdenes(await api.listar(filtro || undefined));
    } catch (error) {
      setAviso({ tipo: 'error', texto: mensajeDeError(error) });
    }
  }, [filtro]);

  useEffect(() => {
    void recargar();
  }, [recargar]);

  const ejecutar = async (accion: () => Promise<Orden>, exito: (orden: Orden) => string): Promise<boolean> => {
    setCargando(true);
    try {
      const orden = await accion();
      setAviso({ tipo: 'ok', texto: exito(orden) });
      await recargar();
      return true;
    } catch (error) {
      setAviso({ tipo: 'error', texto: mensajeDeError(error) });
      return false;
    } finally {
      setCargando(false);
    }
  };

  const cambiarEstado = (orden: Orden, destino: Estado) =>
    void ejecutar(
      () => (destino === 'CANCELADA' ? api.cancelar(orden.id) : api.cambiarEstado(orden.id, destino)),
      (o) => `Orden ${o.id.slice(0, 8)} ahora está ${o.estado}`,
    );

  return (
    <div className="page">
      <header className="masthead">
        <div>
          <p className="eyebrow">Reto QA · Gestión de compras</p>
          <h1>Órdenes de compra</h1>
        </div>
        <a className="docs-link" href="/api/swagger-ui.html" target="_blank" rel="noreferrer">
          Documentación API
        </a>
      </header>

      {aviso && (
        <div
          className={`aviso aviso-${aviso.tipo}`}
          role={aviso.tipo === 'error' ? 'alert' : 'status'}
          data-testid={aviso.tipo === 'error' ? 'error-message' : 'success-message'}
        >
          <span>{aviso.texto}</span>
          <button type="button" className="link" onClick={() => setAviso(null)} aria-label="Cerrar aviso">
            ×
          </button>
        </div>
      )}

      <main className="grid">
        <section className="panel">
          <h2>Nueva orden</h2>
          <FormularioOrden
            deshabilitado={cargando}
            onCrear={(nueva) => ejecutar(() => api.crear(nueva), (o) => `Orden creada: ${o.id}`)}
          />
        </section>

        <section className="panel">
          <h2>Consultar orden</h2>
          <ConsultaOrden seleccion={seleccion} onError={mostrarError} />
        </section>

        <section className="panel panel-wide">
          <div className="panel-head">
            <h2>Órdenes</h2>
            <label className="inline">
              Estado
              <select
                data-testid="status-filter"
                value={filtro}
                onChange={(e) => setFiltro(e.target.value as Estado | '')}
              >
                <option value="">Todos</option>
                {ESTADOS.map((estado) => (
                  <option key={estado} value={estado}>
                    {estado}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <TablaOrdenes
            ordenes={ordenes}
            deshabilitado={cargando}
            onCambiarEstado={cambiarEstado}
            onVer={(id) => setSeleccion((previa) => ({ id, vez: (previa?.vez ?? 0) + 1 }))}
          />
        </section>
      </main>
    </div>
  );
}

function FormularioOrden({
  deshabilitado,
  onCrear,
}: {
  deshabilitado: boolean;
  onCrear: (orden: { cliente: string; productos: Producto[] }) => Promise<boolean>;
}) {
  const [cliente, setCliente] = useState('');
  const [productos, setProductos] = useState<Producto[]>([productoVacio()]);

  // Vista previa informativa: el total oficial siempre lo calcula la API (RN-03).
  const totalEstimado = useMemo(
    () => productos.reduce((suma, p) => suma + (p.cantidad || 0) * (p.valorUnitario || 0), 0),
    [productos],
  );

  const actualizar = (indice: number, cambio: Partial<Producto>) =>
    setProductos((actuales) => actuales.map((p, i) => (i === indice ? { ...p, ...cambio } : p)));

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault();
    if (await onCrear({ cliente, productos })) {
      setCliente('');
      setProductos([productoVacio()]);
    }
  };

  return (
    <form onSubmit={enviar} data-testid="order-form">
      <label>
        Cliente
        <input
          data-testid="customer-input"
          value={cliente}
          onChange={(e) => setCliente(e.target.value)}
          placeholder="Juan Pérez"
          required
        />
      </label>

      <div className="productos">
        {productos.map((producto, i) => (
          <fieldset key={i} className="producto" data-testid="product-row">
            <legend>Producto {i + 1}</legend>
            <label>
              Id
              <input
                data-testid={`product-id-${i}`}
                value={producto.id}
                onChange={(e) => actualizar(i, { id: e.target.value })}
                placeholder="P001"
                required
              />
            </label>
            <label>
              Cantidad
              <input
                data-testid={`product-qty-${i}`}
                type="number"
                min={1}
                value={producto.cantidad}
                onChange={(e) => actualizar(i, { cantidad: Number(e.target.value) })}
                required
              />
            </label>
            <label>
              Valor unitario
              <input
                data-testid={`product-price-${i}`}
                type="number"
                min={0.01}
                step="0.01"
                value={producto.valorUnitario}
                onChange={(e) => actualizar(i, { valorUnitario: Number(e.target.value) })}
                required
              />
            </label>
            {productos.length > 1 && (
              <button
                type="button"
                className="link"
                onClick={() => setProductos((actuales) => actuales.filter((_, j) => j !== i))}
              >
                Quitar
              </button>
            )}
          </fieldset>
        ))}
      </div>

      <div className="form-actions">
        <button
          type="button"
          className="secondary"
          data-testid="add-product"
          onClick={() => setProductos((actuales) => [...actuales, productoVacio()])}
        >
          + Agregar producto
        </button>
        <span className="estimado">Total estimado: {moneda.format(totalEstimado)}</span>
      </div>
      <button type="submit" data-testid="create-order" disabled={deshabilitado}>
        Crear orden
      </button>
    </form>
  );
}

function ConsultaOrden({
  seleccion,
  onError,
}: {
  seleccion: { id: string; vez: number } | null;
  onError: (texto: string) => void;
}) {
  const [id, setId] = useState('');
  const [orden, setOrden] = useState<Orden | null>(null);

  const buscar = useCallback(
    async (valor: string) => {
      setOrden(null);
      try {
        setOrden(await api.consultar(valor));
      } catch (error) {
        onError(mensajeDeError(error));
      }
    },
    [onError],
  );

  // Clic en el id de la tabla: muestra el id completo en el buscador y consulta la orden
  useEffect(() => {
    if (seleccion) {
      setId(seleccion.id);
      void buscar(seleccion.id);
    }
  }, [seleccion, buscar]);

  const consultar = (evento: FormEvent) => {
    evento.preventDefault();
    void buscar(id.trim());
  };

  return (
    <>
      <form onSubmit={consultar} className="search">
        <input
          data-testid="search-input"
          value={id}
          onChange={(e) => setId(e.target.value)}
          placeholder="Id de la orden (UUID)"
          required
        />
        <button type="submit" data-testid="search-button">
          Consultar
        </button>
      </form>
      {orden && (
        <dl className="detalle" data-testid="order-detail">
          <dt>Id</dt>
          <dd className="mono">{orden.id}</dd>
          <dt>Cliente</dt>
          <dd data-testid="detail-customer">{orden.cliente}</dd>
          <dt>Estado</dt>
          <dd>
            <Insignia estado={orden.estado} />
          </dd>
          <dt>Total</dt>
          <dd data-testid="detail-total">{moneda.format(orden.total)}</dd>
          <dt>Productos</dt>
          <dd>
            {orden.productos.map((p) => `${p.id} × ${p.cantidad}`).join(', ')}
          </dd>
        </dl>
      )}
    </>
  );
}

function TablaOrdenes({
  ordenes,
  deshabilitado,
  onCambiarEstado,
  onVer,
}: {
  ordenes: Orden[];
  deshabilitado: boolean;
  onCambiarEstado: (orden: Orden, destino: Estado) => void;
  onVer: (id: string) => void;
}) {
  if (ordenes.length === 0) {
    return <p className="vacio" data-testid="empty-list">No hay órdenes para mostrar.</p>;
  }
  return (
    <div className="tabla-scroll">
      <table>
        <thead>
          <tr>
            <th>Id</th>
            <th>Cliente</th>
            <th>Productos</th>
            <th className="num">Total</th>
            <th>Estado</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          {ordenes.map((orden) => (
            <tr key={orden.id} data-testid="order-row" data-order-id={orden.id}>
              <td>
                <button
                  type="button"
                  className="link id-link"
                  data-testid="row-id"
                  title={`Ver detalle de ${orden.id}`}
                  onClick={() => onVer(orden.id)}
                >
                  {orden.id.slice(0, 8)}
                </button>
              </td>
              <td data-testid="row-customer">{orden.cliente}</td>
              <td>{orden.productos.length}</td>
              <td className="num" data-testid="row-total">
                {moneda.format(orden.total)}
              </td>
              <td>
                <Insignia estado={orden.estado} />
              </td>
              <td>
                <div className="acciones">
                  {orden.transicionesPermitidas.length === 0 && <span className="muted">Sin acciones</span>}
                  {orden.transicionesPermitidas.map((destino) => (
                    <button
                      key={destino}
                      type="button"
                      className={destino === 'CANCELADA' ? 'danger small' : 'small'}
                      data-testid={`action-${destino}`}
                      disabled={deshabilitado}
                      onClick={() => onCambiarEstado(orden, destino)}
                    >
                      {ACCIONES[destino]}
                    </button>
                  ))}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Insignia({ estado }: { estado: Estado }) {
  return (
    <span className={`insignia insignia-${estado.toLowerCase()}`} data-testid="order-status">
      {estado}
    </span>
  );
}
