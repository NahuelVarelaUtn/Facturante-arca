package com.example.facturacion.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.facturacion.dto.ClienteDTO;
import com.example.facturacion.model.Cliente;
import com.example.facturacion.model.CondicionFiscal;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /** El cliente genérico "Consumidor Final" (sin CUIT), si ya existe. */
    Optional<Cliente> findFirstByNombreAndCuitIsNull(String nombre);

    /** El CUIT se pasa ya normalizado (solo dígitos). */
    boolean existsByCuit(String cuit);

    // ---- Para la carga masiva (CargadorDatosDemo): UNA consulta en vez de un "existe" por cada fila ----

    /** Todos los CUIT ya cargados (normalizados, solo dígitos). */
    @Query("select c.cuit from Cliente c where c.cuit is not null")
    Set<String> cuitsExistentes();

    /** Nombres de los clientes sin CUIT (a esos se los identifica por el nombre). */
    @Query("select c.nombre from Cliente c where c.cuit is null")
    Set<String> nombresDeClientesSinCuit();

    /**
     * Explorer de clientes. Cada parámetro en null significa "no filtrar por eso".
     *  - Proyecta directo al DTO: solo viajan las columnas que se muestran (nunca SELECT *).
     *  - CUIT: LIKE por prefijo ('2030%') -> usa el índice idx_cliente_cuit.
     *  - Nombre: LIKE "contiene"; con tablas muy grandes conviene pasar a un índice FULLTEXT.
     *  - "nombre" y "cuitTexto" salen del mismo texto libre: llega uno u otro (un LIKE contra null no coincide).
     *  - Los patrones llegan con los comodines escapados (ver util.Like).
     *  - Orden: por nombre; con recientesPrimero = true (listado completo, sin criterios) por ID descendente.
     *    Siempre termina en c.id para que el orden sea estable y las páginas no se pisen ni salteen filas.
     */
    @Query(value = """
            select new com.example.facturacion.dto.ClienteDTO(
                c.id, c.nombre, c.cuit, c.condicionFiscal, c.domicilio, c.localidad, c.provincia)
            from Cliente c
            where (:id is null or c.id = :id)
              and (:condicion is null or c.condicionFiscal = :condicion)
              and (:cuit is null or c.cuit like concat(:cuit, '%') escape '!')
              and ((:nombre is null and :cuitTexto is null)
                   or lower(c.nombre) like concat('%', :nombre, '%') escape '!'
                   or c.cuit like concat(:cuitTexto, '%') escape '!')
            order by case when :recientesPrimero = true then c.id else 0 end desc, c.nombre, c.id
            """,
            countQuery = """
            select count(c)
            from Cliente c
            where (:id is null or c.id = :id)
              and (:condicion is null or c.condicionFiscal = :condicion)
              and (:cuit is null or c.cuit like concat(:cuit, '%') escape '!')
              and ((:nombre is null and :cuitTexto is null)
                   or lower(c.nombre) like concat('%', :nombre, '%') escape '!'
                   or c.cuit like concat(:cuitTexto, '%') escape '!')
            """)
    Page<ClienteDTO> buscar(@Param("id") Long id,
                            @Param("condicion") CondicionFiscal condicion,
                            @Param("cuit") String cuit,
                            @Param("nombre") String nombre,
                            @Param("cuitTexto") String cuitTexto,
                            @Param("recientesPrimero") boolean recientesPrimero,
                            Pageable pageable);

    // ---- Migración de filas viejas (las usa DatosDePrueba; son UPDATE masivos, no traen filas) ----

    /** Pasa los CUIT guardados con guiones al formato normalizado (solo dígitos). */
    @Transactional
    @Modifying
    @Query("update Cliente c set c.cuit = replace(c.cuit, '-', '') where c.cuit like '%-%'")
    int normalizarCuits();

    @Transactional
    @Modifying
    @Query("update Cliente c set c.condicionFiscal = :condicion where c.condicionFiscal is null and c.cuit is not null")
    int completarCondicionDeIdentificados(@Param("condicion") CondicionFiscal condicion);

    @Transactional
    @Modifying
    @Query("update Cliente c set c.condicionFiscal = :condicion where c.condicionFiscal is null and c.cuit is null")
    int completarCondicionDeNoIdentificados(@Param("condicion") CondicionFiscal condicion);
}
