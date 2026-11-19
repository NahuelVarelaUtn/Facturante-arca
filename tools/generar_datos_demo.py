#!/usr/bin/env python3
"""
Generador de datos de prueba para el sistema de facturación.

Crea dos archivos CSV que el backend carga solo al arrancar (ver config/CargadorDatosDemo.java):
    backend/src/main/resources/seed/clientes.csv    -> exactamente 100 clientes
    backend/src/main/resources/seed/articulos.csv   -> exactamente 100 artículos de tecnología e informática

Uso (desde la raíz del proyecto):
    python tools/generar_datos_demo.py

NO hace falta ejecutarlo para usar el sistema: los CSV ya vienen generados en el repositorio.
Sirve para regenerarlos si se quiere cambiar el contenido.

CLIENTES (100):
  - 84 con CUIT/CUIL de dígito verificador VÁLIDO (el script lo comprueba uno por uno):
      *  9 son empresas y organismos conocidos (CUIT públicos, heredados de los datos base del proyecto).
      * 75 son FICTICIOS: el número se genera al azar y solo se garantiza el formato y el dígito verificador.
        No se sabe si figuran en el padrón de ARCA, así que ARCA puede rechazar una Factura A emitida
        a uno de ellos. Cualquier coincidencia con una persona o empresa real es casual.
  - 16 de relleno: "Consumidor Final" genérico, 13 consumidores finales sin CUIT y 2 clientes con el dígito
    verificador MAL a propósito (sirven para probar el rechazo).

La semilla del azar es fija: cada ejecución produce exactamente los mismos datos.
"""
import random
from pathlib import Path

SEMILLA = 2026
DESTINO = Path(__file__).resolve().parent.parent / "backend" / "src" / "main" / "resources" / "seed"

TOTAL_CLIENTES = 100
MINIMO_CON_CUIT_VALIDO = 80
TOTAL_ARTICULOS = 100

# Clientes FICTICIOS con CUIT válido que se generan, por condición frente al IVA (total: 73)
GENERADOS = {"RESPONSABLE_INSCRIPTO": 40, "MONOTRIBUTO": 20, "EXENTO": 7, "CONSUMIDOR_FINAL": 6}
# Consumidores finales sin CUIT (relleno)
SIN_CUIT = 13

