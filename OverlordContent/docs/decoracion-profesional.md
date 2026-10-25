# Decoración profesional: paleta, pintura y forma de los muebles y objetos

Esta guía sale de estudiar a fondo la decoración comprada que acompaña al servidor:

- Del zip `texturas_y_cosas_asi.zip`:
  - **W6 Benches**: 3 bancos.
  - **Farmer Decoration**: 20 piezas de granja.
  - **3D Food Pack**: 42 alimentos. Viene en RAR; ahora se ha leído entero con `unrar`.
- Del pack de ItemsAdder del servidor (`generated_4`), los packs de decoración:
  - `medieval_rpg` (33) y `dungeon_furniture` (27);
  - `must_have_props` (38) y `workshop_six` (32, cocina y taberna);
  - `crafting_stations` (6) y `omstore` (29);
  - `fischvogel` (50: ventanas y carteles), `food_pack_v1` (18) y `door_pack` (4).

En total son **302 modelos de referencia**, medidos con el mismo programa que las **367 piezas
medievales nuestras**: `medieval_props`, `medieval_herreria`, `medieval_mineria`, `medieval_pesca` y
`medieval_granja`. Las texturas protegidas por ItemsAdder se leyeron con el lector del propio plugin.

Las láminas de render que acompañan el estudio muestran arte comprado. Por eso se entregan aparte y no
se suben a este repositorio, igual que con las armaduras.

Esta guía completa `perfil-estetico.md` (cómo se pinta en general) y `armaduras-profesionales.md`
(la forma de una armadura).

## 1. Lo que se midió

| | Referencias (302) | Nuestras (367) |
|---|---|---|
| Cubos por pieza (p25 / mediana / p75 / p90) | 5 / **9** / 14 / 23 | 13 / **22** / 41 / 60 |
| Cubos girados | **39 %** (22,5° y 45°, en los tres ejes) | 4 % |
| Piezas con algún cubo girado | **67 %** | 19 % |
| Planos (grosor 0) | **26 %** de los cubos; el 63 % de las piezas tiene alguno | 0 % |
| Densidad (píxeles por 1/16 de bloque) | 1× (Farmer, a 2×) | 1× |
| Colores por textura (mediana) | **15** | 190 |
| Colores en todo un pack | **47** (`workshop_six`), **66** (`fischvogel`), **80** (`dungeon_furniture`) | de 2.900 a 11.700 |
| Vecinos de distinto color | **47 %**: hay zonas planas | 93 %: ruido en toda la superficie |
| Salto de luminosidad entre vecinos distintos (L*) | **8,5**: pasos de rampa | 4,8: ruido |
| Luminosidad p5–p95 (L*) | 26–80 | 12–72: más oscuras |

Por tipo de objeto (mediana de cubos; entre paréntesis, girados y planos):

| Tipo | Referencias | Nuestras |
|---|---|---|
| Mesa | 10 (31 % · 6 %) | 14 (2 % · 0) |
| Silla o banco | 10 (22 % · 14 %) | 14 (10 % · 0) |
| Caja, cesta o saco | **9** (33 % · 38 %) | **50** (2 % · 0) |
| Barril | 11 (13 % · 0) | 34 (5 % · 0) |
| Planta o flor | 8 (**71 % · 77 %**) | 24 (0 · 0) |
| Estante o armario | 14 (39 % · 7 %) | 16 (0 · 0) |
| Montón o pila | 12 (46 % · 25 %) | 25 (4 % · 0) |

## 2. La paleta es del pack, no de la pieza

Es el hallazgo principal. Un pack profesional usa **una sola paleta cerrada para todas sus piezas**.
Cada material tiene su rampa fija y todas las piezas la comparten. Así las piezas de un mismo pack se
ven de la misma familia aunque se pongan juntas.

