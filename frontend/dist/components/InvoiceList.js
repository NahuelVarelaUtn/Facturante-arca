import { anularFactura, buscarFacturas, eliminarFactura, emitirFactura } from "../api/facturas.api.js";
import { ESTADO_ANULADA, ESTADO_BORRADOR, ESTADO_EMITIDA } from "../models/factura.dto.js";
import { $, boton, celda, el } from "../utils/dom.js";
import { formatearFecha, formatearNumero, mensajeDeError } from "../utils/format.js";
import { abrirPdf } from "../utils/pdf.js";
import { avisar } from "./Toast.js";
export class InvoiceList {
    constructor(opciones) {
        this.opciones = opciones;
        this.form = $("form-filtros");
        this.fNroFactura = $("f-nroFactura");
        this.fCliente = $("f-cliente");
        this.btnCliente = $("btn-f-cliente");
        this.btnClienteQuitar = $("btn-f-cliente-quitar");
        this.fEstado = $("f-estado");
        this.fDesde = $("f-fechaDesde");
        this.fHasta = $("f-fechaHasta");
        this.tbody = $("tbody-facturas");
        this.resumen = $("lista-estado");
        this.btnPrev = $("btn-prev");
        this.btnNext = $("btn-next");
        this.paginaInfo = $("pagina-info");
        this.pTamanio = $("p-tamanio");
        this.clienteFiltro = null;
        /** Filtros de la última búsqueda (no los que se están tipeando). */
        this.filtros = {};
        this.facturas = [];
        this.pagina = 0; // base 0, como Spring
        this.tamanio = 10;
        this.totalPaginas = 0;
        this.totalElementos = 0;
        this.conectarEventos();
    }
    /** Vuelve a pedir la página actual con los filtros aplicados. */
    async recargar() {
        this.resumen.textContent = "Cargando comprobantes…";
        this.tbody.setAttribute("aria-busy", "true");
        try {
            const respuesta = await buscarFacturas(this.filtros, this.pagina, this.tamanio);
            // Si se eliminó la última fila de la última página, se retrocede una página
            if (respuesta.content.length === 0 && respuesta.page.number > 0 && respuesta.page.totalPages > 0) {
                this.pagina = respuesta.page.totalPages - 1;
                await this.recargar();
                return;
            }
            this.facturas = respuesta.content;
            this.pagina = respuesta.page.number;
            this.totalPaginas = respuesta.page.totalPages;
            this.totalElementos = respuesta.page.totalElements;
            this.render();
        }
        catch (error) {
            this.facturas = [];
            this.totalPaginas = 0;
            this.totalElementos = 0;
            this.render();
            this.resumen.textContent = "No se pudo cargar el listado.";
            avisar(mensajeDeError(error), "error");
        }
        finally {
            this.tbody.removeAttribute("aria-busy");
        }
    }
    /** Vuelve a la primera página (después de crear una factura nueva). */
    async irAlInicio() {
        this.pagina = 0;
        await this.recargar();
    }
    // -------------------------------------------------------------------------
    // Filtros y paginación
    // -------------------------------------------------------------------------
    conectarEventos() {
        this.form.addEventListener("submit", (e) => {
            e.preventDefault();
            if (this.fDesde.value && this.fHasta.value && this.fDesde.value > this.fHasta.value) {
                avisar("La fecha desde no puede ser posterior a la fecha hasta.", "error");
                return;
            }
            this.buscar();
        });
        // "reset" se dispara ANTES de que el navegador vacíe los campos: por eso el setTimeout
        this.form.addEventListener("reset", () => window.setTimeout(() => {
            this.quitarCliente();
            this.buscar();
        }, 0));
        this.btnCliente.addEventListener("click", () => void this.elegirCliente());
        this.btnClienteQuitar.addEventListener("click", () => this.quitarCliente());
        this.btnPrev.addEventListener("click", () => {
            if (this.pagina > 0) {
                this.pagina -= 1;
                void this.recargar();
            }
        });
        this.btnNext.addEventListener("click", () => {
            if (this.pagina + 1 < this.totalPaginas) {
                this.pagina += 1;
                void this.recargar();
            }
        });
        this.pTamanio.addEventListener("change", () => {
            this.tamanio = Number(this.pTamanio.value);
            this.pagina = 0;
            void this.recargar();
        });
    }
    buscar() {
        const filtros = {};
        const nro = this.fNroFactura.value.trim();
        if (nro)
            filtros.nroFactura = nro;
        if (this.clienteFiltro)
            filtros.clienteId = this.clienteFiltro.id;
        if (this.fEstado.value)
            filtros.estado = this.fEstado.value;
        if (this.fDesde.value)
            filtros.fechaDesde = this.fDesde.value;
        if (this.fHasta.value)
            filtros.fechaHasta = this.fHasta.value;
        this.filtros = filtros;
        this.pagina = 0;
        void this.recargar();
    }
    async elegirCliente() {
        const cliente = await this.opciones.explorerClientes.abrir();
        if (!cliente)
            return;
        this.clienteFiltro = cliente;
        this.fCliente.value = cliente.cuit ? `${cliente.nombre} — CUIT ${cliente.cuit}` : cliente.nombre;
        this.btnClienteQuitar.hidden = false;
    }
    quitarCliente() {
        this.clienteFiltro = null;
        this.fCliente.value = "";
        this.btnClienteQuitar.hidden = true;
    }
    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------
    render() {
        this.tbody.replaceChildren();
        this.resumen.textContent = this.totalElementos === 1 ? "1 comprobante" : `${this.totalElementos} comprobantes`;
        this.paginaInfo.textContent = `Página ${this.pagina + 1} de ${Math.max(this.totalPaginas, 1)}`;
        this.btnPrev.disabled = this.pagina <= 0;
        this.btnNext.disabled = this.pagina + 1 >= this.totalPaginas;
        if (this.facturas.length === 0) {
            const hayFiltros = Object.keys(this.filtros).length > 0;
            const fila = el("tr");
            const vacio = el("td", "vacio", hayFiltros
                ? "No hay comprobantes con esos filtros."
                : "Todavía no hay comprobantes. Creá el primero con «Nueva factura».");
            vacio.colSpan = 7;
            fila.append(vacio);
            this.tbody.append(fila);
            return;
        }
        for (const f of this.facturas) {
            const fila = el("tr", f.estado === ESTADO_ANULADA ? "es-anulada" : undefined);
            const numero = el("td");
            numero.append(f.nroFactura
                ? document.createTextNode(f.nroFactura)
                : el("span", "sin-numero", f.letra === "X" ? "A asignar" : "A asignar por ARCA"));
            fila.append(celda(f.letra, "letra"), numero, celda(formatearFecha(f.fecha)), celda(f.clienteNombre), this.celdaEstado(f), celda(`${f.tipoMoneda} ${formatearNumero(f.importeTotal)}`, "num importe"), this.celdaAcciones(f));
            this.tbody.append(fila);
        }
    }
    celdaEstado(f) {
        const td = el("td", "estado");
        const [clase, texto] = f.estado === ESTADO_ANULADA
            ? ["insignia--anulada", "Anulada"]
            : f.estado === ESTADO_EMITIDA
                ? ["insignia--informada", f.cae ? "Emitida · CAE" : "Emitida · interna"]
                : ["insignia--activa", "Borrador"];
        td.append(el("span", `insignia ${clase}`, texto));
        return td;
    }
    /** Solo se muestran las acciones que el estado permite: no hay botones deshabilitados "de adorno". */
    celdaAcciones(f) {
        const td = el("td", "acciones");
        const nombre = this.nombre(f);
        const esBorrador = f.estado === ESTADO_BORRADOR;
        // "editable" lo decide el backend: borradores y Facturas X (sin CAE)
        const verbo = f.editable ? "Editar" : "Ver";
        const abrir = boton(verbo, "btn btn--secondary", `${verbo} ${nombre}`);
        abrir.addEventListener("click", () => this.opciones.alAbrir(f));
        td.append(abrir);
        if (esBorrador) {
            const facturar = boton("Facturar", "btn btn--primario", `Emitir ${nombre}`);
            facturar.title = f.letra === "X" ? "Factura X: la numera el sistema" : "Solicitar CAE a ARCA";
            facturar.addEventListener("click", () => void this.emitir(f, facturar));
            const eliminar = boton("Eliminar", "btn btn--danger", `Eliminar ${nombre}`);
            eliminar.addEventListener("click", () => void this.eliminar(f, eliminar));
            td.append(facturar, eliminar);
        }
        if (f.estado === ESTADO_EMITIDA) {
            const anular = boton("Anular", "btn btn--secondary", `Anular ${nombre}`);
            anular.addEventListener("click", () => void this.anular(f, anular));
            td.append(anular);
        }
        const pdf = boton("PDF", "btn btn--secondary", `Imprimir en PDF ${nombre}`);
        pdf.addEventListener("click", () => void this.conBoton(pdf, () => abrirPdf(f.id)));
        td.append(pdf);
        return td;
    }
    /** Cómo se nombra una factura en los mensajes. */
    nombre(f) {
        return f.nroFactura ? `la Factura ${f.letra} ${f.nroFactura}` : `el borrador #${f.id}`;
    }
    // -------------------------------------------------------------------------
    // Acciones
    // -------------------------------------------------------------------------
    async emitir(f, origen) {
        const detalle = f.letra === "X"
            ? "Es una Factura X: no se informa a ARCA y vas a poder seguir editándola o convertirla en Factura A/B."
            : "Se va a solicitar el CAE a ARCA. Después ya no se podrá modificar.";
        if (!window.confirm(`¿Emitir ${this.nombre(f)}? ${detalle}`))
            return;
        origen.textContent = "Emitiendo…";
        const ok = await this.conBoton(origen, async () => {
            const emitida = await emitirFactura(f.id);
            avisar(emitida.cae
                ? `Factura ${emitida.letra} ${emitida.nroFactura} autorizada por ARCA. CAE ${emitida.cae}.`
                : `Factura ${emitida.letra} ${emitida.nroFactura} emitida.`);
        });
        if (ok)
            await this.trasCambio();
        else
            origen.textContent = "Facturar";
    }
    async anular(f, origen) {
        if (!window.confirm(`¿Anular ${this.nombre(f)}? Va a quedar registrada como ANULADA.`))
            return;
        const ok = await this.conBoton(origen, async () => {
            await anularFactura(f.id);
            avisar(`Se anuló ${this.nombre(f)}.`);
        });
        if (ok)
            await this.trasCambio();
    }
    async eliminar(f, origen) {
        if (!window.confirm(`¿Eliminar ${this.nombre(f)}? Esta acción no se puede deshacer.`))
            return;
        const ok = await this.conBoton(origen, async () => {
            await eliminarFactura(f.id);
            avisar(`Se eliminó ${this.nombre(f)}.`);
        });
        if (ok)
            await this.trasCambio();
    }
    /** Deshabilita el botón mientras corre la acción y muestra el error si falla. Devuelve true si salió bien. */
    async conBoton(origen, accion) {
        origen.disabled = true;
        try {
            await accion();
            return true;
        }
        catch (error) {
            avisar(mensajeDeError(error), "error");
            return false;
        }
        finally {
            origen.disabled = false;
        }
    }
    async trasCambio() {
        this.opciones.alCambiar();
        await this.recargar();
    }
}
//# sourceMappingURL=InvoiceList.js.map