package com.example.facturacion.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.facturacion.dto.ClienteDTO;
import com.example.facturacion.dto.PageResponse;
import com.example.facturacion.model.CondicionFiscal;
import com.example.facturacion.service.ClienteService;
import com.example.facturacion.service.FacturaService;
import com.example.facturacion.util.Paginacion;

/** Explorer de clientes. */
@RestController
@RequestMapping("/api/clientes")
public class ClienteRestController {

    private final ClienteService clienteService;
    private final FacturaService facturaService;

    public ClienteRestController(ClienteService clienteService, FacturaService facturaService) {
        this.clienteService = clienteService;
        this.facturaService = facturaService;
    }

    /**
     * GET /api/clientes?page=1&limit=100&search=andina&id=&cuit=&condicionIva=RESPONSABLE_INSCRIPTO
     * Sin ningún criterio lista todos los clientes paginados (más recientes primero).
     * Respuesta: { content, page, limit, totalElements, totalPages, hasNext }.
     */
    @GetMapping
    public PageResponse<ClienteDTO> buscar(@RequestParam(required = false) String search,
                                           @RequestParam(required = false) Long id,
                                           @RequestParam(required = false) String cuit,
                                           @RequestParam(required = false) CondicionFiscal condicionIva,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer limit) {
        return clienteService.buscar(search, id, cuit, condicionIva, Paginacion.de(page, limit));
    }

    /** GET /api/clientes/consumidor-final  (crea el cliente genérico si todavía no existe) */
    @GetMapping("/consumidor-final")
    public ClienteDTO consumidorFinal() {
        var c = facturaService.obtenerConsumidorFinal();
        return new ClienteDTO(c.getId(), c.getNombre(), c.getCuit(), c.getCondicionFiscal(),
                c.getDomicilio(), c.getLocalidad(), c.getProvincia());
    }
}
