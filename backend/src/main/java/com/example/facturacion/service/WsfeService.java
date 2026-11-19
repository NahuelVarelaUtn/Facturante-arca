package com.example.facturacion.service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


@Service
public class WsfeService {

    private final WsaaService wsaaService;

    @Value("${afip.cuit}")
    private Long cuit;

    @Value("${afip.wsfe.url}")
    private String wsfeUrl;

    public WsfeService(WsaaService wsaaService) {
        this.wsaaService = wsaaService;
    }

    /**
     * Consulta el último número de comprobante autorizado en AFIP para un punto de venta y tipo de factura.
     * Tipo 6 = Factura B (por defecto para pruebas), Tipo 1 = Factura A.
     */
    public long obtenerUltimoComprobante(int puntoVenta, int tipoCbte) {
        try {
            // 1. Obtener Token y Sign vigentes del WSAA
            String[] tokenSign = wsaaService.obtenerTokenYSign("wsfe");
            String token = tokenSign[0];
            String sign = tokenSign[1];

            // 2. Armar sobre SOAP para FECompUltimoAutorizado
            String soapEnvelope = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ar=\"http://ar.gov.afip.dif.FEV1/\">" +
                    "<soapenv:Header/>" +
                    "<soapenv:Body>" +
                    "<ar:FECompUltimoAutorizado>" +
                    "<ar:Auth>" +
                    "<ar:Token>" + token + "</ar:Token>" +
                    "<ar:Sign>" + sign + "</ar:Sign>" +
                    "<ar:Cuit>" + cuit + "</ar:Cuit>" +
                    "</ar:Auth>" +
                    "<ar:PtoVta>" + puntoVenta + "</ar:PtoVta>" +
                    "<ar:CbteTipo>" + tipoCbte + "</ar:CbteTipo>" +
                    "</ar:FECompUltimoAutorizado>" +
                    "</soapenv:Body>" +
                    "</soapenv:Envelope>";

            // Se debe especificar el SOAPAction para WSFE
            String xmlRespuesta = enviarSoapWsfe(soapEnvelope, "http://ar.gov.afip.dif.FEV1/FECompUltimoAutorizado");

            // 3. Parsear respuesta XML para extraer el número
            return extraerNumeroDeRespuesta(xmlRespuesta, "CbteNro");

        } catch (Exception e) {
            throw new RuntimeException("Error al consultar el último comprobante en AFIP: " + e.getMessage(), e);
        }
    }

