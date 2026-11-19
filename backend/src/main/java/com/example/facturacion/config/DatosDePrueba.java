package com.example.facturacion.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.facturacion.model.*;
import com.example.facturacion.repository.*;

/**
 * Datos BASE que el sistema necesita para funcionar: puntos de venta, monedas y listas de precios.
 * Se crean al arrancar si faltan (solo si app.cargar-datos-prueba=true).
 * Los clientes y artículos de prueba los carga CargadorDatosDemo desde los CSV de resources/seed.
 */
@Component
@Order(1) // antes que CargadorDatosDemo
@ConditionalOnProperty(name = "app.cargar-datos-prueba", havingValue = "true")
public class DatosDePrueba implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDePrueba.class);

    private final ClienteRepository clienteRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final ListaPrecioRepository listaPrecioRepository;

    public DatosDePrueba(ClienteRepository clienteRepository,
                         PuntoVentaRepository puntoVentaRepository,
                         TipoMonedaRepository tipoMonedaRepository,
                         ListaPrecioArticuloRepository listaPrecioArticuloRepository,
                         ListaPrecioRepository listaPrecioRepository) {
        this.clienteRepository = clienteRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.listaPrecioRepository = listaPrecioRepository;
    }

    @Override
    public void run(String... args) {
        // Todo se carga con "buscar o crear": con MySQL los datos persisten entre arranques y
        // si se insertaran siempre, los desplegables mostrarían cada opción repetida.
        migrarFilasViejas();

        puntoVenta(1, "Casa central");
        puntoVenta(2, "Sucursal Mendoza");

        moneda("ARS", "Peso argentino");
        moneda("USD", "Dólar estadounidense");

        ListaPrecio minorista = listaPrecio(ListaPrecio.MINORISTA);
        listaPrecio(ListaPrecio.MAYORISTA);
        // Precios cargados antes de que existieran las listas: se consideran minoristas.
        listaPrecioArticuloRepository.asignarListaALosQueNoTienen(minorista);

        log.info("Datos base verificados (sin duplicar): puntos de venta, monedas y listas de precios");
    }

    /**
     * Adapta los datos cargados con versiones anteriores del modelo. Son UPDATE masivos (no traen filas)
     * y no hacen nada si la base ya está al día.
     */
    private void migrarFilasViejas() {
        clienteRepository.normalizarCuits(); // "30-54668997-9" -> "30546689979"
        clienteRepository.completarCondicionDeIdentificados(CondicionFiscal.RESPONSABLE_INSCRIPTO);
        clienteRepository.completarCondicionDeNoIdentificados(CondicionFiscal.CONSUMIDOR_FINAL);
    }

    private void puntoVenta(int numero, String descripcion) {
        if (puntoVentaRepository.findFirstByNumeroOrderByIdAsc(numero).isEmpty()) {
            puntoVentaRepository.save(new PuntoVenta(numero, descripcion));
        }
    }

    private void moneda(String codigo, String descripcion) {
        if (tipoMonedaRepository.findFirstByCodigoOrderByIdAsc(codigo).isEmpty()) {
            tipoMonedaRepository.save(new TipoMoneda(codigo, descripcion));
        }
    }

    private ListaPrecio listaPrecio(String nombre) {
        return listaPrecioRepository.findByNombre(nombre)
                .orElseGet(() -> listaPrecioRepository.save(new ListaPrecio(nombre)));
    }
}
