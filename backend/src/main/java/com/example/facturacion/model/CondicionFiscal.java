package com.example.facturacion.model;

/**
 * Condición frente al IVA de un cliente (receptor del comprobante).
 * "codigoArca" es el valor que espera WSFE en CondicionIVAReceptorId.
 *
 * Ojo: NO es lo mismo que la alícuota (21%, 10,5%...). La alícuota es del artículo.
 */
public enum CondicionFiscal {

    RESPONSABLE_INSCRIPTO(1, "Responsable Inscripto"),
    EXENTO(4, "IVA Sujeto Exento"),
    CONSUMIDOR_FINAL(5, "Consumidor Final"),
    MONOTRIBUTO(6, "Responsable Monotributo");

    private final int codigoArca;
    private final String descripcion;

    CondicionFiscal(int codigoArca, String descripcion) {
        this.codigoArca = codigoArca;
        this.descripcion = descripcion;
    }

    public int getCodigoArca() { return codigoArca; }
    public String getDescripcion() { return descripcion; }
}
