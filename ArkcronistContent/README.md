# ArkcronistContent

Motor de contenido personalizado para **Paper 1.21.4+**, Java 21: define ítems en YAML y el plugin
compila su propio resource pack, lo empaqueta en ZIP, calcula su SHA-1, lo sirve con un servidor
HTTP integrado y se lo envía a cada jugador. El mismo concepto que ItemsAdder u Oraxen, reducido a
una base limpia sobre la que crecer.

```
/customgive <jugador> <item> [cantidad]    entrega un ítem (acepta @a, @p...; autocompleta ids)
/arkcontent reload                         recompila ítems y pack sin reiniciar
/arkcontent info                           ítems cargados, hash del pack, URL servida
```

El mínimo es 1.21.4 porque es la versión que introdujo el componente `item_model` y la carpeta
`assets/<namespace>/items/`, en los que se apoya todo el plugin.

---

## 1. Compilar

```bash
./gradlew build          # -> build/libs/ArkcronistContent-0.1.0.jar
./gradlew test           # pruebas del núcleo, sin servidor
./gradlew runServer      # levanta un Paper 1.21.8 desechable con el plugin instalado
```

El wrapper fija Gradle 9.8.0. `paper-api` se declara `compileOnly`: el servidor ya trae Adventure,
MiniMessage, Brigadier, Gson y SnakeYAML, así que el JAR solo contiene las clases del plugin.
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
        │   │   ├── ItemDefinition         un ítem tal como lo describe el YAML
        │   │   ├── ItemAssets             modelo / textura / parent y carpeta de origen
        │   │   ├── ItemBehaviour          qué conserva del material base
        │   │   └── ResourceLocation       namespace:ruta validado (anti path traversal)
        │   ├── loader/
        │   │   ├── ContentLoader          escanea contents/ y parsea los .yml (SnakeYAML seguro)
        │   │   └── LoadReport             ítems leídos + problemas encontrados
        │   ├── pack/
        │   │   ├── PackCompiler           genera assets/<ns>/{items,models,textures} y pack.mcmeta
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
            ├── pack/PackDelivery          pack vivo + envío con setResourcePack
            ├── command/                   CustomGiveCommand, ContentAdminCommand (Brigadier)
            ├── listener/                  uso, combate, mundo, entrega del pack
            └── event/                     CustomItemEvent y sus subclases
```

## 3. Definir contenido

Cada carpeta directamente bajo `plugins/ArkcronistContent/contents/` es un pack de contenido. Todo
`.yml` dentro de ella, a cualquier profundidad, puede declarar ítems; sus carpetas `models/` y
`textures/` guardan los archivos a los que apuntan.

```
contents/demo/
├── items.yml
├── models/item/ruby.json
└── textures/item/
    ├── ruby.png
    └── ruby_sword.png
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

| Clave | Obligatoria | Qué hace |
|---|---|---|
| `material` | sí | Material base de Minecraft. |
| `display-name` | no | Nombre (componente `item_name`, sin cursiva). MiniMessage. |
| `lore` | no | Líneas de lore. MiniMessage. |
| `resource.model` | no | Modelo propio. Se copia junto con los `parent` y `textures` que use de su namespace. Con otro namespace (`minecraft:item/diamond`) solo se referencia. |
| `resource.texture` | no | Textura para un modelo plano generado. Se ignora si hay `model`. |
| `resource.parent` | no | Parent del modelo generado. Sin namespace significa `minecraft:`. |
| `behaviour.cancel-vanilla-use` | no | `true` anula el uso vanilla del material al hacer clic. |
| `behaviour.placeable` | no | `true` deja colocar como bloque un ítem cuyo material es un bloque. |

Un ítem roto nunca impide que carguen los demás: cada problema se registra en consola con el archivo
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
hilo worker : contents/*.yml → CustomItem → pack/ (assets, pack.mcmeta) → resource_pack.zip → SHA-1
hilo main   : sustituye el registro de ítems y el pack vivo; reenvía a los jugadores conectados
```

Todo el I/O (lectura de YAML, copia de archivos, ZIP, hash, arranque del servidor HTTP) ocurre en
un hilo propio del plugin. El hilo principal solo ejecuta el último paso, que no toca disco. Las
recompilaciones nunca se solapan, y si una falla, los ítems y el pack anteriores siguen activos.

El ZIP es **determinista**: entradas ordenadas y fechas fijas. El cliente cachea los packs por su
SHA-1, así que el mismo contenido produce el mismo hash y los jugadores no vuelven a descargarlo
tras cada reinicio. Se escribe en un temporal y se mueve de forma atómica, así que
`output/resource_pack.zip` nunca está a medio escribir.

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

Una nueva intercepción (proyectiles, soltar o recoger, bloques personalizados) sigue el patrón de
`WorldInterceptionListener`: identificar la stack con `ItemFactory#identify` y disparar una nueva
subclase de `CustomItemEvent`.

Desde otro plugin, el registro está en `JavaPlugin.getPlugin(ArkContentPlugin.class).items()`. Al
ser un plugin de Paper, ese otro plugin debe declararlo como dependencia en su `paper-plugin.yml`
con `join-classpath: true` para ver sus clases.

## 6. Límites conocidos

- Cambiar nombre o lore en el YAML no actualiza las stacks ya entregadas (el modelo sí).
- `/arkcontent reload` no relee `config.yml`: puerto y dirección del servidor HTTP piden reinicio.
- Un ítem hecho de un material con uso propio (arco, comida, perla) conserva ese uso salvo que
  `cancel-vanilla-use` esté activo, y sigue valiendo como ingrediente en recetas vanilla.
- El pack se envía en `PlayerJoinEvent`. En 1.21.7+ Paper permite enviarlo durante la fase de
  configuración, antes de entrar al mundo; es el siguiente paso natural.
