# Avisos y atribuciones

ArkcronistGenerator es un proyecto propio, escrito desde cero. No es un fork ni un
renombrado de ningún generador existente. Su arquitectura, su motor de ruido, su
canalización de terreno, su tabla de biomas, su catálogo de
estructuras y su capa Bukkit son código original de este repositorio.

Durante el desarrollo se estudiaron dos proyectos de código abierto, ambos con
licencias permisivas, para entender qué funciona bien en generación de mundos para
Paper y qué conviene evitar. Se reconocen aquí sus ideas y sus licencias.

## Terra

- Proyecto: Terra (PolyhedralDev)
- Repositorio: https://github.com/PolyhedralDev/Terra
- Licencia: MIT — Copyright (c) 2020-2025 Polyhedral Development

Ideas estudiadas y adaptadas de forma independiente:

- Separar por completo el núcleo de generación de la API del servidor, de modo que
  el terreno se pueda probar y medir sin arrancar Minecraft.
- Componer el ruido como una cadena de muestreadores (fractal, deformación de
  dominio, celular) en lugar de escribir funciones de terreno monolíticas.
- Muestrear campos costosos en una rejilla gruesa e interpolar, en vez de evaluar
  ruido en cada bloque.
- Configuración declarativa: el generador se describe con datos, no con código.

## TerraformGenerator

- Proyecto: TerraformGenerator (Hex27)
- Repositorio: https://github.com/Hex27/TerraformGenerator
- Licencia: Apache License 2.0

Ideas estudiadas y adaptadas de forma independiente:

- Rejilla de "megachunks" para decidir de forma determinista dónde aparecen las
  estructuras grandes, con relleno en los bordes de cada celda.
- Estructuras construidas por código procedural que se adapta al relieve, en lugar
  de plantillas fijas pegadas sobre el terreno.
- Biomas con identidad propia en materiales y decoración, no solo un cambio del
  bioma vanilla.
- Poblar cada chunk consultando también a sus vecinos para que ningún elemento
  quede cortado en el borde.

## Schematics incluidas

Los archivos `.schem` que viajan dentro del JAR bajo `prefabs/` — 66 árboles, 89
construcciones, cinco navíos y una ciudadela — los aportó el propietario del servidor
para este proyecto. No proceden de Terra ni de TerraformGenerator, ni de ningún otro
plugin.

Los árboles y las construcciones se obtuvieron dividiendo paquetes que traían todos
los diseños sobre una misma plataforma. Las herramientas que hacen ese trabajo están
en `tools/` y reproducen los archivos publicados byte a byte:

- `split_schem_bundle.py` separa un paquete en un prefab por construcción, en modo
  `trees` o `buildings`.
- `read_anvil.py` y `world_to_schem.py` hacen lo mismo cuando el paquete llega como
  un mundo de Minecraft entero en vez de como schematic: leen los archivos de región
  `.mca` y exportan la zona construida como schematic.

El lector de NBT y de schematics Sponge (`core/prefab/`) es código original de este
repositorio; no se ha usado ninguna librería de terceros para leerlos.

## Software de terceros

- Paper API (PaperMC) — usada como dependencia `provided` en tiempo de compilación
  y aportada por el servidor en ejecución. https://papermc.io
- JUnit 5 — solo para pruebas.

## Sobre el ruido

La implementación de ruido símplex de `SimplexNoise.java` es propia y sigue la
formulación pública clásica del ruido símplex (Perlin, 2001; descrita por Stefan
Gustavson). No se ha copiado código de ninguno de los proyectos anteriores.
