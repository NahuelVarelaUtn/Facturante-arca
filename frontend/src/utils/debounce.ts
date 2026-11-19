/** Función "con espera": se puede llamar muchas veces seguidas y solo se ejecuta la última. */
export interface ConEspera<A extends unknown[]> {
  (...args: A): void;
  /** Descarta la ejecución pendiente (por ejemplo al cerrar el modal). */
  cancelar(): void;
}

/**
 * Debounce: demora la ejecución hasta que pasen "ms" milisegundos sin nuevas llamadas.
 * En un buscador evita mandar una petición por cada tecla: tipear "mouse" hace 1 búsqueda, no 5.
 */
export function debounce<A extends unknown[]>(fn: (...args: A) => void, ms: number): ConEspera<A> {
  let temporizador: number | undefined;

  const conEspera = ((...args: A) => {
    window.clearTimeout(temporizador);
    temporizador = window.setTimeout(() => fn(...args), ms);
  }) as ConEspera<A>;

  conEspera.cancelar = () => window.clearTimeout(temporizador);
  return conEspera;
}
