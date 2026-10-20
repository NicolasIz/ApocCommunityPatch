# Armaduras profesionales: forma, diseño y cómo construirlas

Esta guía sale de estudiar las armaduras de dos fuentes. La primera es `texturas_y_cosas_asi.zip`
(UNIQUE ARMOR VOL.2, Ares, Duelist, Fiend Skull, Japan Armor, Star Crown, Arthur). La segunda es el pack
de ItemsAdder en dos partes (`generated_4`): MythicArmor (Cleric T1-T5, Darksteel Knight, Mythical
Dragon), elitecreatures (Valkyrie, Crimson Empress), `set_dragon` y los 20 juegos de `dragones_epicos`.

Se miraron así:

- **31 juegos de capas 2D**, medidos píxel a píxel: cobertura de cada cara, capa del sombrero, colores,
  valores y familias de tono.
- **7 yelmos 3D** medidos puestos en la cabeza (los 70 modelos de yelmo, en el estudio de forma).
- **8 armaduras 3D** completas, leídas desde sus `.bbmodel`: los 7 modelos de MythicArmor y el Duelist.
  Se renderizaron de frente, 3/4, lado y espalda.
- **14 juegos** vestidos por bots en el servidor de prueba y fotografiados con un cliente 1.21.8 real.

Las láminas de referencia (`lamina_3d.png`, `lamina_yelmos.png` y la galería en el juego) muestran el
arte de packs comprados. Por eso se entregan aparte y no se suben a este repositorio.

Esta guía completa `perfil-estetico.md`: el perfil dice cómo se pinta y esta guía dice qué forma tiene
una armadura.

## 1. Las tres técnicas

| Técnica | Qué es | Quién la usa | Qué hace falta |
|---|---|---|---|
| **A. Capas 2D** | La textura `humanoid` / `humanoid_leggings` pegada al cuerpo, como la armadura vanilla | Valkyrie, Crimson Empress, `set_dragon`, `dragones_epicos`, Arthur | Solo ArkcronistContent. Se ve en Bedrock (attachables). |
| **B. Capas 2D + yelmo 3D** | El cuerpo en capas y la cabeza como modelo de ítem | UNIQUE ARMOR, Ares, Japan Armor, Star Crown, Fiend Skull | Solo ArkcronistContent (`resource.model` + `equipment.slot: HEAD`). Se ve en Bedrock. |
| **C. Armadura 3D completa** | Un `.bbmodel` que MythicArmor convierte en geometría dibujada por un shader (objmc) | Cleric T1-T5, Darksteel, Mythical Dragon | El plugin MythicArmor. Cliente Java con tarjeta gráfica. Lejos de 32 bloques se ve la capa 2D de respaldo. |

Lo que se comprobó de la técnica C:

- El shader necesita `GL_KHR_shader_subgroup_quad` en el *vertex shader*. Las tarjetas gráficas reales
  lo tienen.
- El cliente de prueba del servidor (render por software: llvmpipe, ni con zink) no lo tiene, y allí el
  cuerpo 3D no se dibuja. Por eso las armaduras C se estudian renderizando el `.bbmodel`.
- Cada armadura C trae dentro sus capas `_layer_1` / `_layer_2`, que se ven a distancia.
- La técnica C en Bedrock no se ha probado.

Elección por defecto: **B** para un juego normal y **C** para el juego estrella de un servidor que ya
tiene MythicArmor. **A** solo si el juego es de tela o cuero, y sin yelmo voluminoso.

## 2. Silueta: lo que separa una armadura profesional

La armadura vanilla no cambia la silueta del jugador. Las profesionales sí, y siempre de la misma
manera: hombros más anchos, algo que sube sobre la cabeza y algo que cuelga bajo la cintura.

### Medidas de las armaduras 3D

Unidades de modelo: el jugador mide 32 de alto y sus hombros llegan a ±8.

| Armadura | Hombros (semiancho) | Sobre la cabeza | Caderas (semiancho) | Lo que cuelga |
|---|---|---|---|---|
| Duelist | 10,4 (×1,30) | +2,0 (ala del sombrero, ±10 de ancho) | 9,9 | falda acampanada |
| Cleric T1 | 10,9 (×1,37) | +2,8 (capucha) | 9,4 | faldones de cadera, capa hasta el suelo |
| Cleric T5 | 12,4 (×1,55) | +7,9 (mitra) | 9,6 | tabardo delantero, capa hasta el suelo |
| Darksteel | 12,5 (×1,56) | (yelmo aparte) | 8,8 | faldones |
| Mythical Dragon | 15,9 (×1,99) | +2,3 | 8,5 | (nada: las púas hacen la silueta) |