    /**
     * Solicita a ARCA la autorización (CAE) de un comprobante.
     * El número NO lo decide el sistema: se le pregunta a ARCA cuál fue el último autorizado
     * para ese punto de venta y tipo, y se pide el siguiente.
     *
     * @throws RuntimeException si ARCA rechaza el comprobante o no se puede conectar
     */
    public ResultadoCae autorizar(SolicitudCae s) {
        try {
            // 1. Token y Sign vigentes del WSAA
            String[] tokenSign = wsaaService.obtenerTokenYSign("wsfe");
            String token = tokenSign[0];
            String sign = tokenSign[1];

            // 2. Próximo número de comprobante según ARCA
            long nroCbte = obtenerUltimoComprobante(s.puntoVenta(), s.tipoComprobante()) + 1;
            String fechaCbte = s.fechaComprobante().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd

            // 3. Sobre SOAP para FECAESolicitar
            StringBuilder xml = new StringBuilder()
                    .append("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ar=\"http://ar.gov.afip.dif.FEV1/\">")
                    .append("<soapenv:Header/>")
                    .append("<soapenv:Body>")
                    .append("<ar:FECAESolicitar>")
                    .append("<ar:Auth>")
                    .append("<ar:Token>").append(token).append("</ar:Token>")
                    .append("<ar:Sign>").append(sign).append("</ar:Sign>")
                    .append("<ar:Cuit>").append(cuit).append("</ar:Cuit>")
                    .append("</ar:Auth>")
                    .append("<ar:FeCAEReq>")
                    .append("<ar:FeCabReq>")
                    .append("<ar:CantReg>1</ar:CantReg>")
                    .append("<ar:PtoVta>").append(s.puntoVenta()).append("</ar:PtoVta>")
                    .append("<ar:CbteTipo>").append(s.tipoComprobante()).append("</ar:CbteTipo>")
                    .append("</ar:FeCabReq>")
                    .append("<ar:FeDetReq>")
                    .append("<ar:FECAEDetRequest>")
                    .append("<ar:Concepto>1</ar:Concepto>") // 1 = Productos
                    .append("<ar:DocTipo>").append(s.docTipo()).append("</ar:DocTipo>")
                    .append("<ar:DocNro>").append(s.docNro()).append("</ar:DocNro>")
                    .append("<ar:CbteDesde>").append(nroCbte).append("</ar:CbteDesde>")
                    .append("<ar:CbteHasta>").append(nroCbte).append("</ar:CbteHasta>")
                    .append("<ar:CbteFch>").append(fechaCbte).append("</ar:CbteFch>")
                    .append("<ar:ImpTotal>").append(s.importeTotal().toPlainString()).append("</ar:ImpTotal>")
                    .append("<ar:ImpTotConc>0</ar:ImpTotConc>")
                    .append("<ar:ImpNeto>").append(s.importeNeto().toPlainString()).append("</ar:ImpNeto>")
                    .append("<ar:ImpOpEx>0</ar:ImpOpEx>")
                    .append("<ar:ImpTrib>0</ar:ImpTrib>")
                    .append("<ar:ImpIVA>").append(s.importeIva().toPlainString()).append("</ar:ImpIVA>")
                    .append("<ar:MonId>PES</ar:MonId>")
                    .append("<ar:MonCotiz>1</ar:MonCotiz>")
                    // RG 5616: la condición frente al IVA del receptor es obligatoria
                    .append("<ar:CondicionIVAReceptorId>").append(s.condicionIvaReceptor())
                    .append("</ar:CondicionIVAReceptorId>");

            // Un bloque <AlicIva> por cada alícuota de la factura. En Factura C no se informa IVA.
            if (!s.alicuotas().isEmpty()) {
                xml.append("<ar:Iva>");
                for (SolicitudCae.Alicuota a : s.alicuotas()) {
                    xml.append("<ar:AlicIva>")
                            .append("<ar:Id>").append(idAlicuotaArca(a.porcentaje())).append("</ar:Id>")
                            .append("<ar:BaseImp>").append(a.baseImponible().toPlainString()).append("</ar:BaseImp>")
                            .append("<ar:Importe>").append(a.importe().toPlainString()).append("</ar:Importe>")
                            .append("</ar:AlicIva>");
                }
                xml.append("</ar:Iva>");
            }

            xml.append("</ar:FECAEDetRequest>")
                    .append("</ar:FeDetReq>")
                    .append("</ar:FeCAEReq>")
                    .append("</ar:FECAESolicitar>")
                    .append("</soapenv:Body>")
                    .append("</soapenv:Envelope>");

            String xmlRespuesta = enviarSoapWsfe(xml.toString(), "http://ar.gov.afip.dif.FEV1/FECAESolicitar");

            // 4. CAE y vencimiento
            return extraerCaeDeRespuesta(xmlRespuesta, nroCbte);

        } catch (Exception e) {
            throw new RuntimeException("Error al autorizar el comprobante en ARCA: " + e.getMessage(), e);
        }
    }

    /** Código que usa ARCA para cada alícuota de IVA (tabla de FEParamGetTiposIva). */
    static int idAlicuotaArca(BigDecimal porcentaje) {
        String clave = porcentaje.stripTrailingZeros().toPlainString();
        return switch (clave) {
            case "0" -> 3;
            case "10.5" -> 4;
            case "21" -> 5;
            case "27" -> 6;
            case "5" -> 8;
            case "2.5" -> 9;
            default -> throw new IllegalArgumentException("Alícuota de IVA no reconocida por ARCA: " + clave + "%");
        };
    }