# Empresas y organismos conocidos: (razón social, CUIT, condición, domicilio, localidad, provincia).
# Domicilios de referencia: verificarlos antes de usarlos en algo que no sea una prueba.
CABA = "Ciudad Autónoma de Buenos Aires"
CONOCIDOS = [
    ("YPF S.A.", "30-54668997-9", "RESPONSABLE_INSCRIPTO", "Macacha Güemes 515", "Buenos Aires", CABA),
    ("Banco de la Nación Argentina", "30-50001091-2", "RESPONSABLE_INSCRIPTO", "Bartolomé Mitre 326", "Buenos Aires", CABA),
    ("Mercado Libre S.R.L.", "30-70308853-4", "RESPONSABLE_INSCRIPTO", "Av. Caseros 3039", "Buenos Aires", CABA),
    ("Telecom Argentina S.A.", "30-63945373-8", "RESPONSABLE_INSCRIPTO", "General Hornos 690", "Buenos Aires", CABA),
    ("Arcor S.A.I.C.", "30-50279317-5", "RESPONSABLE_INSCRIPTO", "Av. Fulvio Pagani 487", "Arroyito", "Córdoba"),
    ("Banco de Galicia y Buenos Aires S.A.U.", "30-50000173-5", "RESPONSABLE_INSCRIPTO", "Tte. Gral. Juan D. Perón 430", "Buenos Aires", CABA),
    ("Aerolíneas Argentinas S.A.", "30-64140555-4", "RESPONSABLE_INSCRIPTO", "Av. Rafael Obligado s/n", "Buenos Aires", CABA),
    ("Correo Oficial de la República Argentina S.A.", "30-70857483-6", "RESPONSABLE_INSCRIPTO", "Brandsen 2070", "Buenos Aires", CABA),
    ("Administración Federal de Ingresos Públicos (ARCA)", "33-69345023-9", "EXENTO", "Hipólito Yrigoyen 370", "Buenos Aires", CABA),
]
# Clientes de prueba que ya traía el proyecto. Los dos primeros tienen dígito verificador correcto;
# los dos últimos lo tienen MAL a propósito, para probar el rechazo.
DE_PRUEBA_VALIDOS = [
    ("Ferretería Los Andes S.R.L. (CUIT sin verificar)", "30-71234567-1", "RESPONSABLE_INSCRIPTO", "San Martín 1450", "Mendoza", "Mendoza"),
    ("Servicios Patagonia S.A. (CUIT sin verificar)", "30-70123456-8", "MONOTRIBUTO", "Av. Roca 820", "Neuquén", "Neuquén"),
]
DE_PRUEBA_INVALIDOS = [
    ("Comercial del Sur S.A. (CUIT inválido)", "30-71234567-8", "RESPONSABLE_INSCRIPTO", "Belgrano 230", "Bahía Blanca", "Buenos Aires"),
    ("Distribuidora Pampa S.R.L. (CUIT inválido)", "30-12345678-0", "MONOTRIBUTO", "Av. San Martín 77", "Santa Rosa", "La Pampa"),
]
# Cliente genérico para ventas sin identificar (mismo nombre que usa el backend: Cliente.NOMBRE_CONSUMIDOR_FINAL)
CONSUMIDOR_FINAL_GENERICO = ("Consumidor Final", "", "CONSUMIDOR_FINAL", "", "", "")

# ----------------------------------------------------------------------------
# CUIT / CUIL
# ----------------------------------------------------------------------------
PESOS_CUIT = (5, 4, 3, 2, 7, 6, 5, 4, 3, 2)


def digito_verificador(diez_digitos: str):
    """Dígito verificador de un CUIT (módulo 11). Devuelve None si esa combinación no admite dígito."""
    resto = sum(int(d) * p for d, p in zip(diez_digitos, PESOS_CUIT)) % 11
    dv = 11 - resto
    if dv == 11:
        return 0
    return None if dv == 10 else dv


def cuit_valido(cuit: str) -> bool:
    digitos = cuit.replace("-", "")
    return len(digitos) == 11 and digitos.isdigit() and digito_verificador(digitos[:10]) == int(digitos[10])


def generar_cuit(azar: random.Random, prefijos, usados: set) -> str:
    """CUIT al azar con dígito verificador correcto, sin repetir. Formato XX-XXXXXXXX-X."""
    while True:
        prefijo = azar.choice(prefijos)
        # Empresas: números de inscripción "altos"; personas: rango de DNI
        numero = azar.randint(50_000_000, 71_999_999) if prefijo in ("30", "33") else azar.randint(14_000_000, 45_999_999)
        base = f"{prefijo}{numero:08d}"
        dv = digito_verificador(base)
        cuit = None if dv is None else f"{prefijo}-{numero:08d}-{dv}"
        if cuit and cuit not in usados:
            usados.add(cuit)
            return cuit


# ----------------------------------------------------------------------------
# Clientes
# ----------------------------------------------------------------------------
RUBROS = ["Distribuidora", "Ferretería", "Supermercado", "Transportes", "Constructora", "Agropecuaria", "Librería",
          "Panificadora", "Metalúrgica", "Farmacia", "Corralón", "Estudio Contable", "Electricidad", "Frigorífico",
          "Maderera", "Textil", "Imprenta", "Autopartes", "Bodega", "Lácteos", "Pinturería", "Sanitarios"]