### Medidas de los yelmos 3D puestos en la cabeza

La cabeza ocupa x/z ±4 e y 24-32.

| Yelmo | Piezas | Giradas | Planos | `display.head` | Sobre la cabeza | Por debajo | Ancho |
|---|---|---|---|---|---|---|---|
| UNIQUE armor5 (cuernos) | 14 | 8 | 0 | ×1,84, y +7,25 | +3,1 | hasta y 21,0 | ±6,9 |
| UNIQUE armor7 (sombrero con plumas) | 29 | 19 | 0 | ×1,84, y +7,25 | +5,3 | hasta y 20,8 | ±5,8 |
| Star Crown (corona de rayos) | 31 | 26 | 7 | ×0,95 | +8,8 | hasta y 22,1 | ±8,1 |
| Red Samurai (kuwagata + shikoro) | 25 | 0 | 21 | ×1,57, y +4,75 | +10,3 | hasta y 17,1 | ±9,3 |
| Ares (cresta) | 23 | 11 | 3 | ×1,5, y +3 | +13,7 | hasta y 21,2 | ±5,4 |
| Fiend Skull (calavera) | 14 | 0 | 0 | ×1,98 | +0,6 | hasta y 22,4 | ±4,9 |
| *Centinela (el mío)* | *11* | *5* | *0* | *ninguno* | *+5,0 (solo un cristal)* | *hasta y 23* | *±5,5 (alas invisibles de frente)* |

### Reglas de silueta

1. **Un «gancho»**: un elemento que identifica el juego desde lejos. Pueden ser cuernos, una cresta, una
   corona de rayos, el ala de un sombrero, una capa o púas. Sobresale **+3 a +14** sobre la cabeza, o
   **×1,3 a ×2** en los hombros.
2. **Hombros anchos**: hombreras de 1,3× a 1,6× el ancho vanilla, de varias láminas en escalera, con
   bordes de otro color.
3. **El yelmo baja**: llega 2 a 7 unidades por debajo de la cabeza (protector de nuca y carrilleras).
   Así se asienta sobre los hombros en vez de flotar como una caja.
4. **Algo cuelga**: faldones, tabardo o capa rompen la línea recta de las piernas. En 3D la capa se
   separa de la espalda (z hasta 12-14) y llega al suelo.
5. **La espalda se diseña**: una capa con emblema (la cruz del Cleric T3, el rombo del T5). No es una
   copia del frente.
6. **Simetría**: casi todo es simétrico. La asimetría se reserva para un detalle (una hombrera mayor,
   una cinta cruzada).
7. **Las mejoras conservan la base**: del Cleric T1 al T5 la base es la misma y cada nivel añade piezas
   (1038 → 1242 caras). La altura crece de +2,8 a +7,9 y los hombros de ×1,37 a ×1,55. Un jugador
   reconoce el nivel por la silueta.

## 3. Construcción

### Yelmos (modelo de ítem, técnicas B y C)

- **Se modela a tamaño de cabeza** dentro del espacio 0-16. Se agranda con `display.head`: `scale`
  entre 1,5 y 1,98, y `translation` y entre +3 y +7,25 para subirlo a la cabeza.
- El cliente dibuja un ítem de cabeza a 0,625. Sin `display.head`, un cubo de 16 queda en 10 unidades:
  solo 1 por lado más que la cabeza. Fue el error del Centinela.
- **Cuernos, crestas, plumas, rayos y láminas del cuello van como planos** (grosor 0, una o dos caras).
  El Red Samurai usa 21 planos de 25 piezas. Un plano visto de canto desaparece, así que se gira para
  que se vea de frente y de 3/4.
- **Muchas piezas giradas**. En Java los giros son de 22,5° en un eje: 19 de 29 en armor7, 26 de 31 en
  Star Crown. En `.bbmodel` (MythicArmor) los ángulos son libres (7,5°, 12,5°, 17,5°…).
