package com.example.facturacion.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.facturacion.dto.ArticuloPrecioDTO;
import com.example.facturacion.model.ListaPrecio;
import com.example.facturacion.model.ListaPrecioArticulo;

public interface ListaPrecioArticuloRepository extends JpaRepository<ListaPrecioArticulo, Long> {

    boolean existsByArticuloIdAndListaPrecioId(Long articuloId, Long listaPrecioId);

    /**
     * Trae en UNA consulta todos los precios pedidos, con su artículo y su lista.
     * La usa FacturaService al armar los renglones (evita una consulta por renglón).
     */
    @Query("""
            select l from ListaPrecioArticulo l
            join fetch l.articulo
            left join fetch l.listaPrecio
            where l.id in :ids
            """)
    List<ListaPrecioArticulo> buscarConArticuloYLista(@Param("ids") Collection<Long> ids);

    /**
     * Explorer de artículos. Cada parámetro en null significa "no filtrar por eso".
     *  - Proyecta directo al DTO en UNA sola consulta (sin N+1 y sin SELECT *).
     *  - Código: LIKE por prefijo ('TEC%') -> usa el índice idx_articulo_codigo.
     *  - Descripción: LIKE "contiene"; con catálogos muy grandes conviene un índice FULLTEXT.
     *  - Orden: por descripción; con recientesPrimero = true (listado completo) por ID de artículo descendente.
     *    Siempre termina en columnas únicas para que el orden sea estable entre páginas.
     */
    @Query(value = """
            select new com.example.facturacion.dto.ArticuloPrecioDTO(
                l.id, a.id, a.codigo, a.descripcion, a.categoria, a.unidadMedida, a.alicuotaIva,
                lp.id, lp.nombre, l.precio)
            from ListaPrecioArticulo l
            join l.articulo a
            join l.listaPrecio lp
            where (:articuloId is null or a.id = :articuloId)
              and (:listaPrecioId is null or lp.id = :listaPrecioId)
              and (:codigo is null or a.codigo like concat(:codigo, '%') escape '!')
              and (:texto is null
                   or lower(a.descripcion) like concat('%', :texto, '%') escape '!'
                   or a.codigo like concat(:textoCodigo, '%') escape '!')
            order by case when :recientesPrimero = true then a.id else 0 end desc, a.descripcion, a.id, lp.nombre
            """,
            countQuery = """
            select count(l)
            from ListaPrecioArticulo l
            join l.articulo a
            join l.listaPrecio lp
            where (:articuloId is null or a.id = :articuloId)
              and (:listaPrecioId is null or lp.id = :listaPrecioId)
              and (:codigo is null or a.codigo like concat(:codigo, '%') escape '!')
              and (:texto is null
                   or lower(a.descripcion) like concat('%', :texto, '%') escape '!'
                   or a.codigo like concat(:textoCodigo, '%') escape '!')
            """)
    Page<ArticuloPrecioDTO> buscar(@Param("articuloId") Long articuloId,
                                   @Param("listaPrecioId") Long listaPrecioId,
                                   @Param("codigo") String codigo,
                                   @Param("texto") String texto,
                                   @Param("textoCodigo") String textoCodigo,
                                   @Param("recientesPrimero") boolean recientesPrimero,
                                   Pageable pageable);

    /** Migración: los precios cargados antes de que existieran las listas pasan a la lista indicada. */
    @Transactional
    @Modifying
    @Query("update ListaPrecioArticulo l set l.listaPrecio = :lista where l.listaPrecio is null")
    int asignarListaALosQueNoTienen(@Param("lista") ListaPrecio lista);
}
