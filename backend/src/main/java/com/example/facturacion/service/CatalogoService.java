package com.example.facturacion.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.facturacion.dto.PuntoVentaDTO;
import com.example.facturacion.dto.TipoMonedaDTO;
import com.example.facturacion.model.PuntoVenta;
import com.example.facturacion.model.TipoMoneda;
import com.example.facturacion.repository.PuntoVentaRepository;
import com.example.facturacion.repository.TipoMonedaRepository;

/** Opciones fijas de los desplegables del formulario (punto de venta y moneda). */
@Service
public class CatalogoService {

    private final PuntoVentaRepository puntoVentaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;

    public CatalogoService(PuntoVentaRepository puntoVentaRepository,
                           TipoMonedaRepository tipoMonedaRepository) {
        this.puntoVentaRepository = puntoVentaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
    }

    /**
     * Quita las opciones repetidas (misma clave natural) quedándose con la de menor ID, y conserva el orden.
     * Sirve de red de seguridad si la base ya tiene filas duplicadas de ejecuciones anteriores.
     */
    private static <T> List<T> sinRepetidos(List<T> items, Function<T, Object> clave, Function<T, Long> id) {
        Map<Object, T> unicos = new LinkedHashMap<>();
        for (T item : items) {
            unicos.merge(clave.apply(item), item, (a, b) -> id.apply(a) <= id.apply(b) ? a : b);
        }
        return List.copyOf(unicos.values());
    }

    private static String norm(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase();
    }

    /** Son pocas filas fijas: se devuelven todas (sin repetidos). */
    @Transactional(readOnly = true)
    public List<PuntoVentaDTO> listarPuntosVenta() {
        return sinRepetidos(
                puntoVentaRepository.findAll().stream()
                        .sorted(Comparator.comparing(PuntoVenta::getId))
                        .map(p -> new PuntoVentaDTO(p.getId(), p.getNumero(), p.getDescripcion()))
                        .toList(),
                PuntoVentaDTO::numero, PuntoVentaDTO::id);
    }

    @Transactional(readOnly = true)
    public List<TipoMonedaDTO> listarMonedas() {
        return sinRepetidos(
                tipoMonedaRepository.findAll().stream()
                        .sorted(Comparator.comparing(TipoMoneda::getId))
                        .map(m -> new TipoMonedaDTO(m.getId(), m.getCodigo(), m.getDescripcion()))
                        .toList(),
                m -> norm(m.codigo()), TipoMonedaDTO::id);
    }
}
