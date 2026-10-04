# ArkcronistContent

Motor de contenido personalizado para **Paper 1.21.8+**, Java 21: define ítems, bloques, muebles
(también asientos, muebles con inventario, cofres con animaciones de Blockbench y camas de dos
bloques), armaduras (con cascos 3D), cultivos y emojis de chat en YAML y el plugin compila
su propio resource pack, lo empaqueta en ZIP, calcula su SHA-1, lo sirve con un servidor HTTP
integrado —o lo sube solo a un servicio de almacenamiento— y se lo envía a cada jugador. Los bloques
y muebles colocados, los cultivos y lo guardado en los muebles se conservan en SQLite. El mismo
concepto que ItemsAdder u Oraxen, reducido a una base limpia sobre la que crecer.

```
/customgive <jugador> <item> [cantidad]    entrega un ítem (acepta @a, @p...; autocompleta ids)
/arkcontent reload                         recompila ítems y pack sin reiniciar
/arkcontent info                           ítems y bloques cargados, colocados, hash del pack, URL
/arkcontent import                         convierte los packs de ItemsAdder de import/ (sección 6)
/arkcontent menu                           explorador de todo el contenido cargado (sección 7)
/arkcontent animate <animación>            reproduce una animación del mueble que miras (sección 4)
/arkcontent shop                           tienda: los ítems con `price`, pagados con Vault (sección 5)
/arkcontent npc equip <hueco> <item> | sit NPC de Citizens con un ítem o sentado (sección 5)
/emojis                                    los emojis de chat disponibles, dibujados (sección 4)
```

| Permiso | Por defecto | Para |
|---|---|---|
| `arkcontent.give` | op | `/customgive`, y sacar ítems del explorador |
| `arkcontent.admin` | op | `reload`, `info`, `import`, `animate`, `npc` |
| `arkcontent.menu` | op | abrir el explorador (solo mirar, si no tiene `arkcontent.give`) |
| `arkcontent.emojis` | todos | `/emojis`. Usar un emoji no necesita permiso, salvo que el emoji declare el suyo |
| `arkcontent.shop` | todos | `/arkcontent shop` (necesita Vault y un plugin de economía) |

Con MythicMobs, ModelEngine, MythicArmor, MMOItems, PlaceholderAPI (y a través de él TAB y
DeluxeMenus), ShopGUI+, Iris, WorldGuard, GriefPrevention, AuraSkills, mcMMO, SkillAPI/ProSkillAPI,
Fabled, Vault, Citizens, DecentHolograms, EconomyShopGUI Premium o ExecutableBlocks instalados se
integra con ellos (sección 5); sin ellos funciona igual —verificado arrancando sin ninguno—.

El plugin se compila contra la API de Paper 1.21.8 y declara esa versión como mínima. El componente
`item_model` y la carpeta `assets/<namespace>/items/`, en los que se apoya todo, existen desde 1.21.4,
pero solo se ha verificado contra 1.21.8.

---

## 1. Compilar

```bash
./gradlew build          # -> build/libs/ArkcronistContent-1.2.0.jar
./gradlew test           # pruebas del núcleo, sin servidor
./gradlew runServer      # levanta un Paper 1.21.8 desechable con el plugin instalado
```

`paper-api` se descarga del repositorio de Paper. En una máquina que no llega a `repo.papermc.io`,
deja `paper-api.jar` y `brigadier.jar` (1.3.10) en `libs/` y el build los usa en su lugar; ver
`libs/README.md`.

Las APIs de los plugins integrados son todas `compileOnly`: solo hacen falta para compilar los
ganchos y ninguna va dentro del JAR.

| API | Versión | De dónde |
|---|---|---|
| MythicMobs (`Mythic-Dist`), ModelEngine, MythicArmor | 5.13.0, R4.2.0, 5.13.4 | `mvn.lumine.io` |
| PlaceholderAPI | 2.11.6 | `repo.extendedclip.com` |
| ShopGUI+ (`shopgui-api`), GriefPrevention | 3.2.0, 16.18.5 | JitPack |
| WorldGuard (`worldguard-core`/`-bukkit`) y WorldEdit (`worldedit-core`/`-bukkit`) | 7.0.14, 7.3.9 | `maven.enginehub.org` |
| Iris, MMOItems | 3.9.2, 6.10 | copias exactas de sus firmas en `src/irisApi` y `src/mmoitemsApi` (sección 5) |

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
    │   └── contents/demo/…               pack de ejemplo: entero en el primer arranque, lo nuevo al actualizar
    └── java/com/arkcronist/content/
        ├── core/                          ── sin Bukkit ──
        │   ├── definition/
        │   │   ├── ItemDefinition         una entrada tal como la describe el YAML
        │   │   ├── Equipment              cómo se lleva puesto: hueco, asset de equipo, modelo en la cabeza
        │   │   ├── ContentType            item · custom_block · custom_furniture
        │   │   ├── Placement              qué pone en el mundo: Block o Furniture (+ Display)
        │   │   ├── ModelSource            modelo aportado, o generado desde parent + texturas
        │   │   ├── ItemBehaviour          qué conserva del material base
        │   │   └── ResourceLocation       namespace:ruta validado (anti path traversal)
        │   ├── allocation/StableAllocator números estables por id (estados de note block, emojis)
        │   ├── animation/
        │   │   ├── BbModelReader          .bbmodel de Blockbench -> un modelo por hueso + animaciones
        │   │   ├── AnimatedModel          huesos, clips, texturas e icono, ya en el marco del display
        │   │   ├── Animator               poses (jerarquía, interpolación) y fotogramas para el cliente
        │   │   └── Playback               qué fotogramas salen en cada tick; bucle, mantener, una vez
        │   ├── furniture/BedLayout        las dos mitades de una cama, el display y dónde va la almohada
        │   ├── block/
        │   │   ├── NoteBlockState         los 800 estados de note block ↔ índice ↔ texto
        │   │   └── NoteBlockAllocator     asignación estable bloque → estado, persistida en JSON
        │   ├── crop/
        │   │   ├── PlantedCrop            un cultivo plantado: posición, etapa, progreso
        │   │   ├── CropGrowth             la aritmética del crecimiento, sin mundo
        │   │   └── CropStore              memoria + SQLite de los cultivos
        │   ├── upload/
        │   │   ├── PackUploader           subida multipart con reintentos y verificación SHA-1
        │   │   ├── MultipartBody          cuerpo multipart/form-data (RFC 7578), sin copiar el ZIP
        │   │   ├── ResponseUrl            el enlace en la respuesta: ruta JSON, regex o texto plano
        │   │   ├── UploadSettings         la sección upload de config.yml
        │   │   └── UploadRecord           data/upload.json: no volver a subir el mismo pack
        │   ├── storage/
        │   │   ├── DatabaseManager        SQLite (JDBC) en su propio hilo, API con CompletableFuture
        │   │   ├── StoredInventory        el contenido guardado de un mueble con inventario
        │   │   ├── PlacedContentIndex     caché en memoria: ConcurrentHashMap por mundo y posición
        │   │   ├── PlacedContentStore     fachada: memoria al instante + escritura asíncrona
        │   │   ├── PlacedContent          una fila de custom_blocks_world
        │   │   └── BlockKey               posición empaquetada en un long
        │   ├── loader/
        │   │   ├── ContentLoader          escanea contents/ y parsea los .yml (SnakeYAML seguro)
        │   │   └── LoadReport             ítems leídos + problemas encontrados
        │   ├── importer/
        │   │   ├── ItemsAdderImporter     import/ (formato ItemsAdder) -> contents/ (este formato)
        │   │   ├── AssetIndex             modelos y texturas en las 5 estructuras de ItemsAdder
        │   │   ├── LegacyText             &c, &#rrggbb... -> MiniMessage
        │   │   └── Rotations              cuaterniones / eje-ángulo -> grados x, y, z
        │   ├── menu/Page                  paginación del explorador
        │   ├── pack/
        │   │   ├── PackCompiler           assets/<ns>/{items,models,textures}, pack.mcmeta y el
        │   │   │                          blockstate de minecraft:note_block
        │   │   ├── PackSettings           contenido de pack.mcmeta
        │   │   ├── ExternalPack           assets de otro plugin que se fusionan en el nuestro
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
            │   └── ItemFactory            crea ItemStacks (setItemModel, equippable) y los reconoce
            ├── block/
            │   ├── BlockRegistry          el mapa global de bloques: por id y por estado
            │   ├── CustomBlock            ítem + estado + BlockData listo para aplicar
            │   └── CustomBlockService     reconocer, colocar, olvidar, resincronizar
            ├── furniture/                 FurnitureService (soporte barrera/luz/cofre/cama + ItemDisplay
            │                              o huesos + vínculo en el chunk), AnimationPlayer (una tarea
            │                              por tick, interpolación en el cliente), SeatService
            │                              (asientos), StorageService (inventarios y tapa)
            ├── protection/                Protection: pregunta a WorldGuard y GriefPrevention antes
            │                              de que el plugin cambie un bloque por su cuenta
            ├── crop/                      CropService, CropTicker (crecimiento asíncrono), CropListener
            ├── emoji/                     EmojiRegistry, ChatEmojiListener (AsyncChatEvent)
            ├── pack/PackDelivery          pack vivo + envío con setResourcePack
            ├── menu/                      ContentMenu (inventario paginado), ContentMenus,
            │                              ContentMenuListener (bloquea todo movimiento)
            ├── hooks/                     integraciones opcionales, una por paquete (también
            │                              placeholderapi/, shopgui/, iris/, mmoitems/, worldguard/,
            │                              griefprevention/):
            │   ├── HookManager            detecta cada plugin y arranca su gancho aislado
            │   ├── mythicmobs/            tipo de drop arkcontent{item=...} + ItemSupplier
            │   ├── modelengine/           muebles dibujados con un blueprint de ModelEngine
            │   ├── mythicarmor/           fusiona el pack generado por MythicArmor
            │   ├── auraskills/            ítems en el registro de AuraSkills + experiencia
            │   ├── mcmmo/ · skillapi/     experiencia por bloques y cultivos (SkillXpHook)
            │   ├── vault/                 la economía de /arkcontent shop (menu/Shop)
            │   ├── citizens/              NPCs con ítems o sentados (NpcBridge)
            │   ├── decentholograms/       holograma de inspección de cultivos
            │   ├── economyshopgui/        proveedor de ítems de EconomyShopGUI Premium
            │   └── executableblocks/      convivencia de bloques con ExecutableBlocks
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
├── armor.yml                          el set del Caballero del Rubí de Sangre (sección 4)
├── models/
│   ├── item/ruby.json, ruby_helmet_worn.json
│   └── furniture/ruby_pedestal.json   modelo 3D (export de Blockbench), ruby_crate.json
└── textures/
    ├── item/ruby.png, ruby_sword.png, ruby_helmet.png…
    ├── entity/equipment/humanoid/ruby_armor.png           capas de armadura (64×32)
    ├── entity/equipment/humanoid_leggings/ruby_armor.png
    ├── block/ruby_block.png
    └── furniture/pedestal_stone.png, ruby_crate.png
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
      modelengine-id: pedestal  # opcional: con ModelEngine, dibuja este blueprint (sección 5)
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
| `furniture.modelengine-id` | no | Blueprint de ModelEngine que dibuja el mueble. También vale `modelengine_id`. Sin ModelEngine se ignora y se usa `resource`. |
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
- **Esquema.** `PRAGMA user_version` lleva la versión: 1 (`custom_blocks_world`), 2 (+ `custom_crops`)
  y 3 (+ `furniture_storage`, ver *Muebles con almacenamiento*). Un archivo de una versión anterior
  gana al abrirse las tablas que le faltan; no se toca nada de lo que ya hay.

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
otro sitio, y el plugin no envía nada salvo que se use una de las dos opciones siguientes.

