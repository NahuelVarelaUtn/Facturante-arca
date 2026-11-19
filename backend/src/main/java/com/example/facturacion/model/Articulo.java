package com.example.facturacion.model;

import java.math.BigDecimal;

import jakarta.persistence.*;

/**
 * Artículo del catálogo. El precio NO vive acá: depende de la lista (ver ListaPrecioArticulo).
 * La alícuota de IVA sí es del artículo (21%, 10,5%, 0%...).
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_articulo_codigo", columnList = "codigo", unique = true),
        @Index(name = "idx_articulo_descripcion", columnList = "descripcion")
})
public class Articulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código de negocio (ej. "TEC-001"), siempre en mayúsculas. null solo en filas viejas sin migrar. */
    @Column(length = 30)
    private String codigo;

    @Column(nullable = false)
    private String descripcion;

    /** Porcentaje de IVA (ej. 21.00). null en filas viejas = se asume 21%. */
    @Column(name = "alicuota_iva", precision = 5, scale = 2)
    private BigDecimal alicuotaIva;

    /** Rubro del artículo (Electrónica, Ferretería...). Sirve para organizar el catálogo. */
    @Column(length = 60)
    private String categoria;

    /** Unidad en la que se vende: unidad, kg, litro, metro, hora, caja, pack. */
    @Column(name = "unidad_medida", length = 20)
    private String unidadMedida;

    public Articulo() {}

    public Articulo(String codigo, String descripcion, BigDecimal alicuotaIva) {
        setCodigo(codigo);
        this.descripcion = descripcion;
        this.alicuotaIva = alicuotaIva;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo == null ? null : codigo.trim().toUpperCase(); }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }
    public BigDecimal getAlicuotaIva() { return alicuotaIva; }
    public void setAlicuotaIva(BigDecimal alicuotaIva) { this.alicuotaIva = alicuotaIva; }
}
