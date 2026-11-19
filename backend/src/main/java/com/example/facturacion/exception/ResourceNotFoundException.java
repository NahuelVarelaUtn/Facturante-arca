package com.example.facturacion.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Se lanza cuando no existe un recurso buscado por ID.
 * Spring la traduce a HTTP 404 Not Found (ver también GlobalExceptionHandler).
 *
 * Reemplaza a las dos excepciones que había (RecursoNoEncontradoException y esta):
 * ahora hay una sola, con dos constructores.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }

    /** Ej.: new ResourceNotFoundException("Cliente", 7L) -> "Cliente con id 7 no encontrado/a" */
    public ResourceNotFoundException(String entidad, Long id) {
        super(entidad + " con id " + id + " no encontrado/a");
    }
}
