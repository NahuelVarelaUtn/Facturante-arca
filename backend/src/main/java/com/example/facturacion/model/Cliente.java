package com.example.facturacion.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.facturacion.util.Cuit;

import jakarta.persistence.*;

/**
 * Cliente (receptor de la factura).
 * Los índices son los que usa el explorer: búsqueda por CUIT (prefijo) y por nombre.
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_cliente_cuit", columnList = "cuit"),
        @Index(name = "idx_cliente_nombre", columnList = "nombre")
})
public class Cliente {

    /** Nombre del cliente genérico para ventas a quien no figura en ARCA (DocTipo 99 / DocNro 0). */
    public static final String NOMBRE_CONSUMIDOR_FINAL = "Consumidor Final";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    /** Solo dígitos (sin guiones). null = no identificado ante ARCA. */
    @Column(length = 11)
    private String cuit;

    /**
     * Condición frente al IVA. Se guarda como texto (VARCHAR) y no como ENUM nativo de MySQL,
     * así agregar una condición nueva no obliga a alterar la tabla.
     * La columna admite null solo por compatibilidad con filas viejas; el código siempre la informa.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "condicion_fiscal", length = 30)
    private CondicionFiscal condicionFiscal;

    @Column(length = 200)
    private String domicilio;

    @Column(length = 100)
    private String localidad;

    @Column(length = 60)
    private String provincia;

    public Cliente() {}

    public Cliente(String nombre, String cuit, CondicionFiscal condicionFiscal) {
        this.nombre = nombre;
        this.cuit = Cuit.normalizar(cuit);
        this.condicionFiscal = condicionFiscal;
    }

    /** Sin CUIT = no identificado ante ARCA = Consumidor Final. */
    public boolean esConsumidorFinal() { return cuit == null || cuit.isBlank(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = Cuit.normalizar(cuit); }
    public String getDomicilio() { return domicilio; }
    public void setDomicilio(String domicilio) { this.domicilio = domicilio; }
    public String getLocalidad() { return localidad; }
    public void setLocalidad(String localidad) { this.localidad = localidad; }
    public String getProvincia() { return provincia; }
    public void setProvincia(String provincia) { this.provincia = provincia; }
    public CondicionFiscal getCondicionFiscal() { return condicionFiscal; }
    public void setCondicionFiscal(CondicionFiscal condicionFiscal) { this.condicionFiscal = condicionFiscal; }
}
