/**
 * Interfaces espejo de los DTOs del backend (Spring Boot).
 *
 * Correspondencia de tipos Java -> TypeScript:
 *   Long / Integer / BigDecimal / Double  -> number
 *   String                                -> string
 *   Date / LocalDate                      -> string (ISO 8601: "2026-10-14" o "2026-10-14T10:30:00.000+00:00")
 *   List<T>                               -> T[]
 *   campo que puede ser null en Java      -> campo?: T | null
 *
 * IMPORTANTE: los nombres de campo de abajo son los que se asumieron a partir de la consigna.
 * Si algún nombre difiere en los DTOs Java de tus compañeros, corregilo SOLO acá
 * y el compilador te marca todo lo que haya que ajustar en el resto del código.
 */

/**
 * Estados de un comprobante (mismo enum EstadoFactura del backend).
 * BORRADOR: editable. EMITIDA: con CAE de ARCA es solo lectura; una Factura X emitida sigue editable.
 * ANULADA: solo lectura.
 */
export type EstadoFactura = "BORRADOR" | "EMITIDA" | "ANULADA";

export const ESTADO_BORRADOR: EstadoFactura = "BORRADOR";
export const ESTADO_EMITIDA: EstadoFactura = "EMITIDA";
export const ESTADO_ANULADA: EstadoFactura = "ANULADA";

/** Letra del comprobante. A, B y C van a ARCA; X es interna (no fiscal). */
export type LetraComprobante = "A" | "B" | "C" | "X";

// ---------------------------------------------------------------------------
// Entrada (POST / PUT)
// ---------------------------------------------------------------------------

/** Renglón de la factura que se envía al backend. Espejo de FacturaDetalleRequestDTO. */
export interface FacturaDetalleRequestDTO {
  articuloId: number;
  cantidad: number;
}

/**
 * Cuerpo de POST /api/facturas (guarda un BORRADOR) y PUT /api/facturas/{id} (actualiza un borrador).
 * No viajan número, CAE, estado ni importes: los decide el backend (y el número, ARCA).
 */
export interface FacturaRequestDTO {
  clienteId: number;
  /** Opcional: si no se manda, se usa la condición que tiene cargada el cliente. */
  condicionIvaReceptor?: CondicionFiscal;
  puntoVentaId: number;
  tipoMonedaId: number;
  /** Opcional: si no se manda, se toma la lista del primer renglón. */
  listaPrecioId?: number;
  /** Fecha de factura. */
  fecha: string;
  /** Fecha de emisión (la que se informa a ARCA). Opcional: por defecto el día en que se emite. */
  fechaEmision?: string;
  /** Opcional: si no se manda, el backend calcula la que corresponde. */
  letra?: LetraComprobante;
  observaciones?: string;
  detalles: FacturaDetalleRequestDTO[];
}

/** Respuesta de GET /api/facturas/letra?condicionIva=... */
export interface LetraSugeridaDTO {
  letra: LetraComprobante;
  letrasPermitidas: LetraComprobante[];
  condicionIvaEmisor: CondicionFiscal;
  discriminaIva: boolean;
}

// ---------------------------------------------------------------------------
// Salida (GET / respuesta de POST y PUT)
// ---------------------------------------------------------------------------

/** Renglón tal como lo devuelve el backend dentro de FacturaReporteDTO. */
export interface FacturaDetalleReporteDTO {
  articuloId: number;
  articuloCodigo?: string | null;
  articuloDescripcion?: string | null;
  cantidad: number;
  precioUnitario: number;
  alicuotaIva?: number | null;
  subtotal: number;
}

