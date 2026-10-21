# Validación ejecutada

Fecha: 21 de octubre de 2026.

## Herramientas y dependencias

- Temurin Java 25.0.4.1+1, archivo de instalación verificado mediante SHA-256.
- Gradle Wrapper 9.3.1, distribución verificada mediante SHA-256.
- Paper API oficial: `26.1.2.build.74-stable`.
- MockBukkit: `mockbukkit-v26.1.2:4.115.0`.
- JUnit Jupiter: 6.1.2.
- Resolución de dependencias guardada en `gradle.lockfile`.

## Ejecución final

`JAVA_HOME=/workspace/tools/jdk-25.0.4.1+1 GRADLE_USER_HOME=/workspace/.gradle ./gradlew clean build --no-daemon` ejecutó la compilación con Java 25, sin modificar el lockfile. Resultado: **BUILD SUCCESSFUL**.

| Suite | Pruebas | Fallos | Errores | Omitidas |
| --- | ---: | ---: | ---: | ---: |
| AtomicFilesTest | 3 | 0 | 0 | 0 |
| InventoryWorkflowTest | 18 | 0 | 0 | 0 |
| RecipeLayoutTest | 3 | 0 | 0 | 0 |
| RecipeStoreTest | 9 | 0 | 0 | 0 |

**Total: 33 pruebas ejecutadas y aprobadas; ninguna omitida.**

Las suites prueban los menús mediante eventos de inventario y carga real del plugin dentro de MockBukkit. La prueba de fallo de disco provoca un error de escritura intencional para comprobar que la edición se conserva y puede reintentarse; el mensaje SEVERE esperado en ese caso no representa un fallo de la suite. ByteBuddy emite una advertencia sobre una API de Java deprecada durante las pruebas.

## Artefacto

- `build/libs/RecetasPersonalizadas-1.0.1.jar`
- JAR inspeccionado: clases Java 25 (major 69), `plugin.yml` y `config.yml` presentes; no empaqueta la API de Bukkit/Paper.
- SHA-256: `0e31d28df0d143033f05b6b03c11ba02743ae49ae04cfe314b4f711ed332d8e3`.
- Informe HTML: `build/reports/tests/test/index.html`.
- Resultados JUnit XML: `build/test-results/test/`.

## Límites de la validación

No se ejecutó un servidor Paper real en esta corrección. En la validación inicial, la API oficial de descargas confirmó Paper 26.1.2 build 74, pero la descarga del servidor desde `fill-data.papermc.io` recibió HTTP 403 del proxy del entorno; esa descarga no se volvió a intentar en esta corrección. La prueba de carga y los flujos de inventarios se ejecutaron en MockBukkit. No se afirma una prueba de interacción con un cliente de Minecraft ni una validación de componentes agregados por otros plugins en un servidor real.

El JAR debe instalarse en un servidor Paper 26.1.2 con Java 25. Se recomienda comprobar allí el flujo con un jugador, especialmente objetos con componentes de otros plugins. Las instrucciones de compilación Windows se incluyen, pero no se ejecutaron en Windows.

El script cloud se ejecutó usando la instalación de Java y las dependencias descargadas durante esta tarea. También se verificaron su sintaxis y la instalación inicial de JDK y Gradle. No se ha comprobado restauración en una tarea cloud nueva ni publicación del snapshot.

## Regresiones corregidas en 1.0.1

Antes de modificar el código, se ejecutaron dos pruebas específicas y ambas fallaron: selección con una envoltura distinta del inventario personal (como sucede en Paper) y regreso desde una receta sin resultado. Después del cambio, la suite completa pasó. Las cuatro pruebas nuevas cubren:

- Selección desde una envoltura distinta, recorriendo inventario principal y hotbar, con objetos reales y cursor intactos.
- Volver desde una creación incompleta, liberar el bloqueo y crear otra receta con el mismo nombre.
- Mantener abierto un guardado manual inválido y permitir salir mediante Volver.
- Volver desde una receta existente sin resultado, conservar su versión guardada y permitir que otro administrador la edite.
