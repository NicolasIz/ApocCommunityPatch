# ArkcronistContent

Motor de contenido personalizado para **Paper 1.21.8+**, Java 21: define ítems, bloques, muebles
(también asientos, muebles con inventario, cofres con animaciones de Blockbench que se unen en cofres
dobles, camas de dos bloques, muebles de varios bloques, tablones de misiones y muebles interactivos:
bebidas y calderos con partículas, carteles que ejecutan comandos y una guillotina animada; se incluyen
60 muebles medievales listos para decorar un spawn), armaduras (con cascos 3D), cultivos, emojis de chat, logros, armas de fuego, líquidos que
fluyen (con brillo y shaders validados contra el cliente 1.21.8) y barras de HUD ancladas al píxel
sobre las de vanilla, en YAML. El plugin compila
su propio resource pack, lo empaqueta en ZIP, calcula su SHA-1, lo sirve con un servidor HTTP
integrado —o lo sube solo a un servicio de almacenamiento— y se lo envía a cada jugador. Los bloques
y muebles colocados, los cultivos y lo guardado en los muebles se conservan en SQLite, y un
verificador de integridad repara o retira los muebles que un rollback, `/fill` o WorldEdit dejan
a medias (2.0). La 3.0 lleva el contenido a los jugadores de **Bedrock** a través de Geyser sin
configurar nada, añade **cosméticos con física** (una mochila que se balancea a la espalda,
calculada en su propio hilo) y **minijuegos de precisión** en la barra de acción para menas raras
y cañas de pescar. El mismo
concepto que ItemsAdder u Oraxen, reducido a una base limpia sobre la que crecer.

```
/customgive <jugador> <item> [cantidad]    entrega un ítem (acepta @a, @p...; autocompleta ids)
/arkcontent reload                         recompila ítems y pack sin reiniciar
/arkcontent info                           ítems y bloques cargados, colocados, hash del pack, URL
/arkcontent cmd <item>                     material y número custom_model_data del ítem (sección 4)
/arkcontent import                         convierte los packs de ItemsAdder de import/ (sección 6)
/arkcontent menu                           explorador del contenido, por carpetas de contents/ (sección 7)
/arkcontent board <subcomando>             avisos de los tablones de misiones: crear, publicar... (sección 4)
/arkcontent animate <animación>            reproduce una animación del mueble que miras (sección 4)
/arkcontent editor [mueble]                mueve, escala y gira en vivo el mueble que miras (sección 4)
/arkcontent showcase <pack>                coloca todos los muebles de un pack en filas delante de ti (sección 4)
/arkcontent shop                           tienda: los ítems con `price`, pagados con Vault (sección 5)
/arkcontent npc equip <hueco> <item> | sit NPC de Citizens con un ítem o sentado (sección 5)
/arkcontent hud <jugador> <hud> [set|add|take <n>]  ver o cambiar el valor de una barra (sección 4)
/arkcontent liquids                        líquidos cargados y fuentes vertidas por mundo (sección 4)
/arkcontent integrity [now|recovered|claim <n>]  verificador de integridad: estado, auditar ya, almacenes recuperados (sección 4)
/arkcontent bedrock                        qué recibió Geyser y qué se enseña a los jugadores de Bedrock (sección 4)
/emojis                                    los emojis de chat disponibles, dibujados (sección 4)
/cosmetic [off]                            lo que llevas puesto, o quitártelo (sección 4)
```

| Permiso | Por defecto | Para |
|---|---|---|
| `arkcontent.give` | op | `/customgive`, y sacar ítems del explorador |
| `arkcontent.admin` | op | `reload`, `info`, `import`, `editor`, `animate`, `showcase`, `npc`, `hud`, `liquids`, `integrity`; avisos del servidor web al entrar |
| `arkcontent.menu` | op | abrir el explorador (solo mirar, si no tiene `arkcontent.give`) |
| `arkcontent.emojis` | todos | `/emojis`. Usar un emoji no necesita permiso, salvo que el emoji declare el suyo |
| `arkcontent.shop` | todos | `/arkcontent shop` (necesita Vault y un plugin de economía) |
| `arkcontent.board` | todos | usar los tablones de misiones: aceptar, entregar y abandonar avisos |
| `arkcontent.board.admin` | op | `/arkcontent board` (también lo permite `arkcontent.admin`) |
| `arkcontent.cosmetic` | todos | ponerse un cosmético (clic derecho con él) y `/cosmetic` |

Con MythicMobs, ModelEngine, MythicArmor, MMOItems, PlaceholderAPI (y a través de él TAB y
DeluxeMenus), ShopGUI+, Iris, WorldGuard, GriefPrevention, AuraSkills, mcMMO, SkillAPI/ProSkillAPI,
Fabled, Vault, Citizens, DecentHolograms, EconomyShopGUI Premium, ExecutableBlocks, Jobs Reborn,
ExcellentJobs, HMCCosmetics, zAuctionHouse, eco (EcoItems, EcoArmor, EcoEnchants, Reforges…), nightcore
(ExcellentCrates, ExcellentShop…), Mimic, ItemBridge, WorldEdit, BetonQuest, AuthMe, Geyser,
PacketEvents, LuckPerms, Lands o Towny instalados se integra con ellos (sección 5); sin ellos funciona igual —verificado arrancando sin ninguno—. La
sección 10 compara, plugin por plugin, con la lista de compatibilidad de ItemsAdder.

El plugin se compila contra la API de Paper 1.21.8 y declara esa versión como mínima. El componente
`item_model` y la carpeta `assets/<namespace>/items/`, en los que se apoya todo, existen desde 1.21.4,
pero solo se ha verificado contra 1.21.8.

---

## 1. Compilar

```bash
./gradlew build          # -> build/libs/ArkcronistContent-3.2.0.jar
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
        │   ├── furniture/
        │   │   ├── BedLayout              las dos mitades de una cama, el display y dónde va la almohada
        │   │   ├── ChestPairing           qué mitad es cada cofre de un cofre doble, como en vanilla
        │   │   └── Footprint              los bloques de un mueble de varios bloques, según hacia dónde mira
        │   ├── quest/                     ── tablones de misiones (1.9) ──
        │   │   ├── QuestDefinition        un aviso: requisitos, objetivos, recompensas
        │   │   ├── QuestReader · QuestJson el aviso desde el YAML de contents/, y como se guarda en SQLite
        │   │   ├── QuestProgress          cómo va un jugador con un aviso; las cuentas de muertes y bloques
        │   │   ├── QuestBoardStore        avisos y progreso en SQLite, en el hilo ArkContent-DB
        │   │   └── Counted                «nombre número» tal como se escribe al final de un comando
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
        │   │   ├── ChestPair · ChestPairStore los cofres dobles: memoria + furniture_pairs
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
        │   ├── menu/
        │   │   ├── Page                   paginación del explorador
        │   │   ├── ContentTree            las carpetas de contents/ como árbol de paquetes
        │   │   └── MenuTitle              el título de un menú con una imagen detrás (espacios negativos)
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
            ├── menu/                      ContentMenu (carpetas, páginas, filtro), ContentMenus (título
            │                              con textura), ContentMenuListener (bloquea todo movimiento)
            ├── quest/                     QuestBoardService (abrir, aceptar, entregar, contar),
            │                              QuestBoardMenu (el pergamino), QuestItems, QuestBoardListener
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
            ├── command/                   CustomGiveCommand, ContentAdminCommand, QuestBoardCommand
            │                              (Brigadier)
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
      face-player: true         # mira hacia quien lo coloca, en pasos de 90° (3.2: hacia donde
                                # está, no hacia donde mira)
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
  de integridad (más abajo).
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

### Verificador de integridad (`sanity`)

Un rollback (CoreProtect…), `/fill`, WorldEdit o una caída pueden cambiar el mundo a espaldas del
plugin: un bloque invisible de un mueble desaparece y su display sigue flotando, `/kill` se lleva el
display y deja las barreras, o un bloque personalizado se convierte en piedra y su fila sigue en
SQLite. Cada `sanity.interval-seconds` (120 por defecto) se auditan los chunks cargados, sin cargar
ninguno.

**El mueble entero (2.0).** Cada mueble colocado tiene ahora una fila en `furniture_pieces` con su
ancla y hacia dónde mira. Así el verificador juzga la pieza completa, por ejemplo los seis bloques
de la guillotina y su display juntos, y no cada bloque por separado:

| Veredicto | Cuándo | Qué se hace |
|---|---|---|
| **Roto** | falta uno o más de sus bloques de soporte | se retira lo que queda: el display (y sus huesos), las barreras y luces que sigan en pie y todas sus filas, en una transacción. Lo que tuviera dentro (un barril, un cofre) pasa a `recovered_storage` para un administrador |
| **Fantasma** | están todos los soportes y falta el display, con las entidades de esos chunks cargadas (`/kill`, un limpiador de entidades) | se dibuja de nuevo, mirando hacia donde miraba; filas y contenido no cambian |
| **Perdido** | como el fantasma, pero no se puede redibujar: ocupa más de un bloque y no se sabe hacia dónde miraba, o es media de un cofre doble | se retira como uno roto: unas barreras sin display serían paredes invisibles |
| *Sin decidir* | un chunk o sus entidades sin cargar | nada; se mira en la siguiente |

Como antes, además:

- un display del plugin sin filas detrás, o un hueso de un mueble animado cuya raíz ya no existe,
  se elimina;
- una fila de bloque cuyo note block ya no tiene el estado de ese bloque, y una fuente de líquido
  cuyo tripwire ya no es el de ese líquido, se purgan. Cada una va en una transacción que solo borra
  si la fila sigue diciendo lo mismo que cuando se encontró.

**Ruta rápida.** No hace falta esperar al ciclo. Dos cosas anotan el chunk afectado:

- un `EditSession` de WorldEdit (o FAWE) que reemplaza un soporte (barrera, luz, cofre o cama);
- la eliminación de un display del plugin por cualquier causa salvo descargar el chunk o salir un
  jugador (`EntityRemoveEvent`).

Ese chunk se audita solo, dos veces: a los 2 s y otra vez 1 s después. Muchas ediciones seguidas
hacen una sola auditoría.

**Almacenes recuperados.** Antes de la 2.0, purgar un mueble con almacenamiento dejaba sus filas de
`furniture_storage` sueltas, sin nada que las abriera. Ahora el contenido de un mueble retirado pasa
a `recovered_storage` en la misma transacción, con el motivo («its blocks were gone (a rollback,
/fill or a world editor)»). Las filas sueltas que dejaron versiones anteriores se recogen en cada
ciclo. `/arkcontent integrity recovered` las lista. `/arkcontent integrity claim <n>` da el
contenido al administrador que lo pide, y lo que no cabe en su inventario cae a sus pies. Cada
almacén se reclama una sola vez y la consola apunta quién lo reclamó.

**Dos auditorías seguidas.** Un hallazgo solo se toca cuando dos auditorías consecutivas lo
encuentran igual: la misma pieza, los mismos soportes, las mismas filas. Un rollback a medias o un
`/fill` que va por chunks parecen un fallo un momento y están bien al siguiente. Antes de cambiar
nada se mira una vez más en el hilo principal. El contenido que ya no está definido no se toca:
puede volver con el siguiente `reload`.

**Datos de antes de la 2.0.** El archivo pasa del esquema 7 al 8 en una transacción: las dos tablas
nuevas y `user_version` van juntos, y si algo falla el archivo queda como estaba. Las piezas
colocadas antes de la 2.0 no tienen fila en `furniture_pieces`. El verificador las adopta al verlas
con su display (ancla y dirección leídas de él, `INSERT OR IGNORE`) y no cambia nada de lo que ya
hay. Una 1.9.x que abra después el archivo deja en paz las tablas que no conoce.

**Datos de antes de la 3.0.** El esquema pasa de 8 a 9 igual, en una transacción: se añaden
`player_cosmetics` (lo que cada jugador lleva puesto: hueco, id e ítem serializado) y
`minigame_stats` (partidas, aciertos, críticos, mejor error y última partida por jugador y juego).
No se toca nada de lo que ya había.

**Hilos.** El ciclo se programa y se juzga en `ArkContent-DB`. Allí se planifica desde memoria qué
filas y piezas de qué mundos mirar, por chunk, y allí se decide. Leer el mundo solo se puede en el
hilo principal, así que se mira allí por trozos, sin pasar de `sanity.max-millis-per-tick` (1 ms)
por tick. En la prueba fueron 358 chunks en 6–7 ticks y 6–7 ms en total. Los cambios de filas
vuelven a `ArkContent-DB`. El núcleo (`PieceAudit`, `SanityJudge`, `AuditPlan`) no depende de
Bukkit ni de NMS.

**Silencioso.** Si se hizo algo, sale una línea en consola («Integrity check: took out 1 broken
piece(s) of furniture (6 row(s)) (…)»); si no, nada. Un fallo se avisa una vez, sin traza, y la
siguiente auditoría se hace igual. `/arkcontent integrity` muestra la última auditoría y los totales
desde el arranque. `/arkcontent integrity now` audita ya, dos veces, todos los chunks cargados.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (2.0, integridad)

Con un bot (mineflayer), WorldEdit 7 y la consola, con `interval-seconds: 10`:

| Qué | Resultado |
|---|---|
| Actualizar desde 1.9.6 | «schema 7 -> 8 (tables added in one transaction; nothing existing was changed)»; las piezas ya colocadas, adoptadas con su dirección |
| Escaparate | 60 piezas, 97 filas de bloque y 60 filas en `furniture_pieces` |
| `/kill` del display de un taburete | redibujado a los 3,1 s, mirando igual; sigue con 1 fila y su barrera |
| `/kill` de un pedestal de antes de la 2.0, girado 270° | redibujado a los 4,3 s, a 270° |
| `/setblock … air` en lo alto de la guillotina | retirada entera a los 12,0 s: los 6 bloques quedan en aire, sus 6 filas fuera |
| `//set air` de WorldEdit en un bloque de un banco | banco retirado a los 3,5 s (ruta rápida) |
| Barril con 16 diamantes y 5 manzanas, `/setblock` en su soporte | retirado a los 11,4 s; su fila de almacenamiento pasa a `recovered_storage` #1; `claim 1` da 16 diamantes y 5 manzanas; un segundo `claim 1` responde «#1 is not waiting: unknown, or already taken.» |
| `/arkcontent integrity now` | «457 chunk(s) in 4.2 ms over 5 tick(s)» |
| Consola | 0 líneas WARN o ERROR del plugin en toda la prueba |

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
- **Un mueble, una transacción (1.9.5).** Las filas de todos los bloques que ocupa un mueble —las seis
  de la guillotina, las seis del puesto de mercado— se escriben en **una sola tarea** del hilo
  `ArkContent-DB` y en **una transacción** (`saveAll`), y se borran igual al romperlo (`deleteAll`):
  el archivo nunca tiene medio mueble. Si una fila falla, no queda ninguna (probado con una fila
  inválida: `rollback`, y la conexión sigue en autocommit para lo siguiente). El autoguardado de los
  inventarios abiertos (cada pocos segundos) también va junto, en una transacción para todos.
- **Apagado.** Es la única espera: `onDisable` deja terminar las escrituras encoladas (máx. 10 s).
- **Esquema.** `PRAGMA user_version` lleva la versión: 1 (`custom_blocks_world`), 2 (+ `custom_crops`),
  3 (+ `furniture_storage`, ver *Muebles con almacenamiento*), 4 (+ `advancement_unlocks`, ver
  *Logros*), 5 (+ `liquid_sources` y `hud_values`, ver *Líquidos* y *HUDs*), 6 (+ `furniture_transforms`,
  ver *Editor visual de muebles*) y 7 (+ `furniture_pairs`, `quest_notices` y `quest_progress`, ver
  *Cofres dobles* y *Tablón de misiones*) y 8 (2.0: + `furniture_pieces`, una fila por mueble con su
  ancla y hacia dónde mira, y `recovered_storage`, ver *Verificador de integridad*). Un archivo de una
  versión anterior gana al abrirse las tablas que le faltan; no se toca nada de lo que ya hay. Las
  tablas nuevas y el `user_version` se escriben en **una sola transacción**: si falla una, el archivo
  sigue en la versión de antes, sin media migración (probado con una sentencia que falla después de crear la tabla y subir la versión: las dos cosas se deshacen). Una
  versión anterior del plugin que abra un archivo más nuevo deja en paz las tablas que no conoce.
  Verificado al pasar de 1.8.0 a 1.9.0 (de 6 a 7, con las tres tablas nuevas y todo lo anterior
  intacto) y de 1.9.6 a 2.0.0 (de 7 a 8, con las piezas ya colocadas adoptadas después por el
  verificador).

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
      seat-height: 0.6          # la superficie del asiento, en bloques sobre el suelo
