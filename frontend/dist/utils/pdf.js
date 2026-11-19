import { descargarPdf } from "../api/facturas.api.js";
/**
 * Abre el PDF de una factura en una pestaña nueva.
 * La pestaña se abre ANTES de pedir el PDF: si se abriera después, el navegador la bloquearía como pop-up.
 */
export async function abrirPdf(facturaId) {
    const ventana = window.open("", "_blank");
    try {
        const url = URL.createObjectURL(await descargarPdf(facturaId));
        if (ventana) {
            ventana.location.href = url;
        }
        else {
            const enlace = document.createElement("a");
            enlace.href = url;
            enlace.download = `factura-${facturaId}.pdf`;
            enlace.click();
        }
        window.setTimeout(() => URL.revokeObjectURL(url), 60000);
    }
    catch (error) {
        ventana?.close();
        throw error;
    }
}
//# sourceMappingURL=pdf.js.map