- **Una cara con carácter**: la visera en T de Ares, la máscara del samurái, los dientes de la calavera,
  la cara abierta enmarcada de los clérigos.

### Cuerpo 3D (técnica C, `.bbmodel` para MythicArmor)

Formato: Blockbench *free*, textura de 128×128 o 256×256 con **1 texel por unidad** (la densidad
vanilla). Huesos y pivotes iguales a los del jugador:

| Hueso (nombres usados) | Pivote |
|---|---|
| `head` | [0, 24, 0] |
| `body` / `torso`; `waist` / `hips` (parte de las grebas) | [0, 24, 0] (en Cleric, `wiast` es un contenedor de todo en [0, 12, 0]) |
| `right_arm` / `left_arm` | [±5, 22, 0] |
| `right_leg` / `left_leg` | [±1,9 o ±2, 12, 0] |
| `right_foot` / `right_feet` / `right_boot` (y los izquierdos) | [±1,9 o ±2, 12, 0] |

- Las láminas miden **1 unidad de grueso**: del 54 al 73 % de las piezas en los clérigos y el 64 % en
  el Duelist. También hay planos sueltos para capas, plumas y cintas: de 11 a 48 por modelo en los
  clérigos y 22 en el Duelist.
- Del 52 al 88 % de las piezas van giradas, casi siempre con ángulos pequeños (hasta 25° en el 56-93 %
  de los giros). Con eso las láminas se solapan como escamas en vez de verse como cajas apiladas.
- El número de piezas no hace la calidad: el Darksteel tiene solo 21 y se lee perfectamente por sus
  hombreras en ala. Los clérigos tienen de 173 a 207.
- Las hombreras y los faldones van en un subgrupo con su propio pivote (en Cleric, `group2` a `group5`).
  Así se inclinan unos grados (2,5° en las caderas) sin mover el brazo.
- Dentro del modelo van también las capas `<nombre>_layer_1` (128×64) y `_layer_2`. Son el respaldo
  lejano y lo que se ve sin shader.
- MythicArmor admite animaciones en el `.bbmodel` (el Mythical Dragon trae dos).

### Capas 2D (técnicas A y B)

| | Gráficos (pocos colores) | Pintados en HD |
|---|---|---|
| Ejemplos | Ares 15, Arthur 7, Fiend 12, Star Crown 42, `dragones_epicos` 14 colores | Japan Armor 250-2090, `set_dragon` 22 361 colores |
| Tamaño | 64×32 o 128×64 | 128×64 (`set_dragon` 512×256) |
| Resultado | Lectura clara a distancia, aspecto «pixel art» | Textura rica de cerca, más ruido de lejos |

Cada juego es de una escuela o de la otra, nunca mezcla. El perfil estético de la casa (mediana de 14
colores, rampas de 4-5) es la escuela gráfica.

- **Espacio negativo**: 8 de los 18 juegos gráficos dejan el pecho cubierto solo al 44-75 % (UNIQUE
  armor5-8, Ares, Fiend Skull, Star Crown y Arthur). El resto se deja transparente para que se vean el
  cuerpo, la ropa y las correas. Así parece armadura puesta y no un pijama pintado. Los otros 10
  (los 6 dragones, Valkyrie, Crimson Empress y las capas de respaldo de Darksteel y Mythical Dragon)
  cubren el 79-100 % y sacan la forma de escamas y solapes pintados. Los juegos HD cubren el 95-100 %.
- **La capa del sombrero** (la cáscara exterior de la cabeza) la usan los juegos sin yelmo 3D: Valkyrie
  42 %, Crimson Empress 52 %, UNIQUE armor6 72 %. Da volumen a la cabeza sin modelo.
- **Botas**: en los juegos gráficos cubren el 22-67 % de su frente. Suelen ser una caña con puntera y
  un borde de color.

## 4. Color y valor (medido en las capas)

- **Una familia dominante (40-60 %) y un acento (10-40 %)**. Ejemplos: Star Crown violeta 51 % y
  naranja 39 %; Ares rojo 46 % y naranja 39 %; Cleric T5 azur 42 % y naranja 16 %; Mythical Dragon
  rojo 55 % y rosa 33 %.
- Lo más común es metal frío y acento cálido, o al revés: oro sobre azul, oro sobre violeta, oro sobre
  rojo.