### Alojar el pack fuera: `external-url` y subida automática

El servidor integrado es la opción por defecto, pero no la única. `/arkcontent info` muestra cuál se
usa (`hosting: builtin | external | upload`) y el enlace que reciben los jugadores.

**`http.external-url`** — el pack lo alojas tú (un CDN, un hosting web, un bucket) y subes
`output/resource_pack.zip` allí después de cada recompilación. Los jugadores reciben ese enlace con
el SHA-1 del pack, así que su cliente sigue comprobando la descarga. `{sha1}` en el enlace se
sustituye por el hash, para servicios que cachean por URL.

**`upload`** — después de cada recompilación el plugin sube el ZIP él mismo a una API web de
almacenamiento y manda a los jugadores el enlace que devuelve, con el SHA-1 nuevo:

```yaml
upload:
  enabled: true
  url: "https://storage.example.com/api/upload"   # el endpoint de subida del servicio
  method: POST                                    # o PUT
  file-field: "file"                              # el campo del formulario con el ZIP
  file-name: "resource_pack.zip"
  fields: { folder: "minecraft" }                 # más campos del formulario
  headers: { Authorization: "Bearer TU-TOKEN" }   # normalmente la clave del servicio
  response:
    url-path: "data.url"        # respuesta JSON: dónde está el enlace (data.url, files[0].url...)
    url-pattern: ""             # otra respuesta: regex cuyo primer grupo es el enlace
  download-url: "{value}"       # el enlace final; {value} = lo encontrado, {sha1} = el hash
  timeout-seconds: 60
  retries: 2                    # tras error de red, timeout o 5xx; pausas de 2, 4, 8... s
  verify: true                  # descargar lo subido y comparar el SHA-1 antes de enviarlo
```

- **Fuera del hilo principal.** La subida es la última etapa de la recompilación en
  `ArkContent-Worker`, y la espera de red ni siquiera ocupa ese hilo: la petición va por el
  `HttpClient` asíncrono de Java y las etapas siguientes vuelven a encolarse en el worker. Solo el
  cambio final —pack, enlace y hash nuevos, y el reenvío a los jugadores conectados— pasa por el
  hilo del servidor.
- **`multipart/form-data`** con `Content-Length` (no chunked, que muchos scripts de subida
  rechazan), el formulario que enviaría un `<input type="file">`. El ZIP no se copia para armar
  la petición.
- **Verificación.** Con `verify: true` se descarga el enlace devuelto y se compara su SHA-1 con el
  del pack: un servicio que recomprime o renombra archivos daría a los jugadores un pack que su
  cliente rechaza, y eso se detecta antes de enviárselo a nadie.
- **Sin subidas repetidas.** `data/upload.json` recuerda el último pack subido (hash, enlace y
  servicio). Un reinicio o un `reload` sin cambios en el pack reutiliza el enlace —comprobándolo
  primero con `verify`, y subiéndolo otra vez si el servicio lo borró— en lugar de llenar el
  servicio de copias.
- **Si la subida falla** (servicio caído, credenciales malas), se avisa en consola con el motivo y
  los jugadores reciben el enlace del servidor integrado si está encendido; si no, conservan el
  pack que ya tenían. Un rechazo (401, 413...) no se reintenta; un error de red o un 5xx sí.
- `upload` tiene prioridad sobre `external-url`, que la tiene sobre el servidor integrado.

### Cultivos (`custom_crop`)

```yaml
items:
  ruby_seeds:
    type: custom_crop           # el ítem es la semilla
    resource:
      texture: item/ruby_seeds  # su icono (opcional: sin él se dibuja como la última etapa)
    crop:
      stages:                   # una por etapa, de recién plantado a maduro; mínimo dos
        - crop/ruby_stage_0     # textura sola: modelo en cruz (block/cross), como un retoño
        - texture: crop/ruby_stage_1
          parent: block/crop    # la forma en # del trigo
        - model: crop/ruby_grown # cualquier modelo
      stage-seconds: 90         # por etapa, ± un 20 % según la posición
      min-light: 9              # como los cultivos vanilla; 0 crece a oscuras
      soil: [FARMLAND]          # dónde se puede plantar
      bone-meal: true
      drops:                    # al cosechar maduro; antes devuelve la semilla
        - item: demo:ruby       # ítem propio o material vanilla (WHEAT)
          amount: 1-3
          chance: 0.75
```

- **Mundo.** Clic derecho con la semilla sobre la cara superior del suelo: se coloca un bloque de luz
  invisible (nivel 0) y un `ItemDisplay` con el modelo de la etapa. Cada etapa tiene su propia
  definición de ítem en el pack (`<ns>:<id>/stage_<n>`), así que crecer es solo cambiar el
  `item_model` del display. Se dispara un `BlockPlaceEvent` real al plantar y un `BlockBreakEvent`
  real al cosechar, así que los plugins de protección deciden.
