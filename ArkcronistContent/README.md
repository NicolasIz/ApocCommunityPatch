# ArkcronistContent

Motor de contenido personalizado para **Paper 1.21.8+**, Java 21: define ítems, bloques, muebles
(también asientos, muebles con inventario, cofres con animaciones de Blockbench y camas de dos
bloques), armaduras (con cascos 3D), cultivos, emojis de chat, logros, armas de fuego, líquidos que
fluyen y barras de HUD en YAML y el plugin compila
su propio resource pack, lo empaqueta en ZIP, calcula su SHA-1, lo sirve con un servidor HTTP
integrado —o lo sube solo a un servicio de almacenamiento— y se lo envía a cada jugador. Los bloques
y muebles colocados, los cultivos y lo guardado en los muebles se conservan en SQLite. El mismo
concepto que ItemsAdder u Oraxen, reducido a una base limpia sobre la que crecer.

```
/customgive <jugador> <item> [cantidad]    entrega un ítem (acepta @a, @p...; autocompleta ids)
/arkcontent reload                         recompila ítems y pack sin reiniciar
/arkcontent info                           ítems y bloques cargados, colocados, hash del pack, URL
/arkcontent cmd <item>                     material y número custom_model_data del ítem (sección 4)
/arkcontent import                         convierte los packs de ItemsAdder de import/ (sección 6)
/arkcontent menu                           explorador de todo el contenido cargado (sección 7)
/arkcontent animate <animación>            reproduce una animación del mueble que miras (sección 4)
/arkcontent editor [mueble]                mueve, escala y gira en vivo el mueble que miras (sección 4)
/arkcontent shop                           tienda: los ítems con `price`, pagados con Vault (sección 5)
/arkcontent npc equip <hueco> <item> | sit NPC de Citizens con un ítem o sentado (sección 5)
/arkcontent hud <jugador> <hud> [set|add|take <n>]  ver o cambiar el valor de una barra (sección 4)
/arkcontent liquids                        líquidos cargados y fuentes vertidas por mundo (sección 4)
/emojis                                    los emojis de chat disponibles, dibujados (sección 4)
```

| Permiso | Por defecto | Para |
|---|---|---|
| `arkcontent.give` | op | `/customgive`, y sacar ítems del explorador |
| `arkcontent.admin` | op | `reload`, `info`, `import`, `editor`, `animate`, `npc`, `hud`, `liquids`; avisos del servidor web al entrar |
| `arkcontent.menu` | op | abrir el explorador (solo mirar, si no tiene `arkcontent.give`) |
| `arkcontent.emojis` | todos | `/emojis`. Usar un emoji no necesita permiso, salvo que el emoji declare el suyo |
| `arkcontent.shop` | todos | `/arkcontent shop` (necesita Vault y un plugin de economía) |

Con MythicMobs, ModelEngine, MythicArmor, MMOItems, PlaceholderAPI (y a través de él TAB y
DeluxeMenus), ShopGUI+, Iris, WorldGuard, GriefPrevention, AuraSkills, mcMMO, SkillAPI/ProSkillAPI,
Fabled, Vault, Citizens, DecentHolograms, EconomyShopGUI Premium, ExecutableBlocks, Jobs Reborn,
ExcellentJobs, HMCCosmetics, zAuctionHouse, eco (EcoItems, EcoArmor, EcoEnchants, Reforges…), nightcore
(ExcellentCrates, ExcellentShop…), Mimic, ItemBridge, WorldEdit, BetonQuest o AuthMe instalados se
integra con ellos (sección 5); sin ellos funciona igual —verificado arrancando sin ninguno—. La
sección 10 compara, plugin por plugin, con la lista de compatibilidad de ItemsAdder.

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
    │   ├── vanilla-item-models.txt   el modelo de cada ítem vanilla de un solo modelo (1.21.8)
    │   ├── vanilla-tripwire.json     los 32 modelos que vanilla da a la cuerda (1.21.8)
    │   └── contents/demo/…               pack de ejemplo: entero en el primer arranque, lo nuevo al actualizar
    └── java/com/arkcronist/content/
        ├── core/                          ── sin Bukkit ──
        │   ├── definition/
        │   │   ├── ItemDefinition         una entrada tal como la describe el YAML
        │   │   ├── AdvancementDefinition  un logro: título, icono, padre, qué lo completa, anuncio
        │   │   ├── JobReward              dinero y experiencia de trabajo por romper o cosechar
        │   │   ├── Equipment              cómo se lleva puesto: hueco, asset de equipo, modelo en la cabeza
        │   │   ├── GunDefinition          cómo dispara un arma: daño, cargador, munición, retroceso
        │   │   ├── ContentType            item · custom_block · custom_furniture · custom_crop · custom_liquid
        │   │   ├── Placement              qué pone en el mundo: Block o Furniture (+ Display)
        │   │   ├── ModelSource            modelo aportado, o generado desde parent + texturas
        │   │   ├── ItemBehaviour          qué conserva del material base
        │   │   └── ResourceLocation       namespace:ruta validado (anti path traversal)
        │   ├── allocation/StableAllocator números estables por id (estados de note block, emojis)
        │   ├── ballistics/                ── trazado de rayos de las armas (1.7) ──
        │   │   ├── Vec3 · Ray · Aabb      vectores, rayos y cajas; el test de slabs de rayo contra caja
        │   │   ├── VoxelTraversal         los bloques que cruza un rayo, en orden (Amanatides-Woo)
        │   │   ├── RayCaster              primer impacto: paredes, hitboxes, cabeza, perforación
        │   │   ├── Spread                 direcciones de los perdigones, uniformes dentro de un cono
        │   │   ├── DamageFalloff          daño que se pierde con la distancia
        │   │   └── Shot                   un disparo copiado del mundo; resolve() es toda la cuenta
        │   ├── liquid/
        │   │   ├── FluidSolver            adónde fluye un líquido desde sus fuentes, y qué cambiar
        │   │   ├── TripwireState          los 32 estados de tripwire libres; dos por líquido
        │   │   └── LiquidModels           modelos padre y el blockstate de minecraft:tripwire
        │   ├── hud/
        │   │   ├── HudDefinition          una barra: iconos, altura, posición, de dónde sale su valor
        │   │   ├── HudRenderer · HudLayout el texto de una barra, que acaba donde empezó
        │   │   ├── Spaces                 caracteres de espacio negativo (U+F801…) y positivo
        │   │   ├── GlyphMetrics           el ancho con que el cliente dibuja un icono
        │   │   └── DefaultFontWidths      el ancho de un texto en la fuente vanilla
        │   ├── animation/
        │   │   ├── BbModelReader          .bbmodel de Blockbench -> un modelo por hueso + animaciones
        │   │   ├── AnimatedModel          huesos, clips, texturas e icono, ya en el marco del display
        │   │   ├── Animator               poses (jerarquía, interpolación) y fotogramas para el cliente
        │   │   └── Playback               qué fotogramas salen en cada tick; bucle, mantener, una vez
        │   ├── furniture/BedLayout        las dos mitades de una cama, el display y dónde va la almohada
        │   ├── advancement/
        │   │   ├── AdvancementCompiler    logros del YAML -> el JSON de logro de vanilla, padres antes
        │   │   └── WornSet                si lo que lleva puesto un jugador es el set entero
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
        │   │   ├── GeneratedPacks         el pack generado (zip en partes) -> packs/, y sus números
        │   │   │                          de custom_model_data -> ítems con item_model
        │   │   ├── AssetIndex             modelos, texturas y sonidos en las 5 estructuras de ItemsAdder
        │   │   ├── LegacyText             &c, &#rrggbb... -> MiniMessage
        │   │   └── Rotations              cuaterniones / eje-ángulo -> grados x, y, z
        │   ├── menu/Page                  paginación del explorador
        │   ├── pack/
        │   │   ├── PackCompiler           assets/<ns>/{items,models,textures}, pack.mcmeta y el
        │   │   │                          blockstate de minecraft:note_block
        │   │   ├── PackSettings           contenido de pack.mcmeta
        │   │   ├── ExternalPack           pack de otro plugin (carpeta o ZIP) que se fusiona en el nuestro
        │   │   ├── PackSource             lee ese pack archivo a archivo: assets/ y sus overlays
        │   │   ├── JsonMerge              une los archivos que comparten los packs (atlas, fuentes,
        │   │   │                          sonidos, idiomas, blockstates, custom_model_data) y nombra
        │   │   │                          las colisiones
        │   │   ├── BlockStates            variantes de un blockstate como las empareja el cliente
        │   │   ├── FontCharacters         los caracteres que dibuja una fuente
        │   │   ├── SoundNames             nombres de sounds.json sin namespace que no sonarían
        │   │   ├── ModelDataDispatch      un material vanilla que dibuja cada ítem por su número
        │   │   │                          custom_model_data y el modelo vanilla para cualquier otro
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
            ├── advancement/               AdvancementService (registro, set puesto, anuncio, sonido y
            │                              partículas), AdvancementListener
            ├── emoji/                     EmojiRegistry, ChatEmojiListener (AsyncChatEvent)
            ├── gun/                       GunService (copia → hilo ArkContent-Guns → daño), GunListener
            ├── liquid/                    LiquidService (hilo ArkContent-Fluids), LiquidRegistry,
            │                              LiquidListener (cubos, física, pistones, explosiones)
            ├── hud/                       HudService, HudRegistry, ActionBars (barra de acción
            │                              compartida), HudListener
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
            │   ├── executableblocks/      convivencia de bloques con ExecutableBlocks
            │   ├── jobs/                  dinero y XP de Jobs Reborn y ExcellentJobs (JobsHook)
            │   ├── hmccosmetics/          los ítems como cosméticos, a través de HibiscusCommons
            │   ├── zauctionhouse/         subastas que conservan el aspecto de los ítems
            │   ├── ContentAccess          lo que piden los ganchos de 1.6: id ↔ ítem, bloques
            │   ├── eco/ · nightcore/      los ítems en las búsquedas de eco y de nightcore
            │   ├── mimic/ · itembridge/   registros de ítems compartidos (Mimic, ItemBridge)
            │   ├── worldedit/             bloques en //set, patrones y máscaras
            │   ├── deluxemenus/           material: arkcontent-<id> en sus menús
            │   ├── betonquest/            tipo de ítem, condición y acción de bloque
            │   └── authme/                el pack tras iniciar sesión
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
  `EntitiesLoadEvent`; uno que se queda huérfano con su chunk ya cargado lo retira el verificador
  de cordura (más abajo).
- **Caché + SQLite**: para responder "¿hay un mueble aquí?" desde memoria.

Romperlo: en supervivencia ningún soporte se puede minar (el barrier es irrompible y el light ni se
puede apuntar), así que el golpe al mueble —siguiendo la línea de visión, para alcanzar los de tipo
LIGHT— se convierte en un `BlockBreakEvent` real. Los plugins de protección deciden como con
cualquier bloque, y la retirada (display, vínculo, fila, ítem) ocurre en un solo sitio. En creativo
el barrier se rompe normal y pasa por el mismo sitio. Modo aventura y espectador no rompen muebles.

**Texturas fuera de `block/` e `item/`.** El cliente dibuja los modelos de bloques e ítems —también
los de los displays— con las texturas del atlas `blocks`, y por sí solo solo mete en él las de las
carpetas `textures/block/` y `textures/item/` (comprobado en el cliente 1.21.8). Una textura en
`textures/furniture/`, `textures/crop/` o la de un modelo de Blockbench sale con los cuadros magenta
y negro de «textura no encontrada» si el pack no la nombra. El compilador escribe
`assets/minecraft/atlases/blocks.json` con una fuente `minecraft:single` por cada textura que usa un
modelo fuera de esas dos carpetas; el cliente suma esas fuentes a las suyas. Si un pack fusionado
(MythicArmor) trae su propio `blocks.json`, se conservan sus fuentes y se añaden las nuestras.
Hasta la 1.4.0 este archivo no existía: el pedestal, la lámpara, el taburete, la caja, la cama, el
cofre y los cultivos de la demo se veían magenta en un cliente real.

