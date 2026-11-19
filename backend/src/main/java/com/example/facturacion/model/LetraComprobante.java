package com.example.facturacion.model;

/**
 * Letra del comprobante. A, B y C son fiscales (se autorizan en ARCA y reciben CAE).
 * X es un comprobante interno, no fiscal: no se informa a ARCA y lo numera el sistema.
 */
public enum LetraComprobante {

    A(1),
    B(6),
    C(11),
    X(null);

    /** Código de tipo de comprobante de ARCA (CbteTipo) para FACTURA de esta letra. null = no fiscal. */
    private final Integer codigoArca;

    LetraComprobante(Integer codigoArca) {
        this.codigoArca = codigoArca;
    }

    public Integer getCodigoArca() { return codigoArca; }

    public boolean esFiscal() { return codigoArca != null; }
}
