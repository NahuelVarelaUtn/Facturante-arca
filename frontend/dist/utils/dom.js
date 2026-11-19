/** Ayudas para crear nodos del DOM sin repetir código. */
/** Busca un elemento por id y falla con un mensaje claro si no existe. */
export function $(id) {
    const nodo = document.getElementById(id);
    if (!nodo)
        throw new Error(`No se encontró #${id} en index.html`);
    return nodo;
}
/** Crea un elemento. Usa textContent, así que el contenido del backend nunca se interpreta como HTML. */
export function el(tag, clase, texto) {
    const nodo = document.createElement(tag);
    if (clase)
        nodo.className = clase;
    if (texto !== undefined)
        nodo.textContent = texto;
    return nodo;
}
export function celda(texto, clase) {
    return el("td", clase, texto);
}
export function boton(texto, clase, ariaLabel) {
    const b = el("button", clase, texto);
    b.type = "button";
    if (ariaLabel)
        b.setAttribute("aria-label", ariaLabel);
    return b;
}
/** Contador para generar ids únicos (label/for, aria-labelledby) en componentes que se instancian varias veces. */
let contadorIds = 0;
export function idUnico(prefijo) {
    contadorIds += 1;
    return `${prefijo}-${contadorIds}`;
}
//# sourceMappingURL=dom.js.map