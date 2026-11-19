package com.example.facturacion.repository;

import com.example.facturacion.model.TipoMoneda;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoMonedaRepository extends JpaRepository<TipoMoneda, Long> {
    Optional<TipoMoneda> findFirstByCodigoOrderByIdAsc(String codigo);
}
