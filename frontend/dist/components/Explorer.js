import { esCancelacion } from "../api/http.js";
import { debounce } from "../utils/debounce.js";
import { boton, celda, el, idUnico } from "../utils/dom.js";
import { mensajeDeError } from "../utils/format.js";
/**
 * EXPLORER GENÉRICO: ventana modal para buscar y elegir UN registro de un catálogo grande.
 *
 * Reglas (son las mismas para clientes, artículos o lo que se agregue después):
 *  - Al abrir NO trae nada: cero fetch hasta que el usuario busque o filtre.
 *  - Al tipear busca desde el primer carácter, esperando 300 ms sin tipeo antes de salir (debounce).
 *  - Enter o el botón "Buscar" con todo vacío trae TODOS los registros (paginados, los más recientes primero).
 *  - Los resultados llegan paginados desde el backend (hasta 100 por página).
 *  - Siempre muestra en qué estado está: inicial, buscando, sin resultados, error o resultados.
 *
 * Este archivo no sabe nada de clientes ni de artículos: eso lo aporta cada ConfigExplorer.
 */
/** Espera entre la última tecla y la búsqueda. */
const ESPERA_BUSQUEDA_MS = 300;
/** Clave reservada para el texto del buscador principal. */
export const CLAVE_BUSQUEDA = "search";
export class Explorer {
    constructor(config) {
        this.config = config;
        this.dialog = el("dialog", "explorer");
        this.inputBusqueda = el("input");
        this.btnBuscar = boton("Buscar", "btn btn--primario");
        this.btnFiltros = boton("Filtros", "btn btn--secondary");
        this.panelFiltros = el("div", "explorer__filtros");
        this.contenedorTabla = el("div", "explorer__tabla");
        this.tbody = el("tbody");
        this.estado = el("p", "explorer__estado");
        this.paginaInfo = el("span", "explorer__pagina");
        this.btnAnterior = boton("← Anterior", "btn btn--secondary");
        this.btnSiguiente = boton("Siguiente →", "btn btn--secondary");
        this.btnSeleccionar = boton("Seleccionar", "btn btn--primario");
        /** Un control (input o select) por cada filtro, indexado por su clave. */
        this.controles = new Map();
        this.opcionesCargadas = false;
        this.fijos = {};
        /**
         * Criterios de la búsqueda que se está mostrando. Anterior / Siguiente paginan SIEMPRE con estos,
         * no con lo que haya en los campos en ese momento: así el número de página nunca queda desfasado
         * respecto del filtro.
         */
        this.criteriosActivos = null;
        this.pagina = 1;
        this.totalPaginas = 0;
        this.seleccionado = null;
        this.filaActiva = null;
        this.resultado = null;
        this.resolver = null;
        /** Cancela la petición en curso cuando sale una búsqueda nueva. */
        this.peticion = null;
        this.buscarConEspera = debounce(() => this.buscarNuevo(false), ESPERA_BUSQUEDA_MS);
        this.construir();
        this.conectarEventos();
    }
    /** Abre el modal y devuelve el registro elegido, o null si se canceló. */
    abrir(opciones = {}) {
        if (this.dialog.open)
            return Promise.resolve(null);
        this.fijos = opciones.fijos ?? {};
        this.reiniciar();
        const promesa = new Promise((resolve) => {
            this.resolver = resolve;
        });
        this.dialog.showModal();
        this.inputBusqueda.focus();
        return promesa; // sin fetch: recién se busca cuando el usuario escribe o filtra
    }
    // -------------------------------------------------------------------------
    // Construcción del DOM (una sola vez)
    // -------------------------------------------------------------------------
    construir() {
        const idTitulo = idUnico("explorer-titulo");
        this.dialog.setAttribute("aria-labelledby", idTitulo);
        const titulo = el("h2", undefined, this.config.titulo);
        titulo.id = idTitulo;
        this.inputBusqueda.type = "search";
        this.inputBusqueda.placeholder = this.config.placeholder;
        this.inputBusqueda.setAttribute("aria-label", this.config.placeholder);
        this.inputBusqueda.autocomplete = "off";
        this.panelFiltros.id = idUnico("explorer-filtros");
        this.panelFiltros.hidden = true;
        this.btnFiltros.setAttribute("aria-expanded", "false");
        this.btnFiltros.setAttribute("aria-controls", this.panelFiltros.id);
        this.btnFiltros.hidden = this.config.filtros.length === 0;
        const barra = el("div", "explorer__busqueda");
        barra.append(this.inputBusqueda, this.btnBuscar, this.btnFiltros);
        for (const filtro of this.config.filtros) {
            this.panelFiltros.append(this.crearCampoFiltro(filtro));
        }
        const btnLimpiar = boton("Limpiar filtros", "btn btn--ghost");
        btnLimpiar.addEventListener("click", () => this.limpiarFiltros());
        this.panelFiltros.append(btnLimpiar);
        const encabezado = el("tr");
        for (const col of this.config.columnas)
            encabezado.append(el("th", col.clase, col.titulo));
        const thead = el("thead");
        thead.append(encabezado);
        const tabla = el("table");
        tabla.append(thead, this.tbody);
        this.contenedorTabla.append(tabla);
        this.estado.setAttribute("role", "status");
        this.estado.setAttribute("aria-live", "polite");
        const paginacion = el("div", "explorer__paginacion");
        paginacion.append(this.btnAnterior, this.paginaInfo, this.btnSiguiente);
        const btnCancelar = boton("Cancelar", "btn btn--secondary");
        btnCancelar.addEventListener("click", () => this.cerrar(null));
        const acciones = el("div", "explorer__acciones");
        acciones.append(btnCancelar, this.btnSeleccionar);
        const pie = el("div", "explorer__pie");
        pie.append(paginacion, acciones);
        const cuerpo = el("div", "explorer__cuerpo");
        cuerpo.append(titulo, barra, this.panelFiltros, this.estado, this.contenedorTabla, pie);
        this.dialog.append(cuerpo);
        document.body.append(this.dialog);
    }
    crearCampoFiltro(filtro) {
        const etiqueta = el("label", undefined, filtro.etiqueta);
        let control;
        if (filtro.tipo === "select") {
            control = el("select");
            control.append(new Option("Todas", ""));
            control.addEventListener("change", () => this.alCambiarCriterios(false));
        }
        else {
            control = el("input");
            control.type = filtro.tipo === "numero" ? "number" : "search";
            if (filtro.tipo === "numero")
                control.min = "1";
            control.placeholder = filtro.placeholder ?? "";
            control.autocomplete = "off";
            control.addEventListener("input", () => this.alCambiarCriterios(false));
        }
        control.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                this.alCambiarCriterios(true);
            }
        });
        this.controles.set(filtro.clave, control);
        etiqueta.append(control);
        return etiqueta;
    }
    conectarEventos() {
        this.inputBusqueda.addEventListener("input", () => this.alCambiarCriterios(false));
        this.inputBusqueda.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                this.alCambiarCriterios(true);
            }
            else if (e.key === "ArrowDown") {
                const primera = this.tbody.querySelector("tr[tabindex]");
                if (primera) {
                    e.preventDefault();
                    primera.focus();
                }
            }
        });
        this.btnBuscar.addEventListener("click", () => this.alCambiarCriterios(true));
        this.btnFiltros.addEventListener("click", () => void this.alternarFiltros());
        this.btnAnterior.addEventListener("click", () => void this.cargarPagina(this.pagina - 1));
        this.btnSiguiente.addEventListener("click", () => void this.cargarPagina(this.pagina + 1));
        this.btnSeleccionar.addEventListener("click", () => this.confirmar());
        // "close" se dispara tanto con los botones como con la tecla Escape
        this.dialog.addEventListener("close", () => {
            this.buscarConEspera.cancelar();
            this.peticion?.abort();
            const valor = this.resultado;
            const resolver = this.resolver;
            this.resultado = null;
            this.resolver = null;
            resolver?.(valor);
        });
    }
    // -------------------------------------------------------------------------
    // Criterios y búsqueda
    // -------------------------------------------------------------------------
    /** Lo que cargó el usuario en el buscador y en los filtros (sin contar los valores fijos de la pantalla). */
    leerCriteriosDelUsuario() {
        const criterios = {};
        const texto = this.inputBusqueda.value.trim();
        if (texto)
            criterios[CLAVE_BUSQUEDA] = texto;
        for (const [clave, control] of this.controles) {
            const valor = control.value.trim();
            if (valor && !(clave in this.fijos))
                criterios[clave] = valor;
        }
        return criterios;
    }
    /**
     * @param explicito true = el usuario pidió buscar (Enter o botón "Buscar"); false = está tipeando o
     *                  cambió un filtro. La diferencia importa cuando no hay nada cargado (ver buscarNuevo).
     */
    alCambiarCriterios(explicito) {
        this.actualizarContadorFiltros();
        if (explicito) {
            this.buscarConEspera.cancelar();
            this.buscarNuevo(true);
        }
        else {
            this.buscarConEspera();
        }
    }
    /**
     * Arranca una búsqueda nueva desde la página 1.
     *  - Con algo cargado (desde 1 carácter): busca.
     *  - Con todo vacío: solo busca si fue explícito (Enter / botón), y en ese caso trae todos los registros.
     *    Borrar el texto tipeando NO dispara el "traer todo": vuelve al mensaje inicial.
     */
    buscarNuevo(explicito) {
        const delUsuario = this.leerCriteriosDelUsuario();
        if (Object.keys(delUsuario).length === 0 && !explicito) {
            this.peticion?.abort();
            this.peticion = null;
            this.criteriosActivos = null;
            this.limpiarResultados();
            this.mostrarEstado("inicial", this.config.ayudaInicial);
            return;
        }
        this.criteriosActivos = { ...delUsuario, ...this.fijos }; // los valores fijos pisan a los del usuario
        void this.cargarPagina(1);
    }
    /** Pide al backend UNA página de la búsqueda activa. */
    async cargarPagina(pagina) {
        const criterios = this.criteriosActivos;
        if (!criterios)
            return;
        this.peticion?.abort(); // la respuesta de un pedido anterior ya no interesa
        const peticion = new AbortController();
        this.peticion = peticion;
        this.mostrarEstado("cargando", "Buscando…");
        try {
            const respuesta = await this.config.buscar(criterios, pagina, peticion.signal);
            if (peticion.signal.aborted)
                return;
            // La página y los totales salen SIEMPRE de la respuesta del backend, no de una cuenta local
            this.pagina = respuesta.page;
            this.totalPaginas = respuesta.totalPages;
            this.renderFilas(respuesta);
        }
        catch (error) {
            if (esCancelacion(error) || peticion.signal.aborted)
                return;
            this.limpiarResultados();
            this.mostrarEstado("error", mensajeDeError(error));
        }
    }
    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------
    renderFilas(respuesta) {
        this.tbody.replaceChildren();
        this.desmarcar();
        if (respuesta.content.length === 0) {
            this.actualizarPaginacion(0);
            this.mostrarEstado("vacio", "No hay resultados para esa búsqueda. Probá con otro texto o revisá los filtros.");
            return;
        }
        for (const item of respuesta.content) {
            const fila = el("tr");
            fila.tabIndex = 0;
            fila.setAttribute("aria-selected", "false");
            for (const col of this.config.columnas)
                fila.append(celda(col.valor(item), col.clase));
            fila.addEventListener("click", () => this.marcar(item, fila));
            fila.addEventListener("dblclick", () => {
                this.marcar(item, fila);
                this.confirmar();
            });
            fila.addEventListener("keydown", (e) => this.alTeclearEnFila(e, item, fila));
            this.tbody.append(fila);
        }
        const total = respuesta.totalElements;
        this.actualizarPaginacion(total);
        this.mostrarEstado("resultados", total === 1 ? "1 resultado." : `${total} resultados.`);
        this.contenedorTabla.scrollTop = 0;
    }
    alTeclearEnFila(e, item, fila) {
        if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            this.marcar(item, fila);
            if (e.key === "Enter")
                this.confirmar();
        }
        else if (e.key === "ArrowDown" || e.key === "ArrowUp") {
            e.preventDefault();
            const vecina = e.key === "ArrowDown" ? fila.nextElementSibling : fila.previousElementSibling;
            if (vecina instanceof HTMLTableRowElement)
                vecina.focus();
            else if (e.key === "ArrowUp")
                this.inputBusqueda.focus();
        }
    }
    /** Único lugar que cambia el mensaje y el aspecto según el estado. */
    mostrarEstado(estado, mensaje) {
        this.estado.textContent = mensaje;
        this.estado.className = `explorer__estado explorer__estado--${estado}`;
        this.contenedorTabla.setAttribute("aria-busy", String(estado === "cargando"));
        this.contenedorTabla.hidden = estado === "inicial" || estado === "vacio" || estado === "error";
        if (estado === "cargando") {
            this.btnAnterior.disabled = true;
            this.btnSiguiente.disabled = true;
        }
    }
    actualizarPaginacion(total) {
        const hayPaginas = total > 0 && this.totalPaginas > 1;
        this.paginaInfo.textContent = hayPaginas ? `Página ${this.pagina} de ${this.totalPaginas}` : "";
        this.btnAnterior.hidden = !hayPaginas;
        this.btnSiguiente.hidden = !hayPaginas;
        this.btnAnterior.disabled = this.pagina <= 1;
        this.btnSiguiente.disabled = this.pagina >= this.totalPaginas;
    }
    limpiarResultados() {
        this.tbody.replaceChildren();
        this.desmarcar();
        this.pagina = 1;
        this.totalPaginas = 0;
        this.actualizarPaginacion(0);
    }
    // -------------------------------------------------------------------------
    // Filtros
    // -------------------------------------------------------------------------
    async alternarFiltros() {
        const abrir = this.panelFiltros.hidden;
        this.panelFiltros.hidden = !abrir;
        this.btnFiltros.setAttribute("aria-expanded", String(abrir));
        if (abrir)
            await this.cargarOpciones();
    }
    /** Las opciones de los desplegables se piden una sola vez, cuando hacen falta. */
    async cargarOpciones() {
        if (this.opcionesCargadas)
            return;
        this.opcionesCargadas = true;
        try {
            for (const filtro of this.config.filtros) {
                const control = this.controles.get(filtro.clave);
                if (!filtro.opciones || !(control instanceof HTMLSelectElement))
                    continue;
                for (const opcion of await filtro.opciones()) {
                    // Si la opción ya existe (la agregó un filtro fijo), solo se le corrige el texto
                    const existente = Array.from(control.options).find((o) => o.value === opcion.valor);
                    if (existente)
                        existente.text = opcion.etiqueta;
                    else
                        control.append(new Option(opcion.etiqueta, opcion.valor));
                }
            }
        }
        catch (error) {
            this.opcionesCargadas = false; // se reintenta la próxima vez que se abra el panel
            this.mostrarEstado("error", mensajeDeError(error));
        }
    }
    limpiarFiltros() {
        for (const [clave, control] of this.controles) {
            if (!(clave in this.fijos))
                control.value = "";
        }
        this.alCambiarCriterios(false);
    }
    /** "Filtros (2)": deja a la vista cuántos filtros hay puestos aunque el panel esté cerrado. */
    actualizarContadorFiltros() {
        let activos = 0;
        for (const control of this.controles.values()) {
            if (control.value.trim())
                activos += 1;
        }
        this.btnFiltros.textContent = activos > 0 ? `Filtros (${activos})` : "Filtros";
    }
    /** Aplica los valores impuestos por la pantalla y bloquea esos controles. */
    aplicarFijos() {
        for (const [clave, control] of this.controles) {
            const fijo = this.fijos[clave];
            control.disabled = fijo !== undefined;
            if (fijo === undefined) {
                control.value = "";
                continue;
            }
            // Un <select> solo acepta valores que existan como opción: si todavía no se cargó, se agrega
            if (control instanceof HTMLSelectElement && !Array.from(control.options).some((o) => o.value === fijo)) {
                control.append(new Option("Definido por la factura", fijo));
            }
            control.value = fijo;
        }
    }
    // -------------------------------------------------------------------------
    // Selección y cierre
    // -------------------------------------------------------------------------
    marcar(item, fila) {
        this.desmarcar();
        fila.classList.add("es-seleccionada");
        fila.setAttribute("aria-selected", "true");
        this.filaActiva = fila;
        this.seleccionado = item;
        this.btnSeleccionar.disabled = false;
    }
    desmarcar() {
        if (this.filaActiva) {
            this.filaActiva.classList.remove("es-seleccionada");
            this.filaActiva.setAttribute("aria-selected", "false");
        }
        this.filaActiva = null;
        this.seleccionado = null;
        this.btnSeleccionar.disabled = true;
    }
    confirmar() {
        if (this.seleccionado !== null)
            this.cerrar(this.seleccionado);
    }
    cerrar(valor) {
        this.resultado = valor;
        this.dialog.close();
    }
    /** Deja el modal como recién creado (se llama en cada apertura). */
    reiniciar() {
        this.resultado = null;
        this.criteriosActivos = null;
        this.inputBusqueda.value = "";
        this.aplicarFijos();
        this.actualizarContadorFiltros();
        this.limpiarResultados();
        this.mostrarEstado("inicial", this.config.ayudaInicial);
    }
}
//# sourceMappingURL=Explorer.js.map