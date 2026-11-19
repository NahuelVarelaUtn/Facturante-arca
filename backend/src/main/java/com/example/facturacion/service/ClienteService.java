package com.example.facturacion.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.facturacion.dto.ClienteDTO;
import com.example.facturacion.dto.PageResponse;
import com.example.facturacion.model.CondicionFiscal;
import com.example.facturacion.repository.ClienteRepository;
import com.example.facturacion.util.Cuit;
import com.example.facturacion.util.Like;
import com.example.facturacion.util.Paginacion;

/** Explorer de clientes: búsqueda paginada. Nunca devuelve más de una página (100 filas) por pedido. */
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteService(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    /**
     * @param search   texto libre: si son solo dígitos/guiones busca por prefijo de CUIT; si no, por nombre
     * @param id       ID exacto
     * @param cuit     prefijo de CUIT (con o sin guiones)
     * @param condicion condición frente al IVA
     * La búsqueda arranca desde 1 carácter. Sin ningún criterio devuelve TODOS los clientes, de a una página
     * (nunca más de 100 filas por pedido), con los más recientes primero (ID descendente).
     */
    @Transactional(readOnly = true)
    public PageResponse<ClienteDTO> buscar(String search, Long id, String cuit, CondicionFiscal condicion,
                                           Paginacion paginacion) {
        String texto = search == null ? "" : search.trim();
        boolean textoEsCuit = texto.matches("[\\d-]+");

        String cuitTexto = textoEsCuit ? Cuit.normalizar(texto) : null;
        String nombre = (!textoEsCuit && !texto.isEmpty()) ? Like.escapar(texto.toLowerCase()) : null;
        String cuitFiltro = Cuit.normalizar(cuit);

        boolean sinCriterios = id == null && condicion == null && cuitFiltro == null
                && nombre == null && cuitTexto == null;
        return PageResponse.de(
                clienteRepository.buscar(id, condicion, cuitFiltro, nombre, cuitTexto, sinCriterios,
                        paginacion.toPageable()),
                paginacion);
    }
}