- `workshop_six`: 29 texturas y 47 colores en total.
  - **Madera**: 6 tonos, `#341e1e` `#4e332d` `#684c3c` `#80624a` `#997a58` `#ab936d`.
    - L* sube 14 → 24 → 35 → 44 → 53 → 62, en pasos de ~10.
    - La saturación es constante (0,27).
    - El tono gira de 0° en la sombra a 37° en la luz: de rojo a amarillo.
  - **Piedra y hierro**: un gris neutro de 9 tonos, de L* 6 a 66, en pasos de 8–10.
  - **Blancos de cristal y loza**: gris frío (tono 185–190°).
- `dungeon_furniture`: 24 texturas y 80 colores.
  - **Madera**: rampa de 5 tonos, de L* 23 a 51, saturación 0,26, tono de 354° a 20°. Hay una variante
    más saturada (0,31–0,34) para la madera roja.
  - **Metal**: azul acero de 6 tonos (tono 207–218°).
  - **Tela y hueso**: blanco violáceo (tono 234–270°, saturación 0,12).
  - La mesa entera usa **5 colores**; el barril, 9.

Reglas de rampa medidas:

1. **4 a 6 tonos por material**, con pasos de L* de **6 a 10**.
2. **Saturación constante** a lo largo de la rampa; si cambia, baja un poco en la luz.
3. **El tono gira con la luz**:
   - la madera, del rojo en la sombra al amarillo en la luz (+5° a +7° por paso);
   - el metal y la piedra, neutros o algo azules;
   - los blancos, fríos.
4. **Nada de negro puro ni de blanco puro** en superficies. El tono más oscuro de la rampa hace de
   línea.

## 3. Cómo se pinta cada material

Las referencias no tienen ruido. Cada píxel distinto de su vecino está puesto a propósito: una
costura, una veta, un brillo o un clavo. Tampoco pintan luz y sombra en cada cara (eso ya lo hace
Minecraft); marcan la **estructura**.

**Madera en tablas.** Hay dos escuelas y las dos sirven.

- **Rayada** (`dungeon_furniture`):
  - Tablas de 5 a 7 px de ancho, separadas por una costura del tono más oscuro.
  - Dentro, vetas de 1 px en dos tonos alternos, rectas y continuas a lo largo de la tabla.
  - La tabla es **un tono más clara en su tramo central** y un tono más oscura en sus extremos.
- **Plana** (`workshop_six`):
  - Tablas de un solo tono, con una línea de luz de 1 px arriba y la costura oscura abajo.
  - Algún nudo o clavo de 1–2 px, muy espaciado.
  - Los paneles se marcan con un rehundido oscuro y un borde claro.

**Madera de canto** (la tapa de un tronco o de un barril, las patas vistas desde arriba): anillos
concéntricos lisos en 2–3 tonos y un borde (la corteza) del tono más oscuro.

**Postes y patas:**
- Un tono base y una o dos vetas largas de 1 px.
- La arista que queda en sombra lleva una línea más oscura.

**Metal** (aros, herrajes, bisagras):
- Banda lisa, con una fila de luz arriba y una de sombra abajo.
- Remaches de 1 px claros sobre el tono medio.
- El metal pulido lleva un brillo diagonal de 1–2 px, no ruido.

**Piedra y ladrillo:**
- Bloques lisos de 1–2 tonos.
- Juntas del tono oscuro, en hileras al tresbolillo.
- Un píxel de luz en la esquina superior izquierda de cada bloque.

**Tela:**
- Color liso.
- Pliegues como líneas de 1 px un tono más oscuras, espaciadas.
- Dobladillo en el borde.
- La arpillera es un tejido regular de 2×2 en dos tonos vecinos, **siempre el mismo dibujo**, nunca
  al azar.

**Paja y heno:**
- Hebras verticales en 3 tonos que se repiten.
- Un atado más oscuro.
- Si se le da forma, los bordes van como planos sueltos (la paja que sobresale).

