package com.example.facturacion.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.facturacion.dto.ArticuloPrecioDTO;
import com.example.facturacion.dto.ListaPrecioDTO;
import com.example.facturacion.dto.PageResponse;
import com.example.facturacion.repository.ListaPrecioArticuloRepository;
import com.example.facturacion.repository.ListaPrecioRepository;
import com.example.facturacion.util.Like;
import com.example.facturacion.util.Paginacion;

/** Explorer de artículos (con su precio por lista). Nunca devuelve más de una página (100 filas) por pedido. */
@Service
public class ArticuloService {

    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final ListaPrecioRepository listaPrecioRepository;

    public ArticuloService(ListaPrecioArticuloRepository listaPrecioArticuloRepository,
                           ListaPrecioRepository listaPrecioRepository) {
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.listaPrecioRepository = listaPrecioRepository;
    }

    /**
     * @param search        texto libre: coincide con la descripción (contiene) o con el código (empieza con)
     * @param codigo        prefijo de código
     * @param articuloId    ID exacto del artículo
     * @param listaPrecioId lista de precios (MAYORISTA / MINORISTA)
     * La búsqueda arranca desde 1 carácter. Sin texto, código ni ID lista TODOS los artículos (de la lista
     * indicada, si hay una), de a una página de hasta 100 filas, con los más recientes primero.
     */
    @Transactional(readOnly = true)
    public PageResponse<ArticuloPrecioDTO> buscar(String search, String codigo, Long articuloId,
                                                  Long listaPrecioId, Paginacion paginacion) {
        String libre = search == null ? "" : search.trim();
        String texto = libre.isEmpty() ? null : Like.escapar(libre.toLowerCase());
        String textoCodigo = libre.isEmpty() ? null : Like.escapar(libre.toUpperCase()); // los códigos van en mayúsculas

        String codigoFiltro = (codigo == null || codigo.isBlank())
                ? null
                : Like.escapar(codigo.trim().toUpperCase());

        boolean listadoCompleto = texto == null && codigoFiltro == null && articuloId == null;
        return PageResponse.de(
                listaPrecioArticuloRepository.buscar(articuloId, listaPrecioId, codigoFiltro, texto, textoCodigo,
                        listadoCompleto, paginacion.toPageable()),
                paginacion);
    }

    /** Opciones del selector de lista de precios. Son pocas filas fijas, por eso se devuelven todas. */
    @Transactional(readOnly = true)
    public List<ListaPrecioDTO> listarListasPrecio() {
        return listaPrecioRepository.findAll().stream()
                .map(l -> new ListaPrecioDTO(l.getId(), l.getNombre()))
                .sorted(Comparator.comparing(ListaPrecioDTO::nombre).reversed()) // MINORISTA primero
                .toList();
    }
}
