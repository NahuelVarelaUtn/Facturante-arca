package com.example.facturacion.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.facturacion.dto.ArticuloPrecioDTO;
import com.example.facturacion.dto.ListaPrecioDTO;
import com.example.facturacion.dto.PageResponse;
import com.example.facturacion.service.ArticuloService;
import com.example.facturacion.util.Paginacion;

/** Explorer de artículos y selector de lista de precios. */
@RestController
@RequestMapping("/api")
public class ArticuloRestController {

    private final ArticuloService articuloService;

    public ArticuloRestController(ArticuloService articuloService) {
        this.articuloService = articuloService;
    }

    /**
     * GET /api/articulos?page=1&limit=100&search=mouse&codigo=MOU&id=&listaPrecioId=1
     * Sin search, codigo ni id lista todos los artículos paginados (más recientes primero).
     * Respuesta: { content, page, limit, totalElements, totalPages, hasNext }.
     */
    @GetMapping("/articulos")
    public PageResponse<ArticuloPrecioDTO> buscar(@RequestParam(required = false) String search,
                                                  @RequestParam(required = false) String codigo,
                                                  @RequestParam(required = false) Long id,
                                                  @RequestParam(required = false) Long listaPrecioId,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer limit) {
        return articuloService.buscar(search, codigo, id, listaPrecioId, Paginacion.de(page, limit));
    }

    /** GET /api/listas-precio  (MAYORISTA / MINORISTA) */
    @GetMapping("/listas-precio")
    public List<ListaPrecioDTO> listasPrecio() {
        return articuloService.listarListasPrecio();
    }
}