**Hojas y plantas:**
- **Planos con recorte alfa**, nunca cubos.
- Dos planos cruzados a 45°, o varios planos girados 22,5° que salen de un tallo.
- Hojas de 3 a 4 tonos de verde con el borde claro.

**Cristal y loza:**
- Opacos, de un tono frío claro.
- Un reflejo vertical de 1 px y el borde un tono más oscuro.

**Comida y contenidos:**
- En una caja o cesta llena, el contenido es **una superficie pintada** a ras del borde, más 2–4
  piezas sueltas encima, algunas giradas.
- No es un montón de cubitos.

**Fuego y velas:**
- Llama como planos cruzados.
- 3 tonos, de amarillo pálido a naranja, y `light_emission` en la llama.

## 4. Forma y composición

1. **Pocos cubos, bien puestos**: 8 a 15 por pieza; más solo en muebles grandes.
2. **Girar para dar vida**:
   - Libros apoyados, la tabla suelta de una pila, la caja de arriba de un montón, una herramienta
     apoyada.
   - Giros de 22,5° (el 68 % de los giros) y de 45°, en cualquier eje.
   - Al menos dos de cada tres piezas tienen algo girado.
3. **Planos para lo fino**: hojas, tela colgante, papel, cuerda, plumas, llamas y paja suelta.
   - Un plano tiene grosor 0 y dos caras.
   - Se gira para no verse de canto.
4. **Grupos con variedad**:
   - Un montón de cajas lleva una girada y una más pequeña.
   - En un grupo de botellas cada una tiene altura y color propios.
   - Los barriles van uno de pie y otro tumbado.
   - Nada se repite idéntico dentro de una pieza.
5. **Lo que sobresale**: tapas que vuelan sobre las patas, cornisas, travesaños y los bordes de un
   tablero. El volumen se lee por el saliente, no por la textura.
6. **Siluetas abiertas**: patas separadas, huecos y estantes con aire. Una caja maciza se lee como un
   bloque, no como un mueble.

## 5. Crítica honesta de nuestras decoraciones

1. **Texturas con ruido.** El pintor de la 1.9.6 suma un azar de ±3 a ±9 a cada píxel. Además
   oscurece sumando RGB, sin girar el tono. Salen 190 colores por pieza y miles por pack. De cerca
   parece tierra y de lejos un borrón marrón.
2. **Sin paleta común.** Cada pieza inventa sus colores. Puestas juntas no parecen del mismo juego.
3. **Demasiado oscuras.** El nogal oscuro domina los marcos y las patas, y el brillo no pasa de L* 72.
   La luz está en el 80 de las referencias.
4. **Bisel en todas las caras.** Una luz arriba a la izquierda y una sombra abajo a la derecha en cada
   cara, que se suman al sombreado del juego y abultan las piezas.
5. **Todo recto y centrado.** Solo el 4 % de los cubos va girado. Las herramientas apoyadas son la
   excepción.
6. **Ni un plano.** Plantas, cultivos, paja, llamas y cristales son cubos.
7. **Contenidos de cubitos.** Las cajas, cestas y carretillas llenas tienen ~50 cubos de fruta o
   mineral, donde las referencias usan 9.
8. **Plantillas repetidas.** 15 cajas, 13 cestas y 7 sacos son la misma pieza con otro relleno. Eso
   está bien como catálogo, pero hace falta que cada relleno tenga carácter.

Lo que está bien y se conserva:
- La variedad y el número de piezas.
- Las formas reconocibles.
- La densidad 1× uniforme.
- Que cada pieza ocupe exactamente sus bloques (la cuenta de Bedrock).
- Que cada pieza haga algo al usarla.

## 6. Receta para mejorar una pieza (y para las nuevas)

1. **Paleta de la casa**: cada material sale de una rampa fija de 4–6 tonos (§2). Ningún color fuera
   de la paleta.
2. **Pintura estructural** (§3):
   - costuras, vetas, juntas, remaches y pliegues;
   - sin ruido y sin bisel por cara;
   - las caras de abajo, un tono más oscuras.
