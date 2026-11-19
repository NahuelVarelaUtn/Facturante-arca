package com.example.facturacion.util;

/** Ayuda para armar patrones LIKE seguros a partir de texto que escribe el usuario. */
public final class Like {

    /** Carácter de escape usado en las consultas: {@code like ... escape '!'}. */
    public static final char ESCAPE = '!';

    private Like() {}

    /** Escapa los comodines de LIKE (% y _) para que se busquen como texto literal. */
    public static String escapar(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
