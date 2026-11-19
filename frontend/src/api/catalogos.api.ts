import type { PuntoVentaDTO, TipoMonedaDTO } from "../models/factura.dto.js";
import { API_URL, getJson } from "./http.js";

/** Desplegables fijos del formulario: son pocas filas, por eso se piden completos. */

export function listarPuntosVenta(): Promise<PuntoVentaDTO[]> {
  return getJson(`${API_URL}/puntos-venta`);
}

export function listarMonedas(): Promise<TipoMonedaDTO[]> {
  return getJson(`${API_URL}/monedas`);
}