    // Se añadió el parámetro 'soapAction' requerido por AFIP
    private String enviarSoapWsfe(String soapEnvelope, String soapAction) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/xml;charset=UTF-8"));
        headers.setAccept(Collections.singletonList(MediaType.ALL));
        // AFIP WSFE requiere especificar qué método se está invocando en la cabecera
        headers.set("SOAPAction", soapAction);

        HttpEntity<String> entity = new HttpEntity<>(soapEnvelope, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(wsfeUrl, entity, String.class);
        return response.getBody();
    }

    private long extraerNumeroDeRespuesta(String xmlRespuesta, String tagName) throws Exception {
        Document doc = parsear(xmlRespuesta);
        String valor = primerTexto(doc, tagName);
        if (valor == null) {
            // Sin el dato NO se puede asumir "todavía no hay comprobantes": se pediría un número equivocado
            throw new RuntimeException("ARCA no informó el último comprobante. " + mensajes(doc));
        }
        return Long.parseLong(valor.trim());
    }

    private ResultadoCae extraerCaeDeRespuesta(String xmlRespuesta, long nroCbte) throws Exception {
        Document doc = parsear(xmlRespuesta);

        // "A" = aprobado, "R" = rechazado. En un rechazo ARCA igual responde 200 con el tag <CAE> vacío,
        // por eso NO alcanza con mirar si existe el tag: hay que leer <Resultado>.
        String resultado = primerTexto(doc, "Resultado");
        String cae = primerTexto(doc, "CAE");
        if (!"A".equals(resultado) || cae == null || cae.isBlank()) {
            throw new RuntimeException("Rechazado por ARCA. " + mensajes(doc));
        }
        return new ResultadoCae(nroCbte, cae.trim(), primerTexto(doc, "CAEFchVto"));
    }

    private Document parsear(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // Seguridad: no se aceptan DOCTYPE ni entidades externas en la respuesta (ataques XXE)
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(new StringReader(xml)));
    }

    /** Texto del primer elemento con ese nombre, o null si no está o viene vacío. */
    private String primerTexto(Document doc, String tag) {
        NodeList nodos = doc.getElementsByTagName(tag);
        if (nodos.getLength() == 0) return null;
        String texto = nodos.item(0).getTextContent();
        return (texto == null || texto.isBlank()) ? null : texto;
    }

    /** Junta los "Code: Msg" de errores y observaciones que devuelve ARCA, para mostrárselos al usuario. */
    private String mensajes(Document doc) {
        NodeList codigos = doc.getElementsByTagName("Code");
        NodeList textos = doc.getElementsByTagName("Msg");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < textos.getLength(); i++) {
            if (sb.length() > 0) sb.append(" | ");
            if (i < codigos.getLength()) sb.append("(").append(codigos.item(i).getTextContent()).append(") ");
            sb.append(textos.item(i).getTextContent());
        }
        return sb.length() == 0 ? "ARCA no informó el motivo." : sb.toString();
    }

    // Clase auxiliar interna para retornar el resultado de la autorización
    public static class ResultadoCae {
        private final long numeroComprobante;
        private final String cae; // Cambiado a String para mantener los ceros a la izquierda o evitar desbordamiento de Long
        private final String fechaVencimiento;

        public ResultadoCae(long numeroComprobante, String cae, String fechaVencimiento) {
            this.numeroComprobante = numeroComprobante;
            this.cae = cae;
            this.fechaVencimiento = fechaVencimiento;
        }

        public long getNumeroComprobante() { return numeroComprobante; }
        public String getCae() { return cae; } // Cambiado a String
        public String getFechaVencimiento() { return fechaVencimiento; }
    }
}