**Las entidades no se pueden crear fuera del hilo principal** (Paper rechaza un *entity add*
asíncrono), así que el display se crea en el hilo principal en el mismo tick que su bloque, ya
configurado antes de entrar al mundo; lo que sale del hilo principal es la escritura en disco.

### Editor visual de muebles (`/arkcontent editor`)

Para calibrar un mueble —sobre todo uno importado, cuyo modelo flota o se hunde— sin tocar el YAML a
mano ni reiniciar: mira el mueble y escribe `/arkcontent editor` (o `/arkcontent editor <id>` para el
más cercano de ese tipo a 8 bloques). Se abre un cofre de 5 filas:

```
fila 0   [Traslación] [Escala] [Rotación]  ·  [la pieza]  ·  [guardar en]  ·  [ayuda]
fila 1 X   ·  [-1] [-0.1] [-0.01]  [valor X]  [+0.01] [+0.1] [+1]  ·
fila 2 Y   ·  [-1] [-0.1] [-0.01]  [valor Y]  [+0.01] [+0.1] [+1]  ·
fila 3 Z   ·  [-1] [-0.1] [-0.01]  [valor Z]  [+0.01] [+0.1] [+1]  ·
fila 4   [parte por defecto]  ·  [revertir]  ·  [Guardar]  ·  [Cancelar]
```

Arriba se elige qué se edita; los paneles de cristal de cada fila mueven un eje (X rojo, Y verde,
Z azul; el tono oscuro resta, el claro suma) en 0.01, 0.1 o 1 —bloques, factor o grados—, y con
mayúsculas diez veces más (10° con el «1» de la rotación). Cada valor se redondea a 4 decimales, así
que cien clics de 0.01 son exactamente 1; la traslación se limita a ±16 bloques, la escala a 0.01–64
y el ángulo a [-180, 180).

**Hilos.** Cada clic cambia la `Transformation` del `ItemDisplay` en el hilo principal, en ese mismo
tick: el mueble se mueve detrás del menú mientras se pulsa. No se escribe nada al pulsar. Al
**Guardar** —o al cerrar el menú con algo cambiado (Escape, desconexión, apagado)— la escritura se
entrega al hilo `ArkContent-Worker` y el clic vuelve al instante:

- **Para todas las piezas** (por defecto): el worker escribe `translation`, `scale` y `rotation` en
  `items.<id>.furniture.display` del YAML del que salió el ítem, en fila con las recompilaciones
  (nunca mientras una lee `contents/`). Antes copia el archivo a `data/editor-backups/` (las 10
  últimas copias por archivo) y lo sustituye entero de una vez (archivo temporal + movimiento
  atómico): una caída deja el viejo o el nuevo, nunca medio. Luego se recompila y se redibujan las
  piezas cargadas. Mientras eso no termina, ninguna pieza de ese tipo se puede abrir.
- **Solo esta pieza** (botón «guardar en»): una fila en la tabla `furniture_transforms` de SQLite para
  el bloque de la pieza; la memoria la tiene al instante y el worker entrega la fila al hilo
  `ArkContent-DB`, el único que escribe en la base de datos. Se aplica al cargar la pieza y tras
  cada reinicio, y se borra con la pieza. Guardar después «para todas» la quita.

**El YAML se edita, no se reescribe.** Solo cambian esas tres líneas (o se añaden con la sangría del
archivo); comentarios —en su columna—, líneas en blanco, orden y comillas quedan como estaban, y una
sección escrita en una línea (`display: {transform: HEAD}`) sigue en una. Antes de escribir, el texto
nuevo se vuelve a leer y se compara con el viejo: deben dar los tres valores pedidos y todo lo demás
idéntico. Si no —una clave repetida, un mapa en varias líneas, un archivo que no es UTF-8— no se
escribe nada, la pieza vuelve a como estaba y se dice por qué.

**Cancelar** devuelve la pieza a como estaba al abrir; **revertir** hace lo mismo sin cerrar. Un
administrador edita una pieza a la vez y dos no pueden editar la misma. Los muebles animados se
editan para todas sus piezas (sus huesos se posan con los valores del tipo); los dibujados por
ModelEngine no se editan aquí (su modelo es el blueprint).

### Verificador de cordura (`sanity`)

Un rollback (CoreProtect…), `/fill`, WorldEdit o una caída pueden cambiar el mundo a espaldas del
plugin: el bloque de un mueble desaparece y su display sigue flotando, o un bloque personalizado se
convierte en piedra y su fila sigue en SQLite. Cada `sanity.interval-seconds` (120 por defecto) se
auditan los chunks cargados:

- un display del plugin cuyo soporte ya no está —o un hueso de un mueble animado cuya raíz ya no
  existe— se elimina (con sus huesos);
- una fila de bloque cuyo note block ya no tiene el estado de ese bloque, una de mueble cuyo soporte
  ya no está y una fuente de líquido cuyo tripwire ya no es el de ese líquido se purgan: cada una en
  una transacción que solo borra si la fila sigue diciendo lo mismo que cuando se encontró (y, si es
  un mueble, su transformación propia con ella).

**Dos auditorías seguidas.** Un hallazgo solo se toca cuando dos auditorías consecutivas lo encuentran
igual (la misma fila y el mismo bloque encontrado en su sitio): un rollback a medias o un `/fill`
que va por chunks parecen un fallo un momento y están bien al siguiente. Antes de borrar se mira una
vez más en el hilo principal. El contenido que ya no está definido no se toca (puede volver con el
siguiente `reload`), y nunca se carga un chunk para auditarlo.

**Hilos.** El ciclo se programa y se juzga en `ArkContent-DB`: allí se planifica (qué filas de qué
mundos, por chunk, desde memoria) y se decide. Leer el mundo solo se puede en el hilo principal, así
que se mira allí por trozos, sin pasar de `sanity.max-millis-per-tick` (1 ms) por tick —en la prueba,
288 chunks en 2 ticks y 2–3 ms en total—, y los borrados de filas vuelven a `ArkContent-DB`.

**Silencioso.** Una línea en consola cuando se limpió algo, nada si no; un fallo se avisa una vez y
la siguiente auditoría se hace igual. `/arkcontent info` muestra la última auditoría y el total
retirado.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.8)

Con un bot (mineflayer) y la consola, en el servidor de pruebas con 1608 ítems y 31 plugins:

| | Resultado |
|---|---|
| Abrir el editor | mirando un `demo:ruby_pedestal` colocado, `/arkcontent editor` abre un cofre de 5 filas «Editing demo:ruby_pedestal» con los botones de la tabla de arriba |
| En vivo | 3 clics en Y +0.1 → `data get` del `ItemDisplay`, con el menú aún abierto: `translation [0, 0.3, 0]`; 9 mayúsculas+clic en rotación Y +1 → `left_rotation [0, 0.7071, 0, 0.7071]` (90°) |
| Guardar para todas | `contents/demo/blocks.yml` con las tres líneas nuevas y los comentarios en su sitio, copia en `data/editor-backups/demo/`, recompilación y «2 loaded piece(s) redrawn»; un pedestal nuevo sale con los valores nuevos |
| Solo esta pieza | fila en `furniture_transforms`; tras reiniciar el servidor la pieza vuelve con `translation [-0.05, 0, 0]`; guardar luego «para todas» borra la fila |
| Cancelar | la pieza vuelve a los valores de antes de abrir |
| Apagar con el editor abierto | `stop` con la escala cambiada: el YAML queda con `scale: [1, 1.02, 1]` (escrito por el worker antes de pararlo) |
| Reabrir durante la recompilación | rechazado («still being saved and rebuilt»); después abre con los valores nuevos |
| Verificador | `/setblock` quita el soporte de un pedestal y cambia un bloque de rubí por piedra: a los ~16 s (dos auditorías de 10 s) se elimina el display y se purgan las 2 filas; el pedestal editado sigue; un arranque limpio no retira nada. En el mundo de pruebas purgó además 5 filas viejas de pruebas anteriores —dentro de la zona que los guiones vacían con `/fill`—; las 10 que quedaron se comprobaron una a una contra el mundo |
| IP pública | con `public-address` vacío, un servicio que responde `192.168.1.20` y otro caído se saltan; ipify da la IP de salida y el enlace del pack la lleva |
| Puertos | 8163–8166 ocupados: tres intentos en 8163, luego 8164–8166, aviso en consola y al administrador al entrar, el plugin arranca; al liberar 8163, «up now» en ≤60 s y el pack se reenvía. Solo 8163 ocupado: sirve en 8164 y el enlace se publica de nuevo con `:8164` |

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
- **Esquema.** `PRAGMA user_version` lleva la versión: 1 (`custom_blocks_world`), 2 (+ `custom_crops`),
  3 (+ `furniture_storage`, ver *Muebles con almacenamiento*), 4 (+ `advancement_unlocks`, ver
  *Logros*), 5 (+ `liquid_sources` y `hud_values`, ver *Líquidos* y *HUDs*) y 6 (+ `furniture_transforms`,
  ver *Editor visual de muebles*). Un archivo de una versión anterior
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
2. Pon en `http.public-address` la IP pública o el dominio del servidor, o déjalo vacío para que se
   descubra sola (abajo).

**IP pública automática.** Con `http.public-address` vacío, al arrancar se pregunta la IP pública de
la máquina a unos servicios ligeros (`http.address-services`: ipify, checkip de Amazon, icanhazip,
ifconfig.me) con el `HttpClient` de Java, de forma asíncrona y uno tras otro hasta que uno responda.
Solo se acepta una dirección IP escrita como tal y pública: una privada, de bucle local, de
documentación o una página de portal cautivo se descarta y se pregunta al siguiente. La IP encontrada
se inyecta en tiempo de ejecución en el enlace del pack; si el pack ya se había publicado, se vuelve
a publicar con el enlace nuevo y se reenvía a quien esté conectado. **No se escribe en `config.yml`**:
dejarlo vacío hace que se descubra en cada arranque, así un hosting que cambia la IP no deja un valor
viejo pegado. Si `server-ip` ya es una IP pública, se usa esa (es donde escucha el juego). Si ningún
servicio responde, se avisa y se usa `server-ip` o `127.0.0.1`. `http.detect-public-address: false`
lo apaga.

**Puerto ocupado o denegado.** Si `http.port` no se puede abrir, el plugin arranca igual y aplica una
regla de contingencia: el mismo puerto dos veces más (2 s y 4 s: el arranque anterior aún puede
tenerlo), luego cada puerto de `http.fallback-ports` (vacío: los tres siguientes; para un puerto
privilegiado <1024 sin root, 8080, 8163 y 8164), y si ninguno sirve, el puerto configurado cada 60 s
hasta que quede libre. Una `bind-address` que no es de la máquina se cambia por `0.0.0.0`. Si acaba en
otro puerto, el enlace del pack lo lleva y se avisa en consola y a los administradores al entrar
(los jugadores solo descargan de un puerto abierto para ellos: pon en `fallback-ports` puertos que tu
hosting redirija). Cuando el servidor web vuelve, el pack se reenvía a quien lo pidió mientras no
había nadie escuchando.

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

**Los modelos de la demo (1.5).** El cofre tiene 37 cubos: cantoneras de oro en L en cada arista,
dos bandas de oro alrededor del cuerpo, patas, una cerradura con su rubí, asas de hierro a los lados
y una tapa abombada en tres escalones con flejes de oro y un rubí tallado encima (un cubo girado
45°). Conserva los mismos huesos y animaciones (`open`, `close`, `shake`), así que la tapa entera
—cúpula, flejes y rubí— se abre de una pieza. La cama tiene 24: colchón, manta que cae por un
lado, sábana doblada, dos almohadas, un cabecero con dos postes rematados en rubí y un panel
tallado con cresta en arco, y un piecero más bajo. Ambos siguen dentro de las medidas de arriba.

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