/** Comprobante tal como lo devuelve el backend. Espejo de FacturaReporteDTO. */
export interface FacturaReporteDTO {
  id: number;
  /** null mientras es borrador: el número lo asigna ARCA junto con el CAE. */
  nroFactura?: string | null;
  numeroComprobante?: number | null;
  letra: LetraComprobante;
  tipoComprobante?: number | null;
  fecha: string;
  fechaEmision?: string | null;
  estado: EstadoFactura;
  /** true si se puede modificar: borradores y Facturas X (sin CAE). */
  editable: boolean;
  /** true si ARCA otorgó CAE. */
  informadaArca: boolean;
  cae?: string | null;
  fechaVtoCae?: string | null;
  clienteId: number;
  clienteNombre: string;
  clienteCuit?: string | null;
  condicionIvaReceptor: CondicionFiscal;
  condicionIvaReceptorDescripcion: string;
  puntoVentaId: number;
  puntoVenta: string;
  tipoMonedaId: number;
  tipoMoneda: string;
  listaPrecioId: number;
  listaPrecio: string;
  importeNeto: number;
  importeIva: number;
  importeTotal: number;
  observaciones?: string | null;
  fechaAnulacion?: string | null;
  /** Solo viene en GET /api/facturas/{id}; en el listado es null. */
  detalles?: FacturaDetalleReporteDTO[] | null;
}

// ---------------------------------------------------------------------------
// Catálogos para los modales "explorer"
// ---------------------------------------------------------------------------

/** Condición frente al IVA de un cliente. Mismos valores que el enum CondicionFiscal del backend. */
export type CondicionFiscal = "RESPONSABLE_INSCRIPTO" | "MONOTRIBUTO" | "EXENTO" | "CONSUMIDOR_FINAL";

/** Fila del explorer de clientes (GET /api/clientes). */
export interface ClienteDTO {
  id: number;
  nombre: string;
  /** Con guiones: "30-54668997-9". null = no identificado ante ARCA. */
  cuit?: string | null;
  condicionIva?: CondicionFiscal | null;
  condicionIvaDescripcion?: string | null;
  domicilio?: string | null;
  localidad?: string | null;
  provincia?: string | null;
}

/** Filtros del explorer de clientes. Sin ninguno, el backend lista todos (paginados). */
export interface ClienteFiltrosDTO {
  search?: string;
  id?: number;
  cuit?: string;
  condicionIva?: CondicionFiscal;
}

/** Opción del desplegable de punto de venta (GET /api/puntos-venta). */
export interface PuntoVentaDTO {
  id: number;
  numero: number;
  descripcion?: string | null;
}

/** Opción del desplegable de moneda (GET /api/monedas). */
export interface TipoMonedaDTO {
  id: number;
  codigo: string;
  descripcion?: string | null;
}

/** Opción del selector de lista de precios (GET /api/listas-precio). */
export interface ListaPrecioOpcionDTO {
  id: number;
  nombre: string;
}

/** Fila del explorer de artículos (GET /api/articulos): un artículo con su precio en una lista. */
export interface ArticuloPrecioDTO {
  /** ID de ListaPrecioArticulo: es el que se envía como articuloId en el renglón. */
  id: number;
  articuloId: number;
  codigo?: string | null;
  descripcion: string;
  /** Rubro: Electrónica, Ferretería, Servicios... */
  categoria?: string | null;
  /** unidad, kg, litro, metro, hora, caja, pack. */
  unidadMedida?: string | null;
  alicuotaIva?: number | null;
  listaPrecioId: number;
  listaPrecio: string;
  precio: number;
}

/** Filtros del explorer de artículos. Sin search, codigo ni id, el backend lista todos (paginados). */
export interface ArticuloFiltrosDTO {
  search?: string;
  codigo?: string;
  id?: number;
  listaPrecioId?: number;
}

/** Respuesta paginada de los explorers (clientes y artículos). "page" arranca en 1. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  limit: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

// ---------------------------------------------------------------------------
// Paginación (Spring PagedModel)
// ---------------------------------------------------------------------------

/** Respuesta paginada de GET /api/facturas. "number" arranca en 0. */
export interface PaginaDTO<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

// ---------------------------------------------------------------------------
// Filtros de búsqueda (query params de GET /api/facturas)
// ---------------------------------------------------------------------------

/** Todos los filtros son opcionales; los que no se informan no se envían. */
export interface FacturaFiltrosDTO {
  nroFactura?: string;
  clienteId?: number;
  informadaArca?: boolean;
  estado?: EstadoFactura;
  fechaDesde?: string;
  fechaHasta?: string;
}
