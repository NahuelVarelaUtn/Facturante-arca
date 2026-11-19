package com.example.facturacion.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.example.facturacion.util.Paginacion;

/**
 * Respuesta paginada de los explorers (clientes y artículos).
 * "page" arranca en 1, igual que el query param ?page=1.
 */
public record PageResponse<T>(List<T> content,
                              int page,
                              int limit,
                              long totalElements,
                              int totalPages,
                              boolean hasNext) {

    public static <T> PageResponse<T> de(Page<T> pagina, Paginacion paginacion) {
        return new PageResponse<>(pagina.getContent(), paginacion.page(), paginacion.limit(),
                pagina.getTotalElements(), pagina.getTotalPages(), pagina.hasNext());
    }

}
