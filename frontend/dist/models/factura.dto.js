/**
 * Interfaces espejo de los DTOs del backend (Spring Boot).
 *
 * Correspondencia de tipos Java -> TypeScript:
 *   Long / Integer / BigDecimal / Double  -> number
 *   String                                -> string
 *   Date / LocalDate                      -> string (ISO 8601: "2026-10-14" o "2026-10-14T10:30:00.000+00:00")
 *   List<T>                               -> T[]
 *   campo que puede ser null en Java      -> campo?: T | null
 *
 * IMPORTANTE: los nombres de campo de abajo son los que se asumieron a partir de la consigna.
 * Si algún nombre difiere en los DTOs Java de tus compañeros, corregilo SOLO acá
 * y el compilador te marca todo lo que haya que ajustar en el resto del código.
 */
export const ESTADO_BORRADOR = "BORRADOR";
export const ESTADO_EMITIDA = "EMITIDA";
export const ESTADO_ANULADA = "ANULADA";
//# sourceMappingURL=factura.dto.js.map