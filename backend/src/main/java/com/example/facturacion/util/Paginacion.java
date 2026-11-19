package com.example.facturacion.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Parámetros de paginación de la API: ?page=1&limit=100 (page arranca en 1).
 * El límite se recorta SIEMPRE en el servidor: el cliente nunca puede pedir más de {@link #LIMITE_MAXIMO}.
 */
public record Paginacion(int page, int limit) {

    public static final int LIMITE_MAXIMO = 100;

    /** Valores ausentes o fuera de rango se corrigen en vez de fallar. */
    public static Paginacion de(Integer page, Integer limit) {
        int p = (page == null || page < 1) ? 1 : page;
        int l = (limit == null || limit < 1) ? LIMITE_MAXIMO : Math.min(limit, LIMITE_MAXIMO);
        return new Paginacion(p, l);
    }

    /** Spring pagina desde 0; la API pública desde 1. */
    public Pageable toPageable() {
        return PageRequest.of(page - 1, limit);
    }
}