- **Crecimiento asíncrono.** Cada `crops.tick-seconds` (config, 5 por defecto) un hilo del
  planificador asíncrono de Bukkit suma el tiempo real transcurrido a todos los cultivos de chunks
  cargados y detecta cuáles terminaron su etapa. Al hilo principal solo le llega esa lista corta, en
  una tarea, y solo si no está vacía: comprueba luz y suelo, cambia el modelo y, al madurar, lanza
  partículas (`HAPPY_VILLAGER` y `END_ROD`) y un sonido. Una huerta de miles de cultivos creciendo
  no cuesta nada al tick. Como en vanilla, no crecen en chunks descargados.
- **Tiempo determinista.** La variación de ±20 % sale de la posición y la etapa, no del azar: un campo
  sembrado de golpe no madura en el mismo tick, y el mismo cultivo tarda siempre lo mismo.
- **Cosecha y pérdidas.** Un golpe lo cosecha (se sigue la línea de visión, el bloque de luz no se
  puede apuntar). También lo arrancan, soltando lo suyo: romper o pisotear el suelo, vaciar un cubo
  sobre él, una explosión bajo él. El agua que corre no puede entrar en un bloque de luz, así que un
  cultivo la detiene como una valla. La tierra de cultivo bajo un cultivo no se seca. Los pistones
  que lo moverían se bloquean.
- **Harina de hueso**: una etapa por clic, con partículas. Un clic sobre un bloque llega al servidor
  dos veces (uso sobre el bloque y uso en el aire), así que se aplica como mucho una vez cada 4 ticks
  por jugador.
- **Persistencia.** Tabla `custom_crops` (mundo, x, y, z, id, etapa, progreso) en el mismo SQLite,
  todo con `CompletableFuture` en el hilo `ArkContent-DB`. Plantar, cosechar y cada cambio de etapa
  se guardan al momento; el progreso dentro de una etapa vive en memoria y se guarda de una vez, en
  una transacción por mundo, al descargarse el mundo o apagar el servidor. Una base de datos de la
  1.0 gana la tabla sola al abrirla (esquema 1 → 2).

### Emojis de chat

```yaml
emojis:                         # en cualquier archivo de contenido
  ruby: emoji/ruby              # textures/emoji/ruby.png, normalmente 16x16
  heart:
    texture: emoji/heart
    height: 8                   # píxeles de fuente (una línea de chat mide 9)
    ascent: 7
    permission: vip.emojis      # opcional: sin él, todos pueden usarlo
```

- Cada emoji recibe un carácter del área de uso privado de Unicode (`U+E000`–`U+F8FF`) y el
  compilador escribe un proveedor `bitmap` en `assets/minecraft/font/default.json`. Es la fuente por
  defecto, así que el carácter se dibuja en cualquier texto: chat, carteles, libros, y los menús,
  scoreboards y tablists de otros plugins. El cliente combina ese archivo con el vanilla, no lo
  reemplaza.
- **Asignación estable**, como los estados de note block: `data/emoji_characters.json` guarda qué
  carácter tiene cada emoji para siempre, y uno eliminado queda retirado. Un cartel con un rubí
  seguirá mostrando un rubí aunque se añadan o quiten otros emojis.
- **Chat asíncrono.** Un listener de `AsyncChatEvent` (fuera del hilo principal, como Paper entrega
  el chat) sustituye cada `:nombre:` que el jugador puede usar por su carácter: en blanco (para que
  la imagen conserve sus colores dentro de un chat coloreado), sin negrita, y con el texto original
  al pasar el ratón. Cualquier otra cosa entre dos puntos —una hora, un emoji sin permiso— queda
  igual. Trabaja sobre el componente del mensaje antes de formatearlo, así que EssentialsChat y
  compañía formatean un mensaje que ya lleva el emoji.
- `/emojis` los muestra dibujados, y un clic escribe la palabra clave.

### Asientos

```yaml
  ruby_stool:
    type: custom_furniture
    furniture:
      interactable: seat
      seat-height: 0.6          # dónde queda el que se sienta, en bloques sobre el suelo
```

Clic derecho sobre el mueble (también a través de un soporte `LIGHT`, siguiendo la línea de visión):
se crea en el hilo principal un `ItemDisplay` vacío —invisible y sin caja de colisión— y el jugador
lo monta con la postura vanilla de ir sentado, mirando hacia donde mira el mueble. Agacharse lo
levanta: el asiento desaparece y el jugador queda de pie sobre el mueble, no dentro de su barrera.
Si ya hay alguien sentado se avisa. El asiento nunca se guarda con el chunk; romper el mueble,
desconectarse o apagar el plugin también lo retiran.

### Muebles con almacenamiento

```yaml
  ruby_crate:
    type: custom_furniture
    resource:
      model: furniture/ruby_crate
    furniture:
      support: BARRIER
      interactable: storage
      slots: 27                       # filas enteras: 9, 18, 27, 36, 45 o 54
      storage-title: "<dark_red>Ruby Crate"   # MiniMessage; sin él, el nombre del ítem
```

Clic derecho sobre el mueble abre su inventario (agachado, el clic queda para vanilla, para poner un
bloque contra él). Como un cofre: un inventario por mueble, compartido —dos jugadores ven los mismos
huecos— y que se cierra solo si el jugador se aleja más de 8 bloques.

- **Persistencia asíncrona.** Tabla `furniture_storage` (esquema 3): mundo, x, y, z, id del mueble,
  tamaño y el contenido serializado por Paper, que guarda la versión de datos para que un Minecraft
  posterior actualice los ítems al leerlos. Toda lectura y escritura va por el hilo `ArkContent-DB`
  con `CompletableFuture`; el hilo del servidor nunca espera a SQLite. Lo que sí ocurre en el hilo
  principal es convertir los ítems en bytes y de vuelta (unos microsegundos por inventario): los
  ítems son objetos del servidor, así que se toma una instantánea en el momento del cierre y lo que
  viaja al hilo de la base de datos son bytes.
- **Cuándo se guarda.** Cada vez que alguien lo cierra; cada 30 s mientras siga abierto y haya
  cambiado (un crash pierde como mucho eso); al descargar su mundo y al apagar el plugin. Las
  escrituras se encolan en orden, así que gana la última.
- **Nada se pierde ni se duplica.** Romper el mueble suelta lo que contiene: el inventario abierto
  si alguien lo tiene abierto, si no lo guardado, leído y borrado en una sola transacción para que
  no pueda soltarse dos veces. Un contenido que no se pudiera leer no se sustituye nunca por un
  inventario vacío: el mueble no se abre y la fila queda intacta. Si se reduce `slots` en la
  configuración, el inventario conserva las filas que aún tienen algo hasta que se vacían. Lo que
  quedara guardado en una posición cuyo mueble desapareció sin romperse (WorldEdit, un rollback) se
  suelta allí cuando se coloca otro, en vez de heredarlo el nuevo.

### Cofres animados (soporte `CHEST` y modelos de Blockbench)

```yaml
  ruby_chest:
    type: custom_furniture
    furniture:
      support: CHEST
      animated-model: furniture/ruby_chest    # models/furniture/ruby_chest.bbmodel, tal cual lo guarda Blockbench
      animations:
        open: open                            # nombres de las animaciones en Blockbench
        close: close
      slots: 27
      storage-title: "<dark_red>Ruby Chest"
```

En el mundo hay un **cofre vanilla real** —se pica con hacha como uno, tiene su hitbox, salta con
las explosiones— y encima el modelo de Blockbench, que lo envuelve. Un soporte `CHEST` siempre es
almacenamiento (`interactable: storage` implícito) con la misma persistencia que la sección
anterior; el inventario propio del cofre no se usa nunca:

- **Clic derecho** abre el almacenamiento cuando un cofre vanilla se abriría: sin agacharse, o
  agachado con las dos manos vacías. En **ese mismo tick** —antes de que conteste la base de datos—
  arranca la animación `open` en todos los clientes cercanos y suena el cofre; cuando lo cierra el
  último jugador, `close`.
- El cofre **nunca se une** a otro en un cofre doble (ni al colocarlo ni si después se pone otro
  cofre al lado, que se queda simple sin romper un cofre doble vanilla vecino). Las **tolvas**,
  vagonetas tolva y soltadores no lo llenan ni lo vacían, y cualquier apertura de su inventario real
  (otro plugin, un comando) se cancela.
