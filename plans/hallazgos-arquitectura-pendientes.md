# Hallazgos de arquitectura pendientes (revisión 2026-07-22)

Diagnóstico de la revisión profunda del 2026-07-22.

**Estado al 2026-08-27:** los 8 hallazgos **cerrados en código**. Lo único que queda de todos es la
**checklist manual de la Fase 5** de #6 (verificación contra la app real, no trabajo de código).

| # | Estado | Commit |
|---|---|---|
| #1 fallos silenciosos ortopedias | hecho | `de1af06` |
| #2 strategies muertas (`IMaterialFilter` + `ICapacidadCalculator`) | hecho | `a370e61` |
| #5 `common` ↔ Swing | hecho | `fc15bd1` |
| #4 `Object[]` → records | hecho (2026-07-23) | `57c87d1` |
| #3 `AppModel` | hecho (2026-07-23) — **disuelto** | `728c88d` |
| #6 concurrencia / EDT | **código cerrado** — Fases 1-6, **4b** y el hallazgo derivado de Lavadero. Falta sólo la checklist manual de la Fase 5, que se corre **sin** `strict` (los 5 autocompletados por tecla son excepción aceptada y en `strict` lanzan). Detalle en [`refactor-concurrencia-edt.md`](refactor-concurrencia-edt.md) | Fase 4b: `14354a2` · Lavadero: `95c9e33` |
| #7 subdivisión de `Equipo*` sin persistir (agregado 2026-08-19) | hecho (2026-08-27) | Pasos 1-8 `01d18be`..`ef6b8c2` + cierre `docs: ... (#7)` |
| #8 Lavadero (Ciclos + Clasificación) fuera del modelo EDT (agregado 2026-08-27, derivado de la verificación de #6/4b) | hecho (2026-08-27) — `CiclosController` colapsó sus 4 lecturas en un `recargar()` con el record `DatosCiclos` + `ConstructorVistaCiclos`, y sus 3 escrituras van por un helper `ejecutar(...)`; `ClasificacionController` y `LavaderoController.guardar()` al patrón de 4b. 970 tests, smoke pasado | `95c9e33` |

Las referencias de línea de abajo fueron **re-verificadas tras los commits de hoy**.

Contexto que se respetó en toda la revisión: la **no-atomicidad auditoría↔dato es
deliberada** (ver `plans/sacar-sql-de-equipo-otros-correccion-service.md`). No unificar
transacciones "por prolijidad".

---

## #3 — `AppModel`: fachada que ya se rompió y crece sin techo  ✅ HECHO (2026-07-23)

**Decisión del usuario:** *disolver* la fachada. `AppModel.java` fue **eliminado** (362 líneas).
`UiCoordinator` pasó a recibir `AppContext` y es ahora el único punto de la UI que lo ve
completo: le entrega a cada controller **solo los services de su alcance**, declarados en su
constructor y documentados con un javadoc de una línea.

Reparto resultante (13 archivos de `src/main` tocados, 0 tests — ninguno referenciaba `AppModel`):

| Controller | Services que recibe |
|---|---|
| `CDEViewController` | `Equipo`, `EquipoOtros` |
| `RegistrarEstadoController` | `Equipo`, `EquipoOtros`, `Material`, `IEstadoValidator` |
| `EquiposParaEntregarController` | `Equipo`, `EquipoOtros`, `Material`, `IEstadoValidator` |
| `CorreccionsController` | `EquipoCorreccion`, `EquipoOtrosCorreccion`, `CatalogoOtros` |
| `LotesController` | `Catalogo`, `Autoclave`, `Lote`, `Equipo`, `EquipoOtros` |
| `VerLotesController` | `Autoclave`, `Lote`, `LoteReporte` |
| `VerEquiposController` | `Equipo`, `EquipoOtros`, `Cliente`, `Institucion`, + los 2 reporte |
| `OrthopediaInputController` | `Cliente`(super), `Catalogo`, `Profesional`, `Institucion`, `Equipo` |
| `OtrosInputController` | `Cliente`(super), `CatalogoOtros`, `EquipoOtros` |
| `AjustesController` | `Cliente` |

