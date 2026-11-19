import { ApiError } from "../api/http.js";

/** Formato y conversión de valores. Sin acceso al DOM: son funciones puras. */

const formatoNumero = new Intl.NumberFormat("es-AR", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** 18500.5 -> "18.500,50" */
export function formatearNumero(n: number | null | undefined): string {
  return typeof n === "number" ? formatoNumero.format(n) : "—";
}

/** "2026-10-14..." -> "14/10/2026" sin pasar por Date, para evitar corrimientos de zona horaria. */
export function formatearFecha(valor: string | null | undefined): string {
  if (!valor) return "—";
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(valor);
  if (m) return `${m[3]}/${m[2]}/${m[1]}`;
  const d = new Date(valor);
  return Number.isNaN(d.getTime()) ? valor : d.toLocaleDateString("es-AR");
}

/** Fecha de hoy en formato ISO ("2026-10-14"), que es el que usa <input type="date">. */
export function hoy(): string {
  const d = new Date();
  const dos = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())}`;
}

/** Convierte el texto de un input en número; devuelve undefined si está vacío o no es válido. */
export function leerNumero(valor: string): number | undefined {
  const limpio = valor.trim();
  if (limpio === "") return undefined;
  const n = Number(limpio);
  return Number.isFinite(n) ? n : undefined;
}

export function esIdValido(n: number | undefined): n is number {
  return n !== undefined && Number.isInteger(n) && n > 0;
}

export function mensajeDeError(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return "Ocurrió un error inesperado.";
}
