# API para desarrolladores (5.4)

Otros plugins usan el contenido de OverlordContent —sus ítems, bloques, muebles y cultivos— a
través de una interfaz estable, `com.arkcronist.content.api.OverlordContentApi`, y se enteran de lo
que los jugadores hacen con él por los eventos de `com.arkcronist.content.api.event`. Todo se
nombra por el id del contenido, `namespace:id`, el mismo que en los YAML (`demo:ruby`).

## Dependencia

El JAR del plugin es la dependencia: se añade como `compileOnly` (no se empaqueta) y
`OverlordContent` va en el `depend` o `softdepend` del `plugin.yml`.

```kotlin
// build.gradle.kts
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly(files("libs/OverlordContent-5.4.0.jar"))
}
```

```yaml
# plugin.yml
depend: [OverlordContent]      # o softdepend, si tu plugin funciona también sin él
```

Solo las clases de `com.arkcronist.content.api` son la API: lo demás del JAR puede cambiar de una
versión a otra.

## La API

```java
OverlordContentApi content = OverlordContentApi.get();       // o find(): Optional, sin excepción

// Ítems (los bloques, muebles y cultivos son ítems también)
content.createItem("demo:ruby", 3).ifPresent(player.getInventory()::addItem);
content.itemId(player.getInventory().getItemInMainHand())   // Optional<String>: "demo:ruby"
        .ifPresent(id -> player.sendMessage("Llevas " + id));
content.itemIds();                                           // todos los ids

// Bloques
content.blockAt(block);                    // Optional<String>: el bloque propio que hay ahí
content.placeBlock(block, "demo:ruby_block");   // colocado y registrado como si lo pusiera un jugador
content.removeBlock(block);                // quitado, sin soltar nada

// Muebles
content.furnitureAt(block);                // el mueble que ocupa ese bloque (cualquiera de los suyos)
content.furnitureOf(entity);               // el mueble del que es parte una entidad (modelo, caja)
content.placeFurniture(block, "demo:ruby_pedestal", 90f);   // girado 90°; false si no cabe
content.removeFurniture(block, true);      // quitado, soltando su ítem
content.playAnimation(block, "open");      // una animación de Blockbench del mueble

// Cultivos y árboles
content.cropAt(block);                     // el cultivo o retoño plantado ahí
content.cropStage(block);                  // su etapa, desde 0

// Todo
content.ready();                           // true cuando bloques y muebles ya están cargados
content.reload();                          // como /arkcontent reload, en segundo plano
content.version();                         // "5.4.0"
```

Todo es para el hilo principal, salvo `itemId`, que vale desde cualquier hilo. Los ítems se conocen
desde el primer tick del servidor; los bloques y muebles, unos segundos después, cuando termina la
primera compilación: `ready()` lo dice, y `OverlordContentLoadedEvent` avisa.

## Eventos

| Evento | Cuándo | Cancelable |
|---|---|---|
| `OverlordContentLoadedEvent` | el contenido se ha cargado (al arrancar y en cada `reload`) | no |
| `CustomBlockPlaceEvent` | un jugador coloca un bloque propio | sí: no se coloca |
| `CustomBlockBreakEvent` | un jugador rompe un bloque propio | sí: el bloque se queda |
| `FurniturePlaceEvent` | un jugador coloca un mueble | sí: no se coloca |
| `FurnitureBreakEvent` | un jugador rompe un mueble | sí: el mueble se queda |
| `FurnitureInteractEvent` | un jugador hace clic derecho en un mueble (también en uno que no hace nada) | sí: el mueble no reacciona |
| `CustomItemTriggerEvent` | los `events:` de un ítem van a reaccionar (`right-click`, `kill`...) | sí: no se hace nada |

Los de colocar y romper llegan **después** de que los plugins de protección (WorldGuard,
GriefPrevention, Lands, Towny) hayan decidido —solo se oyen si el jugador podía hacerlo— y
**antes** de que OverlordContent registre o suelte nada; cancelar uno cancela el evento de vanilla.

```java
public final class MiPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    /** Las ids que mi configuración nombra, comprobadas cuando el contenido está listo. */
    @EventHandler
    public void alCargar(OverlordContentLoadedEvent event) {
        OverlordContentApi content = OverlordContentApi.get();
        if (!content.itemIds().contains("demo:ruby")) {
            getLogger().warning("demo:ruby ya no existe");
        }
    }

    /** Un mueble sin nada propio que hacer abre mi menú. */
    @EventHandler
    public void alUsarMueble(FurnitureInteractEvent event) {
        if (event.getId().equals("miservidor:mesa_mapa")) {
            event.setCancelled(true);              // el mueble no hace lo suyo
            abrirMenuDeMapa(event.getPlayer());
        }
    }

    /** Las menas de rubí solo se pican en la mina. */
    @EventHandler
    public void alRomper(CustomBlockBreakEvent event) {
        if (event.getId().equals("demo:ruby_ore") && !event.getBlock().getWorld().getName().equals("mina")) {
            event.setCancelled(true);
        }
    }

    /** Ningún bastón de tormenta en el spawn. */
    @EventHandler
    public void alDisparar(CustomItemTriggerEvent event) {
        if (event.getItemId().equals("demo:storm_staff") && enSpawn(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
```

Siguen existiendo los eventos de antes, `com.arkcronist.content.bukkit.event.CustomItemUseEvent`,
`CustomItemHitEvent` y `CustomItemBreakBlockEvent`; son de la parte interna y dan el ítem como
objeto interno: para código nuevo, mejor los de `api.event`, que dan ids.

## Lo comprobado

Un plugin de prueba aparte, compilado solo contra la API, en un servidor Paper 1.21.8 con un
jugador bot: encuentra la API por los servicios de Bukkit, oye la carga del contenido (la primera,
con `ready()` ya en true), los siete eventos al colocar, romper y usar bloques, muebles e ítems, y
al cancelarlos el taburete no sienta y el bloque no se rompe; `createItem`, `itemId`, `placeBlock`,
`blockAt`, `placeFurniture` (con su modelo visible), `furnitureAt`, `removeBlock` y
`removeFurniture` hacen lo que dicen.
