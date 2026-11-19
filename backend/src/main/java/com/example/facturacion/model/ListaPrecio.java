package com.example.facturacion.model;

import jakarta.persistence.*;

/** Lista de precios (MAYORISTA, MINORISTA...). Son pocas filas: se pueden listar completas. */
@Entity
@Table(name = "lista_precio")
public class ListaPrecio {

    public static final String MINORISTA = "MINORISTA";
    public static final String MAYORISTA = "MAYORISTA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String nombre;

    public ListaPrecio() {}

    public ListaPrecio(String nombre) {
        this.nombre = nombre;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
