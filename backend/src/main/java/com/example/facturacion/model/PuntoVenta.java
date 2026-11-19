package com.example.facturacion.model;

import jakarta.persistence.*;

/** ENTIDAD DE APOYO (ver Cliente). */
@Entity
public class PuntoVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número de punto de venta (ej. 1 -> "0001"). */
    @Column(nullable = false)
    private Integer numero;

    private String descripcion;

    public PuntoVenta() {}

    public PuntoVenta(Integer numero, String descripcion) {
        this.numero = numero;
        this.descripcion = descripcion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
