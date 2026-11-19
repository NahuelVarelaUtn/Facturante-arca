package com.example.facturacion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.facturacion.model.PuntoVenta;

import jakarta.persistence.LockModeType;

public interface PuntoVentaRepository extends JpaRepository<PuntoVenta, Long> {

    Optional<PuntoVenta> findFirstByNumeroOrderByIdAsc(Integer numero);

    /**
     * Trae el punto de venta BLOQUEANDO su fila (SELECT ... FOR UPDATE) hasta que termine la transacción.
     * Se usa al numerar facturas X: si dos usuarios emiten a la vez, el segundo espera y no repite número.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PuntoVenta p where p.id = :id")
    Optional<PuntoVenta> buscarBloqueando(@Param("id") Long id);
}
