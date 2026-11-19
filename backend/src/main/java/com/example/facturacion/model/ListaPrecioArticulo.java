package com.example.facturacion.model;

import java.math.BigDecimal;

import jakarta.persistence.*;

/**
 * Precio de UN artículo en UNA lista de precios.
 * El "articuloId" que viaja en los renglones de la factura es el ID de ESTA entidad,
 * porque identifica a la vez el artículo y el precio que se aplicó.
 */
@Entity
@Table(indexes = @Index(name = "idx_lpa_lista_articulo", columnList = "lista_precio_id, articulo_id"))
public class ListaPrecioArticulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "articulo_id")
    private Articulo articulo;

    /** La columna admite null solo por compatibilidad con filas viejas; DatosDePrueba las completa. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lista_precio_id")
    private ListaPrecio listaPrecio;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal precio;

    public ListaPrecioArticulo() {}

    public ListaPrecioArticulo(ListaPrecio listaPrecio, Articulo articulo, BigDecimal precio) {
        this.listaPrecio = listaPrecio;
        this.articulo = articulo;
        this.precio = precio;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Articulo getArticulo() { return articulo; }
    public void setArticulo(Articulo articulo) { this.articulo = articulo; }
    public ListaPrecio getListaPrecio() { return listaPrecio; }
    public void setListaPrecio(ListaPrecio listaPrecio) { this.listaPrecio = listaPrecio; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
}
