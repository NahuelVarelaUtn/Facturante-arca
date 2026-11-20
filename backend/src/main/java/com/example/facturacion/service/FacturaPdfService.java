package com.example.facturacion.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.facturacion.exception.ResourceNotFoundException;
import com.example.facturacion.model.EstadoFactura;
import com.example.facturacion.model.FacturaVenta;
import com.example.facturacion.model.FacturaVentaDetalle;
import com.example.facturacion.repository.FacturaVentaRepository;

// Importaciones de iText (Se asume iText 7)
import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

@Service
public class FacturaPdfService {

    // Datos del emisor: fijos para el TP.
    private static final String EMISOR_RAZON_SOCIAL = "MI EMPRESA S.R.L.";
    private static final String EMISOR_DOMICILIO = "San Martín 1234 - Mendoza (5500)";
    private static final String EMISOR_CONDICION_IVA = "IVA Responsable Inscripto";
    private static final String EMISOR_CUIT = "30-12345678-9";
    private static final String EMISOR_INICIO_ACTIVIDADES = "01/03/2020";

    private static final Locale LOCALE_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private record Fuentes(PdfFont normal, PdfFont negrita) {}

    private final FacturaVentaRepository facturaRepository;

    public FacturaPdfService(FacturaVentaRepository facturaRepository) {
        this.facturaRepository = facturaRepository;
    }

    @Transactional(readOnly = true)
    public byte[] generar(Long id) {
        FacturaVenta factura = facturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FacturaVenta", id));

        NumberFormat dinero = NumberFormat.getNumberInstance(LOCALE_AR); // 1.234,50
        dinero.setMinimumFractionDigits(2);
        dinero.setMaximumFractionDigits(2);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            Fuentes fuentes = new Fuentes(
                    PdfFontFactory.createFont(StandardFonts.HELVETICA),
                    PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD));