```

Clic derecho sobre el mueble (también a través de un soporte `LIGHT`, siguiendo la línea de visión):
se crea en el hilo principal un `ItemDisplay` vacío —invisible y sin caja de colisión— y el jugador
lo monta con la postura vanilla de ir sentado, mirando hacia donde mira el mueble. Agacharse lo
levanta: el asiento desaparece y el jugador queda de pie sobre el mueble, no dentro de su barrera.
Si ya hay alguien sentado se avisa. El asiento nunca se guarda con el chunk; romper el mueble,
desconectarse o apagar el plugin también lo retiran.

**Altura (3.0.3).** El `ItemDisplay` va justo a `seat-height`. Al montar, el cliente pone los pies
del jugador 0,6 bloques por debajo de la entidad (el punto de enganche del jugador), y en la postura
de ir sentado la parte de abajo de los muslos queda a esa misma altura sobre los pies: los muslos
se apoyan en la superficie del asiento. Hasta 3.0.2 la entidad iba 0,6 más arriba, que dejaba los
pies a la altura del asiento y al jugador flotando 0,6 bloques por encima. Los `seat-height` de los
muebles no cambian: siempre han sido la altura de la superficie (el banco largo, 8,5 px = 0,53; el
taburete de rubí, 0,6). El cepo de la guillotina pasa de `kneel-height: 0.2` a `0.35` para que la
cabeza quede en el agujero (a 1,41 bloques); un `furniture.yml` de ejemplo que no hayas tocado se
actualiza solo al arrancar.

En un mueble más alto que su asiento (un trono, el cepo de la guillotina) la cabeza del que
se sienta queda dentro de la barrera de arriba: el daño por asfixia de quien está sentado en uno de
estos asientos se cancela (la barrera es invisible, no se ve nada raro). Al levantarse de uno así se
le deja delante del mueble, hacia donde mira, si hay sitio, y si no encima de todo él. Un clic en el
bloque de arriba de un mueble alto sienta en el de abajo; un banco de 2×1 sienta a uno en cada bloque.

**Un asiento en el centro (3.2).** Un mueble grande con un solo sitio, como el trono de 3×3×2, lleva
`seat-at: [lado, adelante]`: un único asiento en el centro del mueble, desplazado esos bloques (a la
derecha de quien se sienta, y hacia el frente), a `seat-height` sobre el suelo. Se clique el bloque
que se clique, se sienta ahí. Sin `seat-at`, como antes: uno por bloque.

```yaml
      size: {width: 3, height: 3, depth: 2}
      interactable: seat
      seat-height: 1.0          # 3.2: hasta 3 bloques
      seat-at: [0, -0.2]        # en el centro, 0,2 bloques hacia el respaldo
```

**La cámara en tercera persona (3.2).** Con F5, la cámara se aleja del jugador hasta que choca con
el primer bloque sólido, y las barreras de un trono o del marco de la guillotina rodean la cabeza del
que está sentado: la cámara se quedaba pegada a su cara. Ahora, mientras alguien está sentado o
arrodillado, **solo a su cliente** se le muestran como aire las barreras de ese mueble
(`sendBlockChanges`, un tick después de sentarse para que no las pise el reenvío del bloque clicado).
Puede alejar la cámara y verse sentado. Los demás siguen chocando con ellas, y al levantarse, al
romperse el mueble, al salir o al apagar el plugin se le vuelven a mostrar.

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
- Un cofre sin sección `double:` **nunca se une** a otro en un cofre doble (ni al colocarlo ni si
  después se pone otro cofre al lado, que se queda simple sin romper un cofre doble vanilla vecino).
  Con `double:`, dos del mismo mueble uno al lado del otro sí se unen (ver *Cofres dobles*). Las **tolvas**,
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

### Cofres dobles (`double:`)

```yaml
  ruby_chest:
    type: custom_furniture
    furniture:
      support: CHEST
      animated-model: furniture/ruby_chest
      slots: 27                                # cada mitad: 27 como máximo
      double:
        animated-model: furniture/ruby_chest_double   # un .bbmodel de dos cofres de ancho
        animations:
          open: open_double                    # los nombres por defecto
          close: close_double
        storage-title: "<dark_red>Large Ruby Chest"
        # o, sin animación: model: furniture/ruby_chest_double (un modelo JSON)
```

Como en vanilla: un cofre colocado **al lado** de otro del mismo mueble (a su izquierda o a su
derecha, no delante ni detrás), mirando hacia el mismo lado y todavía simple, se une a él en un
cofre doble. Agachado no se une. Solo se une si quien lo coloca podría abrir también el otro
(comprobación de contenedor de WorldGuard/GriefPrevention): unirse no es una forma de entrar en el
cofre de otro.

- **La unión.** Los dos cofres vanilla de debajo pasan a ser las dos mitades de un cofre doble
  vanilla (`type=right` y `type=left`, sin física): su hitbox, su forma para tolvas y pistones. Se
  quitan los dos modelos simples y aparece **uno** centrado entre las dos mitades, con el modelo
  doble. El par se guarda en memoria al instante y en la tabla `furniture_pairs` (esquema 7) en
  **una transacción** —borrar los pares que tocaran cualquiera de las dos posiciones e insertar el
  nuevo—, en el hilo `ArkContent-DB`. Si alguien tenía abierto uno de los dos, se guarda y se cierra
  antes.
- **El modelo.** Con `animated-model`, el `.bbmodel` doble se convierte como el simple (un display
  por hueso, ver arriba) con sus propios modelos (`<ns>:<id>/double/bone_<hueso>`), y un display raíz
  ancla el conjunto. Con `model`, un único `ItemDisplay` con el ítem `<ns>:<id>/double`. El modelo
  doble mide 32 px de ancho: x de -15 a 15 alrededor del centro entre los dos bloques (el de la demo,
  47 cubos, va de -15,5 a 15,5).
- **54 huecos, cada mitad en su fila.** El inventario doble tiene 54 huecos: las filas 1-3 son la
  mitad derecha de vanilla (la de la **izquierda** de quien mira el frente) y las 4-6 la otra. Cada
  mitad sigue guardando sus 27 huecos en su propia fila de `furniture_storage`, así que unir o separar
  no mueve ningún ítem. Al guardar se escriben las dos filas en una transacción, y al abrir se leen
  las dos en una sola tarea. Abrirlo desde cualquiera de las dos mitades es el mismo inventario; se
  comprueban las protecciones de las dos.
- **La tapa, a los 63 ms.** `open_double` y `close_double` se reproducen en el hilo principal en el
  **primer tick que empiece al menos `furniture.double-chest-animation-delay-ms` (63) después del
  clic**. El reloj lo decide, no un número de ticks: un clic llega entre dos ticks, así que contar 2
  ticks caería entre 50 y 100 ms después. Con ticks de 50 ms, la tapa se mueve entre 63 y 113 ms
  después del clic; 0 la mueve al instante. Abrir y cerrar conservan su orden. El cofre simple sigue
  moviéndose en el mismo tick del clic.
- **Separar.** Romper una mitad la separa: se va el modelo doble, la otra vuelve a ser un cofre
  simple con su modelo simple y sus 27 huecos, y la mitad rota suelta su ítem y **su** contenido (las
  filas que le tocaban). Se borra el par de `furniture_pairs`.
- **Tras un reinicio o un crash**, al cargarse el chunk se compara lo que se ve con la base de
  datos, que se escribieron en momentos distintos: un modelo doble sobre dos cofres que siguen unidos
  se vuelve a registrar; uno cuyos cofres ya no se unen se separa; un par registrado para un cofre que
  se dibuja simple se olvida.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.9, cofres dobles)

Con un bot de mineflayer (cliente 1.21.8) contra el servidor de prueba:

| Prueba | Resultado |
|---|---|
| Unión | Dos `demo:ruby_chest` mirando al sur, en x=104 y x=105: `execute if block` confirma `type=right` en 104 y `type=left` en 105; una fila en `furniture_pairs` (104 → 105) |
| Caras distintas | Un primer cofre que quedó mirando al norte (el bot giró tarde) no se unió al de al lado, que miraba al sur: correcto |
| Agachado | Un tercer cofre colocado agachado junto al par queda `type=single` y el par sigue `type=left` |
| Inventario | El cliente recibe una ventana `generic_9x6` (54 + 36 del jugador) titulada «Large Ruby Chest»; 10 diamantes al hueco 0 y 5 esmeraldas al 40 dejan dos filas en `furniture_storage`, una por mitad |
| Desde la otra mitad | Abrir el cofre de x=105 muestra lo mismo: diamantes en 0, esmeraldas en 40 |
| Reinicio | Tras parar y arrancar el servidor, el par y los dos contenidos siguen; abre con 54 huecos |
| Tapa | Primer movimiento de un display del cofre doble, medido en el cliente desde que sale el paquete del clic: ~100 ms al abrir y ~100-130 al cerrar (189 ms en la primera prueba, con el servidor aún construyendo el pack); nunca por debajo de 63 |
| Romper una mitad | Romper x=104 suelta el cofre y sus 10 diamantes; x=105 vuelve a `type=single`, abre «Ruby Chest» con 27 huecos y las esmeraldas en el hueco 13 (40 − 27); el par y la fila de 104 desaparecen; su tapa se mueve en el mismo tick (28 ms) |

Lo que no se ha podido ver es el dibujo en un cliente real: lo verificado son los bloques, la base
de datos, los paquetes y que los displays del cofre doble se animan; el aspecto del modelo, no.

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

### Muebles de varios bloques (`size:`)

```yaml
    furniture:
      support: BARRIER                # o LIGHT
      size: {width: 3, height: 2}     # o size: 3x2; de 1 a 6 en cada sentido
      # 3.1: también hacia atrás: size: {width: 6, height: 2, depth: 6}, o size: 6x2x6
```

El mueble ocupa `width` bloques a lo largo de su frente, centrados en donde se coloca (con un ancho
par, sobra uno a la derecha de quien lo mira), `height` hacia arriba y (3.1) `depth` hacia atrás,
alejándose de quien lo coloca; el modelo se dibuja centrado en todo ese volumen. **3.2:** el frente
mira hacia **donde está** quien lo coloca, redondeado a 90°, no hacia donde mira: colocado desde un
lado mirando al frente, se gira hacia ese lado. Solo si está justo encima (a menos de un tercio de
bloque del centro) decide hacia dónde mira, como antes. Un modelo Java solo
llega de -16 a 32 en cada eje (tres bloques): uno más alto o más ancho se dibuja más pequeño y se
agranda con `display.scale` (el pozo de 6×6 va a escala 2), o se baja un bloque y se sube con
`display.translation` (la forja de tres de alto). Cada bloque lleva su soporte
invisible, sólido con `BARRIER`, y todos quedan registrados y vinculados al mismo display: clicar o
golpear cualquiera de ellos es clicar el mueble, y romper uno lo rompe entero. Antes de colocarlo
(`BlockPlaceEvent`, prioridad `HIGH`) se comprueba que todos esos bloques estén libres y que las
protecciones dejen construir en cada uno; si no, no se coloca (sin sitio, se avisa en la barra de
acción).
Solo para soportes `BARRIER` y `LIGHT`: un cofre o una cama son bloques vanilla con su propia forma.

### Tablón de misiones (`medieval_bulletin_board`)

```yaml
  medieval_bulletin_board:
    type: custom_furniture
    resource:
      model: furniture/medieval_bulletin_board
    furniture:
      support: BARRIER
      interactable: medieval_bulletin_board   # también vale: quest_board
      size: {width: 3, height: 2}
      board-title: "Quest Board"
```

Clic derecho en cualquiera de sus 6 bloques abre los **avisos** publicados, 45 por página, sobre una
textura de pergamino (una imagen PNG completa detrás del cofre, como en *Explorador de contenido*):
qué pide cada uno, a quién y qué paga, y cómo va el jugador. Clic en un aviso lo acepta; clic en uno
aceptado lo entrega cuando está hecho; mayúsculas+clic en uno aceptado lo abandona. «My notices»
muestra los que el jugador tiene en curso, de cualquier tablón.

```
filas 1-5   los avisos: título, descripción, requisitos ✔/✘, objetivos con su cuenta, recompensas
fila 6      [Previous] [The board (n)] [My notices (n)] [ ] [Page n of m] [ ] [ ] [Close] [Next]
```

**Qué puede pedir un aviso.**

| | Cómo se escribe | |
|---|---|---|
| Requisito: nivel de habilidad | `skills: {fighting: 5}` · `require <id> skill fighting 5` | AuraSkills; `auraskills/mining` o `mi_plugin/habilidad` para habilidades con espacio de nombres |
| Requisito: nivel de trabajo | `jobs: {miner: 10}` · `require <id> job miner 10` | ExcellentJobs o Jobs Reborn (el mayor de los que respondan); sin el trabajo, nivel 0 |
| Requisito: permiso | `permission: vip.quests` · `require <id> permission vip.quests` | |
| Objetivo: entregar | `{deliver: demo:ruby, amount: 8}` · `objective <id> deliver demo:ruby 8` | un ítem propio o vanilla (`cobblestone`); un ítem propio nunca cuenta como su material; se quita del inventario al entregar |
| Objetivo: matar | `{kill: zombie, amount: 10}` · `objective <id> kill mythicmobs:SkeletonKing 1` | un tipo de criatura, o un mob de MythicMobs; solo cuenta lo que mata el jugador |
| Objetivo: romper | `{break: coal_ore, amount: 16}` · `objective <id> break demo:ruby_block 4` | un bloque vanilla o propio; solo en supervivencia o aventura |
| Recompensa: dinero | `money: 150` · `reward <id> money 150` | Vault |
| Recompensa: ítems | `items: ["demo:ruby_sword 1"]` · `reward <id> item demo:ruby_sword 1` | lo que no cabe se suelta a los pies |
| Recompensa: el ítem de la mano | `reward <id> hand` | la pila exacta, con todos sus datos |
| Recompensa: comando | `commands: ["give %player% diamond 1"]` · `reward <id> command ...` | por consola |

Hasta 8 objetivos por aviso; los requisitos se comprueban al aceptarlo. Un aviso `repeatable` se
puede volver a aceptar después de entregarlo. Los avisos se escriben en el juego:

```
/arkcontent board create bounty <red>Zombie Bounty        un borrador, sin publicar
/arkcontent board desc bounty <gray>Two zombies, quickly.  una línea de descripción (cleardesc las borra)
/arkcontent board objective bounty kill zombie 2
/arkcontent board reward bounty money 50
/arkcontent board reward bounty item demo:ruby 3
/arkcontent board post bounty                              publicarlo (unpost lo retira)
/arkcontent board list | info <id> | title | icon | repeatable | clear <id> requirements|objectives|rewards
/arkcontent board boards <id> demo:medieval_bulletin_board   en qué tablones sale (all: en todos)
/arkcontent board delete <id> | reset <jugador> <id>
```

o como plantillas en el YAML de `contents/`, bajo `quests:` (ver `contents/demo/quest_board.yml`), que
`/arkcontent board post <id>` copia a la base de datos y publica. Un tablón se identifica por el id
de su mueble; un aviso sin `boards` sale en todos.

- **Nada de disco en el hilo principal.** Los avisos (tabla `quest_notices`, el aviso en JSON con
  su versión de formato) y el progreso de cada jugador (`quest_progress`) se leen y escriben en el
  hilo `ArkContent-DB`. Abrir un tablón lee de forma asíncrona y abre el menú cuando llega la
  respuesta. Muertes y bloques se cuentan en memoria y se escriben cada `quest-boards.save-seconds`
  (10), al salir el jugador y al entregar.
- **Una sola vez.** Aceptar y entregar son una sentencia cada una cuya condición comprueba SQLite
  (`... WHERE state = 'ACCEPTED'`), así que un doble clic o dos clics a la vez solo cuentan una vez.
  Los ítems de un objetivo de entrega se quitan antes de completarlo en la base de datos y se
  devuelven si eso falla; el dinero y las recompensas se dan solo cuando la base de datos confirma.
- **Cambios de los administradores en orden.** Cada cambio a un aviso (leerlo, cambiarlo,
  escribirlo) es una sola tarea en el hilo de la base de datos, así que una tanda de comandos
  enviada de golpe (un guion, la consola) se aplica entera y en orden. Un ítem que no existe o un
  número fuera de rango se rechazan antes de enviar nada.
- `quest-boards.max-active` (5) avisos en curso a la vez por jugador. `background` y
  `background-width` eligen la imagen del fondo (por defecto, el pergamino que trae el plugin).

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.9, tablón)

Con un bot de mineflayer, AuraSkills 2.3.12, ExcellentJobs, Vault y EssentialsX:

| Prueba | Resultado |
|---|---|
| Colocar | 6 barreras (x 99-101, y -60 y -59) y 6 filas en `custom_blocks_world`; los bloques de los lados siguen vacíos |
| Sin sitio | Un segundo tablón cuyo frente pisaría x=101 no se coloca; el bot conserva el ítem |
| Comandos | 15 comandos enviados de golpe por consola (crear, descripción, objetivos, recompensas, requisito, publicar, en dos avisos) se aplican todos y en orden; `deliver demo:nope 2` y `item nothing_at_all 1` se rechazan («is no item»), `kill zombie 0` también («1 to 100000») |
| Plantillas | `post ruby_delivery`, `zombie_hunt` y `miners_errand` copian las plantillas de `quest_board.yml` y las publican |
| Abrir | Título `U+F808 U+F007 U+F80C U+F80A U+F808 U+F801` en blanco + «Quest Board» en #404040; 5 avisos con su lore: «✘ Fighting level 2 (you: 0)» (AuraSkills da 0, igual que `%auraskills_fighting%`), «✘ Miner (job) level 1 (you: 0)» (sin el trabajo), «✔ Permission arkcontent.board» |
| Requisito no cumplido | Clic en «Zombie Hunt» no lo acepta |
| Entrega | Aceptar «Rubies for the Smith» con 8 `demo:ruby`: «Bring 8 Ruby (you have 8)», «Click to hand it in!»; dos clics seguidos al entregar pagan **una vez**: saldo de $1,000 a $1,150, una Ruby Sword, los rubíes quitados, `completions` 1, y «click to take it again» (repetible) |
| Matar y romper | Un zombi muerto por el bot y un mineral de carbón roto: «Kill 2 Zombie: 1/2», «Break 2 Coal ore: 1/2»; al salir el bot, `progress` 1 en las dos filas |
| Reinicio | Tras reiniciar el servidor, el tablón muestra 1/2 en los dos; el segundo zombi y el segundo carbón dan 2/2, «Click to hand it in!»; entregar ambos da $50 + $10 (saldo $1,150 → $1,210) y 3 rubíes; los dos quedan `COMPLETED` y el no repetible pone «Done» |
| 1.9.1 | Un aviso cuyo título no trae color llega con `#E6CB98` (antes `#4a3420`, casi invisible sobre el fondo oscuro del tooltip); el pergamino ya no tiene el sello rojo de la esquina izquierda debajo del título, y al arrancar se vuelve a extraer y entra en el pack (mismo SHA-1 en `data/builtin/` y en el ZIP) |