NOMBRES_FANTASIA = ["Del Sur", "San Martín", "Los Andes", "El Progreso", "La Esperanza", "Del Litoral", "Cuyo",
                    "Pampa Húmeda", "Río Paraná", "Aconcagua", "Don Pedro", "Las Heras", "Belgrano", "Güemes",
                    "El Ceibo", "Del Centro", "La Unión", "Norte Grande", "Patagonia", "El Trébol", "Santa Rosa",
                    "Los Álamos", "Del Valle", "La Estrella"]
SOCIEDADES = ["S.A.", "S.R.L.", "S.A.S."]
ENTIDADES = ["Asociación Cooperadora Escuela N° {n}", "Fundación {f}", "Club Social y Deportivo {f}",
             "Asociación Mutual {f}", "Biblioteca Popular {f}", "Asociación Civil {f}",
             "Sociedad de Bomberos Voluntarios de {l}", "Cooperativa de Trabajo {f} Ltda."]
NOMBRES_M = ["Juan", "Carlos", "Luis", "Jorge", "Miguel", "Diego", "Martín", "Pablo", "Sergio", "Fernando",
             "Gustavo", "Ricardo", "Matías", "Nicolás", "Facundo", "Lucas", "Agustín", "Hernán", "Javier", "Raúl"]
NOMBRES_F = ["María", "Laura", "Ana", "Silvia", "Patricia", "Claudia", "Mónica", "Carolina", "Florencia", "Lucía",
             "Valeria", "Natalia", "Gabriela", "Romina", "Camila", "Sofía", "Daniela", "Paula", "Verónica", "Julieta"]
APELLIDOS = ["González", "Rodríguez", "Gómez", "Fernández", "López", "Díaz", "Martínez", "Pérez", "García", "Sánchez",
             "Romero", "Sosa", "Torres", "Álvarez", "Ruiz", "Ramírez", "Flores", "Benítez", "Acosta", "Medina",
             "Herrera", "Suárez", "Aguirre", "Giménez", "Gutiérrez", "Pereyra", "Rojas", "Molina", "Castro", "Ortiz"]
CALLES = ["San Martín", "Belgrano", "Rivadavia", "Mitre", "Sarmiento", "9 de Julio", "Av. Colón", "España", "Urquiza",
          "Las Heras", "Moreno", "Alem", "Av. del Libertador", "Lavalle", "25 de Mayo", "Av. Roca", "Güemes", "Italia"]
LOCALIDADES = [("Mendoza", "Mendoza"), ("Godoy Cruz", "Mendoza"), ("San Rafael", "Mendoza"), ("Córdoba", "Córdoba"),
               ("Villa María", "Córdoba"), ("Río Cuarto", "Córdoba"), ("Rosario", "Santa Fe"), ("Santa Fe", "Santa Fe"),
               ("La Plata", "Buenos Aires"), ("Mar del Plata", "Buenos Aires"), ("Bahía Blanca", "Buenos Aires"),
               ("Buenos Aires", "Ciudad Autónoma de Buenos Aires"), ("San Miguel de Tucumán", "Tucumán"),
               ("Salta", "Salta"), ("Neuquén", "Neuquén"), ("San Juan", "San Juan"), ("Paraná", "Entre Ríos"),
               ("Resistencia", "Chaco"), ("Posadas", "Misiones"), ("San Luis", "San Luis"),
               ("San Carlos de Bariloche", "Río Negro"), ("Santa Rosa", "La Pampa")]


def persona(azar: random.Random):
    """Devuelve (nombre "Apellido, Nombre", prefijo de CUIL según el género)."""
    femenino = azar.random() < 0.5
    nombre = azar.choice(NOMBRES_F if femenino else NOMBRES_M)
    segundo = azar.choice(NOMBRES_F if femenino else NOMBRES_M)
    completo = f"{nombre} {segundo}" if segundo != nombre and azar.random() < 0.45 else nombre
    return f"{azar.choice(APELLIDOS)}, {completo}", ("27" if femenino else "20")


