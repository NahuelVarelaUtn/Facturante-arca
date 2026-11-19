# Facturación de ventas con ARCA (Spring Boot + TypeScript)

Sistema de facturación que guarda borradores, los autoriza en ARCA (ex AFIP) por WSFE para obtener el CAE,
e imprime el comprobante en PDF. Pensado para catálogos grandes: clientes y artículos nunca se cargan
completos, se buscan desde un explorer paginado.

```
facturacion-tp/
├── backend/    API REST — Spring Boot 3.4, Java 17+, JPA, MySQL
├── frontend/   TypeScript sin framework + HTML/CSS (componentes reutilizables)
└── tools/      generador de datos de demostración
```

## Funcionalidades

- **Borrador y facturación separados**: "Guardar borrador" no habla con ARCA; "Facturar" pide el CAE.
- **Solo lectura con CAE**: una factura autorizada no se puede editar ni eliminar, solo anular.
- **El número lo asigna ARCA** junto con el CAE. La única excepción es la Factura X.
- **Factura X** (interna: preventa o prueba): no llama a ARCA, se numera sola, sigue siendo editable y se
  puede **convertir en Factura A/B** para emitirla oficialmente y obtener su CAE.
- **Consumidor Final por defecto**: una factura nueva arranca lista para operar; la condición frente al IVA
  se cambia desde su desplegable.
- **Letra automática y editable** (A, B, C, X) según la condición frente al IVA de emisor y receptor.
- **IVA por artículo**: cada artículo tiene su alícuota y a ARCA se informa un subtotal por alícuota.
- **Lista de precios global** por factura: MAYORISTA o MINORISTA.
- **Explorers de clientes y artículos**: sin carga inicial, búsqueda desde 1 carácter con espera de 300 ms,
  filtros y paginación en el servidor (hasta 100 resultados por página). Enter o "Buscar" con todo vacío
  lista todos los registros, los más recientes primero.

## 1. Backend

Requisitos: JDK 17 o superior, Maven y MySQL.

1. Crear la base: `CREATE DATABASE facturacion_db;`
2. Revisar usuario y contraseña de MySQL en `backend/src/main/resources/application.properties`.
3. Ejecutar: `cd backend && mvn spring-boot:run` (o correr `FacturacionApplication` desde el IDE).

Queda en `http://localhost:8080`. Las tablas se crean solas (`ddl-auto=update`) y, con
`app.cargar-datos-prueba=true`, se cargan puntos de venta, monedas, listas de precios y los datos de prueba.

> Si venís de una versión anterior del esquema, recreá la base (`DROP DATABASE` + `CREATE DATABASE`).

### Datos de prueba: 100 clientes y 100 artículos de tecnología

Con `app.cargar-datos-prueba=true` (viene así), al arrancar el backend el seeder `CargadorDatosDemo`
lee dos CSV y carga en bloque lo que falte:

| Archivo | Contenido |
|---|---|
| `backend/src/main/resources/seed/clientes.csv` | 100 clientes: razón social, CUIT, condición frente al IVA, domicilio, localidad y provincia |
| `backend/src/main/resources/seed/articulos.csv` | 100 artículos de tecnología e informática: SKU, descripción, categoría, unidad, alícuota de IVA y precio minorista y mayorista en pesos |

- **Clientes**: 84 con CUIT/CUIL de dígito verificador válido (49 Responsables Inscriptos, 21 Monotributistas,
  8 Exentos y 6 Consumidores Finales identificados) y 16 de relleno ("Consumidor Final" genérico, 13 consumidores
  finales sin CUIT y 2 con el dígito verificador mal a propósito, para probar el rechazo).
- **Ojo con "válido"**: 9 son empresas y organismos conocidos; los otros 75 son ficticios, generados al azar
  con el dígito verificador correcto. No se sabe si figuran en el padrón de ARCA, así que ARCA puede rechazar
  una Factura A emitida a uno de ellos. Para facturar de verdad, reemplazá esas filas del CSV por clientes
  verificados en el padrón.
- **Artículos**: 9 categorías (notebooks y PC, componentes, almacenamiento, monitores, periféricos, audio y
  video, conectividad, impresión y móviles). Las alícuotas (10,5 % y 21 %) son de referencia.
- **Sin duplicados**: un cliente se identifica por su CUIT (o su nombre, si no tiene) y un artículo por su
  código. Se puede arrancar las veces que haga falta.
- **Cantidades exactas**: sobre una base recién creada quedan exactamente 100 y 100. Si la base ya tenía otros
  clientes o artículos, se suman a esos.
- **Cambiar los datos**: editar los CSV (campos separados por `;`) o regenerarlos con
  `python tools/generar_datos_demo.py`, que verifica cantidades y dígitos verificadores antes de escribir.

### Configuración de ARCA (`application.properties`)

| Propiedad | Para qué |
|---|---|
| `afip.cuit` | CUIT del emisor |
| `afip.condicion-iva-emisor` | `RESPONSABLE_INSCRIPTO` (emite A o B) · `MONOTRIBUTO` o `EXENTO` (emiten C) |
| `afip.cert.path` / `afip.key.path` | Certificado y clave privada de homologación |
| `afip.wsaa.url` / `afip.wsfe.url` | Servicios de autenticación y de facturación (homologación) |

## 2. Frontend

```
cd frontend
npm install        # una sola vez (instala TypeScript)
npm run build      # compila src/ -> dist/   (npm run watch recompila al guardar)
npm start          # sirve la carpeta en http://localhost:5500
```