- **Romperlo** (con hacha, a mano, en creativo o por una explosión que las protecciones permitan)
  suelta el ítem del mueble y lo guardado; nunca el cofre vanilla. Un wither u otro mob no puede
  cambiarlo.
- El modelo tiene que **envolver el cofre vanilla**: x/z de 1 a 15 px e y de 0 a 14 px. Si la tapa se
  abre dejando ver el interior, el cuerpo debe llegar al menos a 14 px (el demo usa 14,25) para que
  no asome la tapa del cofre de debajo.
- `/arkcontent animate <nombre>` reproduce cualquier animación del mueble que se mira (el demo trae
  `shake`), útil para probar un modelo.

#### Cómo se reproduce un `.bbmodel`

El lector convierte el archivo en lo que Minecraft puede dibujar, siguiendo las convenciones de
BetterModel (Blockbench 5 y anteriores, formatos *Generic* y *Java Block*):

- **Un hueso, un `ItemDisplay`.** Cada grupo con cubos se escribe en el pack como un modelo propio
  (`assets/<ns>/models/<id>/bone_<hueso>.json`) con su definición de ítem; el display del hueso lleva
  ese `item_model`. Un display raíz sin ítem ancla el mueble. Las texturas embebidas o con
  `relative_path` dentro del pack se copian; el inventario muestra el modelo entero en reposo
  (`<id>/icon`, con padre `block/block`).
- **Animar no cuesta ticks.** Cada animación se convierte en fotogramas: en cada uno el servidor
  envía a cada hueso su transformación destino y cuántos ticks tardar (`interpolation_duration`), y
  el cliente interpola solo, a sus FPS. Un tramo lineal es **un solo paquete por hueso**; las curvas
  `catmullrom` se muestrean cada 2 ticks; `step` se mantiene y salta en un tick. `loop: once` vuelve
  al reposo al acabar (como Blockbench), `hold` se queda en el último fotograma (la tapa abierta) y
  `loop` se repite exactamente cada `length` ticks. Una sola tarea por tick reproduce todo y se
  detiene sola cuando no hay nada sonando.
- **Jerarquía real**: la posición y rotación de un hijo se componen con las del padre (el pestillo
  gira con la tapa); escala del padre incluida.
- **Límites de Minecraft**: coordenadas de modelo en [-16, 32] (un hueso más grande se encoge y su
  display lo vuelve a escalar), rotación de cubo en un solo eje y a ±22,5° o ±45° (otra se ajusta a
  la más cercana y se avisa), hasta 48 huesos. Molang, bezier y valores vacíos se avisan al cargar y
  se sustituyen (0, o 1 en escala; bezier se reproduce lineal).
- **Tras un reinicio o un crash** los huesos vuelven al reposo al cargar el chunk, y al apagar el
  servidor una tapa abierta se cierra antes de guardarse. Si el modelo cambia de huesos, los
  displays del mueble ya colocado se regeneran al cargar su chunk.

### Camas (soporte `BED`)

```yaml
  ruby_bed:
    type: custom_furniture
    resource:
      model: furniture/ruby_bed       # 16 px de ancho, 32 de largo (z -8 a 24), almohada al norte
    furniture:
      support: BED
      bed-color: red                  # la cama vanilla de debajo y el material del ítem
```

Son **dos bloques de cama vanilla reales** (pies y cabecera), así que dormir, fijar el punto de
reaparición, saltar la noche, no poder dormir con monstruos cerca y explotar en el Nether son de
vanilla. Encima, un solo display centrado entre las dos mitades y girado hacia donde apunta la cama;
se dibuja un 1 % más grande que como se diseñó, apoyado en el suelo, para que un modelo del tamaño
exacto de una cama tape la vanilla sin parpadeo (z-fighting). El cliente tumba al jugador con la
cabeza en la cabecera, a lo largo de la cama: el cuerpo queda alineado con el modelo en X y Z
(verificado: posición del jugador dormido = centro del bloque de cabecera).

Romper cualquiera de las dos mitades (o una explosión) quita la cama entera, el display y los dos
registros, y suelta **un** ítem del mueble, nunca la cama vanilla. Una cama que explota al usarla
fuera del Overworld desaparece con su modelo y no suelta nada, como en vanilla. Los pistones no la
mueven ni la rompen.

### Tienda (`price`)

```yaml
  ruby:
    price: 250        # cada uno; sin price no se vende
```

Con Vault y un plugin de economía (EssentialsX, CMI...), `/arkcontent shop` abre el explorador en
modo tienda: solo los ítems con precio, el precio bajo cada uno, clic compra uno y mayúsculas+clic
un stack. Se compran los que caben en el inventario y se cobran **antes** de entregarlos; sin saldo,
no se entrega nada y se dice cuánto falta.

### Experiencia de habilidades (`skill-xp`)

```yaml
  ruby_block:
    type: custom_block
    block:
      skill-xp: 15      # Minería
  ruby_seeds:
    type: custom_crop
    crop:
      skill-xp: 8       # Agricultura, al cosechar maduro
```

Romper un bloque personalizado o cosechar un cultivo maduro, fuera de creativo, da esa experiencia
a cada plugin de habilidades instalado: AuraSkills (Mining / Farming), mcMMO (Mining / Herbalism),
SkillAPI·ProSkillAPI y Fabled (experiencia de clase, como rotura de bloque). Para esos plugins un
note block o un bloque de luz no valen nada por sí solos; así se pagan.

### Armaduras (`equipment`)

Desde 1.21.4 una armadura no necesita un material propio: el componente de ítem `equippable` dice
en qué hueco se lleva y qué **asset de equipo** la dibuja. El plugin lee la sección `equipment:` de
un ítem (`type: item`), pone el componente en la stack y escribe el asset en el pack.

```yaml
# contents/demo/armor.yml
items:
  ruby_chestplate:
    material: NETHERITE_CHESTPLATE      # armadura, dureza, resistencia al empuje y sonido: de netherita
    display-name: "<!i><gradient:#ff5a6e:#b3122e:#5e0716><b>Coraza del Rubí de Sangre</b></gradient>"
    resource:
      texture: item/ruby_chestplate     # el icono del inventario
    equipment:
      slot: CHEST                       # HEAD, CHEST, LEGS o FEET
      asset: ruby_armor                 # -> demo:ruby_armor

  ruby_helmet:
    material: NETHERITE_HELMET
    resource:
      texture: item/ruby_helmet
    equipment:
      slot: HEAD
      model: item/ruby_helmet_worn      # en la cabeza se dibuja este modelo 3D, no una capa
```

Con `asset: ruby_armor` el compilador escribe `assets/demo/equipment/ruby_armor.json` con una capa
por cada textura que encuentra, y copia esas texturas:

| Capa | Textura (64×32, el mismo patrón que una armadura vanilla) | La usan |
|---|---|---|
| `humanoid` | `textures/entity/equipment/humanoid/ruby_armor.png` | casco, pechera, botas |
| `humanoid_leggings` | `textures/entity/equipment/humanoid_leggings/ruby_armor.png` | grebas |

Todas las piezas de un set nombran el mismo asset; el archivo se escribe una vez. Las capas van
pegadas al cuerpo, infladas 1 px (0,5 px las grebas), como cualquier armadura vanilla. La antigua
ruta `textures/models/armor/*_layer_1.png` ya no la lee el cliente en 1.21.4+.

En la stack, el `equippable` es el del material base con solo el asset cambiado: se conserva el
sonido de equipar, que se pueda poner con clic derecho, con un dispensador o intercambiar, y las
estadísticas siguen siendo las del material (`attribute_modifiers` de la netherita). Si el hueco no
es el del material (un `PAPER` con `slot: HEAD`), se crea un `equippable` nuevo para ese hueco.

**Casco con modelo 3D.** Si el `equippable` de un casco tiene asset, el cliente dibuja la capa de
armadura y **nunca** el modelo del ítem; sin asset, dibuja el modelo del ítem en la cabeza (como una
calabaza). Por eso `equipment.model` va sin `asset`: el plugin quita el asset de la stack y escribe
una definición de ítem que elige por contexto:

```json
{ "model": { "type": "minecraft:select", "property": "minecraft:display_context",
    "cases": [ { "when": "head", "model": { "type": "minecraft:model", "model": "demo:item/ruby_helmet_worn" } } ],
    "fallback": { "type": "minecraft:model", "model": "demo:item/ruby_helmet" } } }
```