**Actualizar desde una versión anterior.** El pack de ejemplo se copia entero solo cuando
`contents/` no existe. Para que lo nuevo llegue también a un servidor que ya lo tenía, cada versión
agrupa los ejemplos que añadió (1.1: cultivos y emojis; 1.3: `furniture.yml` con el cofre y la cama;
1.4: la armadura; 1.5: `advancements.yml` y el fondo de su pestaña) y el plugin recuerda en
`data/examples_offered.txt` qué grupos ofreció. Al
reiniciar con un jar nuevo copia en `contents/demo/` los grupos que falten y lo dice en consola. Nunca
sobrescribe nada; no recrea `contents/demo/` si se borró; un grupo del que ya hay algún archivo se
deja como está (vino con una versión anterior y lo que falta lo borró el dueño); y lo que se borre
después no vuelve. Verificado en el servidor de prueba: un `contents/demo` sin `furniture.yml` y con
la marca que dejó 1.4.0 recibe el cofre y la cama (4 archivos) y no toca los cultivos, que ya
estaban; sin la armadura, recibe sus 9 archivos; borrar `armor.yml` y recargar deja 11 ítems.

**Ejemplos que cambian de una versión a otra (1.5).** Un ejemplo que ya existe tampoco se
sobrescribe… salvo que siga siendo, byte a byte, uno de los que publicó una versión anterior: eso
significa que nadie lo editó. `examples-history.txt` (dentro del jar) guarda el SHA-1 de cada versión
publicada de cada ejemplo, y al arrancar se reemplaza el que coincide con alguno. Así el cofre y la
cama detallados, y los `jobs:` de `blocks.yml` y `crops.yml`, llegan a un servidor que tenía los de
1.3 sin tocar; un archivo con un solo cambio del dueño ya no coincide y no se toca. Verificado:
arrancar 1.5.0 sobre un `contents/demo` de 1.4.1 añade `advancements.yml` y su textura y actualiza los
5 ejemplos sin editar (el pack pasa a otro SHA-1).

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

### Logros (`advancements`)

```yaml
# contents/demo/advancements.yml
advancements:
  arkcronist:                       # sin padre: abre una pestaña propia
    title: "<gradient:#ff5a6e:#b3122e><b>Arkcronist</b></gradient>"
    description: "<gray>Rubíes, muebles y la armadura del Caballero del Rubí de Sangre"
    icon: ruby
    background: gui/advancements/ruby          # textures/gui/advancements/ruby.png de este pack

  first_ruby:
    parent: arkcronist
    title: "Rojo como la sangre"
    description: "<gray>Consigue un rubí"
    icon: ruby
    trigger:
      obtain: ruby

  ruby_knight:
    parent: first_ruby
    title: "<gradient:#ff5a6e:#b3122e><b>¡Cúbrete de Rubí!</b></gradient>"
    description: "<gray>Viste a la vez las cuatro piezas del Caballero del Rubí de Sangre"
    icon: ruby_chestplate
    frame: challenge
    trigger:
      wear: [ruby_helmet, ruby_chestplate, ruby_leggings, ruby_boots]
    announce: "<gradient:#ff5a6e:#b3122e:#5e0716><b><player></b> se ha cubierto de rubí</gradient> <dark_gray>·</dark_gray> <advancement>"
    reward:
      experience: 100
```

| Clave | Por defecto | |
|---|---|---|
| `title`, `description` | — | MiniMessage (degradados, colores, `<b>`…) |
| `icon` | — | un ítem: de este plugin (`ruby`, `demo:ruby`) o vanilla (`minecraft:diamond`) |
| `frame` | `task` | `task`, `goal` o `challenge` |
| `parent` | ninguno | el logro de encima; sin él, abre una pestaña. Puede colgar de una pestaña vanilla (`minecraft:story/root`) |
| `background` | piedra vanilla | fondo de la pestaña (solo en la raíz): una textura de este pack o una vanilla (`minecraft:block/blackstone`) |
| `trigger` | `join` en una raíz, `manual` en el resto | `join`, `manual`, `{obtain: <ítem>}` o `{wear: [<ítems>]}` |
| `announce` | la línea vanilla | MiniMessage para todo el servidor la primera vez que un jugador lo completa: `<player>`, `<advancement>` |
| `celebrate` | sí (no en `join`) | sonido de desafío alrededor del jugador y ráfaga de partículas carmesí |
| `toast`, `hidden` | sí, no | el aviso de la esquina; esconderlo del árbol hasta completarlo |
| `reward.experience` | 0 | puntos de experiencia |

**Por qué no van en `assets/<ns>/advancements/`.** Un logro no es un recurso: el cliente no lee
logros de un resource pack. Son **datos del servidor**, como las recetas: el servidor tiene el árbol
y el progreso de cada jugador, y se los envía al cliente. Un `advancements/` dentro del ZIP no lo
leería nadie. Por eso el plugin los **registra en el servidor** —con `loadAdvancement` de Paper, al
arrancar, tras `/arkcontent reload` y tras un `/minecraft:reload`— y el progreso lo guarda el propio
servidor en `world/advancements/<uuid>.json`, como el de los vanilla (`/advancement grant|revoke`
funcionan). Al pack solo va lo que se ve: el icono, que es el `item_model` del ítem, y el fondo de la
pestaña cuando es una textura propia, que el cliente lee tal cual de `textures/<id>.png`.

El JSON que se registra es el del formato de 1.21.8 (el mismo que `data/minecraft/advancement/` del
jar del servidor). Cada disparador es uno que el servidor ya conoce:

| `trigger` | Criterio vanilla | Quién lo concede |
|---|---|---|
| `join` | `minecraft:tick` | el servidor, en el primer tick: todos tienen la pestaña |
| `obtain` | `minecraft:inventory_changed` con `items` = el material y `components` = `{minecraft:item_model: …}` | el servidor, al entrar el ítem en el inventario: un material vanilla sin ese modelo no cuenta |
| `wear` | `minecraft:impossible` | el plugin (vanilla no tiene un disparador «llevar un set») |
| `manual` | `minecraft:impossible` | `/advancement grant` u otro plugin |

Antes de registrar nada se comprueba lo que solo se puede comprobar con todo leído: que cada padre
existe y que ninguna cadena de padres forma un bucle (se registran los padres antes que los hijos);
que el icono y los ítems de `obtain`/`wear` son ítems reales; que cada pieza de `wear` tiene sección
`equipment` y que no hay dos para el mismo hueco. Lo que falla se dice en consola y no se registra.

**El set puesto.** `PlayerArmorChangeEvent` de Paper salta *después* de que cambie un hueco de
armadura, se haya puesto como se haya puesto: clic derecho, arrastrar en el inventario, mayúsculas+clic,
dispensador, `/item replace` o al reaparecer. Por eso no hace falta escuchar también
`InventoryClickEvent`: ese evento salta *antes* del cambio (habría que esperar un tick) y no ve los
dispensadores ni los comandos. Al entrar, un tick después, se comprueba lo que ya lleva puesto.
Que una pieza «cuenta» no se decide por su aspecto: tiene que ser el ítem del plugin (su etiqueta de
id), del material de su definición (`NETHERITE_*`, es decir, con la armadura, dureza y resistencia
al empuje de la netherita), en su hueco y con el asset de equipo de su definición. Unas botas de
netherita hechas con `/give … [item_model="demo:ruby_boots", equippable={…asset_id:"demo:ruby_armor"}]`
se ven igual, pero no cuentan.

**Al completarlo** (`PlayerAdvancementDoneEvent`, sea cual sea el disparador):

- **Anuncio.** El texto de `announce`, con MiniMessage y `<advancement>` como lo pinta vanilla
  (título entre corchetes, descripción al pasar el ratón), a todo el servidor y solo **la primera vez**
  que ese jugador lo consigue: la tabla `advancement_unlocks` (`player_uuid`, `advancement`,
  `unlocked_at`) de SQLite lo recuerda. Revocarlo y volver a ganarlo vuelve a celebrarse, pero no a
  anunciarse. Con `announce`, el `announce_to_chat` vanilla va apagado para no decirlo dos veces.
- **Sonido.** `ui.toast.challenge_complete` en la posición del jugador para todos los que estén a
  48 bloques (el propio jugador ya lo oye de su aviso cuando el logro es `challenge` con `toast`).
- **Partículas.** Una espiral carmesí que sube alrededor del jugador y una ráfaga final
  (`DUST` y `DUST_COLOR_TRANSITION`, de #FF566A a #5E0716), 14 fotogramas. Se toman en el hilo
  principal la posición y la lista de jugadores; los paquetes se envían desde una tarea asíncrona
  del planificador, sin tocar el mundo.
- **Experiencia.** `reward.experience` la da el propio servidor (es `rewards.experience` vanilla).

Los eventos de Bukkit se reciben siempre en el hilo principal (no existe un listener «asíncrono» de
`PlayerArmorChangeEvent`); lo que hace el listener ahí es barato —comparar cuatro stacks— y lo
costoso, la base de datos y las partículas, va fuera de él.

**Recargar.** `loadAdvancement` añade, pero no puede cambiar ni quitar un logro. Si tras
`/arkcontent reload` uno cambió o desapareció, el plugin hace la recarga de datos del servidor
(`/minecraft:reload`, que también recarga datapacks y recetas) y vuelve a registrar los suyos; si
solo hay nuevos, solo los añade. Antes de registrar se guarda a los jugadores conectados, porque
registrar relee su progreso del disco. Paper escribe además una copia de cada logro en
`world/datapacks/bukkit/data/<ns>/advancements/`, una carpeta que desde 1.21 el juego ya no lee: esas
copias no sobreviven a su YAML.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.5, logros)

Dos bots: el que se viste y uno que mira.

| Comprobación | Resultado |
|---|---|
| Entrar | `demo:arkcronist` concedido (la pestaña aparece), sin línea en el chat |
| `/customgive … demo:ruby` | `demo:first_ruby`, por `inventory_changed` sobre el `item_model` |
| Tres piezas puestas | `ruby_knight` **no** concedido |
| Tres piezas + las botas falsas de `/give` | **no** concedido |
| Las cuatro piezas | concedido; el anuncio en degradado llega a los dos bots, una vez |
| Partículas | 122 paquetes al que se viste y 122 al que mira |
| Sonido | el que mira recibe `ui.toast.challenge_complete` en la posición del otro |
| Experiencia | +100 puntos |
| Revocar y volver a ponerse el set | se concede y se celebra (122 partículas), sin segundo anuncio |
| `/arkcontent reload` sin cambios, con un título cambiado, y `/minecraft:reload` | el progreso se conserva en los tres; tras cambiar el título el servidor ya usa el nuevo (la consola anuncia «Rojo como la sangre (v2)») |
| SQLite | `PRAGMA user_version` = 4; una fila por jugador y logro en `advancement_unlocks` |

### Números de CustomModelData (`pack.custom-model-data`)

Muchos plugins solo saben pedir un ítem como «este material con este número»: los pergaminos de
ClueScrolls, los ítems flotantes de Holographic Displays, el `model_data` de DeluxeMenus o
BossShop, recompensas y menús en general. No pueden poner un `item_model`. ItemsAdder los cubre
dando un número a cada ítem (`/iacustommodeldata`); este plugin hace lo mismo:

```
/arkcontent cmd demo:ruby          ->  demo:ruby: material PAPER, custom_model_data 10000
%arkcontent_cmd_demo:ruby%         ->  10000
```

```yaml
# un menú, una recompensa... que solo admite material + número
material: PAPER
custom-model-data: 10000          # se ve como el rubí
```

