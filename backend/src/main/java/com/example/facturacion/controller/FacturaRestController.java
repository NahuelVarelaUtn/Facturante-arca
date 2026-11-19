package com.example.facturacion.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.facturacion.dto.FacturaReporteDTO;
import com.example.facturacion.dto.FacturaRequestDTO;
import com.example.facturacion.dto.LetraSugeridaDTO;
import com.example.facturacion.model.CondicionFiscal;
import com.example.facturacion.model.EstadoFactura;
import com.example.facturacion.service.FacturaPdfService;
import com.example.facturacion.service.FacturaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/facturas")
public class FacturaRestController {

    /** Más nuevas primero; el ID desempata facturas de la misma fecha. */
    private static final Sort ORDEN_LISTADO = Sort.by(Sort.Direction.DESC, "fecha", "id");

    private final FacturaService facturaService;
    private final FacturaPdfService pdfService;

    public FacturaRestController(FacturaService facturaService, FacturaPdfService pdfService) {
        this.facturaService = facturaService;
        this.pdfService = pdfService;
    }

    /**
     * GET /api/facturas?nroFactura=&fechaDesde=&fechaHasta=&clienteId=&informadaArca=&estado=&page=0&size=10
     * Paginado en el servidor: "page" arranca en 0 y "size" se recorta a 100 (application.properties).
     * Respuesta: { content: [...], page: { size, number, totalElements, totalPages } }.
     */
    @GetMapping
    public PagedModel<FacturaReporteDTO> listar(
            @RequestParam(required = false) String nroFactura,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Boolean informadaArca,
            @RequestParam(required = false) EstadoFactura estado,
            @PageableDefault(size = 10) Pageable pageable) {
        // El orden lo fija el servidor (fecha e ID descendentes). Se ignora un "sort" que mande el cliente:
        // un orden estable evita filas repetidas o salteadas entre páginas, y un campo inexistente daría error.
        Pageable paginaOrdenada = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDEN_LISTADO);
        Page<FacturaReporteDTO> pagina = facturaService.buscarFacturas(
                nroFactura, fechaDesde, fechaHasta, clienteId, informadaArca, estado, paginaOrdenada);
        return new PagedModel<>(pagina);
    }

    /** GET /api/facturas/{id} -> la factura completa, con renglones (para verla o editarla). */
    @GetMapping("/{id}")
    public FacturaReporteDTO obtener(@PathVariable Long id) {
        return facturaService.obtener(id);
    }

    /** GET /api/facturas/letra?condicionIva=MONOTRIBUTO -> letra que corresponde y letras permitidas. */
    @GetMapping("/letra")
    public LetraSugeridaDTO letra(@RequestParam CondicionFiscal condicionIva) {
        return facturaService.sugerirLetra(condicionIva);
    }

    /** POST /api/facturas -> "Guardar borrador". No se comunica con ARCA. */
    @PostMapping
    public ResponseEntity<FacturaReporteDTO> crear(@Valid @RequestBody FacturaRequestDTO request) {
        FacturaReporteDTO creada = facturaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /**
     * PUT /api/facturas/{id} -> actualiza un borrador o una Factura X. 409 si tiene CAE o está anulada.
     * Cambiarle la letra a una X emitida por A/B/C la convierte en borrador fiscal (ver FacturaService).
     */
    @PutMapping("/{id}")
    public ResponseEntity<FacturaReporteDTO> actualizar(@PathVariable Long id,
                                                        @Valid @RequestBody FacturaRequestDTO request) {
        return ResponseEntity.ok(facturaService.actualizar(id, request));
    }

    /**
     * POST /api/facturas/{id}/emitir -> "Facturar": pide el CAE a ARCA (o numera, si es Factura X).
     * 409 si no es borrador; 502 si ARCA la rechaza (el borrador queda como estaba).
     */
    @PostMapping("/{id}/emitir")
    public ResponseEntity<FacturaReporteDTO> emitir(@PathVariable Long id) {
        return ResponseEntity.ok(facturaService.emitir(id));
    }

    @PutMapping("/{id}/anular")
    public ResponseEntity<FacturaReporteDTO> anular(@PathVariable Long id) {
        return ResponseEntity.ok(facturaService.anular(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        facturaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] contenido = pdfService.generar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("factura-" + id + ".pdf").build().toString())
                .body(contenido);
    }

    // --- ENDPOINT PARA EL DASHBOARD DE ARCA ---
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> obtenerMetricasDashboard() {
        LocalDate hoy = LocalDate.now();
        int mesActual = hoy.getMonthValue();
        int anioActual = hoy.getYear();
        
        // Llamadas a la base de datos a través del servicio
        java.math.BigDecimal totalMes = facturaService.calcularTotalFacturadoPorMes(mesActual, anioActual);
        long cantidadMes = facturaService.contarComprobantesPorMes(mesActual, anioActual);
        
        DashboardResponse respuesta = new DashboardResponse(mesActual, anioActual, totalMes, cantidadMes);
        return ResponseEntity.ok(respuesta);
    }

    // Clase auxiliar interna para evitar usar Map<> y que VS Code tire error de sintaxis
    public static class DashboardResponse {
        public int mes;
        public int anio;
        public java.math.BigDecimal totalFacturado;
        public long cantidadComprobantes;

        public DashboardResponse(int mes, int anio, java.math.BigDecimal totalFacturado, long cantidadComprobantes) {
            this.mes = mes;
            this.anio = anio;
            this.totalFacturado = totalFacturado;
            this.cantidadComprobantes = cantidadComprobantes;
        }
    }
}