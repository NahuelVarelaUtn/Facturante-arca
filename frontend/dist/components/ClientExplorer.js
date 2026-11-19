import { buscarClientes } from "../api/clientes.api.js";
import { CLAVE_BUSQUEDA, Explorer } from "./Explorer.js";
/** Condiciones frente al IVA, en el orden en que se muestran. Mismos valores que el enum del backend. */
export const CONDICIONES_FISCALES = [
    { valor: "RESPONSABLE_INSCRIPTO", etiqueta: "Responsable Inscripto" },
    { valor: "MONOTRIBUTO", etiqueta: "Responsable Monotributo" },
    { valor: "EXENTO", etiqueta: "IVA Sujeto Exento" },
    { valor: "CONSUMIDOR_FINAL", etiqueta: "Consumidor Final" },
];
/**
 * Explorer de clientes: busca por nombre o CUIT, y filtra por ID, CUIT o condición frente al IVA.
 * Uso:  const cliente = await new ClientExplorer().abrir();
 */
export class ClientExplorer extends Explorer {
    constructor() {
        super({
            titulo: "Seleccionar cliente",
            placeholder: "Buscar por nombre o CUIT…",
            ayudaInicial: "Escribí para buscar (desde 1 carácter) o presioná Enter para ver todos los clientes.",
            columnas: [
                { titulo: "ID", valor: (c) => String(c.id), clase: "num" },
                { titulo: "Nombre / Razón social", valor: (c) => c.nombre },
                { titulo: "CUIT", valor: (c) => c.cuit ?? "—" },
                { titulo: "Condición IVA", valor: (c) => c.condicionIvaDescripcion ?? "—" },
                { titulo: "Localidad", valor: (c) => [c.localidad, c.provincia].filter(Boolean).join(", ") || "—" },
            ],
            filtros: [
                { clave: "id", etiqueta: "ID", tipo: "numero", placeholder: "Ej. 15" },
                { clave: "cuit", etiqueta: "CUIT", tipo: "texto", placeholder: "Empieza con… ej. 3050" },
                {
                    clave: "condicionIva",
                    etiqueta: "Condición IVA",
                    tipo: "select",
                    opciones: () => CONDICIONES_FISCALES.map((c) => ({ valor: c.valor, etiqueta: c.etiqueta })),
                },
            ],
            buscar: (c, pagina, signal) => buscarClientes({
                search: c[CLAVE_BUSQUEDA],
                id: c.id ? Number(c.id) : undefined,
                cuit: c.cuit,
                condicionIva: c.condicionIva,
            }, pagina, signal),
        });
    }
}
//# sourceMappingURL=ClientExplorer.js.map