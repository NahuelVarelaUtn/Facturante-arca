package com.example.facturacion.util;

/** Utilidades de CUIT. En la base se guarda SOLO con dígitos; el formato con guiones es de presentación. */
public final class Cuit {

    private Cuit() {}

    /** "30-54668997-9" -> "30546689979". null o vacío -> null. */
    public static String normalizar(String cuit) {
        if (cuit == null) return null;
        String digitos = cuit.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /** "30546689979" -> "30-54668997-9". Si no tiene 11 dígitos se devuelve tal cual. */
    public static String formatear(String cuit) {
        if (cuit == null || cuit.length() != 11) return cuit;
        return cuit.substring(0, 2) + "-" + cuit.substring(2, 10) + "-" + cuit.substring(10);
    }
}