- **Números estables.** Cada ítem recibe uno a partir de `first` (10000), el primero libre, y lo
  conserva para siempre en `data/custom_model_data.json`, como los estados de note block y los
  caracteres de emoji: añadir o quitar ítems no mueve el de nadie.
- **En el pack.** Para cada material usado, `assets/minecraft/items/<material>.json` despacha por
  `custom_model_data`: el número de un ítem dibuja ese ítem (su propia definición, con lo que
  tenga) y el número siguiente vuelve al modelo vanilla, de modo que un número intermedio no toma
  prestado el aspecto del anterior. Cualquier otro número, o ninguno, se dibuja como el vanilla. Si
  otro pack fusionado también numera ese material, sus entradas se conservan (`JsonMerge`).
- **En las stacks.** Las del plugin llevan también su número, así que un plugin que reconoce ítems
  por material y número reconoce el de verdad. Lo que lo dibuja sigue siendo el `item_model`.
- **Qué materiales.** Solo los que en vanilla son un único modelo (1274 de los 1416 ítems de 1.21.8,
  lista en `vanilla-item-models.txt`). Los que tienen definición propia —arcos y ballestas que se
  tensan, estandartes, armaduras con adornos, relojes, brújulas, cofres, camas, ítems teñidos— se
  dejan fuera: reemplazar su definición les quitaría lo que hacen. Sus ítems no reciben número
  (`/arkcontent cmd` lo dice). En la demo: rubí, semillas, espada, bloque y muebles sí; armadura,
  cofre y cama no.

### Armas de fuego (`gun`)

Cualquier ítem con una sección `gun:` dispara con clic derecho y recarga con la tecla de cambiar de
mano (F). El ejemplo completo, con todas las claves comentadas, está en `contents/demo/guns.yml`:

```yaml
ruby_pistol:
  material: IRON_HORSE_ARMOR
  resource: { texture: item/ruby_pistol }
  gun:
    damage: 6                      # medio corazón por punto, por perdigón
    headshot-multiplier: 1.75
    range: 64
    falloff: { start: 24, min-factor: 0.5 }   # entero hasta 24 bloques, la mitad a 64
    pellets: 1                     # 8 para una escopeta; spread es el cono en grados
    spread: 0.6
    magazine: 12
    ammo: ruby_bullet              # un ítem de este plugin o un material; sin ammo, no gasta nada
    reload-seconds: 1.4
    fire-rate: 4                   # disparos por segundo como máximo
    recoil: { pitch: 1.8, yaw: 0.6 }
    targets: [players, mythic_mobs, mobs]
```

**Cómo es un disparo.** Leer el mundo solo se puede en el hilo del servidor, así que cada disparo
va en tres pasos, cada uno en el hilo que le corresponde:

1. **Hilo principal, la copia.** Las direcciones de los perdigones (un cono uniforme), las cajas de
   colisión de los bloques que cruza cada línea —recorridos bloque a bloque con
   `VoxelTraversal`, hasta el primer bloque entero— y las hitboxes de las entidades vivas cerca de
   la línea, con una caja de cabeza encima de cada una. Solo valores: ninguna entidad ni bloque.
2. **`ArkContent-Guns`, el trazado.** Un `CompletableFuture` en el hilo de las armas resuelve el
   disparo entero (`Shot.resolve()`): paredes y medias losas que paran o no, primer impacto, si
   entró por la cabeza, perforación, caída del daño, y la suma de los perdigones por objetivo.
3. **Hilo principal, el daño.** Un solo golpe por objetivo, con la suma de sus perdigones, como
   `DamageSource` cuya entidad causante y directa es el tirador: para el resto del servidor es el
   ataque de un jugador. El flag `pvp` de WorldGuard, la protección PvP de los claims de
   GriefPrevention, las mecánicas de daño y la tabla de amenaza de MythicMobs, la armadura y los
   encantamientos lo ven así. Los sonidos suenan en el mundo, para todos los que estén cerca.

El cargador vive en la propia stack (PDC), así que viaja con el arma a un cofre o a otro jugador.
Mientras se dispara, la cuenta se lleva en memoria y se escribe en la stack al guardarla, moverla,
soltarla, recargar o guardar el mundo: cada cambio de la stack hace que el cliente baje y suba el
ítem en la mano, y en cada disparo el arma no pararía de cabecear. Recargar tarda `reload-seconds`
y se cancela si el arma sale de la mano; con el cargador vacío, el siguiente clic recarga solo. En
creativo no se gasta munición.

El retroceso gira la cámara del jugador desde el servidor (Paper lo envía como
`player_rotation`): no toca su posición, su velocidad ni el vehículo en el que vaya.

Placeholders: `%arkcontent_gun_ammo%`, `%arkcontent_gun_magazine%`, `%arkcontent_gun_reserve%` (la
munición que lleva) y `%arkcontent_gun_reloading%`, del arma de la mano; vacíos sin arma. Se leen de
valores guardados para ellos, así que TAB puede pedirlos desde su propio hilo.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.7, armas)

| Qué | Resultado |
|---|---|
| Daño al cuerpo de un zombi sin armadura | 18 → 12: **6,0**, exactamente `damage` |
| Disparo a la cabeza | 12 → 1,5: **10,5** = 6 × 1,75 |
| Una pared de piedra en medio | 0 de daño |
| MythicMobs 5.13 (`RubyKnight`) | muere en 4 disparos con «Ruby Knight was shot by Gunner» y suelta sus drops `arkcontent{…}` |
| Otro jugador, PvP permitido | pierde vida; con `/rg flag __global__ pvp deny`, 0 |
| Cargador | 12 → 9 tras tres disparos; vacío, recarga sola y toma 12 balas (reserva 20 → 8) |
| Escopeta, 8 perdigones a 5 bloques | 4 aciertan: 10 de daño en un solo golpe; munición 6 → 5 |
| Retroceso | el cliente recibe `player_rotation` y la cámara sube 1,8° por disparo |
| El arma en la mano | 31 actualizaciones de su casilla en toda la prueba, con solo 5 versiones distintas de la stack: no cambia en cada disparo |
| Mensaje de munición con barras de HUD | en la misma barra de acción, centrado, sin mover las barras |

Con **AuraSkills** y **mcMMO** instalados, el daño de las armas cambia, porque los dos lo tratan
como el ataque de un jugador y aplican sus bonificaciones; las cifras de arriba son con los dos
retirados. Un jugador recién conectado es invulnerable unos segundos, como en vanilla.

### Líquidos (`custom_liquid`)

Una entrada `type: custom_liquid` es un cubo: con clic derecho vierte una fuente, que fluye como el
agua; con un cubo vacío se recoge. `contents/demo/liquids.yml` trae ácido y escarcha líquida:

```yaml
acid:
  type: custom_liquid
  display-name: "<!i><#7ee05a>Cubo de ácido"
  resource: { texture: item/acid_bucket }       # el cubo; sin él, el de agua vanilla
  liquid:
    texture: block/acid                         # acid.png + acid.png.mcmeta: animada y translúcida
    flow-distance: 4                            # cuánto se extiende de lado (1-8)
    tick-rate: 8                                # ticks entre un paso y el siguiente
    max-fall: 32
    contact:
      element: acid                             # fire, frost, poison, wither o acid
      damage: 1.5
      interval-ticks: 20
      effects: [ { type: poison, duration: 60 } ]
```

**Dónde vive.** Cada líquido se dibuja con dos estados de `minecraft:tripwire` que vanilla no
usa nunca (`disarmed=true, powered=false`): uno para la fuente y otro para el flujo. El tripwire se
dibuja translúcido y no tiene colisión, así que el líquido se ve a través y se puede entrar en él.
Hay 32 de esos estados, para 16 líquidos. Se reparten primero los 16 con `attached=true`: Bukkit
avisa antes de que algo los pise, y el plugin lo cancela, así que esos estados no cambian nunca.
Los líquidos 9 a 16 usan los de `attached=false`, que vanilla pisa sin avisar; el plugin los
devuelve a su estado en el mismo tick. Vanilla también reconecta la cuerda con sus vecinos cada vez
que uno cambia; el plugin corta esa propagación de un bloque de líquido al siguiente y devuelve a su
estado el que haya tocado (lleva un índice del estado que debe tener cada bloque).

**Cómo fluye.** Solo se guardan las **fuentes**: en memoria y en SQLite (`liquid_sources`). El
flujo se deduce de ellas y del terreno, y se vuelve a calcular cuando algo cambia cerca (construir,
romper, explosiones, pistones, cargar el chunk):

1. **Hilo principal.** Se copian como `ChunkSnapshot` los chunks alrededor del cambio, que se pueden
   leer desde cualquier hilo, con las fuentes que podrían llegar a ellos.
2. **`ArkContent-Fluids`.** `FluidSolver` calcula dónde debe haber líquido —cae mientras puede, al
   aterrizar se extiende `flow-distance` bloques, perdiendo fuerza en cada paso— y lo compara con la
   copia: qué bloques llenar y cuáles vaciar. Toda la búsqueda va en este hilo.
3. **Hilo principal, poco a poco.** Los cambios se aplican del más cercano a la fuente al más
   lejano, un paso cada `tick-rate` ticks —se ve correr el líquido— y como mucho
   `liquids.changes-per-tick` por tick. Cada uno se comprueba otra vez contra el mundo de ese
   momento: el bloque sigue vacío y el líquido sigue al lado.

**Protección.** El líquido nunca entra en una región de WorldGuard ni en un claim de GriefPrevention
desde fuera (la misma regla que siguen el agua y la lava vanilla), ni donde una región tenga
`arkcontent-liquid-flow deny`. Verterlo pide permiso de construir; recogerlo, de romper. El contacto
no hace daño donde una región tenga `arkcontent-liquid-damage deny`, ni a quien esté dentro de un
claim al que ese líquido no podría haber llegado desde su fuente. Los dos flags son de este plugin y
se registran en WorldGuard al cargar el servidor:

```
/rg flag spawn arkcontent-liquid-flow deny
/rg flag spawn arkcontent-liquid-damage deny
```

**Contacto.** Cada `liquids.contact-ticks` se mira qué jugadores tienen los pies o la cabeza en un
líquido. A quien está dentro se le aplica, cada `interval-ticks`, el daño (con el `damage-type`
vanilla que se diga), el fuego, la congelación y los efectos del líquido.

Además, el líquido no se rompe a golpes, no se lo llevan pistones, explosiones ni agua vanilla, y
no hace saltar ganchos de tripwire.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.7, líquidos)

| Qué | Resultado |
|---|---|
| Verter ácido sobre hierba | la fuente aparece; el cubo pasa a ser un cubo vacío |
| Flujo en llano (`flow-distance: 4`) | llega a los bloques a 1, 2 y 3 de la fuente; los que están a 4 siguen siendo aire |
| Saliente: fuente sobre un pilar de 4 bloques | cae por el lado hasta el suelo y allí se extiende 3 bloques más |
| Cuerda vanilla colocada junto al líquido | el bloque de líquido no cambia de estado |
| Un jugador dentro | 17 → 14 de vida y envenenado; con `arkcontent-liquid-damage deny`, nada |
| `arkcontent-liquid-flow deny` | la fuente se queda sola, sin flujo |
| Recoger con un cubo vacío | la mano pasa a tener el cubo de ácido; la fuente y su flujo desaparecen |
| Reinicio | «3 liquid source(s) loaded»; un bloque de flujo borrado sin eventos (`/setblock … air`) vuelve a aparecer al arrancar |
| Base de datos de 1.6 | pasa sola a `user_version` 5 con las tablas nuevas |

Lo que no se puede comprobar aquí es cómo se ve: no hay un cliente real con el pack. El blockstate
de tripwire (los 128 estados) y los modelos de los líquidos se han revisado en el ZIP generado.

### HUDs (`huds`)