En la cabeza, el modelo 3D; en el inventario, la mano o el suelo, el icono plano. El cliente dibuja
un ítem de cabeza a 0,625 de su tamaño, centrado en la cabeza y girado 180°: un cubo de 0 a 16 se ve
de 10 px, lo mismo que un casco inflado 1 px. El de la demo es ese cubo con la textura del casco
aprobado (su cara superior lleva `"rotation": 180`, porque la cara `up` de un modelo va al revés que
la del patrón de la cabeza), dos cuernos escalonados con punta de rubí y una cresta: 10 cubos que
sobresalen 3,75 px por encima y 3,4 px por los lados, rompiendo la silueta vanilla sin taparla. Las
texturas de un modelo van en `textures/item/` (u otra carpeta del atlas de bloques), no en
`entity/equipment/`.

El cargador avisa de lo que el jugador vería mal: un hueco que no existe; una pieza cuya capa no
está (sería invisible: `worn on LEGS, demo:ruby_armor is drawn from textures/entity/equipment/humanoid_leggings/ruby_armor.png,
which is not in demo`); `model` fuera de la cabeza (se ignora); `model` y `asset` juntos en la
cabeza (gana el asset, que es lo que el cliente dibujaría); `model` sin `resource`; o un modelo que
no está en `models/`.

**Actualizar desde 1.3.** El pack de ejemplo se copia entero solo cuando `contents/` no existe;
para que la armadura llegue también a un servidor que ya lo tenía, cada versión lista los ejemplos
que añade y el plugin recuerda (`data/examples_version.txt`) la última que los ofreció. Al cambiar
el jar 1.3 por el 1.4.0 y reiniciar, copia en `contents/demo/` `armor.yml` y sus 8 archivos —y lo
dice en consola—, sin tocar nada que ya exista, sin recrear `contents/demo/` si se borró y sin
volver a poner un archivo que el dueño borró después. Verificado en el servidor de prueba: se añaden
los 9 archivos y salen 15 ítems; borrando `armor.yml` y recargando quedan 11 y no vuelve.

El reparto del set de la demo, medido sobre los píxeles visibles: 74 % acero ennegrecido y 26 %
rubí (casco 66/34, pechera 79/21, grebas 73/27, botas 79/21).

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.4)

Un bot (Mineflayer) recibe las cuatro piezas con `/customgive`. Se pone el casco y la pechera con
clic derecho y las grebas y las botas colocándolas en su hueco del inventario. Un segundo bot mira:

| Comprobación | Resultado |
|---|---|
| `data get entity Tester equipment` | casco: `equippable {slot: head, equip_sound: …equip_netherite}` sin `asset_id`; pechera, grebas y botas: `asset_id: "demo:ruby_armor"`; las cuatro con su `item_model` |
| Lo que recibe otro jugador (`entity_equipment`) | las mismas cuatro stacks: `item_model` `demo:ruby_*`, el casco sin asset y el resto con `demo:ruby_armor` |
| `attribute … armor / armor_toughness / knockback_resistance` | 20 / 12 / 0,4: netherita completa |
| `damage Tester 10 minecraft:mob_attack` | 20 → 17,2 de vida (2,8 de daño, lo que da la fórmula con 20 de armadura y 12 de dureza) |
| Reconectar | el cliente recibe las cuatro piezas en los huecos de armadura 5–8: se guardan con el jugador |
| Pack generado | `equipment/ruby_armor.json` con las dos capas, sus dos texturas, `items/ruby_helmet.json` con el `select` de arriba, el modelo y la textura del casco |

Lo que no se puede comprobar aquí es el dibujo en un cliente de Minecraft real (no hay ninguno en
este entorno): las rutas, el formato de los archivos y los componentes son los que lee el cliente
1.21.4+, y la lámina de presentación reproduce ese dibujo a partir de los mismos archivos.

## 5. Integraciones

Todas opcionales. `paper-plugin.yml` declara cada plugin como dependencia con
`required: false` y `join-classpath: true`; cada gancho vive en su propio paquete bajo `hooks/`, es
el único código que nombra clases del otro plugin y solo se crea si esas clases existen. Si algo
falla al crearlo (una versión con otra API), se avisa en consola y el resto del plugin sigue sin ese
gancho. `/arkcontent info` muestra cuáles están activos (`Hooks: MythicMobs, ...`).

| Plugin | Orden de carga | Por qué |
|---|---|---|
| MythicMobs | después de este | lee sus mobs al habilitarse y pregunta entonces por `arkcontent{...}`: hay que estar escuchando ya |
| ModelEngine | antes de este | su API tiene que estar lista antes de que carguen los muebles del mundo |
| MythicArmor | después de este | genera su primer pack al habilitarse |
| PlaceholderAPI | antes de este | la expansión se registra al habilitarse este plugin |
| ShopGUI+ | después de este | pide los proveedores de ítems al habilitarse (`ShopGUIPlusPostEnableEvent`) |
| Iris | antes de este | genera mundos; su servicio de datos tiene que estar en marcha |
| MMOItems (+ MythicLib) | antes de este | su stat se registra en `onLoad`, antes de que MMOItems lea sus ítems al habilitarse |
| WorldGuard (+ WorldEdit) | antes de este | se le pregunta en cada interacción; su API está escrita con clases de WorldEdit |
| GriefPrevention | antes de este | ídem |
| AuraSkills | antes de este | sus menús se cargan en el primer tick: los ítems se registran antes |
| mcMMO, SkillAPI, ProSkillAPI, Fabled | antes de este | solo se les llama al dar experiencia |
| Vault | antes de este | la economía se busca en cada compra (EssentialsX y otros se registran tarde) |
| Citizens, DecentHolograms | antes de este | solo se les llama por comando o interacción |
| EconomyShopGUI Premium | después de este | pide los proveedores de ítems al cargar sus tiendas |
| SCore, ExecutableBlocks | antes de este | la guardia de bloques se pregunta al identificar uno |

**Aislamiento.** Ninguna clase que arranca los ganchos nombra un tipo que herede de otro plugin, ni
siquiera como lo que devuelve una lambda (la JVM resuelve ese tipo antes de ejecutarla): una clase
cuyo padre no existe no se puede ni cargar. `HookIsolationTest` lo comprueba en un classpath sin
ninguno de esos plugins. Un gancho de habilidades que lance una excepción al dar experiencia se
avisa una vez y se deja de usar; la rotura o cosecha sigue igual.

**Los ítems están desde el primer tick.** Al habilitarse, el plugin lee el YAML de forma síncrona
(milisegundos) y publica los ítems antes de arrancar los ganchos; el pack se sigue construyendo en
segundo plano. Así AuraSkills, EconomyShopGUI o ShopGUI+ encuentran los ítems cuando leen su propia
configuración al arrancar, no segundos después.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.3)

| Integración | Estado |
|---|---|
| AuraSkills 2.4.0 | **Verificado.** 11 ítems registrados antes de que cargue sus menús; `key: demo:ruby_seeds` como icono de Farming en `/skills` y `key: demo:ruby` en Strength de `/stats` llegan al cliente con su `item_model`; romper `ruby_block` da `+15 Mining XP` y cosechar maduro `+8 Farming XP` |
| mcMMO 2.3.002 | **Verificado.** Mining sube al romper el bloque y Herbalism al cosechar. mcMMO aplica su modificador por habilidad y los perks de XP (un op con 15 de `skill-xp` recibió 66) |
| Vault 1.7.3 + EssentialsX 2.21.2 | **Verificado.** `/arkcontent shop` muestra solo ítems con precio; comprar un rubí deja el saldo de $1000 en $750; un stack sin saldo suficiente se rechaza sin cobrar |
| DecentHolograms 2.10.1 | **Verificado.** Agachado + clic derecho con la mano vacía en un cultivo: el cliente recibe el holograma (nombre, etapa, estado), solo para ese jugador, que desaparece a los 5 s |
| Citizens | **Solo compilado** contra `citizens-main 2.0.44-SNAPSHOT`: ese artefacto no trae los módulos NMS y se desactiva solo en 1.21.8; la distribución completa (CI de Citizens) no era accesible desde aquí |
| SkillAPI / ProSkillAPI, Fabled | **Solo compilados** contra ProSkillAPI 1.3.1-R1 y Fabled 1.0.4-R0.56 reales; necesitan ProMCCore / CodexCore y clases configuradas para probarse |
| EconomyShopGUI Premium | **Solo compilado** contra la API 1.11.0 real (Premium es de pago) |
| ExecutableBlocks | **Solo compilado** (de pago; firmas de SCore 5.25 copiadas, como Iris y MMOItems) |
| EconomyShopGUI gratuito 7.3.2 | **No compatible**, verificado: no tiene proveedores de ítems y `/editshop addhanditem` guarda solo material y lore, sin `item_model` ni la etiqueta del plugin |
| DailyShop 3.8 | **No arranca en Paper 1.21.8**, verificado (`ArrayIndexOutOfBoundsException` en su propio inicio); no tiene API |

