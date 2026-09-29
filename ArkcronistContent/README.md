# ArkcronistContent

Motor de contenido personalizado para **Paper 1.21.8+**, Java 21: define ítems, bloques y muebles en
YAML y el plugin compila su propio resource pack, lo empaqueta en ZIP, calcula su SHA-1, lo sirve con
un servidor HTTP integrado y se lo envía a cada jugador. Los bloques y muebles colocados se guardan en
SQLite. El mismo concepto que ItemsAdder u Oraxen, reducido a una base limpia sobre la que crecer.

```
/customgive <jugador> <item> [cantidad]    entrega un ítem (acepta @a, @p...; autocompleta ids)
/arkcontent reload                         recompila ítems y pack sin reiniciar
/arkcontent info                           ítems y bloques cargados, colocados, hash del pack, URL
```

El plugin se compila contra la API de Paper 1.21.8 y declara esa versión como mínima. El componente
`item_model` y la carpeta `assets/<namespace>/items/`, en los que se apoya todo, existen desde 1.21.4,
pero solo se ha verificado contra 1.21.8.

---

## 1. Compilar

```bash
./gradlew build          # -> build/libs/ArkcronistContent-0.1.0.jar
./gradlew test           # pruebas del núcleo, sin servidor
./gradlew runServer      # levanta un Paper 1.21.8 desechable con el plugin instalado
```

`paper-api` se descarga del repositorio de Paper. En una máquina que no llega a `repo.papermc.io`,
deja `paper-api.jar` y `brigadier.jar` (1.3.10) en `libs/` y el build los usa en su lugar; ver
`libs/README.md`.

El wrapper fija Gradle 9.8.0. `paper-api` se declara `compileOnly`: el servidor ya trae Adventure,
MiniMessage, Brigadier, Gson, SnakeYAML y JOML, y el driver JDBC de SQLite viene con el propio
servidor, así que el JAR solo contiene las clases del plugin.
La versión de la API está en `gradle.properties`.

## 2. Estructura

El código está partido en dos capas, igual que el generador de este repositorio: un **núcleo** que
no importa nada de Bukkit (y por eso se prueba sin arrancar Minecraft) y la **capa del plugin**, que
lo conecta con el servidor.

```
ArkcronistContent/
├── build.gradle · settings.gradle · gradle.properties · gradlew
└── src/main/
    ├── resources/
    │   ├── paper-plugin.yml
    │   ├── config.yml
    │   └── contents/demo/…               pack de ejemplo, se copia en el primer arranque
    └── java/com/arkcronist/content/
        ├── core/                          ── sin Bukkit ──
        │   ├── definition/
        │   │   ├── ItemDefinition         una entrada tal como la describe el YAML
        │   │   ├── ContentType            item · custom_block · custom_furniture
        │   │   ├── Placement              qué pone en el mundo: Block o Furniture (+ Display)
        │   │   ├── ModelSource            modelo aportado, o generado desde parent + texturas
        │   │   ├── ItemBehaviour          qué conserva del material base
        │   │   └── ResourceLocation       namespace:ruta validado (anti path traversal)
        │   ├── block/
        │   │   ├── NoteBlockState         los 800 estados de note block ↔ índice ↔ texto
        │   │   └── NoteBlockAllocator     asignación estable bloque → estado, persistida en JSON
        │   ├── storage/
        │   │   ├── DatabaseManager        SQLite (JDBC) en su propio hilo, API con CompletableFuture
        │   │   ├── PlacedContentIndex     caché en memoria: ConcurrentHashMap por mundo y posición
        │   │   ├── PlacedContentStore     fachada: memoria al instante + escritura asíncrona
        │   │   ├── PlacedContent          una fila de custom_blocks_world
        │   │   └── BlockKey               posición empaquetada en un long
        │   ├── loader/
        │   │   ├── ContentLoader          escanea contents/ y parsea los .yml (SnakeYAML seguro)
        │   │   └── LoadReport             ítems leídos + problemas encontrados
        │   ├── pack/
        │   │   ├── PackCompiler           assets/<ns>/{items,models,textures}, pack.mcmeta y el
        │   │   │                          blockstate de minecraft:note_block
        │   │   ├── PackSettings           contenido de pack.mcmeta
        │   │   ├── PackZipper             ZIP determinista, escritura atómica, versión asíncrona
        │   │   ├── Sha1                   hash del ZIP (bytes y hex)
        │   │   └── PackArtifact           bytes + hash del pack publicado
        │   └── http/
        │       └── PackHttpServer         com.sun.net.httpserver, un único endpoint
        └── bukkit/                        ── capa Paper ──
            ├── ArkContentPlugin           cableado y ciclo de vida
            ├── ContentPipeline            orquesta la recompilación asíncrona
            ├── EngineSettings             config.yml
            ├── item/
            │   ├── CustomItem             el ítem validado contra el servidor
            │   ├── ItemRegistry           el mapa global namespace:id -> CustomItem
            │   └── ItemFactory            crea ItemStacks (setItemModel) y los reconoce
            ├── block/
            │   ├── BlockRegistry          el mapa global de bloques: por id y por estado
            │   ├── CustomBlock            ítem + estado + BlockData listo para aplicar
            │   └── CustomBlockService     reconocer, colocar, olvidar, resincronizar
            ├── furniture/FurnitureService soporte invisible + ItemDisplay + vínculo en el chunk
            ├── pack/PackDelivery          pack vivo + envío con setResourcePack
            ├── command/                   CustomGiveCommand, ContentAdminCommand (Brigadier)
            ├── listener/                  uso, combate, mundo, bloques, muebles, almacenamiento,
            │                              entrega del pack
            └── event/                     CustomItemEvent y sus subclases
```