Barras dibujadas con la fuente del pack, como el maná o la sed. Cada barra es una fila de iconos
(lleno, medio, vacío) escrita con **espacios negativos**: caracteres que no dibujan nada y mueven
el texto unos píxeles a la derecha o a la izquierda. Así la barra queda justo donde se pone, y el
resto de la línea no se mueve. `contents/demo/huds.yml`:

```yaml
huds:
  thirst:
    icons: { full: hud/thirst_full, half: hud/thirst_half, empty: hud/thirst_empty }
    height: 9
    ascent: -5              # en la barra de acción, -5 la deja justo encima de la armadura
    segments: 10
    spacing: -1             # píxeles entre iconos
    offset: 10              # desde el centro: encima del hambre
    action-bar: true
    value:                  # un valor que guarda este plugin
      max: 20
      per-second: -0.025    # se va vaciando
      empty-damage: 1       # daño por segundo a 0
      consume: { POTION: 8, MILK_BUCKET: 4 }
  mana:
    icons: { full: hud/mana_full, empty: hud/mana_empty }
    value: { placeholder: "%mmocore_mana%", max: "%mmocore_max_mana%" }   # o el de otro plugin
```

**Fuente.** El pack escribe en `assets/minecraft/font/default.json` un glifo por icono, en
U+F000–U+F7FF (los emojis pasan a U+E000–U+EFFF), y un proveedor `space` con los caracteres que la
comunidad ya usa para esto: U+F801…F808 (−1 a −8 px), U+F809…F80F (−16 a −1024), U+F821…F82F (lo
mismo hacia delante). Se escribe aunque no haya HUDs, para menús y marcadores que maquetan con
ellos (`pack.negative-spaces: false` lo quita). El ancho de cada icono se mide en su PNG como lo
mide el cliente (última columna con algún píxel visible, escalada a `height`, más uno). Así la barra
sabe exactamente cuánto retroceder.

**Dónde se muestra.**

| Placeholder | Devuelve |
|---|---|
| `%arkcontent_hud_<id>%` | la barra, lista para pintar |
| `%arkcontent_hud_<id>_value%` · `_max` | su valor y su máximo |
| `%arkcontent_space_<píxeles>%` | ese desplazamiento, positivo o negativo |

Sirven en cualquier sitio que lea PlaceholderAPI: TAB (marcador, cabecera, pie, bossbar),
DeluxeMenus (títulos, nombres, lore), hologramas. Las barras con `action-bar: true` las envía
además el plugin a la barra de acción cada `huds.action-bar-ticks`. La comparte con los mensajes
cortos (la munición de un arma), que se centran sin desplazar las barras.

**Valores guardados.** Los HUD sin `placeholder` guardan su valor por jugador en SQLite
(`hud_values`): se leen al entrar, cambian cada segundo según `per-second`, suben al comer o beber
lo que diga `consume` (un material o el id de un ítem de este plugin) y se escriben cada
`huds.save-seconds` y al salir. Para cambiarlos a mano, `/arkcontent hud <jugador> <hud> set|add|take <n>`.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.7, HUDs)

| Qué | Resultado |
|---|---|
| `%arkcontent_hud_thirst%` a 19,9 de 20 | +10 px, 9 gotas llenas separadas por −1 px, 1 media, y −91 px de vuelta: ancho neto 0 |
| `/arkcontent hud … thirst set 7` | 3 llenas, 1 media, 6 vacías; `_value` = 7 |
| Beber una botella de agua | 7 → 14,9 (`consume: POTION: 8`, menos lo que se vacía) |
| `%arkcontent_space_-20%` | U+F809 U+F804 |
| Barra de acción | sed (desde +10 px) y maná (desde −91 px) en cada envío |
| **TAB 6.2.0** | la cabecera de la tablist trae la barra de sed |
| **DeluxeMenus 1.14.1** | el título del menú trae la barra y «Sed 14.8/20» |
| Persistencia | una fila por jugador y HUD en `hud_values` |

AuraSkills envía su propia barra de acción (vida y maná) varias veces por segundo y tapa la de este
plugin. Con AuraSkills, desactiva la suya (`action_bar` en su configuración) o muestra las barras
por placeholders. El texto de la barra de acción y de TAB lleva sombra; en la barra de acción el
plugin la quita, pero lo que llega por PlaceholderAPI es texto plano y la sombra depende de quien
lo pinte.

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
| Jobs Reborn | antes de este | se le paga al romper o cosechar |
| HibiscusCommons | antes de este | el gancho de ítems se registra en esa librería… |
| HMCCosmetics | después de este | …antes de que HMCCosmetics lea sus cosméticos |
| zAuctionHouse v4 | antes de este | sus eventos de venta y retirada se escuchan desde el arranque |
| eco | antes de este | el proveedor `arkcontent` se añade a su búsqueda de ítems… |
| EcoItems, EcoArmor, EcoEnchants, EcoMobs, Reforges, StatTrackers, Talismans, EcoSkills, EcoShop… | después de este | …antes de que lean sus configuraciones |
| nightcore | antes de este | el adaptador `arkcontent` se registra en su `ItemBridge`… |
| ExcellentCrates, ExcellentShop, ExcellentEnchants, ExcellentJobs | después de este | …antes de que lean sus recompensas y productos |
| Mimic, ItemBridge, WorldEdit, BetonQuest, AuthMe | antes de este | este plugin se registra en sus APIs al habilitarse |
| DeluxeMenus | antes de este | el gancho se añade a su tabla; ver su apartado |

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

**Orden de carga (desde 1.7).** Hasta 1.6, ArkcronistContent cargaba antes que MythicMobs para estar
escuchando cuando este lee sus mobs. Con AuraSkills y BetonQuest también instalados, que cargan
después de MythicMobs y antes que este plugin, eso forma un ciclo, y Paper se niega a arrancar
(`Circular plugin loading`). Pasó de verdad en el servidor de prueba. Ahora MythicMobs carga
primero, y si alguno de sus archivos usa `arkcontent{`, el plugin ejecuta `/mythicmobs reload` una
vez, un tick después de arrancar. MythicMobs vuelve a leer los mobs con el gancho escuchando.
Verificado con AuraSkills, BetonQuest y MythicMobs juntos: arranca, recarga MythicMobs, y el
`RubyKnight` lleva su espada y suelta sus drops.

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
| `%arkcontent_gun_ammo%` · `_magazine` · `_reserve` · `_reloading` | el arma de la mano (sección 4, *Armas*) |
| `%arkcontent_hud_<id>%` · `_value` · `_max` | una barra de HUD y su valor (sección 4, *HUDs*) |
| `%arkcontent_space_<píxeles>%` | espacio positivo o negativo, para maquetar |

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
del emoji y el número de ítems. En 1.7, con **TAB 6.2.0** instalado, la cabecera de la tablist
llega al cliente con la barra de `%arkcontent_hud_thirst%`; y el título de un menú de DeluxeMenus,
con la barra y su valor.

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
| Un líquido de este plugin que fluye | `build` + `block-break` desde su fuente, y `arkcontent-liquid-flow` | mismo claim o mismo dueño que su fuente |
| Un líquido que hace daño a quien está dentro | `arkcontent-liquid-damage` | el líquido podría haber llegado ahí desde su fuente |
| Verter / recoger un líquido | `build` + `block-place` / `block-break` | `/trust` |
| Un disparo a un jugador | `pvp` (el disparo es el ataque de un jugador) | su protección PvP en claims |

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

### Jobs Reborn y ExcellentJobs

Para un plugin de trabajos, un bloque de este plugin es un note block y un cultivo es un
`ItemDisplay`: su propia configuración no paga por ellos. La sección `jobs:` de un bloque o de un
cultivo dice cuánto se paga, por trabajo:

```yaml
# contents/demo/blocks.yml
  ruby_block:
    type: custom_block
    block:
      jobs:
        Miner: {money: 2.5, xp: 4}      # el id del trabajo en el plugin de trabajos

# contents/demo/crops.yml
  ruby_seeds:
    type: custom_crop
    crop:
      jobs:
        Farmer: {money: 1.5, xp: 3}     # al cosechar maduro
```

Se paga solo a quien tiene ese trabajo y no está en creativo; `money` y `xp` pueden ir solos. El
nombre del trabajo se busca tal cual y, si no, en minúsculas (ExcellentJobs usa ids como `miner`).

- **Jobs Reborn.** El pago pasa por `Jobs.perform`, la misma llamada que hace Jobs para una acción
  suya, como un bloque roto: saltan sus eventos previos al pago (los boosts y otros plugins pueden
  cambiar las cantidades), se aplican sus límites diarios, el dinero va por su economía con búfer y
  la experiencia puede subir de nivel al jugador, con sus mensajes y comandos de nivel.
- **ExcellentJobs 2.** Su API (`JobsAPIProvider`, registrada en los servicios de Bukkit) da la
  experiencia con `LevelingManager#addXP`, que sube de nivel como sus objetivos. No tiene una llamada
  pública para pagar dinero fuera de un objetivo, así que el dinero se ingresa con **Vault**, la misma
  economía con la que paga ExcellentJobs; sin Vault se avisa una vez en consola y solo se da la XP.

### HMCCosmetics

HMCCosmetics no genera un pack: dibuja cada cosmético (sombreros, mochilas, globos…) con un ítem de
un plugin que sí lo tiene —ItemsAdder, Oraxen, Nexo— a través de su librería **HibiscusCommons**. El
gancho registra este plugin en HibiscusCommons con el id `arkcontent`:

```yaml
# plugins/HMCCosmetics/cosmetics/hats.yml
ruby_crown:
  slot: HELMET
  item:
    material: arkcontent:demo:ruby_helmet
```

El cosmético es entonces la misma stack que da `/customgive`, con su `item_model`, y su modelo ya
está en nuestro pack: el sombrero o la mochila no necesitan un segundo pack y no hay ids que puedan
chocar. Es la forma recomendada de hacer cosméticos con modelos de este plugin.

### Fusionar otros packs (`pack.merge`): CosmeticsCore y otros

Para un plugin que trae su propio resource pack (CosmeticsCore, un pack de sombreros descargado…),
`config.yml` acepta una lista de carpetas o ZIPs, relativos a la carpeta del servidor:

```yaml
pack:
  merge:
    - plugins/CosmeticsCore/resourcepack
    - packs/hats.zip
```

Al construir el ZIP en memoria se toman sus `assets/` (el `pack.mcmeta` es el nuestro; un ZIP puede
tenerlos en la raíz o una carpeta más abajo, y una entrada que intente salir de la carpeta, como
`../`, se ignora). Si una ruta existe en los dos packs:

- un **modelo o una textura** es un choque real: se queda el nuestro y se avisa;
- los archivos que son **listas a las que cada pack añade lo suyo** se **unen** (`JsonMerge`) en vez
  de quedarse con uno, que es como desaparecen los sombreros o emojis de otro plugin al fusionar:
  `atlases/*.json` (sus `sources`), `font/*.json` (sus `providers`, los nuestros primero),
  `sounds.json` y `lang/*.json` (sus claves), el `models/item/*.json` de un ítem vanilla al estilo
  anterior a 1.21.4 (sus `overrides` de `custom_model_data`, unidos y ordenados por número, como
  los necesita el cliente) y su `items/*.json` de 1.21.4+ cuando ambos hacen `range_dispatch` sobre
  `custom_model_data` (sus entradas, por umbral).
- Si los dos packs dan **el mismo número** de `custom_model_data` (o el mismo sonido) a cosas
  distintas, eso es una **colisión de IDs**: se queda el nuestro y la consola nombra el archivo, el
  número y los dos modelos, para renumerar uno.

Como los ítems de este plugin usan `item_model` y no `custom_model_data`, no ocupan ningún número:
los packs de cosméticos basados en `custom_model_data` no chocan con ellos, solo entre sí. Es el
mismo mecanismo que usa el gancho de MythicArmor.

### zAuctionHouse