### AuraSkills

```yaml
# AuraSkills/menus/skills.yml — el icono de Farming
      farming:
        group: second_row
        order: 1
        key: demo:ruby_seeds          # cualquier ítem, como namespace:id (o namespace/id)
# AuraSkills/menus/stats.yml — una estadística
      strength: {group: upper_left, order: 1, key: demo:ruby}
```

Cada ítem se registra en el registro de ítems de AuraSkills con su propio namespace e id, así que
sirve en sus menús (`/skills`, `/stats`...), recompensas y tablas de loot con `key:`. Tras
`/arkcontent reload` el registro se actualiza (altas y bajas); un menú que use un ítem nuevo lo
muestra después de `/skills reload`. Experiencia: ver «Experiencia de habilidades».

### mcMMO, SkillAPI / ProSkillAPI y Fabled

Solo experiencia (`skill-xp`, sección 4): Mining y Herbalism en mcMMO; experiencia de clase con
origen *rotura de bloque* en SkillAPI y Fabled, que la clase gana si su `exp-sources` lo incluye.

### Vault

`/arkcontent shop` (permiso `arkcontent.shop`), ver «Tienda» en la sección 4.

### Citizens

```
/npc select
/arkcontent npc equip hand demo:ruby_sword     # hand, off_hand, helmet, chestplate, leggings, boots, body...
/arkcontent npc sit                            # mirando un mueble: se sienta a la altura de su asiento
```

El NPC recibe la misma stack que `/customgive`, con su modelo; Citizens la guarda con el NPC.
`/npc equip` con el ítem en la mano también funciona; esto ahorra escribir y lo permite desde consola
o scripts. Si Citizens está instalado pero no arrancó, el comando lo dice en vez de fallar.

### DecentHolograms

Agachado y con la mano vacía, clic derecho en un cultivo: un holograma visible solo para ti dice
su nombre, la etapa con una barra y si está maduro, durante 5 s. Esos hologramas no se guardan en
los archivos de DecentHolograms. En tus propios hologramas los emojis funcionan por PlaceholderAPI
(`%arkcontent_emoji_<nombre>%`); una línea `#ICON` no puede mostrar el modelo de un ítem
personalizado, porque DecentHolograms solo conserva material y CustomModelData, no `item_model`.

### EconomyShopGUI Premium

```yaml
# EconomyShopGUI-Premium/shops/Gems.yml
pages:
  page1:
    items:
      '1':
        material: "arkcontent:demo:ruby"
        buy: 250
        sell: 50
```

Proveedor externo de ítems registrado cuando EconomyShopGUI lo pide (`ItemProviderPreLoadEvent`):
el comprador recibe la stack de `/customgive` y vender reconoce la etiqueta del plugin. La versión
gratuita no tiene esta API (ver la tabla de arriba).

### ExecutableBlocks

Convivencia: un bloque colocado por ExecutableBlocks nunca se toma por un bloque personalizado de
este plugin, aunque sea un note block cuyo estado coincida; y ExecutableBlocks no puede colocar un
bloque (por ítem o por comando como `SETEXECUTABLEBLOCK`) donde ya hay un bloque, mueble o cultivo
de este plugin.

### DailyShop

Sin gancho: no tiene API. La versión 3.8 no llega a arrancar en Paper 1.21.8 (ver tabla).


### MythicMobs

Los ítems se usan en los archivos de mobs como un tipo de drop, tanto en `Equipment` como en
`Drops` (MythicMobs lee ambos como tablas de drops):

```yaml
RubyKnight:
  Type: ZOMBIE
  Equipment:
  - arkcontent{item=demo:ruby_sword} HAND
  Drops:
  - arkcontent{item=demo:ruby} 1-3 0.5      # cantidad y probabilidad, como cualquier drop
  - arkcontent{item=demo:ruby_block} 1
```

El ítem sale de `ItemFactory` como con `/customgive`: mismo `item_model`, nombre, lore y etiqueta
PDC, así que el resto del plugin lo reconoce. El id se resuelve en cada drop, de modo que
`/arkcontent reload` se refleja sin recargar MythicMobs. Un id que no existe se avisa una vez en
consola y no suelta nada.

`demo:ruby` a secas **no** funciona: MythicMobs interpreta cualquier `:` en el nombre de un drop
como el formato antiguo `MATERIAL:data` antes de preguntar a ningún plugin. Por eso el id va entre
llaves.

Además, cada namespace se registra como `ItemSupplier` en el `ItemManager` de MythicMobs, para
código que pida ítems a MythicMobs por namespace. MythicMobs 5.13 no consulta esos suppliers al leer
sus archivos de mobs, así que en configuración la forma válida es `arkcontent{item=...}`.

Verificado en un Paper 1.21.8 real con MythicMobs 5.13.0: el mob aparece con `demo:ruby_sword`
(modelo, nombre, lore y PDC correctos), los drops salen como entradas separadas, y
`/arkcontent reload` y `/mm reload` funcionan con el gancho activo.

### ModelEngine

Un mueble con `furniture.modelengine-id` (o `modelengine_id`) se dibuja con ese blueprint en lugar
del modelo de ítem:

```yaml
  ruby_statue:
    type: custom_furniture
    resource:
      model: furniture/ruby_pedestal   # sigue siendo obligatorio: icono del ítem y respaldo
    furniture:
      support: BARRIER
      modelengine-id: ruby_statue      # id del blueprint en ModelEngine
      display:
        scale: 1.0                     # se aplica también al modelo de ModelEngine
```

El mueble conserva todo lo demás —soporte, vínculo en el chunk, fila en SQLite, rotura—. El
`ItemDisplay` sigue existiendo como entidad base: `ModelEngineAPI.getOrCreateModeledEntity(display)`
le añade el modelo y ModelEngine oculta el display. El modelo se marca para guardarse con la entidad,
y al cargar un chunk se vuelve a poner si ModelEngine no lo ha restaurado. Al romper el mueble se
destruye el modelo y luego el display.

Sin ModelEngine, la clave se ignora y el mueble se dibuja con su modelo de ítem. Lo mismo si
ModelEngine no tiene ese blueprint (aviso en consola, una vez por blueprint) o falla al crearlo. Así,
quitar ModelEngine nunca deja muebles invisibles.

### MythicArmor

La API de MythicArmor (`net.mythic.armor:mythicarmor-api`) es **de solo lectura**: permite consultar
sus piezas (`MythicArmorAPI#getItems`) y dónde generó su pack, pero no tiene ningún método para
registrar armaduras desde fuera. Inyectar las armaduras de este plugin en su registro no es posible.

Lo que sí hace el gancho es que sus armaduras se vean con **un único pack**: cada vez que
MythicArmor genera el suyo (`MythicArmorGeneratePackEvent`), los `assets/` de esa carpeta se
fusionan en el nuestro y se recompila. Si una ruta existe en los dos, gana la nuestra y se avisa en
consola. Si MythicArmor también envía su propio pack a los jugadores, desactívalo en su
configuración para que no reciban dos.

### PlaceholderAPI, TAB y DeluxeMenus

Con PlaceholderAPI instalado se registra la expansión `arkcontent`:

| Placeholder | Devuelve |
|---|---|
| `%arkcontent_emoji_<nombre>%` | el carácter del emoji, que el pack dibuja como su imagen |
| `%arkcontent_held%` | id del ítem personalizado en la mano principal, o vacío |
| `%arkcontent_items%` | cuántos ítems personalizados hay cargados |

**TAB** (NEZNAMY) lee placeholders de PlaceholderAPI en cabecera, pie, prefijos y nombres, así que
un emoji en la tablist es `header: "&fBienvenido %arkcontent_emoji_heart%"`. El `&f` delante
conserva los colores de la imagen: el carácter toma el color del texto que lo rodea.

