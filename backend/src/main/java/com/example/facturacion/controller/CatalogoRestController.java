package com.example.facturacion.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.facturacion.dto.PuntoVentaDTO;
import com.example.facturacion.dto.TipoMonedaDTO;
import com.example.facturacion.service.CatalogoService;

/** Desplegables fijos del formulario (pocas filas). Los explorers están en Cliente/ArticuloRestController. */
@RestController
@RequestMapping("/api")
public class CatalogoRestController {

    private final CatalogoService catalogoService;

    public CatalogoRestController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    /** GET /api/puntos-venta  (alimenta el desplegable de punto de venta) */
    @GetMapping("/puntos-venta")
    public List<PuntoVentaDTO> puntosVenta() {
        return catalogoService.listarPuntosVenta();
    }

    /** GET /api/monedas  (alimenta el desplegable de moneda) */
    @GetMapping("/monedas")
    public List<TipoMonedaDTO> monedas() {
        return catalogoService.listarMonedas();
    }
}
