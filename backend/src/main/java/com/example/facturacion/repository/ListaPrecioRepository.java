package com.example.facturacion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.facturacion.model.ListaPrecio;

public interface ListaPrecioRepository extends JpaRepository<ListaPrecio, Long> {

    Optional<ListaPrecio> findByNombre(String nombre);
}