**DeluxeMenus** lee los mismos placeholders en nombres y lore, y además tiene la opción nativa
`item_model`, que es exactamente la clave que usan estos ítems. Un botón con la apariencia del rubí,
que además lo entrega:

```yaml
ruby_button:
  material: PAPER
  item_model: demo:ruby            # el modelo del pack: el mismo que ve quien tiene el ítem
  display_name: "&cRubí %arkcontent_emoji_ruby%"
  slot: 13
  left_click_commands:
    - "[console] customgive %player_name% demo:ruby 1"
```

Verificado: con PlaceholderAPI 2.11.6 en un Paper 1.21.8 real, `/papi parse` devuelve el carácter
del emoji y el número de ítems.

### ShopGUI+

Los ítems se usan en las tiendas con la clave `arkcontent` dentro de `item:`:

```yaml
items:
  ruby:
    type: item
    item:
      arkcontent: demo:ruby
    buyPrice: 100
    sellPrice: 20
```

La tienda muestra —y el comprador recibe— la misma pila que `/customgive`, con su `item_model`;
para vender, el ítem se reconoce por la etiqueta del plugin, no por su nombre. El proveedor se
registra en `ShopGUIPlusPostEnableEvent`, el momento que pide la API de ShopGUI+ (ya arrancado,
tiendas aún sin cargar). Compilado contra `shopgui-api` 3.2.0 (ShopGUI+ 1.111.0 o posterior);
ShopGUI+ es de pago y no se ha podido ejecutar aquí.

### Iris

Registra un proveedor de datos en Iris, así que un pack de Iris puede nombrar un bloque
personalizado por su id en cualquier sitio que acepte un bloque. Un filón de rubí, en el JSON de un
bioma o una región:

```json
"ores": [{
  "palette": [{"block": "demo:ruby_block"}],
  "minHeight": -32,
  "maxHeight": 32
}]
```

Lo que recibe Iris es el propio estado de note block del bloque, así que se coloca tan rápido como
la piedra —sin entidad ni llamada posterior— y se reconoce como bloque personalizado al romperlo.
Los ítems sirven igual en las tablas de botín de Iris. Iris llama desde sus hilos de generación; los
registros que lee se sustituyen enteros en cada recompilación y nunca se bloquean.

Iris no publica su API: el gancho se compila contra una copia exacta de las firmas de Iris 3.9.2 (la
versión para 1.21.x), en un source set propio que nunca entra en el jar. Se comprobó, referencia por
referencia, que el código compilado solo usa firmas que existen tal cual en Iris 3.9.2; no se ha
podido ejecutar con Iris real aquí. Los chunks que Iris genera antes de que este plugin arranque
(el área de spawn de un mundo nuevo) no tienen aún el proveedor: pregenera después de arrancar.

### MMOItems

Registra un stat propio en MMOItems, `ARKCONTENT_ITEM` (`arkcontent-item` en sus YAML): el motor
pasa a ser el proveedor visual de los ítems RPG. Un ítem de MMOItems con ese stat se dibuja con el
modelo y las texturas del ítem de este plugin, del pack que este plugin genera y envía, y conserva
todo lo demás de MMOItems (daño, habilidades, gemas, tiers):

```yaml
CUTLASS:
  base:
    material: IRON_SWORD
    name: '&cRuby Cutlass'
    arkcontent-item: demo:ruby_sword
```

- Solo pone el componente `item_model`; se guarda en el NBT del ítem como cualquier stat, así que
  sobrevive a las actualizaciones de ítems de MMOItems y se puede editar en su GUI (`/mi edit`), que
  rechaza con un mensaje un id que no sea un ítem cargado.
- Se registra en `onLoad` de este plugin: MMOItems carga antes y lee las configuraciones de sus ítems
  al habilitarse, así que el stat ya existe entonces.
- MMOItems solo publica su API como snapshots en su propio repositorio. Igual que con Iris, el gancho
  se compila contra una copia exacta de las firmas que usa (MMOItems 6.10, `src/mmoitemsApi`, nunca
  dentro del JAR), y se comprobó compilándolo también contra el jar real `MMOItems-API`
  6.10.1-SNAPSHOT: el bytecode resultante hace exactamente las mismas referencias. MMOItems es de
  pago y no se ha podido ejecutar aquí.

### WorldGuard y GriefPrevention

La mayoría de lo que un jugador hace a este contenido llega a los plugins de protección como un
evento real (`BlockBreakEvent`, `BlockPlaceEvent`) que pueden cancelar igual que con cualquier
bloque. Pero hay acciones que el plugin hace por su cuenta y que no pasan por ningún evento de
bloque. Esas se consultan antes con WorldGuard y GriefPrevention, y se detienen si cualquiera dice que no:

| Acción | WorldGuard (flags que se prueban, como WorldGuard hace con lo vanilla) | GriefPrevention |
|---|---|---|
| Plantar un cultivo | `build` + `block-place` | confianza de construcción (`/trust`) |
| Cosechar o arrancar un cultivo, romper un mueble, vaciar un cubo sobre un cultivo | `build` + `block-break` | `/trust` |
| Harina de hueso en un cultivo; pisotear la tierra de un cultivo | `build` | `/trust` |
| Sentarse en un mueble | `ride` + `interact` (lo mismo que comprueba WorldGuard al montar) | `/accesstrust` |
| Abrir un mueble con almacenamiento | `interact` + `chest-access` | `/containertrust` |
| Una explosión que alcanza un bloque personalizado o la tierra bajo un cultivo | `build` + `block-break` desde el origen de la explosión, y su flag (`tnt`, `creeper-explosion`...) | el claim admite explosiones (`/claimexplosions`) |
| Un mob que pisotea, agua que fluye | `build` + `block-break` desde donde viene | mismo claim o mismo dueño |

- Todos los listeners de cultivos y note blocks que reaccionan a un evento vanilla (explosiones,
  pistones, pisotones, cubos, agua) corren tarde (`HIGHEST`/`MONITOR`) e ignoran lo ya cancelado,
  así que primero deciden los plugins de protección; y, además, cada bloque que el plugin cambiaría
  por su cuenta se consulta de nuevo. Ejemplo verificado: un TNT fuera de una región rompe la tierra
  bajo un cultivo que está dentro; vanilla se llevaría el cultivo, aquí se queda.
- Los pistones nunca mueven bloques personalizados, muebles ni cultivos, protegidos o no: se
  cancelan siempre.
- Al jugador se le dice `This area is protected.` en la barra de acción. Quien tiene el bypass de
  WorldGuard o `/ignoreclaims` de GriefPrevention pasa, como en todo lo demás.
- Si la comprobación de un plugin de protección lanza un error (una versión con otra API), cuenta
  como **no**: una comprobación rota no puede convertirse en una puerta abierta. Se avisa una vez
  en consola.
- Compilado contra WorldGuard 7.0.14 (WorldEdit 7.3.9) y GriefPrevention 16.18.5, y verificado en
  un servidor Paper 1.21.8 real con dos bots: una región sin flags y otra con `chest-access` y
  `ride` permitidos; un claim sin confianza, con `/containertrust` y con `/trust`; y explosiones
  con y sin `/claimexplosions`.

## 6. Importar desde ItemsAdder

```
plugins/ArkcronistContent/import/     <- copia aquí plugins/ItemsAdder/contents/ (o un pack suelto)
/arkcontent import                    <- convierte, copia recursos y recompila
```

El importador lee todo lo que haya en `import/`, en cualquiera de las cinco estructuras de carpetas
que acepta ItemsAdder (`configs/` + `models/`/`textures/`, `resourcepack/assets/<ns>/`,
`resourcepack/<ns>/`, `assets/<ns>/` o `<ns>/`). Por cada archivo de ItemsAdder con `items:` escribe
`contents/<namespace>/imported/<su ruta en import/>`, y copia cada modelo, textura y animación
(`.png.mcmeta`) que usan esos ítems a `contents/<namespace>/models|textures/`, que es donde el
loader y el compilador del pack los buscan. Después recompila, y los ítems ya están en el juego.