Abrir `http://localhost:5500`. Hay que servirlo por HTTP (no abrir el .html con doble clic):
los módulos ES no cargan desde `file://`.

### Estructura

```
frontend/
├── index.html                página: métricas, listado y botón "Nueva factura"
├── styles/app.css
└── src/
    ├── app.ts                punto de entrada: crea los componentes y los conecta
    ├── api/                  única capa que usa fetch (http.ts) + un módulo por recurso
    ├── components/
    │   ├── Explorer.ts           modal genérico de búsqueda (debounce, filtros, estados, paginación)
    │   ├── ClientExplorer.ts     explorer de clientes
    │   ├── ArticleExplorer.ts    explorer de artículos
    │   ├── PriceListSelector.ts  selector MAYORISTA / MINORISTA
    │   ├── InvoiceForm.ts        formulario de factura (nueva, editar borrador, ver emitida)
    │   ├── InvoiceList.ts        listado con filtros, paginación y acciones
    │   └── Toast.ts              avisos
    ├── utils/                debounce, formato, ayudas de DOM, PDF
    └── models/               tipos espejo de los DTOs del backend
```

## 3. API

| Método | URL | Respuesta |
|---|---|---|
| GET | `/api/facturas?nroFactura=&fechaDesde=&fechaHasta=&clienteId=&informadaArca=&estado=&page=0&size=10` | 200 + página de facturas (sin renglones) |
| GET | `/api/facturas/{id}` | 200 + factura completa (con renglones) |
| GET | `/api/facturas/letra?condicionIva=MONOTRIBUTO` | 200 + `{ letra, letrasPermitidas, condicionIvaEmisor, discriminaIva }` |
| POST | `/api/facturas` | 201. **Guarda un BORRADOR** (no se comunica con ARCA) |
| PUT | `/api/facturas/{id}` | 200. Actualiza un borrador o una Factura X (409 si tiene CAE o está anulada). Cambiar la letra de una X emitida por A/B/C la convierte en borrador fiscal |
| POST | `/api/facturas/{id}/emitir` | 200 con número y CAE (409 si no es borrador; 502 si ARCA la rechaza) |
| PUT | `/api/facturas/{id}/anular` | 200 (anulación lógica; solo facturas EMITIDAS) |
| DELETE | `/api/facturas/{id}` | 204 (solo BORRADORES; 409 en otro caso) |
| GET | `/api/facturas/{id}/pdf` | 200 + `application/pdf` |
| GET | `/api/facturas/dashboard` | 200 + métricas del mes |
| GET | `/api/clientes?page=1&limit=100&search=&id=&cuit=&condicionIva=` | 200 + página (sin criterios lista todos, por ID descendente) |
| GET | `/api/clientes/consumidor-final` | 200 + cliente genérico para ventas sin identificar |
| GET | `/api/articulos?page=1&limit=100&search=&codigo=&id=&listaPrecioId=` | 200 + página (sin `search`, `codigo` ni `id` lista todos, por ID descendente) |
| GET | `/api/listas-precio` | 200 + lista (MAYORISTA / MINORISTA) |
| GET | `/api/puntos-venta` · `/api/monedas` | 200 + lista (desplegables) |

### Paginación

Todos los listados se paginan en el servidor (`LIMIT` en la consulta SQL); el navegador nunca recibe más de
100 filas por pedido.

| Listado | Parámetros | Metadatos de la respuesta |
|---|---|---|
| Facturas | `page` (desde 0), `size` (por defecto 10, máximo 100) | `page: { number, size, totalElements, totalPages }` |
| Clientes y artículos | `page` (desde 1), `limit` (por defecto y máximo 100) | `page, limit, totalElements, totalPages, hasNext` |

El orden lo fija el servidor y siempre termina en el ID, para que sea estable entre páginas.

Errores: 400 (datos inválidos), 404 (no existe), 409 (regla de negocio), 502 (rechazo de ARCA).
Todos devuelven `{ timestamp, status, error, message }`.

### Ciclo de vida de la factura

`BORRADOR` → `EMITIDA` → `ANULADA`

| Acción | Se permite si... | Si no, responde |
|---|---|---|
| Guardar / actualizar | no tiene CAE y no está anulada (borradores y Facturas X) | 409 |
| Emitir | es `BORRADOR` | 409 |
| Eliminar (físico) | es `BORRADOR` | 409 |
| Anular (lógico) | es `EMITIDA` | 409 |

**Factura X.** Al emitirla queda `EMITIDA` con un número interno por punto de venta, sin pasar por ARCA,
y sigue siendo editable. Al convertirla (se le cambia la letra por A, B o C) pierde ese número, vuelve a
`BORRADOR` y se emite como cualquier factura fiscal: ARCA le asigna número y CAE.

### Letra del comprobante

| Emisor | Receptor | Letra |
|---|---|---|
| Responsable Inscripto | Responsable Inscripto o Monotributo | A |
| Responsable Inscripto | Consumidor Final o Exento | B |
| Monotributo o Exento | cualquiera | C |
| cualquiera | (la elige el usuario) | X — interna, no se informa a ARCA |

## Pendientes conocidos

- La anulación es lógica: todavía no emite la Nota de Crédito ante ARCA.
- Solo se informan a ARCA comprobantes en pesos.
- Los datos del emisor que imprime el PDF están fijos en `FacturaPdfService`.
- Antes de publicar el repositorio: pasar la contraseña de la base y el certificado a variables de entorno.
#   F a c t u r a n t e - a r c a  
 