### Muebles medievales interactivos (1.9.5, rehechos en 1.9.6)

`contents/medieval_props/` trae **60 muebles** para la plaza de un spawn: 59 modelos Java y la
guillotina, de Blockbench, cada uno con su propia textura en `textures/furniture/<id>.png`. En un
servidor que ya tenía `contents/` se copia la primera vez que arranca (una carpeta de pack nueva sí
se crea en una actualización; una que ya se ofreció y se borró no vuelve).

**1.9.6: modelos y texturas rehechos.** Cada cara de cada cubo tiene su propio rectángulo en la
textura del mueble, a la densidad de Minecraft (un texel por píxel de modelo), pintado para su
material y su tamaño: la veta de la madera sigue la pieza (sube por una pata, corre a lo largo de un
listón, y los cortes muestran los anillos), los tablones llevan juntas y clavos, el metal bisel y
remaches, el cuero costuras o capitoné, la tela su trama, y los carteles y estandartes su emblema
pintado (monedas, jarra, yunque, balanza, herradura, matraz, luna; corona y flor de lis). Todas las
caras llevan un borde claro arriba a la izquierda y oscuro abajo a la derecha, para que se lean como
sólidas con la luz plana del juego. La geometría también ganó detalle: calderos redondeados con su
contenido a la vista (estofado con cucharón, pociones con llama azul, la bruja con un hueso), barriles
y jarras abombados, carteles colgados de un brazo con cadenas, estandartes con cola de golondrina,
maniquí con peto, hombreras y yelmo, carro con ruedas de radios, horno con su pala, forja con fuelle,
trono con respaldo capitoné, y la guillotina con la hoja en diagonal, cesta de mimbre y la cuerda de
la palanca a la polea. Se mantienen las alturas de asiento y los puntos de donde salen las partículas.

Al actualizar desde la 1.9.5, los archivos del pack que no habías cambiado (el `furniture.yml` y los
61 modelos) se sustituyen solos por los nuevos, y las 60 texturas se añaden; lo que hubieras editado
se queda como está. El `medieval_atlas.png` de la 1.9.5 deja de usarse y puede borrarse.

**3.1: los 60 hacen algo con clic derecho, y tres son grandes.** Las mesas abren una mesa de trabajo,
el yunque un yunque, la piedra de afilar una afiladora; el armero, el maniquí, el puesto de mercado y
la pila de leña guardan cosas; los estandartes dan fuerza o resistencia; el carro de heno es un
asiento; el abrevadero cura y salpica; los faroles, lámparas y candelabros se apagan y se encienden.
La fogata y el brasero asan lo que se les acerca en la mano (`cook: campfire`), el horno de pan
hornea (`smoker`), y la **forja** funde mineral y metal en bruto (`blast`: hierro, oro, cobre, chatarra
antigua) o, con la mano vacía, abre una mesa de herrería. La forja ocupa ahora **3×3×2** (ancho, alto,
fondo) con una textura nueva a doble densidad (piedra con hollín, ladrillo, brasas que brillan por la
boca del hogar, fuelle de cuero, cubo de temple) y alumbra la plaza con luz 15 encima; el **pozo de
los deseos** ocupa **6×6** sobre su enlosado, con brocal octogonal, tejadillo, torno y cubo; la **pila
de leña** es de **3×2×2**. Las piezas de estos tres que ya estuvieran colocadas conservan sus bloques
de antes: rómpelas y vuelve a ponerlas para que ocupen su tamaño nuevo. Sus modelos y texturas se
actualizan solos si no los habías editado.

**3.2: el trono, el horno de pan y el mapa, grandes.**

- **Trono real, 3×3×2.** Sobre un estrado de dos escalones de piedra con alfombra roja de ribete
  dorado: sillón de roble oscuro con cojín de terciopelo capitoné, reposabrazos con pomos de oro,
  respaldo alto con panel de terciopelo enmarcado en oro, corona con un rubí en lo alto, y un
  estandarte con corona a cada lado. Un solo asiento en el centro, a un bloque de altura
  (`seat-at`); ver *La cámara en tercera persona*.
- **Horno de pan, 3×2×2.** Cúpula de barro escalonada sobre una base de piedra, boca encendida en
  un arco con clave, leña apilada en un hueco de la base, puerta de ceniza de hierro, chimenea
  detrás, hogazas en la repisa, un saco de harina y la pala apoyada al lado. Da luz 12, echa humo
  por la chimenea y chispas por la boca, y hornea igual que antes.
- **Mapa del reino, 6×4** (a escala 2): dos postes y un travesaño con tejadillo, un tablero
  enmarcado con tachuelas de latón y una repisa con tintero y pergamino. El **dibujo es una textura
  aparte**, `textures/furniture/mapa_mural_mapa.png` (320×176), que el plugin dibuja **a partir del
  mundo del servidor**:
  - `/arkcontent map spawn` dibuja los alrededores del spawn del mundo en el que estás (desde
    consola, del primer mundo).
  - `/arkcontent map` dibuja alrededor de donde estás.
  - Un número al final es el radio en bloques a lo ancho, de 40 a 640 (por defecto 160: 320×176
    bloques, un bloque por píxel). Con más radio, hasta 4 bloques por píxel (1280×704).
  - Cada píxel es el bloque más alto de esa columna con su color de mapa vanilla, más claro donde el
    terreno sube hacia el norte y más oscuro donde baja, y el agua más oscura cuanto más honda. Todo
    sobre pergamino: colores tirando a sepia, grano, bordes tostados, doble marco de tinta, rosa de
    los vientos con el norte en rojo arriba a la derecha y una cruz roja en el spawn.
  - Los chunks se cargan sin generar ninguno (lo que nadie ha explorado queda en pergamino); de cada
    uno se toma una instantánea en el hilo principal, y la lectura, el dibujo y el PNG van fuera de
    él. Después se recompila el pack y se manda a los jugadores conectados.
  - Si cambia el spawn, vuelve a lanzarlo. Mientras no lo lances, el tablero trae un mapa inventado.
    El dibujo es tuyo: al actualizar no se toca.

Los modelos, las texturas y el `furniture.yml` de ejemplo se actualizan solos si no los habías
editado, y el dibujo del mapa se añade. Si los tuyos ya estaban colocados con su tamaño de antes
(1×2), rómpelos y vuelve a ponerlos.

| Cliente 1.21.8 real y bots, Paper 1.21.8 (3.2) | Resultado |
|---|---|
| Actualización desde 3.1.1 | «Updated 7 example file(s) you had not changed» (el `furniture.yml` y los modelos y texturas de los tres) y «Added 1 example file(s)» (el dibujo del mapa) |
| Colocar el horno desde un lado (de pie al este, mirando al oeste) | se gira hacia el este, hacia quien lo coloca; trono y mapa colocados de frente miran al sur |
| Los tres en el cliente | con su textura, sin errores de modelo en el registro del cliente; humo por la chimenea del horno |
| `/arkcontent map spawn` (mundo plano de pruebas) | 320×176 bloques de 204 chunks en un segundo; pack recompilado y enviado; el tablero muestra la hierba, las plataformas grises, lo no explorado en pergamino, la rosa y la cruz en el spawn |
| Sentarse en el trono | asiento en (x + 0,5, y + 1, centro − 0,2): en el centro, un bloque arriba; el que se sienta ve las 18 barreras como aire y el otro jugador las sigue viendo; al levantarse queda delante y las vuelve a ver |
| Primera versión (sin el tick de espera) | el bloque clicado volvía a llegar como barrera (17 de 18): el servidor lo reenvía tras cancelar el clic |
| Tercera persona en el trono (F5) | detrás: se ve el respaldo entero desde la distancia normal; delante: el jugador sentado en el trono, de cuerpo entero |
| Guillotina | arrodillado ve 0 de sus 6 barreras, otro jugador las 6; al levantarse, las 6 |
| `legacy look` | quita el display sin marcas al que se mira y deja el de al lado; mirando al trono: «... is a piece of medieval_props:trono_real that is still standing» |
| Horno y mapa con clic | «Pan recién horneado.»; «[Mapa] Estás en la plaza del reino, en ...» |

| Clave en `on-click:` (3.1) | Qué hace |
|---|---|
| `station: crafting` | abre una mesa de trabajo; también `anvil`, `grindstone`, `smithing`, `stonecutter`, `loom`, `cartography`, `enchanting` |
| `cook: blast` | cocina lo que el jugador tiene en la mano con las recetas del servidor: `furnace`, `blast`, `smoker` o `campfire`; da la experiencia de la receta. Sin nada cocinable en la mano pasa a `station` (si hay) o lo dice |
| `toggle-light: true` | apaga y enciende la luz del mueble (y sus partículas) |
| `particles: [...]` | una ráfaga al clicar, además de las de siempre |

`light:` funciona ahora también con `support: BARRIER`: la luz va en el bloque libre justo encima del
mueble (si está libre), así que un mueble sólido puede alumbrar.

| Tipo | Muebles | Clic derecho |
|---|---|---|
| `seat` (14) | sillas de taberna, taburetes, banco largo y de iglesia (2×1), trono (3×3×2 desde 3.2, un asiento en el centro), tocón, fardo, sillón, carro de heno… | sentarse |
| `effect` (25) | jarras, copa, licor, barril con grifo, calderos, fogata y brasero (asan), candelabro, faroles y lámparas (se apagan), pozo (6×6), alambique, forja (3×3×2: funde y abre la mesa de herrería), horno de pan (3×2×2 desde 3.2: hornea), mesas (mesa de trabajo), yunque, piedra de afilar, estandartes, abrevadero | partículas; según el mueble: pociones, estación, cocinar o luz |
| `command` (10) | carteles de banco, taberna, herrería, mercado, establo, alquimista y posada, campana, buzón, mapa (6×4 desde 3.2, con el spawn dibujado) | ejecuta sus comandos |
| `storage` (10) | barril, caja, baúl, estantería de botellas, tonel de vino (54 huecos), saco, armero, maniquí, puesto de mercado (3×2), pila de leña (3×2×2) | abre su inventario |
| `guillotine` (1) | `guillotina_plaza`, 2×3 | cepo: arrodillarse · palanca: dejar caer la hoja |

Para verlos todos de golpe: `/arkcontent showcase medieval_props` los coloca en filas delante de quien
lo ejecuta, mirándole —cuatro por tick, para no frenar el servidor—, y dice cuánto tardó el tick más
lento.

#### `interactable: effect`

```yaml
  caldero_estofado:
    type: custom_furniture
    furniture:
      interactable: effect
      effect:
        interval: 8          # ticks entre ráfagas (1-1200)
        range: 24            # bloques: quién las ve
        particles:           # solo partículas sin datos (flame, smoke, witch, bubble_pop...)
          - {particle: campfire_cosy_smoke, offset: [0, 0.9, 0], count: 1, spread: 0.15, speed: 0.01}
        on-click:            # opcional: lo que da al beberlo
          potions: ["saturation 2 3", {effect: speed, seconds: 30, amplifier: 0}]
          sound: minecraft:entity.generic.eat
          cooldown: 30       # segundos, por jugador y mueble
          message: "<gold>Estofado caliente."
```

`offset` es `[a lo ancho, arriba, adelante]` en bloques, desde el centro del mueble a ras de suelo,
y gira con él. **Bucle asíncrono:** cada tick el hilo principal solo elige qué muebles tocan este
tick y anota dónde está cada jugador; dónde cae cada partícula, quién está a menos de `range` y el
envío de los paquetes van en el hilo `ArkContent-Effects` (el `ParticleBuilder` de Paper puede
usarse fuera del hilo principal). Una partícula que no existe o que necesita datos (un color, un
bloque) se avisa una vez en consola y se omite. Un mueble solo con partículas no se queda con el
clic: se puede colocar un bloque contra él.

#### `interactable: command`

```yaml
  cartel_banco:
    type: custom_furniture
    furniture:
      interactable: command
      command:
        run:                 # uno o varios; la barra inicial sobra
          - 'minecraft:tellraw %player% {"text":"Bienvenido al banco"}'
          - 'dm open banco %player%'
        as: console          # o player: como si lo escribiera el jugador
        cooldown: 2          # segundos
        permission: plaza.banco    # opcional
        message: "<gold>..." # opcional
        sound: minecraft:block.chain.place
```

Marcadores: `%player%`, `%uuid%`, `%world%`, `%dimension%` (`minecraft:overworld`, para `execute in`),
`%x% %y% %z%` (el bloque del mueble). Los rellena el servidor, nunca el jugador, y cada valor se
comprueba antes de entrar en un comando de consola: un nombre solo puede ser `[A-Za-z0-9_]` (con el
`.`/`*` de Floodgate delante), así que no puede añadir un selector, un argumento ni un segundo
comando; un comando es una línea. Los de los carteles incluidos solo saludan: cámbialos por tu menú,
tu warp o tu tienda. Cada comando ejecutado queda en la consola, con quién y dónde.

#### `interactable: guillotine`

```yaml
  guillotina_plaza:
    type: custom_furniture
    furniture:
      size: {width: 2, height: 3}
      animated-model: furniture/guillotina_plaza   # huesos frame, blade, lever
      interactable: guillotine
      guillotine:
        drop: drop_blade      # animación de la caída (hold: se queda abajo)
        reset: reset_blade    # la que la sube
        delay-ms: 63          # desde el clic hasta que empieza a caer
        impact-ms: 250        # desde ahí hasta que llega abajo (por defecto, lo que dura drop)
        reset-after: 3        # segundos abajo antes de subirla
        lever: {across: 1, up: 0}   # el bloque de la palanca (por defecto, el de abajo a la derecha)
        kneel-height: 0.35   # donde apoya los muslos: con 0.35 la cabeza queda en el agujero del cepo
        victim: occupant      # o user: cae sobre quien tira de la palanca
        respect-pvp: true
        impact-sound: minecraft:block.anvil.land
        impact-particles: [{particle: crit, offset: [0, 1.4, 0], count: 18, spread: 0.3, speed: 0.4}]
        commands: ['minecraft:tellraw @a {"text":"La guillotina ha caído sobre %player%"}']
```

Clic derecho en el armazón: el jugador se arrodilla en el cepo (un asiento, un poco detrás del
armazón, mirando hacia fuera; agacharse lo levanta). Clic derecho en el bloque de la palanca:

1. en el **primer tick que llega al menos `delay-ms` después del clic** empieza `drop_blade`: las nuevas
   transformaciones de los huesos salen como datos de entidad a todos los que lo ven. Con 63 ms y
   ticks de 50 ms es el segundo tick tras el clic, entre 63 y 113 ms después; el reloj decide, no un
   número de ticks, así que el tick en que llegó el clic no lo mueve;
2. `impact-ms` después cae: partículas y sonido del impacto, y quien siga en el cepo recibe un golpe
   letal en el hilo principal —`minecraft:generic_kill`, el daño de `/kill`, que no paran armaduras,
   efectos ni tótems—. Con `respect-pvp` el golpe es del que tiró de la palanca, así que si un plugin
   de protección no deja hacer PvP ahí (WorldGuard, GriefPrevention, EssentialsX tras un
   teletransporte…) no pasa nada y se avisa al que tiró. Luego sus `commands`, desde la consola
   (`%player%` el que muere, `%killer%` el que tiró). Con el cepo vacío la hoja cae sin más;
3. `reset-after` después, `reset_blade` la sube; hasta entonces la palanca no hace nada.

Quien se levanta del cepo antes de que llegue la hoja se salva.

