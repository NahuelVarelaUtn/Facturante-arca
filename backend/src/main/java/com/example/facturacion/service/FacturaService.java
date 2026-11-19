package com.example.facturacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.facturacion.dto.FacturaDetalleRequestDTO;
import com.example.facturacion.dto.FacturaReporteDTO;
import com.example.facturacion.dto.FacturaRequestDTO;
import com.example.facturacion.dto.LetraSugeridaDTO;
import com.example.facturacion.exception.ResourceNotFoundException;
import com.example.facturacion.mapper.FacturaMapper;
import com.example.facturacion.model.*;
import com.example.facturacion.repository.*;

/**
 * Ciclo de vida de la factura:
 *
 *   crear / actualizar  -> guardan un BORRADOR (no se habla con ARCA, se puede editar las veces que haga falta)
 *   emitir              -> Factura A/B/C: pide el CAE a ARCA.  Factura X: la numera el sistema, sin llamar a ARCA.
 *   actualizar una X    -> una X emitida sigue editable; si se le pone letra A/B/C se convierte en borrador fiscal
 *   anular              -> solo facturas EMITIDAS
 *   eliminar            -> solo BORRADORES
 *
 * Regla central: una factura con CAE de ARCA (o anulada) es de SOLO LECTURA.
 */
@Service
public class FacturaService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal ALICUOTA_GENERAL = new BigDecimal("21.00");
    private static final String MONEDA_PESOS = "ARS";
    /** Códigos de tipo de documento de ARCA. */
    private static final int DOC_CUIT = 80;
    private static final int DOC_SIN_IDENTIFICAR = 99;

    private final FacturaVentaRepository facturaRepository;
    private final ClienteRepository clienteRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final ListaPrecioRepository listaPrecioRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final LetraComprobanteResolver letraResolver;
    private final WsfeService wsfeService;
    /** Permite abrir transacciones cortas "a mano" dentro de emitir() (ver ese método). */
    private final TransactionTemplate transaccion;

    /**
     * IDs de facturas que se están emitiendo en este momento. Evita que un doble clic en "Facturar"
     * pida DOS CAE para el mismo borrador. Alcanza mientras haya una sola instancia del backend;
     * con varias instancias habría que llevar esta marca a la base de datos.
     */
    private final Set<Long> emitiendo = ConcurrentHashMap.newKeySet();

    public FacturaService(FacturaVentaRepository facturaRepository,
                          ClienteRepository clienteRepository,
                          PuntoVentaRepository puntoVentaRepository,
                          TipoMonedaRepository tipoMonedaRepository,
                          ListaPrecioRepository listaPrecioRepository,
                          ListaPrecioArticuloRepository listaPrecioArticuloRepository,
                          LetraComprobanteResolver letraResolver,
                          WsfeService wsfeService,
                          PlatformTransactionManager transactionManager) {
        this.facturaRepository = facturaRepository;
        this.clienteRepository = clienteRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.listaPrecioRepository = listaPrecioRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.letraResolver = letraResolver;
        this.wsfeService = wsfeService;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    // =====================================================================
    // CONSULTAS
    // =====================================================================

    /** Listado paginado con filtros opcionales. Devuelve resúmenes (sin renglones). */
    @Transactional(readOnly = true)
    public Page<FacturaReporteDTO> buscarFacturas(String nroFactura,
                                                  LocalDate fechaDesde,
                                                  LocalDate fechaHasta,
                                                  Long clienteId,
                                                  Boolean informadaArca,
                                                  EstadoFactura estado,
                                                  Pageable pageable) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta");
        }
        Specification<FacturaVenta> filtros = FacturaVentaRepository.conFiltros(
                nroFactura, fechaDesde, fechaHasta, clienteId, informadaArca, estado);

        return facturaRepository.findAll(filtros, pageable).map(FacturaMapper::toResumen);
    }

    /** Una factura completa, con sus renglones (para verla o editarla). */
    @Transactional(readOnly = true)
    public FacturaReporteDTO obtener(Long id) {
        return FacturaMapper.toReporte(buscarFactura(id));
    }

    /** Letra que corresponde a un receptor, para que el formulario la proponga antes de guardar. */
    public LetraSugeridaDTO sugerirLetra(CondicionFiscal condicionReceptor) {
        return new LetraSugeridaDTO(
                letraResolver.sugerir(condicionReceptor).name(),
                letraResolver.permitidas().stream().map(Enum::name).toList(),
                letraResolver.emisor().name(),
                letraResolver.discriminaIva());
    }

    // =====================================================================
    // GUARDAR BORRADOR (POST) / ACTUALIZAR BORRADOR (PUT)
    // =====================================================================

    @Transactional
    public FacturaReporteDTO crear(FacturaRequestDTO dto) {
        FacturaVenta factura = new FacturaVenta();
        factura.setEstado(EstadoFactura.BORRADOR);
        aplicarDatos(factura, dto);
        return FacturaMapper.toReporte(facturaRepository.save(factura));
    }

    /**
     * Actualiza un borrador o una Factura X ya emitida (las X son internas y siguen siendo editables).
     * Si a una X numerada se le cambia la letra por A, B o C, se CONVIERTE: pierde el número interno
     * y vuelve a ser un borrador, listo para emitirse en ARCA y recibir su número y CAE.
     */
    @Transactional
    public FacturaReporteDTO actualizar(Long id, FacturaRequestDTO dto) {
        FacturaVenta factura = buscarFactura(id);
        exigirEditable(factura);

        boolean eraInternaNumerada = factura.tieneNumero(); // solo una X emitida es editable teniendo número
        Long puntoVentaAnterior = factura.getPuntoVenta().getId();

        aplicarDatos(factura, dto);

        if (eraInternaNumerada) {
            if (factura.getLetra().esFiscal()) {
                // Conversión X -> A/B/C: el número de la serie X ya no corresponde; el nuevo lo asigna ARCA
                factura.setNumeroComprobante(null);
                factura.setNroFactura(null);
                factura.setEstado(EstadoFactura.BORRADOR);
            } else if (!puntoVentaAnterior.equals(factura.getPuntoVenta().getId())) {
                throw new IllegalArgumentException(
                        "No se puede cambiar el punto de venta de una Factura X que ya tiene número.");
            }
        }
        return FacturaMapper.toReporte(facturaRepository.save(factura));
    }

    /** Vuelca el DTO sobre la factura (nueva o existente), validando cada dato, y recalcula los importes. */
    private void aplicarDatos(FacturaVenta factura, FacturaRequestDTO dto) {
        // 1) Receptor y su condición frente al IVA
        Cliente cliente = buscar(clienteRepository, dto.clienteId(), "Cliente");
        CondicionFiscal condicion = resolverCondicionReceptor(cliente, dto.condicionIvaReceptor());

        // 2) Letra: la que eligió el usuario o, si no eligió, la que corresponde
        LetraComprobante letra = dto.letra() != null ? dto.letra() : letraResolver.sugerir(condicion);
        letraResolver.validar(letra, !cliente.esConsumidorFinal());

        // 3) Renglones y lista de precios
        Map<Long, ListaPrecioArticulo> precios = cargarPrecios(dto.detalles());
        ListaPrecio lista = resolverListaPrecio(dto.listaPrecioId(), dto.detalles(), precios);

        factura.setCliente(cliente);
        factura.setCondicionFiscalReceptor(condicion);
        factura.setLetra(letra);
        factura.setTipoComprobante(letra.getCodigoArca());
        factura.setPuntoVenta(buscar(puntoVentaRepository, dto.puntoVentaId(), "PuntoVenta"));
        factura.setTipoMoneda(buscar(tipoMonedaRepository, dto.tipoMonedaId(), "TipoMoneda"));
        factura.setListaPrecio(lista);
        factura.setFecha(dto.fecha() != null ? dto.fecha() : LocalDate.now());
        factura.setFechaEmision(dto.fechaEmision());
        factura.setObservaciones(dto.observaciones());

        // Documento que se informa a ARCA: CUIT si el cliente lo tiene; si no, "sin identificar"
        if (cliente.esConsumidorFinal()) {
            factura.setDocTipo(DOC_SIN_IDENTIFICAR);
            factura.setDocNro(0L);
        } else {
            factura.setDocTipo(DOC_CUIT);
            factura.setDocNro(Long.parseLong(cliente.getCuit()));
        }

        factura.setDetalles(construirDetalles(dto.detalles(), precios, lista));
        recalcularImportes(factura);
    }

    // =====================================================================
    // EMITIR: pedir el CAE a ARCA (A/B/C) o numerar internamente (X)
    // =====================================================================

    /**
     * Este método NO es @Transactional a propósito. Hace tres pasos cortos:
     *   1) transacción: validar el borrador y armar el pedido para ARCA
     *   2) SIN transacción: llamar a ARCA (puede tardar varios segundos)
     *   3) transacción: guardar el CAE y cerrar la factura
     * Así no se retiene una conexión a la base mientras se espera a ARCA, y un error de ARCA
     * deja el borrador intacto para corregirlo y reintentar.
     */
    public FacturaReporteDTO emitir(Long id) {
        if (!emitiendo.add(id)) {
            throw conflicto("La factura " + id + " ya se está emitiendo. Esperá a que termine.");
        }
        try {
            boolean esFiscal = transaccion.execute(estado -> {
                FacturaVenta factura = buscarFactura(id);
                exigirBorrador(factura, "emitirse otra vez");
                return factura.getLetra().esFiscal();
            });

            if (!esFiscal) {
                return transaccion.execute(estado -> emitirInterna(id));
            }

            SolicitudCae solicitud = transaccion.execute(estado -> armarSolicitud(buscarFactura(id)));

            WsfeService.ResultadoCae resultado = pedirCae(solicitud);
            return transaccion.execute(estado -> registrarCae(id, solicitud, resultado));
        } finally {
            emitiendo.remove(id);
        }
    }

    /** Llama a ARCA. Si rechaza o no responde, el borrador queda intacto para corregir y reintentar. */
    private WsfeService.ResultadoCae pedirCae(SolicitudCae solicitud) {
        try {
            return wsfeService.autorizar(solicitud);
        } catch (RuntimeException e) {
            // 502: el problema está del lado de ARCA (rechazo o caída)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ARCA no autorizó el comprobante: " + e.getMessage(), e);
        }
    }

    /** Factura X: no va a ARCA. El sistema le da el número siguiente de ese punto de venta. */
    private FacturaReporteDTO emitirInterna(Long id) {
        FacturaVenta factura = buscarFactura(id);
        // Bloquea el punto de venta hasta el commit: dos emisiones simultáneas no pueden repetir número
        PuntoVenta puntoVenta = puntoVentaRepository.buscarBloqueando(factura.getPuntoVenta().getId())
                .orElseThrow(() -> new ResourceNotFoundException("PuntoVenta", factura.getPuntoVenta().getId()));

        long numero = facturaRepository.ultimoNumero(puntoVenta.getId(), factura.getLetra()) + 1;
        numerar(factura, puntoVenta.getNumero(), numero);
        if (factura.getFechaEmision() == null) {
            factura.setFechaEmision(LocalDate.now());
        }
        factura.setEstado(EstadoFactura.EMITIDA);
        return FacturaMapper.toReporte(facturaRepository.save(factura));
    }

    /** Convierte la factura en el pedido plano que entiende WsfeService. */
    private SolicitudCae armarSolicitud(FacturaVenta factura) {
        if (!MONEDA_PESOS.equals(factura.getTipoMoneda().getCodigo())) {
            throw new IllegalArgumentException(
                    "Por ahora solo se pueden informar a ARCA comprobantes en pesos (ARS).");
        }

        List<SolicitudCae.Alicuota> alicuotas = new ArrayList<>();
        if (letraResolver.discriminaIva()) {
            basesPorAlicuota(factura).forEach((porcentaje, base) ->
                    alicuotas.add(new SolicitudCae.Alicuota(porcentaje, base, iva(base, porcentaje))));
        }

        return new SolicitudCae(
                factura.getPuntoVenta().getNumero(),
                factura.getTipoComprobante(),
                factura.getDocTipo(),
                factura.getDocNro(),
                factura.getCondicionFiscalReceptor().getCodigoArca(),
                factura.getFechaEmision() != null ? factura.getFechaEmision() : LocalDate.now(),
                factura.getImporteNeto(),
                factura.getImporteIva(),
                factura.getImporteTotal(),
                alicuotas);
    }

    /** ARCA aprobó: se guardan número y CAE, y la factura pasa a ser de solo lectura. */
    private FacturaReporteDTO registrarCae(Long id, SolicitudCae solicitud, WsfeService.ResultadoCae resultado) {
        FacturaVenta factura = buscarFactura(id);
        numerar(factura, solicitud.puntoVenta(), resultado.getNumeroComprobante());
        factura.setCae(resultado.getCae());
        factura.setFechaVtoCae(parsearFechaArca(resultado.getFechaVencimiento()));
        factura.setFechaEmision(solicitud.fechaComprobante());
        factura.setEstado(EstadoFactura.EMITIDA);
        return FacturaMapper.toReporte(facturaRepository.save(factura));
    }

    private void numerar(FacturaVenta factura, int puntoVenta, long numero) {
        factura.setNumeroComprobante(numero);
        factura.setNroFactura(String.format("%04d-%08d", puntoVenta, numero));
    }

    // =====================================================================
    // ANULACIÓN Y ELIMINACIÓN
    // =====================================================================

    /**
     * Anulación lógica: la factura queda guardada y marcada como ANULADA.
     * (Fiscalmente, una factura con CAE se revierte además con una Nota de Crédito; eso todavía no está hecho.)
     */
    @Transactional
    public FacturaReporteDTO anular(Long id) {
        FacturaVenta factura = buscarFactura(id);

        if (factura.getEstado() == EstadoFactura.ANULADA) {
            throw conflicto("La factura " + id + " ya está anulada");
        }
        if (factura.esBorrador()) {
            throw conflicto("La factura " + id + " es un borrador: no se anula, se elimina.");
        }

        factura.setEstado(EstadoFactura.ANULADA);
        factura.setFechaAnulacion(new Date());
        return FacturaMapper.toReporte(facturaRepository.save(factura));
    }

    @Transactional
    public void eliminar(Long id) {
        FacturaVenta factura = buscarFactura(id);
        exigirBorrador(factura, "eliminarse (solo puede anularse)");
        facturaRepository.delete(factura);
    }

    // =====================================================================
    // DASHBOARD
    // =====================================================================

    @Transactional(readOnly = true)
    public BigDecimal calcularTotalFacturadoPorMes(int mes, int anio) {
        LocalDate desde = LocalDate.of(anio, mes, 1);
        return facturaRepository.totalFacturado(desde, desde.plusMonths(1));
    }

    @Transactional(readOnly = true)
    public long contarComprobantesPorMes(int mes, int anio) {
        LocalDate desde = LocalDate.of(anio, mes, 1);
        return facturaRepository.contarComprobantes(desde, desde.plusMonths(1));
    }

    // =====================================================================
    // Métodos auxiliares
    // =====================================================================

    /**
     * Devuelve el cliente genérico "Consumidor Final" (sin CUIT), creándolo la primera vez.
     * Sirve tanto con la base H2 de prueba como con MySQL, sin depender de un ID fijo.
     */
    @Transactional
    public Cliente obtenerConsumidorFinal() {
        return clienteRepository.findFirstByNombreAndCuitIsNull(Cliente.NOMBRE_CONSUMIDOR_FINAL)
                .orElseGet(() -> clienteRepository.save(
                        new Cliente(Cliente.NOMBRE_CONSUMIDOR_FINAL, null, CondicionFiscal.CONSUMIDOR_FINAL)));
    }

    /** La condición que mandó el formulario o, si no mandó, la que tiene cargada el cliente. */
    private CondicionFiscal resolverCondicionReceptor(Cliente cliente, CondicionFiscal pedida) {
        CondicionFiscal condicion = pedida != null ? pedida : cliente.getCondicionFiscal();
        if (condicion == null) {
            // Cliente viejo sin el dato: lo único seguro de asumir es Consumidor Final
            condicion = CondicionFiscal.CONSUMIDOR_FINAL;
        }
        if (cliente.esConsumidorFinal() && condicion != CondicionFiscal.CONSUMIDOR_FINAL) {
            throw new IllegalArgumentException("Un cliente sin CUIT solo puede facturarse como Consumidor Final.");
        }
        return condicion;
    }

    /** Trae todos los precios de los renglones en una sola consulta y verifica que existan. */
    private Map<Long, ListaPrecioArticulo> cargarPrecios(List<FacturaDetalleRequestDTO> detalles) {
        Set<Long> ids = detalles.stream().map(FacturaDetalleRequestDTO::articuloId).collect(Collectors.toSet());
        Map<Long, ListaPrecioArticulo> precios = listaPrecioArticuloRepository.buscarConArticuloYLista(ids).stream()
                .collect(Collectors.toMap(ListaPrecioArticulo::getId, Function.identity()));

        for (Long id : ids) {
            if (!precios.containsKey(id)) {
                throw new ResourceNotFoundException("ListaPrecioArticulo", id);
            }
        }
        return precios;
    }

    /** La lista indicada en el formulario o, si no vino, la del primer renglón. */
    private ListaPrecio resolverListaPrecio(Long listaPrecioId,
                                            List<FacturaDetalleRequestDTO> detalles,
                                            Map<Long, ListaPrecioArticulo> precios) {
        if (listaPrecioId != null) {
            return buscar(listaPrecioRepository, listaPrecioId, "ListaPrecio");
        }
        ListaPrecio lista = precios.get(detalles.get(0).articuloId()).getListaPrecio();
        if (lista == null) {
            throw new IllegalArgumentException("El artículo del primer renglón no tiene lista de precios asignada.");
        }
        return lista;
    }

    private List<FacturaVentaDetalle> construirDetalles(List<FacturaDetalleRequestDTO> dtos,
                                                        Map<Long, ListaPrecioArticulo> precios,
                                                        ListaPrecio lista) {
        boolean discriminaIva = letraResolver.discriminaIva();
        List<FacturaVentaDetalle> detalles = new ArrayList<>();

        for (FacturaDetalleRequestDTO dto : dtos) {
            ListaPrecioArticulo precio = precios.get(dto.articuloId());
            Articulo articulo = precio.getArticulo();

            // Toda la factura trabaja con UNA lista: no se mezclan precios mayoristas y minoristas
            if (precio.getListaPrecio() == null || !precio.getListaPrecio().getId().equals(lista.getId())) {
                throw new IllegalArgumentException("El precio elegido para «" + articulo.getDescripcion()
                        + "» no pertenece a la lista " + lista.getNombre() + ".");
            }

            // El precio SIEMPRE sale de la base, nunca de lo que mande el navegador
            BigDecimal subtotal = precio.getPrecio().multiply(dto.cantidad()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal alicuota = !discriminaIva ? BigDecimal.ZERO.setScale(2)
                    : articulo.getAlicuotaIva() != null ? articulo.getAlicuotaIva().setScale(2, RoundingMode.HALF_UP)
                    : ALICUOTA_GENERAL;

            FacturaVentaDetalle detalle = new FacturaVentaDetalle();
            detalle.setListaPrecioArticulo(precio);
            detalle.setCantidad(dto.cantidad());
            detalle.setPrecioUnitario(precio.getPrecio());
            detalle.setSubtotal(subtotal);
            detalle.setAlicuotaIva(alicuota);
            detalle.setImporteIva(iva(subtotal, alicuota));
            // Datos "congelados": si mañana el artículo cambia de nombre o código, la factura no cambia
            detalle.setCodigoArticulo(articulo.getCodigo());
            detalle.setDescripcion(articulo.getDescripcion());
            detalles.add(detalle);
        }
        return detalles;
    }

    /**
     * Neto = suma de subtotales. IVA = se calcula POR ALÍCUOTA sobre la base total de esa alícuota
     * (no sumando el IVA redondeado de cada renglón): es exactamente la cuenta que ARCA verifica.
     */
    private void recalcularImportes(FacturaVenta factura) {
        BigDecimal neto = BigDecimal.ZERO.setScale(2);
        BigDecimal iva = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<BigDecimal, BigDecimal> grupo : basesPorAlicuota(factura).entrySet()) {
            neto = neto.add(grupo.getValue());
            iva = iva.add(iva(grupo.getValue(), grupo.getKey()));
        }
        factura.setImporteNeto(neto);
        factura.setImporteIva(iva);
        factura.setImporteTotal(neto.add(iva));
    }

    /** Agrupa los renglones por alícuota: { 10.50 -> base, 21.00 -> base }. */
    private static SortedMap<BigDecimal, BigDecimal> basesPorAlicuota(FacturaVenta factura) {
        SortedMap<BigDecimal, BigDecimal> bases = new TreeMap<>();
        for (FacturaVentaDetalle detalle : factura.getDetalles()) {
            bases.merge(detalle.getAlicuotaIva(), detalle.getSubtotal(), BigDecimal::add);
        }
        return bases;
    }

    private static BigDecimal iva(BigDecimal base, BigDecimal porcentaje) {
        return base.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    /** ARCA devuelve las fechas como "yyyyMMdd". */
    private static LocalDate parsearFechaArca(String fecha) {
        return (fecha == null || fecha.isBlank()) ? null : LocalDate.parse(fecha.trim(), DateTimeFormatter.BASIC_ISO_DATE);
    }

    private void exigirBorrador(FacturaVenta factura, String accion) {
        if (factura.esBorrador()) return;
        String motivo = factura.getEstado() == EstadoFactura.ANULADA ? "está anulada"
                : factura.tieneCae() ? "ya tiene CAE otorgado por ARCA"
                : "ya fue emitida";
        throw conflicto("La factura " + factura.getId() + " " + motivo + " y no puede " + accion + ".");
    }

    private void exigirEditable(FacturaVenta factura) {
        if (factura.esEditable()) return;
        String motivo = factura.getEstado() == EstadoFactura.ANULADA ? "está anulada"
                : "ya tiene CAE otorgado por ARCA";
        throw conflicto("La factura " + factura.getId() + " " + motivo + " y no puede modificarse.");
    }

    private FacturaVenta buscarFactura(Long id) {
        return facturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FacturaVenta", id));
    }

    private ResponseStatusException conflicto(String mensaje) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }

    private <T> T buscar(JpaRepository<T, Long> repo, Long id, String nombreEntidad) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(nombreEntidad, id));
    }
}
