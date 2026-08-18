# Avisos y atribuciones

ArkcronistGenerator es un proyecto propio, escrito desde cero. No es un fork ni un
renombrado de ningún generador existente. Su arquitectura, su motor de ruido, su
canalización de terreno, su tabla de biomas, su sistema de árboles, su catálogo de
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

## Software de terceros

- Paper API (PaperMC) — usada como dependencia `provided` en tiempo de compilación
  y aportada por el servidor en ejecución. https://papermc.io
- JUnit 5 — solo para pruebas.

## Sobre el ruido

La implementación de ruido símplex de `SimplexNoise.java` es propia y sigue la
formulación pública clásica del ruido símplex (Perlin, 2001; descrita por Stefan
Gustavson). No se ha copiado código de ninguno de los proyectos anteriores.