3. **Forma** (§4):
   - quitar cubos que no se ven;
   - contenidos como superficie pintada y unas pocas piezas giradas;
   - plantas y cosas finas como planos;
   - girar lo suelto 22,5°.
4. **Comprobar con números**, con la misma medición del estudio:
   - colores por textura cerca de 15–30;
   - los vecinos distintos por debajo del 60 %;
   - saltos de L* de 6–10;
   - L* p95 hacia 80;
   - al menos la mitad de las piezas con algo girado o un plano.
5. **Comprobar a la vista**: lámina de render junto a las referencias, y fotos en el juego a 3 y 8
   bloques.

## 7. Aplicación: las 367 piezas medievales en la 5.1

Se aplicó la guía a todas las piezas sin cambiar sus nombres, los bloques que ocupan, lo que hacen
ni sus YAML:

- **Un pintor nuevo** con la paleta de la casa: cada material es una rampa de cinco tonos y la pintura
  es solo estructural (§2 y §3).
- **Los contenidos**: una superficie pintada a ras del borde y unas pocas piezas encima.
- **Las plantas**, como planos cruzados.
- **Lo suelto**, girado 22,5°.

| | Antes | Ahora | Referencias |
|---|---|---|---|
| Colores por textura (mediana) | 190 | **14** | 15 |
| Colores en todo un pack | 2.900 a 11.000 | **149 a 277** | 47 a 80 (packs de 24-40 piezas) |
| Vecinos de distinto color | 93 % | **45 %** | 47 % |
| Salto de L* entre vecinos distintos | 4,8 | **9,5** | 8,5 |
| Cubos por pieza (mediana / p75 / p90) | 22 / 41 / 60 | **21 / 35 / 57** | 9 / 14 / 23 |
| Cubos de una caja llena de la granja | ~50 | **~19-30** | 9 |
| Piezas con algún cubo girado o un plano | 19 % | **55 %** | 67 % |
| Luminosidad p5-p95 (L*) | 12-72 | 24-72 | 26-80 |

Lo aprendido al hacerlo:

- **El ruido no se arregla bajándolo, se arregla quitándolo.** Con el azar en cada píxel una
  textura sale con 190 colores aunque la rampa sea buena. Sin azar salen 14.
- **Una cara grande de canto no son anillos.** Los anillos concéntricos solo leen bien en un extremo
  pequeño (la tapa de una pata, la de un tronco). En la tapa de una mesa o el lateral de una caja se
  ven como una diana, y ahí van tablas.
- **Una rampa sale de un color, no de tres.** Si la «luz» de un material es otro color (la pátina
  verde del cobre, el morado del nabo), la mezcla parece musgo o un mantel. La rampa se hace con el
  color propio y el otro va como acento en pocos píxeles.
- **Un patrón de 1 píxel se ve como ruido.** La cuerda trenzada en diagonal de 1 px y la arpillera en
  damero parecían estática. A 4 px la cuerda se lee, y la arpillera funciona casi lisa con rayitas
  de hilo cada tres filas.
- **Girar no se puede recortar.** En Java, una pieza girada que sobresale de su bloque no se puede
  cortar al borde. Lo que cruzaría el techo del bloque se deja recto.
- **Qué se gira.** Se giran los grupos pequeños que descansan sobre algo al menos el doble de grande y
  no tienen nada encima. Con esa regla no se tuercen patas, tejados ni tapas, y aun así las piezas
  con algo girado pasan del 19 % al 55 %.

Lo que queda por debajo de las referencias:

- **Siguen teniendo más cubos.** Las piezas grandes y las de 3.5 (vagonetas, redes, montones de
  mineral) tienen más del doble que las referencias. Reducirlas pide rehacer su forma una a una.
- **La luz.** Las referencias llegan a L* 80 en lo más claro; nosotros a 72.
