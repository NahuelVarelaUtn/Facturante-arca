import { API_URL, getJson } from "./http.js";
/** Tamaño de página de los explorers. El backend nunca devuelve más de 100 aunque se pida más. */
export const LIMITE_EXPLORER = 100;
/** GET /api/clientes?page=1&limit=100&search=..&id=..&cuit=..&condicionIva=.. */
export function buscarClientes(filtros, page = 1, signal) {
    return getJson(`${API_URL}/clientes`, { ...filtros, page, limit: LIMITE_EXPLORER }, signal);
}
/** GET /api/clientes/consumidor-final: cliente genérico para ventas sin identificar. */
export function obtenerConsumidorFinal() {
    return getJson(`${API_URL}/clientes/consumidor-final`);
}
//# sourceMappingURL=clientes.api.js.map