| ItemsAdder | Aquí |
|---|---|
| `name` / `display_name`, `lore` | `display-name`, `lore`: claves de diccionario resueltas (`dictionary`, prefiere inglés); `&c`, `§l`, `&#ff8800`, `&x&f&f...` pasan a MiniMessage |
| `resource.material` / `material` | `material` (solo ítems) |
| `resource` con `generate: true` + `textures` | `resource.texture` (o `textures` + `parent`); espadas y herramientas con `item/handheld` |
| `resource.model_path` / `graphics.model` | `resource.model`, con el modelo, sus padres y sus texturas copiados |
| `graphics.texture` / `graphics.textures` / `graphics.parent` | igual, con el padre por defecto de ItemsAdder (`block/cube` o `cube_all` en bloques) |
| `resource.model_id` (CustomModelData) | innecesario: cada ítem tiene su `item_model` |
| `behaviours.block` | `type: custom_block`; 6 texturas en el orden de ItemsAdder (down, east, north, south, up, west); `drop_when_mined` / `cancel_drop` -> `block.drop-self` |
| `behaviours.furniture` | `type: custom_furniture`; `solid` -> `BARRIER`, si no `LIGHT` con `light_level`; `fixed_rotation` -> `face-player: false`; `display_transformation` (transform, translation, scale, left/right_rotation) -> `furniture.display` |

Arreglos que hace de paso: un modelo exportado de Blockbench con texturas sin namespace
(`"item/espada"`, que el cliente busca en `minecraft:`) se corrige en la copia a `mi_ns:item/espada`
cuando esa textura está en el pack; y a los bloques de seis caras se les da textura de partículas.

Lo que no tiene equivalente —recetas, loot, eventos, durabilidad, encantamientos, sonidos, hitbox de
más de un bloque, `variant_of`— no se importa, y **se lista por ítem** en la consola en vez de
perderse en silencio. En el juego, el comando resume: ítems importados, archivos copiados, y cuántos
problemas y notas hay en consola.

Garantías:

- **Un archivo roto no para la importación.** YAML inválido, un ítem mal formado, una textura que
  falta: se informa con archivo, línea o ítem, y el resto sigue.
- **Nada fuera de `import/`.** No se siguen enlaces simbólicos y toda ruta se valida como las de los
  archivos de contenido (sin `..`), porque lo copiado acaba en un pack público.
- **Idempotente y no destructivo.** `import/` no se modifica. Un recurso que ya existe en
  `contents/` con otros bytes se conserva (y se avisa). Los `.yml` convertidos llevan una cabecera
  que los marca como generados: reimportar solo sobrescribe esos, nunca uno escrito a mano. Para
  editar uno a mano, sácalo de `imported/`.
- **Asíncrono.** Leer, convertir y copiar ocurren en el hilo `ArkContent-Worker`, en la misma cola que
  las recompilaciones: nunca mientras una está leyendo `contents/`. El hilo principal solo recibe el
  resultado.

Diferencias que conviene revisar tras importar: todos los bloques son note blocks (sólidos y opacos,
aunque en ItemsAdder fueran `REAL_TRANSPARENT`, `REAL_WIRE` o `TILE`); los muebles se dibujan con un
item display en el centro del bloque, así que si ItemsAdder usaba un armor stand o un marco, la altura
puede necesitar ajustar `furniture.display.translation`; y los bloques ya colocados en un mundo por
ItemsAdder no se migran (sus estados de note block eran los de ItemsAdder).

## 7. Explorador de contenido

`/arkcontent menu` (o `/arkcontent` a secas) abre un cofre de 6 filas con todo lo cargado, en tres
pestañas —Items, Blocks, Furniture; los textos del juego están en inglés, como el resto de mensajes
del plugin— de 45 por página, ordenado por id:

```
filas 1-5   los ítems: el ItemStack real de ItemFactory, con su item_model, nombre y lore
fila 6      [Previous] [Items] [Blocks] [Furniture] [Page n of m] [Close] [Next]
```

Cada casilla muestra el ítem tal como es —el cliente dibuja su modelo 3D en la casilla— con su id
debajo del lore. Con `arkcontent.give`, clic toma uno y mayúsculas+clic una pila, siempre al
inventario principal (si no cabe, se dice; nunca se tira al suelo). Sin ese permiso el menú es solo
para mirar.

Nada sale del menú de otra forma. Se cancela **todo** clic y arrastre mientras está abierto, en el
menú y en el inventario del jugador debajo (mayúsculas+clic, teclas numéricas, Q, doble clic para
recoger, arrastrar, cambio a la otra mano), primero con prioridad `LOWEST` y otra vez en `HIGHEST`
por si otro plugin lo descancelara. El ítem que se entrega es siempre una pila nueva de
`ItemFactory`, resuelta por id contra el registro en ese momento, nunca la de la casilla. Una
recompilación refresca los menús abiertos, y al deshabilitar el plugin se cierran (sin su listener
serían un cofre normal lleno de ítems).

## 8. Extender

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

## 9. Límites conocidos

- Cambiar nombre o lore en el YAML no actualiza las stacks ya entregadas (el modelo sí).
- `/arkcontent reload` no relee `config.yml`: puerto y dirección del servidor HTTP, y la sección
  `upload`, piden reinicio.
- Un ítem hecho de un material con uso propio (arco, comida, perla) conserva ese uso salvo que
  `cancel-vanilla-use` esté activo, y sigue valiendo como ingrediente en recetas vanilla.
- Bloques personalizados: rompen con la dureza y herramienta de un note block (madera, hacha);
  pick-block (clic central) da un note block vanilla.
- Note blocks vanilla que ya estuvieran afinados en el mundo antes de definir bloques pueden
  coincidir con el estado de un bloque personalizado y verse como él.
- Cultivos: uno por bloque, siempre sobre el suelo configurado y dibujados con un `ItemDisplay` cada
  uno; para granjas enormes, eso son muchas entidades de display (baratas, pero entidades).
- Emojis: se reemplazan en el chat; en carteles y libros funcionan si se escribe el carácter (o se
  pega desde `/emojis`), no la palabra clave.
- Asientos: uno por mueble, con la altura fija de `seat-height`. Un mueble es asiento, contenedor o
  ninguno de los dos, no ambos.
- Muebles con almacenamiento: las tolvas no los alimentan ni los vacían (con soporte barrera no hay
  contenedor; con soporte cofre se bloquean a propósito, porque el contenido vive en la base de
  datos).
- Cofres y camas: el modelo debe envolver el bloque vanilla (medidas en la sección 4). Clicar en la
  parte de un modelo que sobresale del bloque con la mano vacía no llega al servidor (sin ítem en la
  mano el cliente no envía clic al aire); con un ítem sí, y se sigue la línea de visión.
- Un mueble animado no usa ModelEngine (si ambos están configurados gana el modelo animado). Las
  animaciones se reproducen al abrir/cerrar o por comando; aún no hay animaciones en bucle al estar
  colocado.
- Subida automática: solo APIs que aceptan un formulario multipart (o PUT con él). Servicios que
  exigen firmar la petición (S3 con firma v4) o subir por partes no están cubiertos; para esos,
  `external-url` y subir el ZIP con su propia herramienta.
- Importar desde ItemsAdder convierte configuración y recursos, no mundos: los bloques y muebles
  que ItemsAdder ya colocó siguen siendo suyos. Recetas, loot, eventos y demás se listan como no
  importados.
- Muebles de un solo bloque de hitbox. Sujetar un ítem de mueble muestra las partículas de
  barrier/light que el cliente dibuja al sostener esos materiales.
- La caché guarda en memoria todo lo colocado en los mundos cargados (~100 bytes por entrada).
- MythicMobs: los ítems se referencian como `arkcontent{item=ns:id}`, no como `ns:id` (ver
  sección 5).
- ModelEngine: el gancho se compila contra la API real R4.2.0, pero no se ha podido probar en un
  servidor aquí (el artefacto público es solo la API, sin el plugin). Del modelo solo se aplica
  `scale`; `translation` y `rotation` afectan al display oculto.
- MythicArmor: no se pueden añadir armaduras a su registro (API de solo lectura); las armaduras
  propias se definen con `equipment:` (sección 4).
- Armaduras: el modelo 3D solo se dibuja en la cabeza; pechera, grebas y botas son capas planas
  (como las vanilla). Un casco con modelo no tiene capa de armadura, así que tampoco muestra
  adornos (armor trims). El dibujo en un cliente real no se ha podido verificar aquí (sección 4).
- El pack se envía en `PlayerJoinEvent`. En 1.21.7+ Paper permite enviarlo durante la fase de
  configuración, antes de entrar al mundo; es el siguiente paso natural.
