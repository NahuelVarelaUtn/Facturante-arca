import { buscarArticulos } from "../api/articulos.api.js";
import { listarMonedas, listarPuntosVenta } from "../api/catalogos.api.js";
import { obtenerConsumidorFinal } from "../api/clientes.api.js";
import { actualizarFactura, crearFactura, emitirFactura, obtenerFactura, sugerirLetra, } from "../api/facturas.api.js";
import { ESTADO_ANULADA, ESTADO_EMITIDA } from "../models/factura.dto.js";
import { boton, celda, el } from "../utils/dom.js";
import { formatearFecha, formatearNumero, hoy, leerNumero, mensajeDeError } from "../utils/format.js";
import { abrirPdf } from "../utils/pdf.js";
import { ArticleExplorer } from "./ArticleExplorer.js";
import { CONDICIONES_FISCALES } from "./ClientExplorer.js";
import { PriceListSelector } from "./PriceListSelector.js";
const TEXTO_LETRA = {
    A: "A",
    B: "B",
    C: "C",
    X: "X — interna, no fiscal",
};
/** Código de comprobante de ARCA para la factura de cada letra. */
const CODIGO_ARCA = { A: "001", B: "006", C: "011", X: null };
/** Estructura fija del formulario. No contiene datos del usuario: esos se cargan con textContent / value. */
const PLANTILLA = `
  <form class="factura" novalidate>
    <header class="factura__encabezado">
      <h2 data-ref="titulo"></h2>
      <span class="insignia" data-ref="estado"></span>
    </header>

    <p class="factura__bloqueo" data-ref="bloqueo" hidden></p>
    <p class="factura__aviso" data-ref="aviso" role="alert" hidden></p>

    <div class="factura__contenido" data-ref="contenido">
      <!-- Un <fieldset disabled> bloquea TODOS los controles que tiene adentro: así se hace el solo lectura -->
      <fieldset class="factura__campos" data-ref="campos">

        <section>
          <h3>Comprobante</h3>
          <div class="campos">
            <label>Letra
              <select data-ref="letra"></select>
            </label>
            <label>Tipo de comprobante
              <input data-ref="tipo" type="text" readonly tabindex="-1">
            </label>
            <label>Punto de venta (sucursal)
              <select data-ref="puntoVenta"></select>
            </label>
            <label>Nº de factura
              <input data-ref="numero" type="text" readonly tabindex="-1">
            </label>
            <label>Fecha de factura
              <input data-ref="fecha" type="date">
            </label>
            <label>Fecha de emisión <span class="ayuda">(la que se informa a ARCA)</span>
              <input data-ref="fechaEmision" type="date">
            </label>
            <label>Moneda
              <select data-ref="moneda"></select>
            </label>
          </div>
          <p class="factura__ayuda" data-ref="ayudaLetra"></p>
        </section>

        <section>
          <h3>Cliente</h3>
          <div class="campos">
            <label class="ancho-2">Cliente
              <span class="campo-modal">
                <input data-ref="cliente" type="text" readonly placeholder="Buscá un cliente">
                <button data-ref="btnCliente" class="btn btn--secondary" type="button">Buscar…</button>
              </span>
            </label>
            <label>Condición frente al IVA
              <select data-ref="condicionIva"></select>
            </label>
          </div>
        </section>

        <section>
          <div data-ref="listaPrecios"></div>
        </section>

        <section>
          <h3>Renglones</h3>
          <div class="entrada-renglon" data-ref="entradaRenglon">
            <label>Artículo
              <span class="campo-modal">
                <input data-ref="articulo" type="text" readonly placeholder="Buscá un artículo">
                <button data-ref="btnArticulo" class="btn btn--secondary" type="button">Buscar…</button>
              </span>
            </label>
            <label>Precio unitario
              <input data-ref="precio" type="text" readonly tabindex="-1" placeholder="—">
            </label>
            <label>Cantidad
              <input data-ref="cantidad" type="number" min="0" step="any" value="1" inputmode="decimal">
            </label>
            <button data-ref="btnAgregar" class="btn btn--secondary" type="button">Agregar renglón</button>
          </div>

          <div class="tabla-scroll">
            <table aria-label="Renglones de la factura">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Artículo</th>
                  <th class="num">Cantidad</th>
                  <th class="num">Precio unit.</th>
                  <th class="num">IVA</th>
                  <th class="num">Subtotal</th>
                  <th><span class="solo-lector">Acciones</span></th>
                </tr>
              </thead>
              <tbody data-ref="renglones"></tbody>
            </table>
          </div>

          <dl class="factura__totales" data-ref="totales"></dl>
        </section>

        <section>
          <label>Observaciones
            <textarea data-ref="observaciones" maxlength="500"></textarea>
          </label>
        </section>
      </fieldset>
    </div>

    <footer class="factura__pie">
      <button data-ref="btnPdf" class="btn btn--secondary" type="button">Imprimir PDF</button>
      <span class="factura__separador"></span>
      <button data-ref="btnCerrar" class="btn btn--secondary" type="button">Cancelar</button>
      <button data-ref="btnConvertir" class="btn btn--secondary" type="button">Convertir a Factura A/B</button>
      <button data-ref="btnGuardar" class="btn btn--secondary" type="button">Guardar borrador</button>
      <button data-ref="btnFacturar" class="btn btn--primario" type="button">Facturar</button>
    </footer>
  </form>
`;
export class InvoiceForm {
    /** @param explorerClientes se recibe de afuera para compartir un solo modal con el filtro del listado */
    constructor(explorerClientes) {
        /** Se crea ya con la plantilla adentro: las referencias de abajo la necesitan para resolverse. */
        this.dialog = crearDialogo();
        this.explorerArticulos = new ArticleExplorer();
        // Referencias al DOM (se resuelven una sola vez en el constructor)
        this.titulo = this.ref("titulo");
        this.insignia = this.ref("estado");
        this.bloqueo = this.ref("bloqueo");
        this.aviso = this.ref("aviso");
        this.contenido = this.ref("contenido");
        this.campos = this.ref("campos");
        this.letra = this.ref("letra");
        this.tipo = this.ref("tipo");
        this.puntoVenta = this.ref("puntoVenta");
        this.numero = this.ref("numero");
        this.fecha = this.ref("fecha");
        this.fechaEmision = this.ref("fechaEmision");
        this.moneda = this.ref("moneda");
        this.ayudaLetra = this.ref("ayudaLetra");
        this.inputCliente = this.ref("cliente");
        this.btnCliente = this.ref("btnCliente");
        this.condicionIva = this.ref("condicionIva");
        this.entradaRenglon = this.ref("entradaRenglon");
        this.inputArticulo = this.ref("articulo");
        this.btnArticulo = this.ref("btnArticulo");
        this.inputPrecio = this.ref("precio");
        this.inputCantidad = this.ref("cantidad");
        this.btnAgregar = this.ref("btnAgregar");
        this.tbody = this.ref("renglones");
        this.totales = this.ref("totales");
        this.observaciones = this.ref("observaciones");
        this.btnPdf = this.ref("btnPdf");
        this.btnCerrar = this.ref("btnCerrar");
        this.btnConvertir = this.ref("btnConvertir");
        this.btnGuardar = this.ref("btnGuardar");
        this.btnFacturar = this.ref("btnFacturar");
        // Estado de la factura que se está cargando
        this.facturaId = null;
        this.soloLectura = false;
        /** true si la factura abierta es una X que ya fue emitida (tiene número interno pero sigue editable). */
        this.emitidaInterna = false;
        /** Número con el que está guardada la factura (null si todavía no tiene). */
        this.numeroGuardado = null;
        /** true después de usar "Convertir a Factura A/B" (para explicarle al usuario qué sigue). */
        this.convertida = false;
        /** Cartel que explica por qué la factura está bloqueada (null si es editable). */
        this.textoSoloLectura = null;
        this.cliente = null;
        this.renglones = [];
        this.articuloElegido = null;
        /** Letra que corresponde según emisor y receptor (la calcula el backend). */
        this.letraSugerida = null;
        /** true cuando el usuario eligió una letra distinta de la sugerida: deja de actualizarse sola. */
        this.letraManual = false;
        /** false si el emisor no discrimina IVA (Monotributo / Exento): el total es igual al neto. */
        this.discriminaIva = true;
        this.sucio = false;
        this.ocupado = false;
        this.catalogos = null;
        this.resultado = { huboCambios: false };
        this.resolver = null;
        this.explorerClientes = explorerClientes;
        document.body.append(this.dialog);
        this.selectorLista = new PriceListSelector(this.ref("listaPrecios"));
        for (const c of CONDICIONES_FISCALES)
            this.condicionIva.append(new Option(c.etiqueta, c.valor));
        this.conectarEventos();
    }
    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------
    /** Abre el formulario vacío para cargar una factura nueva. */
    async abrirNueva() {
        const cierre = this.mostrar();
        try {
            await this.prepararCatalogos();
            this.reiniciar();
            // Arranca lista para operar: Consumidor Final ya elegido (y su letra calculada), sin clics extra
            await this.asignarConsumidorFinal();
            this.sucio = false;
        }
        catch (error) {
            this.mostrarAviso(mensajeDeError(error));
        }
        return cierre;
    }
    /** Abre una factura existente: editable si es borrador, solo lectura si ya fue emitida o anulada. */
    async abrir(id) {
        const cierre = this.mostrar();
        try {
            const [factura] = await Promise.all([obtenerFactura(id), this.prepararCatalogos()]);
            this.reiniciar();
            await this.cargar(factura);
        }
        catch (error) {
            this.mostrarAviso(mensajeDeError(error));
        }
        return cierre;
    }
    // -------------------------------------------------------------------------
    // Apertura, carga y cierre
    // -------------------------------------------------------------------------
    mostrar() {
        this.resultado = { huboCambios: false };
        this.titulo.textContent = "Cargando…";
        this.campos.disabled = true;
        const cierre = new Promise((resolve) => {
            this.resolver = resolve;
        });
        if (!this.dialog.open)
            this.dialog.showModal();
        return cierre;
    }
    /** Puntos de venta, monedas y listas de precios: pocas filas fijas, se piden una sola vez. */
    prepararCatalogos() {
        this.catalogos ?? (this.catalogos = (async () => {
            const [puntos, monedas] = await Promise.all([listarPuntosVenta(), listarMonedas(), this.selectorLista.cargar()]);
            for (const p of puntos) {
                const nro = String(p.numero).padStart(4, "0");
                this.puntoVenta.append(new Option(p.descripcion ? `${nro} — ${p.descripcion}` : nro, String(p.id)));
            }
            for (const m of monedas) {
                this.moneda.append(new Option(m.descripcion ? `${m.codigo} — ${m.descripcion}` : m.codigo, String(m.id)));
            }
        })().catch((error) => {
            this.catalogos = null; // si falló, se reintenta la próxima vez que se abra
            throw error;
        }));
        return this.catalogos;
    }
    /** Deja el formulario como una factura nueva y vacía. */
    reiniciar() {
        this.facturaId = null;
        this.soloLectura = false;
        this.cliente = null;
        this.renglones = [];
        this.letraSugerida = null;
        this.letraManual = false;
        this.convertida = false;
        this.sucio = false;
        this.fecha.value = hoy();
        this.fechaEmision.value = hoy();
        this.puntoVenta.selectedIndex = 0;
        this.moneda.selectedIndex = 0;
        this.inputCliente.value = "";
        this.condicionIva.value = "CONSUMIDOR_FINAL";
        this.observaciones.value = "";
        this.inputCantidad.value = "1";
        this.limpiarArticuloElegido();
        this.llenarLetras([], null);
        this.ayudaLetra.textContent = "";
        this.ocultarAviso();
        this.actualizarModo(null);
        this.renderRenglones();
        this.contenido.scrollTop = 0; // el modal se reutiliza: siempre arranca desde arriba
    }
    /** Vuelca una factura guardada en el formulario. */
    async cargar(f) {
        this.facturaId = f.id;
        this.soloLectura = !f.editable;
        this.cliente = {
            id: f.clienteId,
            nombre: f.clienteNombre,
            cuit: f.clienteCuit,
            condicionIva: f.condicionIvaReceptor,
        };
        this.renglones = (f.detalles ?? []).map((d) => ({
            precioId: d.articuloId,
            codigo: d.articuloCodigo ?? null,
            descripcion: d.articuloDescripcion ?? `Artículo #${d.articuloId}`,
            cantidad: d.cantidad,
            precioUnitario: d.precioUnitario,
            alicuotaIva: d.alicuotaIva ?? 0,
        }));
        this.fecha.value = f.fecha.slice(0, 10);
        this.fechaEmision.value = f.fechaEmision ? f.fechaEmision.slice(0, 10) : this.soloLectura ? "" : hoy();
        this.puntoVenta.value = String(f.puntoVentaId);
        this.moneda.value = String(f.tipoMonedaId);
        this.selectorLista.seleccionar(f.listaPrecioId);
        this.observaciones.value = f.observaciones ?? "";
        this.mostrarCliente();
        this.condicionIva.value = f.condicionIvaReceptor;
        // Se conserva la letra con la que se guardó, aunque no sea la sugerida
        this.letraManual = true;
        this.llenarLetras([f.letra], f.letra);
        if (!this.soloLectura)
            await this.actualizarLetra();
        this.actualizarModo(f);
        this.renderRenglones();
        this.sucio = false;
    }
    /** Título, insignia y bloqueo según sea nueva, borrador, Factura X emitida o solo lectura. */
    actualizarModo(f) {
        const estado = f?.estado ?? "BORRADOR";
        this.numeroGuardado = f?.nroFactura ?? null;
        this.emitidaInterna = estado === ESTADO_EMITIDA && f?.letra === "X";
        this.titulo.textContent = !f
            ? "Nueva factura"
            : this.numeroGuardado
                ? `Factura ${f.letra} ${this.numeroGuardado}`
                : `Borrador #${f.id}`;
        this.insignia.textContent =
            estado === ESTADO_ANULADA ? "Anulada" : this.emitidaInterna ? "Emitida · interna" : estado === ESTADO_EMITIDA ? "Emitida" : "Borrador";
        this.insignia.className = `insignia ${estado === ESTADO_ANULADA ? "insignia--anulada" : estado === ESTADO_EMITIDA ? "insignia--informada" : "insignia--activa"}`;
        this.campos.disabled = this.soloLectura;
        this.entradaRenglon.hidden = this.soloLectura;
        this.btnPdf.hidden = this.facturaId === null;
        this.btnCerrar.textContent = this.soloLectura ? "Cerrar" : "Cancelar";
        this.textoSoloLectura = !f || !this.soloLectura
            ? null
            : f.cae
                ? `Solo lectura: ARCA otorgó el CAE ${f.cae} (vence el ${formatearFecha(f.fechaVtoCae)}). Una factura con CAE no se puede modificar.`
                : estado === ESTADO_ANULADA
                    ? "Solo lectura: la factura está anulada."
                    : "Solo lectura: la factura ya fue emitida.";
        this.actualizarDatosDeLetra();
    }
    /** Cierra pidiendo confirmación si hay cambios sin guardar. */
    intentarCerrar() {
        if (this.ocupado)
            return;
        if (this.sucio && !this.soloLectura && !window.confirm("Hay cambios sin guardar. ¿Cerrar igual?"))
            return;
        this.dialog.close();
    }
    cerrarCon(mensaje) {
        this.resultado = { huboCambios: true, mensaje };
        this.sucio = false;
        this.dialog.close();
    }
    // -------------------------------------------------------------------------
    // Eventos
    // -------------------------------------------------------------------------
    conectarEventos() {
        // Cualquier cambio en un campo marca el formulario como "con cambios sin guardar"
        this.campos.addEventListener("input", () => {
            this.sucio = true;
        });
        this.btnCliente.addEventListener("click", () => void this.elegirCliente());
        this.condicionIva.addEventListener("change", () => void this.alCambiarCondicion());
        this.letra.addEventListener("change", () => {
            this.letraManual = this.letra.value !== this.letraSugerida;
            this.convertida = false;
            this.actualizarDatosDeLetra();
        });
        this.btnConvertir.addEventListener("click", () => this.convertirAFiscal());
        this.btnArticulo.addEventListener("click", () => void this.elegirArticulo());
        this.btnAgregar.addEventListener("click", () => this.agregarRenglon());
        this.inputCantidad.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                this.agregarRenglon();
            }
        });
        // Cambiar de lista con renglones cargados: se recalculan con los precios de la lista nueva
        this.selectorLista.alCambiar = (nueva, anterior) => {
            if (this.renglones.length > 0) {
                const confirmado = window.confirm(`Los renglones cargados van a pasar a los precios de la lista ${nueva.nombre}. ¿Continuar?`);
                if (!confirmado)
                    return false;
                void this.recalcularPrecios(nueva, anterior);
            }
            this.limpiarArticuloElegido();
            this.sucio = true;
            return true;
        };
        this.btnGuardar.addEventListener("click", () => void this.guardarBorrador());
        this.btnFacturar.addEventListener("click", () => void this.facturar());
        this.btnCerrar.addEventListener("click", () => this.intentarCerrar());
        this.btnPdf.addEventListener("click", () => void this.imprimir());
        // Enter dentro de un campo no debe enviar el formulario
        this.dialog.querySelector("form")?.addEventListener("submit", (e) => e.preventDefault());
        // "cancel" = tecla Escape: se usa el mismo cierre con confirmación
        this.dialog.addEventListener("cancel", (e) => {
            e.preventDefault();
            this.intentarCerrar();
        });
        this.dialog.addEventListener("close", () => {
            const resolver = this.resolver;
            this.resolver = null;
            resolver?.(this.resultado);
        });
    }
    // -------------------------------------------------------------------------
    // Cliente, condición frente al IVA y letra
    // -------------------------------------------------------------------------
    async elegirCliente() {
        const cliente = await this.explorerClientes.abrir();
        if (cliente)
            await this.asignarCliente(cliente);
    }
    /** Cliente genérico para ventas sin identificar. Es el valor por defecto de una factura nueva. */
    async asignarConsumidorFinal() {
        await this.asignarCliente(await obtenerConsumidorFinal());
    }
    /**
     * La condición frente al IVA se cambia directo desde su desplegable:
     *  - "Consumidor Final"  -> venta sin identificar: se asigna sola el cliente genérico.
     *  - cualquier otra      -> hace falta un cliente con CUIT: si el actual no tiene, se abre el buscador
     *                           ya filtrado por esa condición.
     */
    async alCambiarCondicion() {
        const condicion = this.condicionIva.value;
        try {
            if (condicion === "CONSUMIDOR_FINAL") {
                await this.asignarConsumidorFinal();
                return;
            }
            if (!this.cliente?.cuit) {
                const cliente = await this.explorerClientes.abrir({ fijos: { condicionIva: condicion } });
                if (cliente) {
                    await this.asignarCliente(cliente);
                }
                else {
                    // Canceló la búsqueda: se vuelve a Consumidor Final para no dejar una combinación inválida
                    this.condicionIva.value = "CONSUMIDOR_FINAL";
                    await this.actualizarLetra();
                }
                return;
            }
            await this.actualizarLetra();
        }
        catch (error) {
            this.mostrarAviso(mensajeDeError(error));
        }
    }
    async asignarCliente(cliente) {
        this.cliente = cliente;
        this.sucio = true;
        this.mostrarCliente();
        // La condición arranca con la del cliente; al cambiar de cliente la letra vuelve a calcularse sola
        this.condicionIva.value = cliente.condicionIva ?? "CONSUMIDOR_FINAL";
        this.letraManual = false;
        await this.actualizarLetra();
    }
    mostrarCliente() {
        const c = this.cliente;
        this.inputCliente.value = !c ? "" : c.cuit ? `${c.nombre} — CUIT ${c.cuit}` : c.nombre;
    }
    /** Pregunta al backend qué letra corresponde a la condición elegida y actualiza el desplegable. */
    async actualizarLetra() {
        try {
            const info = await sugerirLetra(this.condicionIva.value);
            this.letraSugerida = info.letra;
            this.discriminaIva = info.discriminaIva;
            const actual = this.letra.value;
            const conservar = this.letraManual && info.letrasPermitidas.includes(actual);
            if (!conservar)
                this.letraManual = false;
            this.llenarLetras(info.letrasPermitidas, conservar ? actual : info.letra);
            const receptor = CONDICIONES_FISCALES.find((c) => c.valor === this.condicionIva.value)?.etiqueta ?? "";
            const emisor = CONDICIONES_FISCALES.find((c) => c.valor === info.condicionIvaEmisor)?.etiqueta ?? "";
            this.ayudaLetra.textContent = `Letra automática: ${info.letra} (emisor ${emisor} → cliente ${receptor}). Podés cambiarla.`;
            this.renderTotales();
        }
        catch (error) {
            this.mostrarAviso(`No se pudo determinar la letra. ${mensajeDeError(error)}`);
        }
    }
    llenarLetras(letras, elegida) {
        this.letra.replaceChildren();
        if (letras.length === 0)
            this.letra.append(new Option("—", ""));
        for (const l of letras)
            this.letra.append(new Option(TEXTO_LETRA[l], l));
        this.letra.disabled = letras.length === 0;
        if (elegida)
            this.letra.value = elegida;
        this.actualizarDatosDeLetra();
    }
    /** Lo que depende de la letra elegida: tipo de comprobante, número, botones disponibles y cartel informativo. */
    actualizarDatosDeLetra() {
        const letra = this.letra.value;
        const esInterna = letra === "X";
        /** La factura abierta es una X ya emitida y SIGUE siendo X (no se la está convirtiendo). */
        const sigueSiendoXEmitida = this.emitidaInterna && esInterna;
        this.tipo.value = !letra
            ? ""
            : esInterna
                ? "Factura X · interna"
                : `Factura ${letra} · cód. ${CODIGO_ARCA[letra]}`;
        // Al convertir una X numerada, su número interno deja de valer: el nuevo lo asigna ARCA
        this.numero.value = this.emitidaInterna && !esInterna ? "" : (this.numeroGuardado ?? "");
        this.numero.placeholder = esInterna ? "A asignar por el sistema" : "A asignar por ARCA";
        // El número de una X ya emitida incluye el punto de venta: no se puede cambiar
        this.puntoVenta.disabled = sigueSiendoXEmitida;
        this.btnGuardar.hidden = this.soloLectura;
        this.btnGuardar.textContent =
            this.facturaId === null ? "Guardar borrador" : sigueSiendoXEmitida ? "Guardar cambios" : "Actualizar borrador";
        this.btnFacturar.hidden = this.soloLectura || sigueSiendoXEmitida;
        this.btnFacturar.textContent = esInterna ? "Emitir Factura X" : "Facturar · Solicitar CAE a ARCA";
        // Convertir tiene sentido sobre una X que ya está guardada
        this.btnConvertir.hidden = this.soloLectura || !esInterna || this.facturaId === null;
        const cartel = this.textoSoloLectura ??
            (esInterna
                ? `Factura X: comprobante interno. No se informa a ARCA y se puede seguir editando.${this.facturaId === null ? "" : " Para emitirla oficialmente usá «Convertir a Factura A/B»."}`
                : this.emitidaInterna
                    ? `Conversión a Factura ${letra}: al guardar pierde el número interno de la X y queda como borrador hasta que ARCA la autorice. Revisá la condición frente al IVA y presioná «Facturar» para solicitar el CAE.`
                    : this.convertida
                        ? `Ahora es una Factura ${letra}. Revisá la condición frente al IVA (define la letra) y presioná «Facturar» para solicitar el CAE.`
                        : null);
        this.bloqueo.hidden = cartel === null;
        this.bloqueo.textContent = cartel ?? "";
    }
    /**
     * "Convertir a Factura A/B": pasa la X a la letra fiscal que corresponde al cliente.
     * No habla con ARCA todavía: deja todo listo para que el usuario revise y presione "Facturar".
     */
    convertirAFiscal() {
        const opciones = Array.from(this.letra.options).map((o) => o.value);
        const destino = this.letraSugerida && this.letraSugerida !== "X" ? this.letraSugerida : opciones.find((l) => l !== "X");
        if (!destino) {
            this.mostrarAviso("No hay una letra fiscal disponible para este emisor.");
            return;
        }
        this.letra.value = destino;
        this.letraManual = false; // desde acá la letra vuelve a seguir a la condición frente al IVA
        this.convertida = true;
        this.fechaEmision.value = hoy(); // ARCA solo acepta fechas de emisión cercanas al día
        this.sucio = true;
        this.ocultarAviso();
        this.actualizarDatosDeLetra();
        this.contenido.scrollTop = 0;
        this.condicionIva.focus();
    }
    // -------------------------------------------------------------------------
    // Renglones
    // -------------------------------------------------------------------------
    async elegirArticulo() {
        const lista = this.selectorLista.valor;
        if (!lista) {
            this.mostrarAviso("No hay una lista de precios seleccionada.");
            return;
        }
        // El explorer solo ofrece precios de la lista activa de la factura
        const articulo = await this.explorerArticulos.abrirConLista(lista.id);
        if (!articulo)
            return;
        this.articuloElegido = articulo;
        this.inputArticulo.value = `${articulo.codigo ?? "s/cód."} — ${articulo.descripcion}`;
        this.inputPrecio.value = `${formatearNumero(articulo.precio)} (${articulo.listaPrecio})`;
        this.inputCantidad.focus();
        this.inputCantidad.select();
    }
    limpiarArticuloElegido() {
        this.articuloElegido = null;
        this.inputArticulo.value = "";
        this.inputPrecio.value = "";
    }
    agregarRenglon() {
        const articulo = this.articuloElegido;
        const cantidad = leerNumero(this.inputCantidad.value);
        if (!articulo) {
            this.mostrarAviso("Primero buscá un artículo.");
            this.btnArticulo.focus();
            return;
        }
        if (cantidad === undefined || cantidad <= 0) {
            this.mostrarAviso("La cantidad debe ser mayor a 0.");
            this.inputCantidad.focus();
            return;
        }
        // El mismo artículo dos veces suma cantidad en vez de duplicar el renglón
        const existente = this.renglones.find((r) => r.precioId === articulo.id);
        if (existente) {
            existente.cantidad += cantidad;
        }
        else {
            this.renglones.push(this.aRenglon(articulo, cantidad));
        }
        this.sucio = true;
        this.ocultarAviso();
        this.limpiarArticuloElegido();
        this.inputCantidad.value = "1";
        this.renderRenglones();
        this.btnArticulo.focus();
    }
    aRenglon(articulo, cantidad) {
        return {
            precioId: articulo.id,
            codigo: articulo.codigo ?? null,
            descripcion: articulo.descripcion,
            cantidad,
            precioUnitario: articulo.precio,
            alicuotaIva: articulo.alicuotaIva ?? 21,
        };
    }
    /** Busca cada artículo cargado en la lista nueva y le actualiza el precio. */
    async recalcularPrecios(nueva, anterior) {
        this.ponerOcupado(true);
        try {
            const sinPrecio = [];
            const actualizados = [];
            for (const renglon of this.renglones) {
                const candidatos = renglon.codigo
                    ? (await buscarArticulos({ codigo: renglon.codigo, listaPrecioId: nueva.id })).content
                    : [];
                const precio = candidatos.find((a) => a.codigo === renglon.codigo);
                if (precio)
                    actualizados.push(this.aRenglon(precio, renglon.cantidad));
                else
                    sinPrecio.push(renglon.descripcion);
            }
            this.renglones = actualizados;
            this.renderRenglones();
            if (sinPrecio.length > 0) {
                this.mostrarAviso(`Se quitaron artículos que no tienen precio en la lista ${nueva.nombre}: ${sinPrecio.join(", ")}.`);
            }
            else {
                this.ocultarAviso();
            }
        }
        catch (error) {
            // No se pudo recalcular: se vuelve a la lista anterior para no mezclar precios
            if (anterior)
                this.selectorLista.seleccionar(anterior.id);
            this.mostrarAviso(`No se pudieron actualizar los precios. ${mensajeDeError(error)}`);
        }
        finally {
            this.ponerOcupado(false);
        }
    }
    renderRenglones() {
        this.tbody.replaceChildren();
        if (this.renglones.length === 0) {
            const fila = el("tr");
            const vacio = el("td", "vacio", "Todavía no hay renglones. Buscá un artículo y agregalo.");
            vacio.colSpan = 7;
            fila.append(vacio);
            this.tbody.append(fila);
        }
        this.renglones.forEach((r, indice) => {
            const fila = el("tr");
            fila.append(celda(r.codigo ?? "—"), celda(r.descripcion), celda(String(r.cantidad), "num"), celda(formatearNumero(r.precioUnitario), "num"), celda(this.discriminaIva ? porcentaje(r.alicuotaIva) : "—", "num"), celda(formatearNumero(redondear(r.cantidad * r.precioUnitario)), "num"));
            const acciones = el("td", "acciones");
            if (!this.soloLectura) {
                const quitar = boton("Quitar", "btn btn--ghost", `Quitar ${r.descripcion}`);
                quitar.addEventListener("click", () => {
                    this.renglones.splice(indice, 1);
                    this.sucio = true;
                    this.renderRenglones();
                });
                acciones.append(quitar);
            }
            fila.append(acciones);
            this.tbody.append(fila);
        });
        this.renderTotales();
    }
    /**
     * Misma cuenta que hace el backend: el IVA se calcula por alícuota sobre la base total de esa alícuota.
     * Es una vista previa: los importes definitivos son los que devuelve el backend al guardar.
     */
    renderTotales() {
        const bases = new Map();
        for (const r of this.renglones) {
            const alicuota = this.discriminaIva ? r.alicuotaIva : 0;
            bases.set(alicuota, (bases.get(alicuota) ?? 0) + redondear(r.cantidad * r.precioUnitario));
        }
        let neto = 0;
        let iva = 0;
        const lineasIva = [];
        for (const [alicuota, base] of [...bases].sort((a, b) => a[0] - b[0])) {
            const importe = redondear((base * alicuota) / 100);
            neto += base;
            iva += importe;
            if (this.discriminaIva)
                lineasIva.push([`IVA ${porcentaje(alicuota)}`, importe]);
        }
        this.totales.replaceChildren();
        const linea = (etiqueta, importe, clase) => {
            const fila = el("div", clase);
            fila.append(el("dt", undefined, etiqueta), el("dd", undefined, `$ ${formatearNumero(importe)}`));
            this.totales.append(fila);
        };
        linea(this.discriminaIva ? "Neto gravado" : "Subtotal", neto);
        for (const [etiqueta, importe] of lineasIva)
            linea(etiqueta, importe);
        linea("Total", redondear(neto + iva), "factura__total");
    }
    // -------------------------------------------------------------------------
    // Guardar borrador / Facturar
    // -------------------------------------------------------------------------
    /** Valida y arma el cuerpo que espera el backend. Devuelve null (y avisa) si falta algo. */
    armarSolicitud() {
        const puntoVentaId = leerNumero(this.puntoVenta.value);
        const tipoMonedaId = leerNumero(this.moneda.value);
        const lista = this.selectorLista.valor;
        const falta = !this.cliente
            ? "Elegí un cliente (o Consumidor Final)."
            : !this.letra.value
                ? "Falta definir la letra del comprobante."
                : puntoVentaId === undefined
                    ? "Elegí el punto de venta."
                    : tipoMonedaId === undefined
                        ? "Elegí la moneda."
                        : !this.fecha.value
                            ? "Indicá la fecha de factura."
                            : !lista
                                ? "Elegí la lista de precios."
                                : this.renglones.length === 0
                                    ? "Agregá al menos un renglón."
                                    : null;
        if (falta || !this.cliente || puntoVentaId === undefined || tipoMonedaId === undefined || !lista) {
            this.mostrarAviso(falta ?? "Faltan datos obligatorios.");
            return null;
        }
        return {
            clienteId: this.cliente.id,
            condicionIvaReceptor: this.condicionIva.value,
            puntoVentaId,
            tipoMonedaId,
            listaPrecioId: lista.id,
            fecha: this.fecha.value,
            fechaEmision: this.fechaEmision.value || undefined,
            letra: this.letra.value,
            observaciones: this.observaciones.value.trim() || undefined,
            detalles: this.renglones.map((r) => ({ articuloId: r.precioId, cantidad: r.cantidad })),
        };
    }
    /** Crea el borrador o, si ya existe, lo actualiza. Es el mismo botón: "Guardar" funciona como "Actualizar". */
    async guardar(solicitud) {
        const guardada = this.facturaId === null ? await crearFactura(solicitud) : await actualizarFactura(this.facturaId, solicitud);
        this.facturaId = guardada.id;
        this.resultado = { huboCambios: true };
        this.sucio = false;
        return guardada;
    }
    async guardarBorrador() {
        const solicitud = this.armarSolicitud();
        if (!solicitud || this.ocupado)
            return;
        this.ponerOcupado(true);
        try {
            const eraInternaEmitida = this.emitidaInterna;
            const guardada = await this.guardar(solicitud);
            this.cerrarCon(guardada.estado === ESTADO_EMITIDA
                ? `Factura X ${guardada.nroFactura} actualizada.`
                : eraInternaEmitida
                    ? `La Factura X se convirtió en el borrador #${guardada.id} (Factura ${guardada.letra}). Falta facturarlo para obtener el CAE.`
                    : `Borrador #${guardada.id} guardado (Factura ${guardada.letra}). Todavía no se informó a ARCA.`);
        }
        catch (error) {
            this.mostrarAviso(mensajeDeError(error));
        }
        finally {
            this.ponerOcupado(false);
        }
    }
    async facturar() {
        const solicitud = this.armarSolicitud();
        if (!solicitud || this.ocupado)
            return;
        const esInterna = solicitud.letra === "X";
        const pregunta = esInterna
            ? "¿Emitir la Factura X? No se informa a ARCA: el sistema le asigna un número interno. Vas a poder seguir editándola o convertirla en Factura A/B."
            : `¿Solicitar el CAE a ARCA para esta Factura ${solicitud.letra}? Una vez autorizada ya no se podrá modificar.`;
        if (!window.confirm(pregunta))
            return;
        this.ponerOcupado(true, esInterna ? "Emitiendo…" : "Solicitando CAE a ARCA…");
        let guardada = null;
        try {
            guardada = await this.guardar(solicitud);
            const emitida = await emitirFactura(guardada.id);
            this.cerrarCon(emitida.cae
                ? `Factura ${emitida.letra} ${emitida.nroFactura} autorizada por ARCA. CAE ${emitida.cae}.`
                : `Factura ${emitida.letra} ${emitida.nroFactura} emitida.`);
        }
        catch (error) {
            // Si falló la emisión, el borrador ya quedó guardado: se sigue editando ese mismo borrador
            if (guardada)
                this.actualizarModo(guardada);
            this.mostrarAviso(guardada
                ? `No se pudo emitir. ${mensajeDeError(error)} El borrador #${guardada.id} quedó guardado: corregilo y volvé a intentar.`
                : mensajeDeError(error));
        }
        finally {
            this.ponerOcupado(false);
        }
    }
    async imprimir() {
        if (this.facturaId === null)
            return;
        try {
            await abrirPdf(this.facturaId);
        }
        catch (error) {
            this.mostrarAviso(mensajeDeError(error));
        }
    }
    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------
    /** Mientras hay una operación en curso se bloquean los botones (evita el doble clic en "Facturar"). */
    ponerOcupado(ocupado, textoFacturar) {
        this.ocupado = ocupado;
        for (const b of [this.btnGuardar, this.btnFacturar, this.btnConvertir, this.btnCerrar, this.btnPdf])
            b.disabled = ocupado;
        this.dialog.setAttribute("aria-busy", String(ocupado));
        if (ocupado && textoFacturar)
            this.btnFacturar.textContent = textoFacturar;
        if (!ocupado)
            this.actualizarDatosDeLetra(); // restaura el texto del botón
    }
    /** Los avisos van DENTRO del modal: un aviso flotante de la página quedaría tapado por el <dialog>. */
    mostrarAviso(mensaje) {
        this.aviso.textContent = mensaje;
        this.aviso.hidden = false;
        this.aviso.scrollIntoView({ block: "nearest" });
    }
    ocultarAviso() {
        this.aviso.hidden = true;
    }
    ref(nombre) {
        const nodo = this.dialog.querySelector(`[data-ref="${nombre}"]`);
        if (!nodo)
            throw new Error(`InvoiceForm: falta data-ref="${nombre}" en la plantilla`);
        return nodo;
    }
}
function crearDialogo() {
    const dialog = el("dialog", "factura-modal");
    dialog.innerHTML = PLANTILLA;
    return dialog;
}
/** 10.5 -> "10,5%" */
function porcentaje(n) {
    return `${String(n).replace(".", ",")}%`;
}
/** Redondeo a 2 decimales (centavos). */
function redondear(n) {
    return Math.round((n + Number.EPSILON) * 100) / 100;
}
//# sourceMappingURL=InvoiceForm.js.map