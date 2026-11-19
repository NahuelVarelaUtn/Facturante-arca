import { API_URL, eliminar, getBlob, getJson, sendJson } from "./http.js";
const URL_FACTURAS = `${API_URL}/facturas`;
/** GET /api/facturas?nroFactura=..&clienteId=..&estado=..&informadaArca=..&fechaDesde=..&fechaHasta=..&page=..&size=.. */
export function buscarFacturas(filtros = {}, pagina = 0, tamanio = 10) {
    return getJson(URL_FACTURAS, { ...filtros, page: pagina, size: tamanio });
}
/** GET /api/facturas/{id} -> la factura completa, con renglones. */
export function obtenerFactura(id) {
    return getJson(`${URL_FACTURAS}/${id}`);
}
/** GET /api/facturas/letra?condicionIva=.. -> letra que corresponde a ese cliente y letras permitidas. */
export function sugerirLetra(condicionIva) {
    return getJson(`${URL_FACTURAS}/letra`, { condicionIva });
}
/** POST /api/facturas -> guarda un BORRADOR (no se comunica con ARCA). */
export function crearFactura(factura) {
    return sendJson(URL_FACTURAS, "POST", factura);
}
/** PUT /api/facturas/{id} -> actualiza un borrador. 409 si la factura ya no es borrador. */
export function actualizarFactura(id, factura) {
    return sendJson(`${URL_FACTURAS}/${id}`, "PUT", factura);
}
/** POST /api/facturas/{id}/emitir -> pide el CAE a ARCA (o numera, si es X). 502 si ARCA rechaza. */
export function emitirFactura(id) {
    return sendJson(`${URL_FACTURAS}/${id}/emitir`, "POST");
}
/** PUT /api/facturas/{id}/anular -> anulación lógica (no borra). Solo facturas emitidas. */
export function anularFactura(id) {
    return sendJson(`${URL_FACTURAS}/${id}/anular`, "PUT");
}
/** DELETE /api/facturas/{id}. Solo borradores. */
export function eliminarFactura(id) {
    return eliminar(`${URL_FACTURAS}/${id}`);
}
/** GET /api/facturas/{id}/pdf -> el PDF como Blob. */
export function descargarPdf(id) {
    return getBlob(`${URL_FACTURAS}/${id}/pdf`);
}
/** GET /api/facturas/dashboard -> métricas del mes. */
export function obtenerDashboard() {
    return getJson(`${URL_FACTURAS}/dashboard`);
}
//# sourceMappingURL=facturas.api.js.map