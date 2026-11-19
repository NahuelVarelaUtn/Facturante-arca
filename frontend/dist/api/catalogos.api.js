import { API_URL, getJson } from "./http.js";
/** Desplegables fijos del formulario: son pocas filas, por eso se piden completos. */
export function listarPuntosVenta() {
    return getJson(`${API_URL}/puntos-venta`);
}
export function listarMonedas() {
    return getJson(`${API_URL}/monedas`);
}
//# sourceMappingURL=catalogos.api.js.map