**Luz.** Los faroles, la lámpara, el candelabro, la fogata y el brasero están sobre bloques `LIGHT`
con su nivel (`light:`), que se pone al colocarlos, en el mismo tick, en cada bloque que ocupan. Ese
nivel es parte del bloque y lo guarda el propio mundo con su chunk; no cambia mientras el mueble
existe (un clic con un bloque de luz en la mano no lo altera), así que no se duplica en SQLite: lo
que sí va al hilo `ArkContent-DB`, en una transacción por mueble, son sus posiciones.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.9.5)

Con bots (mineflayer) y una sonda de pruebas que mide cada tick; los scripts están en el scratchpad
de la sesión (`bot/medieval195.js`, `guillo195.js`, `deaths195.js`).

| Prueba | Resultado |
|---|---|
| Actualización desde 1.9.2 | «Added 63 example file(s)»: `contents/medieval_props/` creado; 1669 ítems (60 más) |
| `showcase medieval_props` | 60 muebles, 97 filas en la base de datos (6 de la guillotina, 6 del puesto); 15 ticks, el más lento 4,7-14,9 ms (antes de repartirlo por ticks: 59 ms en uno) |
| Asientos | silla: sentado a 0,55 + 0,6 (desde 3.0.3, a 0,55: ver *Altura*); trono: clic en el bloque de arriba sienta abajo, 3 s sentado con salud 20 (sin asfixia), se levanta delante |
| Bebida | jarra de cerveza: `white_ash` a 0,45 bloques, 4 paquetes en 4,2 s (cada 20 ticks); al beber, Speed 600 ticks y «¡Salud!»; otro clic: «Not yet: 9s» |
| Caldero | `campfire_cosy_smoke` + `bubble_pop`, 14-16 paquetes en 3 s (cada 8 ticks) |
| Carteles | banco, taberna, herrería, mapa: su mensaje en el chat y la línea en consola `medieval_props:cartel_banco at world 131,-60,97 (Tester): console: /minecraft:tellraw Tester ...`; campana: `block.bell.use`; buzón (como jugador): «You do not have any mail.» |
| Guillotina vacía | primer paquete de huesos 91-98 ms tras el clic; partículas de impacto (`crit`, `electric_spark`, `enchanted_hit`) a 328-345 ms, a 1,40 bloques; otro tirón: «The blade is not back up yet» |
| Guillotina con víctima | arrodillada 4,5 s con salud 20; primer paquete de huesos 94-99 ms; «Victim was killed» y «[Plaza] La guillotina ha caído sobre Victim.» para los dos |
| PvP denegado (WorldGuard) | «Hey! Sorry, but you can't PvP here.», la víctima sigue con 20; el evento lo cancela WorldGuard |
| Romperla con alguien dentro | se le levanta delante; filas de la guillotina 6 → 0 |
| Barril | inventario de 27 + el del jugador; al cerrarlo, una fila en `furniture_storage` |
| TPS | con los 60 muebles y sus bucles de partículas y el jugador en medio: 20.0 / 20.0 / 20.0, MSPT media 1,7-1,9 ms, máx. 5,3-6,4 ms en 5-10 s; 67 paquetes de partículas por segundo al jugador |

#### Y en la 1.9.6

Sobre el mismo servidor, con los archivos de la 1.9.5 sin tocar en `contents/medieval_props/`:

| Prueba | Resultado |
|---|---|
| Actualización desde 1.9.5 | «Added 60 example file(s)» (las texturas) y «Updated 62 example file(s) you had not changed» (`furniture.yml` y los 61 modelos); el pack generado lleva las 60 texturas en `assets/medieval_props/textures/furniture/`, las 60 en el atlas de bloques, y los huesos de la guillotina |
| `showcase medieval_props` | 60 muebles, 97 filas; el tick más lento 6,4 ms (25,3 ms la primera vez tras reiniciar, con todo en frío) |
| La misma batería de bots | asientos, bebida (Speed 600 ticks, «Not yet: 9s»), `white_ash` a 0,44 bloques, caldero 16 paquetes en 3 s, carteles con su mensaje y su línea en consola, campana, buzón, guillotina (primer paquete de huesos 101-102 ms, impacto a 346 ms a 1,40 bloques, muerte y aviso; PvP denegado por WorldGuard), romperla (6 → 0 filas), barril guardado |
| TPS | 20.0 / 19.99 / 20.0 con los 60 muebles; 67 paquetes de partículas por segundo |

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

**Piezas sin `equipment:` (3.0.2).** Una armadura sin sección `equipment:` se llevaba como su
material: una `NETHERITE_CHESTPLATE`, de netherita. Es lo que pasa con las piezas que trae un pack
generado de ItemsAdder (`/arkcontent import`), que solo tiene sus iconos: un set cuyo aspecto 3D
pone otro plugin —MythicArmor dibuja la Darksteel Knight con
`mythicarmor:darksteel_knight_armor_chest_piece`— no tiene nada que lo una a
`darksteel:darksteel_knight_chestplate_item` salvo el nombre. Así que en cada recompilación, a cada
pieza sin `equipment:` se le busca en el pack ya compilado el asset de equipo que se llama igual:

- de los dos nombres se quitan las palabras que solo dicen qué pieza es (`helmet`, `casco`,
  `chestplate`, `chest`, `peto`, `leggings`, `legs`, `grebas`, `boots`, `feet`, `botas`…) o que es
  armadura (`item`, `piece`, `armor`, `armadura`, `set`…), y lo que queda tiene que coincidir:
  `darksteel_knight`. Un nivel como `t2` cuenta (`cleric_chestplate_t2` →
  `cleric_armor_t2_chest_piece`, no `cleric_armor_chest_piece`);
- un asset con el nombre de un hueco solo se lleva en ese hueco; uno sin hueco (el de todo el set,
  `astralion_armadura`) en cualquiera;
- si encajan varios: primero el que nombra el hueco, después el del namespace del ítem; si aun así
  quedan varios, la pieza se queda como estaba y la consola lo dice.

Un casco sin asset cuyo modelo es 3D (`elements`) y dice cómo va en la cabeza (`display.head`) se
lleva como ese modelo: el casco de la Darksteel. El cuero se deja en paz: su aspecto depende del
color de la stack, que el pack no dice. En consola, cada recompilación lo resume:

```
[armor] 3 armour piece(s) with no equipment section, worn as the pack's equipment asset of the same name: darksteel:darksteel_knight_boots_item -> mythicarmor:darksteel_knight_armor_feet_piece, ...
[armor] 1 helmet(s) worn as their 3D model: darksteel:darksteel_knight_helmet.
```

Las stacks que ya tienen los jugadores se ponen al día al entrar, al ponerse la pieza y después de
cada recompilación para los que están conectados (solo el aspecto: nombre, lore y encantamientos no
se tocan). Para que una pieza siga siendo de netherita, o se lleve con otro asset, escríbele su
`equipment:` (por ejemplo `slot: CHEST` y `asset: minecraft:netherite`): una pieza con sección
propia nunca se toca. Verificado en el servidor de prueba con las piezas importadas de la Darksteel
Knight: de 1674 ítems se enlazan exactamente las tres del cuerpo y el casco, y una pechera hecha con
3.0.1 se pone al día en cuanto se la pone el jugador.

**Actualizar desde una versión anterior.** El pack de ejemplo se copia entero solo cuando
`contents/` no existe. Para que lo nuevo llegue también a un servidor que ya lo tenía, cada versión
agrupa los ejemplos que añadió (1.1: cultivos y emojis; 1.3: `furniture.yml` con el cofre y la cama;
1.4: la armadura; 1.5: `advancements.yml` y el fondo de su pestaña; 1.9: `quest_board.yml` con el
tablón y su modelo y textura, y el modelo del cofre doble) y el plugin recuerda en
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
    glow: 10                                    # 2.0: se ve con esa luz aunque esté a oscuras (0-15)
    contact:
      element: acid                             # fire, frost, poison, wither o acid
      damage: 1.5
      interval-ticks: 20
      mobs: true                                # 2.0: también a los mobs; a los jugadores, siempre
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

**Brillo (2.0).** `glow: 0-15` pone `"light_emission": N` en los elementos del modelo del líquido,
igual que hace vanilla con sus plantas que brillan (`cross_emissive`). El cliente lo dibuja con esa
luz aunque esté a oscuras. No ilumina lo que tiene alrededor: eso solo lo hace un bloque que emite
luz, y el tripwire no la emite. Si das tus dos modelos (`model` y `flowing-model`), `glow` no se
aplica y se avisa: el `light_emission` va entonces en tus modelos.

**Mobs (2.0).** Con `contact: { mobs: true }`, los mobs que estén dentro reciben lo mismo que un
jugador, cada `interval-ticks`. Solo se miran los chunks donde hay ese líquido, solo si sus
entidades están cargadas, y se respeta `arkcontent-liquid-damage` y los claims igual que con los
jugadores. Sin `mobs`, a los mobs no les pasa nada (como antes).

**Por qué no se mezcla con el agua ni con la lava.** Para el cliente, el líquido no es un fluido:
es un bloque de tripwire dibujado con su modelo. El cliente solo trata como fluido el agua y la
lava, así que no le aplica nada de ellas: ni la niebla, ni el nado, ni las caras compartidas con el
agua de al lado, ni el contacto agua-lava que hace piedra u obsidiana. En el servidor, el agua y la
lava que intentan entrar en él se cancelan (`BlockFromToEvent`) y no lo sustituyen. La textura se
anima con su `.png.mcmeta`, que va al pack junto al PNG (desde 1.7).

**Shaders (2.0).** Un pack de contenido puede traer los suyos:

| En `contents/<pack>/` | En el pack | |
|---|---|---|
| `shaders/core/*.vsh`, `*.fsh`, `*.json` y `shaders/post/…` | `assets/minecraft/shaders/…` | el cliente solo lee los shaders principales del espacio `minecraft`: estos sustituyen a los de vanilla |
| `shaders/include/*.glsl` | `assets/<pack>/shaders/include/` | se importan con `#moj_import <pack:archivo.glsl>` |

Cualquier otro archivo en `shaders/` se avisa y se deja fuera. Al construir el pack se leen todos
sus shaders, los de los packs de contenido y los de los packs fusionados (ItemsAdder…), contra lo
que hace con ellos el cliente 1.21.8. Las tablas salen de su propio jar: qué shaders principales
carga, qué bloques de uniformes da a cada uno y qué archivos de `include` trae. Solo se revisan los
que ese cliente carga de verdad (formato 64): un overlay para otras versiones, o un archivo que
otro overlay sustituye, se cuenta aparte. Se avisa de:

- la primera directiva no es `#version`;
- un `#moj_import` que nada del pack ni de vanilla proporciona;
- uniformes sueltos (`uniform float GameTime;`), es decir, escrito para 1.21.4 o antes: desde
  1.21.5 van en bloques (`Globals`, `Fog`…) y el aviso dice en cuál está cada uno;
- un shader principal que el cliente 1.21.8 no carga;
- un bloque de uniformes que no se le da nunca.

`pack.shaders` decide qué hacer: `keep` (por defecto) solo avisa; `drop-broken` deja fuera además
cada shader que no compilaría o dibujaría mal, con su otra mitad; `drop` los deja todos fuera.

El plugin **no trae ningún shader activado**. Un shader principal sustituye al de vanilla para todo
lo que dibuja, no solo para el líquido; y si falla, solo se nota en el cliente, no en el servidor ni
en el ZIP. Sin un cliente real con el pack no se puede comprobar cómo queda. Lo que sí se ha
comprobado, en el bytecode del cliente 1.21.8: el shader del terreno (`core/terrain`, el de los
bloques translúcidos como el tripwire) recibe `Globals` y con él `GameTime` aunque su pipeline no
lo declare, porque `RenderSystem.bindDefaultUniforms` lo enlaza al dibujar los chunks. Un shader que
haga ondular o brillar el líquido con el tiempo es posible; el validador lo da por bueno.

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

#### Qué se ha verificado en un servidor Paper 1.21.8 real (2.0, líquidos y shaders)

| Qué | Resultado |
|---|---|
| Ácido con `glow: 10` | en el ZIP, `acid_source` hereda de `arkcontent:block/liquid_source_glow_10`, que lleva `"light_emission": 10` |
| Un cerdo en ácido (`mobs: true`) | 10 → 6 → 2,5 de vida → muerto, envenenado |
| Un cerdo en escarcha líquida (sin `mobs`) | sigue con 10 |
| Shaders con el pack generado de ItemsAdder del servidor (con overlays) y un pack de prueba | «26 shader file(s) in the pack; the 11 a 1.21.8 client loads checked (15 in overlays for other versions, or replaced by one): 2 finding(s)». Los dos avisos son de un `rendertype_translucent.vsh` de 1.21.4 puesto a propósito: el cliente ya no lo carga, y además usa uniformes sueltos. Un `core/terrain.vsh` que usa `GameTime` y los shaders de ItemsAdder que se cargan pasan sin avisos |

Cómo se ve el brillo, o un shader, sigue sin poder comprobarse sin un cliente con el pack.

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
    anchor: { at: above-food, row: 1 }   # 2.0: una fila por encima de las burbujas de aire
    segments: 10
    spacing: -1             # píxeles entre iconos: una gota mide 8, la fuente avanza 9
    value:                  # un valor que guarda este plugin
      max: 20
      per-second: -0.025    # se va vaciando
      empty-damage: 1       # daño por segundo a 0
      consume: { POTION: 8, MILK_BUCKET: 4 }
  mana:
    icons: { full: hud/mana_full, half: hud/mana_half, empty: hud/mana_empty }
    height: 9
    anchor: { at: above-health, row: 1 }
    spacing: -2
    value: { start: 100, max: 100, per-second: 2 }   # con AuraSkills (2.0): { auraskills: mana }
  shield:
    icons: { full: hud/shield_full, half: hud/shield_half, empty: hud/shield_empty }
    height: 9
    anchor: { at: above-health, row: 2 }
    spacing: -2
    value: { stat: absorption, max: 20 }  # 2.0: los corazones dorados, leídos del servidor
```

Sin `anchor` sigue valiendo lo de antes: `ascent` (cuánto sube sobre la línea), `offset` (píxeles
desde donde se escribe) y `action-bar: true|false`. Otro plugin se lee con
`value: { placeholder: "%mmocore_mana%", max: "%mmocore_max_mana%" }`.

**Fuente.** El pack escribe en `assets/minecraft/font/default.json` un glifo por icono, en
U+F000–U+F7FF (los emojis pasan a U+E000–U+EFFF), y un proveedor `space` con los caracteres que la
comunidad ya usa para esto: U+F801…F808 (−1 a −8 px), U+F809…F80F (−16 a −1024), U+F821…F82F (lo
mismo hacia delante). Se escribe aunque no haya HUDs, para menús y marcadores que maquetan con
ellos (`pack.negative-spaces: false` lo quita). El ancho de cada icono se mide en su PNG como lo
mide el cliente (última columna con algún píxel visible, escalada a `height`, más uno). Así la barra
sabe exactamente cuánto retroceder.

**Anclajes (2.0).** Con `anchor`, la barra se coloca contra el HUD de vanilla y el plugin calcula
su `ascent` y su `offset`. Las cuentas salen de la clase `Gui` del cliente 1.21.8:

- la línea de la barra de acción se dibuja en `(ancho/2, alto − 68)` y su texto empieza 4 píxeles
  más arriba;
- la parte de arriba de un glifo queda `7 − ascent` por debajo de donde empieza su línea;
- corazones y hambre están a 39 píxeles del borde inferior, y la armadura y el aire 10 más arriba;
- la barra de objetos llega a 91 píxeles a cada lado del centro.

Todo va en píxeles de la interfaz, así que vale con cualquier tamaño de ventana y escala de GUI. La
línea que envía el plugin tiene ancho neto 0, de modo que el cliente la centra sobre nada y cada
barra empieza exactamente donde dice su `offset`.

| `at` | Fila | En horizontal |
|---|---|---|
| `health` | la de los corazones | desde el extremo izquierdo de los corazones (−91) |
| `food` | la del hambre | termina donde termina el hambre (+91) y se llena desde la derecha, como el hambre |
| `above-health` | la de la armadura, encima de los corazones | como `health` |
| `above-food` | la de las burbujas de aire, encima del hambre | como `food` |

`row: n` la sube n filas de 10 píxeles y `nudge: [x, y]` la mueve píxeles a la derecha y hacia
arriba. Hay un límite: en un glifo de la fuente, el `ascent` no puede pasar de su `height`, así que
un icono bajo no se puede subir mucho sobre la línea en que se escribe. Con iconos de 9 píxeles, lo
más alto es 69 píxeles sobre el borde inferior (`health` fila 3, `above-health` o `above-food`
fila 2). Una fila más alta se baja a la máxima y se avisa al cargar.

**Valores del servidor (2.0).** `value: { stat: <nombre>, max: <n> }` lee del propio jugador
`health`, `absorption` (los corazones dorados: un escudo), `armor`, `toughness`, `food`,
`saturation`, `air`, `level` o `experience`, sin otro plugin. `max` es opcional; si falta: la vida máxima del
jugador, 20 (absorción, armadura, hambre, saturación), 12 (dureza), el aire máximo, 100 (nivel) o 1
(la barra de experiencia). `value: { auraskills: mana }` toma el maná y el máximo de AuraSkills por su
API (`SkillsUser.getMana()`), sin pasar por PlaceholderAPI. Se leen en el hilo principal una vez por
segundo.

**TAB y DeluxeMenus.** El anclaje coloca la barra en la pantalla, y eso solo se puede en la barra de
acción. La envía el plugin, y una barra anclada va a ella salvo `action-bar: false`.
TAB 6.2.0 no tiene barra de acción (comprobado en su jar), así que «encima del hambre» no se puede
hacer desde TAB. Por placeholder, en la cabecera, el pie, el marcador o la bossbar de TAB y en los
títulos de DeluxeMenus, cada plugin decide dónde pinta su texto. Ahí sirve una barra sin `anchor`,
con su `ascent` y su `offset`, y `%arkcontent_hud_<id>_value%` / `_max` valen en cualquier sitio.

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

#### Qué se ha verificado en un servidor Paper 1.21.8 real (2.0, HUDs anclados)

Con un bot (mineflayer): se descodificó la barra de acción que recibió usando la fuente del pack
generado, con el ancho y el `ascent` de cada glifo, como los calcula el cliente.

| Qué | Resultado |
|---|---|
| La línea entera | ancho neto 0 |
| Sed, `above-food` fila 1 | arriba a 59 píxeles del borde inferior; termina a +91 del centro, donde termina el hambre; 7 de 10 llenas, en orden «EEEFFFFFFF»: se vacía por la izquierda |
| Escudo, `above-health` fila 2, con Absorción II | arriba a 69; empieza a −91, donde empiezan los corazones; 4 de 10 llenos; `%arkcontent_hud_shield_value%` = 8, `_max` = 20 |
| Maná, `above-health` fila 1 | arriba a 59; empieza a −91 |
| Maná de AuraSkills 2.3.12 por su API | 20 de 20, lo mismo que `%auraskills_mana%` (20.0) |

Cómo se ve en un cliente real no se ha podido comprobar: no hay cliente con el pack. Las posiciones
salen de las cuentas del propio cliente, no de una captura.

### Jugadores de Bedrock (Geyser) — 3.0

Con **Geyser-Spigot** en el mismo servidor, los jugadores de Bedrock (móvil, consolas, Windows) ven
los ítems, los bloques, los líquidos y los muebles sin que el administrador escriba una línea: ni
`items.json`, ni `blocks.json`, ni un pack aparte. Nada se escribe en la carpeta de Geyser; sus
mapeos se crean en memoria, por su API, a partir del contenido que el plugin ya construye.

```yaml
# config.yml
bedrock:
  enabled: auto     # auto: cuando Geyser-Spigot está instalado · true: siempre · false: nunca
