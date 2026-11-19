/** Ayudas para crear nodos del DOM sin repetir código. */

/** Busca un elemento por id y falla con un mensaje claro si no existe. */
export function $<T extends HTMLElement>(id: string): T {
  const nodo = document.getElementById(id);
  if (!nodo) throw new Error(`No se encontró #${id} en index.html`);
  return nodo as T;
}

/** Crea un elemento. Usa textContent, así que el contenido del backend nunca se interpreta como HTML. */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  clase?: string,
  texto?: string,
): HTMLElementTagNameMap[K] {
  const nodo = document.createElement(tag);
  if (clase) nodo.className = clase;
  if (texto !== undefined) nodo.textContent = texto;
  return nodo;
}

export function celda(texto: string, clase?: string): HTMLTableCellElement {
  return el("td", clase, texto);
}

export function boton(texto: string, clase: string, ariaLabel?: string): HTMLButtonElement {
  const b = el("button", clase, texto);
  b.type = "button";
  if (ariaLabel) b.setAttribute("aria-label", ariaLabel);
  return b;
}

/** Contador para generar ids únicos (label/for, aria-labelledby) en componentes que se instancian varias veces. */
let contadorIds = 0;
export function idUnico(prefijo: string): string {
  contadorIds += 1;
  return `${prefijo}-${contadorIds}`;
}