- **Gris, con medida**: en los juegos gráficos de color el gris no pasa del ~40 % (Ares 14 %, Star Crown
  0 %, Mythical Dragon 0 %). Los juegos casi monocromos (Darksteel 99 %, `nocturnis` 100 %, los Japan oscuros
  71-95 %) existen, pero confían toda la lectura a la forma: hombreras en ala, yelmo 3D o textura HD.
  El Centinela tiene un 53 % de gris sin tener esa forma.
- **Rango de valor amplio**: del 5 al 95 % del brillo va de ~0,3 a ~1,0 en los juegos «brillantes»
  (Ares, Star Crown, Cleric T5). Los juegos oscuros (UNIQUE armor7, 0,11-0,33) dejan la silueta al
  yelmo 3D, porque la textura sola no se lee.
- **Tres masas de valor** que se leen a 10 bloques: oscuro, medio y claro. En el Cleric T3 son la capa
  azul noche, las placas azules y las cruces de oro. Los detalles de 1 píxel (remaches, juntas) se
  pierden de lejos y no deben cargar el diseño.
- **Un motivo repetido en 3 o más sitios**: el rombo del Cleric (mitra, pecho, hombros, rodillas), la
  cruz (pecho y capa), la V del dragón (pecho y botas).

## 5. Crítica honesta del Centinela del Ocaso

El Centinela siguió bien el perfil de pintura: densidad 2× constante, rampas cortas, contorno selectivo
y sin tramado. Pero no tiene forma de armadura profesional:

1. **El yelmo es una caja.** Es un cubo de 16 sin `display.head`, que queda en 10 unidades sobre una
   cabeza de 8, y no baja de la cabeza (se queda en y 23). Las alas son láminas giradas solo en el eje
   x: de frente se ven de canto y desaparecen, y de lado son un manchón pequeño. El cristal de la cresta
   (1,6 × 3,2) no se lee a distancia.
2. **No tiene gancho.** Nada sube de verdad sobre la cabeza ni ensancha los hombros. Su silueta es la de
   la armadura vanilla.
3. **Cubre todo.** El pecho, la espalda y los brazos están pintados al 100 %, sin espacio negativo. Es
   una carcasa uniforme.
4. **Pocas masas de valor.** Acero gris oscuro con hilos de oro de 1 píxel y un acento del 5,6 % (los
   profesionales usan del 10 al 40 %). De lejos es un bloque gris.
5. **Los motivos son pequeños.** Cristales de 2×2 y remaches de 1 píxel. No hay un emblema grande ni un
   motivo repetido que se reconozca.
6. **La espalda es plana.** La capa está pintada sobre la espalda, sin volumen.

## 6. Receta para la próxima armadura

1. **Concepto en una frase y un gancho.** Ejemplo: «templario del ocaso: mitra con halo de rayos, capa
   partida, rombo de cristal». El gancho decide la silueta.
2. **Técnica**: B por defecto, C si va a MythicArmor (el `.bbmodel` se entrega listo para
   `plugins/MythicArmor/models/`).
3. **Silueta primero**, en negro sobre blanco, de frente y de lado. Comprobar las reglas de §2: gancho
   de +6 a +12 o hombros ×1,4-1,6, yelmo que baja 3-5, algo que cuelga y espalda diseñada.
4. **Valor y color**: tres masas, una familia dominante del 40-60 % y un acento del 15-35 %. Un motivo
   en 3 o más sitios.
5. **Modelo**:
   - Yelmo a tamaño de cabeza, `display.head` con escala ~1,6 y y ~+4, 20-30 piezas, la mitad giradas,
     planos para lo fino.
   - Cuerpo 3D con láminas de 1 de grueso, giros pequeños y subgrupos para hombreras y faldones.
6. **Pintura** según `perfil-estetico.md` (rampas de 4-5, contorno selectivo, sin tramado), con
   espacio negativo en las capas 2D.
7. **Respaldo e iconos**: las capas 2D (las de lejos en la técnica C) y los iconos de inventario de
   32×32.
8. **Verificación**:
   - render de frente, 3/4, lado y espalda con `tools/bbrender.py`, que dibuja `.bbmodel` y modelos Java
     (con `--head`, un yelmo puesto) sobre un maniquí, sin tarjeta gráfica;
   - fotos en el juego a 3, 8 y 16 bloques;
   - medir con las mismas cifras de las tablas de §2 y §4.