```

**En cada recompilación**, en el hilo `ArkContent-Worker` y justo después de compilar el pack Java,
el conversor (`core.geyser`, sin nada de Bukkit) lee del pack recién compilado los modelos, sus
padres (también los de vanilla más comunes: `cube_all`, `cube_column`, `orientable`...) y las
texturas PNG, y produce:

| Para Bedrock | De dónde sale |
|---|---|
| Un icono por ítem (`textures/items/ark/`) | los modelos planos, sus capas; los 3D, dibujados en isométrico (32×32, con luz de arriba) |
| Un bloque por bloque personalizado | `minecraft:geometry.full_block` con una textura por cara si es un cubo; si no, una geometría propia |
| Dos bloques por líquido (fuente y fluyendo) | su modelo, con `render_method: blend`, sin colisión, con su `glow` como luz |
| Los muebles, cortados en bloques | el modelo del mueble partido en las celdas de bloque que ocupa, una geometría por celda y por cuarto de vuelta |
| Los emojis | hojas de glifos `font/glyph_E0.png`… |
| El `.mcpack` | `output/bedrock/arkcontent-<hash>.mcpack`, con UUID derivado del contenido |

En el servidor de prueba (1673 ítems, 60 muebles medievales y los de ejemplo) la conversión tarda
entre 0,8 y 1,4 s y el pack ocupa 430 KB. Las rutas del pack se acortan a menos de 80 caracteres
(algunas plataformas de Bedrock no abren rutas más largas; Geyser avisa si las ve).

**Geyser los recibe al arrancar.** Geyser 2.8 arranca cuando el servidor termina de cargar
(`ServerLoadEvent`), después de todos los plugins, y pide entonces ítems, bloques y packs. Sus
eventos se escuchan desde que este plugin se habilita, y cada uno espera a la primera compilación
(en el servidor de prueba esperó entre 4,5 y 11,5 s; el límite son 180 s). Por eso no hay orden de
carga entre los dos (`load: OMIT`): un orden fijo hace ciclos con plugins que cargan después de
Geyser, como PacketEvents. Lo que Geyser recibe se queda hasta el siguiente reinicio: un ítem
añadido con `/arkcontent reload` llega a Bedrock tras reiniciar, y la consola lo dice.

**Ítems.** Geyser 2.8 (el de 1.21.8) solo distingue los ítems personalizados por su número
`custom_model_data`; con Bedrock activo, todo ítem con modelo recibe uno (el pack Java no cambia si
`pack.custom-model-data` está apagado). Geyser 2.8 invierte el orden de registro y da a una stack
el primer ítem cuyo número es menor o igual que el suyo, así que se registran de menor a mayor:
cada stack se ve como ella misma. Con Geyser 2.9 o posterior se usa su API nueva, por `item_model`.
Las stacks antiguas sin número lo reciben cuando un jugador de Bedrock entra con ellas. Un ítem del
que no se puede dibujar icono (su modelo o sus texturas no están en el pack) se registra igual, con
su nombre y un signo de interrogación como dibujo; la lista entera queda en
`output/bedrock/items-without-picture.txt`.

**Muebles.** Geyser no dibuja `ItemDisplay`, que es lo que un mueble es para Java. Cada celda de
cada mueble, en cada uno de sus cuatro giros, es un bloque personalizado de Bedrock que Geyser
asocia a un estado de note block reservado («portador»). A los jugadores de Bedrock —y solo a
ellos— se les envían esos estados como cambios de bloque donde está el mueble: donde hay un soporte
(barrera o bloque de luz) y, para las partes del modelo que sobresalen de sus soportes (el respaldo
de una silla), solo donde el mundo tiene aire. El mundo y los jugadores de Java siguen viendo las
barreras. Los portadores se toman desde arriba del rango de note blocks hacia abajo, se guardan en
`data/bedrock_carriers.json` para que no cambien entre compilaciones, y ningún bloque personalizado
recibe nunca uno de esos estados. Se reenvían al cargar el chunk, tras tocar un mueble y al quitarlo.

**Lo que Bedrock no puede dibujar se le da en texto.** A un jugador de Bedrock no se le envía el pack
Java (Geyser le manda el `.mcpack` al conectar); las barras del HUD no se le envían (no hay espacios
negativos); el minijuego le llega como texto con colores; y los títulos de los menús le llegan con
sus palabras, sin las imágenes ni los espacios que los colocan.

`/arkcontent bedrock` dice cuántos ítems, bloques y portadores recibió Geyser, cuántos jugadores de
Bedrock hay, cuántas piezas se les están mostrando y cuántos bloques portadores se han enviado.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (3.0, Bedrock)

Con Geyser-Spigot 2.8.4 y un cliente de Bedrock 1.21.111 sin cuenta (`bedrock-protocol`; para él, y
solo en el servidor de prueba, `auth-type: offline` y `enable-proxy-connections: true`):

| Qué | Resultado |
|---|---|
| Arranque | Geyser esperó a la primera compilación y registró 690 bloques (684 portadores de 65 muebles) y 1671 ítems; ningún error |
| El pack | ofrecido al conectar: `arkcontent` versión 3.0.0, 440 KB |
| `StartGame` | 690 bloques `geyser_custom:ark_...` y 2361 entradas de ítem (1671 ítems + los ítems de los 690 bloques) |
| Muebles junto a la exhibición medieval | el cliente recibió 2321 cambios a bloques personalizados, los mismos 2321 portadores que `/arkcontent bedrock` dice haber enviado |
| Inventario | rubí, cubo de escarcha, mochila, cubo de ácido y un hueso importado sin texturas: cada uno como su propio `geyser_custom:` (el hueso, con el signo de interrogación) |
| Menú del explorador | el título llega como «Categories», sin caracteres de uso privado |
| Conexión | sin desconexiones ni avisos de Geyser |

La prueba encontró dos fallos, corregidos antes de esta versión: registrados en el orden del
contenido, el rubí (12007) se veía como la escarcha (12006), por la regla de Geyser 2.8 descrita
arriba; y ~1590 ítems importados sin texturas en el pack de prueba se habrían visto como otro ítem.
Ambos tienen test.

No verificado: **cómo se ve** —aquí no hay cliente de Bedrock que dibuje—. La orientación de las
geometrías sigue las convenciones de Blockbench y de las calaveras de Geyser (X reflejado, giros
`-x, -y, z`), pero no se ha visto en pantalla. Tampoco se ha probado aquí: romper o usar un mueble
desde Bedrock, el minijuego en Bedrock, Geyser 2.9+ (la API por `item_model` solo está compilada),
Floodgate, ni Geyser en un proxy (para ese caso, `bedrock.enabled: true` deja el `.mcpack` en
`output/bedrock/`, pero no hay Geyser en este servidor al que darle los bloques).

### Cosméticos con física (`cosmetic`) — 3.0

Un ítem con `cosmetic:` se lleva puesto con un clic derecho: se consume, y si ya había algo en ese
hueco se devuelve. `/cosmetic` dice lo que llevas y `/cosmetic off` te lo quita (vuelve al
inventario). Lo puesto se guarda en SQLite (`player_cosmetics`) y vuelve al entrar.

```yaml
# contents/medieval_props/cosmetics.yml
items:
  mochila_viaje:
    material: PAPER
    display-name: "<#c8a165>Mochila de viaje"
    resource:
      model: cosmetic/mochila_viaje
    cosmetic:
      slot: back            # de momento, solo la espalda
      offset: [0, -0.32, 0.27]
      scale: 1.0
      physics:
        stiffness: 40       # muelle: cuánto tira hacia su sitio
        damping: 5          # cuánto frena la oscilación
        sway: 1             # cuánto responde al movimiento
        max-angle: 28       # grados como mucho
        bounce: 0.45        # rebote al caer
```

La física corre en su propio hilo, `ArkContent-Physics`, cada 50 ms. `PlayerMoveEvent` solo apunta
dónde está el jugador, hacia dónde mira su cuerpo y si se agacha (unas pocas escrituras); el hilo
calcula velocidad y aceleración, integra un muelle amortiguado en pasos de 5 ms (péndulo al andar,
retraso y torsión al girar, rebote al caer de un salto, inclinación al agacharse) y envía la nueva
transformación. Con **PacketEvents** instalado, el display es un paquete —no existe en el mundo—
montado sobre el jugador y la transformación sale directamente del hilo de física; sin él, es un
`ItemDisplay` real, no persistente, que el hilo principal mueve cada tick con la última pose. En
los dos casos, una pose que no cambia lo visible (menos de 1 mm y de 0,07°) no se envía: una
mochila en reposo no cuesta paquetes.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (3.0, cosméticos)

Con dos bots, el que la lleva y otro que mira:

| Qué | Con PacketEvents 2.14.0 | Sin PacketEvents |
|---|---|---|
| Ponérsela | clic derecho; se consume; `/cosmetic` la nombra | igual |
| Lo que recibe cada cliente | el display, como pasajero del jugador | un display real que le sigue |
| Paquetes de la pose, quieto 3 s | 0 | 0 |
| Paquetes de la pose, andando y saltando | 20 por segundo (uno por paso de física) | solo cuando cambia la pose |
| Tras reiniciar | la lleva puesta otra vez | — |

Corregido durante la prueba: sin PacketEvents, la 3.0 no llegaba a habilitarse (la referencia
`PacketCosmetics::new` enlazaba la clase fuera de la protección del gancho; `HookIsolationTest` lo
comprueba ahora); los umbrales de «sin cambios» eran tan finos que una mochila quieta seguía
enviando 20 paquetes por segundo; y el display real reenviaba su retardo de interpolación en cada
tick.

No verificado: cómo se ve el balanceo en un cliente (los bots no dibujan). Los jugadores de Bedrock
no ven los cosméticos (Geyser no dibuja displays).

### Minijuegos de precisión (`minigame`) — 3.0

Una aguja recorre una barra de calibración en la barra de acción; se golpea (clic izquierdo, al aire
o al bloque) cuando pasa por la zona. En un **bloque personalizado** —una mena rara— la partida
empieza con clic derecho y el bloque solo se puede minar así; en un ítem hecho de **caña de
pescar**, empieza cuando algo pica y se recoge, y decide si la captura se queda.

```yaml
# contents/demo/minigames.yml
  star_ore:
    type: custom_block
    resource:
      texture: block/star_ore
    block:
      drop-self: false
    minigame:
      speed: 1.2            # veces por segundo que la aguja cruza la barra (0,1-10)
      zone: 22              # píxeles de la barra de 100 que cuentan como acierto
      critical: 5           # píxeles en el centro de la zona que cuentan como crítico
      sweeps: 4             # cruces antes de que acabe sola («Too slow»)
      random-zone: true     # la zona cambia de sitio en cada partida
      drops:
        - { item: demo:ruby, amount: 1 }
      critical-drops:
        - { item: demo:ruby, amount: 3 }
        - { item: DIAMOND, amount: 1, chance: 0.5 }
      on-miss: keep         # bloque: keep (reintentar) o break (se rompe sin nada)
      cooldown-seconds: 1
```

La barra se dibuja con la fuente de espacios negativos del pack (los mismos espacios que los HUD),
sin ningún shader: un marco, la zona, el crítico y la aguja en su píxel. **El servidor manda**: la
posición de la aguja es una función del tiempo en el servidor; al golpear, el juicio se hace en el
hilo `ArkContent-Minigames` con un `CompletableFuture` —con el instante del clic menos el ping del
jugador, hasta 250 ms— y el resultado vuelve al hilo principal, que rompe el bloque y suelta los
drops, o da la captura. Los primeros 200 ms de una partida no cuentan (el clic que la empieza). Las
partidas se cuentan en `minigame_stats`.

#### Qué se ha verificado en un servidor Paper 1.21.8 real (3.0, minijuegos)

| Qué | Resultado |
|---|---|
| Mena de ejemplo | ~20 dibujos de la barra por segundo en la barra de acción; partidas y fallos en `minigame_stats` |
| Mena de prueba con toda la barra crítica | «Critical hit!», el bloque se rompe y suelta sus 3 rubíes |
| Mena de prueba con 1 píxel de zona | «Missed» tres veces; el bloque sigue (`on-miss: keep`) |
| Sin golpear | «Too slow» al acabarse los cruces |
| Caña del maestro, pescando de verdad en un estanque | picó, 18 dibujos de la barra, «Hit», la captura (un nenúfar) se queda |

Las menas de prueba eran contenido solo del servidor de prueba. Un golpe al aire llega al servidor
con la dirección en la que el cliente mira en ese momento: si mira a un bloque, cuenta como golpe
al bloque, que también vale. Cómo se ve la barra en un cliente real no se ha podido ver.

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
| MythicArmor (o MythicArmors) | después de este | genera su primer pack al habilitarse |
| BetterHud (3.0.5) | después de este | genera su pack al habilitarse; hay que estar escuchando ya |
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
| Lands, Towny | antes de este | se les pregunta en cada acción protegida (3.0) |
| LuckPerms | antes de este | sus prefijos se leen de su caché al pedirlos (3.0) |
| PacketEvents | antes de este | el renderizador de cosméticos registra su listener de paquetes al habilitarse (3.0) |
| Geyser-Spigot | **ninguno** (`load: OMIT`) | arranca después de todos, en `ServerLoadEvent`, y espera a la primera compilación; un orden fijo hace ciclos con plugins que cargan tras Geyser, como PacketEvents (visto en el servidor de prueba) (3.0) |

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
        key: demo/ruby_seeds          # cualquier ítem, como namespace/id
# AuraSkills/menus/stats.yml — una estadística
      strength: {group: upper_left, order: 1, key: demo/ruby}
```

Cada ítem se registra en el registro de ítems de AuraSkills con su propio namespace e id, así que
sirve en sus menús (`/skills`, `/stats`...), recompensas y tablas de loot con `key:`. Tras
`/arkcontent reload` el registro se actualiza (altas y bajas); un menú que use un ítem nuevo lo
muestra después de `/skills reload`. Experiencia: ver «Experiencia de habilidades».

Las claves se escriben `namespace/id`, que AuraSkills entiende en todas partes (`/skills item give
<jugador> demo/ruby_block` incluido); `namespace:id` solo lo aceptan algunos de sus sitios.

