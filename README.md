# RecetasPersonalizadas 1.0.0

Plugin informativo para **PaperMC 26.1.2 y Java 25**. Permite compartir recetas mediante inventarios de cofre, con creación y edición dentro del juego. **No registra recetas de crafteo** ni entrega sus resultados.

## Compilar

Instalá un JDK 25 y configurá `JAVA_HOME`. El proyecto incluye Gradle Wrapper 9.3.1, con verificación SHA-256 de la distribución.

Linux/macOS:

```sh
./gradlew clean build
```

Windows:

```bat
gradlew.bat clean build
```

El JAR queda en **`build/libs/RecetasPersonalizadas-1.0.0.jar`**. Las pruebas se ejecutan como parte de `build`; el informe está en `build/reports/tests/test/index.html`.

La dependencia solicitada es `io.papermc.paper:paper-api:26.1.2.build.+`, desde el repositorio oficial `https://repo.papermc.io/repository/maven-public/`. `gradle.lockfile` fija la resolución verificada a **26.1.2.build.74-stable** y todas las dependencias de pruebas. Para actualizar deliberadamente esa resolución: `./gradlew build --write-locks --refresh-dependencies`.

En este entorno cloud se puede ejecutar `bash scripts/cloud-setup.sh`: instala Temurin 25 con checksum fijo si falta, configura Gradle para el proxy y los certificados del sistema y compila. Usa `/workspace/tools` y `/workspace/.gradle`; no contiene secretos. Los demás equipos no necesitan este script.

## Instalar

El repositorio incluye el [JAR compilado de la versión 1.0.0](dist/RecetasPersonalizadas-1.0.0.jar) y su [SHA-256](dist/RecetasPersonalizadas-1.0.0.jar.sha256). Para regenerarlo, ejecutá la compilación indicada arriba.

1. Detené tu servidor Paper **26.1.2** que utiliza **Java 25**.
2. Copiá `build/libs/RecetasPersonalizadas-1.0.0.jar` a la carpeta `plugins/`.
3. Iniciá el servidor. El plugin genera `plugins/RecetasPersonalizadas/config.yml` y el directorio `recipes/`.
4. Abrí `/recetas`. Como operador, usá `/editrecetas` para crear contenido.

No necesita bases de datos, otras bibliotecas instaladas como plugins, claves ni acceso a Internet durante el funcionamiento. La aceptación del EULA y administración del servidor corresponden a su propietario.

## Comandos y permisos

| Comando | Función | Permiso |
| --- | --- | --- |
| `/recetas` | Catálogo público de 54 espacios | `recetaspersonalizadas.ver` (todos por defecto) |
| `/editrecetas` | Catálogo administrativo | `recetaspersonalizadas.admin` (operadores por defecto) |
| `/editrecetas nombre <nombre>` | Completa una solicitud de creación o cambio de nombre | Administrador |
| `/editrecetas cancelar` | Cancela la solicitud; conserva cambios válidos de una edición existente | Administrador |

Los comandos requieren un jugador. Los permisos se vuelven a comprobar al interactuar y al guardar, incluso si se revocan con un menú abierto.

## Consultar

Cada ícono representa el resultado y lleva el nombre identificativo y la indicación de clic. El catálogo muestra 45 recetas por página y botones de navegación. Si no hay recetas, muestra un aviso. No hay un máximo artificial de recetas.

El visor tiene 27 espacios. Los índices, desde cero, son:

```text
 0  [1] [2] [3]  4   5   6   7   8
 9 [10][11][12] 13  14 [15] 16  17
18 [19][20][21] 22  23  24  25  26
```

Los nueve ingredientes están entre corchetes en las tres primeras columnas de la matriz. **El resultado es el índice 15** (séptima columna de la segunda fila). El botón de volver está en 26. Se conservan los ítems originales, sus cantidades y posiciones vacías. El visor nunca añade lore ni nombres a los ítems guardados; sólo el catálogo usa íconos de presentación.

## Crear y editar

