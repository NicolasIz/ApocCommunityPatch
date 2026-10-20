# Perfil estético: la línea de diseño de las texturas y modelos

Este perfil sale de medir dos fuentes. La primera son los 18 packs comprados de `texturas_y_cosas_asi.zip`:
Dreadknight, Ares, Duelist, UNIQUE ARMOR VOL.2, Evergreen, Fiend Skull, Sakura, Arthur, Farmer, Japan
Armor, Star Crown, L-Bear, Cosmetics VOL 1-2, W6 Benches, Dark Forest, Goblin Camp, Fantasy Vol.1 y
3D Food. La segunda es el pack generado de ItemsAdder del servidor (`generated_4`), con los namespaces
propios `arkpicos`, `arkkatanas`, `arkespadas` y `ark_rangos`.

Se midieron:

- 4192 texturas: 789 de los packs y 3403 del pack de ItemsAdder, incluidas las «protegidas»;
- 893 modelos `.json` distintos;
- 75 `.bbmodel`.

La lámina `perfil-estetico-paleta.png`, en esta carpeta, muestra las rampas de color.

Quedaron fuera dos archivos: `World_Mob_Pack` y los `.json` de `3D_Food_Pack` vienen en RAR comprimido.
Sus PNG sí se leyeron. El World Mob Pack solo trae configuraciones de MythicMobs, sin texturas.

Todo lo nuevo que se cree sigue este perfil, salvo que se pida otra cosa.

## 1. Paleta y sombreado

### Lo que se midió

| | Packs comprados (referencia) | Armas propias `ark*` (256×256) |
|---|---|---|
| Colores por textura | **mediana 14** (casi todas entre 5 y 35) | mediana 80 |
| Pasos de una rampa (metal, madera) | **4** (entre 3 y 5) | 7 |
| Pasos de una rampa (cristal, oro) | **4-6** | 8-9 |
| Borde oscuro (contorno) | 28 % del borde (al azar sería 20 %): contorno **selectivo**, no negro | 23 % |
| Tramado (dithering) | casi nunca (mediana 0,3 %) | casi nunca |
| Semitransparencia | casi nunca (6 de 771 texturas) | nunca |
| Tamaños | 32×32, 64×64, 128×128, 48×48, 96×96; armaduras 64×32 y 128×64 | 256×256 |

Las armas `ark*` tienen el mismo contorno y la misma ausencia de tramado. Usan muchos más colores y
rampas más largas, porque se pintan a 4 píxeles por cada 1/16 de bloque (ver §2).

### Reglas de sombreado

1. **Paleta corta y cerrada.** Entre 8 y 20 colores para un objeto de 32-64 px y, como mucho, unos 35
   para uno de 128 px. Cada material tiene su rampa de 4-5 tonos, y no se mezclan degradados suaves.
   La excepción son las armas legendarias a 4×, que pueden llegar a 6-8 pasos por material.
2. **Saltos de luminosidad parejos.** Entre 8 y 11 puntos de L* por paso; por ejemplo, el hierro
   neutro va L* 16 → 26 → 35 → 43 → 51. El brillo especular, casi blanco (L* 83-96), se pone en muy
   pocos píxeles, en las aristas de arriba.
3. **Sombras frías y desplazadas de tono** en los materiales saturados:
   - El **oro** no se oscurece hacia el marrón: su sombra es un índigo (`#261C42`, `#29214F`) que salta
     directo a los tonos dorados.
   - El **cristal** sube de violeta en la sombra a cian en la luz.
   - El **rojo** va de un carmesí-magenta en la sombra a un naranja-rojo en la luz.
   - En la **madera**, la sombra se va al rojo y la luz al amarillo.
4. **La luz pierde saturación.** En todas las familias, la sombra está más saturada que la luz:
   cristal 0,66 → 0,61, madera 0,56 → 0,51, oro 0,86 → 0,68.
5. **Metal oscuro casi neutro, con un toque azul en la sombra**, con el azul unos 6-12 puntos por
   encima del rojo (`#262930`, `#363941`, `#444A55`). El gris acero de referencia es `#6E788C`, que
   aparece en los packs comprados y en el de ItemsAdder.
6. **Contorno selectivo, nunca negro puro.** El borde va con el tono más oscuro de la rampa del propio
   material, y solo en el lado en sombra. El negro `#000000` no se usa.
7. **Sin tramado y opaco.** El cristal y el vidrio son opacos: se leen por la rampa y el brillo
   cian-blanco, no por transparencia. El alfa se usa solo para recortar la silueta.
8. **Brillo emisivo como acento**, nunca como base: un acento saturado (sangre, fuego, energía verde)
   ocupa el 10-20 % de la textura, sobre metal oscuro o madera.

