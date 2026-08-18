# ArkcronistGenerator

Generador de mundos procedural para **Paper / Purpur 1.21.8 – 1.21.11**, Java 21, en un único JAR
sin dependencias externas y sin NMS.

No es un fork de nada: es un proyecto propio que estudia las buenas ideas de
[Terra](https://github.com/PolyhedralDev/Terra) (MIT) y
[TerraformGenerator](https://github.com/Hex27/TerraformGenerator) (Apache-2.0) y las lleva más lejos
en los puntos que importan para este servidor: océanos de verdad, cordilleras reales, cuevas con
sentido, estructuras que se adaptan al relieve y minibosses. Ver [NOTICE.md](NOTICE.md).

```
/ag                     comando principal
-g ArkcronistGenerator            preset por defecto (config.yml)
-g ArkcronistGenerator:BASE       mundo natural, variado y jugable
-g ArkcronistGenerator:CHAOTIC    mundo accidentado: cordilleras, cañones, islas, biomas fragmentados
-g ArkcronistGenerator:INSANE     mundo extremo: picos gigantes, arcos, salientes, islas flotantes, megacuevas, fosas
```

---

## 1. Qué genera

### Terreno

| Sistema | Qué hace |
|---|---|
| Continentes | Un campo de muy baja frecuencia con deformación de dominio decide tierra o mar. Los continentes miden miles de bloques. |
| Costas | Transición continua entre el fondo marino y la tierra, con rugosidad propia: bahías, entrantes, penínsulas. |
| Plataforma continental | Escalón somero pegado a la costa antes de que el fondo se hunda. |
| Talud continental | Caída larga desde la plataforma hasta la llanura abisal. |
| Llanura abisal | Fondo profundo con ondulación propia. En BASE se pasa de 100 bloques de profundidad. |
| Fosas oceánicas | Grietas estrechas y muy profundas, solo mar adentro. |
| Montañas submarinas | Relieve montañoso bajo el agua que a veces emerge como isla. |
| Cordilleras | Ruido *ridged* con máscara propia: las montañas forman cinturones, no una manta uniforme. |
| Mesetas | Aterrazado selectivo del relieve. |
| Cañones | Sustracción estrecha y profunda siguiendo una red deformada. |
| Acantilados | Bandas de aterrazado que producen repisas y escalones en pendientes fuertes. |
| Ríos | Red de valles deformada que se desvanece con la altitud (los ríos altos quedan como gargantas secas). |
| Lagos | Cuencas celulares con **nivel de agua propio**, no atados al nivel del mar. |
| Erosión simulada | Erosión térmica con talud límite y sedimentación, calculada sobre la rejilla compartida. |
| Estratos geológicos | Bandas horizontales deformadas por ruido, con "provincias" que comparten secuencia de roca. |
| Salientes y arcos | Campo 3D alrededor de la superficie: repisas, cornisas y arcos naturales. |
| Islas flotantes | Masas lenticulares agrupadas por ruido celular (CHAOTIC e INSANE). |
| Cuevas | Cuatro sistemas: túneles espagueti, cuevas *cheese*, cavernas y megacuevas. |
| Menas | Campo 3D de vetas + hash por bloque: depósitos con forma, sesgados por bioma. |

Los océanos son obligatoriamente reales: **costa → plataforma → talud → llanura abisal → fosa**, con
montañas submarinas encima. Medido sobre 30.000 columnas por preset:

| Preset | Profundidad media en abisal | Punto más profundo | Pico más alto |
|---|---|---|---|
| BASE | ~55 bloques | ~104 bloques bajo el mar | y ≈ 180 |
| CHAOTIC | ~70 bloques | ~113 bloques bajo el mar | y ≈ 244 |
| INSANE | ~90 bloques | ~121 bloques bajo el mar | y ≈ 306 |

### Biomas

Unos 34 biomas, cada uno con identidad propia en **relieve, altura, clima, vegetación, árboles,
materiales, minerales, cuevas, estructuras y densidad de decoración**. La selección es una búsqueda
suave por distancia sobre continentalidad, gradiente oceánico, factor de montaña, altitud,
temperatura, humedad y "rareza", así que las fronteras se mueven con el terreno y las transiciones
son naturales, no líneas rectas.

Dos biomas pueden compartir clave vanilla y verse completamente distintos: la clave vanilla solo
controla color de hierba, niebla y spawns naturales; el resto lo decide ArkcronistGenerator.

Bajo tierra el proveedor de biomas cambia a biomas de cueva por regiones, así que el subsuelo tiene
ambiente propio.

### Árboles

Árboles procedurales completos: **tronco (con estrechamiento), ramas orientadas, raíces, copa**, y
variantes por semilla: gigantes, inclinados, caídos, tocones y árboles muertos. Catorce especies
repartidas por región (roble, abedul, pícea, pícea gigante, jungla, jungla gigante, acacia, roble
oscuro, roble pálido, mangle, cerezo, azalea, muerto…).

**Ningún árbol se corta en el borde de un chunk.** Cada árbol se dibuja entero y el escritor recorta;
cada chunk recalcula además los árboles de sus vecinos y coloca la parte que le corresponde. Hay una
prueba automatizada que compara el árbol dibujado entero contra el mismo árbol ensamblado desde una
rejilla de chunks: deben ser idénticos bloque a bloque.

### Estructuras

Catorce familias, todas procedurales y adaptadas al terreno (nivelan su plataforma, hunden cimientos
en la pendiente y despejan el espacio superior):

`village` · `city` · `castle` · `fortress` · `tower` · `battle_tower` · `temple` · `ruins` · `camp` ·
`bridge` · `underwater` · `sky_sanctuary` · `vault` · `dungeon`

Se colocan sobre dos rejillas deterministas (una gruesa para ciudades, castillos y aldeas; otra fina
para torres, campamentos, ruinas y criptas), se construyen una sola vez en un búfer y se vuelcan por
chunk, de modo que una ciudad de 128 bloques cruza decenas de chunks sin costuras.

### Minibosses

Las estructuras registran qué entidades quieren y dónde; el plugin las materializa en el hilo
principal cuando el chunk se carga (y solo una vez, marcado en el *persistent data* del chunk).

- Guarnición normal escalada por nivel.
- Minibosses con vida y daño multiplicados, armadura, resistencia al empuje, efectos permanentes,
  equipo encantado, nombre personalizado visible, brillo y persistencia.
- Nombres configurables por rol en `config.yml` (`tower_captain`, `castle_lord`, `deep_leviathan`…).
- Cofres con botín por niveles y temática, generados de forma determinista por posición.
- Los generadores (`spawner`) se configuran con la entidad correcta de cada estructura.

---

## 2. Instalación y uso

1. Copia `ArkcronistGenerator-1.0.0.jar` en `plugins/`.
2. Arranca el servidor una vez para que se genere `plugins/ArkcronistGenerator/config.yml`.
3. Crea el mundo con tu gestor de mundos (ArkcronistWorlds, Multiverse, etc.):

```
-g ArkcronistGenerator
-g ArkcronistGenerator:BASE
-g ArkcronistGenerator:CHAOTIC
-g ArkcronistGenerator:INSANE
```

En `bukkit.yml` sería:

```yaml
worlds:
  arkcronist:
    generator: ArkcronistGenerator:INSANE
```

Requisitos: **Java 21**, Paper o Purpur **1.21.8 – 1.21.11**. Un único JAR, sin dependencias, sin
NMS: todo se hace con la API de Paper, así que no hay nada que actualizar entre parches de esa serie.

---

## 3. Comandos

| Comando | Qué hace |
|---|---|
| `/ag help` | Ayuda. |
| `/ag info` | Preset, semilla, bioma, altura de superficie y nivel de agua donde estás. |
| `/ag biome [x z]` | Bioma del generador en una posición. |
| `/ag locate [tipo]` | Busca la estructura más cercana (asíncrono). |
| `/ag structures` | Catálogo de estructuras. |
| `/ag presets` | Lista BASE, CHAOTIC e INSANE. |
| `/ag stats` | Memoria, tamaño de caché, tasa de aciertos, colas de mobs. |
| `/ag bench [preset] [chunks]` | Benchmark de generación en un hilo aparte. |
| `/ag top` | Te sube a la superficie. |
| `/ag reload` | Recarga `config.yml`. |
| `/ag version` | Versión y estados de bloque resueltos. |

Permisos: `arkcronist.command` (op) y `arkcronist.admin` (op, solo para `reload`).

---

## 4. Configuración

`config.yml` viene con **todos los parámetros documentados y comentados**. Lo que está bajo
`terrain:` afecta a todos los presets; lo que está bajo `presets.<nombre>:` solo a ese. Las claves que
borres simplemente conservan el valor del preset, así que el archivo se puede dejar mínimo.

```yaml
default-preset: BASE

performance:
  terrain-cache-chunks: 512     # ~12 KB por chunk cacheado
  parallel-generation: true

minibosses:
  enabled: true
  health-multiplier: 4.0
  damage-multiplier: 2.0
  names:
    tower_captain: "Capitán de la Torre"

terrain:
  # abyss-depth: 82.0
  # mountain-amplitude: 115.0
  # floating-island-density: 0.0
  # cave-cheese-threshold: 0.56
```

Los cambios afectan a los chunks **nuevos**; el terreno ya generado nunca se reescribe.

---

## 5. Arquitectura

```
com.arkcronist.gen
├── core/                  ← 100% independiente de Bukkit, probable y medible sin servidor
│   ├── math/              FastRandom (xoroshiro128++), hashing posicional, utilidades
│   ├── noise/             símplex 2D/3D, fractal (fBm, ridged, billow), warp, celular
│   ├── block/             registro de bloques → ids enteros, paletas ponderadas
│   ├── terrain/           muestreador de columnas, rejilla + erosión, cuevas, densidad 3D,
│   │                      estratos, menas, caché, motor
│   ├── biome/             tabla de biomas, perfiles de decoración, selector
│   ├── tree/              especies, variantes, constructor procedural
│   ├── decorate/          colocación de árboles/rocas y decoración por columna
│   ├── structure/         búfer, kit de construcción, colocador, 14 estructuras
│   └── bench/             benchmark ejecutable
└── bukkit/                ← capa fina sobre la API de Paper
    ├── ArkChunkGenerator, ArkBiomeProvider, BlockBridge, WorldRegistry
    ├── populator/         features + estructuras
    ├── mobs/              cola de spawns, fábrica de minibosses, listener
    ├── loot/              relleno de cofres
    ├── command/           /ag
    └── config/            lectura de config.yml
```

El núcleo escribe **enteros**, no `BlockData`. La traducción a bloques reales ocurre una sola vez al
arrancar. Eso hace que todo el terreno se pueda probar en JUnit sin servidor y que la generación no
toque nunca el parser de estados de bloque.

### Rendimiento

Decisiones que están en el código, no en la intención:

- **Rejilla gruesa + interpolación.** El apilamiento de ruido se evalúa cada 4 bloques y se sube a
  resolución de bloque con interpolación bicúbica. Los campos 3D (cuevas, salientes, vetas, islas,
  moteado de estratos) usan rejillas 4×4×4 con interpolación trilineal.
- **Rejilla global alineada y con relleno.** Dos chunks vecinos interpolan desde los mismos nodos con
  los mismos pesos, así que las fronteras coinciden exactamente. La erosión es un *stencil* de solo
  lectura, lo que la mantiene idéntica sea cual sea el chunk que la dispare.
- **Una sola pasada por columna.** Terreno, salientes, cuevas, agua, capas de superficie, estratos y
  menas se resuelven en un único recorrido descendente por columna.
- **Nada de estado por orden de llamada.** Todo sale de la posición del mundo, así que la generación
  es reproducible, paralela y segura entre hilos.
- **Caché acotada** de datos 2D por chunk (límite duro configurable) en lugar de crecer con el área
  explorada.
- **Rechazo temprano.** Los árboles de los chunks vecinos se descartan por caja envolvente antes de
  construirse; las menas hacen primero un hash barato y solo entonces consultan el campo de vetas.
- **Cero E/S durante la generación** y sin hilos propios: se usa el planificador de Paper.

Optimizaciones concretas hechas durante el desarrollo, con su medida:

| Cambio | Antes | Después |
|---|---|---|
| Sacar el ruido de estratos del bucle interno (por columna, no por bloque) + rejilla de moteado | 5,9 ms/chunk | 2,8 ms/chunk |
| Hash barato antes de consultar el campo de vetas de menas | — | incluido arriba |
| Descarte por caja envolvente de los árboles de vecinos | 1,5 ms/chunk | 1,0 ms/chunk |

Medición actual (un solo hilo, contenedor de desarrollo, 96 chunks por preset):

```
BASE:    ~4,5 ms/chunk   (~220 chunks/s/hilo)
CHAOTIC: ~4,4 ms/chunk   (~226 chunks/s/hilo)
INSANE:  ~6,0 ms/chunk   (~166 chunks/s/hilo)
```

Reproducible con:

```bash
mvn -q package -DskipTests
java -cp target/ArkcronistGenerator-1.0.0.jar com.arkcronist.gen.core.bench.TerrainBenchmark 1234 256
```

o dentro del juego con `/ag bench INSANE 256`.

---

## 6. Pruebas

93 pruebas JUnit 5, todas sin servidor:

```bash
mvn test
```

Cubren, entre otras cosas:

- Rango, determinismo e independencia por semilla del ruido.
- **Continuidad entre chunks** (la prueba anti-costuras) y estabilidad de la erosión.
- Terreno idéntico generado en paralelo desde 8 hilos.
- Perfil oceánico: la plataforma es mucho menos profunda que la llanura abisal, y cada preset baja
  más que el anterior.
- Coherencia bioma/agua: el mar profundo no elige biomas de tierra ni al revés.
- Bedrock, llenado de agua, capas de superficie y reproducibilidad bloque a bloque.
- **Las cuevas nunca abren el fondo marino** (los océanos no se vacían).
- Volumen de cueva dentro de límites por preset (ni macizo ni hueco).
- **Árboles nunca cortados en el borde de chunk**, para las catorce especies.
- Volcado de estructuras sin pérdidas ni solapes, colocación determinista, botín y minibosses.
- Caché acotada, tasa de aciertos y benchmark.

Las tres pruebas que fallaron durante el desarrollo (superficie bajo terreno con salientes, volumen
de cuevas, estructuras de cielo y criptas inalcanzables) se corrigieron y quedaron cubiertas para
evitar regresiones.

---

## 7. Compilar

```bash
mvn -B package        # ejecuta las pruebas y produce target/ArkcronistGenerator-1.0.0.jar
```

Requiere JDK 21 y la Paper API 1.21.8 (`repo.papermc.io`, ya declarado en el `pom.xml`).

---

## 8. Estado y siguientes pasos

Esta es la primera versión completa y funcional, pensada para probarla en el servidor real y afinarla
con lo que se vea in situ. Lo que ya está listo para iterar:

- Ajuste fino de densidad de estructuras y de botín con datos de juego real.
- Habilidades activas de minibosses (ahora tienen estadísticas, equipo y efectos).
- Más variantes por bioma de las estructuras existentes.
- Decoración de cuevas (estalactitas, musgo, lagos subterráneos) más allá de lo actual.