**Versiones.** AuraSkills abrió su registro de ítems a otros plugins en la 2.4.0
(`ItemManager.register`). De la 2.0 a la 2.3 el registro ya existía —es el de `/skills item
register`—, solo que fuera de la API: con esas versiones los ítems se meten directamente en él, la
misma llamada que la API de la 2.4.0 hace por dentro (un `Map.put` en memoria, nada en disco). Si
ninguna de las dos vías está, los ítems no se registran y se avisa en una línea; la experiencia por
bloques y cultivos funciona con cualquier 2.x. Verificado con 2.3.12 (1608 ítems registrados,
`/skills item give` entrega el ítem con su modelo, romper un bloque de rubí suma sus 15 de
Minería) y con 2.4.0.

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

**Packs escritos para ItemsAdder: `namespace:id` a secas.** Sin ItemsAdder instalado, una línea
como las de esos packs también funciona, en `Drops` y en `Equipment`:

```yaml
  Drops:
  - dragones_epicos:escama_umbraxis 6-12
  - dragones_epicos:umbraxis_botas 1 0.05
```

MythicMobs lee el `:` como el formato antiguo `MATERIAL:data`: se queda con lo de delante
(`dragones_epicos`) como tipo de drop y pregunta a los plugins por ese tipo, con la línea entera.
Si es el namespace de ítems de este plugin, se responde con el ítem que nombra la línea; antes solo
se respondía a `arkcontent` y MythicMobs avisaba `Drop type not found.` en cada línea. Los ítems
importados conservan el namespace y el id de ItemsAdder. Con ItemsAdder instalado esas líneas son
suyas y no se tocan.

Verificado en Paper 1.21.8 con MythicMobs 5.13.0 y el pack `DragonesEpicos` de un servidor real
(unas 300 líneas `dragones_epicos:...`): 0 avisos `Drop type not found` al arrancar (antes, uno por
línea). `/mm mobs spawn DE_Umbraxis` y matarlo dejó 6 `dragones_epicos:escama_umbraxis` y 3
`dragones_epicos:hueso_umbraxis` en el suelo: PAPER con su `item_model`, nombre y la etiqueta
`arkcronistcontent:item`. MythicMobs pide estos tipos un tick después de leer los mobs, cuando este
plugin ya escucha, así que no hace falta recargarlo para ellos.

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
MythicArmor genera el suyo (`MythicArmorGeneratePackEvent`), esa carpeta se fusiona en el nuestro
—sus `assets/` y sus overlays, que llevan los shaders con los que dibuja en 3D pecheras, pantalones
y botas— y se recompila. Si en un arranque MythicArmor no vuelve a generar su pack (no hay evento),
una vez arrancado el servidor se toma el último que generó, a través de su API.

Su pack **gana a las copias de sus archivos** que traigan otros packs fusionados: una exportación de
ItemsAdder en `packs/` hecha con un MythicArmor anterior trae shaders `entity.vsh`/`entity.fsh`
antiguos en las mismas rutas, y con ellos las piezas del cuerpo no se dibujan (el casco sí, porque
es un modelo normal). Si una ruta está en los dos, el pack de MythicArmor manda; frente a los
archivos propios de este plugin, ganan los nuestros y se avisa en consola.

Desde 3.0.7 esas copias perdidas no salen archivo por archivo: una exportación que se tragó el pack
entero de MythicArmor daba un `WARN ... is already supplied by MythicArmor pack` por cada textura
(más de 900 en un servidor real). Ahora es una sola línea de información por pack, con el total y
tres ejemplos, y no cuenta como problema:

```
[pack] packs/generated_5 pack: <N> file(s) are copies of ones MythicArmor pack builds as it runs,
       with other content - its versions are kept (assets/mythicarmor/textures/...)
```

Vale igual para cualquier pack vivo (BetterHud, 3.0.5). Cuando chocan dos packs copiados en disco, o
un pack con archivos de este plugin, sí es un problema, pero también una línea por pareja de packs
con el número de archivos.

Si MythicArmor también envía su propio pack a los jugadores, **desactívalo** en su configuración:
el nuestro ya lo lleva, y con dos packs el que queda encima puede tapar los shaders del otro.
En la consola, `Merging the MythicArmor resource pack from <carpeta> into ours.` confirma que se
ha fusionado.

Algunas versiones del plugin se llaman `MythicArmors` (su carpeta es `plugins/MythicArmors/`). Con
un nombre que no fuera el que declara `paper-plugin.yml`, Paper no deja ver sus clases y el gancho no
arranca; desde 3.0.2 se declaran los dos.

Las piezas que da MythicArmor (`/mythicarmor give <jugador> <set> <pieza>`) ya llevan su asset. Las
del mismo set importadas de ItemsAdder no lo llevaban, y por eso se veían de netherita; desde 3.0.2
se les pone solo, por el nombre (sección 4, *Piezas sin `equipment:`*).

### BetterHud (3.0.5)

BetterHud dibuja su HUD (y los packs hechos para él, como PrimeHUD) con un resource pack propio:
sus fuentes, sus imágenes y un shader de texto (`rendertype_text`) que coloca cada pieza en
cualquier punto de la pantalla, sobre una **bossbar**. No envía ese pack a nadie salvo que lo
aloje él mismo (`enable-self-host`). El gancho lo mete en el nuestro: cada vez que BetterHud lo
genera (al arrancar y con `/hud reload`) avisa con `PluginReloadedEvent` y entrega los archivos;
se escriben en `data/betterhud/<hora>/`, se fusionan como pack vivo (gana a las copias de las
mismas rutas en otros packs fusionados, como el `rendertype_text` de una exportación de
ItemsAdder; los archivos propios de este plugin siguen ganando) y el pack se recompila. No hace
falta `pack.merge` ni copiar su carpeta `build`, y no se lee nunca una carpeta que BetterHud esté
reescribiendo. En la consola: `BetterHud built its resource pack: N file(s), merged into ours.`

- **Versión.** BetterHud 2.x está compilado para Java 25: en un servidor 1.21.8 con Java 21 no
  carga (`UnsupportedClassVersionError`). La 1.14.1 sí, y su shader de texto ya lleva la rama de
  1.21.6+ (`#if SHADER_VERSION >= 3`, bloques de uniforms). Con Java 25 la 2.0.0 carga, pero su
  shader de texto **no compila en clientes 1.21.6 a 1.21.11**: ver más abajo (3.0.8).
- **Packs de terceros (PrimeHUD v2).** Copia sus `huds/`, `layouts/`, `images/`, `texts/`,
  `heads/`, `backgrounds/`, `assets/`, `config.yml` y `shader.yml`, pero **no** su `shaders/` ni
  su `build/`: son de 1.21.4 o anteriores (uniforms sueltos, archivos `.json` de programa) y en
  1.21.8 no compilan o no se leen. Su carpeta `Resourcepack/` tampoco hace falta: BetterHud ya
  mete en su pack los sprites transparentes que ocultan el HUD vanilla.
- **El HUD de este plugin.** Desde 3.1 BetterHud dibuja las barras de este plugin él mismo: ver
  *Las barras de este plugin, dibujadas por BetterHud (3.1)* más abajo. A los jugadores de Bedrock
  BetterHud no les dibuja nada (`disable-to-bedrock-player`).
- **3.0.6.** El texto para Bedrock de 3.0.4 y 3.0.5 (`bedrock:`) se ha quitado: el HUD de este
  plugin vuelve a ser el de 3.0.3. Una clave `bedrock:` que quede en un `huds.yml` no hace nada.
- **TAB y el marcador.** BetterHud no toca el marcador lateral ni la lista de TAB (cabecera, pie,
  nombres). Sí usa una bossbar: las de TAB siguen saliendo, pero BetterHud oculta la textura de las
  barras del color de `shader.yml` (`bar-color`), así que no uses ese color en las de TAB.

`pack.shaders` (3.0.5) lee cada shader como lo haría el preprocesador del cliente: `#define`,
`#if`, `#ifdef`, `#ifndef`, `#elif`, `#else` y `#endif` sobre números y macros que el propio shader
define. Así, el `#else` con las declaraciones de 1.21.4 de un shader escrito para varias versiones
(el de BetterHud) no se cuenta como roto, y `drop-broken` no lo quita. Una condición sobre algo
que el shader no define (una macro que pone el cliente) se lee entera, como antes.

#### Las barras de este plugin, dibujadas por BetterHud (3.1)

Con BetterHud y PlaceholderAPI instalados, las barras que irían en la barra de acción (las de
`huds:` con `anchor`, o con `action-bar: true`: la sed, el maná y el escudo del ejemplo) las dibuja
**BetterHud**, como HUDs suyos, junto al resto de su HUD (el de PrimeHUD, por ejemplo), y dejan de
salir en la barra de acción. Sin configurar nada:

- En cada recarga se escriben en la carpeta de BetterHud, todos con nombre `arkcontent`:
  `assets/arkcontent/<pack>_<id>_bar.png` y `_empty.png` (la barra llena y la vacía, hechas con
  los mismos iconos y la misma separación que en la barra de acción), `images/arkcontent.yml`,
  `layouts/arkcontent.yml` y `huds/arkcontent.yml`. Cada HUD lleva `default: true` (lo ven todos los
  jugadores sin tocar el `config.yml` de BetterHud). No los edites: se reescriben; cambia el
  `huds.yml` de este plugin.
- La barra llena es una imagen `listener` de BetterHud con `class: placeholder`, que lee
  `(number)papi:arkcontent_hud_<id>_value` y `_max` y enseña la parte que toca, por medias
  piezas si la barra tiene icono de mitad. La de la sed se vacía por la izquierda, como la comida.
- Si los archivos cambiaron y BetterHud ya estaba en marcha, se ejecuta `hud reload`; BetterHud
  rehace su pack y el gancho lo fusiona en el nuestro. Si no cambiaron, no se recarga nada.
  En la consola: `BetterHud draws 3 HUD bar(s) of this plugin's: mana, shield, thirst
  (BetterHud/huds/arkcontent.yml).`
- **Dónde.** En el mismo sitio que en la barra de acción: medido desde el centro del borde inferior
  (`gui: 50, 100`), contra la hotbar. BetterHud sube todos los HUD 54 píxeles (la altura de la
  hotbar) mientras su `config.yml` tenga `disable-legacy-offset: false`, como lo trae PrimeHUD: el
  plugin lo lee y los baja otro tanto. Si cambias esa opción, recarga este plugin
  (`/arkcontent reload`) para que recoloque las barras.
- BetterHud centra cada imagen en su `x` (un `align:` en ella no cambia nada en 2.0.0): el plugin
  suma la mitad del ancho de la barra para que su borde izquierdo quede donde estaba.
- Para moverlas, en el HUD: `betterhud: { gui: [0, 0], pixel: [60, 120] }`, en los términos de
  BetterHud: porcentaje de pantalla, y luego píxeles a la derecha y hacia abajo hasta el centro del
  borde de arriba de la barra. Aquí no se compensa nada. `betterhud: false` deja ese HUD en la barra de acción, y `huds.betterhud: false` en el
  `config.yml` de este plugin los deja todos ahí. Sin PlaceholderAPI no se pasan a BetterHud.
- Un HUD cuyos iconos son texturas de otro pack (no de su carpeta) se queda en la barra de acción,
  con un aviso.

**3.1.1: un solo HUD.** Si BetterHud ya dibuja barras de estado (las de PrimeHUD: imágenes
`health_bar` y `hunger_bar` de 12 píxeles de alto en sus `layouts/`), las barras de este plugin no
salen aparte junto a la hotbar: se dibujan **como más barras de esas**, en el mismo panel, en filas
debajo de las suyas.

- **Aspecto.** Igual que las de PrimeHUD: marco del mismo ancho, borde claro con las esquinas
  recortadas, interior oscuro translúcido, el icono pequeño a la izquierda, y encima el relleno de su
  color con el hueco del icono. El dibujo es del plugin; de PrimeHUD solo se lee dónde están sus
  barras y cuánto miden.
- **Dónde.** La que va del lado de la comida (`anchor` en `food` o `above-food`: la sed) va en la
  columna de la comida, y las demás (maná, escudo) en la de la vida. Empiezan en la fila siguiente
  a la de armadura y aire de PrimeHUD, con su mismo paso (15 píxeles), en el mismo sitio que su HUD
  de vida (`gui` y `pixel` del HUD que la muestra, el de `default-hud`, no el `test_hud` de ejemplo
  de BetterHud), así que su `disable-legacy-offset` las mueve a la vez.
- **Por HUD**, en `betterhud:`:
  - `icon: hud/thirst_small` es el icono pequeño, de unos 7×6. Sin él se usa el icono lleno reducido.
  - `color: "#1c9ec0"` es el color de la barra. Sin él se toma el del icono.
  - `warn-below: 30` hace que parpadee al 30 % o menos, como la vida baja de PrimeHUD. Usa el
    placeholder nuevo `%arkcontent_hud_<id>_percent%` (de 0 a 100).
  - `hide-empty: true` hace que solo salga mientras valga más de 0, como la armadura de PrimeHUD.
    El ejemplo lo usa en el escudo.
  - Un HUD con `gui:`/`pixel:` propios sigue saliendo como fila de iconos donde se diga.
- **Los ejemplos** traen los tres iconos pequeños (`textures/hud/*_small.png`, que se añaden solos
  al actualizar) y el `huds.yml` con esas claves. Si no habías tocado el `huds.yml`, se actualiza
  solo. Si lo habías editado, añade a mano `betterhud: { icon: ..., color: ... }`; sin eso salen igual
  como barras de PrimeHUD, con el icono reducido y el color del icono.
- Sin barras de estado en BetterHud (su configuración por defecto, u otro pack), todo sigue como en
  3.1: filas de iconos donde las ponía la barra de acción.

| Cliente 1.21.8 real, BetterHud 2.0.0 + PrimeHUD v2 (3.1) | Resultado |
|---|---|
| Primera carga con `value: "papi:..."` | BetterHud: `this type is not a number`; con `(number)papi:...` carga |
| `hud reload` tras escribir | `Reload successful!`, sin errores; el pack de BetterHud se fusiona en el nuestro |
| Captura del cliente | escudo, maná (estrellas) y sed (gotas) dibujados por BetterHud con sus valores: 4 de 20 de absorción, 55 de 100, 13 de 20 vaciándose por la izquierda |
| Posición, con `disable-legacy-offset: false` | sin compensar salían 54 px más arriba y 40 a la izquierda; compensadas, el maná empieza en el borde izquierdo de la hotbar (centro − 91) y la sed acaba en el derecho (centro + 91), a 59 px del borde inferior, y el escudo a 69 |
| Arranque con los archivos ya escritos e iguales | no se recarga BetterHud ni se reescribe nada |
| 3.1.1, captura del cliente con PrimeHUD | maná (morado, 55 %) y sed (turquesa, 25 %) en la fila de debajo de la de armadura y aire, escudo (dorado, solo con absorción) en la siguiente, con el mismo marco, borde, interior y corte que las barras de PrimeHUD; la sed parpadea en blanco (≤ 30 %) |
| 3.1.1, carpeta con el `default-hud.yml` de ejemplo de BetterHud (`test_hud`, no mostrado) | se toma el HUD que sí se muestra (`default-hud` de su `config.yml`); antes las barras quedaban fuera de la pantalla |

#### BetterHud 2.0.0, ItemsAdder y un pack que el cliente rechaza (3.0.8)

Un cliente que descarga el pack y no lo aplica (`... downloaded the resource pack but could not apply
it`, estado `FAILED_RELOAD`) casi siempre ha encontrado un shader que no compila. Entonces el cliente
**quita todos los packs del servidor**, no solo el roto: ni fuentes, ni modelos, ni HUD (texto en
cuadros, ítems morados y negros). Se reprodujo con un cliente 1.21.8 real, sin pantalla (Xvfb y
Mesa), con la configuración de un servidor real (PrimeHUD v2 sobre BetterHud 2.0.0, una exportación
de ItemsAdder y MythicArmor):

- **BetterHud 2.0.0** numera sus overlays `SHADER_VERSION` 0, 1, 2 y 3 (`betterhud_1_21_2`, `_1_21_4`,
  `_1_21_6`, `_26_1`), pero su plantilla (`plugins/BetterHud/shaders/text.vsh` y `text.fsh`, y también
  la vieja de PrimeHUD) solo usa la sintaxis de 1.21.6 con `#if SHADER_VERSION >= 3`. El overlay que
  carga un cliente 1.21.6–1.21.11 lleva el 2, toma la rama de 1.21.4 y el cliente dice
  `` `FogColor' redeclared `` y `no function with name 'linear_fog'`. Arreglo, uno de los dos:
  - BetterHud **1.14.1** (carga en Java 21 y en 25), borrando la carpeta `plugins/BetterHud/shaders/`
    para que escriba la suya; o
  - BetterHud 2.0.0, borrando `plugins/BetterHud/shaders/` para que escriba sus plantillas, y
    cambiando en `text.vsh` y `text.fsh` las cuatro líneas `#if SHADER_VERSION >= 3` por
    `#if SHADER_VERSION >= 2`; después `/hud reload`.
