package com.example.facturacion.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.facturacion.model.Articulo;

public interface ArticuloRepository extends JpaRepository<Articulo, Long> {

    Optional<Articulo> findFirstByCodigo(String codigo);

    Optional<Articulo> findFirstByDescripcionOrderByIdAsc(String descripcion);

    // ---- Para la carga masiva (CargadorDatosDemo) ----

    /** Todos los códigos ya cargados: UNA consulta en vez de un "existe" por cada fila. */
    @Query("select a.codigo from Articulo a where a.codigo is not null")
    Set<String> codigosExistentes();

    /** Artículos cargados antes de que existiera la unidad de medida (para completarles el dato). */
    List<Articulo> findByUnidadMedidaIsNull();
}
