package com.example.facturacion.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.facturacion.model.CondicionFiscal;
import com.example.facturacion.model.LetraComprobante;

/**
 * Decide qué letra de comprobante corresponde según la condición frente al IVA de emisor y receptor.
 *
 *   Emisor Responsable Inscripto -> receptor Resp. Inscripto o Monotributo: A
 *                                -> receptor Consumidor Final o Exento:     B
 *   Emisor Monotributo o Exento  -> siempre C
 *   X (interna, no fiscal)       -> nunca se sugiere: la elige el usuario
 *
 * La condición del emisor sale de application.properties (afip.condicion-iva-emisor).
 */
@Component
public class LetraComprobanteResolver {

    private final CondicionFiscal emisor;

    public LetraComprobanteResolver(
            @Value("${afip.condicion-iva-emisor:RESPONSABLE_INSCRIPTO}") CondicionFiscal emisor) {
        if (emisor == CondicionFiscal.CONSUMIDOR_FINAL) {
            throw new IllegalStateException("afip.condicion-iva-emisor no puede ser CONSUMIDOR_FINAL");
        }
        this.emisor = emisor;
    }

    public CondicionFiscal emisor() {
        return emisor;
    }

    /** Solo un Responsable Inscripto discrimina IVA. Monotributo y Exento facturan el total sin IVA. */
    public boolean discriminaIva() {
        return emisor == CondicionFiscal.RESPONSABLE_INSCRIPTO;
    }

    /** Letra que corresponde por defecto para ese receptor. */
    public LetraComprobante sugerir(CondicionFiscal receptor) {
        if (!discriminaIva()) {
            return LetraComprobante.C;
        }
        boolean receptorRecibeA = receptor == CondicionFiscal.RESPONSABLE_INSCRIPTO
                || receptor == CondicionFiscal.MONOTRIBUTO;
        return receptorRecibeA ? LetraComprobante.A : LetraComprobante.B;
    }

    /** Letras que este emisor puede usar (la sugerida es editable, pero dentro de estas). */
    public List<LetraComprobante> permitidas() {
        return discriminaIva()
                ? List.of(LetraComprobante.A, LetraComprobante.B, LetraComprobante.X)
                : List.of(LetraComprobante.C, LetraComprobante.X);
    }

    /**
     * Valida la letra elegida por el usuario.
     * @param receptorIdentificado true si el cliente tiene CUIT
     * @throws IllegalArgumentException (HTTP 400) si la combinación no es válida
     */
    public void validar(LetraComprobante letra, boolean receptorIdentificado) {
        if (!permitidas().contains(letra)) {
            throw new IllegalArgumentException("Un emisor " + emisor.getDescripcion()
                    + " no puede emitir comprobantes " + letra + ". Letras permitidas: " + permitidas());
        }
        if (letra == LetraComprobante.A && !receptorIdentificado) {
            throw new IllegalArgumentException("La Factura A exige un cliente identificado con CUIT.");
        }
    }
}
