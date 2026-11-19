package com.example.facturacion.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.facturacion.model.Articulo;
import com.example.facturacion.model.Cliente;
import com.example.facturacion.model.CondicionFiscal;
import com.example.facturacion.model.ListaPrecio;
import com.example.facturacion.model.ListaPrecioArticulo;
import com.example.facturacion.repository.ArticuloRepository;
import com.example.facturacion.repository.ClienteRepository;
import com.example.facturacion.repository.ListaPrecioArticuloRepository;
import com.example.facturacion.repository.ListaPrecioRepository;
import com.example.facturacion.util.Cuit;

/**
 * SEEDER: carga masiva de clientes y artículos al arrancar el backend.
 *
 *   src/main/resources/seed/clientes.csv   -> 100 clientes
 *   src/main/resources/seed/articulos.csv  -> 100 artículos de tecnología (con precio minorista y mayorista)
 *
 * Se ejecuta solo si app.cargar-datos-prueba=true (application.properties), después de DatosDePrueba.
 *
 * Cómo trabaja (pensado para cargar en bloque, no fila por fila):
 *   1. Lee el CSV completo.
 *   2. Pregunta a la base UNA vez qué CUIT / códigos ya existen.
 *   3. Inserta todos los que faltan con saveAll, dentro de una sola transacción
 *      (si una fila falla, no queda nada a medio cargar).
 *
 * Es seguro ejecutarlo en cada arranque: un cliente se identifica por su CUIT (o por su nombre, si no
 * tiene CUIT) y un artículo por su código; lo que ya existe no se duplica ni se pisa.
 *
 * Para cambiar los datos: editar los CSV (campos separados por ";") o regenerarlos con
 * tools/generar_datos_demo.py.
 */
@Component
@Order(2) // después de DatosDePrueba (puntos de venta, monedas y listas de precios)
@ConditionalOnProperty(name = "app.cargar-datos-prueba", havingValue = "true")
public class CargadorDatosDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CargadorDatosDemo.class);
    private static final String SEPARADOR = ";";

    private final ClienteRepository clienteRepository;
    private final ArticuloRepository articuloRepository;
    private final ListaPrecioRepository listaPrecioRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;

    public CargadorDatosDemo(ClienteRepository clienteRepository,
                             ArticuloRepository articuloRepository,
                             ListaPrecioRepository listaPrecioRepository,
                             ListaPrecioArticuloRepository listaPrecioArticuloRepository) {
        this.clienteRepository = clienteRepository;
        this.articuloRepository = articuloRepository;
        this.listaPrecioRepository = listaPrecioRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int clientes = cargarClientes();
        int articulos = cargarArticulos();
        log.info("Seeder: {} clientes y {} artículos nuevos. Total en la base: {} clientes, {} artículos.",
                clientes, articulos, clienteRepository.count(), articuloRepository.count());
    }

    /** Columnas: nombre;cuit;condicion;domicilio;localidad;provincia  (cuit vacío = cliente sin identificar) */
    private int cargarClientes() {
        Set<String> cuits = clienteRepository.cuitsExistentes();
        Set<String> nombresSinCuit = clienteRepository.nombresDeClientesSinCuit();

        List<Cliente> nuevos = new ArrayList<>();
        for (String[] c : leer("seed/clientes.csv", 6)) {
            String nombre = c[0];
            String cuit = Cuit.normalizar(c[1]); // null si la columna viene vacía
            boolean yaExiste = cuit != null ? cuits.contains(cuit) : nombresSinCuit.contains(nombre);
            if (yaExiste) {
                continue;
            }
            Cliente cliente = new Cliente(nombre, cuit, CondicionFiscal.valueOf(c[2]));
            cliente.setDomicilio(vacioANull(c[3]));
            cliente.setLocalidad(vacioANull(c[4]));
            cliente.setProvincia(vacioANull(c[5]));
            nuevos.add(cliente);
        }
        clienteRepository.saveAll(nuevos);
        return nuevos.size();
    }

    /** Columnas: codigo;descripcion;categoria;unidad;alicuota_iva;precio_minorista;precio_mayorista */
    private int cargarArticulos() {
        ListaPrecio minorista = lista(ListaPrecio.MINORISTA);
        ListaPrecio mayorista = lista(ListaPrecio.MAYORISTA);
        Set<String> codigos = articuloRepository.codigosExistentes();

        List<Articulo> nuevos = new ArrayList<>();
        List<ListaPrecioArticulo> precios = new ArrayList<>();
        Map<String, String[]> filasPorCodigo = new HashMap<>();

        for (String[] a : leer("seed/articulos.csv", 7)) {
            String codigo = a[0].trim().toUpperCase();
            filasPorCodigo.put(codigo, a);
            if (codigos.contains(codigo)) {
                continue;
            }
            Articulo articulo = new Articulo(codigo, a[1], new BigDecimal(a[4]));
            articulo.setCategoria(a[2]);
            articulo.setUnidadMedida(a[3]);
            nuevos.add(articulo);
            precios.add(new ListaPrecioArticulo(minorista, articulo, new BigDecimal(a[5])));
            precios.add(new ListaPrecioArticulo(mayorista, articulo, new BigDecimal(a[6])));
        }
        articuloRepository.saveAll(nuevos);           // primero los artículos (reciben su ID)...
        listaPrecioArticuloRepository.saveAll(precios); // ...y después sus dos precios

        completarArticulosViejos(filasPorCodigo);
        return nuevos.size();
    }

    /** A los artículos cargados con versiones anteriores les completa categoría y unidad de medida. */
    private void completarArticulosViejos(Map<String, String[]> filasPorCodigo) {
        for (Articulo articulo : articuloRepository.findByUnidadMedidaIsNull()) {
            String[] fila = articulo.getCodigo() == null ? null : filasPorCodigo.get(articulo.getCodigo());
            if (fila != null) {
                articulo.setCategoria(fila[2]);
                articulo.setUnidadMedida(fila[3]);
            }
        }
        // No hace falta save(): dentro de la transacción, Hibernate guarda solo los cambios de estas entidades
    }

    private ListaPrecio lista(String nombre) {
        return listaPrecioRepository.findByNombre(nombre)
                .orElseGet(() -> listaPrecioRepository.save(new ListaPrecio(nombre)));
    }

    private static String vacioANull(String valor) {
        return valor.isBlank() ? null : valor.trim();
    }

    /** Lee un CSV del classpath salteando el encabezado. Falla con un mensaje claro si una fila está mal. */
    private List<String[]> leer(String recurso, int columnas) {
        List<String[]> filas = new ArrayList<>();
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(new ClassPathResource(recurso).getInputStream(), StandardCharsets.UTF_8))) {
            String linea = lector.readLine(); // encabezado
            int numero = 1;
            while ((linea = lector.readLine()) != null) {
                numero++;
                if (linea.isBlank()) {
                    continue;
                }
                String[] campos = linea.split(SEPARADOR, -1);
                if (campos.length != columnas) {
                    throw new IllegalStateException(recurso + ", línea " + numero + ": se esperaban "
                            + columnas + " columnas y hay " + campos.length);
                }
                filas.add(campos);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + recurso, e);
        }
        return filas;
    }
}
