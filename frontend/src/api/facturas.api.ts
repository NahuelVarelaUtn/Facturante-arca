import type {
  CondicionFiscal,
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  LetraSugeridaDTO,
  PaginaDTO,
} from "../models/factura.dto.js";
import { API_URL, eliminar, getBlob, getJson, sendJson } from "./http.js";

const URL_FACTURAS = `${API_URL}/facturas`;

/** GET /api/facturas?nroFactura=..&clienteId=..&estado=..&informadaArca=..&fechaDesde=..&fechaHasta=..&page=..&size=.. */
export function buscarFacturas(
  filtros: FacturaFiltrosDTO = {},
  pagina = 0,
  tamanio = 10,
): Promise<PaginaDTO<FacturaReporteDTO>> {
  return getJson(URL_FACTURAS, { ...filtros, page: pagina, size: tamanio });
}

/** GET /api/facturas/{id} -> la factura completa, con renglones. */
export function obtenerFactura(id: number): Promise<FacturaReporteDTO> {
  return getJson(`${URL_FACTURAS}/${id}`);
}

/** GET /api/facturas/letra?condicionIva=.. -> letra que corresponde a ese cliente y letras permitidas. */
export function sugerirLetra(condicionIva: CondicionFiscal): Promise<LetraSugeridaDTO> {
  return getJson(`${URL_FACTURAS}/letra`, { condicionIva });
}

/** POST /api/facturas -> guarda un BORRADOR (no se comunica con ARCA). */
export function crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
  return sendJson(URL_FACTURAS, "POST", factura);
}

/** PUT /api/facturas/{id} -> actualiza un borrador. 409 si la factura ya no es borrador. */
export function actualizarFactura(id: number, factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
  return sendJson(`${URL_FACTURAS}/${id}`, "PUT", factura);
}

/** POST /api/facturas/{id}/emitir -> pide el CAE a ARCA (o numera, si es X). 502 si ARCA rechaza. */
export function emitirFactura(id: number): Promise<FacturaReporteDTO> {
  return sendJson(`${URL_FACTURAS}/${id}/emitir`, "POST");
}

/** PUT /api/facturas/{id}/anular -> anulación lógica (no borra). Solo facturas emitidas. */
export function anularFactura(id: number): Promise<FacturaReporteDTO> {
  return sendJson(`${URL_FACTURAS}/${id}/anular`, "PUT");
}

/** DELETE /api/facturas/{id}. Solo borradores. */
export function eliminarFactura(id: number): Promise<void> {
  return eliminar(`${URL_FACTURAS}/${id}`);
}

/** GET /api/facturas/{id}/pdf -> el PDF como Blob. */
export function descargarPdf(id: number): Promise<Blob> {
  return getBlob(`${URL_FACTURAS}/${id}/pdf`);
}

/** GET /api/facturas/dashboard -> métricas del mes. */
export function obtenerDashboard(): Promise<{ totalFacturado: number; cantidadComprobantes: number }> {
  return getJson(`${URL_FACTURAS}/dashboard`);
}