## 3. Definir contenido

Cada carpeta directamente bajo `plugins/ArkcronistContent/contents/` es un pack de contenido. Todo
`.yml` dentro de ella, a cualquier profundidad, puede declarar ítems; sus carpetas `models/` y
`textures/` guardan los archivos a los que apuntan.

```
contents/demo/
├── items.yml                          ítems
├── blocks.yml                         bloques y muebles
├── models/
│   ├── item/ruby.json
│   └── furniture/ruby_pedestal.json   modelo 3D (export de Blockbench)
└── textures/
    ├── item/ruby.png, ruby_sword.png
    ├── block/ruby_block.png
    └── furniture/pedestal_stone.png
```

```yaml
namespace: demo                 # por defecto, el nombre de la carpeta; 'minecraft' está reservado

items:
  ruby:                         # id único dentro del namespace -> demo:ruby
    material: PAPER             # material base
    display-name: "<red>Ruby"   # MiniMessage
    lore:
      - "<gray>Cut from the deep caves."
    resource:
      model: item/ruby          # -> contents/demo/models/item/ruby.json
    behaviour:
      cancel-vanilla-use: true  # anula el uso propio del material (comer, lanzar, colocar...)

  ruby_sword:
    material: DIAMOND_SWORD
    resource:
      texture: item/ruby_sword  # solo textura: se genera un modelo plano
      parent: item/handheld     # cómo se sostiene (por defecto item/generated)
```

Bloques y muebles se declaran igual, bajo `items:`, con un `type` (ver `contents/demo/blocks.yml`).
Siguen siendo ítems — `/customgive` los entrega — y `type` dice qué ocurre al colocarlos:

```yaml
items:
  ruby_block:
    type: custom_block          # un estado propio de note block
    display-name: "<red>Block of Ruby"
    resource:
      texture: block/ruby_block # una textura = las seis caras (block/cube_all)
    block:
      drop-self: true           # fuera de creativo suelta este ítem, no un note block

  ruby_pedestal:
    type: custom_furniture      # soporte invisible + ItemDisplay
    resource:
      model: furniture/ruby_pedestal   # modelo 3D de Blockbench
    furniture:
      support: BARRIER          # BARRIER (sólido) o LIGHT (atravesable, puede iluminar)
      light: 0                  # solo LIGHT: nivel 0-15
      face-player: true         # mira hacia quien lo coloca, en pasos de 90°
      display:
        transform: NONE         # contexto de display del modelo (NONE, FIXED, HEAD, GUI...)
        translation: [0, 0, 0]  # bloques, desde el centro del bloque
        scale: 1.0              # o [x, y, z]
        rotation: [0, 0, 0]     # grados en x, y, z
```