            PdfDocument pdf = new PdfDocument(new PdfWriter(salida));
            try (Document documento = new Document(pdf, PageSize.A4)) {
                documento.setFont(fuentes.normal()).setFontSize(9);
                documento.setMargins(30, 30, 30, 30);

                documento.add(new Paragraph("ORIGINAL").setFont(fuentes.negrita())
                        .setTextAlignment(TextAlignment.CENTER)
                        .setBorder(new SolidBorder(1)).setPadding(3));
                documento.add(cabecera(factura, fuentes));
                documento.add(datosCliente(factura, fuentes).setMarginTop(8));
                documento.add(detalle(factura, fuentes, dinero).setMarginTop(8));
                documento.add(totales(factura, fuentes, dinero).setMarginTop(8));

                if (factura.getObservaciones() != null && !factura.getObservaciones().isBlank()) {
                    documento.add(new Paragraph("Observaciones: " + factura.getObservaciones()).setMarginTop(8));
                }
                
                // Se pasa el objeto pdf para poder generar el XObject del QR
                documento.add(pie(factura, fuentes, pdf).setMarginTop(14));
                
                documento.add(new Paragraph("Comprobante generado por sistema facturador.")
                        .setFontSize(7).setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER).setMarginTop(6));
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la factura " + id, e);
        }
        return salida.toByteArray();
    }

    // ---------------------------------------------------------------------
    // Bloques del comprobante
    // ---------------------------------------------------------------------

    private Table cabecera(FacturaVenta f, Fuentes fu) {
        Table tabla = new Table(UnitValue.createPercentArray(new float[]{45, 10, 45})).useAllAvailableWidth();

        Cell emisor = celdaConBorde();
        emisor.add(new Paragraph(EMISOR_RAZON_SOCIAL).setFont(fu.negrita()).setFontSize(14));
        emisor.add(new Paragraph("Domicilio comercial: " + EMISOR_DOMICILIO));
        emisor.add(new Paragraph("Condición frente al IVA: " + EMISOR_CONDICION_IVA));

        // La letra y el código salen de la factura. La X es un comprobante interno, sin código de ARCA.
        boolean esFiscal = f.getLetra().esFiscal();
        String letraDoc = f.getLetra().name();
        String codDoc = esFiscal ? String.format("COD. %03d", f.getTipoComprobante()) : "NO FISCAL";

        Cell letra = celdaConBorde().setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        letra.add(new Paragraph(letraDoc).setFont(fu.negrita()).setFontSize(30));
        letra.add(new Paragraph(codDoc).setFontSize(7));

        String numero = f.tieneNumero() ? f.getNroFactura() : "SIN NÚMERO (borrador)";
        Cell comprobante = celdaConBorde();
        comprobante.add(new Paragraph("FACTURA").setFont(fu.negrita()).setFontSize(16));
        if (!esFiscal) {
            comprobante.add(new Paragraph("Documento no válido como factura").setFontSize(8));
        }
        
        // Punto de venta dinámico si existe
        int ptoVta = f.getPuntoVenta() != null ? f.getPuntoVenta().getNumero() : 1;
        comprobante.add(new Paragraph("Punto de venta: " + String.format("%04d", ptoVta)));
        comprobante.add(new Paragraph("Nº: " + numero).setFont(fu.negrita()));
        comprobante.add(new Paragraph("Fecha de emisión: "
                + fecha(f.getFechaEmision() != null ? f.getFechaEmision() : f.getFecha())));
        comprobante.add(new Paragraph("CUIT: " + EMISOR_CUIT));
        comprobante.add(new Paragraph("Inicio de actividades: " + EMISOR_INICIO_ACTIVIDADES));
        if (f.getEstado() == EstadoFactura.ANULADA) {
            comprobante.add(new Paragraph("*** COMPROBANTE ANULADO ***")
                    .setFont(fu.negrita()).setFontColor(ColorConstants.RED));
        }

        tabla.addCell(emisor).addCell(letra).addCell(comprobante);
        return tabla;
    }

    private Table datosCliente(FacturaVenta f, Fuentes fu) {
        Table tabla = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();

        Cell izquierda = celdaConBorde();
        izquierda.add(new Paragraph().add("Cliente: ").add(new Text(
                f.getCliente().getNombre()).setFont(fu.negrita())));
        
        // Mostrar número de documento real enviado a AFIP
        String tipoDocTexto = f.getDocTipo() != null && f.getDocTipo() == 96 ? "DNI" : "CUIT";
        if (f.getDocTipo() != null && f.getDocTipo() == 99) tipoDocTexto = "Consumidor Final";
        String nroDocTexto = f.getDocNro() != null && f.getDocNro() > 0 ? String.valueOf(f.getDocNro()) : "0";
        
        izquierda.add(new Paragraph(tipoDocTexto + ": " + nroDocTexto));

        Cell derecha = celdaConBorde();
        derecha.add(new Paragraph("Condición de IVA: " + f.getCondicionFiscalReceptor().getDescripcion()));
        derecha.add(new Paragraph("Moneda: " + f.getTipoMoneda().getCodigo()));

        tabla.addCell(izquierda).addCell(derecha);
        return tabla;
    }

    private Table detalle(FacturaVenta f, Fuentes fu, NumberFormat dinero) {
        Table tabla = new Table(UnitValue.createPercentArray(new float[]{10, 46, 12, 16, 16}))
                .useAllAvailableWidth();

        for (String titulo : new String[]{"Código", "Descripción", "Cant.", "Precio unit.", "Subtotal"}) {
            boolean numerica = !titulo.equals("Código") && !titulo.equals("Descripción");
            tabla.addHeaderCell(new Cell()
                    .add(new Paragraph(titulo).setFont(fu.negrita()))
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setTextAlignment(numerica ? TextAlignment.RIGHT : TextAlignment.LEFT));
        }

        for (FacturaVentaDetalle d : f.getDetalles()) {
            tabla.addCell(celdaDetalle(valorOGuion(d.getCodigoArticulo()), TextAlignment.LEFT));
            // Utilizamos la descripción inmutable guardada en el detalle
            String desc = d.getDescripcion() != null ? d.getDescripcion() : "Artículo"; 
            tabla.addCell(celdaDetalle(desc, TextAlignment.LEFT));
            tabla.addCell(celdaDetalle(d.getCantidad().stripTrailingZeros().toPlainString(), TextAlignment.RIGHT));
            tabla.addCell(celdaDetalle(dinero.format(d.getPrecioUnitario()), TextAlignment.RIGHT));
            tabla.addCell(celdaDetalle(dinero.format(d.getSubtotal()), TextAlignment.RIGHT));
        }
        return tabla;
    }

    private Table totales(FacturaVenta f, Fuentes fu, NumberFormat dinero) {
        Table tabla = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .setWidth(UnitValue.createPercentValue(50))
                .setHorizontalAlignment(HorizontalAlignment.RIGHT);

        String moneda = f.getTipoMoneda().getCodigo();
        filaTotal(tabla, "Importe neto gravado", moneda + " " + dinero.format(f.getImporteNeto()), null);
        filaTotal(tabla, "IVA", moneda + " " + dinero.format(f.getImporteIva()), null);
        filaTotal(tabla, "IMPORTE TOTAL", moneda + " " + dinero.format(f.getImporteTotal()), fu.negrita());
        return tabla;
    }

    /** Pie del comprobante: QR, CAE y fecha de vencimiento. */
    private Table pie(FacturaVenta f, Fuentes fu, PdfDocument pdfDocument) {
        Table tabla = new Table(UnitValue.createPercentArray(new float[]{30, 70})).useAllAvailableWidth();

        // Celda Izquierda: Código QR
        Cell qrCell = celdaConBorde().setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (f.tieneCae()) {
            String urlAfip = generarUrlQrAfip(f);
            BarcodeQRCode qr = new BarcodeQRCode(urlAfip);
            PdfFormXObject qrFormObject = qr.createFormXObject(ColorConstants.BLACK, pdfDocument);
            Image qrImage = new Image(qrFormObject).setWidth(90).setHeight(90);
            qrCell.add(qrImage);
        } else {
            qrCell.add(new Paragraph("Sin QR").setFontColor(ColorConstants.GRAY));
        }
        tabla.addCell(qrCell);

        // Celda Derecha: Datos de Autorización
        Cell datosAutorizacion = celdaConBorde().setVerticalAlignment(VerticalAlignment.MIDDLE);
        
        if (f.tieneCae()) {
            // Se utiliza CAE en lugar de CAI por ser comprobante electrónico
            datosAutorizacion.add(new Paragraph("Comprobante Autorizado por ARCA").setFont(fu.negrita()).setFontSize(12));
            datosAutorizacion.add(new Paragraph("CAE Nº: " + valorOGuion(f.getCae())).setFont(fu.negrita()));
            datosAutorizacion.add(new Paragraph("Fecha de Vto. de CAE: " + fecha(f.getFechaVtoCae())).setFont(fu.negrita()));
        } else if (!f.getLetra().esFiscal()) {
            datosAutorizacion.add(new Paragraph("Comprobante interno (X). No se informa a ARCA.")
                    .setFontSize(10).setFontColor(ColorConstants.GRAY));
        } else {
            datosAutorizacion.add(new Paragraph("Comprobante todavía no informado a ARCA (Borrador).")
                    .setFontSize(10).setFontColor(ColorConstants.GRAY));
        }
        
        tabla.addCell(datosAutorizacion);
        return tabla;
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    private String generarUrlQrAfip(FacturaVenta f) {
        // Formato flat JSON exigido por AFIP
        String cuitEmisorLimpio = EMISOR_CUIT.replace("-", "");
        int ptoVta = f.getPuntoVenta() != null ? f.getPuntoVenta().getNumero() : 1;
        int tipoCmp = f.getTipoComprobante() != null ? f.getTipoComprobante() : 6;
        long nroCmp = f.getNumeroComprobante() != null ? f.getNumeroComprobante() : 0;
        LocalDate emision = f.getFechaEmision() != null ? f.getFechaEmision() : f.getFecha();
        String fechaEmision = emision != null ? emision.toString() : LocalDate.now().toString();
        
        String json = String.format(
            "{\"ver\":1,\"fecha\":\"%s\",\"cuit\":%s,\"ptoVta\":%d,\"tipoCmp\":%d,\"nroCmp\":%d,\"importe\":%s,\"moneda\":\"%s\",\"ctz\":1,\"tipoDocRec\":%d,\"nroDocRec\":%d,\"tipoCodAut\":\"E\",\"codAut\":%s}",
            fechaEmision,
            cuitEmisorLimpio,
            ptoVta,
            tipoCmp,
            nroCmp,
            f.getImporteTotal().toPlainString(),
            "PES",
            f.getDocTipo() != null ? f.getDocTipo() : 99,
            f.getDocNro() != null ? f.getDocNro() : 0,
            f.getCae() != null ? f.getCae() : "0"
        );
        
        String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return "https://www.afip.gob.ar/fe/qr/?p=" + base64;
    }

    private Cell celdaConBorde() {
        return new Cell().setBorder(new SolidBorder(1)).setPadding(8);
    }

    private Cell celdaDetalle(String texto, TextAlignment alineacion) {
        return new Cell().add(new Paragraph(texto)).setTextAlignment(alineacion)
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f));
    }

    private void filaTotal(Table tabla, String etiqueta, String valor, PdfFont fuente) {
        Paragraph pEtiqueta = new Paragraph(etiqueta);
        Paragraph pValor = new Paragraph(valor).setTextAlignment(TextAlignment.RIGHT);
        if (fuente != null) {
            pEtiqueta.setFont(fuente).setFontSize(11);
            pValor.setFont(fuente).setFontSize(11);
        }
        tabla.addCell(new Cell().add(pEtiqueta).setBorder(Border.NO_BORDER));
        tabla.addCell(new Cell().add(pValor).setBorder(Border.NO_BORDER));
    }

    private String fecha(LocalDate fecha) {
        return fecha == null ? "—" : fecha.format(FORMATO_FECHA);
    }

    private String valorOGuion(String valor) {
        return valor == null || valor.isBlank() ? "—" : valor;
    }
}