**Lo que resolvió, punto por punto:**
- **(b) queda cerrado por diseño, no por disciplina:** `LotesController` ya no *puede* llamar
  `fusionarClientes()` — nunca ve `ClienteService`. El compilador reemplaza a la regla escrita.
- **Los 5 getters de servicio desaparecieron solos**, incluida la "excepción reconocida al patrón
  facade" de `getEquipoCorreccionService()`: `CorreccionsController` ya guardaba el service como
  campo, solo cambió de dónde lo recibe.
- **16 de los 47 métodos de delegación eran código muerto** (cero llamadores en `src/main`:
  `obtenerEquipoPorId`, `actualizarEquipo`, `contarEquipos`, `obtenerCatalogo`,
  `obtenerLotesFinalizados`, `obtenerMaterialesPorLote`, `obtenerVolumenesPorLote`,
  `obtenerLotesEnRango`, `obtenerClientesPorLote`, `obtenerMaterialesPorClientePorLote`,
  `obtenerOtrosPorClientePorLote`, `obtenerClientePorId`, `obtenerProfesionalPorId`,
  `obtenerInstitucionPorId`, `obtenerEquiposOtrosEntreFechas`, `obtenerEquiposEntreFechas`).
  Se fueron con la clase, sin necesidad de un refactor-clean aparte.
- **(a) el crecimiento lineal se cortó:** agregar una operación ya no toca un archivo compartido
  por toda la app; toca el controller que la usa y la línea de `UiCoordinator` que lo construye.

**Efectos colaterales que destapó:**
- `validarConexion()` era el **único** método de `AppModel` que no delegaba a un service. Se movió
  a `ConnectionPool.validarConexion()`, que es donde vive esa responsabilidad. `AppController`
  ahora la llama ahí.
- `ConstructorEquipo` declaraba un campo `AppModel model` **que nunca usaba** — `construir()` solo
  lee del panel. Se eliminó el parámetro; el constructor quedó en `(PantallaIngresoOrtopedia)`.
- `EquipoInputControllerBase` exponía `protected final AppModel model` a sus dos subclases, o sea
  que heredaban acceso a toda la API del sistema. Ahora tiene un `private final ClienteService`
  (lo único realmente común: el autocompletado de cliente) y cada subclase declara lo suyo.
- `App.main` perdió un paso entero: la secuencia de arranque bajó de 7 a 6 pasos. Actualizados
  `CLAUDE.md` y `README-DEPLOY.md` (incluida la salida de log esperada).

**Verificación:** `mvn compile` OK, **509 tests en verde**, `grep AppModel src/` sin resultados.

<details>
<summary>Diagnóstico original</summary>

**Qué:** `AppModel` (**362 líneas**, ~60 métodos de delegación de una línea). El javadoc dice
explícitamente *"No exponer servicios internos (get\*Service)"* y abajo hay **cinco** getters
de servicio (**[AppModel.java:341-359](../src/main/java/com/example/app/AppModel.java)** —
`getEquipoCorreccionService` 341, `getEquipoOtrosCorreccionService` 345, `getLoteReporteService` 349,
`getEquipoReporteService` 353, `getEquipoOtrosReporteService` 357), uno con excusa documentada
y cuatro sin ella. Los reporte-services se pasan directo desde `UiCoordinator` a los
controllers, salteando la fachada.