| Clave | Obligatoria | Qué hace |
|---|---|---|
| `type` | no | `item` (por defecto), `custom_block` o `custom_furniture`. |
| `material` | solo ítems | Material base de Minecraft. En bloques es siempre `NOTE_BLOCK`; en muebles, el soporte. |
| `display-name` | no | Nombre (componente `item_name`, sin cursiva). MiniMessage. |
| `lore` | no | Líneas de lore. MiniMessage. |
| `resource.model` | no | Modelo propio. Se copia junto con los `parent` y `textures` que use de su namespace. Con otro namespace (`minecraft:item/diamond`) solo se referencia. |
| `resource.texture` | no | Una textura: modelo plano (`layer0`) para ítems, cubo `cube_all` (`all`) para bloques. Se ignora si hay `model`. |
| `resource.textures` | no | Variables de textura para cualquier parent, p. ej. `end`/`side` con `block/cube_column`, o `0`/`1` de Blockbench. |
| `resource.parent` | no | Parent del modelo generado. Sin namespace significa `minecraft:`. |
| `block.drop-self` | no | `false` hace que el bloque no suelte nada. |
| `furniture.*` | no | Soporte, luz, orientación y transformación del display (ver arriba). |
| `behaviour.cancel-vanilla-use` | no | `true` anula el uso vanilla del material al hacer clic. |
| `behaviour.placeable` | no | `true` deja colocar como bloque un ítem cuyo material es un bloque. |

Bloques y muebles necesitan `resource`; `material` y `cancel-vanilla-use` se ignoran en ellos (con aviso),
porque romperían la colocación. Un ítem roto nunca impide que carguen los demás: cada problema se registra en consola con el archivo
y el ítem afectados. Solo se copian al pack los archivos que algún ítem referencia, y ninguna ruta
puede salir de su carpeta (`..` se rechaza), porque el pack se sirve públicamente.

## 4. Cómo funciona

### Data components en vez de CustomModelData

`ItemFactory` escribe dos cosas en cada ItemStack:

- `meta.setItemModel(demo:ruby)`: el componente `item_model`. El cliente busca
  `assets/demo/items/ruby.json` (la *definición de ítem* que genera el compilador) y esta apunta al
  modelo. No hay tablas de overrides por material ni números mágicos. Como la stack solo guarda la
  clave, cambiar el modelo en el YAML actualiza también los ítems que los jugadores ya tienen.
- Una etiqueta PDC `arkcronistcontent:item = demo:ruby`: lo que usa el servidor para reconocer el
  ítem. El modelo es solo visual y cualquiera puede copiarlo; la etiqueta es la identidad.

### Pipeline asíncrono

```
hilo worker : contents/*.yml → CustomItem → estados de note block → pack/ (assets, blockstate,
              pack.mcmeta) → resource_pack.zip → SHA-1
hilo main   : sustituye los registros de ítems y bloques y el pack vivo; reenvía a los conectados
hilo DB     : lecturas y escrituras de custom_blocks_world (ver Persistencia)
```

Todo el I/O (lectura de YAML, copia de archivos, ZIP, hash, arranque del servidor HTTP) ocurre en
un hilo propio del plugin. El hilo principal solo ejecuta el último paso, que no toca disco. Las
recompilaciones nunca se solapan, y si una falla, los ítems y el pack anteriores siguen activos.

El ZIP es **determinista**: entradas ordenadas y fechas fijas. El cliente cachea los packs por su
SHA-1, así que el mismo contenido produce el mismo hash y los jugadores no vuelven a descargarlo
tras cada reinicio. Se escribe en un temporal y se mueve de forma atómica, así que
`output/resource_pack.zip` nunca está a medio escribir.

### Bloques personalizados (note block)

Un bloque personalizado es un **note block en un estado reservado para él**. El cliente dibuja cada
estado según el blockstate del pack, así que cada combinación de `instrument` × `note` × `powered`
es una ranura: 16 instrumentos de bloque × 25 notas × 2 = **800 estados**. Los 7 instrumentos de
cabeza de mob (zombie, creeper…) se dejan a vanilla. El estado 0 (`harp`, nota 0, sin energía) queda
reservado para los note blocks vanilla, así que hay **799 ranuras**.

- **Asignación estable.** Un bloque colocado *es* su estado, así que el estado no puede depender del
  orden de la lista: añadir `amber_block` convertiría todos los `ruby_block` del mundo en otra cosa.
  `NoteBlockAllocator` guarda la asignación en `data/note_block_states.json`: cada bloque conserva el
  suyo para siempre, los nuevos toman el menor libre (en orden de id) y los eliminados quedan
  *retirados*, no se reutilizan. Para liberar uno, borra su línea cuando no quede ninguno colocado.