1. `/editrecetas` → **Crear receta**.
2. Escribí `/editrecetas nombre Espada especial`. El nombre debe ser único, ignorando mayúsculas, y tener de 1 a 64 caracteres.
3. En el editor, hacé clic izquierdo o derecho en un ítem de tu inventario para **seleccionar una copia completa**, incluyendo su cantidad.
4. Hacé clic izquierdo en cualquiera de los nueve espacios de ingredientes o en el resultado (15) para colocar esa copia. Se puede repetir en otros espacios. También se admite copiar un ítem real que ya esté en el cursor.
5. Clic derecho sobre un espacio editable para vaciarlo. Los ingredientes son opcionales; el resultado debe ser un ítem válido.
6. **Guardar y volver** (8) confirma el guardado en disco antes de volver al catálogo. **Volver** (26) hace lo mismo. Cerrar el inventario también guarda automáticamente si el resultado es válido.

El editor es de plantillas: no arrastra ni transfiere objetos reales. El inventario personal y el cursor no se modifican por los clics del plugin. Shift-click, números, mano secundaria, doble clic, arrastres, drops y clonados creativos quedan bloqueados mientras el menú está abierto. El cierre del inventario sigue el comportamiento normal de Minecraft para un objeto real que el jugador ya tuviera en el cursor.

El botón **Cambiar nombre** (6) conserva temporalmente los cambios mientras se solicita el nuevo nombre. El botón **Eliminar** (7) abre una confirmación; se puede cancelar y continuar editando. Cerrar la confirmación sin eliminar guarda los cambios válidos. Un segundo administrador no puede editar la misma receta hasta que se libere su sesión y termine la escritura.

Si se cierra una edición sin resultado válido, se descartan esos cambios y se conserva la versión previamente guardada. El guardado manual inválido mantiene abierto el editor para corregirlo. Si falla una escritura, se conserva el archivo anterior y la edición se puede reintentar; `/editrecetas` recupera la sesión fallida mientras el jugador siga conectado. Los errores también aparecen en consola. No desconectarse antes de resolver un error de guardado.

## Configuración y datos

`decorar-menus: true` rellena los espacios libres del visor y editor con cristal gris; `false` los deja vacíos. Reiniciá el servidor para aplicar cambios. Los diez espacios de receta y los controles nunca se sustituyen por decoración.

Cada receta se guarda en `plugins/RecetasPersonalizadas/recipes/<uuid>.recipe`. Es un formato binario versionado que usa **`ItemStack.serializeAsBytes()` / `deserializeBytes()` de Paper** para conservar metadatos y componentes compatibles. No es necesario editar estos archivos manualmente. Las escrituras se serializan en un trabajador dedicado, fuerzan los datos del temporal a disco y sustituyen el archivo atómicamente. Si el sistema de archivos no permite el reemplazo atómico, se informa el fallo sin hacer un reemplazo inseguro.

La receta aparece actualizada para los demás jugadores después de completar el guardado y volver a abrir el menú. En el apagado se espera a las escrituras pendientes. Si un archivo es inválido, el plugin se deshabilita sin sobrescribirlo: revisá la consola y restaurá ese archivo desde tu respaldo. Hacé copias del directorio completo `recipes/` con el servidor detenido. No se garantiza recuperación de recetas perdidas por un fallo físico del almacenamiento; mantené respaldos. No bajes de versión de Minecraft sin comprobar antes la compatibilidad de sus ItemStack.

## Arquitectura

- `RecetasPlugin`: ciclo de vida y registro.
- `Recipe`, `RecipeLayout`: snapshots defensivos, matriz y paginación.
- `RecipeManager`: nombres, catálogo compartido, bloqueo y operaciones pendientes.
- `RecipeStore`, `AtomicFiles`: serialización nativa y persistencia asíncrona segura.
- `Menu`: InventoryHolder con tipo, propietario y sesión; no se identifican menús por títulos.
- `Menus`: construcción compartida de catálogos, visor, editor y confirmación.
- `MenuListener`: seguridad, edición de copias, navegación y cierre.
- `RecipeCommands`, `Permissions`: comandos y autorización.

## Validación

La suite usa JUnit y MockBukkit **para 26.1.2**: múltiples recetas, recarga, edición, eliminación, conservación de metadatos/PDC/encantamientos, cantidades, escrituras atómicas, fallos de disco, nombres únicos, bloqueo entre administradores, permisos revocados, matriz exacta, paginación, copias sin consumo y cierres del editor. No hay tests deshabilitados intencionalmente.

MockBukkit utiliza su propia implementación de serialización: una prueba en un servidor Paper real sigue siendo recomendable para componentes introducidos por otros plugins. Ver `VALIDACION.md` para el resultado ejecutado en este entorno y sus límites.