zAuctionHouse v4 guarda cada ítem entero: en 1.20.5+ como sus datos completos, componentes y
etiquetas incluidos. Así que un rubí puesto en subasta es el rubí que se compra, y se dibuja con su
`item_model` también en los menús de la subasta. El gancho es la red de seguridad para cuando algo
por el camino reconstruye la stack y deja solo la etiqueta de id del plugin: al poner un ítem en
venta (`AuctionPreSellEvent`) y al salir de la casa de subastas —comprado, retirado o recogido tras
caducar (`RemoveEvent`)— se le devuelve el aspecto desde su definición (`item_model` y `equippable`;
nombre, lore y lo demás no se tocan).

### eco: EcoItems, EcoArmor, EcoEnchants, EcoMobs, Reforges, StatTrackers, Talismans…

Todos los plugins de Auxilor leen los ítems de su configuración (recetas, drops, tiendas, piedras de
mejora, ingredientes) con la búsqueda de ítems de eco, a la que otros plugins añaden un espacio de
nombres. Este añade `arkcontent`:

```yaml
# una receta de EcoItems, un drop de EcoMobs, un producto de EcoShop...
item: arkcontent:demo__ruby
```

eco parte la búsqueda por los `:`, así que el `namespace:id` del ítem va con `__` en lugar de `:`,
como en cualquier integración de eco con espacios de nombres. Lo que eco recibe se compara por el
id del ítem, no por su aspecto: un papel con el mismo modelo no cuenta.

### nightcore: ExcellentCrates, ExcellentShop, ExcellentEnchants…

nightcore, la librería de NightExpress, pregunta a cada adaptador registrado si un ítem es de su
plugin cuando un admin lo pone en una caja o en una tienda desde la mano; si lo es, guarda solo el
plugin y el id, y lo vuelve a crear cada vez que lo da. Lo hace para ItemsAdder, Oraxen, Nexo y
MMOItems; el adaptador `arkcontent` hace que lo haga también para este plugin. Así un rubí puesto
en una caja sigue siendo un rubí —con el aspecto de su definición actual— y no una copia de la
stack del día en que se añadió. Es también una alternativa a PhoenixCrates.

### Mimic e ItemBridge

Dos registros de ítems compartidos, por los que otros plugins piden ítems sin saber de qué plugin
son (RPGInventory y los plugins RPG escritos para Mimic; los que usan ItemBridge):

```
/mimic items give Steve arkcontent:demo:ruby
/ib give Steve arkcontent:demo:ruby
```

ItemBridge, además, coloca y lee bloques: un plugin que pone bloques a través de él pone los
bloques personalizados de este (registrados como si los hubiera puesto un jugador) y puede
preguntar cuál hay en un sitio.

### WorldEdit

```
//set demo:ruby_block
//replace stone 10%demo:ruby_block,90%stone
```

Los bloques personalizados valen donde WorldEdit acepta un bloque: `//set`, `//replace`, patrones,
máscaras, pinceles, con autocompletado. El analizador se consulta antes que el de WorldEdit y solo
responde a ids de este plugin. WorldEdit coloca, copia, gira y deshace el estado de note block del
bloque como cualquier otro; pero sus efectos secundarios lo actualizan como un note block vanilla
(recalculan `powered` y el instrumento), lo que lo devolvería a vanilla. Por eso cada bloque
personalizado que una edición coloca se anota al pasar y, un tick después, se reafirma en su estado
sin física y se registra; uno que una edición sustituye (un `//undo`, un `//set air`) se olvida.

### DeluxeMenus

```yaml
items:
  ruby:
    material: arkcontent-demo:ruby
    slot: 13
```

Como `itemsadder-<id>` o `nexo-<id>`. DeluxeMenus revisa los materiales de cada menú al habilitarse,
contra una lista de prefijos que copia una vez de sus ganchos, y crea la tabla de ganchos en el mismo
paso: no hay un momento para añadir el de este plugin antes, y el primer arranque avisa
`Material for item: ... is not valid!`. Por eso el gancho entra en la tabla, su prefijo en esa lista
y, si algún menú usa `arkcontent-`, DeluxeMenus se recarga (su propio `/dm reload`, que conserva
ambos) un tick después de que todo haya arrancado. El aviso del primer intento queda en la consola;
tras la recarga el menú carga. DeluxeMenus no publica su API: se compila contra firmas copiadas
de 1.14.1, como Iris o MMOItems.

### BetonQuest

```yaml
items:
  ruby: "arkcontent demo:ruby"
conditions:
  hasRuby: "item ruby:3"
  rubyOnAltar: "arkcontentBlock demo:ruby_block 100;64;100;world"
actions:
  giveRuby: "give ruby:5"
  buildAltar: "arkcontentBlock demo:ruby_block 100;64;100;world"
```

Lo mismo que la integración de ItemsAdder que trae BetonQuest: un tipo de ítem (y con él todas
las acciones, condiciones y objetivos de BetonQuest que manejan ítems, por id), una condición y una
acción de bloque. Se registra por el servicio de integraciones de BetonQuest, la vía que ofrece a
otros plugins. Que el id sea un bloque se comprueba al ejecutarse la misión, no al cargarla:
BetonQuest lee sus paquetes antes de que la primera compilación de este plugin asigne los estados
de los bloques.

### AuthMe

Con AuthMe el pack no se envía al entrar sino al iniciar sesión (por contraseña o por sesión
recordada): la pantalla de descarga no tapa el aviso de `/login`, y un pack obligatorio rechazado
ahí no expulsa al jugador antes de poder entrar. Es lo que la página de AuthMe de ItemsAdder pide
montar a mano (`apply-on-join: false` y un comando en `onLogin`). `delivery.after-login: false` lo
desactiva.

### PhoenixCrates

**No integrado.** Su SDK de complementos (que se descarga del repositorio de Phoenix Plugins) no
estaba disponible (HTTP 503) al desarrollar esta versión, y sin él no se puede compilar ni probar un
gancho. Mientras tanto, dos formas que funcionan sin gancho:

- **Recompensa desde la mano.** Crear la recompensa con el ítem del plugin en la mano: los
  componentes `item_model` y `equippable` van dentro de la stack y sobreviven a las serializaciones
  de Bukkit, YAML y bytes de Paper (verificado, ver la tabla de abajo). Con PhoenixCrates en sí no se
  ha podido probar.
- **Recompensa por comando.** `customgive {player} demo:ruby 1` como comando de la recompensa:
  entrega el ítem del plugin exactamente como `/customgive`. Para el icono de la animación, el ítem
  de la mano.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.5, integraciones)

| Integración | Estado |
|---|---|
| Jobs Reborn 5.2.6.3 (+ CMILib) | **Verificado.** Un bot con el trabajo Miner rompe `ruby_block`: saldo de $100 a $102,50 y 4,00 de XP de Miner |
| ExcellentJobs 2.0.2 (+ nightcore) | **Verificado.** Un bot empleado como `miner` rompe `ruby_block`: saldo de $100 a $102,50 (por Vault) y XP de 0 a 4,0. Con los contratos de ExcellentJobs activos (`Contract.Required: true`, lo que trae por defecto) el jugador tiene que firmar uno para estar empleado; la prueba se hizo con `Required: false` |
| HibiscusCommons 0.9.3 | **Verificado.** `Hooks.getItem("arkcontent:demo:ruby_helmet")` devuelve el casco del plugin con su `item_model` y `equippable`, y `getStringItem` de esa stack devuelve `arkcontent:demo:ruby_helmet` |
| HMCCosmetics | **Solo compilado** contra la API real; el plugin completo no se distribuye por Maven y no se ha podido arrancar aquí (lo que usa de nosotros es lo verificado en HibiscusCommons) |
| `pack.merge` (CosmeticsCore y otros) | **Probado con tests** (`JsonMergeTest`, `PackCompilerTest`: carpetas, ZIPs, ZIPs con carpeta raíz, `../`, colisiones de `custom_model_data`); no con el pack real de CosmeticsCore, que es de pago |
| zAuctionHouse 4 | **Solo compilado** contra `zauctionhousev4-api` 4.0.1.3. Verificado en el servidor lo que necesita: una pieza de la armadura conserva `item_model`, `equippable` e id por los tres caminos de serialización —stream de objetos de Bukkit, YAML y bytes de Paper—, y la stack recuperada es igual a la original |
| PhoenixCrates | **No integrado** (ver arriba) |
| Pago por cosechar un cultivo | Usa el mismo código que el del bloque; no se ha probado en vivo |

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.6)

| Integración | Estado |
|---|---|
| eco 2026.40 | **Verificado** con la búsqueda que usan sus plugins: `Items.lookup("arkcontent:demo__ruby")` devuelve el rubí con su `item_model`; coincide con el rubí real y no con un papel; un id que no existe da vacío. EcoItems y los demás son de pago y no se han arrancado |
| nightcore 2.16.3 + ExcellentCrates 6.6.1 | **Verificado** con lo que hace nightcore con un ítem de la mano: el adaptador del rubí es `arkcontent`, id `demo:ruby`, y la stack que reconstruye conserva `item_model`; `createItem("demo:ruby")` funciona. El editor de recompensas de ExcellentCrates no se ha recorrido |
| Mimic 0.8.0 | **Verificado.** Registros `[mimic, arkcontent, minecraft]`; `getItem` y `getItemId` (`arkcontent:demo:ruby`); `/mimic items give … arkcontent:demo:ruby 2` da dos rubíes |
| ItemBridge (compilación 2acbb8ffe8 de jitpack, con un `plugin.yml` escrito para la prueba) | **Verificado.** `/ib give … arkcontent:demo:ruby 4`; la clave del rubí en la mano; `setBlock`, `getBlock` y `removeBlock` con `demo:ruby_block` |
| WorldEdit 7.3.19 | **Verificado.** `//set demo:ruby_block` en 3 bloques: los tres reconocidos (antes de reafirmar el estado salían como note block vanilla con `powered=false`); romper uno suelta *Block of Ruby*; `//undo` los quita |
| DeluxeMenus 1.14.1 | **Verificado.** Menú con `material: arkcontent-demo:ruby`: el cliente recibe el rubí con su `item_model` en la casilla (tras la recarga automática, ver arriba) |
| BetonQuest 3.2.0 | **Verificado.** `give ruby:3` da 3 rubíes; `item ruby:3` es verdadera; `arkcontentBlock` coloca el bloque y la condición lo ve |
| AuthMe 5.7.0 | **Verificado.** Ni al registrarse ni al volver a entrar llega el pack en los 5 s ante el aviso de AuthMe; llega 70–150 ms después de `/register` y de `/login` |
| `custom_model_data` | **Verificado.** Números 10000–10006 para los 7 ítems de materiales simples; `paper.json`, `diamond_sword.json`, `note_block.json` y `barrier.json` en el pack; las stacks llevan su número; el casco no tiene (`/arkcontent cmd` lo explica); `%arkcontent_cmd_demo:ruby%` → `10000`. El dibujo en un cliente real no se puede comprobar aquí |

## 6. Importar desde ItemsAdder

```
plugins/ArkcronistContent/import/     <- copia aquí plugins/ItemsAdder/contents/ (o un pack suelto),
                                         y/o el pack generado de ItemsAdder (.zip, entero o en partes)
/arkcontent import                    <- convierte, copia recursos y recompila
```