- **El shader de ItemsAdder tapaba el de BetterHud.** El cliente pone los overlays sobre la raíz en el
  orden de `pack.mcmeta`, el último encima, y los de una exportación copiada van después de los de un
  pack vivo: el `rendertype_text.vsh` de `ia_overlay_1_21_6_to_11` era el que dibujaba, BetterHud no
  movía nada y PrimeHUD quedaba fuera de la pantalla aunque el pack se aplicara. Desde 3.0.8, lo que
  da un pack vivo (en su raíz o en sus overlays) no lo vuelve a traer el overlay de un pack copiado; un
  shader va entero, con su otra mitad. Las listas JSON compartidas (fuentes, atlas) se siguen uniendo.
  Sale en la nota de las copias: `packs/generated_4 pack: 4 file(s) are copies of ones BetterHud pack
  builds as it runs (at the same path, or in an overlay of its own) ...`.
- **`pack.shaders` lo ve ahora.** BetterHud escribe las declaraciones en una sola línea y el revisor
  solo leía la primera de cada línea (aquí `uniform sampler2D Sampler0;`, correcta): decía `0
  finding(s)`. Ahora lee la línea entera, dice que un uniform suelto que un bloque importado ya declara
  no compila (`'FogColor' redeclared`), y que una llamada a `linear_fog`, `linear_fog_fade`,
  `fog_distance` o `minecraft_sample_lightmap` (funciones de los includes de vanilla hasta 1.21.5)
  tampoco. Un uniform suelto que el shader declara y nunca lee (el `ChunkOffset` de BetterHud 1.14.1)
  ya no se cuenta: sin rellenar no cambia nada, y `drop-broken` no debe quitar un shader que funciona.
- **El aviso nombra al sospechoso.** Si la build tiene shaders rotos, el `FAILED_RELOAD` los dice:
  `This build keeps 1 shader(s) a 1.21.8 client cannot use - the likely cause:
  betterhud_1_21_6/assets/minecraft/shaders/core/rendertype_text.fsh (BetterHud pack). Fix them where
  they come from, or set pack.shaders: drop-broken to leave them out.` Con `drop-broken` el pack se
  aplica, aunque el HUD de ese shader no se dibuje bien: arreglarlo en su origen es lo bueno.

| Cliente 1.21.8 real (Mesa llvmpipe), Paper 1.21.8 en Java 25 | Resultado |
|---|---|
| Pack de BetterHud 2.0.0 con la plantilla de PrimeHUD, solo | `Caught error loading resourcepacks`: `FogColor` redeclarado, sin `linear_fog` |
| Igual con la plantilla propia de BetterHud 2.0.0 | el mismo error |
| BetterHud 1.14.1, 3.0.7 (overlay de ItemsAdder encima) | el pack se aplica, PrimeHUD no se ve: el `rendertype_text.vsh` cargado es el de `ia_overlay_1_21_6_to_11` |
| BetterHud 1.14.1, 3.0.8 | se aplica y PrimeHUD se ve en una captura del cliente: perfil, barras, coordenadas, Vote Party, Job, hotbar |
| BetterHud 2.0.0 con la plantilla corregida (`>= 2`), 3.0.8 | se aplica y PrimeHUD se ve igual |
| Revisor sobre el pack de BetterHud 2.0.0 | marca `rendertype_text.fsh`: `ColorModulator` suelto, `FogColor` redeclarado, `linear_fog()` |

#### Qué se ha verificado en un servidor Paper 1.21.8 real (3.0.5, BetterHud + PrimeHUD v2)

| Qué | Resultado |
|---|---|
| BetterHud 2.0.0 en Java 21 | no carga: compilado para Java 25 |
| BetterHud 1.14.1 con la configuración de PrimeHUD v2 (sin `shaders/` ni `build/`) | arranca; genera su pack (1312 archivos) y el gancho lo fusiona: 25 402 archivos, 46 MB |
| Conflictos | el `rendertype_text.vsh` de la exportación de ItemsAdder cede ante el de BetterHud; el shader de 1.21.6+ de BetterHud ya no se marca como roto |
| Bot de Java | recibe un solo pack (el de este plugin); la bossbar de BetterHud se actualiza unas 20 veces por segundo con 14 fuentes suyas; ninguna barra de acción de este plugin, con `action-bar: false` |

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

**Tiendas y botones escritos para ItemsAdder.** Sin ItemsAdder instalado, `item:` con
`itemsAdder: <namespace:id>` (también `itemsadder`) se lee como `arkcontent:`: los ítems importados
conservan el namespace y el id de ItemsAdder, así que una configuración como esta funciona sin tocarla:

```yaml
# ShopGUIPlus/config.yml
buttons:
  goBack:
    item:
      itemsAdder: ginko_fantasy_shop:transparent
      name: '&c&lBack'
    slot: 49
```

Antes nuestro proveedor solo miraba `arkcontent:`; ShopGUI+ caía entonces a `material:`, no lo
encontraba (`Invalid or no material name specified`) y, como un botón que no carga es un error fatal
para él, se deshabilitaba entero. Con ItemsAdder instalado la clave sigue siendo suya y no se toca.

Verificado sin el ShopGUI+ real (es de pago): en Paper 1.21.8, un plugin de prueba llamado
`ShopGUIPlus` con las mismas clases de API (`ItemProvider`, `ShopGuiPlusApi.registerItemProvider`,
`ShopGUIPlusPostEnableEvent`) lanza el evento y luego carga cada `item:` del `config.yml` real de un
servidor, primero con los proveedores registrados y después con `material:`. Resultado: 46 de 46
cargados, 0 fallos; los 30 con `itemsAdder:` (`transparent`, `btn_1`, `btn_64`, `btn_m1`, `btn_p1`,
`btn_m10`, `btn_p10`, `cat_blocks`… `cat_potions`) salen como PAPER con su `item_model`
(`ginko_fantasy_shop:btn_1`…), `isValidItem` y `compare` responden `true`. Lo que esa prueba no
puede confirmar es el orden interno del ShopGUI+ real (que consulte los proveedores antes de dar el
material por inválido); su propio log —«Enabled item provider for ArkcronistContent» antes de los
errores de botones— indica que el proveedor ya estaba registrado cuando los cargaba.

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

### Lands y Towny (3.0)

Las mismas acciones de la tabla de arriba —cultivos, muebles, almacenes, líquidos que fluyen o se
vierten, explosiones— se consultan también con **Lands** y **Towny**, y se detienen si cualquiera
dice que no:

- **Lands**: el permiso de rol del jugador en el terreno (`BLOCK_BREAK`, `BLOCK_PLACE`,
  `INTERACT_CONTAINER`...), las flags naturales del terreno para explosiones (`TNT_GRIEFING`,
  `ENTITY_GRIEFING`), y que un líquido o un mob solo entre en un terreno desde el mismo terreno.
- **Towny**: la caché de permisos de Towny para el jugador (construir, destruir, interruptor), los
  permisos de explosión de la parcela, y que un líquido solo entre en una ciudad desde la misma ciudad.

**Balística.** Un disparo de un arma de este plugin a una entidad se consulta antes de hacer daño:
con Lands (`canPvP`, y `ATTACK_MONSTER` / `ATTACK_ANIMAL` para mobs) y con Towny (su comprobación
de combate, la misma que usa para un ataque cuerpo a cuerpo). Un objetivo protegido no recibe daño
y el disparo sigue su camino. Como con WorldGuard, si la comprobación lanza un error cuenta como no.

Compilado contra LandsAPI 7.25.4 y Towny 0.101.2.7. Towny se cargó en el servidor de prueba y el
gancho arrancó, pero no se crearon ciudades: las reglas no se han ejercitado en vivo. Lands no se
ha ejecutado (es de pago).

### LuckPerms (3.0)

Los prefijos, sufijos y meta de LuckPerms, con los emojis del plugin dentro, para TAB, chat o menús:

```
%arkcontent_luckperms_prefix%          &c:crown: Admin  ->  la imagen de la corona, luego «Admin» en rojo
%arkcontent_luckperms_suffix%
%arkcontent_luckperms_meta_<clave>%
```

Un `:emoji:` del prefijo se cambia por su carácter, dibujado en blanco (un color lo teñiría), y
después se restaura el color y el formato que tenía el texto (`&`/`§`, `&#rrggbb` y `§x`). Se lee
de la caché de LuckPerms, sin consultas. Verificado en el servidor de prueba con LuckPerms 5.5.71:
el prefijo `&#d4af37:ruby: &lVIP &r` da `&#d4af37&f<U+E026>&#d4af37 &lVIP &r`, donde U+E026 es el
carácter del emoji `:ruby:`.

### PacketEvents y Geyser (3.0)

Con **PacketEvents** los cosméticos son paquetes enviados desde su propio hilo; sin él, displays
reales. Con **Geyser-Spigot** el contenido llega a los jugadores de Bedrock. Los dos están en la
sección 4.

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

**Menús escritos para ItemsAdder.** Sin ItemsAdder instalado, `material: itemsadder-<namespace:id>`
también se resuelve con este plugin: el mismo gancho toma el prefijo `itemsadder-`, que el gancho
de ItemsAdder propio de DeluxeMenus solo ocupa cuando ItemsAdder está. Así un menú como este sirve
tal cual con los ítems importados:

```yaml
  pico_segador_vacio:
    material: itemsadder-arkpicos:pico_segador_vacio
    slot: 10
```

Igual que con `arkcontent-`, el primer pase de DeluxeMenus avisa `Material for item: ... is not
valid!` (aún no conoce el gancho) y la recarga automática de un tick después los carga. Verificado con
los menús `ark_menu_picos` y `ark_menu_picos_raros` de un servidor real: tras la recarga no queda
ningún aviso, y `/picos` muestra los 8 picos con su modelo (`arkpicos:pico_segador_vacio`…), leído
por un bot desde la ventana que recibe el cliente.

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

### Muebles que ItemsAdder dejó colocados (3.1)

Los muebles que se colocaron mientras ItemsAdder estaba instalado siguen en el mundo cuando se quita:
su modelo es una entidad (un item display, o un soporte de armadura o un marco con el ítem) que dibuja
el pack importado, a menudo sobre barreras y con una caja de interacción, y nadie los gestionaba:
romper la barrera dejaba el modelo flotando (también el caldero de una versión anterior). Ahora se
reconocen por lo que su ítem aún lleva (`custom_data: {itemsadder: {namespace, id}}`) o por una
clave de ItemsAdder en la entidad, y se quitan enteros:

- Romper una de sus barreras (o golpearla en supervivencia) quita el modelo, su caja de interacción
  y las demás barreras de la pieza (hasta 12 juntas, a 3 bloques como mucho: un muro de barreras no
  es un mueble).
- Golpear la caja de interacción o el modelo hace lo mismo.
- En supervivencia suelta el ítem de este plugin con ese id, si existe (los importados con
  `/arkcontent import` conservan el id de ItemsAdder); en creativo no suelta nada.
- `/arkcontent legacy [radio]` cuenta los que quedan cerca, por id, y `/arkcontent legacy remove
  [radio]` los quita (radio por defecto 32, solo en chunks cargados).
- **3.2: `/arkcontent legacy look`** quita el modelo al que **miras** (hasta 8 bloques, también si
  está dentro de una barrera): un display, un soporte de armadura, un marco o una caja de interacción.
  Sirve para los que ya no llevan nada que diga de quién son, como los que quedan flotando si se
  borró antes la estructura de ItemsAdder (su ítem ya no lleva `itemsadder`). Si es una pieza de
  ItemsAdder reconocible, se quita entera, con sus barreras. Un mueble de este plugin que sigue en
  pie no se quita así (se dice cuál es): para eso, rómpelo.

Los muebles de este plugin no se tocan: llevan su propia marca. Además, al colocar un mueble se
retira cualquier display suyo que hubiera quedado huérfano en esos bloques.

### Font images: menús escritos para ItemsAdder (`:offset_N:`, `%img_nombre%`)

Muchos menús de otros plugins —AuraSkills, UpgradeableSpawners, DeluxeMenus, tiendas— están escritos
con la sintaxis de ItemsAdder para dibujar su fondo y sus iconos con imágenes de fuente:

```yaml
# AuraSkills/menus/skills.yml
title: '<white>:offset_-20: %img_skills_menu_book%'
bar_completed: '<#ffd556>%img_offset_-2%%img_xp_bar_white_lore% '
```

Sin ItemsAdder, eso salía como texto. Ahora se dibuja igual:

| Escrito | Qué es | Dónde funciona |
|---|---|---|
| `:offset_-20:` / `%img_offset_-20%` | 20 píxeles atrás (positivo: adelante), con los mismos caracteres de espacio que ItemsAdder | títulos de menú / PlaceholderAPI |
| `:skills_menu_book:` / `:namespace:nombre:` | la imagen de ese nombre | títulos de menú |
| `%img_skills_menu_book%` | la imagen de ese nombre | PlaceholderAPI (lore, mensajes, hologramas…) y títulos de menú |

- **Títulos.** Se reescriben al abrirse el menú (`InventoryOpenEvent#titleOverride` de Paper, sin NMS
  ni ProtocolLib), lo abra el plugin que lo abra. Una imagen toma el color del texto que la rodea,
  como en ItemsAdder; si el título no tiene color propio se dibuja en blanco, con sus colores, en vez
  de teñida del gris oscuro por defecto. Negrita y demás decoraciones se quitan de los caracteres
  insertados, porque la negrita ensancha cada uno un píxel y movería los desplazamientos.
- **PlaceholderAPI.** `%img_<nombre>%` devuelve el carácter tal cual, sin color: el color escrito
  antes lo tiñe, así que `<#ffd556>%img_xp_bar_white_lore%` pinta la barra blanca de amarillo, como
  hacía ItemsAdder. Solo se registra si ItemsAdder no está instalado (el identificador es suyo).
- **De dónde salen los nombres.**
  1. `font_images:` del contenido, con el mismo formato que ItemsAdder (`path`, `y_position`,
     `scale_ratio`, `symbol`; o `texture`, `ascent`, `height`). `/arkcontent import` lo conserva de
     sus configs —antes lo descartaba—, también de un archivo que solo tiene `font_images`. Cada una se
     empareja con el carácter con el que el pack generado de ItemsAdder ya dibuja esa imagen, a esa
     altura y con ese `symbol`. Si ningún pack fusionado la dibuja, nuestro pack le da un carácter
     estable.
  2. Para los packs fusionados sin sus configs, el nombre del archivo de la imagen
     (`spectra_aurelium_skills:skills_menu_book.png` → `skills_menu_book`), que es como ItemsAdder
     suele nombrarlas. Un nombre que dibujan varios caracteres distintos se deja fuera y se lista en
     consola.

  3. Las configs de la propia carpeta de ItemsAdder, si sigue en el servidor
     (`plugins/ItemsAdder/contents/<pack>/configs/`, o `data/items_packs/` de ItemsAdder 3): sus
     `font_images:` se leen en cada rebuild, sin importarlas, y se usan las que dibuja algún pack
     fusionado. Las del contenido propio ganan si se repite el id. En consola:
     `N font image name(s) read from the configs in plugins/ItemsAdder`.

  Hay nombres que no coinciden con el archivo (`skill_book_sources` es `book_all_squares.png`,
  `xp_bar_white_lore` es `xp_bar_white`): esos necesitan las configs, de la carpeta de ItemsAdder
  (punto 3) o importadas. Para no depender de esa carpeta, copia el pack de ItemsAdder
  (`contents/<pack>/configs/` y `resourcepack/`) a `import/` y ejecuta `/arkcontent import`.
- `config.yml` → `font-images.titles` y `font-images.placeholders` las apagan. `/arkcontent info`
  dice cuántas hay.

#### Qué se ha verificado (con los menús de AuraSkills de un servidor real)

Paper 1.21.8, AuraSkills 2.3.12 con su propia carpeta (`menus/`, `messages/`), y sus packs de
ItemsAdder `spectra_aurelium_skills` y `ginko_fantasy_shop` importados junto al pack generado ya
fusionado. Lo que recibe el cliente, leído por un bot:

| Menú | Título enviado |
|---|---|
| `/skills` (`<white>:offset_-20: %img_skills_menu_book%`) | `U+F809 U+F804` (−20 px) y `U+EB10`, blanco |
| `/stats` (`:offset_-18: %img_stats_menu%`) | −18 px y `U+EB13` |
| DeluxeMenus `:offset_-44::scenes_spawner_small:` (como el título del spawner) | −44 px y `U+A412`, en blanco al no tener color el título |

El lore de cada habilidad lleva sus 10 segmentos de barra `U+EB29` (`%img_xp_bar_white_lore%`), sin
texto literal; `/papi parse` responde a `%img_...%` y deja como está un nombre que no existe. Las 24
font images de esos dos packs se emparejaron todas con los caracteres del pack generado. Antes de
importar las configs ya había 223 nombres sacados de los archivos.

**Sin importar las configs (leídas de `plugins/ItemsAdder`).** Mismo servidor, quitando del
contenido importado la sección `font_images:` de `spectra_aurelium_skills` y poniendo su config tal
cual en `plugins/ItemsAdder/contents/spectra_aurelium_skills/configs/`: el rebuild leyó 16 nombres de
ahí; `/papi parse --null %img_xp_bar_white_lore%` y `%img_skill_book_sources%` devuelven su carácter
(antes salían literales: no coinciden con el nombre del archivo), y el bot vio la lore de `/skills` y
la del menú Levels de una habilidad con sus 10 segmentos de barra, sin ningún `%img_...%` literal.