- **Blockstate.** El compilador escribe `assets/minecraft/blockstates/note_block.json` — en el
  namespace `minecraft`, porque el cliente busca los estados por el id del bloque
  (`minecraft:note_block`); un archivo en `assets/<ns>/blockstates/` nunca se leería. Enumera los
  1150 estados (23 × 25 × 2) en orden fijo: los asignados apuntan al modelo del bloque y el resto al
  note block vanilla. Mismas asignaciones, mismos bytes, mismo hash.
- **`CustomBlockListener`.** Al colocar el ítem aplica el `BlockData` del estado; al romper suelta el
  ítem propio en lugar de un note block; cancela el afinado con clic derecho, el sonido al golpearlo,
  el fuego, y las actualizaciones de física (que recalcularían instrumento y `powered`), reenviando el
  estado real al cliente que lo hubiera predicho. Las explosiones rompen el bloque con su ítem.

Consecuencia honesta: **mientras exista al menos un bloque personalizado, los note blocks vanilla
dejan de ser instrumentos** — se quedan en el estado reservado, suenan al golpearlos y nada más (sin
afinado ni redstone), porque cualquier cambio de estado los convertiría en un bloque personalizado.
Sin bloques definidos, el plugin no toca los note blocks en absoluto.

### Muebles (ItemDisplay)

Un mueble es un **bloque de soporte invisible** (`BARRIER`, sólido, o `LIGHT`, atravesable y con luz
opcional) y una entidad **`ItemDisplay`** en el centro de ese bloque que dibuja el modelo 3D. El
display se crea con `setItemModel` en su ítem, invulnerable, sin gravedad, persistente, con la
transformación (escala, rotación, traslación) del YAML y girado hacia el jugador si se pide.

El soporte y el display se vinculan en tres sitios, cada uno para quien lo lee:

- **PDC del chunk**: el UUID del display bajo una clave con la posición del bloque. Un barrier o
  light no tiene `PersistentDataContainer` propio (solo los *block entities* lo tienen), así que el
  chunk hace de contenedor del bloque, una clave por bloque. Se guarda con el chunk, así que nunca
  contradice a los bloques que describe.
- **PDC del display**: id del mueble y posición del soporte. Un display que carga sin su soporte
  (caída del servidor, rollback, soporte quitado con `/fill`) se reconoce huérfano y se elimina en
  `EntitiesLoadEvent`.
- **Caché + SQLite**: para responder "¿hay un mueble aquí?" desde memoria.

Romperlo: en supervivencia ningún soporte se puede minar (el barrier es irrompible y el light ni se
puede apuntar), así que el golpe al mueble —siguiendo la línea de visión, para alcanzar los de tipo
LIGHT— se convierte en un `BlockBreakEvent` real. Los plugins de protección deciden como con
cualquier bloque, y la retirada (display, vínculo, fila, ítem) ocurre en un solo sitio. En creativo
el barrier se rompe normal y pasa por el mismo sitio. Modo aventura y espectador no rompen muebles.

**Las entidades no se pueden crear fuera del hilo principal** (Paper rechaza un *entity add*
asíncrono), así que el display se crea en el hilo principal en el mismo tick que su bloque, ya
configurado antes de entrar al mundo; lo que sale del hilo principal es la escritura en disco.

### Persistencia (SQLite asíncrona)

`data/world_content.db`, tabla `custom_blocks_world`:

| Columna | Tipo | |
|---|---|---|
| `world_uuid` | TEXT | UID del mundo (sobrevive a renombrar la carpeta) |
| `x`, `y`, `z` | INTEGER | posición; clave primaria junto con el mundo |
| `block_id` | TEXT | `namespace:id` del bloque o mueble |
| `type` | TEXT | `BLOCK` o `FURNITURE` (con `CHECK`) |

- **Hilo propio.** `DatabaseManager` ejecuta todo (abrir, `INSERT OR REPLACE`, `DELETE`, `SELECT`)
  en un único hilo `ArkContent-DB` y devuelve `CompletableFuture`. Un solo hilo con una sola conexión
  es lo correcto para SQLite (un escritor) y mantiene el orden: colocar y romper en el mismo segundo
  llega al archivo como insert y luego delete. WAL + `synchronous=NORMAL`.
- **Caché en memoria.** `PlacedContentIndex`: `ConcurrentHashMap` por mundo con la posición
  empaquetada en un `long`. Los listeners consultan ahí, sin tocar disco. Al arrancar se cargan los
  mundos ya cargados y, después, cada `WorldLoadEvent`; al descargar un mundo se libera. Al
  descargar un **chunk** no se hace nada: la caché lo sobrevive.