**Regla de oro: los recursos se copian byte a byte.** Un modelo conserva su diccionario `textures`,
sus elementos y sus UV exactamente como los escribió Blockbench; una textura, su tamaño (16x16, 32x32
o HD) y sus píxeles; un `.png.mcmeta`, sus fotogramas; un `.ogg`, su frecuencia y sus canales. Nada
se renombra, se recodifica ni se "optimiza", y la jerarquía bajo `assets/<ns>/` se mantiene
(`assets/itemsadder/textures/item/custom/arma.png` sigue en esa misma ruta). Si una referencia no va
a resolverse, se **avisa** con la corrección exacta; el archivo no se reescribe.

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
| `resource.model_id` (CustomModelData) | no se arrastra: el ítem se dibuja con `meta.setItemModel()` (`item_model`) y se anota en consola |
| `behaviours.block` | `type: custom_block`; 6 texturas en el orden de ItemsAdder (down, east, north, south, up, west); `drop_when_mined` / `cancel_drop` -> `block.drop-self` |
| `behaviours.furniture` | `type: custom_furniture`; `solid` -> `BARRIER`, si no `LIGHT` con `light_level`; `fixed_rotation` -> `face-player: false`; `display_transformation` (transform, translation, scale, left/right_rotation) -> `furniture.display` |

Un modelo exportado de Blockbench con texturas sin namespace (`"item/espada"`, que el cliente busca
en `minecraft:`) **ya no se corrige**: se copia tal cual y se avisa de que escribas `mi_ns:item/espada`
en él si sale morado y negro (la textura se copia igualmente, para que baste ese cambio). A los
bloques de seis caras que el importador genera se les da textura de partículas.

Los sonidos de cada namespace importado (`sounds.json` y `sounds/**/*.ogg`) se copian byte a byte a
`contents/<namespace>/`, y el compilador los mete en `assets/<namespace>/` tal cual: `sounds.json` se
une al de otros packs por eventos (`JsonMerge`), y los `.ogg` no se tocan.

Lo que no tiene equivalente —recetas, loot, eventos, durabilidad, encantamientos, hitbox de
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

### El pack generado de ItemsAdder (`/iazip`)

El zip que genera ItemsAdder —el que se envía a los jugadores, a menudo partido en `parte 1`,
`parte 2`...— no trae configuraciones: ni nombres, ni lore, ni comportamientos; solo lo que dibuja el
cliente. Así que no se convierte: **se absorbe entero**.

1. Las partes se reconocen por el nombre (`generated_4 - parte 1.zip`, `pack_part2.zip`, `x.pt3.zip`)
   y se juntan en `plugins/ArkcronistContent/packs/<nombre>/`: cada archivo byte a byte, en su misma
   ruta, con `assets/`, **los overlays** (`ia_overlay_1_21_6_plus/`, `mythicarmors_1_21_6/`...) y el
   `pack.mcmeta` que los declara. Si falta una parte o no hay `pack.mcmeta`, se avisa.
2. Todo lo que hay en `packs/` (carpetas o `.zip`) se funde con nuestro pack en cada recompilación,
   y las entradas `overlays` de su `pack.mcmeta` se añaden al nuestro tal cual.
3. Cada número de `custom_model_data` del pack que dibuja un ítem pasa a ser un ítem de este plugin
   dibujado con **`item_model`**: `contents/<ns>/imported/<pack>-pack.yml` con
   `resource.item-model: <id>`, y `contents/<ns>/items/<id>.json` = la entrada de ItemsAdder **tal
   cual** (condiciones de arco, `tints`, `oversized_in_gui`). Si el pack ya trae su propio
   `assets/<ns>/items/<id>.json`, se usa ese. Los huesos de ModelEngine, los modelos vanilla y los
   iconos internos de ItemsAdder no son ítems y se dejan al pack. El nombre sale del id
   (`voltharion_yelmo` -> "Voltharion Yelmo"), porque el pack no tiene otro: si pones también las
   configuraciones de ItemsAdder en `import/`, **sus ítems ganan**, con su nombre y su lore, y el
   modelo no se copia otra vez.
4. **Armaduras.** Una pieza de armadura (casco, peto, grebas, botas) se pone con el juego de capas
   del pack (`assets/<ns>/equipment/`) cuyo nombre comparte más palabras con el suyo
   (`astralion_peto` -> `astralion_armadura`, `carmesina_coloso_peto` -> `carmesina_coloso_armadura`)
   o, si ninguno comparte, con el único del namespace: `equipment: {slot: CHEST, asset: ...}`. El
   cuero no, porque su aspecto lo da el color de cada stack, que solo dicen las configuraciones. Un
   yelmo, sombrero o corona de papel con `display.head` en su modelo se lleva en la cabeza con su
   propio modelo (`equipment: {slot: HEAD}`). Las piezas sin pareja se listan en consola; si alguna
   deducción no te vale, cambia o quita su `equipment:`.

`resource.item-model` sirve también a mano: el ítem apunta con `item_model` a ese archivo de
definición, copiado sin tocar desde `contents/<ns>/items/` o tomado de un pack de `packs/`.

Cómo conviven el pack absorbido y el nuestro, sin alterar lo de ItemsAdder:

- **Atlas.** ItemsAdder renombra sus texturas como sprites (`"4": "ia:564"` en un modelo, y
  `ia:564 -> dragones_epicos:armor/voltharion/yelmo` en `atlases/blocks.json`). El atlas se copia y,
  si hay sprites nuestros, se añaden detrás: sus 2231 fuentes siguen intactas.
- **CustomModelData.** Los números que usa el pack (también en sus overlays) quedan reservados: a
  ningún ítem nuestro se le da uno. Si ya tenía uno de esos, se le da otro libre y se avisa. En la copia
  de un overlay (lo que ven los clientes 1.21.6+), nuestras entradas se añaden y, si coinciden, gana la
  de ItemsAdder.
- **Fuente.** Los caracteres que dibuja su `default.json` quedan reservados para emojis y HUDs (los
  nuestros que coincidían se mueven, avisando), y nuestros espacios negativos (U+F801...) ceden ante
  sus glifos en esos caracteres.
- **Bloques.** `note_block.json` se une estado por estado: un bloque de cualquiera de los dos gana al
  aspecto vanilla, y los estados que usan sus bloques no se dan a bloques nuevos nuestros.
- **Ítems vanilla con otro aspecto** (ItemsAdder dibuja la barrera como un botón): ese aspecto pasa a
  ser el de cualquier barrera sin número nuestro.
- **Sonidos.** Un `sounds.json` con nombres sin namespace (`"golem_ancestral/invocar"` dentro de
  `arkcronist_jefes`) hace que el cliente los busque en `minecraft:sounds/` (el cliente 1.21.8 los
  lee con `ResourceLocation.parse`), y uno con el namespace de otro pack
  (`"enderman_overhaul:cave_hurt_1"` dentro de `enderman_expansion`) los busca allí: en ambos casos,
  si el `.ogg` está en el namespace del propio `sounds.json`, **el pack que reciben los jugadores**
  lo nombra con ese namespace (`"arkcronist_jefes:golem_ancestral/invocar"`) y el sonido suena. Solo
  cambia el nombre: eventos, volumen, tono, `stream` y el `.ogg` quedan igual, y la copia de
  `packs/` y de `contents/` no se toca. Con `pack.fix-sound-names: false` solo se avisa.
- **Espacios negativos.** Su fuente dibuja U+F801... con glifos bitmap; el cliente calcula su ancho
  como `(int) (0.5 + altura) + 1`, que da exactamente lo nuestro hasta 128 px, pero 257, 513 y 1025
  en los tres más grandes. Por eso nuestros HUDs y `%arkcontent_space_<n>%` solo usan pasos de hasta
  128 (más caracteres para un salto largo): caen en el mismo píxel con cualquiera de las dos fuentes.

#### Qué se ha verificado con un pack real (v1.7.1 y v1.7.2)

Con un pack generado de ItemsAdder de 22.127 archivos en dos zips (40 namespaces, 12 overlays):

- Importación: 1588 ítems con `item_model` en 40 namespaces; 7542 entradas de ModelEngine, 55
  vanilla y 19 internas no se convierten en ítems; 0 problemas al cargar.
- Compilación, zip y hash: 23.716 entradas, 36,7 MiB, SHA-1 de 40 hex que coincide con el del zip, y
  **el mismo SHA-1 al recompilar**.
- 22.018 de 22.125 archivos idénticos byte a byte; los 107 restantes son listas compartidas a las que
  se añadió lo nuestro (106 `items/*.json` de CMD y `font/default.json`), en las que **se conservan
  intactas** las 5058 entradas de ItemsAdder y sus 478 glifos.
- 9268 modelos con 10.273 referencias de textura: todas resuelven (por el atlas) a un PNG del pack o a
  una textura vanilla. Yelmo `dragones_epicos:armor/voltharion_yelmo` -> `ia:564` -> PNG de 112x112;
  crate `medieval_rpg:crate_1` -> `ia:1206` -> PNG de 256x256; UV como en Blockbench.
- `arkcronist_jefes/sounds/golem_ancestral/invocar.ogg` idéntico (Vorbis 44,1 kHz, 1 canal).
- v1.7.2: 318 nombres de sonido corregidos en 4 `sounds.json` (`arkcronist_jefes` 228,
  `littleroom_warden` 47, `enderman_expansion` 24, `sonidos_custom` 19), con los mismos eventos y
  ajustes; de los 387 nombres del pack, **ninguno** queda apuntando a un archivo que no existe. 287
  piezas de armadura con su juego de capas y 84 yelmos/sombreros en la cabeza; 13 piezas sin pareja
  (8 de cuero teñido de `elitecreatures`, 4 de `darksteel`, 1 de `littleroom_warden`).
- En el servidor Paper 1.21.8 de pruebas, con contenido previo: `/arkcontent import` desde consola,
  recompilación, un bot recibe el pack con su hash (y la descarga coincide con él), y los ítems dados
  llevan `item_model` (`dragones_epicos:voltharion_yelmo`...) y su nombre. En el pack servido siguen
  sus 2231 sprites del atlas, sus 124 estados de note block y sus 36.384 entradas de CMD.

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
- Logros: los disparadores son entrar, tener un ítem, llevar un set y manual; el resto (matar,
  minar, viajar…) se puede conceder desde otro plugin o un comando con `/advancement grant`.
  Cambiar o quitar un logro con `/arkcontent reload` hace una recarga de datos del servidor
  (`/minecraft:reload`): un tirón breve que también recarga datapacks y recetas. Si se borra
  `data/world_content.db`, el siguiente que consiga un logro con `announce` se volverá a anunciar.
- ExcellentJobs: el pago va directo (XP con su API, dinero por Vault), así que no pasa por sus
  contratos: no mira el horario laboral, no suma puntos de contrato ni aplica sus bonus por nivel.
- PhoenixCrates no está integrado; HMCCosmetics y zAuctionHouse están compilados contra sus APIs
  reales, pero los plugins completos no se han podido arrancar aquí (sección 5).
- 1.6, integraciones: los plugins construidos sobre eco (EcoItems y los demás) son de pago y no se
  han arrancado; lo verificado es la búsqueda de eco que ellos usan. Igual con ExcellentCrates: se
  ha verificado el adaptador de nightcore, no el editor de recompensas. ItemBridge se probó con su
  compilación de jitpack (su repositorio no trae `plugin.yml`). Con FastAsyncWorldEdit, en lugar de
  WorldEdit, no se ha probado. DeluxeMenus deja en consola el aviso de su primer intento con un menú
  `arkcontent-` antes de recargarse. Los bloques que pone WorldEdit quedan bien un tick después de la
  edición, no en el mismo.
- Números CustomModelData: solo para ítems de materiales con una definición vanilla de un solo
  modelo (ver sección 4); el resto no recibe número.
- El pack se envía en `PlayerJoinEvent`. En 1.21.7+ Paper permite enviarlo durante la fase de
  configuración, antes de entrar al mundo; es el siguiente paso natural.
- 1.7, armas: el trazado (la cuenta) va en su hilo, pero copiar lo que hay alrededor del disparo
  —bloques y entidades— se hace en el hilo principal, porque Paper solo deja leer el mundo ahí. Es
  un disparo por clic: el cliente repite el clic derecho mantenido cada 4 ticks, así que el
  automático llega a 5 disparos por segundo. Los disparos son instantáneos (sin balas que viajen ni
  caigan). Un objetivo dentro de un bloque no lleno (una valla, un panel de cristal) se copia caja
  a caja de su forma de colisión.