**Arreglado en la importación por el camino.** Importar las configs de un pack cuyo pack generado se
había importado antes dejaba el archivo de ítems de ese pack generado, que volvía a definir los
mismos ítems por delante de los de la config. Ahora se borra cuando la config los da todos. La
carpeta `resourcepack/` de un pack de contenido con su propio `pack.mcmeta` ya no se fusiona entera
como si fuera un pack generado. Y un ítem `generate: true` cuyo modelo ya trae el pack generado usa
ese modelo, en vez de generar otro igual en la misma ruta. En la prueba los avisos del rebuild
pasaron de 95 a 1, el informativo de los espacios.

## 7. Explorador de contenido

`/arkcontent menu` (o `/arkcontent` a secas) abre un cofre de 6 filas con todo lo cargado,
**organizado como `contents/`**: el primer nivel son los paquetes —una carpeta por pack de
contenido, `dragones_epicos/`, `arkespadas/`, `demo/`…— y cada uno abre sus subcarpetas y después sus
ítems, 45 por página, ordenados por id. Los textos del juego están en inglés, como el resto de
mensajes del plugin.

```
filas 1-5   subcarpetas (su primer ítem como icono, en dorado, con cuántos ítems y carpetas tiene)
            y después los ítems: el ItemStack real de ItemFactory, con su item_model, nombre y lore
fila 6      [◄ Previous] [Back] [All packs] [Showing: ...] [✖ Close] [<carpeta> - page n of m] [ ] [ ] [Next ►]
```

### El libro (1.9.2)

El menú se dibuja sobre un **libro de anillas**, al estilo del libro de categorías de ItemsAdder:
tapas de cuero con un filete dorado, una página de pergamino con la espiral roja a la izquierda, una
cinta oscura arriba con el título, y debajo el inventario del jugador tallado en el cuero.

- **Una sola imagen, más grande que el cofre.** `arkcontent:content_browser` mide 196 × 246 px: 10 px
  más ancha que el cofre por cada lado y 22 px más alta por arriba, y llega hasta abajo, de modo que
  tapa también las casillas del jugador. Es **opaca en todo el rectángulo del cofre** (el gris de
  vanilla no asoma por ningún píxel) y **transparente fuera**, en las esquinas redondeadas, donde se
  ve el mundo. Se declara con `ascent` 35 (13 + 22) y `height` 246, y el título lleva
  `espacio(-18)` + el carácter (avanza 197) + `espacio(-179)` para que el texto empiece donde siempre.
  Un test comprueba píxel a píxel que la imagen es opaca sobre los 176 × 222 del cofre y
  transparente en sus cuatro esquinas.
- **Las 45 entradas, dentro de la página.** Los márgenes, la espiral y el cuero quedan fuera de las
  casillas, así que las 45 de arriba muestran contenido sin pisar el marco. Para un fondo propio cuyo
  marco sí cubra casillas, `content-slots` dice cuáles usar (`"10-16, 19-25, 28-34"` deja libres los
  bordes); la página tiene entonces tantas entradas como casillas se den.
- **El título**, «Categories» en la raíz (o «Shop») y el nombre de la carpeta abierta dentro
  («Dragones Epicos»), va en `#E6CB98` y **centrado** sobre la cinta: su ancho se mide con las
  anchuras de la fuente por defecto y se coloca con espacios del rango U+F801 (negativos) y U+F821
  (positivos). Como un título con imagen y colores no se puede cambiar en un inventario abierto
  (Paper solo admite texto plano), entrar en otra carpeta abre un inventario nuevo con su título;
  pasar de página no.
- **Botones con icono propio.** Anterior y Siguiente en los extremos de la fila de abajo, el cierre
  —un sello de lacre con una cruz— en la casilla central (49), y Atrás, Todo, Filtro y Página entre
  ellos. Son ítems `paper` con `item_model` `arkcontent:menu/<icono>`, cuyos modelos y texturas de
  16 × 16 mete el plugin en el pack. Sin libro (`textured: false`) vuelven las flechas, la brújula y la
  barrera de vanilla, y el cristal gris de relleno.
- **Las páginas se preparan en `ArkContent-Worker`.** Al pulsar, el hilo principal solo copia dónde
  está el menú (carpeta, filtro, página) y se lo pasa al worker, que calcula el árbol de carpetas
  —una vez por recompilación, y luego lo reutiliza—, lo que contiene la carpeta del tipo mostrado y
  las ItemStacks de la página. El tick siguiente, el hilo principal las pone en el inventario: eso
  sí tiene que ser en el hilo principal, porque Paper no deja tocar inventarios desde otro. Mientras
  una página está en camino se ignoran más pasos de página (dos clics seguidos no se saltan una), y
  si llegan dos respuestas gana la más nueva. El precio de la tienda se pide a la economía en el
  hilo principal, nunca en el worker.

```yaml
menus:
  folders: true                        # false: una sola lista, como antes de 1.9
  textured: true
  background: "arkcontent:content_browser"
  background-width: 176                # solo para un fondo propio: su ancho dibujado
  background-x: 0                      # solo para un fondo propio: su borde izquierdo
  title-colour: "#E6CB98"
  title-align: centre                  # o left, donde lo empieza vanilla
  content-slots: "0-44"
```

El fondo puede ser cualquier font image de los packs, también las de ItemsAdder; para esos,
`background-width` y `background-x` dicen cómo colocarlo (el libro del plugin los ignora, así que un
`config.yml` de 1.9.0 con `background-width: 176` sigue funcionando).

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

#### Qué se ha verificado en un servidor Paper 1.21.8 real (v1.9.2, el libro)

Con un bot de mineflayer y los 1609 ítems del servidor de prueba cargados:

| Prueba | Resultado |
|---|---|
| Pack | `assets/arkcontent/textures/gui/content_browser.png` de 196 × 246 en la fuente por defecto con `ascent` 35 y `height` 246 (U+F006); el pergamino del tablón sigue en 13 y 222; los 7 iconos con su definición de ítem, su modelo `item/generated` y su textura |
| Título | Llega `U+F809 U+F802` + U+F006 + `U+F80C U+F80A U+F809 U+F803` en blanco y, tras `U+F82A U+F829 U+F826`, «Categories» en `#E6CB98`; en `dragones_epicos`, «Dragones Epicos» tras `U+F82A U+F827` |
| Botones | Fila de abajo: `arkcontent:menu/filter` en 48, `close` en 49, `page` en 50 («Categories - page 1 of 1»); dentro de una carpeta, también `up` (46), `home` (47) y `next` (53) |
| Carpetas | La raíz lista 41 carpetas; «Dragones Epicos» (560 ítems) abre una ventana nueva con su título, 45 entradas, «page 1 of 13» |
| Páginas | Del paquete del clic a la página nueva: 61, 50, 45 y 43 ms (un tick); dos «Next» enviados a la vez avanzan una página, no dos |
| Sin `arkcontent.give` | Con el menú abierto se le quita el op al bot y se envían 24 clics en crudo sobre un ítem y sobre su propio inventario: izquierdo, derecho, mayúsculas (los dos), las 9 teclas numéricas, cambio a la otra mano, clic central, Q y Ctrl+Q, un arrastre completo, doble clic para recoger, mayúsculas sobre su inventario y clic fuera. Su inventario en el servidor sigue vacío (`[]`) y hay los mismos 7 ítems sueltos alrededor antes y después (restos de pruebas anteriores): no se ha soltado nada |
| Con permiso | Un clic normal de un op da una pila nueva del ítem (unas botas de Abyssor), como debe |
| Tienda | `/arkcontent shop` abre «Shop» con la carpeta Demo; dentro, el rubí con «Price: $250» |

La imagen del libro no se ha podido ver en un cliente real: lo verificado son los caracteres exactos
que recibe, la fuente y los archivos del pack, y la imagen misma píxel a píxel en los tests.

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
- Asientos: uno por bloque del mueble (o uno en su centro con `seat-at`), con la altura fija de
  `seat-height`. Un mueble es asiento, contenedor o ninguno de los dos, no ambos.
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
- Muebles de varios bloques (`size:`) solo con soporte barrera o luz, hasta 5 × 5, y siempre en un
  plano vertical a lo ancho de su frente (sin fondo). Sujetar un ítem de mueble muestra las
  partículas de barrier/light que el cliente dibuja al sostener esos materiales.
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
  (`liquids.chunk-reach`), ni empuja a quien está dentro, ni apaga el fuego. Los mobs solo reciben
  el efecto de contacto con `mobs: true` (2.0).
- 1.8, font images: `:nombre:` y `:offset_N:` se reescriben en títulos de menú al abrirse; un
  plugin que cambia el título de un menú ya abierto (`InventoryView#setTitle`) no pasa por ahí. En
  chat, scoreboards y nombres de ítems sirve `%img_...%` a través de PlaceholderAPI, no la forma
  `:nombre:`. Una imagen en una fuente que no es la por defecto no cabe en un texto de
  PlaceholderAPI, así que ahí no se devuelve.
- 1.8, editor: «solo esta pieza» no existe para muebles animados ni el editor para los de ModelEngine
  (ver sección 4). El YAML se edita línea a línea: un `display` escrito como mapa en varias líneas,
  o un ítem definido dos veces en el archivo, se rechaza sin escribir. Los valores escritos a mano
  como un solo número (`scale: 0.5`) pasan a lista (`[0.5, 0.5, 0.5]`). Ver el modelo moverse
  detrás del menú depende del cliente: el menú de cofre oscurece el fondo pero no lo tapa.
- 1.8/2.0, verificador: solo audita chunks cargados. Fuera de la ruta rápida (ediciones de
  WorldEdit/FAWE y displays eliminados) tarda dos intervalos en actuar: un `/fill`, un `/setblock` o
  un rollback de CoreProtect esperan al ciclo. Un mueble de más de un bloque colocado antes de la 2.0
  que ya había perdido su display al actualizar no tiene dirección guardada y no se puede redibujar:
  se retira y su contenido va a `recovered_storage`. Si algo devuelve después el display original
  de un mueble ya redibujado (un rollback de entidades), quedan dos: el verificador no busca displays
  repetidos, y esto no se ha probado. Los cultivos no se auditan (tienen su propio ciclo).
- 1.9, cofres dobles: la tapa no puede moverse a los 63 ms justos, porque el servidor trabaja en
  ticks de 50 ms: se mueve en el primer tick a partir de los 63 ms (entre 63 y 113 ms). Un cofre doble
  con `animated-model` es un display por hueso más su raíz, no un solo `ItemDisplay`: así es como se
  animan los huesos por separado; un solo `ItemDisplay` gigante es lo que se obtiene con `model:` (sin
  animación). Dos cofres solo se unen al colocar el segundo, como en vanilla: dos que ya estaban uno al
  lado del otro antes de 1.9, o antes de añadir `double:`, siguen simples hasta que se vuelva a
  colocar uno. Cada mitad tiene como mucho 27 huecos.
- 1.9, tablón: los objetivos son entregar, matar y romper (no hay "hablar con", "viajar a" ni
  "pescar"); los requisitos se miran al aceptar, no al entregar. Las muertes y bloques contados en
  los últimos `save-seconds` antes de un crash se pierden (al salir el jugador o al apagar se
  guardan). La recompensa en dinero necesita Vault con un plugin de economía; sin él, el aviso se
  entrega igual, y se avisa en consola y al jugador de que no se pudo pagar. El aspecto del pergamino y del tablón en un
  cliente real no se ha podido ver aquí.
- 1.9.2, libro: sobresale 22 px por encima del cofre, así que en una ventana muy baja para su escala
  de interfaz (menos de 266 px de alto a esa escala) se recorta por arriba. El centrado del título
  usa las anchuras de la fuente por defecto; una letra fuera del ASCII se cuenta como su letra base y
  puede quedar a un píxel. Los iconos de los botones y el libro necesitan el pack: sin él, los botones
  se ven como un modelo que falta. Entrar en una carpeta reabre el inventario (es como Paper deja
  cambiar un título con imagen). Mientras el worker está ocupado con una recompilación, la página
  pedida espera a que termine.
- 1.9.5/1.9.6, muebles medievales: los 60 modelos se generaron por programa y se comprobó que
  compilan, que cada uno usa su textura, que caben en los límites de un modelo Java y que ninguna cara
  queda en el mismo plano que otra (no parpadean); se revisaron renderizados en un visor 3D con las
  mismas UV, pero su aspecto en un cliente real no se ha podido ver aquí (los bots no dibujan), ni tampoco dónde queda exactamente la cabeza
  del que se arrodilla en el cepo respecto al agujero. Las partículas `effect:` solo pueden ser de
  las que no llevan datos (nada de `dust` con color). Los marcadores de los comandos son los de la
  tabla; `%player%` de PlaceholderAPI u otros se dejan como están, para el plugin del comando.
- 1.9.5, guillotina: el golpe es el ataque de quien tira de la palanca, así que todo lo que impide
  atacar lo impide: con EssentialsX, quien acaba de teletransportarse o de entrar no puede atacar en
  `teleport-invulnerability` / `login-attack-delay` segundos (4 y 5 en el servidor de prueba), y la
  víctima recién teletransportada tampoco puede ser dañada. `respect-pvp: false` lo quita, pero
  entonces tampoco lo para una región sin PvP. En el servidor de prueba, la **primera** muerte por
  PvP tras arrancar tardó un tick de 196 ms: 70,7 ms en los listeners de daño y 92,2 ms en los de
  muerte, de otros plugins, la primera vez que corren; la segunda muerte ya no pasó de 20 ms de tick
  (un `/kill` normal, la primera vez, 51 ms).
- 1.8, red: la IP descubierta es por la que la máquina **sale** a internet; en un nodo con varias IPs
  puede no ser por la que **entran** los jugadores. Ahí, `http.public-address` (o un `server-ip`
  público) manda. Un puerto de respaldo solo sirve si el hosting lo redirige.
- 1.7/2.0, HUDs: la posición en pantalla depende de `ascent` y `offset` (o del `anchor`, que los
  calcula con la geometría del cliente 1.21.8). La escala de interfaz del jugador no cambia nada
  (es todo en píxeles de la interfaz), pero otros mods de cliente que muevan la barra de acción, los
  corazones o el hambre sí. Un anclaje no se aparta cuando vanilla dibuja algo en esa fila: el
  escudo en la fila de la armadura la taparía si el jugador lleva armadura (por eso el de ejemplo va
  dos filas más arriba), y lo mismo la sed con las burbujas de aire. Con más de 10 corazones entre vida y
  absorción, vanilla añade filas de corazones y sube la armadura; las barras ancladas a ese lado no
  suben con ellas. Una barra anclada solo queda en su sitio en la barra de acción; TAB no tiene barra de
  acción. AuraSkills tapa la barra de acción con la suya (ver sección 4). El dibujo en un cliente
  real no se ha podido comprobar aquí: lo verificado es el texto exacto que recibe el cliente y la
  fuente del pack.
- 2.0, líquidos y shaders: el brillo (`glow`) hace que el líquido se vea iluminado, pero no ilumina
  lo de alrededor. El plugin no trae ningún shader activado; el validador revisa los que se añadan
  contra lo que el cliente 1.21.8 carga y declara, pero no compila GLSL: un error dentro del cuerpo
  de un shader (un tipo mal, una variable que no existe) solo se ve en el cliente. Cómo se ven el
  brillo y cualquier shader no se ha podido comprobar aquí.
- 3.0, Bedrock: lo que Geyser recibe se fija al arrancar; contenido nuevo tras un `reload` llega a
  Bedrock al reiniciar. Los muebles son bloques: solo los cuatro giros de 90° (un mueble girado a
  45° se ve en el giro más cercano), sin animaciones, sin cosméticos (Geyser no dibuja displays), y
  una geometría de Bedrock no puede salir más de 7 píxeles de su bloque, por eso se corta por celdas.
  Un mueble de soporte de luz (que se atraviesa) es para Geyser un note block: la corrección de
  movimiento de Geyser podría empujar a quien lo atraviesa; no se ha podido comprobar. Con Geyser
  2.8, una stack con un `custom_model_data` ajeno mayor que los de este plugin se vería como el
  último ítem de este plugin por debajo de ese número (es la regla de Geyser). Geyser tiene que estar
  en este servidor; en un proxy, el `.mcpack` se puede copiar, pero los bloques no se registran.
  Cómo se ve todo en un cliente de Bedrock no se ha podido ver aquí.
- 3.0, cosméticos: un hueco, la espalda. Sin PacketEvents el display es una entidad real movida por
  el hilo principal (barata, pero una entidad por jugador).
- 3.0, minijuegos: la barra va en la barra de acción, así que otro plugin que la use a la vez (la de
  AuraSkills, por ejemplo) se alterna con ella. Una mena de minijuego solo se mina jugando: ni
  pistones, ni explosiones, ni otros plugins la rompen por esa vía.

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

Fuera de esa lista, la 3.0 añade Geyser (todo el contenido para Bedrock, sin configuración; ✔ con
un cliente de Bedrock por protocolo), PacketEvents (✔), LuckPerms (✔), Towny (◐: cargado, sin
ciudades) y Lands (◐: compilado).

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
