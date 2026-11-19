import { obtenerDashboard } from "./api/facturas.api.js";
import { ClientExplorer } from "./components/ClientExplorer.js";
import { InvoiceForm } from "./components/InvoiceForm.js";
import type { ResultadoFormulario } from "./components/InvoiceForm.js";
import { InvoiceList } from "./components/InvoiceList.js";
import { avisar } from "./components/Toast.js";
import { $ } from "./utils/dom.js";
import { formatearNumero } from "./utils/format.js";

/**
 * Punto de entrada: crea los componentes y los conecta entre sí.
 * Acá no hay lógica de negocio ni HTML: cada cosa vive en su componente (ver components/).
 */

const dashTotal = $<HTMLElement>("dash-total");
const dashCantidad = $<HTMLElement>("dash-cantidad");
const btnNuevaFactura = $<HTMLButtonElement>("btn-nueva-factura");

// Un solo explorer de clientes, compartido por el filtro del listado y el formulario
const explorerClientes = new ClientExplorer();
const formulario = new InvoiceForm(explorerClientes);
const listado = new InvoiceList({
  explorerClientes,
  alAbrir: (factura) => void abrirFormulario(() => formulario.abrir(factura.id), false),
  alCambiar: () => void cargarDashboard(),
});

/** Abre el formulario y, cuando se cierra, refresca lo que haga falta. */
async function abrirFormulario(abrir: () => Promise<ResultadoFormulario>, esNueva: boolean): Promise<void> {
  const resultado = await abrir();
  if (resultado.mensaje) avisar(resultado.mensaje);
  if (!resultado.huboCambios) return;
  await Promise.all([esNueva ? listado.irAlInicio() : listado.recargar(), cargarDashboard()]);
}

async function cargarDashboard(): Promise<void> {
  try {
    const datos = await obtenerDashboard();
    dashTotal.textContent = `$ ${formatearNumero(datos.totalFacturado)}`;
    dashCantidad.textContent = String(datos.cantidadComprobantes);
  } catch {
    // Las métricas son secundarias: si fallan no se interrumpe el uso de la pantalla
    dashTotal.textContent = "—";
    dashCantidad.textContent = "—";
  }
}

btnNuevaFactura.addEventListener("click", () => void abrirFormulario(() => formulario.abrirNueva(), true));

void listado.recargar();
void cargarDashboard();
