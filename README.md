# ArkcronistGenerator

Generador de mundos procedural para **Paper / Purpur 1.21.8 – 1.21.11**, Java 21, en un único JAR
sin dependencias externas y sin NMS.

No es un fork de nada: es un proyecto propio que estudia las buenas ideas de
[Terra](https://github.com/PolyhedralDev/Terra) (MIT) y
[TerraformGenerator](https://github.com/Hex27/TerraformGenerator) (Apache-2.0) y las lleva más lejos
en los puntos que importan para este servidor: océanos de verdad, cordilleras reales, cuevas con
sentido, estructuras que se adaptan al relieve y minibosses. Ver [NOTICE.md](NOTICE.md).

```
/ag                     comando principal
-g ArkcronistGenerator            preset por defecto (config.yml)
-g ArkcronistGenerator:BASE       mundo natural, variado y jugable
-g ArkcronistGenerator:CHAOTIC    mundo accidentado: cordilleras, cañones, islas, biomas fragmentados
-g ArkcronistGenerator:INSANE     mundo extremo: picos gigantes, arcos, salientes, islas flotantes, megacuevas, fosas
```

---

## 1. Qué genera

### Terreno

| Sistema | Qué hace |
|---|---|
| Continentes | Un campo de muy baja frecuencia con deformación de dominio decide tierra o mar. Los continentes miden miles de bloques. |
| Costas | Transición continua entre el fondo marino y la tierra, con rugosidad propia: bahías, entrantes, penínsulas. |
| Plataforma continental | Escalón somero pegado a la costa antes de que el fondo se hunda. |
| Talud continental | Caída larga desde la plataforma hasta la llanura abisal. |
| Llanura abisal | Fondo profundo con ondulación propia. En BASE se pasa de 100 bloques de profundidad. |
| Fosas oceánicas | Grietas estrechas y muy profundas, solo mar adentro. |
| Montañas submarinas | Relieve montañoso bajo el agua que a veces emerge como isla. |
| Cordilleras | Ruido *ridged* con máscara propia: las montañas forman cinturones, no una manta uniforme. |
| Mesetas | Aterrazado selectivo del relieve. |
| Cañones | Sustracción estrecha y profunda siguiendo una red deformada. |
| Acantilados | Bandas de aterrazado que producen repisas y escalones en pendientes fuertes. |
| Ríos | Red de valles deformada que se desvanece con la altitud (los ríos altos quedan como gargantas secas). |
| Lagos | Cuencas celulares con **nivel de agua propio**, no atados al nivel del mar. |
| Erosión simulada | Erosión térmica con talud límite y sedimentación, calculada sobre la rejilla compartida. |
| Estratos geológicos | Bandas horizontales deformadas por ruido, con "provincias" que comparten secuencia de roca. |
| Salientes y arcos | Campo 3D alrededor de la superficie: repisas, cornisas y arcos naturales. |
| Islas flotantes | Masas lenticulares agrupadas por ruido celular (CHAOTIC e INSANE). |
| Cuevas | Cuatro sistemas de excavación (túneles, cuevas *cheese*, cavernas y megacuevas) más biomas de cueva, acuíferos y decoración. |
| Acuíferos | Cada región tiene su propia capa freática: hay cuevas inundadas, lagos subterráneos y cuevas secas. La lava se queda en pozas junto a la bedrock, no en un océano. |
| Menas | Campo 3D de vetas + hash por bloque: depósitos con forma, sesgados por bioma. |

Los océanos son obligatoriamente reales: **costa → plataforma → talud → llanura abisal → fosa**, con
montañas submarinas encima. Medido sobre 30.000 columnas por preset:

| Preset | Profundidad media en abisal | Punto más profundo | Pico más alto |
|---|---|---|---|
| BASE | ~55 bloques | ~104 bloques bajo el mar | y ≈ 180 |
| CHAOTIC | ~70 bloques | ~113 bloques bajo el mar | y ≈ 244 |
| INSANE | ~90 bloques | ~121 bloques bajo el mar | y ≈ 306 |

### Clima y biomas

Los biomas se eligen por un mapa climático de tres campos: temperatura, humedad y rareza. **La
temperatura se mantiene deliberadamente más suave que los otros dos**, y esa es la diferencia entre
un mundo con estaciones y un mundo a cuadros.

El motivo es de juego, no estético: en Minecraft la nieve y el hielo se derriten si el bioma **de ese
bloque exacto** es lo bastante cálido. Con el mapa térmico troceado, un nevero acababa pegado a un
prado, la nieve del lado cálido se derretía al primer tick aleatorio y quedaban esos parches pelados
de tierra y flores en medio del hielo. Ahora la temperatura usa su propio campo, con una longitud de
onda mayor y una deformación de dominio mucho menor, y la fragmentación solo se aplica a humedad y
rareza — así un bosque, un pantano y un prado pueden alternar dentro de una misma franja térmica,
pero un desierto no aparece junto a un glaciar.

Medido sobre 15.600 columnas por preset, comparando con la 1.5:

| | tamaño medio de bioma | frío pegado a cálido | terreno llano |
|---|---|---|---|
| BASE | 60 → **74** bloques | 1,0% → **0,6%** | 51% → **59%** |
| CHAOTIC | 43 → **63** bloques | 0,5% → **0,5%** | 26% → **45%** |
| INSANE | 30 → **56** bloques | 1,4% → **0,3%** | 11% → **26%** |

Hay una prueba automatizada que falla si más del 4% del suelo helado toca suelo cálido.

Unos 34 biomas, cada uno con identidad propia en **relieve, altura, clima, vegetación, árboles,
materiales, minerales, cuevas, estructuras y densidad de decoración**. La selección es una búsqueda
suave por distancia sobre continentalidad, gradiente oceánico, factor de montaña, altitud,
temperatura, humedad y "rareza", así que las fronteras se mueven con el terreno y las transiciones
son naturales, no líneas rectas.

Dos biomas pueden compartir clave vanilla y verse completamente distintos: la clave vanilla solo
controla color de hierba, niebla y spawns naturales; el resto lo decide ArkcronistGenerator.

Bajo tierra el proveedor de biomas cambia a biomas de cueva por regiones, así que el subsuelo tiene
ambiente propio.

### Cuevas: por qué la ancient city salía destrozada

La ciudad **sí** se generaba (la franja `deep_dark` ya llega hasta Y=-13), pero salía en pedazos
colgando dentro de una caverna. La causa es el orden: el servidor escribe los bloques de una
estructura vanilla **sobre el terreno que el excavador ya dejó**. Si ahí había una sala de 40 bloques
de alto, la ciudad queda flotando en el vacío.

En INSANE había `mega-cave-density 0.55` — más de la mitad de las celdas con una sala de 40 bloques
de alto — más `cavern-density 0.78` encima. Nada de eso se parece a vanilla.

Ahora las cuevas están a escala vanilla: salas mega de 40 → 26 bloques de alto y de 0,55 → 0,12 de
densidad, cavernas de 26 → 20 bloques, y el queso más pequeño en los tres presets.

| | hueco bajo tierra (-60..0) | hueco en la franja de la ciudad | columnas con una sala (>80% vacío) |
|---|---|---|---|
| BASE | 19,8% → **8,1%** | 16,7% → **7,9%** | 1,6% → **0,2%** |
| CHAOTIC | 18,8% → **11,2%** | 16,6% → **9,4%** | 1,8% → **0,4%** |
| INSANE | 23,0% → **12,2%** | 19,0% → **9,9%** | 1,8% → **0,3%** |

Un 8-12% de hueco es el rango en el que se mueve una cueva vanilla. Las columnas donde una estructura
quedaría colgando en una sala bajan seis veces.

### Profundidad y agua bajo tierra

`cave-water` pasa a **0**: ninguna capa freática, las cuevas de tierra adentro están secas. El mar no
depende de eso — una cueva bajo el fondo marino se inunda desde el propio mar — así que las cuevas
submarinas siguen con agua.

La bedrock ya estaba en Y=-64, que es el **suelo de la dimensión**: por debajo no hay bloques, hay
vacío. No se puede bajar más sin un datapack que cambie el tipo de dimensión. Lo que sí se ha hecho
es adelgazar la franja irregular de bedrock (`bedrock-roughness` 4 → 2) y bajar el suelo de las
cavernas de -58 a -60:

| | antes | ahora |
|---|---|---|
| bedrock más alta | Y=-60 | **Y=-62** |
| media | — | Y=-62,89 |

Son **2 bloques más** de profundidad minable en todo el mundo.

### Agua en las cuevas, y por qué no salía la ancient city

Las capas freáticas subterráneas no tenían ningún control: **39% de las columnas** llevaban una, con
nivel medio en **Y=-5** y llegando hasta **Y=55**. Eso ahoga las cuevas profundas y lo que el
servidor ponga en ellas — una ancient city ocupa aproximadamente Y=-51 a -20, y el 38% de las
columnas tenía agua por encima de su suelo.

Con `cave-water: 0.10` (por defecto):

| | subsuelo con capa freática | nivel medio | más alto | inunda la banda de la ancient city |
|---|---|---|---|---|
| todos los presets | 39,2% → **1,2%** | -5 → **-48** | 55 → **-44** | 37,8% → **0,9%** |

`cave-water: 0` deja las cuevas completamente secas.

**Pero el agua no era lo único que impedía la ancient city.** El proveedor de biomas solo devolvía
`deep_dark` por debajo de `minY + 32` (Y=-32). El servidor comprueba el bioma a la **altura de inicio
de la estructura**, y una ancient city llega bastante más arriba de esa franja — así que la
comprobación caía sobre dripstone o piedra y **ninguna ciudad podía colocarse jamás**. La franja
ahora llega hasta `minY + 52` (Y=-12), con margen de sobra.

### Agua (una sola superficie en todo el mundo)

Había **tres** fallos encadenados, y por eso el agua salía a distinta altura en distintos chunks:

1. **Los lagos no tenían límite de altura.** Los ríos siempre se desvanecieron con la altitud — por
   eso dejan gargantas secas arriba. Los lagos no tenían nada equivalente, así que se formaban tan
   alto como diera el relieve. Medido en el mundo de pruebas: agua real a **Y=110** con el mar en 63.
2. **El nivel del lago se sacaba del centro de su cuenca**, un número distinto del nivel del mar.
3. **La rejilla interpolaba entre ambos.** Entre un lago a 69 y el mar a 63, las columnas de en medio
   recibían 64, 65, 66, 67, 68 — *cada una su propio nivel de agua*. Eso son las láminas de agua a
   distintas alturas en la ladera, y por qué cambiaba de un chunk a otro.

El tercero era el fallo de fondo: un nivel de agua **no es una cantidad continua** y no se puede
mezclar. Ahora se toma del nodo más cercano, entero, sin mezclar.

Y por defecto (`water-at-sea-level: true`, que es lo que pediste) hay **una sola superficie de agua
en todo el mundo**. Los lagos siguen excavando su cuenca, pero solo se llenan si su fondo queda bajo
el nivel del mar.

Medido sobre 6000x6000 bloques por preset:

| | agua sobre el nivel del mar | Y más alto | niveles distintos | vecinos a distinta altura |
|---|---|---|---|---|
| BASE | 4,43% → **0%** | 85 → **63** | 7 → **1** | 0,23% → **0%** |
| CHAOTIC | 2,66% → **0%** | 93 → **63** | 4 → **1** | 0,07% → **0%** |
| INSANE | 5,01% → **0%** | 110 → **63** | 6 → **1** | 0,13% → **0%** |

Si algún día quieres lagos de montaña de vuelta, `water-at-sea-level: false` los devuelve — ya
nivelados y con un tope duro de altura (`lake-altitude-fade-end`, 26 bloques sobre el mar). Ese
camino también tiene pruebas.

### Pendientes (por qué se veían como escaleras)

Todas las alturas se calculan sobre una rejilla muestreada **cada 4 bloques** y luego se interpolan.
Eso limita el terreno a detalles de 8 bloques o más: por debajo de eso no existe nada. Medido, una
ladera tenía 0,066 bloques de curvatura contra una pendiente de 0,35 — es decir, era una **rampa
perfectamente recta**. Y una rampa recta hecha de bloques es, literalmente, una escalera. De ahí las
terrazas paralelas en cada cerro.

La corrección añade rugosidad a **resolución de bloque**, después de la interpolación. Está limitada
por pendiente: por debajo de un gradiente de 0,20 el suelo se deja exactamente como estaba, que es lo
que mantiene las explanadas construibles.

| | rectitud de la ladera (curvatura) | terreno llano |
|---|---|---|
| BASE | 0,066 → **0,132** | 45,9% → 42,3% |
| CHAOTIC | 0,074 → **0,140** | 35,3% → 32,4% |
| INSANE | 0,078 → **0,145** | 27,0% → 24,8% |

La amplitud está fijada en 1,0 bloques y **no más**: por encima de eso el buscador de sitios planos
deja de encontrar sitio para los templos, y una familia de estructuras desaparece del mundo. Es un
compromiso real y medido, no un valor elegido a ojo. Ambos lados están cubiertos por pruebas.

Se ajusta con `surface-detail-amplitude` en `config.yml` (0 lo desactiva por completo).

### Superficie

Cada bioma tiene una **paleta de superficie**: una ladera nevada es nieve, hierba y hielo compacto en
proporción 6:3:1. Hasta la 1.6.1 esa proporción se sorteaba **bloque a bloque** con un hash, así que
los tres materiales salían intercalados uno junto a otro y la ladera se veía moteada, con tierra
asomando entre la nieve como si se hubiera derretido. No se derretía: se generaba así.

Ahora el sorteo usa ruido coherente y es **uno por columna**, compartido por toda la pila de
superficie — la tierra que hay bajo un parche de nieve pertenece a ese parche. La paleta pinta
manchas, no confeti: un tramo de nieve, luego un banco de hielo compacto.

Dos correcciones acompañan al cambio:

- Se mezcla algo de ruido blanco **antes** de aplicar la curva, para que los bordes de cada mancha
  queden deshilachados. Sin eso las fronteras son curvas suaves y parecen dibujadas.
- El resultado se aplana con la CDF normal. El ruido fractal es acampanado (σ ≈ 0,145 medida sobre
  490.000 muestras) y **nunca llegaba al primer ni al último decil**: una paleta 6:3:1 le daba casi
  todo a la entrada del medio y el hielo azul no aparecía jamás. Aplanado, los pesos vuelven a
  significar lo que dicen.

Medido sobre 230.000 columnas por preset, columnas vecinas que comparten material:

| | antes | ahora |
|---|---|---|
| BASE | 65,3% | **92,9%** |
| CHAOTIC | 65,1% | **92,4%** |
| INSANE | 62,9% | **92,0%** |

Esos promedios favorecen al método viejo, porque un bioma de un solo material coincide consigo mismo
de cualquier forma; eran las paletas mezcladas — las nevadas — las que salían moteadas. El resto de
desacuerdo son fronteras de bioma y orilla, que sí cambian de material de un bloque al siguiente.

El knob `surface-roughness` de cada bioma, que hasta ahora estaba declarado pero no se leía, controla
el tamaño de las manchas.

### Agua

Las superficies de agua son **planas**. Parece obvio, y sin embargo el nivel de cada columna se
sacaba de la altura de esa misma columna: en una ladera, cada columna llevaba su propio nivel y el
resultado eran láminas de agua trepando por la pendiente. Ahora un río corre al nivel del mar — por
encima de esa cota se talla el valle pero se deja seco — y un lago toma un único nivel medido en su
centro, así que su orilla la decide dónde el terreno sube por encima del agua, que es como funciona
un lago de verdad.

Medido: entre columnas de agua contiguas, **0,08–0,22% tienen un escalón**, y son los puntos donde un
lago se encuentra con el mar.

El hielo de los biomas helados se construye con **hielo compacto y hielo azul**. El hielo normal se
derrite con la luz y era lo primero que desaparecía dejando agujeros de agua abierta en la banquisa.

### Explanadas

`flatland-strength` amortigua el relieve en regiones amplias: llanos abiertos entre las cordilleras,
lo bastante grandes para que la búsqueda de terreno plano del emplazador encuentre dónde poner una
aldea o un castillo. CHAOTIC e INSANE la llevan alta, porque eran justo los presets donde no había
sitio para construir. La máscara de montañas se retira donde el llano manda, así que una cordillera
nunca arranca en mitad de una llanura.

### Cuevas

El subsuelo no es un agujero vacío. Cada cavidad pertenece a un **bioma de cueva** que decide de qué es el suelo, qué cuelga del techo y qué la ilumina:

| Bioma de cueva | Qué encuentras |
|---|---|
| Frondosa (lush) | Musgo, azaleas, enredaderas con bayas luminosas, flores de espora, hojas de gotera, arcilla y agua. |
| Dripstone | Bosques de estalactitas y estalagmitas con base, tronco y punta reales, bloques de dripstone. |
| Deep dark | Sculk, catalizadores, sensores, chillones. Sin luz natural. Aquí vive la **ancient city**. |
| Hielo | Hielo compacto y azul, capas de nieve, techos helados. Bajo regiones frías. |
| Champiñones | Champiñones gigantes, shroomlight, micelio y podzol. |
| Cristal | Geodas de amatista, calcita, basalto liso, paredes en gemación. |
| Magma | Basalto, blackstone, bloques de magma y fuego de almas, cerca de la bedrock. |

Medido sobre 36 chunks por preset: **10% del subsuelo hueco en BASE, 13,8% en CHAOTIC, 18,8% en INSANE**, de lo cual ~27% está inundado. La lava ocupa el 0,1% (pozas), no el 12-24% de la primera versión.

### Árboles (.schem)

**Todo árbol grande del mundo es un archivo `.schem`.** El generador procedural de troncos y copas ya
no existe: lo que hay en `plugins/ArkcronistGenerator/prefabs/trees/` es exactamente lo que crece. La
vegetación pequeña — hierba, flores, arbustos, setas, plantas de cueva — sigue siendo procedural.

El JAR trae **66 árboles**, de arbustos de 3×2×3 hasta gigantes de 31×45×31, repartidos en
familias:

| Familia | Ejemplo | Cómo se ve |
|---|---|---|
| Jungla / mangle | `giant_jungle_mangrove_01` (31×45×31) | Tronco grueso con ramas largas, copa doble de hoja de jungla y mangle, enredaderas colgando. |
| Azalea / abedul | `giant_azalea_birch_01` (31×37×31) | Copa clara y ancha, hoja florida mezclada con abedul. |
| Acacia / roble | `giant_acacia_oak_01` (31×37×31) | Copa en plataformas escalonadas sobre ramas inclinadas. |
| Pícea / abedul | `medium_spruce_birch_02` (15×55×15) | Conífera estrecha y muy alta, faldón de ramas hasta abajo. |
| Muertos (`dead`) | `large_dead_dark_oak_01` (21×39×21) | Ramaje desnudo, sin una sola hoja. |
| Cristal y otoño | `giant_crystal_amethyst_01`, `giant_autumn_birch_01` | Copas de amatista y de vidrio tintado: solo en CHAOTIC e INSANE. |

Los tamaños se mezclan solos: un bosque sale sobre todo de sotobosque y árboles jóvenes con algún
gigante viejo, no una plantación de copas idénticas.

**Añadir árboles no requiere recompilar.** Basta dejar el `.schem` en la carpeta y reiniciar. El
nombre del archivo es la ficha técnica: `giant_cherry_01.schem` se registra como tamaño `giant`,
especie `cherry`, y a partir de ahí los bosques de cerezos lo usan. Si un bioma pide una especie que
nadie ha aportado, el registro cae al pariente más cercano (cerezo → azalea o abedul) en vez de dejar
el suelo pelado. Las familias `dead`, `crystal` y `autumn` son *opt-in*: no aparecen en un robledal
normal por mucho que su nombre de archivo contenga «oak».

Cada árbol se elige por **bioma, especie, tamaño, preset y semilla**, y se coloca con una de cuatro
rotaciones (los estados de bloque rotan con él: escaleras, troncos con eje, vallas, raíles, carteles).
La separación entre árboles se deriva del ancho del propio prefab, así que meter un árbol colosal da
un bosque de árboles colosales bien espaciados y no un techo continuo de hojas.

**Ningún árbol se corta en el borde de un chunk.** Cada árbol se dibuja entero y el escritor recorta;
cada chunk recalcula además los árboles de sus vecinos y coloca la parte que le corresponde. Hay una
prueba automatizada que compara el prefab dibujado entero contra el mismo prefab ensamblado desde una
rejilla de chunks: deben ser idénticos bloque a bloque.

### Barcos (.schem)

Cinco navíos de vela en `prefabs/ships/`: **cúter** (28×28×13), **goleta** (39×39×14), **bergantín**
(50×50×26), **fragata** (68×55×27) y **navío de primera clase** (85×77×32).

El mar decide qué es cada sitio:

- **Océano profundo** (14+ bloques de agua): el casco flota en la superficie y sus bodegas se
  vacían. El aire que se escribe es solo el que el casco encierra — calculado con un relleno por
  inundación desde fuera al cargar el prefab — así que un barco flotando no abre un agujero
  rectangular en el mar.
- **Plataforma continental y costa**: el mismo `.schem` cae al fondo, se hunde unos bloques en él,
  queda abierto al agua y se deteriora de los mástiles hacia abajo. El deterioro es determinista por
  semilla: el mismo pecio sale igual siempre.

Los cofres del barco se registran como botín, así que abordar uno tiene sentido.

| Preset | Flota |
|---|---|
| BASE | Un cúter o una goleta, poco frecuentes, casi siempre pecios. |
| CHAOTIC | Cascos más grandes, más a menudo; a veces dos anclados juntos. |
| INSANE | Navíos de primera clase en alta mar y escuadras de hasta tres. |

### Tus propias estructuras .schem

Cualquier carpeta dentro de `prefabs/` es una categoría, y **el nombre de la carpeta decide qué es
la estructura**: en qué biomas aparece, en qué rejilla se coloca y a qué estructura procedural
sustituye. No hay nada que registrar ni que recompilar.

| Carpeta | Familia | Sustituye a |
|---|---|---|
| `houses/` | `VILLAGE` | `village` — cada archivo es **una casa**; el generador monta la aldea |
| `villages/` | `VILLAGE` | `village` — cada archivo es una aldea entera |
| `castles/` | `CASTLE` | `castle` |
| `cities/` | `CITY` | `city` |
| `fortresses/` | `FORTRESS` | `fortress` |
| `outposts/` | `OUTPOST` | `outpost` |
| `towers/` | `TOWER` | `tower` |
| `battle_towers/` | `BATTLE_TOWER` | `battle_tower` |
| `temples/` | `TEMPLE` | `temple` |
| `camps/` | `CAMP` | `camp` |
| `mansions/` | `MANSION` | `mansion` |
| `ruins/` | `PREFAB_RUIN` | nada: se suman como monumentos |
| `ships/` | `SHIP` | nada: reglas marítimas propias |
| `trees/` | — | todos los árboles grandes |

**En cuanto una carpeta tiene archivos, la versión procedural de esa familia se apaga.** Si pones
castillos en `castles/`, el mundo genera tus castillos y no los míos; las familias para las que no
aportes nada siguen siendo procedurales. Una carpeta que no esté en la tabla (`statues/`, por
ejemplo) se carga igual pero no se coloca sola.

**Aldeas a partir de casas sueltas.** `houses/` es el caso especial y el más útil: cada `.schem` es
una casa, y el generador decide cuántas hay, dónde y mirando hacia dónde. Las coloca en anillo
alrededor de una plaza, orientadas hacia el centro, con caminos de tierra pisada entre ellas y
aldeanos dentro (más un gólem de hierro de vez en cuando). Cada casa **nivela su propia parcela**, así
que una aldea en cuesta sale con menos casas en vez de con casas colgando de un barranco. Cuántas
casas depende del preset: 5–8 en BASE, 6–11 en CHAOTIC, 9–15 en INSANE.

**Qué hace el generador que la schematic no trae.** Un archivo exportado desde una parcela plana no
sabe lo que es una ladera, así que el emplazamiento nivela el suelo por los dos lados: pone cimientos
hasta donde el terreno se hunde y recorta lo que sobresale por encima del suelo elegido. Ambas cosas
solo en las columnas que el edificio ocupa de verdad, así que una torre se hace su terraza en la
pendiente en lugar de afeitar un rectángulo del paisaje. Además reparte guarnición, registra los
cofres del archivo como botín y los spawners como spawners, y a los edificios grandes o muy altos
les pone un jefe.

**Cómo nombrar los archivos.** Igual que los árboles: las palabras del nombre son etiquetas y una de
ellas puede ser el tamaño (`small`, `medium`, `large`, `giant`). El tamaño decide cuál se elige según
el preset — BASE tira de los medianos, INSANE de los gigantes. `giant_viking_longhouse_01.schem`,
`large_watchtower_02.schem`, `medium_stone_keep_01.schem`.

### Los paquetes de construcciones que vienen incluidos

El JAR trae **96 construcciones** ya repartidas por carpetas, así que un mundo nuevo genera aldeas
sin que tengas que hacer nada:

| Estilo | Qué trae |
|---|---|
| Medieval de entramado | 47 casas, iglesia, torreón, casa-puerta y tres torres |
| Vikingo | 25 casas de tejado a dos aguas y 2 torres |
| Fantasía | 6 casas altas y una mansión de 41×44×37 con torre |
| Piedra | Un castillo de 25×37×25 con torre del homenaje |

El desglose del paquete medieval:

| Carpeta | Qué trae |
|---|---|
| `houses/` | **47 casas** de entramado de madera, entre 11×9×11 y 23×20×20. Cuatro variantes de material de cada diseño: yeso de arenisca o de lana blanca, tejado de teja o de roble. |
| `temples/` | Una **iglesia** de 19×27×35 con vidriera y tres agujas. |
| `castles/` | Un **torreón** de 24×25×24 con torres de esquina. |
| `towers/` | Una **casa-puerta** de 9×24×25 con arco, una torre almenada y dos torretas. |

Añadidas en la 1.7.1, cada una entera, sin trocear:

| Carpeta | Qué trae |
|---|---|
| `castles/` | Dos castillos más: 37×35×37 y 25×35×25 |
| `temples/` | Un **templo de oasis** de 23×21×23 y una **pirámide** de 23×12×23 |
| `battle_towers/` | **Torre de mago** de 50×148×51 y **torre en ruinas nevada** de 25×26×24 |
| `ruins/` | **Ruinas nevadas** de 50×19×47 |

Como `houses/`, `temples/`, `castles/`, `towers/` y ahora `battle_towers/` traen archivos, **las
versiones procedurales de aldea, templo, castillo, torre y torre de combate se apagan solas**. La
fortaleza, la ciudad y las demás siguen siendo procedurales.

**Dos cosas que conviene saber sobre estas nuevas:**

*El bioma no elige la schematic.* El filtro por bioma actúa sobre la **familia** (qué biomas admiten
un templo), no sobre el archivo concreto: el selector de edificios recibe `null` como etiqueta
preferida. Así que la pirámide del desierto puede salir en `cherry_hills` o `lush_valley`, que son
dos de los cuatro biomas que admiten templos. Se puede arreglar, pero es un cambio en el selector.

*La torre de mago encoge dónde caben las torres de combate.* El filtro de emplazamiento usa el
**máximo de la familia** en altura y radio, no el del archivo que va a colocarse. Con 148 bloques de
alto y 50 de ancho, esa torre marca el listón para las dos. Medido sobre el terreno:

| | terreno que admite una torre de combate |
|---|---|
| BASE | 96,4% → **82,4%** |
| CHAOTIC | 89,8% → **63,2%** |
| INSANE | 71,3% → **31,8%** |

En INSANE se reduce a menos de la mitad. No es un fallo introducido aquí — es cómo funciona el filtro
desde siempre — pero un archivo de 148 bloques lo hace notar. Si prefieres torres de combate más
frecuentes, sácala de `battle_towers/`; si prefieres la torre, déjala como está.

**Se colocan tal cual.** El generador no toca el interior: lo que hay en el archivo es lo que se
construye. Lo único que hace es el emplazamiento — nivelar la parcela, poner cimientos, rotar el
edificio y registrar sus cofres como botín.

Merece la pena saberlo antes de dejarlo así: casi ninguna schematic compartida trae cama ni luz, y
**un aldeano necesita una cama para reclamar la casa**; sin casa reclamada no hay comercio, ni
crianza, ni gólems de hierro. Si prefieres que el generador rellene lo que falte, pon
`prefabs.furnish: true` en `config.yml` y añadirá cama, linterna y bloque de oficio solo donde no
los haya.

**Cómo se apoyan.** El relleno bajo un edificio va **solo bajo las columnas en las que apoya de
verdad** — no bajo el alero del tejado, que es donde antes salía un muro — y usa la tierra y el
césped del propio bioma en vez de piedra, así que una casa en cuesta se encuentra con la colina en
lugar de quedar sobre un bloque de mampostería. Una parcela que necesitaría más de unos pocos
bloques de relleno sencillamente no se usa.

**Altura de colocación.** El Y=0 de una schematic suele estar un par de hiladas por debajo del suelo
visible, así que apoyarla en la altura exacta del terreno la deja enterrada. `prefabs.lift.buildings`
la sube (3 por defecto) y `prefabs.lift.ships` hace lo propio con los barcos (6 por defecto, porque
la línea de flotación se deduce del casco y se queda corta: sin subirlos flotan con la cubierta
sumergida y solo asoman los mástiles).

**Monumento incluido.** `prefabs/ruins/citadel_bashna.schem` — una ciudadela en ruinas de 38×98×38 en
piedra musgosa, con guarnición y jefe.

### Estructuras

**31 familias**, todas procedurales y adaptadas al terreno (nivelan su plataforma, hunden cimientos en la pendiente, despejan el espacio superior y se iluminan por dentro). Incluye el equivalente completo del set vanilla, mejorado:

| Grupo | Estructuras |
|---|---|
| Asentamientos | `village` (casas amuebladas y con luz, camas, taller, chimenea, huertos, pozo techado, campana, farolas), `city` (murallas, torres, calles, mercado), `castle` (muralla con adarve, torres de esquina, casa-puerta con arco, patio con jardines y pozo, torreón con sala del trono), `fortress`, `outpost` (avanzada pillager con empalizada y jaula), `camp` |
| Templos y torres | `tower` (torre escalonada con arcos, ventanas, hiedra y almenas), `battle_tower`, `temple`, `desert_pyramid` (con la sala trampa y su TNT), `jungle_temple` (con palancas y cable trampa) |
| Ruinas | `ruins`, `trail_ruins` (calzada enterrada con grava sospechosa y vasijas), `ruined_portal`, `fossil` |
| Frío y ciénaga | `igloo` (con laboratorio en el sótano), `witch_hut` (sobre pilotes) |
| Agua | `underwater`, `monument` (monumento oceánico con guardián anciano), `shipwreck`, `buried_treasure` |
| Aire | `sky_sanctuary`, `bridge` |
| Subsuelo | `mineshaft` (galerías en dos niveles, raíles, soportes, nido de arañas), `stronghold` (biblioteca, celdas, fuente y sala del portal), `ancient_city` (sculk, columnata, marco de deepslate reforzado), `trial_chamber` (arenas de cobre y tuff con trial spawners y vaults), `dungeon`, `vault`, `geode` (geoda de amatista) |

**Estructuras vanilla auténticas.** Ojo con cómo lo hace el servidor: Paper coloca los bloques de una
estructura vanilla **dentro de su pasada de decoración**, no en la de estructuras. Con la decoración
apagada, el monumento oceánico y las minas se planificaban y no se construían nunca — que es
exactamente lo que se veía. Activar `vanilla-structures` ahora activa también esa pasada, así que
vienen con la vegetación y las menas vanilla de propina. No hay forma de separarlas en la API.

 Con `structures.vanilla-structures: true` (por defecto) el servidor genera
sus propias estructuras en el mundo — monumento oceánico, stronghold, ancient city, mansión, mina, pirámide
del desierto, templo de jungla, iglú, cabaña de bruja, naufragio, tesoro, portal en ruinas, trail ruins,
trial chambers y avanzada pillager — tal cual vienen en el juego, siguiendo las claves de bioma vanilla que
reporta este generador. Las equivalentes propias se apartan solas para no duplicarlas; se controla en
`config.yml` con `force-enabled` y `disabled`.

Lo que sigue siendo propio: `castle`, `city`, `village`, `fortress`, `tower`, `battle_tower`, `camp`,
`ruins`, `bridge`, `temple`, `dungeon`, `vault`, `geode`, `fossil`, `sky_sanctuary`, `underwater`.

Las de superficie se colocan en dos rejillas (una gruesa para ciudades y castillos, otra fina para torres y
ruinas) y **buscan el terreno más plano de su celda** antes de construir; las subterráneas van en su propia
rejilla y se sitúan por profundidad, no por bioma.

### Minibosses

Las estructuras registran qué entidades quieren y dónde; el plugin las materializa en el hilo
principal cuando el chunk se carga (y solo una vez, marcado en el *persistent data* del chunk).

- Guarnición normal escalada por nivel.
- Minibosses con vida y daño multiplicados, armadura, resistencia al empuje, efectos permanentes,
  equipo encantado, nombre personalizado visible, brillo y persistencia.
- Nombres configurables por rol en `config.yml` (`tower_captain`, `castle_lord`, `deep_leviathan`…).
- Cofres con botín por niveles y temática, generados de forma determinista por posición.
- Los generadores (`spawner`) se configuran con la entidad correcta de cada estructura.

---

## 2. Instalación y uso

1. Copia `ArkcronistGenerator-1.8.0.jar` en `plugins/`.
2. Arranca el servidor una vez para que se genere `plugins/ArkcronistGenerator/config.yml`.
3. Crea el mundo con tu gestor de mundos (ArkcronistWorlds, Multiverse, etc.):

```
-g ArkcronistGenerator
-g ArkcronistGenerator:BASE
-g ArkcronistGenerator:CHAOTIC
-g ArkcronistGenerator:INSANE
```

En `bukkit.yml` sería:

```yaml
worlds:
  arkcronist:
    generator: ArkcronistGenerator:INSANE
```

Requisitos: **Java 21**, Paper o Purpur **1.21.8 – 1.21.11**. Un único JAR, sin dependencias, sin
NMS: todo se hace con la API de Paper, así que no hay nada que actualizar entre parches de esa serie.

---

## 3. Comandos

| Comando | Qué hace |
|---|---|
| `/ag help` | Ayuda. |
| `/ag info` | Preset, semilla, bioma, altura de superficie y nivel de agua donde estás. |
| `/ag biome [x z]` | Bioma del generador en una posición. |
| `/ag locate [tipo]` | Busca la estructura más cercana (asíncrono). |
| `/ag structures` | Catálogo de estructuras. |
| `/ag prefabs` | Lista los `.schem` cargados: árboles, barcos y monumentos, con tamaño y número de bloques. |
| `/ag presets` | Lista BASE, CHAOTIC e INSANE. |
| `/ag stats` | Memoria, tamaño de caché, tasa de aciertos, colas de mobs. |
| `/ag bench [preset] [chunks]` | Benchmark de generación en un hilo aparte. |
| `/ag top` | Te sube a la superficie. |
| `/ag reload` | Recarga `config.yml`. |
| `/ag version` | Versión y estados de bloque resueltos. |

Permisos: `arkcronist.command` (op) y `arkcronist.admin` (op, solo para `reload`).

---

## 4. Configuración

`config.yml` viene con **todos los parámetros documentados y comentados**. Lo que está bajo
`terrain:` afecta a todos los presets; lo que está bajo `presets.<nombre>:` solo a ese. Las claves que
borres simplemente conservan el valor del preset, así que el archivo se puede dejar mínimo.

```yaml
default-preset: BASE

performance:
  terrain-cache-chunks: 512     # ~12 KB por chunk cacheado
  parallel-generation: true

minibosses:
  enabled: true
  health-multiplier: 4.0
  damage-multiplier: 2.0
  names:
    tower_captain: "Capitán de la Torre"

terrain:
  # abyss-depth: 82.0
  # mountain-amplitude: 115.0
  # floating-island-density: 0.0
  # cave-cheese-threshold: 0.56
```

Los cambios afectan a los chunks **nuevos**; el terreno ya generado nunca se reescribe.

---

## 5. Arquitectura

```
com.arkcronist.gen
├── core/                  ← 100% independiente de Bukkit, probable y medible sin servidor
│   ├── math/              FastRandom (xoroshiro128++), hashing posicional, utilidades
│   ├── noise/             símplex 2D/3D, fractal (fBm, ridged, billow), warp, celular
│   ├── block/             registro de bloques → ids enteros, paletas ponderadas
│   ├── terrain/           muestreador de columnas, rejilla + erosión, cuevas, densidad 3D,
│   │                      estratos, menas, caché, motor
│   ├── biome/             tabla de biomas, perfiles de decoración, selector
│   ├── prefab/            lector NBT+gzip propio, schematics Sponge v2/v3, rotación de
│   │                      estados de bloque, registro de prefabs por carpeta
│   ├── decorate/          colocación de árboles .schem/rocas y decoración por columna
│   ├── structure/         búfer, kit de construcción, colocador, 33 estructuras
│   └── bench/             benchmark ejecutable
└── bukkit/                ← capa fina sobre la API de Paper
    ├── ArkChunkGenerator, ArkBiomeProvider, BlockBridge, WorldRegistry, PrefabInstaller
    ├── populator/         features + estructuras
    ├── mobs/              cola de spawns, fábrica de minibosses, listener
    ├── loot/              relleno de cofres
    ├── command/           /ag
    └── config/            lectura de config.yml
```

El núcleo escribe **enteros**, no `BlockData`. La traducción a bloques reales ocurre una sola vez al
arrancar. Eso hace que todo el terreno se pueda probar en JUnit sin servidor y que la generación no
toque nunca el parser de estados de bloque.

Los `.schem` entran por la misma puerta: al cargarlos, cada entrada de su paleta se registra como un
entero más — una vez por cada una de las cuatro rotaciones — de modo que colocar un prefab cuesta lo
mismo que colocar terreno. El lector de NBT y gzip es propio y no añade ninguna dependencia: el
plugin sigue siendo **un solo JAR sin librerías empaquetadas**.

### Rendimiento

Decisiones que están en el código, no en la intención:

- **Rejilla gruesa + interpolación.** El apilamiento de ruido se evalúa cada 4 bloques y se sube a
  resolución de bloque con interpolación bicúbica. Los campos 3D (cuevas, salientes, vetas, islas,
  moteado de estratos) usan rejillas 4×4×4 con interpolación trilineal.
- **Rejilla global alineada y con relleno.** Dos chunks vecinos interpolan desde los mismos nodos con
  los mismos pesos, así que las fronteras coinciden exactamente. La erosión es un *stencil* de solo
  lectura, lo que la mantiene idéntica sea cual sea el chunk que la dispare.
- **Una sola pasada por columna.** Terreno, salientes, cuevas, agua, capas de superficie, estratos y
  menas se resuelven en un único recorrido descendente por columna.
- **Nada de estado por orden de llamada.** Todo sale de la posición del mundo, así que la generación
  es reproducible, paralela y segura entre hilos.
- **Caché acotada** de datos 2D por chunk (límite duro configurable) en lugar de crecer con el área
  explorada.
- **Rechazo temprano.** Los árboles de los chunks vecinos se descartan por caja envolvente antes de
  construirse; las menas hacen primero un hash barato y solo entonces consultan el campo de vetas.
- **Cero E/S durante la generación** y sin hilos propios: se usa el planificador de Paper.

Optimizaciones concretas hechas durante el desarrollo, con su medida:

| Cambio | Antes | Después |
|---|---|---|
| Sacar el ruido de estratos del bucle interno (por columna, no por bloque) + rejilla de moteado | 5,9 ms/chunk | 2,8 ms/chunk |
| Hash barato antes de consultar el campo de vetas de menas | — | incluido arriba |
| Descarte por caja envolvente de los árboles de vecinos | 1,5 ms/chunk | 1,0 ms/chunk |
| La pasada de bloques publica la superficie sólida en vez de recalcular los campos 3D | 2,3 ms/chunk (features) | 1,3 ms/chunk |
| Rejilla de cuevas a 4x6 en vez de 4x4 | 7,9 ms/chunk | 7,7 ms/chunk |
| La comprobación de emplazamiento se hace una sola vez, al resolver la celda | 10,7 ms/chunk | 8,4 ms/chunk |

Sobre lo último: `/ag locate` y la construcción usaban cada uno su propia comprobación de terreno, y
la de `locate` además consumía el generador aleatorio compartido del emplazamiento — es decir, mirar
dónde estaba un castillo cambiaba el castillo que se construía. Ahora la celda guarda una semilla y
la comprobación se hace una vez: sale más barato **y** deja de ser una fuente de indeterminismo.

Los prefabs no aparecen como una línea aparte en la tabla porque no la necesitan: un `.schem` se
coloca escribiendo enteros ya resueltos, en mosaicos de 16×16 que se descartan enteros cuando el
chunk actual no los toca. Sustituir el constructor procedural de árboles por prefabs bajó la fase de
*features* de ~2,3 a ~1,8 ms/chunk.

Medición actual (un solo hilo, contenedor de desarrollo, 256 chunks por preset, 160 prefabs cargados):

```
BASE:    ~9,4 ms/chunk  (~106 chunks/s/hilo)
CHAOTIC: ~9,4 ms/chunk  (~106 chunks/s/hilo)
INSANE:  ~10,3 ms/chunk (~97 chunks/s/hilo)
```

Reparto por fase en BASE: 6,4 ms bloques, 2,1 ms features (árboles y rocas), 0,9 ms estructuras,
0,004 ms mapa de alturas (99% de aciertos de caché). Sigue siendo generación en paralelo: Paper
reparte los chunks entre varios hilos.

Reproducible con:

```bash
mvn -q package -DskipTests
java -cp target/ArkcronistGenerator-1.8.0.jar com.arkcronist.gen.core.bench.TerrainBenchmark 1234 256
```

o dentro del juego con `/ag bench INSANE 256`.

---

## 6. Pruebas

126 pruebas JUnit 5, todas sin servidor:

```bash
mvn test
```

Cubren, entre otras cosas:

- Rango, determinismo e independencia por semilla del ruido.
- **Continuidad entre chunks** (la prueba anti-costuras) y estabilidad de la erosión.
- Terreno idéntico generado en paralelo desde 8 hilos.
- Perfil oceánico: la plataforma es mucho menos profunda que la llanura abisal, y cada preset baja
  más que el anterior.
- Coherencia bioma/agua: el mar profundo no elige biomas de tierra ni al revés.
- Bedrock, llenado de agua, capas de superficie y reproducibilidad bloque a bloque.
- **Las cuevas nunca abren el fondo marino** (los océanos no se vacían).
- Volumen de cueva dentro de límites por preset (ni macizo ni hueco).
- **Prefabs nunca cortados en el borde de chunk**: el árbol, el barco y la ciudadela
  dibujados enteros se comparan bloque a bloque contra los mismos ensamblados desde una rejilla de
  chunks.
- Volcado de estructuras sin pérdidas ni solapes, colocación determinista, botín y minibosses.
- Caché acotada, tasa de aciertos y benchmark.

Pruebas añadidas en la 1.1 a raíz de los fallos vistos en el servidor real:

- **La superficie donde se planta un árbol o se apoya una estructura es sólida de verdad** (era el
  origen de los árboles flotando y medio enterrados: los campos 3D del paso de bloques y los de la
  búsqueda de superficie no compartían la misma rejilla en Y).
- **Las cuevas están decoradas y no son agujeros vacíos** (≥5% de bloques de vegetación/cristal por
  volumen excavado).
- **Hay cuevas inundadas y la lava se queda junto a la bedrock** (regresión del mar de lava).
- **Todas las estructuras construyen algo y tienen alguna fuente de luz** (regresión de los
  interiores a oscuras).
- **Todas las familias de estructuras son alcanzables en el mundo** (regresión de las estructuras
  con etiqueta que ningún bioma aceptaba).

Pruebas añadidas en la 1.2:

- **Bajo el fondo marino no queda ni un bloque de aire seco** (el fallo de la cueva que se inundaba
  al entrar).
- **Las estructuras de superficie caen en terreno plano**, comprobado sobre los emplazamientos que
  devuelve `/ag locate` — que ahora es literalmente el mismo cálculo que usa la construcción.

Pruebas añadidas en la 1.3, para el sistema de prefabs:

- Las 39 schematics del JAR cargan sin un solo error, con sus dimensiones, anclajes y tamaños.
- **Las cuatro rotaciones escriben exactamente los mismos bloques** y giran el pie de la huella; los
  estados de bloque (escaleras, ejes de tronco, vallas, raíles, carteles) rotan con ellas.
- Colocar un prefab dos veces da el mismo resultado, y el deterioro de un pecio es determinista.
- **El aire que se escribe es solo el que el casco encierra**: un barco a flote conserva sus bodegas
  secas sin abrir un hueco rectangular en el mar.
- Los cofres se registran en las coordenadas donde realmente se escribieron, en las cuatro rotaciones.
- Un `.schem` corrupto o vacío se rechaza con un error legible en vez de tumbar el arranque.
- La selección por especie acierta la familia, y `dead`, `crystal` y `autumn` no aparecen si no se
  piden por su nombre — ni siquiera se cruzan entre ellas.
- Los bosques se plantan en los tres presets, **BASE nunca produce árboles de cristal u otoño y
  INSANE sí**, y la misma semilla da el mismo bosque dos veces.
- Los barcos se colocan sobre agua en los tres presets y llevan botín; el monumento `.schem` se
  levanta sobre terreno llano y con guarnición.
- Una carpeta de prefabs vacía degrada en silencio en vez de fallar.

Pruebas de las categorías de prefab (con schematics sintéticas escritas y leídas en disco, así que
cubren gzip, NBT, el flujo de varints y el cargador de carpetas):

- Una carpeta cualquiera se convierte en categoría, con su tamaño y sus etiquetas leídos del nombre.
- **Poner archivos en `castles/`, `towers/`, `battle_towers/` o `houses/` apaga la estructura
  procedural equivalente**, y las familias sin archivos siguen intactas.
- Las sustitutas conservan la etiqueta de familia, así que aparecen en los mismos biomas.
- Los edificios prefab se encuentran en el mundo, construyen, se iluminan y llevan guarnición.
- Una aldea se monta con varias casas y tiene aldeanos y cofres dentro.
- **Ninguna columna de un edificio empieza en el aire**: el cimiento llega hasta el terreno real.
- Una carpeta desconocida se carga pero nunca se coloca sola.
- **Un marcador de mob o de cofre que cae fuera de los bloques de su estructura sigue llegando a su
  chunk** — antes se perdía en silencio, y afectaba también a las estructuras procedurales.

---

## 7. Compilar

```bash
mvn -B package        # ejecuta las pruebas y produce target/ArkcronistGenerator-1.8.0.jar
```

Requiere JDK 21 y la Paper API 1.21.8 (`repo.papermc.io`, ya declarado en el `pom.xml`).

---

## 8. Estado y siguientes pasos

La 1.3 sustituye por completo el generador procedural de árboles por el sistema de prefabs `.schem`.
Lo que queda por delante:

- Los paquetes incluidos son exteriores: sin camas, sin luz y sin muebles. Se colocan tal cual
  (ver `prefabs.furnish` si quieres que el generador rellene lo mínimo).
- Evitar solapes entre las estructuras propias y las vanilla, que hoy se ignoran mutuamente.
- Ajuste fino de densidad de estructuras y de botín con datos de juego real.
- Habilidades activas de minibosses (ahora tienen estadísticas, equipo y efectos).
