# Plan: Botón "Buscar actualizaciones" (auto-reemplazo robusto del fat JAR)

**Estado:** aprobado para ejecución · **Creado:** 2026-07-23 · **Pasos:** 5 (seriales, cada uno depende del anterior)
**Rama base sugerida:** `AutoUpdates` (rama actual del repo) — un commit/PR por fase: `autoupdate/0-pipeline-version`, `autoupdate/1-chequeo`, `autoupdate/2-descarga`, `autoupdate/3-reemplazo`, `autoupdate/4-entrypoint-ui`
**Modo:** directo (no hay `gh` CLI disponible en este entorno) — cada fase se integra por merge normal a `AutoUpdates`, sin automatizar PRs.

## Objetivo

Agregar un botón "Buscar actualizaciones" a la app que: chequea el último release de GitHub, descarga el fat JAR nuevo, verifica su integridad, y se auto-reemplaza — incluso si el JAR vive en una ruta protegida (ej. `Program Files`) donde el usuario no tiene permiso de escritura directo.

## Decisiones ya tomadas (no reabrir sin razón nueva)

1. **Sin instalador:** no hay jpackage. Se sigue distribuyendo como fat JAR vía `maven-shade-plugin`, lanzado por `ejecutar.bat` (`cd /d "C:\Sistema\app" && java -jar aptium.jar`). Este `.bat` **no se toca** — sigue funcionando igual, porque la app resuelve su propia ruta en runtime.
2. **Ruta del JAR:** se resuelve SIEMPRE vía `App.class.getProtectionDomain().getCodeSource().getLocation()`. Nunca hardcodear `C:\Sistema\app` en código nuevo.
3. **Nombre de asset fijo:** cada release en GitHub publica el fat JAR siempre como `aptium.jar` (no versionado en el nombre) + `aptium.jar.sha256`. Así la lógica de descarga no necesita parsear nombres variables.
4. **Arquitectura del flujo (revisado contra hexagonal-architecture + convenciones propias del proyecto):**
   - `ActualizacionService` es el ÚNICO punto de entrada público del feature completo (chequeo → descarga → verificación → generación de script → instalación). El controller de UI recibe solo esta clase — nunca varias piezas del flujo para orquestar él mismo el orden. Mismo patrón que `EquipoCorreccionService` componiendo internamente `EquipoDAO + MaterialDAO + AuditoriaDAO + CatalogoDAO`.
   - El único puerto/interfaz que hace falta es `IReleaseRepository` (para poder fakear la llamada de red en tests, igual que las DAOs ya están detrás de contratos). `DescargaService`, `ScriptDeReemplazoGenerator`, `ActualizacionInstaller` son colaboradores concretos de infraestructura sin lógica de negocio propia — no necesitan interfaz.
5. **Primera migración a producción es manual:** la versión que introduce este botón no puede autoactualizarse a sí misma (no existe el mecanismo en la versión vieja). Ese primer salto se hace reemplazando `aptium.jar` a mano, como hoy. No es una tarea de código de este plan.
6. **Riesgos aceptados sin mitigar en este plan:** SmartScreen/antivirus podría marcar el script o el JAR sin firmar como sospechoso (firmar código queda fuera de alcance); rate limit no autenticado de GitHub (60/h por IP) es aceptable porque el chequeo es manual, no periódico.

## Contexto general para cualquier agente frío

- App Swing de escritorio, Java 17, sin framework de DI — todo se cablea a mano en `AppContext` (`src/main/java/com/example/app/AppContext.java`), que es el **único** composition root.
- `UiCoordinator` (`src/main/java/com/example/app/ui/UiCoordinator.java`) es el único punto de la UI que ve el `AppContext` completo; cada controller declara en su constructor exactamente los services que usa — **no existe fachada intermedia** entre controller y services (regla explícita del proyecto, ver `CLAUDE.md`).
- Jerarquía de excepciones: `AptiumException` → subclases (`BusinessException`, `DataAccessException`, `ValidationException` con builder, `ResourceNotFoundException`, `DatabaseException`). Las excepciones nuevas de este feature deben colgar de `AptiumException`.
- Tests: JUnit 5 + Mockito + H2 en memoria, ~500 tests en `src/test/java` reflejando la estructura de `src/main/java`. Ningún test unitario debe pegarle a la red real ni a GitHub.
- Build: `mvn clean package` (genera `target/aptium.jar` vía shade plugin, mainClass `com.example.app.App`), `mvn test`, `mvn verify` (con JaCoCo).
- Pantalla de settings ya existente: `features/ajustes/view/PantallaAjustes.java` + `features/ajustes/controller/AjustesController.java` — es donde va el botón nuevo, sin crear una pantalla nueva en el `CardLayout`.
- `pom.xml` hoy tiene `<version>1.0-SNAPSHOT</version>` fijo, sin relación con tags de git.

---

## Fase 0 — Pipeline de release y versión embebida ✅ REALIZADA (2026-07-23)