### Rampas de referencia (hex, de sombra a luz)

| Material | Rampa | De dónde sale |
|---|---|---|
| Acero oscuro | `#262930` `#363941` `#444A55` `#5C626E` · brillos `#979CA5` `#CCCED1` `#F3F4F7` | Dreadknight |
| Hierro neutro | `#282928` `#3C3D3B` `#50524F` `#636663` `#767A76` | W6 Benches |
| Netherita / obsidiana | `#10121A` `#0D1A2F` `#2D2C43` `#4B3253` `#614F6D` `#976986` | Duelist |
| Madera medieval | `#4E332D` `#684C3C` `#80624A` `#997A58` | W6 Benches |
| Roble oscuro | `#221006` `#34241C` `#4E3A34` `#684E42` | UNIQUE ARMOR (casco 5) |
| Madera cálida | `#54341C` `#77421A` `#92582B` `#A76B3D` | Star Crown |
| Pino claro | `#895026` `#B46433` `#D38449` `#E59C65` `#F4D5B8` | Farmer (mesa) |
| Oro (sombra índigo) | `#261C42` `#29214F` → `#E19F3E` `#F0C069` `#F7E198` | Duelist (estoque) |
| Oro de Ares | `#D27441` `#EB8E48` `#FAC15A` `#FFE189` `#FFFFFF` | Ares |
| Cristal violeta → cian | `#1D1138` `#2F1850` `#442067` `#5E2881` `#722D91` `#5963A4` `#6A8CD2` `#7AACEE` `#9ED8FD` | Duelist (estoque de diamante) |
| Cristal verde | `#0B1C2E` `#0D3447` `#0F5F6B` `#0E926C` `#0EC83F` `#6DF41F` | Evergreen |
| Rubí / sangre emisiva | `#290311` `#51081E` `#7A002C` `#990921` `#AE0925` `#C20505` `#DF1C12` `#FF2B13` | Dreadknight |
| Fuego | `#FF581B` `#FF6A29` `#FF9D31` `#FFB439` `#FFFF55` | Star Crown |

## 2. Geometría, UV e inflado

### Lo que se midió

| | Packs comprados | Muebles del pack de ItemsAdder | Armas `ark*` |
|---|---|---|---|
| Cubos por modelo | mediana 8 (p75 21, p90 43) | mediana 13 (p90 40) | mediana 72 |
| `texture_size` | 16 (sin poner), 64, 32, 24, 128 | 64, 32, 16, 128 | 16 |
| Ángulos de rotación | 0, ±22,5, ±45 | 0, ±22,5, ±45 | 0, ±22,5, ±45 (y ±11,25 en dos piezas) |
| Caras por cubo | 6 (un 25 % omite las ocultas) | 6 | 6, 5 o 4 |
| Rotación de cara (UV) | 0 / 90 / 180 / 270 | 90 / 270 / 180 | no |
| `light_emission` | no | casi no | sí, 10-15 en las partes que brillan |
| Coordenadas | dentro de −16…32 | dentro de −16…32 | dentro de −16…32 |

**Densidad de textura** (píxeles por cada 1/16 de bloque, medida con el tamaño real del PNG):

- **1×** (16 px por bloque): cosméticos, bancos, Duelist, Arthur, UNIQUE ARMOR y los muebles del pack
  de ItemsAdder.
- **2×** (32 px por bloque): Dreadknight, Ares, Sakura, Farmer y L-Bear.
- **4×** (64 px por bloque): Evergreen, Fiend Skull y las armas `ark*`.

**`.bbmodel`:** 74 de 75 usan **UV por cara** (`box_uv: false`), con islas pequeñas y aprovechadas.
Las resoluciones son 32, 64 y 128 (256-512 en jefes de ModelEngine). El formato es `java_block` para
objetos y armaduras, y `free` para ModelEngine.

**Inflado.** Un modelo `.json` de Java no tiene inflado: Blockbench lo convierte en `from`/`to` al
exportar. Los valores que se usan:

| Inflado | Para qué |
|---|---|
| ±0,01 a ±0,03 | separar cubos que se tocan, para que no parpadeen (el más común, en 22 modelos) |
| 0,15 a 0,30 | capas por encima: placas de armadura sobre el cuerpo, el filo sobre la hoja, la cinta del casco |
| 0,75 a 1,0 | la carcasa exterior de un casco 3D sobre la cabeza |
| −0,01 a −0,30 | encoger cubos internos de mobs de ModelEngine |

### Reglas de geometría

