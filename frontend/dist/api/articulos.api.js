import { LIMITE_EXPLORER } from "./clientes.api.js";
import { API_URL, getJson } from "./http.js";
/** GET /api/articulos?page=1&limit=100&search=..&codigo=..&id=..&listaPrecioId=.. */
export function buscarArticulos(filtros, page = 1, signal) {
    return getJson(`${API_URL}/articulos`, { ...filtros, page, limit: LIMITE_EXPLORER }, signal);
}
/** GET /api/listas-precio (MAYORISTA / MINORISTA). Son pocas opciones fijas. */
export function listarListasPrecio() {
    return getJson(`${API_URL}/listas-precio`);
}
//# sourceMappingURL=articulos.api.js.map