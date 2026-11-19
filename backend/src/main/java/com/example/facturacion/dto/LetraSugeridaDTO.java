package com.example.facturacion.dto;

import java.util.List;

/** Respuesta de GET /api/facturas/letra: letra que corresponde y cuáles puede elegir el usuario. */
public record LetraSugeridaDTO(String letra,
                               List<String> letrasPermitidas,
                               String condicionIvaEmisor,
                               boolean discriminaIva) {
}
