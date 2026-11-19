package com.example.facturacion.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.facturacion.model.EstadoFactura;
import com.example.facturacion.model.FacturaVenta;
import com.example.facturacion.model.LetraComprobante;

import jakarta.persistence.criteria.Predicate;

/**
 * JpaSpecificationExecutor permite armar el filtro del listado dinámicamente (solo se aplican
 * los filtros que vengan informados) y combinarlo con paginación.
 */
public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long>,
        JpaSpecificationExecutor<FacturaVenta> {

    /**
     * Consulta paginada del listado trayendo las relaciones ManyToOne en el mismo SELECT (@EntityGraph).
     * Sin esto, el mapper dispararía 4 consultas extra por cada fila de la página (problema N+1).
     */
    @Override
    @EntityGraph(attributePaths = {"cliente", "puntoVenta", "tipoMoneda", "listaPrecio"})
    Page<FacturaVenta> findAll(Specification<FacturaVenta> spec, Pageable pageable);

    /** Último número usado por las facturas internas (X) de un punto de venta. 0 si todavía no hay. */
    @Query("""
            select coalesce(max(f.numeroComprobante), 0)
            from FacturaVenta f
            where f.puntoVenta.id = :puntoVentaId and f.letra = :letra
            """)
    long ultimoNumero(@Param("puntoVentaId") Long puntoVentaId, @Param("letra") LetraComprobante letra);

    // ---- Dashboard: se filtra por RANGO de fechas (usa el índice), no con MONTH()/YEAR() ----

    /** Total facturado con CAE entre dos fechas (desde inclusive, hasta exclusive). */
    @Query("""
            select coalesce(sum(f.importeTotal), 0)
            from FacturaVenta f
            where f.fecha >= :desde and f.fecha < :hasta and f.cae is not null
            """)
    BigDecimal totalFacturado(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query("select count(f) from FacturaVenta f where f.fecha >= :desde and f.fecha < :hasta")
    long contarComprobantes(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Arma el WHERE con los filtros opcionales. Un parámetro null = "no filtrar por eso". */
    static Specification<FacturaVenta> conFiltros(String nroFactura,
                                                  LocalDate fechaDesde,
                                                  LocalDate fechaHasta,
                                                  Long clienteId,
                                                  Boolean informadaArca,
                                                  EstadoFactura estado) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();

            if (nroFactura != null && !nroFactura.isBlank()) {
                // búsqueda parcial e insensible a mayúsculas: "0001-0000" encuentra "0001-00000012"
                condiciones.add(cb.like(cb.lower(root.<String>get("nroFactura")),
                        "%" + nroFactura.trim().toLowerCase() + "%"));
            }
            if (fechaDesde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("fecha"), fechaDesde));
            }
            if (fechaHasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.<LocalDate>get("fecha"), fechaHasta));
            }
            if (clienteId != null) {
                condiciones.add(cb.equal(root.get("cliente").get("id"), clienteId));
            }
            if (informadaArca != null) {
                // "Informada" = ARCA le otorgó CAE
                condiciones.add(informadaArca ? cb.isNotNull(root.get("cae")) : cb.isNull(root.get("cae")));
            }
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }
}