- **Cambios.** Colocar o romper actualiza la caché al instante y encola la escritura. Romper borra
  la fila aunque la caché aún no tuviera el mundo cargado; y un bloque roto *mientras* su mundo se
  carga no "resucita" cuando llegan las filas.
- **Apagado.** Es la única espera: `onDisable` deja terminar las escrituras encoladas (máx. 10 s).

El driver es el `org.xerial` SQLite que Paper/Spigot traen en el servidor; si faltara, se avisa en
consola y bloques y muebles siguen funcionando (reconocidos por su estado y su vínculo), solo que
sin guardar dónde están.

### Servidor HTTP

`PackHttpServer` usa `com.sun.net.httpserver.HttpServer` con un pool acotado de hilos propios. Solo
responde a `GET`/`HEAD /resource_pack.zip` (todo lo demás es 404), sirve el pack desde memoria para
que una recompilación nunca corte una descarga en curso, y responde 304 si el cliente ya lo tiene.

Al entrar, `PlayerJoinEvent` envía el pack con `player.setResourcePack(id, url, hash, prompt,
required)`. El id es fijo, así que una recompilación **reemplaza** el pack del jugador en lugar de
apilar otro.

Para que funcione desde fuera:

1. Abre o redirige el puerto `http.port` (8163 por defecto), igual que el del juego.
2. Pon en `http.public-address` la IP pública o el dominio del servidor. Si lo dejas vacío se usa
   `server-ip` de `server.properties`, y si también está vacío, `127.0.0.1`, que solo sirve en la
   misma máquina.

Con `http.enabled: false` el pack se sigue compilando en `output/resource_pack.zip` para alojarlo en
otro sitio, y el plugin no envía nada.

## 5. Extender

Los listeners solo reconocen el ítem y disparan un evento; el comportamiento va en quien lo escuche:

```java
@EventHandler
public void onUse(CustomItemUseEvent event) {
    if (event.getItem().id().equals("demo:ruby") && event.getAction().isRightClick()) {
        event.getPlayer().sendMessage(Component.text("The ruby glows."));
    }
}
```

| Evento | Cuándo | Cancelarlo |
|---|---|---|
| `CustomItemUseEvent` | clic con el ítem, en el aire o sobre un bloque | anula el uso vanilla (empieza cancelado si `cancel-vanilla-use: true`) |
| `CustomItemHitEvent` | golpe cuerpo a cuerpo con el ítem en la mano principal | anula el golpe; el daño es editable |
| `CustomItemBreakBlockEvent` | romper un bloque con el ítem en la mano principal | el bloque no se rompe |

Una nueva intercepción (proyectiles, soltar o recoger, sentarse en un mueble) sigue el patrón de
`WorldInterceptionListener`: identificar la stack con `ItemFactory#identify` y disparar una nueva
subclase de `CustomItemEvent`.

Desde otro plugin, los registros están en `JavaPlugin.getPlugin(ArkContentPlugin.class)`: `items()`,
`blocks()` y `placed()` (qué hay colocado en cada posición). Al
ser un plugin de Paper, ese otro plugin debe declararlo como dependencia en su `paper-plugin.yml`
con `join-classpath: true` para ver sus clases.

## 6. Límites conocidos

- Cambiar nombre o lore en el YAML no actualiza las stacks ya entregadas (el modelo sí).
- `/arkcontent reload` no relee `config.yml`: puerto y dirección del servidor HTTP piden reinicio.
- Un ítem hecho de un material con uso propio (arco, comida, perla) conserva ese uso salvo que
  `cancel-vanilla-use` esté activo, y sigue valiendo como ingrediente en recetas vanilla.
- Bloques personalizados: rompen con la dureza y herramienta de un note block (madera, hacha);
  pick-block (clic central) da un note block vanilla.
- Note blocks vanilla que ya estuvieran afinados en el mundo antes de definir bloques pueden
  coincidir con el estado de un bloque personalizado y verse como él.
- Muebles de un solo bloque de hitbox; sin interacción de clic derecho todavía (el gancho está en
  `FurnitureListener#onUse`). Sujetar un ítem de mueble muestra las partículas de barrier/light
  que el cliente dibuja al sostener esos materiales.
- La caché guarda en memoria todo lo colocado en los mundos cargados (~100 bytes por entrada).
- El pack se envía en `PlayerJoinEvent`. En 1.21.7+ Paper permite enviarlo durante la fase de
  configuración, antes de entrar al mundo; es el siguiente paso natural.
