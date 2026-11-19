/**
 * Debounce: demora la ejecución hasta que pasen "ms" milisegundos sin nuevas llamadas.
 * En un buscador evita mandar una petición por cada tecla: tipear "mouse" hace 1 búsqueda, no 5.
 */
export function debounce(fn, ms) {
    let temporizador;
    const conEspera = ((...args) => {
        window.clearTimeout(temporizador);
        temporizador = window.setTimeout(() => fn(...args), ms);
    });
    conEspera.cancelar = () => window.clearTimeout(temporizador);
    return conEspera;
}
//# sourceMappingURL=debounce.js.map