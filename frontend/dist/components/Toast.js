import { el } from "../utils/dom.js";
/** Aviso flotante de la página (abajo a la derecha). Se crea solo la primera vez que se usa. */
const DURACION_MS = 6000;
let nodo = null;
let temporizador;
export function avisar(mensaje, tipo = "ok") {
    if (!nodo) {
        nodo = el("div", "toast");
        nodo.setAttribute("role", "status");
        nodo.setAttribute("aria-live", "polite");
        document.body.append(nodo);
    }
    nodo.textContent = mensaje;
    nodo.className = `toast toast--${tipo} is-visible`;
    window.clearTimeout(temporizador);
    temporizador = window.setTimeout(() => nodo?.classList.remove("is-visible"), DURACION_MS);
}
//# sourceMappingURL=Toast.js.map