**Riesgo:** bajo · **Depende de:** nada · **Toca:** `pom.xml`, `.github/workflows/release.yml` (nuevo), `src/main/resources/version.properties.template` (nuevo), `common/VersionInfo.java` (nuevo)

**Nota de implementación:** el filtrado nativo de Maven no renombra archivos, así que
`version.properties.template` → `version.properties` se resuelve con dos `<resource>`
(uno filtrado solo para el template, excluyendo binarios como los `.png` existentes)
más una ejecución de `maven-antrun-plugin` (`rename-version-properties`) atada a la fase
`process-classes`, que corre siempre después de `process-resources` sin importar el
orden de declaración de plugins. Verificado con `mvn clean package`: `version.properties`
queda embebido (`app.version=dev-SNAPSHOT`) en `target/classes` y en `target/aptium.jar`,
los `.png` de `src/main/resources` no se corrompieron (mismo tamaño), y los 577 tests
existentes siguen en verde (`BUILD SUCCESS`).

### Contexto para agente frío
No hace falta ninguna fase posterior para validar esta. El objetivo es únicamente: (a) que el JAR sepa su propia versión en runtime, y (b) que cada release en GitHub publique el asset con nombre predecible. Nada de esto depende de lógica de negocio.

### Tareas
1. En `pom.xml`, agregar `maven-resources-plugin` con filtering (o usar el que ya exista) para que `src/main/resources/version.properties.template` se copie filtrado a `version.properties` en el classpath, reemplazando `${app.version}` (propiedad Maven, con default `dev-SNAPSHOT` si no se pasa `-Dapp.version=...`).
2. Crear `src/main/resources/version.properties.template`:
   ```properties
   app.version=${app.version}
   ```
3. Crear `com.example.common.VersionInfo` (clase con método estático o instancia simple):
   - Carga `version.properties` del classpath (`getResourceAsStream`) al construirse.
   - Expone `String actual()`.
   - Si el recurso no existe o está vacío, devuelve `"dev-SNAPSHOT"` (no debe romper el arranque de la app — nunca lanzar excepción desde acá).
4. Crear `.github/workflows/release.yml`:
   - Trigger: push de tag `v*`.
   - Steps: checkout, setup JDK 17, `mvn -B -Dapp.version=${GITHUB_REF_NAME#v} clean package`, renombrar el jar generado a `aptium.jar` si el nombre del artifact no coincide, calcular `sha256sum aptium.jar > aptium.jar.sha256`, crear el release de GitHub subiendo ambos archivos como assets con `gh release create` o `softprops/action-gh-release`.

### Verificación
```bash
mvn clean package
# confirmar que target/classes/version.properties (o dentro del jar) tiene app.version=dev-SNAPSHOT
unzip -p target/aptium.jar version.properties
```
Revisión manual del YAML (sintaxis válida; no hace falta ejecutarlo realmente en esta fase).

### Criterios de salida
- `mvn clean package` sigue funcionando igual que antes (sin romper el build actual).
- `version.properties` queda embebido en el JAR con `dev-SNAPSHOT` en build local.
- El workflow es sintácticamente válido y no se ejecuta todavía contra ningún tag real (se prueba con el primer tag real cuando el feature esté completo, no antes).

### Rollback
Revert del commit — no toca `src/main/java` productivo, solo build/config.

---

## Fase 1 — Chequeo de versión nueva (solo lectura) ✅ REALIZADA (2026-07-23)

**Riesgo:** bajo · **Depende de:** Fase 0 (usa `VersionInfo`) · **Toca:** `common/constants/Constantes.java`, paquete nuevo `features/actualizaciones/`

**Nota de implementación:** la jerarquía de excepciones del proyecto usa el nombre real
`ApplicationException` (no `AptiumException`, que era el nombre en este plan) — `ActualizacionException`
cuelga de esa clase. `GithubReleaseClient` quedó creado con `java.net.http.HttpClient` + `org.json:json:20240303`
(agregado a `pom.xml`) tal como especifica la tarea 6, aunque no tiene test dedicado porque el plan solo
pide tests sin red real para `Version` y `ActualizacionService`. Verificado con
`mvn -Dtest=VersionTest,ActualizacionServiceTest test` y con `mvn clean package` completo (build entero
en verde, sin tests rotos).

