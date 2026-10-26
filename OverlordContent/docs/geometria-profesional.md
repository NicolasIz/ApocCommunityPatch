# Geometría profesional: cómo están construidos los objetos de los packs comprados

Esta guía completa `decoracion-profesional.md`, que trata sobre todo de la paleta y la pintura. Aquí se
estudia solo la **forma**: con cuántos cubos se hace cada objeto, de qué grosor, en qué rejilla, cómo se
unen y cómo se giran. Al final se explica qué se cambió en la 5.2.0 por este estudio y qué no.

Las láminas de construcción (cada cubo de un color según su forma) muestran arte comprado. Por eso se
entregan aparte y no se suben al repositorio.

## 1. Qué se estudió

**389 objetos de referencia**, cada uno clasificado por tipo (mesa, silla, barril, caja, montón...):

- **Del zip, decoración (64 piezas):**
  - W6 Benches;
  - Farmer Decoration;
  - 3D Food Pack.
- **Del zip, otros objetos (95 piezas):**
  - los accesorios de Cosmetics Bundle (el caldero, entre ellos);
  - el cofre y los juguetes del oso;
  - las herramientas y armas de Evergreen, Fiend Skull, Duelist y Arthur;
  - el mimic de Dark Forest.
- **De `generated_4` (230 piezas):** los packs de decoración de ItemsAdder (`medieval_rpg`,
  `dungeon_furniture`, `must_have_props`, `workshop_six`, `crafting_stations`, `omstore`,
  `fischvogel`, `food_pack_v1` y `door_pack`).

Se compararon con **nuestras 367 piezas medievales**: la 5.1, y la 5.2 una vez rehecha.

**Cómo se midió.** Los modelos `.bbmodel` se leyeron con sus grupos girados. De cada cubo se anotó:

- su **forma**:
  - **plano**: grosor 0;
  - **lámina**: menos de 1 px;
  - **tabla**: hasta 2,5 px de grueso y ancha;
  - **barra**: delgada y larga;
  - **bloque**: todo lo demás;
- dónde caen sus coordenadas: en el **píxel entero**, el medio píxel o el cuarto;
- si está **girado**, cuánto y en qué eje;
- si se **mete** en otro cubo o solo lo **toca**;
- qué caras tiene **tapadas** por otro cubo o por el suelo.

De cada pieza se anotó:

- su tamaño;
- cuánto de su caja llena;
- cuántas alturas distintas tiene;
- cuánto apoya en el suelo;
- qué parte de su volumen llevan sus 3 cubos mayores.

En los muebles se midió además:

- el grosor de las patas;
- el grosor del tablero;
- cuánto sobresale el tablero de las patas.

## 2. Las cifras generales

| | Todas las referencias | Zip (decoración) | generated_4 | Nuestras 5.1 | Nuestras 5.2 |
|---|---|---|---|---|---|
| Cubos por pieza (mediana) | **11** | 9 | 9 | 21 | 20 |
| Cubos girados | **47 %** | 39 % | 40 % | 11 % | 14 % |
| Planos (grosor 0) | **18 %** | 25 % | 26 % | 1 % | 3 % |
| Caras dibujadas pero tapadas | **13 %** | 16 % | 17 % | 28 % | **19 %** |
| Coordenadas en píxel entero | **50 %** | 55 % | — | 42 % | **57 %** |
| Cubos de menos de 1 px de grueso (mediana por pieza) | **0 %** | 0 % | 0 % | 25 % | 18 % |
| Volumen que llevan los 3 cubos mayores | **71 %** | 82 % | 78 % | 63 % | 63 % |
| Apoyo en el suelo | 24 % | 51 % | 32 % | 68 % | 68 % |
| Alturas distintas | 5 | 5 | 5 | 8 | 8 |

**Lo que dicen las cifras.** Un objeto profesional:

- **es pocos cubos y grandes:**
  - 9–11 cubos por pieza;
  - los tres mayores llevan tres cuartas partes del volumen;
  - el detalle lo pone la textura, no la geometría;
- **está en píxeles enteros:**
  - la mitad de sus coordenadas son enteras y casi todo lo demás es medio píxel;
  - **no tiene cubos de menos de un píxel**: lo fino se hace con un **plano** (una cara sola, grosor 0)
    o se pinta;
- **gira mucho:** casi la mitad de sus cubos van a 22,5° o 45°, sobre todo planos y láminas (hojas,
  aros, pliegues, contenidos sueltos);
- **no dibuja lo que no se ve:** solo un 13–17 % de las caras dibujadas quedan tapadas.

Nuestras piezas de la 5.1 eran lo contrario:

- el doble de cubos;
- un cuarto de los cubos más finos que un píxel;
- el doble de caras tapadas;
- la masa repartida en muchos cubos pequeños.

## 3. Cómo se construye cada tipo (recetas con números)

Las medidas son en píxeles de modelo: un bloque mide 16.

### Mesas (19 referencias)

