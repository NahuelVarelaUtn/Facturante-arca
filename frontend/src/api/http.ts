/**
 * Cliente HTTP compartido. Es el ÚNICO lugar del frontend que usa fetch:
 * los módulos de api/ arman la URL y los componentes solo llaman a esos módulos.
 */

/** Raíz de la API del backend. */
export const API_URL = "http://localhost:8080/api";

/** Error de la API con el código HTTP, para que la UI pueda mostrar un mensaje claro. */
export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "ApiError";
  }
}

/** true si el error es una petición cancelada con AbortController (no hay que mostrárselo al usuario). */
export function esCancelacion(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

/** Agrega a la URL solo los parámetros que tienen valor: { search: "", page: 1 } -> "?page=1". */
export function conQuery(url: string, params: object = {}): string {
  const query = new URLSearchParams();
  for (const [clave, valor] of Object.entries(params)) {
    if (valor !== undefined && valor !== null && valor !== "") query.append(clave, String(valor));
  }
  const texto = query.toString();
  return texto ? `${url}?${texto}` : url;
}

/** GET que devuelve JSON. "signal" permite cancelar la petición (lo usa el explorer al seguir tipeando). */
export async function getJson<T>(url: string, params?: object, signal?: AbortSignal): Promise<T> {
  const respuesta = await enviar(conQuery(url, params), {
    method: "GET",
    headers: { Accept: "application/json" },
    signal,
  });
  return (await respuesta.json()) as T;
}

/** POST / PUT con cuerpo JSON opcional; devuelve el JSON de la respuesta. */
export async function sendJson<T>(url: string, metodo: "POST" | "PUT", cuerpo?: unknown): Promise<T> {
  const respuesta = await enviar(url, {
    method: metodo,
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  });
  return (await respuesta.json()) as T;
}

export async function getBlob(url: string): Promise<Blob> {
  const respuesta = await enviar(url, { method: "GET" });
  return respuesta.blob();
}

export async function eliminar(url: string): Promise<void> {
  await enviar(url, { method: "DELETE" });
}

/** Ejecuta el fetch y convierte fallos de red y respuestas no-2xx en ApiError. */
async function enviar(url: string, init: RequestInit): Promise<Response> {
  let respuesta: Response;
  try {
    respuesta = await fetch(url, init);
  } catch (error) {
    if (esCancelacion(error)) throw error;
    throw new ApiError(0, "No se pudo conectar con el servidor. Verificá que el backend esté levantado en el puerto 8080.");
  }
  if (!respuesta.ok) {
    throw new ApiError(respuesta.status, await extraerMensaje(respuesta));
  }
  return respuesta;
}

/** Intenta leer el mensaje de error que devuelve Spring ({ message } o { error }). */
async function extraerMensaje(respuesta: Response): Promise<string> {
  let detalle = "";
  try {
    const texto = await respuesta.text();
    if (texto) {
      try {
        const json = JSON.parse(texto) as { message?: string; error?: string };
        detalle = json.message || json.error || "";
      } catch {
        detalle = texto.slice(0, 200);
      }
    }
  } catch {
    /* sin cuerpo legible */
  }

  switch (respuesta.status) {
    case 400:
      return detalle || "Los datos enviados no son válidos.";
    case 404:
      return detalle || "No se encontró el recurso solicitado.";
    case 409:
      return detalle || "La operación entra en conflicto con el estado actual de la factura.";
    case 502:
      return detalle || "ARCA no autorizó el comprobante.";
    case 500:
      return detalle || "El servidor tuvo un error interno. Probá de nuevo en unos instantes.";
    default:
      return detalle || `Error ${respuesta.status} al comunicarse con el servidor.`;
  }
}