1. **Una sola densidad por modelo:**
   - muebles y cosméticos a 1×, o 2× si son de detalle;
   - armas y herramientas a 2×;
   - legendarias a 4×, como las `ark*`.

   La textura se dimensiona para esa densidad: una espada de 2 bloques a 2× cabe en 64×64.
2. **UV por cara**, con islas empaquetadas. Para ahorrar sitio se giran las caras (0/90/180/270), y las
   caras que no se ven se quitan.
3. **Rotaciones solo de 22,5° en 22,5°** (0, ±22,5, ±45), en un eje por cubo y sin `rescale`.
4. **Coordenadas dentro de −16…32.**
5. **Pocos cubos:** objetos de mano por debajo de 25-45 cubos y muebles por debajo de 40. Lo que brille
   lleva `light_emission` (12-15) en sus cubos, como las armas `ark*`, o una textura animada en tira
   con su `.mcmeta`, como Evergreen, Fiend y Sakura.
6. **`display` completo:** `gui`, `ground`, `fixed`, `thirdperson_righthand`/`lefthand` y
   `firstperson_righthand`/`lefthand`, más `head` si es un casco o un cosmético. En `gui` la escala
   habitual ronda 0,6.
7. **Armaduras:** capas de equipo de 64×32 (1×) o 128×64 (2×). `layer_1` es casco, peto y botas;
   `layer_2` son las grebas. Un casco 3D es un modelo de objeto con `display.head` y una carcasa
   inflada de 0,75 a 1,0.

## 3. Rutas y namespace

**Formato propio de ArkcronistContent** (el que se genera a partir de ahora):

```
contents/<ns>/<archivo>.yml             namespace: <ns>  +  items:
contents/<ns>/models/<categoria>/<id>.json
contents/<ns>/textures/<categoria>/<id>.png
```

- En el YAML, el modelo se nombra como `resource: { model: '<categoria>/<id>' }`.
- Dentro del modelo, la textura es `"<ns>:<categoria>/<id>"`, con **un solo nivel de subcarpeta**, que
  es la norma en los packs (583 de 719 referencias).
- Categorías en uso: `item`, `furniture` y `armor`.

**Formato de ItemsAdder** (el de los packs comprados):

```
contents/<ns>/configs/*.yml
contents/<ns>/resourcepack/assets/<ns>/models/<grupo>/<id>.json
contents/<ns>/resourcepack/assets/<ns>/textures/<grupo>/<id>.png
```

Los namespaces propios del servidor, en el pack generado, son `arkpicos`, `arkkatanas` y `arkespadas`,
con la estructura `items/<id>.json`, `models/item/<id>.json` y `textures/item/<id>.png`.

### Esqueleto JSON

Así es un modelo de objeto a 2×: 64×64 para unos 2 bloques. Los cubos llevan UV por cara y rotación de
22,5°. Las dos últimas piezas solo muestran cómo va una pieza que brilla:

```json
{
  "credit": "ArkcronistContent",
  "texture_size": [64, 64],
  "textures": {
    "0": "<ns>:item/<id>",
    "particle": "<ns>:item/<id>"
  },
  "elements": [
    {
      "name": "hoja",
      "from": [7.5, 4, 7.75], "to": [8.5, 22, 8.25],
      "rotation": {"angle": 0, "axis": "y", "origin": [8, 8, 8]},
      "faces": {
        "north": {"uv": [0, 0, 0.5, 9], "texture": "#0"},
        "south": {"uv": [0.5, 0, 1, 9], "texture": "#0"},
        "east":  {"uv": [1, 0, 1.25, 9], "texture": "#0"},
        "west":  {"uv": [1.25, 0, 1.5, 9], "texture": "#0"},
        "up":    {"uv": [1.5, 0, 1.75, 0.5], "texture": "#0", "rotation": 90}
      }
    },
    {
      "name": "filo_emisivo",
      "from": [7.48, 3.98, 7.73], "to": [8.52, 22.02, 8.27],
      "light_emission": 12,
      "faces": {
        "north": {"uv": [2, 0, 2.5, 9], "texture": "#0"},
        "south": {"uv": [2.5, 0, 3, 9], "texture": "#0"}
      }
    }
  ],
  "display": {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand":  {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand":  {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui":    {"rotation": [0, 0, -45], "scale": [0.6, 0.6, 0.6]},
    "fixed":  {"rotation": [0, 180, 0], "scale": [0.8, 0.8, 0.8]}
  }
}
```

El filo emisivo está inflado 0,02 sobre la hoja: es el valor que evita el parpadeo, y Blockbench lo
escribe ya sumado en `from`/`to`. Las UV de una cara se calculan con la densidad: a 2×, 1 unidad de
modelo son 2 px de la textura de 64 px, es decir, 2/64 × 16 = 0,5 unidades de UV.