def generar_clientes(azar: random.Random):
    fijos = CONOCIDOS + DE_PRUEBA_VALIDOS + DE_PRUEBA_INVALIDOS
    cuits = {fila[1] for fila in fijos}
    nombres = {fila[0] for fila in fijos}
    generados = []

    def agregar(nombre, prefijos, condicion):
        """prefijos = None crea un cliente SIN CUIT (consumidor final no identificado)."""
        if nombre in nombres:
            return False
        nombres.add(nombre)
        localidad, provincia = azar.choice(LOCALIDADES)
        domicilio = f"{azar.choice(CALLES)} {azar.randint(50, 4900)}"
        cuit = generar_cuit(azar, prefijos, cuits) if prefijos else ""
        generados.append([nombre, cuit, condicion, domicilio, localidad, provincia])
        return True

    def repetir(cantidad, crear):
        hechos = 0
        while hechos < cantidad:
            hechos += 1 if crear() else 0

    # Empresas y comercios: Responsables Inscriptos
    repetir(GENERADOS["RESPONSABLE_INSCRIPTO"], lambda: agregar(
        f"{azar.choice(RUBROS)} {azar.choice(NOMBRES_FANTASIA)} {azar.choice(SOCIEDADES)}", ("30", "30", "30", "33"),
        "RESPONSABLE_INSCRIPTO"))

    # Monotributistas: personas (profesionales, oficios, pequeños comercios)
    def monotributista():
        nombre, prefijo = persona(azar)
        return agregar(nombre, (prefijo,), "MONOTRIBUTO")
    repetir(GENERADOS["MONOTRIBUTO"], monotributista)

    # Exentos: entidades sin fines de lucro
    repetir(GENERADOS["EXENTO"], lambda: agregar(
        azar.choice(ENTIDADES).format(n=azar.randint(1, 180), f=azar.choice(NOMBRES_FANTASIA),
                                      l=azar.choice(LOCALIDADES)[0]), ("30",), "EXENTO"))

    # Consumidores finales identificados (personas con CUIL)
    def consumidor_identificado():
        nombre, prefijo = persona(azar)
        return agregar(nombre, (prefijo,), "CONSUMIDOR_FINAL")
    repetir(GENERADOS["CONSUMIDOR_FINAL"], consumidor_identificado)

    # Relleno: consumidores finales sin CUIT
    repetir(SIN_CUIT, lambda: agregar(persona(azar)[0], None, "CONSUMIDOR_FINAL"))

    azar.shuffle(generados)  # mezclados: los ID no quedan agrupados por condición
    # Primero el genérico y los que ya traía el proyecto; después los generados
    return [list(CONSUMIDOR_FINAL_GENERICO)] + [list(fila) for fila in fijos] + generados


# ----------------------------------------------------------------------------
# Artículos: 100 productos de TECNOLOGÍA E INFORMÁTICA
#   (SKU, descripción detallada, unidad de medida, alícuota de IVA %, precio minorista de referencia en pesos, sin IVA)
# Alícuotas de referencia: 10.5 para equipos y componentes informáticos; 21 para el resto.
# Antes de facturar de verdad, confirmá con tu contador la alícuota de cada producto.
# ----------------------------------------------------------------------------
DESCUENTO_MAYORISTA = 0.15  # la lista mayorista es 15% más barata que la minorista

