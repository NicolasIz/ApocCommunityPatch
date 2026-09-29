# libs/

Solo hace falta cuando la máquina que compila **no llega a `repo.papermc.io`**. Si este directorio
tiene `paper-api.jar`, `build.gradle` compila contra él en lugar de descargar `paper-api` de Paper;
si no, lo ignora.

| Archivo | Qué es | De dónde sale |
|---|---|---|
| `paper-api.jar` | La API de Paper 1.21.8 compilada | El JAR binario de `paper-api` 1.21.8, o el JAR de fuentes compilado (ver abajo) |
| `brigadier.jar` | Brigadier **1.3.10** | `com.mojang:brigadier:1.3.10` de `libraries.minecraft.net`; no está en Maven Central |

El resto de lo que `paper-api` traería por su POM (Adventure, Guava, Gson, SnakeYAML, JOML…) viene de
Maven Central; la lista está en `build.gradle`. Los JAR de este directorio no se suben a git
(`*.jar` está en `.gitignore`): son binarios de terceros.

Brigadier tiene que ser la 1.3.10. Las versiones 1.0.x no tienen `ArgumentType#parse(StringReader, S)`,
que la API de Paper 1.21.8 usa en `CustomArgumentType`.

## Compilar `paper-api.jar` desde el JAR de fuentes

1. Descomprimir `paper-api-1.21.8-R0.1-*-sources.jar`.
2. Compilar todas las `.java` con `javac --release 21 -proc:none`, con el classpath formado por
   `brigadier.jar` y las dependencias de la lista de `build.gradle`, más `log4j-api`, `checker-qual`,
   `commons-lang:2.6`, `maven-resolver-provider` 3.9.x y `maven-resolver-connector-basic` /
   `-transport-http` 1.9.x, que la API usa internamente.
3. Empaquetar las clases: `jar --create --file paper-api.jar -C classes .`

Con esos pasos, las fuentes del 17/08/2025 (build 35) compilan sin un solo error: 1826 fuentes, 2195 clases.