> Nota: en esta sesión se le agregó `esEntregable(EstadoEquipo)` (hallazgo #2), que es
> delegación legítima al `estadoValidator` — pero ilustra el punto (a): la fachada creció
> otra vez.

**Por qué es problema:**
- (a) Crece linealmente con cada feature; cualquier cambio recompila y re-testea toda la app.
- (b) Todo controller ve la API completa del sistema — `LotesController` puede llamar
  `fusionarClientes()`. La regla "no exponer servicios" se cumple por disciplina, no por diseño.

**Decisión que estaba pendiente (resuelta el 2026-07-23):** se eligió **disolver** — inyección
directa por controller. La alternativa descartada era mantener la fachada y hacer cumplir por
diseño la regla de no exponer servicios (p. ej. segregando en interfaces por rol), que conservaba
la capa de nombres semánticos pero dejaba vivo el problema (a): la interfaz también crece con
cada feature, con más archivos.

</details>

---

## #4 — `Object[]` posicional cruzando la frontera DAO→service  ✅ HECHO (2026-07-23)

Se creó el record [`FilaMaterial`](../src/main/java/com/example/features/equipos/ortopedias/dao/FilaMaterial.java)
(`id, equipoId, codigo, descripcion, cantidad, estado`) y `MaterialDAO.obtenerMaterial` /
`obtenerMaterialesPorCodigo` pasaron a devolverlo. `EquipoCorreccionService` ya no castea por
índice. Un único record sirve a las dos consultas: se completó cada `SELECT` con la columna que
le faltaba (`em.id` y `em.equipo_id`), que ya eran parámetros del `WHERE`, así que el record
viene siempre poblado y no hay campos "válidos según quién llamó". El mapeo quedó centralizado
en un `mapearFila(ResultSet)` privado que accede **por nombre de columna**, así que reordenar el
`SELECT` ya no rompe nada y cambiar el record falla al compilar.

**Requirió subir el proyecto de Java 11 a 17** (`maven.compiler.release`), porque `record` es
16+. Consecuencia operativa: el jar ahora exige **JRE 17+** en producción; se actualizaron
`README-DEPLOY.md` y `CLAUDE.md`. Verificado: 509 tests en verde, `mvn package` OK, bytecode
major 61.

**Pendientes menores que dejó, fuera del alcance de #4:**
- ~~`MaterialCorreccionDTO` es código muerto (cero referencias) y tiene los mismos 6 campos que
  `FilaMaterial`~~ → ✅ **borrado el 2026-08-27** en el refactor-clean.
- `MaterialDAO.obtenerMaterial` sigue devolviendo `null` cuando no encuentra; con Java 17
  disponible, `Optional` es ahora una opción. No se tocó por estar fuera del hallazgo.
- Quedan `Object[]` locales en `EquipoMaterialHelper` y `EquipoOtrosMaterialHelper` (se arman
  y consumen dentro del mismo método, no cruzan ninguna frontera) y en los table models de
  Swing (los exige la API de `DefaultTableModel`). Ambos casos se dejaron a propósito.

<details>
<summary>Diagnóstico original</summary>

**Qué:** DAOs devuelven `Object[]` / `List<Object[]>` que el service castea por índice.
Ejemplo en [EquipoCorreccionService.java:110-114](../src/main/java/com/example/features/equipos/ortopedias/service/EquipoCorreccionService.java):
```java
Object[] materialActual = materialDAO.obtenerMaterial(materialId);
Integer codigoAnterior      = (Integer) materialActual[0];
String  descripcionAnterior = (String)  materialActual[2];
```
También `List<Object[]>` en la línea 197. Origen en `MaterialDAO.obtenerMaterial` y
`MaterialDAO.obtenerMaterialesPorCodigo`. Re-medido hoy: **12 archivos en
`src/main/java`** contienen `Object[]` (el conteo original de "29 ocurrencias en 15
archivos" incluía tests). `EquipoCorreccionService` no fue tocado por los commits de
hoy, así que sus referencias de línea siguen vigentes.

**Por qué es problema:** si alguien reordena el `SELECT` del DAO, no falla la compilación:
falla en runtime con `ClassCastException`, o peor, no falla y guarda el dato equivocado en
la auditoría. El compilador no ayuda.

**Fix:** un `record` por cada forma (ej. 5 campos para el material). Elimina la clase de bug
entera. Barato y de bajo riesgo. Buen candidato para hacer primero entre los pendientes.

</details>

---

## #5 — La capa `common` depende de Swing  ✅ HECHO (`fc15bd1`)

Se separó en `Validador` (puro, en `common/util/`) + nuevo
[`RestriccionesCampo`](../src/main/java/com/example/ui/common/RestriccionesCampo.java)
en `ui/common/`, con los métodos renombrados a `soloNumeros` / `soloLetrasYEspacios`.
Actualizados los 4 call-sites (`PantallaIngresoOrtopedia` ×2, `PantallaCorrecciones`,
`AgregarMaterialDialog`, `PanelMateriales`).

Verificación: `grep -E "javax\.swing|java\.awt" src/main/java/com/example/common` → sin
resultados. El núcleo ya no depende de la UI.

**Pendientes menores que dejó — ✅ los dos cerrados el 2026-08-27:**
- ~~`Validador.esEmailValido` y `esNumeroPositivo` sin llamadores~~ → borrados en el refactor-clean.
- ~~No existe `ValidadorTest`~~ → creado, 15 tests sobre los 4 métodos que quedan
  (`noEstaVacio`, `esFormatoNombre`, `soloNumeros`, `detectarDuplicados`) + el constructor privado.

---

## #6 — Concurrencia ad-hoc y trabajo de BD en el EDT  (MEDIO)

**Qué:** tres modelos de concurrencia conviviendo:
- `new Thread()` crudo — **20 ocurrencias** en `src/main/java` (re-medido hoy; el conteo
  original decía "8 sitios"), incluido dentro de una **view**
  ([PantallaAuditoria.java:219](../src/main/java/com/example/features/equipos/ortopedias/view/PantallaAuditoria.java)).
- `SwingWorker` (VerLotesController, VerEquiposController).
- Llamadas sincrónicas directas.

`LotesController.cargarDatos()`
(**[línea 162](../src/main/java/com/example/features/lotes/controller/LotesController.java)** —
se corrió +1 por el import de `OcupacionAutoclave` en `a370e61`)
hace **cinco queries en serie sobre el EDT**, incluidas dos `obtenerTodos()` sin paginar —
y `UiCoordinator.crearRefrescador()` lo llama junto con otros cuatro `cargarDatos()` después
de cada guardado.

**Por qué es problema:** la UI se congela y el congelamiento **crece con el volumen de datos
históricos**. Sin cancelación: dos refrescos rápidos pueden aplicar resultados fuera de orden.

**Fix (dirección):** estandarizar en `SwingWorker` (o un helper propio), sacar todo acceso a BD
del EDT, y agregar cancelación/debounce al refresco global. Es el hallazgo de más trabajo y el
que conviene planificar con cuidado (toca varios controllers y el flujo de refresco).

**Estado:** se ejecuta en `plans/refactor-concurrencia-edt.md`. Fases 1-6 y 4b cerradas; el
hallazgo derivado de Lavadero (Ciclos + Clasificación) también, el 2026-08-27.

**Lo que queda del EDT, medido el 2026-08-27:** sólo los **cinco autocompletados por tecla**,
que son excepción aceptada — `AutocompleteListener` de clientes/profesionales/instituciones, el
`CatalogoLookup` de `OrthopediaInputController`, el de `catalogo_otros`, y el de clientes de
`LavaderoController`. Se disparan en cada pulsación y moverlos a fondo pide cancelación y orden
de resultados, que es un cambio aparte.

En `strict` esos cinco **lanzan**, lo que vuelve inutilizables los campos de cliente de Lavadero
y del CDE: por eso los smokes de esas pantallas se corren **sin** `strict`, verificando los WARNs
del log.

`LavaderoController.guardar()` también escribía en el EDT (se le escapó al inventario del
hallazgo de Lavadero, que sólo miró Ciclos y Clasificación). Arreglado el 2026-08-27 con el
patrón de la Fase 4b.

---

## #7 — La subdivisión de `Equipo*` no se persiste  ✅ HECHO (2026-08-27)

Blueprint de 9 pasos ([`fracciones-de-equipo-persistidas.md`](fracciones-de-equipo-persistidas.md))
ejecutado entero sobre `ConexionConCDE`, commit por paso. Resultado:

- **`V19`** — tabla `instancias_equipo_ciclo` + columna `instancia_equipo_id` en
  `elementos_ciclo_lavadero`. **`V20`** — `salidas_lavadero.elemento_ciclo_id` pasa a nullable +
  columna `instancia_equipo_id`. Ninguna migración existente se tocó.
- `SQL_DISPONIBLES` y la detección usan la fórmula
  `SUM(cantidad donde instancia IS NULL) + COUNT(DISTINCT instancia_equipo_id)`: un equipo repartido
  en N consume 1 unidad, no N.
- `CicloLavaderoDAO.crearInstanciaEquipo` (+ validación en el service); `CiclosController` crea las
  instancias antes de lanzar y **exige "Lanzar Todos"** para grupos repartidos (bloquea el lanzamiento
  individual y valida config completa del grupo).
- `AgrupadorInstanciasSalida` (clase plana, testeable) agrupa las N fracciones en 1 fila de Salidas,
  visible sólo cuando las N partes tienen ciclo finalizado. `SalidaLavaderoDAO` opera `marcarListo` /
  `volverALavado` / `derivar` sobre la instancia entera. Records `ElementoLavadoPendiente` / `SalidaLista`
  cambiaron `lavarropasNumero: int` → `lavarropas: String`.
- `CicloLavaderoDAO.detectarLineasSobregiradas()` — detección (sin reparación) de bases de desarrollo
  con datos previos.
- Defecto destapado por el test de integración y arreglado en el Paso 8: `SalidaLavaderoService.marcarListo`
  deduplicaba con `elementoCicloId` (ahora `null` en toda instancia), rompiendo el marcado masivo cuando
  dos equipos repartidos terminaban juntos — clave de duplicado nueva `claveDeDuplicado`.

**Verificación:** `mvn test` en verde (951+ tests), `mvn clean package` OK. Smoke manual de GUI
pendiente (fuera del blueprint).

<details>
<summary>Diagnóstico original (ALTO — agregado 2026-08-19)</summary>

**Qué:** cuando un `Equipo*` se reparte entre varios lavarropas, el `instanciaId` que agrupa sus
fracciones es un `AtomicInteger` en memoria de `CiclosController` (línea 46) y no viaja a la base:
`ElementoCicloMovimiento` no lo lleva y `elementos_ciclo_lavadero` no tiene dónde guardarlo.

**Por qué es problema:** un equipo de cantidad 1 repartido en 4 lavarropas escribe 4 filas de
`cantidad = 1` contra una línea de clasificación de cantidad 1 → `SQL_DISPONIBLES` calcula
`ya_procesada = 4 > 1`. El `HAVING` esconde el síntoma, así que la inconsistencia es invisible hasta
que otra feature suma esas filas. La pantalla de Salidas (rama `ConexionConCDE`) es la primera que lo
hace, y multiplica por 4 lo que manda al CDE.

**Encontrado por:** el smoke manual del Paso 8 de `salidas-lavadero-listo-y-derivacion-cde.md`.
No es deuda de ese plan: es anterior, del plan de ciclos.

**Diagnóstico completo, decisiones cerradas y blueprint paso a paso:**
[`fracciones-de-equipo-persistidas.md`](fracciones-de-equipo-persistidas.md).

</details>

---

## Lo que está bien (no tocar)
Jerarquía de excepciones + `ValidationException.Builder`; `TransactionalConnection`;
`SimpleEntityDAO` con detección de integridad por clase de SQLState `23`; el patrón de extraer
lógica de Swing a clases planas (`AgrupadorIngresosLote`, `ReconciliadorPendientes`,
`SincronizadorVolumenFinal`, `OcupacionAutoclave`); `AppContext` como composition root único.

## Plan de sesiones (definido al cerrar el 2026-07-22)

Cada ítem en un **chat nuevo**, no con `/compact`: los hallazgos son independientes y el
handoff son los commits + este doc, así que un chat limpio arranca más barato que un
resumen lossy.

1. ~~**#4** (`Object[]`→records)~~ — ✅ hecho el 2026-07-23.
2. ~~**#3** (`AppModel`)~~ — ✅ hecho el 2026-07-23, disuelto.
3. **#6** (concurrencia/EDT) — el más grande y el único que queda. Plan escrito el 2026-07-23
   en [`refactor-concurrencia-edt.md`](refactor-concurrencia-edt.md): 5 fases, a ejecutar en
   2 chats (fases 1-3 / fases 4-5). Es donde está el riesgo real: los bugs de EDT y de
   refrescos fuera de orden no los agarra la suite de tests.
   Nota: #3 dejó el terreno mejor — `UiCoordinator.crearRefrescador()` sigue igual, pero
   ahora cada controller declara qué services toca, así que es visible cuáles hacen I/O.
4. ~~**refactor-clean**~~ — ✅ hecho el 2026-08-27. Se borraron `Validador.esEmailValido` /
   `esNumeroPositivo` (de #5) y `MaterialCorreccionDTO` (de #4), los tres con cero referencias en
   `src/main` y `src/test`. Los 16 métodos muertos de `AppModel` ya se habían ido con la clase en #3.
   De paso se escribió el `ValidadorTest` que #5 anotaba como faltante (15 tests sobre los 4 métodos
   que quedan). Suite: **985 verdes** (eran 970).
   **Queda una decisión abierta:** `PantallaVerCDEv1` (69 líneas) se instancia y se registra en el
   `CardLayout` de `PantallaPrincipal:85,108`, pero `Constantes.Pantallas.VER_CDE` no se referencia
   desde ningún botón — la reemplazó `PantallaVerCDEv2`. Es inalcanzable; borrarla es una decisión
   del usuario, no del refactor-clean.
5. ~~**security review**~~ — ✅ hecha el 2026-08-27. **Sin hallazgos CRÍTICOS ni ALTOS.**
   Resultado en la sección "Revisión de seguridad" al final de este documento.
6. **code review de la branch** — usar `/code-review ultra`, que corre la revisión
   multi-agente en la nube y **no consume el contexto del chat**. Es user-triggered y
   facturado aparte. **Único ítem del plan de sesiones que sigue abierto.**

---

## Revisión de seguridad (2026-08-27)

**Sin hallazgos CRÍTICOS ni ALTOS.** La superficie es chica y bien tratada: app de escritorio,
sin endpoints, sin sesiones, sin HTML — no aplican XSS, CSRF ni SSRF.

**Lo que está bien:**

- **Inyección SQL: sin superficie.** Todo dato de usuario viaja por `PreparedStatement`. La única
  concatenación que arma SQL es el nombre de tabla en `SimpleEntityDAO` (`getTableName()`), y
  viene de un literal por subclase, nunca de entrada del usuario.
- **Cero secretos commiteados.** `config.properties` está en `.gitignore`; `README-DEPLOY.md` y
  `docs/conexion-remota-mysql-tailscale.md` usan placeholders (`TuContraseñaFuerte123!`,
  `192.168.1.100`, `<USUARIO>`); `.vscode/launch.json` no lleva credenciales.
- **Precedencia de config correcta:** variables de entorno → `config.properties` (fuera del repo,
  con instrucciones de `icacls`/permisos 600) → defaults, y el arranque **avisa por log** cuando
  cae a `localhost:root:root`. Falla ruidosamente si falta `db.ip`/`db.user`/`db.name`.
- **El runbook de Tailscale acota el firewall** a `100.64.0.0/10` (el CGNAT del tailnet) en vez de
  abrir 3306 al mundo, y ya advierte de no dejar `root@'%'`.

**Recomendaciones (MEDIO, ninguna bloqueante):**

1. **`GRANT ALL PRIVILEGES` para el usuario de la app.** Incluye `DROP`/`ALTER`, y la app además
   necesita `CREATE DATABASE` (`ConnectionPool.crearBaseDeDatosSiNoExiste`). Least privilege sería
   partirlo en dos usuarios: uno de migraciones con DDL, y uno de runtime con
   `SELECT/INSERT/UPDATE/DELETE`. Tiene costo operativo real (Flyway corre al arrancar la app), así
   que es una decisión, no un defecto.
2. **`bind-address = 0.0.0.0` protegido sólo por la regla de firewall.** El propio runbook avisa
   que la regla no persiste entre migraciones de servidor: si se pierde, MySQL queda escuchando en
   todas las interfaces. Atarlo a la IP de Tailscale es más robusto que depender del firewall.
3. **Las credenciales viven en cada puesto de trabajo.** Es consecuencia de la arquitectura
   —escritorio contra MySQL directo, sin capa de servidor—: quien tenga acceso a la PC puede leer
   `config.properties` o las variables de entorno y conectarse a la base por fuera de la app. La app
   tampoco tiene autenticación propia. Aceptado por diseño; anotado para que sea una decisión
   consciente y no una sorpresa.