- 1.7, líquidos: 16 como máximo; solo los 8 primeros usan estados que nadie altera, y los otros 8 se
  restauran en cada tick en que alguien está dentro. Con un modelo de bloque no se puede ocultar la
  cara entre dos bloques de líquido contiguos, así que con texturas translúcidas se ven esas caras
  interiores. Solo hay dos alturas (fuente y flujo), no los 8 niveles del agua. No se puede
  construir dentro de un líquido (la cuerda no es reemplazable, como sí lo es el agua): hay que
  recoger la fuente primero. El flujo no sale de los chunks cargados alrededor de su fuente
  (`liquids.chunk-reach`), ni empuja a quien está dentro, ni apaga el fuego. Solo los jugadores
  reciben el efecto de contacto, no los mobs.
- 1.8, editor: «solo esta pieza» no existe para muebles animados ni el editor para los de ModelEngine
  (ver sección 4). El YAML se edita línea a línea: un `display` escrito como mapa en varias líneas,
  o un ítem definido dos veces en el archivo, se rechaza sin escribir. Los valores escritos a mano
  como un solo número (`scale: 0.5`) pasan a lista (`[0.5, 0.5, 0.5]`). Ver el modelo moverse
  detrás del menú depende del cliente: el menú de cofre oscurece el fondo pero no lo tapa.
- 1.8, verificador: solo audita chunks cargados, y tarda dos intervalos en actuar. Al purgar un
  mueble de almacenamiento, el contenido guardado en `furniture_storage` no se borra. Los cultivos no
  se auditan (tienen su propio ciclo).
- 1.8, red: la IP descubierta es por la que la máquina **sale** a internet; en un nodo con varias IPs
  puede no ser por la que **entran** los jugadores. Ahí, `http.public-address` (o un `server-ip`
  público) manda. Un puerto de respaldo solo sirve si el hosting lo redirige.
- 1.7, HUDs: la posición en pantalla depende de `ascent` y `offset`, y la escala de interfaz del
  jugador no cambia nada (es todo en píxeles de fuente), pero otros mods de cliente que muevan la
  barra de acción sí. AuraSkills tapa la barra de acción con la suya (ver sección 4). El dibujo en
  un cliente real no se ha podido comprobar aquí: lo verificado es el texto exacto que recibe el
  cliente y la fuente del pack.

## 10. Compatibilidad frente a ItemsAdder

La [página de compatibilidad de ItemsAdder](https://wiki.itemsadder.com/compatibility-with-other-plugins/compatible)
lista 73 plugins. «Compatible» ahí significa cosas muy distintas, según su propia wiki: una
integración que escribió ItemsAdder; una que escribió el otro plugin (que llama a la API de
ItemsAdder y solo a la suya); emojis a través de PlaceholderAPI; fusionar el pack del otro; poner
un número de CustomModelData; o un apaño o un «probado y funciona». Esta tabla dice, para cada uno,
cómo lo cubre ArkcronistContent.

Leyenda: **gancho** – integración propia de este plugin · **puente** – a través de un registro
compartido que este plugin alimenta (eco, nightcore, Mimic, PlaceholderAPI, `pack.merge`, números
de CustomModelData) · **sin gancho** – el otro plugin guarda o usa la stack entera, o va por
material, y los ítems de este plugin ya funcionan · **necesita al otro plugin** – solo funciona si
ese plugin añade soporte para este, como lo añadió para ItemsAdder · **no aplica**.
✔ verificado en el servidor de prueba · ◐ compilado contra su API real o verificado solo a través
de su librería · ○ sin probar.

| Plugin | Cómo lo hace ItemsAdder | ArkcronistContent | |
|---|---|---|---|
| AdditionsPlus | sin detalle en su wiki | puente: PlaceholderAPI (emojis); ítems desde la mano | ○ |
| AdvancedEnchantments | declarar el encantamiento en el ítem | sin gancho: los encantamientos van por el material base, que es vanilla | ○ |
| AdvancedOreGen | el generador nombra bloques de IA | necesita al otro plugin | — |
| AlixAnimations | sin página en su wiki | no aplica (no se sabe qué integra) | — |
| AnimatedScoreboard | `%img_x%` por PlaceholderAPI | puente: `%arkcontent_emoji_x%` | ◐ |
| AuthMe | config + comando en `onLogin` | gancho: el pack tras iniciar sesión | ✔ |
| BanItem | sin detalle | sin gancho: se banea el ítem sostenido | ○ |
| BentoBox | arreglo de explosiones de granadas de IA | no aplica (este plugin no tiene explosivos) | — |
| BetonQuest | addon de terceros / integración de BetonQuest | gancho: tipo de ítem, condición y acción de bloque | ✔ |
| BossShop | addon de terceros | puente: material + número CustomModelData | ○ |
| ChatControl-Red | emojis | puente: `%arkcontent_emoji_x%` en formatos; el reemplazo de `:ruby:` en mensajes no se ha probado con ChatControl | ○ |
| Citizens | NPCs con ítems de IA | gancho (v1.3) | ◐ |
| ClueScrolls | material + CustomModelData | puente: número CustomModelData | ○ |
| CMI | rangos por PlaceholderAPI | puente: PlaceholderAPI | ◐ |
| CosmeticsCore | fusión de packs | puente: `pack.merge` | ◐ |
| CraftEnhance | parcial | sin gancho: recetas con el ítem de la mano | ○ |
| CustomCrafting | arrastrar ítems de IA | sin gancho: arrastrar el ítem lo guarda entero (coincidencia exacta); con eco instalado, también por eco | ○ |
| DailyShop | — | no aplica: la 3.8 no arranca en Paper 1.21.8 (verificado en v1.3) | — |
| DecentHolograms | — | gancho (v1.3) | ✔ |
| DeluxeMenus | `material: itemsadder-<id>` | gancho: `material: arkcontent-<id>`; y emojis por PlaceholderAPI | ✔ |
| DimensionsAddons | sin detalle | no aplica (no se sabe qué integra) | — |
| EcoArmor | integración de eco | puente: eco (`arkcontent:<ns>__<id>`) | ◐ |
| EcoEnchants | integración de eco | puente: eco | ◐ |
| EcoItems | integración de eco | puente: eco | ◐ |
| EcoMobs (EcoBosses) | integración de eco | puente: eco | ◐ |
| EpicBackpacks | `type: ITEMSADDER_ITEM` | necesita al otro plugin | — |
| ExcellentEnchants | encantamiento declarado en el ítem | sin gancho (por material) + puente nightcore | ◐ |
| ExecutableBlocks | — | gancho (v1.3) | ◐ |
| ExecutableItems | enlazar un ítem de EI a la textura de IA | sin gancho: el ítem de EI puede llevar `item_model: demo:ruby` o su número CustomModelData | ○ |
| FancyWaystones | sin detalle | necesita al otro plugin | — |
| Graves / GravesX | muebles/bloques de IA como tumba | necesita al otro plugin (los ítems sí caen en la tumba tal cual) | — |
| GriefPreventionStickFix | arreglo para ítems con metadatos | no aplica: funciona igual con estos ítems | — |
| HeadsAnywhere | fusionar su pack de fuente | puente: `pack.merge` (las fuentes se unen) | ◐ |
| HMCCosmetics | — | gancho (v1.5, HibiscusCommons) | ◐ |
| Holographic Displays | `%img_x%` y CustomModelData | puente: PlaceholderAPI y número CustomModelData | ○ |
| HopperSorter | sin detalle | sin gancho (ordena por la stack) | ○ |
| HyperStones | «probado» | sin gancho (por material) | ○ |
| InteractionVisualizer | sin detalle | sin gancho (muestra la stack) | ○ |
| InteractiveChat | «probado» | sin gancho (muestra la stack, con su `item_model`) | ○ |
| Iris | inyector de bloques | gancho (v1.1) | ◐ |
| ItemBridge | `ia:<id>` | gancho: `arkcontent:<id>`, ítems y bloques | ✔ |
| ItemFrameShops | precios por tienda | sin gancho | ○ |
| JetsPrisonMines | minas con bloques de IA | necesita al otro plugin | — |
| LootChest | sin detalle | sin gancho (guarda las stacks) | ○ |
| Mimic | `ia:<id>` | gancho: `arkcontent:<id>` | ✔ |
| MMOItems | — | gancho (v1.2) | ◐ |
| ModelEngine | fusión de packs | gancho (v1.1) | ◐ |
| MythicMobs | drops de IA | gancho (v1.1) | ✔ |
| Nova | fusión de packs | puente: `pack.merge` | ○ |
| Ore Regenerator | bloques de IA | necesita al otro plugin | — |
| Ouroboros-Mines | bloques de IA | necesita al otro plugin | — |
| RealisticWorldGenerator | bloques de IA en menas | necesita al otro plugin | — |
| Recipe Control | sin detalle | sin gancho (recetas con el ítem de la mano) | ○ |
| Reforges | integración de eco | puente: eco | ◐ |
| RPG Chest Premium | sin detalle | sin gancho (guarda las stacks) | ○ |
| RPGBank | iconos por id de IA | necesita al otro plugin | — |
| RPGInventory | addon de terceros | puente: Mimic | ○ |
| RPGMoney | iconos por id de IA | necesita al otro plugin | — |
| Scoreboard-revision | `%img_x%` | puente: PlaceholderAPI | ○ |
| ShopGUI+ | — | gancho (v1.1) | ◐ |
| SkinsRestorer | volver a aplicar el pack tras cambiar la piel | no aplica (es un apaño entre otros dos plugins) | — |
| Slimefun4 | fusionar su pack | puente: `pack.merge` | ○ |
| Space | fusionar su pack | puente: `pack.merge` | ○ |
| Spartan Anti Cheat | sin detalle | no aplica: los bloques se rompen a la velocidad de un note block | — |
| StatTrackers | integración de eco | puente: eco | ◐ |
| TAB | `%img_x%` | puente: PlaceholderAPI (emojis, barras de HUD) | ✔ |
| Talismans | integración de eco | puente: eco | ◐ |
| TrMenu | `source:ITEMSADDER:<id>` | necesita al otro plugin para `source:`; por número CustomModelData y PlaceholderAPI sí | — |
| ValhallaMMO | ItemsAdderAdditions escribe sus datos en el ítem | no hecho (necesitaría escribir los datos de ValhallaMMO en la stack) | — |
| ValhallaTrinkets | ídem | no hecho | — |
| Wailat | hecho para IA | necesita al otro plugin | — |
| WorldEdit | addon oficial | gancho: bloques en `//set`, patrones y máscaras | ✔ |
| WorldGuard | flags de IA | gancho: protección (v1.2) y flags propios para líquidos (v1.7) | ✔ |

**En números:** de los 73, **52 quedan cubiertos** —16 con gancho propio, 22 a través de un
puente, 14 sin necesitar gancho— y en **7 la cuestión no aplica**. De los 52, 10 están verificados
en el servidor de prueba con el plugin real, 19 compilados contra su API real o verificados a
través de su librería (eco, nightcore, Mimic, PlaceholderAPI) y 23 sin probar con ese plugin. Los
**14 restantes no están cubiertos**: 12 solo funcionarían si ese plugin añadiera soporte para
ArkcronistContent, como lo añadió para ItemsAdder (llaman a la API de ItemsAdder y a ninguna
otra), y 2 —ValhallaMMO y ValhallaTrinkets— sí se podrían hacer desde aquí, escribiendo sus datos
en las stacks como hace ItemsAdderAdditions, pero no están hechos.

ItemsAdder lleva años siendo el estándar y gran parte de su lista son integraciones que escribieron
los otros plugins. La cifra que ArkcronistContent puede cambiar por sí solo es la de sus propios
ganchos y puentes.