### Contexto para agente frío
Esta fase NO toca filesystem del JAR target ni descarga nada pesado — solo determina "¿hay una versión más nueva que la mía?". Es la fase con más superficie de tests unitarios puros. `IReleaseRepository` es el puerto que permite testear sin red real (ver decisión de arquitectura #4 arriba).

### Tareas
1. En `Constantes.java`, agregar un bloque `Actualizaciones` con constantes: `GITHUB_OWNER`, `GITHUB_REPO`, `ASSET_JAR = "aptium.jar"`, `ASSET_CHECKSUM = "aptium.jar.sha256"`.
2. Crear `features/actualizaciones/model/Version.java`: value object inmutable, `implements Comparable<Version>`. Parsea strings tipo `v1.2.3` o `1.2.3` (con o sin prefijo `v`). Constructor o factory `Version.parse(String)` que lanza `IllegalArgumentException` en formato inválido.
3. Crear `features/actualizaciones/model/ReleaseInfo.java`: record con `String tag`, `Map<String, String> assets` (nombre → URL de descarga), `String changelog` (el `body` del release).
4. Crear `features/actualizaciones/exception/ActualizacionException.java extends AptiumException`.
5. Crear `features/actualizaciones/service/IReleaseRepository.java`: interfaz con un único método `ReleaseInfo obtenerUltimoRelease()`.
6. Crear `features/actualizaciones/service/GithubReleaseClient.java implements IReleaseRepository`: usa `java.net.http.HttpClient` para GET a `https://api.github.com/repos/{owner}/{repo}/releases/latest`, parsea la respuesta con `org.json` (agregar dependencia `org.json:json` al `pom.xml` si no está — versión estable reciente, revisar Maven Central antes de fijar el número). Mapea a `ReleaseInfo`. Envuelve errores de red/parseo en `ActualizacionException`.
7. Crear `features/actualizaciones/service/ActualizacionService.java` — versión embrionaria de esta fase: constructor recibe `IReleaseRepository` y `VersionInfo`; expone `Optional<ReleaseInfo> hayActualizacionDisponible()` que compara `Version.parse(release.tag())` contra `Version.parse(versionInfo.actual())`. (Este método se mantiene igual en la Fase 4; ahí se le agregan los métodos de descarga/instalación al mismo service, no una clase nueva.)

### Tests a escribir
- `VersionTest`: parseo con/sin prefijo `v`, comparación mayor/menor/igual, formato inválido lanza excepción, casos borde (`1.2.3` vs `1.2.10` — comparación numérica, no lexicográfica).
- `ActualizacionServiceTest`: con un fake/mock de `IReleaseRepository` — release más nuevo → `Optional` presente; release igual o más viejo → `Optional.empty()`; el repositorio lanza `ActualizacionException` → se propaga (no se traga silenciosamente).
- **No escribir tests que le peguen a la API real de GitHub** — usar siempre un fake de `IReleaseRepository`.

### Verificación
```bash
mvn test -Dtest=VersionTest,ActualizacionServiceTest
```

### Criterios de salida
- Los tests de esta fase pasan en verde sin ninguna de las fases siguientes implementadas.
- `ActualizacionService` no tiene ningún método de descarga/instalación todavía — eso es explícitamente de la Fase 4.

### Rollback
Revert del commit — código nuevo aislado en un paquete propio, sin tocar nada existente salvo `Constantes.java` (aditivo).

---

## Fase 2 — Descarga y verificación de checksum ✅ REALIZADA (2026-07-23)

**Riesgo:** medio (I/O real, pero a un staging propio, no al target protegido) · **Depende de:** Fase 1 (usa `ReleaseInfo`) · **Toca:** `features/actualizaciones/service/DescargaService.java` (nuevo)

**Nota de implementación:** los tests usan el servidor HTTP embebido de la JDK
(`com.sun.net.httpserver.HttpServer`) atado a `127.0.0.1` en un puerto efímero —
tal como sugería el plan, sin agregar ninguna dependencia nueva. Verificado con
`mvn test -Dtest=DescargaServiceTest` y con `mvn clean package` completo (build
entero en verde, incluyendo los ~580 tests existentes).

### Contexto para agente frío
Esta fase descarga bytes reales a una carpeta que el usuario siempre puede escribir (`%LOCALAPPDATA%\Aptium\updates\`), nunca a la ruta protegida del JAR en ejecución — ese problema es de la Fase 3, no de esta. Acá el único requisito es: descargar + verificar que no esté corrupto, y fallar limpio si algo no cierra.

### Tareas
1. Crear `features/actualizaciones/service/DescargaService.java`:
   - Método principal, algo como `Path descargarYVerificar(ReleaseInfo release, Consumer<Long> onBytesDescargados)`.
   - Resuelve el directorio de staging: `System.getenv("LOCALAPPDATA") + "\\Aptium\\updates\\"` (crear si no existe).
   - Descarga `release.assets().get(Constantes.Actualizaciones.ASSET_JAR)` a `aptium-{tag}.jar.part` (nombre temporal), y el asset de checksum a memoria o a un archivo temporal.
   - Al completar la descarga del JAR, calcula SHA-256 local (`MessageDigest.getInstance("SHA-256")`) y compara contra el valor esperado (parseado del contenido del asset `.sha256`, formato típico `<hash>  aptium.jar`).
   - Si coincide: renombra `.part` → `aptium-{tag}.jar` (nombre final) y devuelve el `Path`.
   - Si no coincide, o si falta algún asset: borra el archivo parcial y lanza `ActualizacionException` con mensaje claro.
   - Reporta progreso de bytes descargados vía el `Consumer<Long>` (para que la UI de la Fase 4 pueda mostrar una barra).

### Tests a escribir
- Con un fake/stub que sirva bytes controlados (no red real — usar un `HttpClient` de prueba, un servidor HTTP embebido mínimo, o refactorizar la descarga detrás de un método protegido/paquete-privado que el test pueda stubear):
  - Éxito: checksum coincide → devuelve el `Path`, el archivo existe y su contenido es el esperado.
  - Fallo: checksum no coincide → lanza `ActualizacionException`, no deja el archivo corrupto en el directorio final (a lo sumo el `.part`, que puede limpiarse).
  - Fallo: asset faltante en `ReleaseInfo.assets()` → lanza `ActualizacionException` antes de intentar la descarga.

### Verificación
```bash
mvn test -Dtest=DescargaServiceTest
```
Prueba manual opcional (no bloqueante para cerrar la fase): correr `DescargaService` contra un release real de GitHub y confirmar que el archivo baja y el checksum valida.

### Criterios de salida
- Tests en verde.
- Ningún test de esta fase toca la ruta real del JAR en ejecución — todo el I/O ocurre en el directorio de staging.

### Rollback
Revert del commit — clase nueva y aislada, sin efectos en el resto de la app.

---

## Fase 3 — Reemplazo robusto del JAR ✅ REALIZADA (2026-07-23, incluye prueba manual en Windows real)

**Riesgo:** alto · **Depende de:** Fase 2 (usa el `Path` del JAR descargado y verificado) · **Toca:** `features/actualizaciones/service/RutaJarResolver.java`, `ScriptDeReemplazoGenerator.java`, `ActualizacionInstaller.java` (todos nuevos)

**Nota de implementación:** las tres clases quedaron creadas con sus dos tests unitarios
(`RutaJarResolverTest`, `ScriptDeReemplazoGeneratorTest`, 5 tests en verde; `mvn clean package`
completo con 608 tests sin fallos). Decisiones de implementación:
- `RutaJarResolver` usa `App.class.getProtectionDomain()` (decisión #2). Corriendo desde el
  classpath de test devuelve el directorio de clases, no un JAR — el test documenta esa limitación.
- El **fallback manual del paso 5** necesita una URL de página de release que la firma de 4
  parámetros del generador (PID, staged, target, java.home) no incluía. Se resolvió derivándola
  dentro del generador como `https://github.com/{GITHUB_OWNER}/{GITHUB_REPO}/releases/latest`
  desde `Constantes`, sin agregar un 5º parámetro.
- El script captura **cualquier** fallo del `Move-Item` directo y reintenta con elevación UAC
  (no un tipo de excepción PowerShell específico): más robusto ante variantes de excepción, y
  respeta igual el requisito "sin UAC cuando no hace falta" porque el camino de éxito nunca entra
  al `catch`. `java.exe` se resuelve vía `Join-Path $javaHome 'bin\java.exe'` (no depende del PATH).
- `ActualizacionInstaller` lanza el script con `cmd /c start "" /min powershell -ExecutionPolicy Bypass
  -WindowStyle Hidden -File ...`, desacoplado y sin heredar streams, y **solo** llama `System.exit(0)`
  si el `ProcessBuilder.start()` no lanzó excepción (si falla el lanzamiento, propaga
  `ActualizacionException` sin cerrar la app).

**Bugs reales encontrados y corregidos durante la prueba manual (no detectables por JUnit):**
1. `ActualizacionInstaller.lanzarScriptDesacoplado`: `pb.redirectInput(ProcessBuilder.Redirect.DISCARD)`
   lanzaba `IllegalArgumentException` en runtime — `DISCARD` solo es válido para stdout/stderr, no
   para stdin. Corregido a `Redirect.from(new File("NUL"))` (dispositivo nulo de Windows).
2. `ScriptDeReemplazoGenerator`: el `Move-Item` elevado se invocaba con `-Command` en un proceso
   PowerShell hijo que **no hereda** `$ErrorActionPreference = 'Stop'` del script padre — un fallo
   ahí adentro no se reflejaba en el `ExitCode`, así que el script podía reportar éxito (código 0)
   aunque el `Move-Item` hubiera fallado. Corregido fijando `$ErrorActionPreference` explícitamente
   dentro del comando elevado y devolviendo `exit 0`/`exit 1` según corresponda.

**Nota de metodología:** la primera ronda de pruebas usó `jshell` como driver manual, lo cual dio
falsos negativos — el proceso `jshell.exe` (herramienta interactiva) retiene el JAR abierto en su
classpath de forma independiente del JVM "remoto" donde corre el snippet, así que el archivo seguía
bloqueado incluso después de que el snippet llamara `System.exit(0)`. Esto no ocurre en producción
(`java -jar aptium.jar` es un solo proceso). Se resolvió reemplazando `jshell` por un driver mínimo
de una clase (`DriverPruebaManual`, descartado al terminar, no forma parte del código productivo)
compilado y ejecutado como proceso `java` normal — fiel al comportamiento real de la app.

**Resultado del checklist manual (Windows real, sesión interactiva del usuario, 2026-07-23):**
| Punto | Resultado |
|---|---|
| 1. Carpeta con ACL restringida (`icacls`, deny-write solo a usuarios estándar, allow-full a Administradores/SYSTEM) + reemplazo aceptando UAC | ✅ PASS — cierra, pide UAC, reemplaza, relanza la app nueva, sin `.bak` residual |
| 2. Mismo caso cancelando el UAC | ✅ PASS — el jar queda intacto (hash original), sin `.bak` residual, staged sin tocar, relanza la app vieja igual (abre además la página de release como fallback) |
| 3. Carpeta sin restricción de ACL | ✅ PASS — reemplaza sin pedir UAC, sin `.bak` residual |
| 4. Ningún camino deja la app sin poder levantar | ✅ Confirmado en los 3 casos anteriores |

### Contexto para agente frío
Esta es la fase central del pedido original: el JAR no puede sobrescribirse a sí mismo mientras la JVM lo tiene abierto (bloqueo de archivo en Windows), y si además está en una ruta protegida (ACL deniega escritura al usuario actual), hace falta elevar privilegios (UAC) **solo para el paso puntual de mover el archivo**, no para toda la app. Son dos problemas independientes — no confundirlos: el bloqueo de archivo se resuelve esperando a que el proceso termine; el permiso denegado se resuelve reintentando ese único paso elevado.

**Importante:** esta fase no es completable solo con JUnit. El script generado corre fuera de la JVM, en un proceso desacoplado, y su comportamiento real (espera de proceso, UAC, reintentos) solo se puede confirmar en una máquina Windows real. Los criterios de salida incluyen un checklist de prueba manual — no se puede cerrar esta fase solo con tests automatizados en verde.

### Tareas
1. Crear `features/actualizaciones/service/RutaJarResolver.java`:
   - Método `Path resolverJarActual()` usando `App.class.getProtectionDomain().getCodeSource().getLocation().toURI()` → convertir a `Path`.
   - Debe funcionar sin importar dónde esté el JAR — no asumir `C:\Sistema\app` en ningún lado.
2. Crear `features/actualizaciones/service/ScriptDeReemplazoGenerator.java`:
   - Genera un script PowerShell (`.ps1`) en el directorio de staging con esta lógica (parametrizada: PID actual, ruta del JAR staged/verificado, ruta real del JAR target, ruta de `java.home`):
     1. `Wait-Process -Id <pid>` — espera a que el proceso actual termine.
     2. Copia `aptium.jar` (target) → `aptium.jar.bak` (mismo directorio) como respaldo.
     3. Intenta `Move-Item <staged> <target> -Force` sin elevar.
     4. Si falla por acceso denegado (capturar la excepción específica de PowerShell): relanza el script (o una porción mínima de él) vía `Start-Process powershell -Verb RunAs -ArgumentList ...` **solo para el paso de mover el archivo**, esperando su finalización.
     5. Si el usuario cancela el UAC o la elevación también falla: restaura `aptium.jar.bak` → `aptium.jar` (si el move parcial llegó a tocar el original), abre en el navegador default la página del release (fallback manual), y relanza la JVM vieja igual (`& "<java.home>\bin\java.exe" -jar <target>`) para que la app no quede cerrada.
     6. En éxito: relanza `& "<java.home>\bin\java.exe" -jar <target>` y termina.
   - El path de `java.home` se resuelve en el lado Java (`System.getProperty("java.home")`) y se pasa como parámetro al script — el script no debe depender de `java` estando en el PATH del sistema.
3. Crear `features/actualizaciones/service/ActualizacionInstaller.java`:
   - Método `void instalarYReiniciar(Path jarVerificado)`: resuelve PID actual (`ProcessHandle.current().pid()`), resuelve ruta target con `RutaJarResolver`, genera el script con `ScriptDeReemplazoGenerator`, lo lanza como proceso desacoplado (`ProcessBuilder` con `cmd /c start "" powershell -File <script>` oculto, sin heredar streams del proceso padre), y finalmente llama `System.exit(0)`.
   - Este método es el único punto donde la app decide cerrarse — no debe llamarse desde ningún lado salvo el flujo de instalación confirmado por el usuario (Fase 4).

### Tests a escribir
- `RutaJarResolverTest`: puede testear que devuelve un `Path` válido y existente quando se corre desde el classpath de test (documentar la limitación: en test corre desde `target/test-classes`, no desde un JAR real — el test valida que el método no explota y devuelve algo razonable, no el comportamiento completo bajo JAR empaquetado).
- `ScriptDeReemplazoGenerator`: test unitario que genera el script a un archivo temporal y verifica que el contenido incluye los placeholders esperados (PID, rutas, `Wait-Process`, `Start-Process ... -Verb RunAs`, el fallback de restauración) — es un test de contenido de texto, no de ejecución real.
- **No es posible ni deseable** testear con JUnit la ejecución real del script (UAC, espera de proceso, relanzamiento) — eso es la prueba manual de abajo.

### Prueba manual obligatoria (criterio de salida, no opcional)

Como la Fase 4 (botón de UI) todavía no existe, se dispara `ActualizacionInstaller`
directo con `jshell` contra el `aptium.jar` ya compilado — no hace falta la BD porque
solo se ejercitan las clases de `features/actualizaciones`, no la app completa.

**0. Preparar** (PowerShell, desde la raíz del repo):
```powershell
mvn clean package -q
$prot = "C:\AptiumTestProtegida"; $libre = "C:\AptiumTestLibre"; $stage = "C:\AptiumTestStage"
New-Item -ItemType Directory -Force -Path $prot,$libre,$stage | Out-Null
Copy-Item target\aptium.jar "$prot\aptium.jar"
Copy-Item target\aptium.jar "$libre\aptium.jar"
Copy-Item target\aptium.jar "$stage\aptium-vTEST.jar"   # "JAR nuevo" simulado (contenido real da igual para esta fase)
```

**Caso A — carpeta protegida, aceptando UAC:**
```powershell
icacls $prot /inheritance:r
icacls $prot /grant:r "$env:USERNAME:(RX)"        # solo lectura+ejecución, sin escritura
jshell --class-path "$prot\aptium.jar"
```
Dentro de `jshell`:
```java
import com.example.features.actualizaciones.service.*;
import java.nio.file.*;
new ActualizacionInstaller().instalarYReiniciar(Path.of("C:\\AptiumTestStage\\aptium-vTEST.jar"));
```
Esto cierra `jshell` (equivalente a que la app cierre). Confirmar:
- (a) el proceso `jshell` termina; (b) aparece el prompt de UAC; (c) al **aceptar**, `$prot\aptium.jar` queda reemplazado (comparar hash: `Get-FileHash $prot\aptium.jar` debe coincidir con `Get-FileHash C:\AptiumTestStage\aptium-vTEST.jar` antes de moverlo) y arranca un `java.exe -jar $prot\aptium.jar` nuevo (verificar en el Administrador de tareas — puede fallar al bootear por falta de BD, eso no invalida el test de reemplazo).
- Repetir el mismo caso pero **cancelando** el UAC: confirmar que `$prot\aptium.jar` queda igual al original (restaurado desde `.bak`) y que igual se relanza `java.exe` con el JAR viejo — la app nunca queda sin proceso vivo.

Reset entre corridas: `icacls $prot /reset /T` y volver a copiar `aptium.jar` limpio antes de repetir.

**Caso B — carpeta sin restricción:**
```powershell
jshell --class-path "$libre\aptium.jar"
```
Mismo comando de `instalarYReiniciar(...)` apuntando a un staged fresco. Confirmar que el reemplazo ocurre **sin** que aparezca ningún prompt de UAC.

**Limpieza:**
```powershell
Get-Process java,jshell,powershell -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*AptiumTest*" } | Stop-Process -Force
Remove-Item -Recurse -Force $prot,$libre,$stage
```

Documentar el resultado (pass/fail) de cada uno de los 4 puntos antes de dar la fase por cerrada.

### Verificación
```bash
mvn test -Dtest=RutaJarResolverTest,ScriptDeReemplazoGeneratorTest
```
Más el checklist manual de arriba, documentado con resultado (pass/fail por punto) antes de dar la fase por cerrada.

### Criterios de salida
- Tests unitarios en verde.
- Checklist de prueba manual completo y documentado (los 4 puntos), en una máquina Windows real.
- Ningún camino del script deja la app sin poder levantar (siempre hay relanzamiento, exitoso o de rollback).

### Rollback
Revert del commit. Si ya se probó en una máquina real y quedó algún `aptium.jar.bak` residual, limpiarlo manualmente (no es rollback de código, es limpieza de la máquina de prueba).

---

## Fase 4 — `ActualizacionService` como entry point único + UI ✅ REALIZADA (2026-07-23), incluye prueba manual end-to-end en Windows real

**Riesgo:** medio · **Depende de:** Fases 1, 2 y 3 · **Toca:** `ActualizacionService.java` (ampliar), `PantallaAjustes.java`, `AjustesController.java`, `AppContext.java`, `UiCoordinator.java`

**Nota de implementación:** `ActualizacionService` se amplió con `descargarActualizacion(ReleaseInfo, Consumer<Long>)`
e `instalarActualizacion(Path)`, ambos delegando directamente en `DescargaService` e
`ActualizacionInstaller` (nuevos parámetros de su constructor). El controller no orquesta
los sub-servicios — solo depende de `ActualizacionService`, con tres llamadas secuenciales
(`hayActualizacionDisponible` → `descargarActualizacion` → `instalarActualizacion`)
intercaladas con los diálogos de confirmación, porque esos diálogos son intrínsecamente
responsabilidad de la UI y no pueden vivir dentro del service sin romper la separación de capas.
El botón se agregó a `PantallaAjustes` (barra inferior, patrón `setOnX(Runnable)` como el
resto del paquete). `mvn verify` final: 622 tests en verde.

**Limitación de test documentada:** `AjustesControllerTest` solo verifica que el click dispara
`hayActualizacionDisponible()` en background (con y sin fallo). No se testean las ramas que
dependen de `JOptionPane` (confirmación, progreso, instalación) porque esos diálogos corren en
el EDT dentro del `SwingWorker` de `TareaUI`, y el mock estático de `JOptionPane` en Mockito es
por-thread — no intercepta llamadas hechas desde el EDT si se abre desde el thread del test.
Cubrir esas ramas requeriría inyectar una abstracción de diálogos testeable, que no estaba
pedida por este plan y sería una interfaz nueva "por las dudas" (anti-patrón ya listado abajo).

**Prueba manual end-to-end (Windows real, contra un release real de GitHub, 2026-07-23):
✅ PASS.** Se detecta la actualización, se descarga, se reemplaza el JAR sin caer en el
fallback y sin pedir UAC (carpeta sin ACL restringida — el camino con UAC ya se había
probado en la Fase 3), la app se relanza sola con la versión nueva, y al volver a chequear
informa correctamente que ya está actualizada. La ejecución de esta prueba manual encontró y
corrigió **cuatro bugs reales** que ningún test unitario detectó, todos con fix + test agregado:

1. **El repo era privado** — `GithubReleaseClient` pega a `api.github.com` sin autenticación
   a propósito (decisión #6), lo cual nunca iba a funcionar contra un repo privado. Se decidió
   con el usuario hacer el repo público (no es un cambio de código; se revisó primero que no
   quedara ninguna credencial vigente en el historial de git — había una vieja, ya en desuso,
   que se dejó tal cual a pedido del usuario).
2. **`Version.parse` no soportaba el versionado real del proyecto**: los tags reales usan un
   4° segmento de hotfix (`v1.1.4.2`), pero la regex solo aceptaba `major.minor.patch`. Se
   agregó un 4° segmento opcional (`hotfix()`), comparado numéricamente igual que el resto.
   Ver `Version.java` y `VersionTest`.
3. **`HttpClient` no seguía redirects** (`GithubReleaseClient` y `DescargaService`): el default
   de Java es `Redirect.NEVER`, y GitHub redirige (302) la descarga de assets hacia
   `objects.githubusercontent.com`. Se agregó `.followRedirects(HttpClient.Redirect.NORMAL)`
   a ambos clientes.
4. **`Move-Item -Force` no sobreescribe un destino que ya existe** (limitación documentada del
   cmdlet, a diferencia de `Copy-Item -Force`) — y el target siempre existe (es el JAR que se
   reemplaza), así que el reemplazo fallaba siempre, tanto sin elevar como elevado. Se agregó
   `Remove-Item` del target antes del `Move-Item` en ambos intentos (`ScriptDeReemplazoGenerator`).
5. **El `.ps1` generado no llevaba BOM UTF-8**: Windows PowerShell 5.1 (no `pwsh`) solo detecta
   UTF-8 en un script sin BOM cayendo al codepage ANSI del sistema, lo que corrompía cualquier
   carácter no-ASCII embebido en las rutas (ej. una tilde en el nombre de usuario de Windows) —
   rompiendo la ruta del JAR staged y el reemplazo directo. Se agregó BOM UTF-8 al escribir el
   script. Este bug no depende de tener o no un usuario con tilde: el fix es puramente defensivo
   para cualquier PC.

Diagnóstico de los bugs 4 y 5 se hizo agregando logging temporal al script generado
(`Log(...)` a un archivo `reemplazo.log` en el directorio de staging) para ver la causa real
sin depender de reproducir a mano — se mantuvo en el código final porque es útil para
diagnosticar problemas reales de usuarios en producción, no solo para esta prueba.

### Contexto para agente frío
Esta fase consolida todo lo anterior detrás de un único método público y lo conecta a la UI. El punto de arquitectura importante (ya revisado y acordado): el controller de Swing **no** debe llamar por separado a `DescargaService`, `ActualizacionInstaller`, etc. — recibe solo `ActualizacionService`, que internamente compone todo. Esto respeta tanto la regla del proyecto ("el controller declara en su constructor los services que usa, sin fachada intermedia") como el hallazgo de la revisión hexagonal (no dejar que el adaptador de UI orqueste el orden de los pasos del caso de uso).

### Tareas
1. Ampliar `ActualizacionService.java` (creado en Fase 1) agregando:
   - Constructor recibe también `DescargaService` y `ActualizacionInstaller` (además de `IReleaseRepository` y `VersionInfo` ya existentes).
   - Nuevo método público de alto nivel, ej. `void buscarYAplicarActualizacion(Consumer<String> onProgreso)` (o dividido en pasos si conviene para poder mostrar diálogos de confirmación intermedios — decidir según cómo quede más simple de cablear con `SwingWorker`, pero manteniendo TODO el orquestado dentro de esta clase, nunca en el controller).
2. En `features/ajustes/view/PantallaAjustes.java`: agregar un botón "Buscar actualizaciones" con su listener expuesto (getter o método `addBuscarActualizacionesListener`, siguiendo el patrón de listeners que ya usen otras pantallas de este mismo paquete — revisar `PantallaAjustes.java` actual antes de escribir esto).
3. En `features/ajustes/controller/AjustesController.java`: agregar `ActualizacionService` como nuevo parámetro de constructor (uno solo). Cablear el click del botón a un `SwingWorker` que:
   - Llama a `hayActualizacionDisponible()` en background.
   - Si no hay actualización: diálogo informativo simple.
   - Si hay: diálogo con versión + changelog (`ReleaseInfo.changelog()`) y botones "Actualizar ahora" / "Más tarde".
   - Si el usuario confirma: diálogo de progreso (usa el callback de `DescargaService` vía `ActualizacionService`), y al terminar, aviso explícito de que la app va a cerrarse y podría pedir permiso de administrador — **antes** de llamar al método que dispara `System.exit(0)` (para no sorprender al usuario con el prompt de UAC sin aviso previo, riesgo ya identificado).
   - Cualquier `ActualizacionException` en cualquier paso: diálogo de error legible, la app sigue corriendo normalmente (nunca debe crashear ni dejar la app en estado raro por un fallo de red o checksum).
4. En `app/AppContext.java`: instanciar `ActualizacionService` en `createDefault()`, componiendo `GithubReleaseClient`, `VersionInfo`, `DescargaService`, `ActualizacionInstaller` internamente; exponer getter `getActualizacionService()`.
5. En `app/ui/UiCoordinator.java`: pasar `context.getActualizacionService()` al construir `AjustesController`.

### Tests a escribir
- `AjustesControllerTest` (o ampliar el existente si ya hay uno): con un `ActualizacionService` fake/mock, verificar que el click del botón dispara el flujo esperado y que los distintos resultados (sin actualización, con actualización, error) producen el diálogo correcto — sin tocar Swing real donde se pueda evitar (seguir el patrón de tests de controllers ya existentes en el repo).

### Verificación
```bash
mvn verify
```
Prueba manual end-to-end: `mvn clean package`, ejecutar el JAR generado, ir a Ajustes, click en "Buscar actualizaciones" contra un release real de prueba en GitHub (puede ser un release de testing con una versión mayor a `dev-SNAPSHOT`), confirmar todo el flujo hasta el reemplazo real (reutilizando el checklist de la Fase 3 si aplica).

### Criterios de salida
- ✅ `AjustesController` tiene un solo parámetro nuevo en su constructor (`ActualizacionService`), no varios.
- ✅ `mvn verify` en verde (622 tests), cobertura razonable en la lógica nueva.
- ✅ Flujo completo probado manualmente de punta a punta contra un release real (Windows real, ver nota de implementación arriba).

### Rollback
Revert del commit. Si algo queda mal cableado en `AppContext`/`UiCoordinator`, el resto de la app no se ve afectada (el nuevo parámetro es aditivo en ambos constructores).

---

## Invariantes (verificar después de CADA fase)

- `mvn test` (o `mvn verify` desde la Fase 1 en adelante) verde.
- Ningún test unitario nuevo pega contra la red real ni contra el filesystem del JAR en ejecución real — todo detrás de fakes o en un staging aislado.
- `ejecutar.bat` sigue funcionando exactamente igual que hoy, sin modificaciones.
- Ninguna clase nueva asume la ruta `C:\Sistema\app` — siempre resuelta vía `RutaJarResolver`.

## Anti-patrones a evitar

- No dejar que `AjustesController` orqueste el orden de los pasos del flujo de actualización — eso vive enteramente en `ActualizacionService` (Fase 4).
- No crear una interfaz para `DescargaService`, `ScriptDeReemplazoGenerator` o `ActualizacionInstaller` "por las dudas" — son infraestructura de hoja, sin necesidad de abstracción adicional; solo `IReleaseRepository` la necesita (para poder testear sin red).
- No intentar reemplazar el JAR desde dentro de la misma JVM que lo tiene abierto — siempre vía el script externo desacoplado (Fase 3).
- No saltear la prueba manual en Windows real de la Fase 3 — es un criterio de salida obligatorio, no opcional, porque el comportamiento de UAC/bloqueo de archivo no es reproducible con JUnit.
- No versionar el nombre del asset del JAR en GitHub Releases (nada de `aptium-1.2.3.jar`) — rompe la lógica de descarga con nombre fijo.
