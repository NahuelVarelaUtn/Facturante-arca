package com.example.facturacion.dto;

import com.example.facturacion.model.CondicionFiscal;
import com.example.facturacion.util.Cuit;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fila del explorer de clientes.
 * JSON: { id, nombre, cuit: "30-54668997-9", condicionIva: "RESPONSABLE_INSCRIPTO", condicionIvaDescripcion,
 *         domicilio, localidad, provincia }.
 */
public record ClienteDTO(Long id, String nombre, String cuit, CondicionFiscal condicionIva,
                         String domicilio, String localidad, String provincia) {

    /** El CUIT se guarda solo con dígitos; hacia afuera siempre sale con guiones. */
    public ClienteDTO {
        cuit = Cuit.formatear(cuit);
    }

    @JsonProperty("condicionIvaDescripcion")
    public String condicionIvaDescripcion() {
        return condicionIva == null ? null : condicionIva.getDescripcion();
    }
}
