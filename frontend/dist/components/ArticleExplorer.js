import { buscarArticulos, listarListasPrecio } from "../api/articulos.api.js";
import { formatearNumero } from "../utils/format.js";
import { CLAVE_BUSQUEDA, Explorer } from "./Explorer.js";
/**
 * Explorer de artículos: busca por nombre o código, y filtra por código, ID o lista de precios.
 * Con todo vacío, Enter o "Buscar" lista todos los artículos de la lista de precios activa, paginados.
 *
 * Uso:  const articulo = await new ArticleExplorer().abrirConLista(listaActiva.id);
 */
export class ArticleExplorer extends Explorer {
    constructor() {
        super({
            titulo: "Seleccionar artículo",
            placeholder: "Buscar por nombre o código…",
            ayudaInicial: "Escribí para buscar (desde 1 carácter) o presioná Enter para ver todos los artículos.",
            columnas: [
                { titulo: "Código", valor: (a) => a.codigo ?? "—" },
                { titulo: "Artículo", valor: (a) => a.descripcion },
                { titulo: "Categoría", valor: (a) => a.categoria ?? "—" },
                { titulo: "Unidad", valor: (a) => a.unidadMedida ?? "—" },
                { titulo: "Lista", valor: (a) => a.listaPrecio },
                { titulo: "IVA", valor: (a) => (a.alicuotaIva == null ? "—" : `${String(a.alicuotaIva).replace(".", ",")}%`), clase: "num" },
                { titulo: "Precio", valor: (a) => formatearNumero(a.precio), clase: "num" },
            ],
            filtros: [
                { clave: "codigo", etiqueta: "Código", tipo: "texto", placeholder: "Empieza con… ej. TEC" },
                { clave: "id", etiqueta: "ID", tipo: "numero", placeholder: "Ej. 3" },
                {
                    clave: "listaPrecioId",
                    etiqueta: "Lista de precios",
                    tipo: "select",
                    opciones: async () => (await listarListasPrecio()).map((l) => ({ valor: String(l.id), etiqueta: l.nombre })),
                },
            ],
            buscar: (c, pagina, signal) => buscarArticulos({
                search: c[CLAVE_BUSQUEDA],
                codigo: c.codigo,
                id: c.id ? Number(c.id) : undefined,
                listaPrecioId: c.listaPrecioId ? Number(c.listaPrecioId) : undefined,
            }, pagina, signal),
        });
    }
    /** Abre el explorer mostrando solo precios de la lista activa de la factura (filtro bloqueado). */
    abrirConLista(listaPrecioId) {
        return this.abrir({ fijos: { listaPrecioId: String(listaPrecioId) } });
    }
}
//# sourceMappingURL=ArticleExplorer.js.map