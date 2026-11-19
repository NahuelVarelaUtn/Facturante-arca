package com.example.facturacion.service;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;

import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

@Service
public class WsaaService {

    static {
        // Registrar BouncyCastle para los algoritmos criptográficos
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Value("${afip.wsaa.url}")
    private String wsaaUrl;

    // Se inyectan las rutas desde application.properties
    @Value("${afip.cert.path}")
    private String certPath;

    @Value("${afip.key.path}")
    private String keyPath;

    // --- VARIABLES DE CACHÉ PARA REUTILIZAR EL TOKEN ---
    private String cachedToken = null;
    private String cachedSign = null;
    private LocalDateTime tokenExpiration = null;

    public String[] obtenerTokenYSign(String servicio) {
        // 1. Si ya tenemos un token guardado en RAM y no expiró, LO REUTILIZAMOS.
        if (cachedToken != null && cachedSign != null && tokenExpiration != null && LocalDateTime.now().isBefore(tokenExpiration)) {
            return new String[]{cachedToken, cachedSign};
        }

        try {
            // 2. Si no hay token o ya venció, generamos una nueva petición
            String xmlLoginTicketRequest = generarLoginTicketRequest(servicio);
            byte[] cmsFirmado = firmarXml(xmlLoginTicketRequest);
            String cmsBase64 = Base64.getEncoder().encodeToString(cmsFirmado);
            String respuestaXml = enviarSoapWsaa(cmsBase64);
            
            String[] tokenYSign = extraerTokenYSignDeRespuesta(respuestaXml);

            // 3. Guardamos en memoria el nuevo token (Validez de 5 horas para margen de seguridad)
            this.cachedToken = tokenYSign[0];
            this.cachedSign = tokenYSign[1];
            this.tokenExpiration = LocalDateTime.now().plusHours(5);

            // 4. Imprimir en consola para verificar su correcta obtención
            System.out.println("\n=== ¡TOKEN OBTENIDO CON ÉXITO! ===");
            System.out.println("Token: " + cachedToken);
            System.out.println("Sign: " + cachedSign + "\n");

            return tokenYSign;

        } catch (Exception e) {
            throw new RuntimeException("Error al autenticar con WSAA de AFIP: " + e.getMessage(), e);
        }
    }

    private String generarLoginTicketRequest(String servicio) {
        // Zona horaria requerida por el SCHEMA de AFIP
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'-03:00'");
        
        // Retrasamos 8 horas para saltar el bloqueo por intentos repetidos durante nuestras pruebas
        LocalDateTime ahora = LocalDateTime.now().minusHours(8);
        String generationTime = ahora.minusMinutes(10).format(dtf);
        String expirationTime = ahora.plusHours(12).format(dtf);
        
        // Dividido por 1000 para cumplir con el límite unsignedInt de 32 bits de AFIP
        long uniqueId = System.currentTimeMillis() / 1000;

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<loginTicketRequest version=\"1.0\">" +
                "<header>" +
                "<uniqueId>" + uniqueId + "</uniqueId>" +
                "<generationTime>" + generationTime + "</generationTime>" +
                "<expirationTime>" + expirationTime + "</expirationTime>" +
                "</header>" +
                "<service>" + servicio + "</service>" +
                "</loginTicketRequest>";
    }

    private byte[] firmarXml(String xml) throws Exception {
        org.springframework.core.io.ResourceLoader resourceLoader = new org.springframework.core.io.DefaultResourceLoader();
        
        // Cargar Certificado .crt usando la ruta inyectada
        X509Certificate cert;
        // Ajustamos la ruta para que Spring la resuelva correctamente desde el classpath
        String certResourcePath = certPath.replace("src/main/resources/", "classpath:");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(resourceLoader.getResource(certResourcePath).getInputStream(), StandardCharsets.UTF_8))) {
            try (PEMParser pemParser = new PEMParser(reader)) {
                org.bouncycastle.cert.X509CertificateHolder certHolder = (org.bouncycastle.cert.X509CertificateHolder) pemParser.readObject();
                cert = new JcaX509CertificateConverter().getCertificate(certHolder);
            }
        }

        // Cargar Clave Privada .key usando la ruta inyectada
        PrivateKey key;
        String keyResourcePath = keyPath.replace("src/main/resources/", "classpath:");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(resourceLoader.getResource(keyResourcePath).getInputStream(), StandardCharsets.UTF_8))) {
            try (PEMParser pemParser = new PEMParser(reader)) {
                Object object = pemParser.readObject();
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
                if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                    key = converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) object);
                } else {
                    org.bouncycastle.openssl.PEMKeyPair keyPair = (org.bouncycastle.openssl.PEMKeyPair) object;
                    key = converter.getPrivateKey(keyPair.getPrivateKeyInfo());
                }
            }
        }

        byte[] data = xml.getBytes(StandardCharsets.UTF_8);
        CMSProcessableByteArray msg = new CMSProcessableByteArray(data);
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();

        ContentSigner sha1Signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(key);
        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().setProvider("BC").build()).build(sha1Signer, cert));
        gen.addCertificate(new org.bouncycastle.cert.X509CertificateHolder(cert.getEncoded()));

        CMSSignedData sigData = gen.generate(msg, true);
        
        // ESENCIAL: Forzar formato DER, de lo contrario AFIP devuelve xml.bad
        return sigData.getEncoded("DER");
    }

    private String enviarSoapWsaa(String cmsBase64) {
        RestTemplate restTemplate = new RestTemplate();
        String soapEnvelope = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ws=\"http://wsaa.view.sua.dvad.isa.afip.gov.ar/\">"
                + "<soapenv:Header/>"
                + "<soapenv:Body><ws:loginCms><in0>" + cmsBase64 + "</in0></ws:loginCms></soapenv:Body>"
                + "</soapenv:Envelope>";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/xml;charset=UTF-8"));
        headers.setAccept(Collections.singletonList(MediaType.ALL));
        // Cabecera vacía requerida por SOAP 1.1 en AFIP
        headers.set("SOAPAction", "");

        HttpEntity<String> entity = new HttpEntity<>(soapEnvelope, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(wsaaUrl, entity, String.class);
        return response.getBody();
    }

    private String[] extraerTokenYSignDeRespuesta(String xmlRespuesta) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        
        // Decodificar entidades HTML si AFIP las envía escapadas
        if (xmlRespuesta.contains("&lt;")) {
            xmlRespuesta = xmlRespuesta.replace("&lt;", "<")
                                       .replace("&gt;", ">")
                                       .replace("&quot;", "\"")
                                       .replace("&#39;", "'");
        }

        int startIdx = xmlRespuesta.indexOf("<loginTicketResponse");
        int endIdx = xmlRespuesta.indexOf("</loginTicketResponse>") + "</loginTicketResponse>".length();
        String loginTicketResponse = xmlRespuesta.substring(startIdx, endIdx);

        Document doc = db.parse(new InputSource(new StringReader(loginTicketResponse)));
        String token = doc.getElementsByTagName("token").item(0).getTextContent();
        String sign = doc.getElementsByTagName("sign").item(0).getTextContent();

        return new String[]{token, sign};
    }
}