| | Referencias | 5.1 | 5.2 |
|---|---|---|---|
| Grosor de las patas | **3** (2,3–6) | 1,5 | **3** |
| Grosor del tablero | **2** (2–3) | 1,5 | **2** |
| Cuánto sobresale el tablero | **1–2** | 0,5 | **1** |
| Coordenadas enteras | **94 %** | 50 % | **92 %** |

- **Tablero:** una o varias tablas de 2–3 px. Las mesas largas lo hacen de 4 tablas paralelas de 7 px
  (`farmer table`, `dungeon table`) o de una tabla con dos travesaños de 3 px debajo (`tavern_long_table`).
- **Patas:** de 3×3 a 6×6, un píxel hacia dentro del borde del tablero.
- **Faldones o travesaños:** de 2–3 px de alto, entre las patas y bajo el tablero, o un larguero a media
  altura.
- Las patas de las mesas de taberna (`fancy_table`) y de los taburetes se **abren** girando cada una
  22,5° hacia su esquina.

### Sillas y taburetes (12 referencias)

| | Referencias | 5.1 | 5.2 |
|---|---|---|---|
| Patas | **2×2** | 1,5×1,5 | **2×2** |
| Grosor del asiento | **2** | 1,4 | **2** |
| Altura del asiento | **≈ 9–10** | 8,5 | 9 |
| Coordenadas enteras | **83 %** | 46 % | **81 %** |

- **Asiento:** un bloque de 2–3 px. Encima puede ir un cojín de 1 px.
- **Respaldo:** dos postes de 2×2 que siguen a las patas traseras, con tablas de 2 px o un plano.
- **Travesaños entre patas:** casi siempre son **planos** (`tavern_stool`: ocho planos de 1 px de alto),
  no barras.
- **Taburetes:** las patas van giradas 22,5° hacia fuera (`dungeon stool`, `smallchair_forest`).

### Bancos (5 referencias)

- Asiento de 2 px y unos 11 px de fondo.
- Caballetes en los extremos: dos postes de 2–3 px sobre un pie y bajo un travesaño.
- Un larguero entre los caballetes.
- El asiento sobresale 2–3 px de los caballetes.

### Estantes y armarios (12 referencias)

- Baldas y costados de 2 px.
- Lo que se guarda (botellas, libros) son bloques sueltos, a menudo girados 22,5° en `y`.

### Barriles y cubos (6 referencias)

- **4–13 cubos:**
  - un bloque de cuerpo;
  - un segundo bloque medio píxel más estrecho, que hace de aros con la textura;
  - tapas de 2 px algo más pequeñas.
- Cuando los aros son geometría:
  - en el cubo de madera (`wooden_bucket`) son **planos** a 0,2 px de la pared;
  - en la lechera (`milk_tank`) son láminas de 1 px.
- El tonel tumbado se hace igual, girado.

### Cajas y cofres (13 referencias)

- **La caja básica son 7 cubos** (`dungeon box`):
  - un bloque de 14×12×14;
  - cuatro postes de esquina de 2×12×2;
  - tapa y fondo de 16×2×16.
- Las tablas, las juntas y los clavos están **pintados**.
- Los cofres añaden la tapa en dos bloques y bisagras giradas 45°.

### Cestas (4 referencias)

- Un bloque de cuerpo, más un borde de **planos girados ±22,5°** que se abren hacia fuera
  (`corn_basket`).
- El contenido, como bloques sueltos medio girados.
- El asa, de planos.

### Sacos (7 referencias)

- Un bloque de cuerpo de unos 12×11×12.
- Planos en los costados para los pliegues.
- Una tabla de 2 px de boca.
- El contenido: bloques de 4×4×4, girados 22,5° en ejes distintos, con planos de hojas encima
  (`apple_sack`).

### Montones de piedra, leña o mineral (11 referencias)

- **6 cubos de mediana.** Casi todos son bloques (59 %).
- Los tres mayores llevan el **80 %** del volumen.
- Uno de cada tres va girado.

### Plantas (21 referencias)

- **73 % planos.** El 62 % va girado:
  - la mitad a 22,5°;
  - la mitad a 45°;
  - en los tres ejes.
- La maceta, unos pocos bloques.

### Comida (80 referencias)

- 11 cubos de mediana.
- El 49 % va girado y el 22 % son planos (hojas, migas, salsas).
- Muy poco apoyo propio: la comida se pone **sobre** algo.

### Herramientas y armas (56 referencias)

- Son la excepción: muchas láminas finas (70 %) giradas en `x`, porque se ven en la mano y de cerca.
- No sirven de modelo para los muebles.

## 4. Las reglas que salen del estudio

1. **Píxeles enteros.** Todo cubo en píxel entero o, si tiene que centrarse, en medio píxel.
2. **Nada de menos de un píxel.** Lo fino es un plano, y su grosor lo da la textura:
   - un aro;
   - una etiqueta;
   - un borde;
   - la superficie del contenido.
3. **Los muebles se miden así:**

   | Mueble | Patas | Tablero o asiento | Vuelo |
   |---|---|---|---|
   | Mesa | 3 px | 2 px | 1–2 px |
   | Silla | 2 px | 2 px | — |
   | Banco | 2–3 px | 2 px | 2–3 px |

   Los faldones y travesaños, de 2 px.