CATEGORIAS = {
    "Notebooks y PC": [
        ("NOT-015", "Notebook 15 pulgadas", "unidad", 10.5, 950000),
        ("NOT-101", "Notebook 15,6\" Core i5 13.ª gen. 16 GB RAM SSD 512 GB", "unidad", 10.5, 1185000),
        ("NOT-102", "Notebook 14\" Ryzen 5 8 GB RAM SSD 256 GB", "unidad", 10.5, 842000),
        ("NOT-103", "Notebook 15,6\" Core i7 16 GB RAM SSD 1 TB", "unidad", 10.5, 1640000),
        ("NOT-104", "Notebook gamer 16\" Ryzen 7 32 GB RAM RTX 4060 SSD 1 TB", "unidad", 10.5, 2390000),
        ("NOT-105", "Notebook 14\" Celeron 4 GB RAM eMMC 128 GB", "unidad", 10.5, 468000),
        ("PCA-101", "PC de escritorio oficina Core i3 8 GB RAM SSD 240 GB", "unidad", 10.5, 612000),
        ("PCA-102", "PC de escritorio gamer Ryzen 5 16 GB RAM RTX 4060 SSD 1 TB", "unidad", 10.5, 1780000),
        ("PCA-103", "Mini PC Core i5 16 GB RAM SSD 512 GB", "unidad", 10.5, 798000),
        ("PCA-104", "All in One 24\" Core i5 8 GB RAM SSD 512 GB", "unidad", 10.5, 1120000),
    ],
    "Componentes de PC": [
        ("CPU-101", "Procesador 6 núcleos 12 hilos 4,2 GHz socket AM4", "unidad", 10.5, 198000),
        ("CPU-102", "Procesador 8 núcleos 16 hilos 4,7 GHz socket AM5", "unidad", 10.5, 412000),
        ("CPU-103", "Procesador 10 núcleos 16 hilos 4,6 GHz socket LGA1700", "unidad", 10.5, 289000),
        ("CPU-104", "Procesador 14 núcleos 20 hilos 5,3 GHz socket LGA1700", "unidad", 10.5, 498000),
        ("MBO-101", "Motherboard micro ATX socket AM4 chipset B550", "unidad", 10.5, 142000),
        ("MBO-102", "Motherboard ATX socket AM5 chipset B650 WiFi", "unidad", 10.5, 268000),
        ("MBO-103", "Motherboard micro ATX socket LGA1700 chipset B760", "unidad", 10.5, 189000),
        ("RAM-101", "Memoria RAM DDR4 8 GB 3200 MHz", "unidad", 10.5, 34900),
        ("RAM-102", "Memoria RAM DDR4 16 GB 3200 MHz", "unidad", 10.5, 62800),
        ("RAM-103", "Memoria RAM DDR5 16 GB 5600 MHz", "unidad", 10.5, 89500),
        ("RAM-104", "Memoria RAM DDR5 32 GB (2 x 16 GB) 6000 MHz", "unidad", 10.5, 176000),
        ("RAM-105", "Memoria RAM SODIMM DDR4 8 GB 3200 MHz para notebook", "unidad", 10.5, 36400),
        ("GPU-101", "Placa de video 8 GB GDDR6 gama media", "unidad", 10.5, 548000),
        ("GPU-102", "Placa de video 12 GB GDDR6 gama alta", "unidad", 10.5, 912000),
        ("GPU-103", "Placa de video 6 GB GDDR6 gama de entrada", "unidad", 10.5, 329000),
        ("FUE-101", "Fuente de alimentación 550 W 80 Plus Bronze", "unidad", 10.5, 78500),
        ("FUE-102", "Fuente de alimentación 750 W 80 Plus Gold modular", "unidad", 10.5, 149000),
        ("GAB-101", "Gabinete mid tower vidrio templado con 3 coolers RGB", "unidad", 10.5, 86000),
        ("GAB-102", "Gabinete micro ATX oficina sin fuente", "unidad", 10.5, 48500),
        ("COO-101", "Cooler para CPU torre con ventilador 120 mm", "unidad", 21, 39800),
        ("COO-102", "Refrigeración líquida 240 mm ARGB", "unidad", 21, 128000),
        ("COO-103", "Pasta térmica jeringa 4 g", "unidad", 21, 8900),
    ],
    "Almacenamiento": [
        ("SSD-101", "Disco SSD 240 GB SATA 2,5\"", "unidad", 10.5, 38900),
        ("SSD-102", "Disco SSD 480 GB SATA 2,5\"", "unidad", 10.5, 61500),
        ("SSD-103", "Disco SSD NVMe M.2 500 GB 3500 MB/s", "unidad", 10.5, 72400),
        ("SSD-104", "Disco SSD NVMe M.2 1 TB 5000 MB/s", "unidad", 10.5, 118000),
        ("SSD-105", "Disco SSD NVMe M.2 2 TB 7000 MB/s", "unidad", 10.5, 236000),
        ("HDD-101", "Disco rígido 1 TB 7200 rpm SATA 3,5\"", "unidad", 10.5, 79800),
        ("HDD-102", "Disco rígido 4 TB 5400 rpm SATA 3,5\"", "unidad", 10.5, 162000),
        ("EXT-101", "Disco externo portátil 1 TB USB 3.0", "unidad", 21, 98700),
        ("EXT-102", "Disco externo portátil 2 TB USB 3.0", "unidad", 21, 139000),
        ("PEN-101", "Pendrive 64 GB USB 3.0", "unidad", 21, 9800),
        ("PEN-102", "Pendrive 128 GB USB 3.2", "unidad", 21, 16400),
        ("MSD-101", "Tarjeta de memoria microSD 128 GB clase 10 con adaptador", "unidad", 21, 17900),
    ],
    "Monitores": [
        ("MON-024", "Monitor 24 pulgadas", "unidad", 10.5, 210000),
        ("MON-101", "Monitor LED 22\" Full HD 75 Hz HDMI y VGA", "unidad", 10.5, 168000),
        ("MON-102", "Monitor IPS 24\" Full HD 100 Hz", "unidad", 10.5, 224000),
        ("MON-103", "Monitor IPS 27\" QHD 165 Hz gamer", "unidad", 10.5, 468000),
        ("MON-104", "Monitor curvo 34\" ultrawide WQHD 144 Hz", "unidad", 10.5, 789000),
        ("MON-105", "Monitor 27\" 4K UHD IPS para diseño", "unidad", 10.5, 612000),
        ("MON-106", "Soporte de escritorio para monitor con brazo articulado", "unidad", 21, 54800),
    ],
    "Periféricos": [
        ("TEC-001", "Teclado mecánico", "unidad", 21, 45000),
        ("MOU-001", "Mouse inalámbrico", "unidad", 21, 18500.50),
        ("TEC-101", "Teclado USB español latino con teclado numérico", "unidad", 21, 12900),
        ("TEC-102", "Teclado mecánico switch rojo retroiluminado RGB 60 %", "unidad", 21, 64800),
        ("TEC-103", "Combo teclado y mouse inalámbricos 2,4 GHz", "unidad", 21, 32400),
        ("MOU-101", "Mouse óptico USB 1000 dpi", "unidad", 21, 6900),
        ("MOU-102", "Mouse gamer 12000 dpi 6 botones RGB", "unidad", 21, 34700),
        ("MOU-103", "Mouse vertical ergonómico inalámbrico", "unidad", 21, 28600),
        ("PAD-101", "Mouse pad gamer XL 80 x 30 cm", "unidad", 21, 14800),
        ("WEB-101", "Cámara web Full HD 1080p con micrófono", "unidad", 21, 46300),
        ("JOY-101", "Joystick inalámbrico para PC", "unidad", 21, 58900),
        ("UPS-101", "UPS 800 VA con estabilizador de tensión", "unidad", 21, 124000),
        ("EST-101", "Estabilizador de tensión 1000 VA 4 tomas", "unidad", 21, 38500),
    ],
    "Audio y video": [
        ("AUR-101", "Auriculares con micrófono para PC conexión USB", "unidad", 21, 27800),
        ("AUR-102", "Auriculares gamer 7.1 con micrófono desmontable", "unidad", 21, 68400),
        ("AUR-103", "Auriculares Bluetooth in-ear con estuche de carga", "unidad", 21, 39800),
        ("PAR-101", "Parlantes 2.0 para PC alimentación USB", "unidad", 21, 19600),
        ("PAR-102", "Parlante portátil Bluetooth 20 W resistente al agua", "unidad", 21, 54200),
        ("MIC-101", "Micrófono condensador USB con brazo y filtro antipop", "unidad", 21, 76500),
        ("PRO-101", "Proyector LED Full HD 4000 lúmenes HDMI", "unidad", 21, 498000),
    ],
    "Conectividad y redes": [
        ("CAB-002", "Cable HDMI 2 m", "unidad", 21, 6200),
        ("ROU-101", "Router WiFi 5 doble banda AC1200", "unidad", 21, 58300),
        ("ROU-102", "Router WiFi 6 doble banda AX3000", "unidad", 21, 128000),
        ("ROU-103", "Sistema WiFi mesh x 2 nodos AX1800", "unidad", 21, 219000),
        ("SWI-101", "Switch 8 puertos Gigabit no administrable", "unidad", 21, 36900),
        ("SWI-102", "Switch 24 puertos Gigabit rackeable", "unidad", 21, 168000),
        ("WIF-101", "Adaptador WiFi USB doble banda AC600", "unidad", 21, 17400),
        ("WIF-102", "Placa de red PCI Express WiFi 6 con Bluetooth 5.2", "unidad", 21, 42800),
        ("UTP-101", "Cable de red UTP Cat 6 por metro", "metro", 21, 780),
        ("UTP-102", "Bobina de cable UTP Cat 5e exterior 305 m", "unidad", 21, 148000),
        ("UTP-103", "Patch cord UTP Cat 6 2 m", "unidad", 21, 3900),
        ("RJ4-101", "Conectores RJ45 Cat 6 bolsa x 100", "paquete", 21, 14500),
        ("CAB-101", "Cable HDMI 2.1 8K 3 m", "unidad", 21, 16800),
        ("CAB-102", "Cable DisplayPort 1.4 2 m", "unidad", 21, 13900),
        ("CAB-103", "Cable USB-C a USB-C 100 W 1 m", "unidad", 21, 9600),
        ("HUB-101", "Hub USB-C 7 en 1 con HDMI, lector SD y carga PD", "unidad", 21, 47800),
    ],
    "Impresión": [
        ("IMP-101", "Impresora multifunción tinta continua color WiFi", "unidad", 10.5, 386000),
        ("IMP-102", "Impresora láser monocromática WiFi dúplex", "unidad", 10.5, 298000),
        ("IMP-103", "Impresora térmica de comprobantes 80 mm USB", "unidad", 10.5, 142000),
        ("TON-101", "Cartucho de tóner negro compatible 2000 páginas", "unidad", 21, 28600),
        ("TIN-101", "Botella de tinta negra 70 ml para sistema continuo", "unidad", 21, 11400),
    ],
    "Dispositivos móviles": [
        ("CEL-101", "Celular 6,6\" 128 GB 6 GB RAM doble SIM", "unidad", 21, 398000),
        ("CEL-102", "Celular 6,7\" 256 GB 8 GB RAM 5G", "unidad", 21, 742000),
        ("TAB-101", "Tablet 10,1\" 64 GB 4 GB RAM WiFi", "unidad", 21, 289000),
        ("TAB-102", "Tablet 11\" 128 GB 8 GB RAM con lápiz", "unidad", 21, 568000),
        ("REL-101", "Reloj inteligente con GPS y monitor cardíaco", "unidad", 21, 124000),
        ("CAR-101", "Cargador de pared USB-C 25 W carga rápida", "unidad", 21, 14700),
        ("CAR-102", "Cargador GaN 65 W 3 puertos", "unidad", 21, 46900),
        ("POW-101", "Batería portátil 20000 mAh con carga rápida", "unidad", 21, 52800),
    ],
}


