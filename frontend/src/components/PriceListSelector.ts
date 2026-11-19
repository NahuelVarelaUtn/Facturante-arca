import { listarListasPrecio } from "../api/articulos.api.js";
import type { ListaPrecioOpcionDTO } from "../models/factura.dto.js";
import { el, idUnico } from "../utils/dom.js";
import { mensajeDeError } from "../utils/format.js";

/**
 * Selector GLOBAL de lista de precios (MAYORISTA / MINORISTA) de una factura.
 * Deja siempre a la vista con qué lista se está trabajando, porque de eso depende el precio de cada artículo.
 *
 * Uso:
 *   const selector = new PriceListSelector(contenedor);
 *   selector.alCambiar = (nueva, anterior) => confirm("¿Cambiar de lista?");  // devolver false cancela el cambio
 *   await selector.cargar();
 *   selector.valor  // { id, nombre } de la lista activa
 */
export class PriceListSelector {
  /**
   * Se llama cuando el USUARIO elige otra lista. Si devuelve false, el cambio se deshace
   * (sirve para pedir confirmación cuando la factura ya tiene renglones con la lista anterior).
   */
  alCambiar: ((nueva: ListaPrecioOpcionDTO, anterior: ListaPrecioOpcionDTO | null) => boolean | void) | null = null;

  private readonly grupo = el("div", "lista-precios__opciones");
  private readonly aviso = el("p", "lista-precios__aviso");
  private readonly nombreGrupo = idUnico("lista-precios");

  private listas: ListaPrecioOpcionDTO[] = [];
  private activa: ListaPrecioOpcionDTO | null = null;
  private deshabilitado = false;

  constructor(contenedor: HTMLElement) {
    const marco = el("fieldset", "lista-precios");
    marco.append(el("legend", undefined, "Lista de precios"), this.grupo, this.aviso);
    this.aviso.setAttribute("role", "status");
    this.aviso.textContent = "Cargando listas…";
    contenedor.replaceChildren(marco);
  }

  /** Lista activa, o null si todavía no se cargaron. */
  get valor(): ListaPrecioOpcionDTO | null {
    return this.activa;
  }

  /** Trae las listas del backend (son 2 o 3 filas fijas) y deja elegida la primera. */
  async cargar(): Promise<void> {
    try {
      this.listas = await listarListasPrecio();
      this.activa = this.listas[0] ?? null;
      this.render();
    } catch (error) {
      this.aviso.textContent = `No se pudieron cargar las listas de precios. ${mensajeDeError(error)}`;
      this.aviso.classList.add("lista-precios__aviso--error");
    }
  }

  /** Cambia la lista por código (al editar una factura guardada). No dispara alCambiar. */
  seleccionar(listaPrecioId: number): void {
    const lista = this.listas.find((l) => l.id === listaPrecioId);
    if (!lista) return;
    this.activa = lista;
    this.render();
  }

  /** Bloquea el selector (factura de solo lectura). */
  set bloqueado(valor: boolean) {
    this.deshabilitado = valor;
    this.render();
  }

  private render(): void {
    this.grupo.replaceChildren();
    for (const lista of this.listas) {
      const radio = el("input");
      radio.type = "radio";
      radio.name = this.nombreGrupo;
      radio.value = String(lista.id);
      radio.checked = lista.id === this.activa?.id;
      radio.disabled = this.deshabilitado;
      radio.addEventListener("change", () => this.alElegir(lista));

      const opcion = el("label", "lista-precios__opcion");
      opcion.append(radio, el("span", undefined, lista.nombre));
      this.grupo.append(opcion);
    }
    this.aviso.classList.remove("lista-precios__aviso--error");
    this.aviso.textContent = this.activa
      ? `Los precios de los artículos salen de la lista ${this.activa.nombre}.`
      : "No hay listas de precios cargadas.";
  }

  private alElegir(nueva: ListaPrecioOpcionDTO): void {
    const anterior = this.activa;
    if (nueva.id === anterior?.id) return;
    if (this.alCambiar?.(nueva, anterior) === false) {
      this.render(); // se canceló: vuelve a marcar la lista anterior
      return;
    }
    this.activa = nueva;
    this.render();
  }
}