4. **Pocos cubos grandes.** Un montón son 5–8 bultos y no 50 piedrecitas; los tres mayores llevan la
   mayor parte. El detalle va en la textura.
5. **Girar lo suelto.** Lo que no está clavado se gira 22,5°:
   - el contenido;
   - las patas abiertas de un taburete;
   - los bultos de un montón;
   - una hoja.
6. **No dibujar caras tapadas** y **no dejar dos caras en el mismo plano**, porque parpadean.

## 5. Lo que se hizo en la 5.2.0

Se aplicaron las reglas a las 367 piezas medievales:

- **Una pasada de limpieza a cada pieza:**
  - todas las coordenadas al píxel entero, sin abrir huecos entre partes que se tocaban;
  - las láminas finas pegadas a otra cosa (aros, bandas, etiquetas, la superficie de lo que contiene un
    recipiente) pasan a **planos**;
  - las partes que asomaban menos de un píxel vuelven a asomar medio píxel o uno;
  - las caras que habrían parpadeado se separan.
- **Muebles rehechos con las medidas del estudio:**
  - mesas de granjero, herrero, pescadero, armero y tasador: patas de 3, tablero de 2 que sobresale 1,
    faldones de 2;
  - sillas: patas, asiento y respaldo de 2;
  - bancos;
  - taburetes con las patas abiertas a 22,5°;
  - candelabro.
- **Montones de mineral:**
  - unos pocos bultos grandes, uno de cada tres girado;
  - de 72 cubos de mediana a **26**;
  - los tres mayores pasan de llevar el 34 % al 49 % del volumen.
- **En total:**
  - 9.617 cubos pasan a **8.587**;
  - las caras tapadas bajan del 28 % al **19 %**;
  - las coordenadas enteras suben del 42 % al **57 %**.
- **De paso se corrigieron parpadeos que ya tenía la 5.1:**
  - el metal fundido de los moldes;
  - el costado del cofre pirata;
  - la etiqueta de la botella;
  - las manzanas de la caja.

**Lo que no se cambió, a propósito:**

- **Las piezas redondas** se dejaron como estaban:
  - barriles;
  - cubos;
  - cestas;
  - botellas;
  - el cuerpo de los sacos.

  Su forma se hace con discos y aros que encajan al cuarto de píxel, y al redondearlos al píxel se
  rompían: aparecían huecos y aros enterrados. Por eso los barriles siguen con aros de 0,3 px y la cifra
  de cubos finos solo baja del 25 % al 18 %. Rehacerlos como las referencias (un bloque de cuerpo más
  aros pintados o en planos) es un cambio de diseño mayor, que se hizo en la 5.3 (abajo).
- **Los acentos pequeños** (llamas, clavos, ojos, menos de 2 px) y **los cubos ya girados** se dejan
  como se dibujaron.
- **No se tocó nada que cambie cómo se usa una pieza:**
  - ids;
  - tamaños en bloques;
  - asientos;
  - interacciones;
  - el YAML.

Cada pieza se comparó antes y después en vistas renderizadas, las 367, y se corrigió lo que empeoraba:

- lingotes que se juntaban;
- el hombro abierto de la lechera;
- la boca de la regadera enterrada;
- el agua de los barriles fuera de sitio;
- la etiqueta de la botella.

## 6. 5.3: las piezas redondas, como las de las referencias

Las 58 piezas redondas (barriles, toneles, cubos, pilas, lecheras, cestas, sacos, la botella y las
jarras) se rehicieron con la receta de las referencias en vez de con discos octogonales:

- **El cuerpo es un solo bloque cuadrado**, tan ancho como llegaba el octógono: lo que se apoyaba en su
  costado (la placa del barril de pólvora, el asa de la jarra, la etiqueta de la botella) sigue tocándolo
  donde lo tocaba.
- **Cada aro es una banda entera** que rodea el cuerpo a medio píxel de él, como la carcasa del barril de
  la mazmorra o los planos del cubo de madera; antes eran cuatro tiras con las esquinas abiertas.
- **El tonel tumbado** es el mismo cuerpo girado, con sus bandas enteras.
- **Las cestas** son un bloque sobre un pie más estrecho, con un borde de cuatro listones que se abren
  hacia fuera 22,5° (como la cesta de maíz), el contenido por encima del borde y un asa de tres barras.
- Un recipiente abierto (un barril sin tapa, un cubo) no deja ningún escalón sin tapa entre el cuerpo y
  el borde: por ahí se veía el agua desde el costado.

| Las 58 piezas | 5.2 | 5.3 |
|---|---|---|
| Cubos en total | 2.066 | **1.105** |
| Cubos por pieza (mediana) | 33 | **15** |
| Píxeles de textura | 174.592 | **127.744** |

Nada más cambia: ni ids, ni tamaños en bloques, ni asientos, ni interacciones, ni el YAML; el resto de las
367 piezas sale idéntico, byte a byte. Un servidor que tenga los ficheros de la 5.2 sin tocar los recibe
nuevos al arrancar (comprobado: 116 ficheros, en un Paper 26.3 con un cliente 26.3 real mirándolos).

