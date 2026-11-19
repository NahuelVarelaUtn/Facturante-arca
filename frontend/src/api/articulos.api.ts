import type {
  ArticuloFiltrosDTO,
  ArticuloPrecioDTO,
  ListaPrecioOpcionDTO,
  PageResponse,
} from "../models/factura.dto.js";
import { LIMITE_EXPLORER } from "./clientes.api.js";
import { API_URL, getJson } from "./http.js";

/** GET /api/articulos?page=1&limit=100&search=..&codigo=..&id=..&listaPrecioId=.. */
export function buscarArticulos(
  filtros: ArticuloFiltrosDTO,
  page = 1,
  signal?: AbortSignal,
): Promise<PageResponse<ArticuloPrecioDTO>> {
  return getJson(`${API_URL}/articulos`, { ...filtros, page, limit: LIMITE_EXPLORER }, signal);
}

/** GET /api/listas-precio (MAYORISTA / MINORISTA). Son pocas opciones fijas. */
export function listarListasPrecio(): Promise<ListaPrecioOpcionDTO[]> {
  return getJson(`${API_URL}/listas-precio`);
}