def generar_articulos():
    filas = []
    for categoria, articulos in CATEGORIAS.items():
        for codigo, descripcion, unidad, alicuota, minorista in articulos:
            mayorista = round(minorista * (1 - DESCUENTO_MAYORISTA), 2)
            filas.append([codigo, descripcion, categoria, unidad,
                          f"{alicuota:.2f}", f"{minorista:.2f}", f"{mayorista:.2f}"])
    return filas


# ----------------------------------------------------------------------------
# Escritura y controles
# ----------------------------------------------------------------------------
def escribir(nombre, encabezado, filas):
    DESTINO.mkdir(parents=True, exist_ok=True)
    for fila in filas:  # el separador es ";": ningún dato puede contenerlo
        assert all(";" not in str(campo) for campo in fila), fila
    # Formato simple a propósito: una fila por línea, campos separados por ";", sin comillas ni escapes
    with open(DESTINO / nombre, "w", encoding="utf-8", newline="\n") as archivo:
        for fila in [encabezado, *filas]:
            archivo.write(";".join(str(campo) for campo in fila) + "\n")


def main():
    azar = random.Random(SEMILLA)
    clientes = generar_clientes(azar)
    articulos = generar_articulos()

    # Controles: si algo no cumple, el script falla en vez de generar datos malos
    con_cuit = [c for c in clientes if c[1]]
    validos = [c for c in con_cuit if cuit_valido(c[1])]
    invalidos = [c[1] for c in con_cuit if not cuit_valido(c[1])]
    assert len(clientes) == TOTAL_CLIENTES, f"hay {len(clientes)} clientes y deben ser {TOTAL_CLIENTES}"
    assert len(validos) >= MINIMO_CON_CUIT_VALIDO, f"solo {len(validos)} clientes tienen CUIT válido"
    assert sorted(invalidos) == sorted(c[1] for c in DE_PRUEBA_INVALIDOS), f"CUIT inválidos inesperados: {invalidos}"
    assert len({c[1] for c in con_cuit}) == len(con_cuit), "hay CUIT repetidos"
    assert len({c[0] for c in clientes}) == len(clientes), "hay nombres de cliente repetidos"
    # Un cliente con CUIT necesita domicilio completo; sin CUIT solo puede ser Consumidor Final
    assert all(c[3] and c[4] and c[5] for c in con_cuit), "hay un cliente con CUIT sin domicilio completo"
    assert all(c[2] == "CONSUMIDOR_FINAL" for c in clientes if not c[1]), "cliente sin CUIT que no es Consumidor Final"
    assert len(articulos) == TOTAL_ARTICULOS, f"hay {len(articulos)} artículos y deben ser {TOTAL_ARTICULOS}"
    assert len({a[0] for a in articulos}) == len(articulos), "hay códigos de artículo repetidos"
    assert len({a[1] for a in articulos}) == len(articulos), "hay descripciones de artículo repetidas"
    assert all(float(a[5]) > float(a[6]) > 0 for a in articulos), "hay un precio inválido"

    escribir("clientes.csv", ["nombre", "cuit", "condicion", "domicilio", "localidad", "provincia"], clientes)
    escribir("articulos.csv", ["codigo", "descripcion", "categoria", "unidad", "alicuota_iva",
                               "precio_minorista", "precio_mayorista"], articulos)

    print(f"{len(clientes)} clientes y {len(articulos)} artículos generados en {DESTINO}")
    print(f"  Clientes con CUIT de dígito verificador válido: {len(validos)}"
          f" | con dígito inválido a propósito: {len(invalidos)} | sin CUIT: {len(clientes) - len(con_cuit)}")
    for titulo, columna, datos in (("Clientes por condición", 2, clientes), ("Artículos por categoría", 2, articulos),
                                   ("Artículos por alícuota", 4, articulos), ("Artículos por unidad", 3, articulos)):
        conteo = {}
        for fila in datos:
            conteo[fila[columna]] = conteo.get(fila[columna], 0) + 1
        print(f"  {titulo}: " + ", ".join(f"{clave} {cantidad}" for clave, cantidad in sorted(conteo.items())))


if __name__ == "__main__":
    main()
