# Eliminar un ingreso completo, protegido con password, desde las pantallas de consulta

**Objetivo:** poder eliminar un ingreso completo, **en cualquier estado de avance** (incluidos
`ENTREGADO` y `FINALIZADO`), desde **Historial de Lavadero** y desde **Ver Equipos** (las dos
grillas: Ortopedias y Otros). Se pide una **password** y un **motivo obligatorio**. El ingreso
eliminado no vuelve a verse en ninguna pantalla. Antes de borrar, se guarda una **copia completa**
en un archivo dentro de la misma transacción.

**Rama:** `EliminarIngresos` (se crea desde `main` actualizado en la Sesión 1) · **Modo:** directo,
un commit por paso, sin PRs
**Fecha de creación:** 2026-09-29 · **Revisión adversarial:** ver el final del documento.

---

## Decisiones tomadas con el usuario (no reabrir)

| Tema | Decisión |
|---|---|
| Borrado | **Físico, con archivo previo.** No hay baja lógica: obligaría a filtrar unas 30 consultas en 7 DAOs, más las que parten de tablas hijas (Disponibles en Ciclos lee la clasificación sin pasar por el ingreso), y cada filtro olvidado haría reaparecer un ingreso borrado. Con el borrado físico las consultas existentes quedan intactas, y todo se concentra en un método por módulo que en **una** transacción (a) archiva el ingreso completo con motivo y fecha y (b) lo borra. **No se puede deshacer desde la UI**, y eso está aceptado. |
| Lote o ciclo en curso | **Nunca se elimina.** Se rechaza con un mensaje que dice qué hacer ("finalizá el lote X / el ciclo del lavarropas N y volvé a intentar"). La verificación va **dentro** de la transacción del borrado. No se desarma nada automáticamente. CDE: se rechaza si algún material está en un lote con `fecha_fin IS NULL`. Lavadero: se rechaza si algún elemento está en un ciclo sin finalizar, o si el `equipo_otros` derivado está en un lote en curso. |
| Qué se borra en Lavadero | Todo lo que representa al ingreso: `salidas_lavadero`, `instancias_equipo_ciclo`, `elementos_ciclo_lavadero`, la clasificación, las bolsas, el ingreso **y** el `equipo_otros` que creó `DerivadorIngresoCDE` (vinculado por `salidas_lavadero.equipo_otros_id`). Un ciclo **finalizado** que queda sin elementos se borra (sus insumos caen por `CASCADE`). |
| `equipo_otros` derivado **compartido** | **Se rechaza.** Un mismo `equipo_otros` puede tener ropa de **varios** ingresos de Lavadero (ver "Estado del que parte"). Si el derivado de este ingreso también tiene salidas de otro, el borrado aborta con un mensaje que nombra el ingreso del CDE y los otros ingresos de Lavadero, y que manda a eliminarlo primero desde Ver Equipos → Otros. Si el derivado es sólo de este ingreso, se borra con él. |
| Lote finalizado que queda vacío | **Queda vacío.** No deja huecos en la numeración (`id_negocio`) y conserva la constancia de que el autoclave corrió. Ver Lotes lo muestra sin materiales. |
| Formato del archivo | **Una tabla nueva `ingresos_eliminados` con snapshot JSON**, la misma para los tres tipos: columnas para buscar y el árbol completo en un JSON (`org.json`, ya está en el `pom`). **No** reusa `equipos_eliminados`/`materiales_eliminados` y **no** aparece en la pantalla de Auditoría. |
| Password | Hash **PBKDF2** (viene en el JDK, sin dependencias nuevas), con salt e iteraciones, en una tabla nueva. La migración carga una password inicial. Se cambia desde Ajustes pidiendo la actual. Si se olvida, un `UPDATE` documentado la vuelve a la inicial; se acepta porque quien puede correrlo ya tiene acceso a la base. **Nunca** se hardcodea en el código: el fat JAR se publica en GitHub Releases. `JPasswordField`, `char[]` limpiado después de usarlo, y nada en los logs. |
| Password inicial | **Sólo avisar.** Con la inicial se puede eliminar, pero el diálogo muestra un aviso para cambiarla en Ajustes. (El SQL de la migración viaja en el JAR público, así que la inicial es pública; se aceptó.) |

### Decisiones de diseño tomadas por el plan (con su porqué)

| Tema | Decisión | Por qué |
|---|---|---|
| **Guarda del CDE: `version`** | El borrado de un `equipos`/`equipo_otros` compara la `version` que se vio en el resumen previo (`SELECT … FOR UPDATE`) y además hace el `DELETE … AND version = ?` con `exigirFilaAfectada`. Si otro avanzó, entregó o corrigió algo mientras tanto, es **conflicto**. | Es el mismo criterio que Correcciones: una operación esporádica, irreversible y auditada, donde "otro tocó esto mientras lo mirabas" es información que el operador quiere ver. El falso positivo (dos operadores sobre materiales distintos) acá **es deseado**: lo que se archiva tiene que ser lo que el operador confirmó. Ver Equipos ya trae la `version` (verificado). |
| **Guarda de Lavadero: `estado` + conjunto de derivados** | `ingresos_lavadero` no tiene `version`, y **no se le agrega** (política de `CLAUDE.md`). La guarda es el `estado` que se vio **más** el conjunto de ids de `equipo_otros` derivados que mostró el resumen. Si cualquiera de los dos cambió, es conflicto. | El `estado` solo no alcanza: una derivación parcial crea un `equipo_otros` nuevo sin mover el estado del ingreso (sigue `LAVADO` hasta que sale todo), y el operador confirmaría un borrado que se lleva un ingreso del CDE que no vio. |
| **Resumen previo leído de la base** | Antes del diálogo se lee un `ResumenEliminacion` (por `TareaUI`): qué se va a borrar, qué lo bloquea y el token de guarda. El diálogo se arma con eso, no con la fila de la grilla. | La confirmación tiene que listar **todo** lo que desaparece: en Lavadero eso incluye ingresos del CDE que la grilla no muestra. Además, un bloqueo (lote o ciclo en curso, derivado compartido) se avisa **antes** de pedir la password. El resumen es informativo: la transacción **re-verifica todo**. |
| **Mismos bloqueos en el resumen y en la transacción** | `sealed interface Bloqueo` (`LoteEnCurso`, `CicloEnCurso`, `DerivadoCompartido`, `DerivadoEnLoteEnCurso`). El resumen los lista y la transacción lanza `EliminacionBloqueadaException(Bloqueo)`. El texto sale de **una** función plana. | Un solo texto por causa: el operador lee lo mismo si el bloqueo se vio antes o si apareció por una carrera. |
| **Rechazo por bloqueo ≠ conflicto** | `EliminacionBloqueadaException extends BusinessException`, **no** `ConflictoConcurrenciaException`. | Un lote en curso no es "otro se te adelantó": es un estado que el operador tiene que resolver (finalizar el lote o el ciclo). El conflicto (`CONFLICTO_ELIMINACION`) queda para "el ingreso cambió desde que lo viste" y para la contención de locks. |
| **El archivo va dentro de la transacción** | `INSERT INTO ingresos_eliminados` antes del `DELETE`, con la misma `Connection`. | Es la diferencia con Correcciones, que audita **después** del `DELETE` y acepta la no-atomicidad. Acá la copia **es** lo único que queda del ingreso: un borrado sin archivo no puede existir. |
| **Una fila de archivo por ingreso borrado** | El borrado de Lavadero escribe una fila `LAVADERO` y, además, una fila `OTROS` por cada derivado, con `archivo_padre_id` apuntando a la de Lavadero. | Así, buscar un ingreso del CDE por su id lo encuentra aunque haya caído junto con uno de Lavadero. |
| **`puesto` en el archivo** | Columna con `user.name@hostname` del puesto que borró. | La app no tiene usuarios. Es lo único que dice desde dónde se borró, y no cuesta nada. |
| **Borrar desde Ver Equipos un `equipo_otros` que vino de Lavadero** | **Se permite**, como hoy en Correcciones: `salidas_lavadero.equipo_otros_id` pasa a `NULL` por la FK (`V17`), y la salida conserva `destino = 'CDE_OTROS'`. El resumen lo avisa ("vino de Lavadero, ingreso #N; ese ingreso no se elimina"). | Es la única salida que tiene el operador ante un derivado compartido (decisión del usuario). Y el `SET NULL` ya está documentado como historia válida en `V17`. |
| **Los escritores concurrentes chocan, no fallan** | Seis escrituras existentes tratan "la fila ya no existe" como `SQLException` (error técnico). Pasan a `ConflictoConcurrenciaException` (Paso 5). | Hoy sólo Correcciones borra, y sólo equipos `NUEVO`. Con esta feature cualquier ingreso puede desaparecer bajo el staging de otro operador. Un "Error al lanzar lote" por algo que otro borró es el mismo error que el repo ya corrigió en cada guarda. |
| **Refresco después de borrar** | La pantalla que borró publica `consulta.recontando()` y pide su grupo, **y además** pide el grupo `operativo`. Ninguno más. | **No existe un refresco global**: hay cinco grupos (`UiCoordinator:81-85`), y las pantallas de consulta (Ver Lotes, Ver Ciclos, la otra) releen al mostrarse. Como no se pueden ver dos cards a la vez, el operativo cubre lo que queda visible en otro lado. El recuento evita páginas fantasma. |
| **Página fuera de rango después de recontar** | Si la página actual queda más allá de la última, se vuelve a pedir la última, **sin pintar** la vacía. | Borrar la única fila de la última página pasa a ser un caso común. |
| **Dónde vive cada cosa** | `common/eliminacion/` (modelo compartido y archivo), un eliminador por módulo en su feature, y `features/eliminaciones/` (service, clases planas, diálogo). La password va en `features/seguridad/`. | Los eliminadores de CDE y de Lavadero dependen del archivo. Si el archivo viviera en `features/eliminaciones/`, que a su vez depende de los eliminadores, habría un ciclo entre features. |
| **Lavadero borra en el CDE reusando el eliminador de Otros** | `EliminadorEquipoOtros` expone fases sobre una `Connection` ajena (bloquear, verificar, archivar, borrar). `EliminadorIngresoLavadero` las llama dentro de **su** transacción. | Es el segundo punto donde Lavadero escribe en tablas del CDE (el primero es `DerivadorIngresoCDE`). Se concentra igual: una sola clase, y ninguna consulta de borrado de CDE copiada en Lavadero. |

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.

### Reglas duras del repo que este plan debe respetar

1. **Ningún acceso a BD en el EDT.** Resumen, borrado, verificación y cambio de password corren en
   `TareaUI`. PBKDF2 tarda cientos de ms a propósito: en el EDT congelaría la UI.
2. **Estado mutable del controller sólo en el EDT.**
3. **Toda escritura que dependa de un dato leído lleva guarda y mira filas afectadas.** `0 filas` =
   la realidad cambió: se aborta la transacción entera, se avisa y se recarga. Sin reintento.
4. **Se toman todos los bloqueos antes de la primera lectura no bloqueante.** Bajo el
   `REPEATABLE READ` de MySQL, la vista se fija en la primera lectura no bloqueante. **H2 no lo
   delata** (corre en `READ COMMITTED` y no toma gap locks).
5. **La contención de locks sale como `ConflictoConcurrenciaException`** vía
   `ControlConcurrencia.esContencionDeLock(e)`. `catch (SQLTransactionRollbackException)` no alcanza:
   el 1205 viaja con `SQLSTATE HY000`.
6. **Un conteo de guarda nunca sale de `executeBatch()`** (`SUCCESS_NO_INFO` con
   `rewriteBatchedStatements=true`).
7. **Los services no tienen JDBC.** Validan y delegan.
8. **La lógica embebida en Swing va a una clase plana, sin Swing y con tests** (como
   `GuardaRefresco`: `JOptionPane` tira `HeadlessException` en los tests).
9. **Los textos para el operador van en `Constantes`.** Dicen **qué pasó y qué hacer**.
10. **Cada controller declara en su constructor los services que usa**; `UiCoordinator` los provee
    desde `AppContext`. No hay fachada.
11. **Migraciones: siempre nuevas, nunca se modifica una existente.**
12. **TDD, un commit por paso, `mvn test` en verde al final de cada paso.**

### Estado del que parte este plan (verificado contra el código, 2026-09-29)

**FKs que dictan el orden de borrado:**

| Tabla hija → padre | Acción | Migración |
|---|---|---|
| `equipo_materiales.equipo_id → equipos` | CASCADE | V1 |
| `equipo_materiales.lote_id → lotes` | SET NULL | V1 |
| `material_movimientos.(material_id, equipo_id)` | CASCADE | V1 |
| `equipo_otros_materiales.equipo_otros_id → equipo_otros` | CASCADE | V2 |
| `equipo_otros_materiales.lote_id → lotes` | SET NULL | V2 |
| `otros_material_movimientos.(material_id, equipo_otros_id)` | CASCADE | V2 |
| `lote_otros_volumenes.(lote_id, equipo_otros_id)` | CASCADE | V13 |
| `bolsas_lavadero.ingreso_id → ingresos_lavadero` | CASCADE | V7 |
| `elementos_clasificacion_lavadero.ingreso_id → ingresos_lavadero` | CASCADE | V9 |
| `elementos_ciclo_lavadero.ciclo_id → ciclos_lavadero` | CASCADE | V10 |
| `elementos_ciclo_lavadero.elemento_clasificacion_id → elementos_clasificacion_lavadero` | **RESTRICT** | V10 |
| `salidas_lavadero.elemento_ciclo_id → elementos_ciclo_lavadero` | **RESTRICT** | V17 |
| `salidas_lavadero.equipo_otros_id → equipo_otros` | SET NULL | V17 |
| `instancias_equipo_ciclo.elemento_clasificacion_id → elementos_clasificacion_lavadero` | **RESTRICT** | V19 |
| `elementos_ciclo_lavadero.instancia_equipo_id → instancias_equipo_ciclo` | **RESTRICT** | V19 |
| `salidas_lavadero.instancia_equipo_id → instancias_equipo_ciclo` | **RESTRICT** | V20 |
| `insumos_ciclo_lavadero.ciclo_id → ciclos_lavadero` | CASCADE | V24 |

De ahí sale el orden obligatorio en Lavadero:
**salidas → elementos_ciclo → instancias → ingreso** (la clasificación y las bolsas caen por
`CASCADE`) **→ ciclos finalizados vacíos**. En el CDE alcanza con borrar la cabecera: materiales,
movimientos y `lote_otros_volumenes` caen por `CASCADE`, y `salidas_lavadero.equipo_otros_id` pasa
a `NULL`.

**El borrado que ya existe (Correcciones), y por qué no se reusa:**
- `EquipoCorreccionService.eliminarEquipo` / `EquipoOtrosCorreccionService.eliminarEquipo` sólo
  permiten estado `NUEVO`, borran con `EquipoDAO.eliminarConVersion` / `EquipoOtrosDAO.eliminarEquipo`
  (un CAS `DELETE … AND version = ?` de una sentencia, en su **propia** conexión) y auditan
  **después**, fuera de la transacción, en `equipos_eliminados`/`materiales_eliminados`
  (`AuditoriaDAO.registrarEquipoEliminado`). Esa no-atomicidad está documentada y aceptada allá.
- Acá no sirve: el archivo tiene que ser atómico con el borrado, porque es lo único que queda. Y
  esas tablas no tienen dónde guardar los lotes, los movimientos ni nada de Lavadero. El usuario
  eligió una tabla nueva. **Correcciones no se toca.**

**Ver Equipos tiene la `version`:** `EquipoDAO.SQL_EQUIPOS_CON_MATERIALES` (que usa
`obtenerPorIds`, el camino de la página) selecciona `e.version`, y `EquipoOtrosDAO.mapearEquipo`
hace `setVersion`. De todos modos, **la versión que viaja como guarda es la del resumen previo**,
que es lo que el operador confirma.

**Un `equipo_otros` derivado puede mezclar ingresos de Lavadero.** `ConstructorIngresoCDE.construir`
arma **un `EquipoOtros` por cliente asignado** y suma las salidas del mismo elemento por nombre.
Con `AsignadorClienteAptium` todas las salidas de una derivación caen en el mismo ingreso, vengan del
cliente que vengan. No existe el vínculo material ↔ salida: por eso no se puede "descontar sólo su
parte" (y por eso el usuario eligió rechazar).

**Reporte de Otros y Ver Lotes (commit `bbd7bcf`).** El reporte muestra
`equipo_otros.volumen_equipo`, que acumula **sólo** los litros de **ese** ingreso
(`lote_otros_volumenes` es por lote e ingreso). Al borrar el equipo desaparece su fila del reporte
y su fila de `lote_otros_volumenes`. Los demás ingresos del mismo lote conservan sus litros.
**No hay recálculo que hacer.** `lotes.capacidad_usada` queda como estaba: es lo que se cargó en el
autoclave, un hecho histórico. Ver Lotes muestra el lote con los materiales que queden, o vacío
(decisión del usuario). Ver Ciclos pierde los elementos del ingreso. El ciclo finalizado que queda
vacío se borra.

**No existe un "refresco global".** `UiCoordinator` tiene cinco `Disparador`es: `operativo`,
`verEquipos`, `historialLotes`, `historialCiclos` e `historialLavadero`. Las pantallas de consulta
releen en `componentShown`, y Clasificación, Ciclos y Salidas, desde el listener de su botón de
menú. `VerEquiposController` y `HistorialLavaderoController` hoy reciben **sólo** su propio
disparador.

**Seis escritores tratan "la fila ya no existe" como error técnico** (`throw new SQLException(…)`
→ `DatabaseException`):

| Dónde | Flujo |
|---|---|
| `MaterialDAO:132-133` | Registrar Estado, ortopedias |
| `EquipoOtrosDAO:643` (cabecera) y `:707` (material) | Registrar Estado, otros |
| `LoteDAO:772` (ortopedias), `:927` (cabecera otros) y `:995` (material otros) | Lanzar lote |

Además, `SalidaLavaderoDAO.derivar` no traduce la contención de locks (`marcarListo` sí lo hace).
Los demás ya chocan bien ante una fila borrada: entregas (CAS), Correcciones (CAS de `version`),
Clasificación (`SQL_MARCAR_CLASIFICADO` → `CONFLICTO_CLASIFICACION`), `lanzarTanda` (línea sin fila
→ saldo 0 → `SaldoConsumidoException`), `marcarListo` (saldo inexistente → conflicto), `volverALavado`
(CAS) y la parte de `estamparDestino` de `derivar` (CAS). **El Paso 5 lo re-verifica** uno por uno.

**Órdenes de bloqueo existentes** (contra los que se diseña el borrado):

| Operación | Orden |
|---|---|
| `MaterialDAO.aplicarMovimientos` / `EquipoOtrosDAO.aplicarMovimientos` | materiales `FOR UPDATE` → cabecera (recálculo) |
| Entregas (`entregarMateriales`, `entregar`) | materiales (CAS, ordenados) → cabecera |
| `LoteDAO.lanzarLote` | **lectura no bloqueante** (`MAX(secuencia)`) → `INSERT lotes` → materiales `FOR UPDATE` → cabecera |
| `LoteDAO.finalizarLote` / `marcarLoteFallo` | `lotes` (UPDATE CAS) → materiales `FOR UPDATE` → cabecera |
| Correcciones (`bumpVersionConGuarda`) | **cabecera → materiales** (el único al revés) |
| `CicloLavaderoDAO.lanzarTanda` | `lavarropas` X → `ciclos_lavadero` (gap) → líneas de clasificación (ascendente) → `INSERT`s |
| `CicloLavaderoDAO.finalizarCiclo` | `ciclos_lavadero` (UPDATE CAS) → `ingresos_lavadero` (UPDATE) |
| `ClasificacionLavaderoDAO.guardar` | `ingresos_lavadero` (UPDATE CAS) → `INSERT` líneas |
| `SalidaLavaderoDAO.marcarListo` | `elementos_ciclo` → `instancias` (ascendente) → `INSERT salidas` |
| `SalidaLavaderoDAO.derivar` | `INSERT equipo_otros` → `salidas` (UPDATE CAS) → `ingresos_lavadero` (UPDATE) |

**Otros hechos:**
- **Próxima migración libre: `V28`** (la última es `V27__lavarropas_sin_capacidad.sql`). Flyway
  corre con `outOfOrder(true)`. Re-verificar al empezar cada paso que agrega una.
- **`V17` dice "Nada del lavadero se borra nunca".** Con esta feature eso deja de ser cierto. No se
  toca la migración: se documenta en `CLAUDE.md` (Paso 9).
- **`FusionClientesDAOTest.todaTablaConFkAClientes_estaContempladaEnLaFusion`** compara
  `FusionClientesDAO.REFERENCIAS` contra el `INFORMATION_SCHEMA`. Por eso **`ingresos_eliminados` no
  lleva FK a `clientes`**: guarda el nombre como texto, igual que `equipos_eliminados`.
- `TransactionalConnection` revierte en el `close()` si no hubo `commit` (verificado en el plan de
  entregas).
- Hay ~1637 tests (`CLAUDE.md`).
- La jerarquía de excepciones es `ApplicationException` → `BusinessException` (→
  `ConflictoConcurrenciaException`), `ValidationException`, `ResourceNotFoundException` y
  `DatabaseException`.

### Archivos de referencia

| Para | Leer |
|---|---|
| CAS + filas afectadas + contención | `common/dao/ControlConcurrencia.java` |
| Transacción | `infrastructure/db/TransactionalConnection` y `MaterialDAO.aplicarMovimientos` como molde |
| Bloquear todo antes de leer | `CicloLavaderoDAO.exigirSaldoSuficiente` + javadoc de `SQL_BLOQUEAR_LINEA`, `SalidaLavaderoDAO.bloquearAfectados` |
| Orden `lavarropas → ciclos` | javadoc de `CicloLavaderoDAO.SQL_LAVARROPAS_ACTIVO` y de `LavarropasDAO.darDeBaja` |
| Escritura cruzada Lavadero → CDE | `lavadero/dao/derivadores/DerivadorIngresoCDE`, `EquipoOtrosDAO.guardar(Connection, …)` |
| Borrado de Correcciones (NO se reusa) | `EquipoCorreccionService.eliminarEquipo`, `EquipoOtrosCorreccionService.eliminarEquipo` |
| Pantallas paginadas | `VerEquiposController`, `HistorialLavaderoController`, `ConsultaEquipos`, `ConsultaHistorial`, `common/paginacion/Pagina` |
| Clase plana de decisión | `ui/common/GuardaRefresco` |
| Forma de los tests de concurrencia | `infrastructure/db/ConcurrenciaOptimistaTest` |
| Ajustes | `ajustes/view/PantallaAjustes`, `ajustes/controller/LavarropasAjustesController` (molde de pestaña con `TareaUI`) |

### Comandos

```bash
mvn test
mvn verify
mvn test -Dtest=NombreDeClase
mvn clean package && java -jar target/aptium.jar   # smoke manual (SIN -Daptium.edt.strict=true)
```

---

## Grafo de dependencias

```
Paso 1 (archivo: V28 + ArchivoIngresosDAO + modelo común)          Paso 2 (password: V29 + PBKDF2 + service)
   │                                                                   │            │
   ▼                                                                   │            ▼
Paso 3 (eliminar equipo del CDE: ortopedias + otros)                   │     Paso 7 (Ajustes: cambiar password)
   │                                                                   │            │
   ▼                                                                   │            │
Paso 4 (eliminar ingreso de Lavadero, incluido el derivado)            │            │
   │                                                                   │            │
   └──────────────► Paso 6 (service de eliminación + clases planas) ◄──┘            │
                           │                                                        │
Paso 5 (los escritores     ▼                                                        │
concurrentes chocan) ──► Paso 8 (UI: Ver Equipos + Historial) ◄─────────────────────┘
                           │
                           ▼
                  Paso 9 (revisión + seguridad + docs + cierre)
```

El **Paso 5 no depende de nada**: sus tests borran con SQL directo. Puede correr en paralelo con los
Pasos 1 a 4.

| Paso | Modelo sugerido | Archivos que toca |
|---|---|---|
| 1 | **Opus 5.5**, alto | `V28__ingresos_eliminados.sql`, `common/eliminacion/*` (nuevo), tests |
| 2 | **Opus 5.5**, alto | `V29__password_eliminacion.sql`, `features/seguridad/*` (nuevo), `Constantes`, `AppContext`, tests |
| 3 | **Opus 5.5**, alto | `common/eliminacion/` (bloqueos y resumen), `EliminadorEquipoOrtopedia`, `EliminadorEquipoOtros` (nuevos), `Constantes.Mensajes`, tests, `ConcurrenciaOptimistaTest` |
| 4 | **Opus 5.5**, muy alto | `EliminadorIngresoLavadero` (nuevo), `Constantes.Mensajes`, tests, `ConcurrenciaOptimistaTest` |
| 5 | **Opus 5.5**, alto | `MaterialDAO`, `EquipoOtrosDAO`, `LoteDAO`, `SalidaLavaderoDAO`, sus tests, `ConcurrenciaOptimistaTest` |
| 6 | **Sonnet 5.5**, alto | `features/eliminaciones/service` + `controller/helpers` (nuevos), `AppContext`, tests |
| 7 | **Sonnet 5.5**, alto | `ajustes/view/PanelPasswordEliminacion`, `ajustes/controller/PasswordAjustesController`, `PantallaAjustes`, `UiCoordinator`, clase plana + test |
| 8 | **Sonnet 5.5**, alto | `EliminarIngresoDialog`, `PantallaVerEquipos`, `PantallaHistorialLavadero`, `VerEquiposController`, `HistorialLavaderoController`, `ConsultaEquipos`/`ConsultaHistorial`, `UiCoordinator`, tests |
| 9 | **Opus 5.5**, alto | `CLAUDE.md`, memoria, este archivo |

**Invariantes verificados después de CADA paso:**
- [ ] `mvn test` en verde
- [ ] Toda escritura nueva con guarda usa `executeUpdate()` + `exigirFilaAfectada`/`exigirFilasAfectadas`
- [ ] Todos los `FOR UPDATE` de una transacción van antes de su primera lectura no bloqueante
- [ ] Cero JDBC en services · cero I/O en el EDT
- [ ] Ninguna migración existente modificada (`git diff --stat main -- src/main/resources/db/migration` sólo lista archivos nuevos)
- [ ] La password no aparece en ningún log, mensaje de excepción, `toString` ni nombre de `TareaUI`

---

## Paso 1 — El archivo de ingresos eliminados

### Contexto (autocontenido)

Antes de borrar un ingreso se guarda una copia completa en `ingresos_eliminados`, **dentro de la
misma transacción** que el borrado. Este paso crea la tabla, el DAO que escribe una fila sobre una
`Connection` ajena y el modelo común que comparten los tres eliminadores. Todavía nadie lo llama.

Decisión del usuario: **una sola tabla** para los tres tipos (`ORTOPEDIA`, `OTROS`, `LAVADERO`), con
columnas para buscar y el árbol completo en JSON (`org.json`, ya en el `pom`). No aparece en
Auditoría.

### Tareas

1. **Migración `V28__ingresos_eliminados.sql`.** Re-verificar primero que `V28` está libre.
   ```sql
   CREATE TABLE ingresos_eliminados (
       id                  INT AUTO_INCREMENT PRIMARY KEY,
       modulo              VARCHAR(20)  NOT NULL,   -- ORTOPEDIA | OTROS | LAVADERO (ModuloIngreso.name())
       ingreso_id_original INT          NOT NULL,
       cliente_nombre      VARCHAR(150) NULL,
       fecha_ingreso       TIMESTAMP    NULL,
       estado              VARCHAR(50)  NULL,
       motivo              VARCHAR(500) NOT NULL,
       puesto              VARCHAR(255) NULL,
       archivo_padre_id    INT          NULL,       -- la fila LAVADERO que arrastró a este OTROS
       fecha_eliminacion   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
       snapshot            LONGTEXT     NOT NULL,
       INDEX idx_ing_elim_modulo_id (modulo, ingreso_id_original),
       INDEX idx_ing_elim_fecha (fecha_eliminacion)
   );
   ```
   El comentario de la migración explica tres cosas:
   - **por qué no hay FK**, ni a `clientes` (el archivo guarda nombres como texto, y
     `FusionClientesDAOTest` exigiría contemplar la tabla en la fusión) ni `archivo_padre_id` → `id`
     (es un archivo, no se borra nada de acá);
   - **por qué sin `CHECK` ni `AFTER`** (patrón del repo, H2 en modo MySQL);
   - que a partir de acá **Lavadero sí se borra**, lo que desmiente el comentario de `V17`, que no
     se toca.
2. **`common/eliminacion/ModuloIngreso`** (enum `ORTOPEDIA`, `OTROS`, `LAVADERO`). Se persiste con
   `name()`, así que su javadoc avisa que renombrar un valor rompe el archivo.
3. **`common/eliminacion/IngresoArchivado`** (record): `modulo`, `ingresoIdOriginal`,
   `clienteNombre`, `fechaIngreso` (`LocalDateTime`, nullable), `estado`, `motivo`, `puesto`,
   `archivoPadreId` (`Integer`, nullable) y `snapshot` (`String` con el JSON ya armado).
   - El constructor compacto valida que `motivo` no sea blanco, que tenga ≤ 500 caracteres
     (`Constantes.Eliminacion.MOTIVO_MAX_LARGO`) y que `snapshot` no sea vacío.
   - Sin `char[]` ni nada sensible: el `toString` por defecto es seguro.
4. **`common/eliminacion/ArchivoIngresosDAO.archivar(Connection conn, IngresoArchivado a) → int`**.
   - `INSERT` con `RETURN_GENERATED_KEYS` sobre la conexión **del llamador**. No abre ni cierra
     nada, y propaga `SQLException`, como `EquipoOtrosDAO.guardar(Connection, …)`.
   - Javadoc: por qué va adentro de la transacción del borrado (el archivo es lo único que queda) y
     la diferencia con Correcciones (que audita después).
5. **`common/eliminacion/PuestoDeTrabajo.actual() → String`**: `System.getProperty("user.name") + "@"
   + InetAddress.getLocalHost().getHostName()`. Si el hostname falla, sólo el usuario. **Nunca
   lanza**: el puesto es informativo y no puede impedir un borrado.
6. **`common/eliminacion/SnapshotJson`**, una clase plana con los helpers que usan los eliminadores
   para armar el árbol:
   - `JSONObject raiz(ModuloIngreso, int formato)` con `"formato": 1`, para poder evolucionar el
     contenido sin migrar filas viejas;
   - un conversor de `ResultSet` → `JSONArray` que respeta los nombres de columna y convierte
     `Timestamp` a ISO-8601 y `NULL` a `JSONObject.NULL`.

   Así cada eliminador escribe "qué tablas" y no "cómo serializar".
7. **Tests:**
   - `ArchivoIngresosDAOTest` (H2): `archivar_devuelveElIdYPersisteTodasLasColumnas`,
     `archivar_usaLaConexionDelLlamador_rollbackNoDejaFila` (abre `TransactionalConnection`, archiva
     y cierra sin `commit` → 0 filas), `archivar_padreNull_yConPadre`.
   - `IngresoArchivadoTest`: `motivoBlanco_lanza`, `motivoDeMasDe500_lanza`, `snapshotVacio_lanza`.
   - `SnapshotJsonTest`: `resultSetAJsonArray_timestampEnIsoYNullComoJsonNull`,
     `raiz_llevaModuloYFormato`.
   - `PuestoDeTrabajoTest`: `actual_nuncaLanzaYEmpiezaConElUsuario`.

### Verificación

```bash
mvn test -Dtest='ArchivoIngresosDAOTest,IngresoArchivadoTest,SnapshotJsonTest,PuestoDeTrabajoTest,FusionClientesDAOTest'
mvn test
```

### Criterio de salida

- [ ] `V28` nueva; ninguna migración existente tocada
- [ ] `FusionClientesDAOTest` sigue verde (la tabla no tiene FK a `clientes`)
- [ ] `ArchivoIngresosDAO` no abre conexiones propias
- [ ] Commit: `feat: tabla y dao de archivo de ingresos eliminados`

---

## Paso 2 — La password de eliminación

### Contexto (autocontenido)

Eliminar un ingreso pide una password configurable. Se guarda como **hash PBKDF2** (JDK, sin
dependencias), con salt e iteraciones propios de cada fila. La migración carga una **password
inicial** cuyo valor queda documentado en `CLAUDE.md` junto con el `UPDATE` de reseteo. Como el SQL
viaja en el JAR público, la inicial es pública, y el usuario decidió **sólo avisar** mientras siga
vigente.

**Qué protege y qué no — escribirlo así en el javadoc del service:** es una **barrera contra borrados
accidentales o no autorizados de operadores**, no un límite de seguridad contra quien tiene las
credenciales de la base (están en `config.properties` y permiten todo, incluido el `UPDATE` de
reseteo). Con eso se justifica que no haya rate limiting más allá del costo de PBKDF2.

### Tareas

1. **Migración `V29__password_eliminacion.sql`** (re-verificar el número):
   ```sql
   CREATE TABLE passwords (
       proposito      VARCHAR(40)  PRIMARY KEY,     -- 'ELIMINAR_INGRESO'
       algoritmo      VARCHAR(40)  NOT NULL,        -- 'PBKDF2WithHmacSHA256'
       iteraciones    INT          NOT NULL,
       salt           VARCHAR(64)  NOT NULL,        -- Base64
       hash           VARCHAR(128) NOT NULL,        -- Base64
       es_inicial     BOOLEAN      NOT NULL,
       actualizado_en TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
   );
   INSERT INTO passwords (proposito, algoritmo, iteraciones, salt, hash, es_inicial)
   VALUES ('ELIMINAR_INGRESO', 'PBKDF2WithHmacSHA256', 600000, '<salt>', '<hash>', TRUE);
   ```
   - **Password inicial: `aptium`.** El salt y el hash se generan **una vez** con el
     `HasherPbkdf2` de este paso (un test o un `main` descartable, que no se commitea) y se pegan
     literales. El comentario de la migración dice que el valor inicial está en `CLAUDE.md`
     ("Eliminar ingresos → password"), **no** lo escribe en claro, y explica por qué una tabla por
     propósito y no una clave-valor genérica.
   - 600 000 iteraciones: la recomendación de OWASP para PBKDF2-HMAC-SHA256. Van **en la fila**,
     para poder subirlas después sin invalidar el hash vigente.
2. **`features/seguridad/model/HashPassword`** (record): `algoritmo`, `iteraciones`, `salt` (`byte[]`)
   y `hash` (`byte[]`), con copias defensivas.
   - `equals`/`hashCode`/`toString` **sobreescritos**: el `toString` no imprime ni salt ni hash, y
     la igualdad **no** se usa para verificar.
3. **`features/seguridad/HasherPbkdf2`** (clase plana, sin estado salvo `SecureRandom`):
   - `HashPassword hashear(char[] password)`: salt nuevo de 16 bytes, clave de 256 bits;
   - `boolean verificar(char[] candidata, HashPassword guardado)`: recalcula con el salt y las
     iteraciones **del guardado** y compara con **`MessageDigest.isEqual`** (tiempo constante);
   - `PBEKeySpec.clearPassword()` en un `finally`. El hasher **no** limpia el `char[]` que recibe:
     es de quien lo creó (ver el Paso 6);
   - un constructor package-private con iteraciones configurables, **sólo para tests** (600 000 en
     cada test los haría lentos).
4. **`features/seguridad/dao/PasswordDAO`**:
   - `HashPassword leer(String proposito)` y `boolean esInicial(String proposito)`;
   - `void reemplazar(String proposito, HashPassword anterior, HashPassword nuevo)`: un
     `UPDATE … SET …, es_inicial = FALSE, actualizado_en = CURRENT_TIMESTAMP WHERE proposito = ? AND
     hash = ? AND salt = ?` con `exigirFilaAfectada(…, Mensajes.CONFLICTO_PASSWORD)`. Dos cambios
     simultáneos: gana uno y el otro choca, en vez de pisar en silencio.
5. **`features/seguridad/service/PasswordEliminacionService`** (sin JDBC):
   - `void verificar(char[] password)`: lanza `PasswordIncorrectaException` (nueva, `extends
     BusinessException`, mensaje `Mensajes.PASSWORD_INCORRECTA`) si no coincide o si viene vacía.
     El mensaje **no** dice si falló por vacía o por distinta;
   - `boolean esInicial()`;
   - `void cambiar(char[] actual, char[] nueva, char[] repetida)`: verifica la actual; valida con el
     builder (nueva == repetida, `nueva.length >= Constantes.Eliminacion.PASSWORD_MIN_LARGO` = 6,
     nueva ≠ actual); hashea y llama a `reemplazar`;
   - **ningún `log` recibe un argumento derivado de la password.** Se loguea "password de
     eliminación cambiada desde {puesto}" y "verificación fallida", sin más datos.
6. **`AppContext`**: construir `PasswordDAO`, `HasherPbkdf2` y `PasswordEliminacionService`, y
   exponer el getter del service.
7. **`Constantes`**: `Mensajes.PASSWORD_INCORRECTA` ("La contraseña no es correcta. No se eliminó
   nada."), `CONFLICTO_PASSWORD` ("Otro puesto cambió la contraseña mientras tanto. Volvé a
   intentar con la contraseña nueva."), los de validación del cambio, y `Eliminacion.PASSWORD_MIN_LARGO`.
8. **Tests:**
   - `HasherPbkdf2Test`:
     - `verificar_laMismaPassword_true`, `verificar_otra_false`;
     - `hashear_dosVeces_saltsDistintos`;
     - `verificar_usaLasIteracionesDelGuardado` (un hash hecho con N verifica con un hasher
       configurado en M);
     - `hashPassword_toStringNoExponeSaltNiHash`.
   - **`PasswordDAOTest` (H2, con Flyway): `semillaDeLaMigracion_verificaConLaPasswordInicialDocumentada`**,
     con el hasher real de 600 000. Es el único test lento, y es el que garantiza que el
     `CLAUDE.md` no miente. También `reemplazar_conHashViejo_conflicto` y
     `reemplazar_apagaEsInicial`.
   - `PasswordEliminacionServiceTest` (Mockito):
     - `verificar_incorrecta_lanzaPasswordIncorrecta`, `verificar_vacia_lanzaElMismoMensaje`;
     - `cambiar_actualIncorrecta_noReemplaza`;
     - `cambiar_nuevaYRepetidaDistintas_validation`, `cambiar_nuevaCorta_validation`,
       `cambiar_nuevaIgualALaActual_validation`;
     - `cambiar_ok_reemplazaConHashNuevo`.
9. **Revisión de seguridad propia del paso** (se anota el resultado en el commit):
   `grep -rn -i "password\|contrase" src/main --include=*.java`. Cada aparición en un `log.` o en un
   mensaje de excepción tiene que ser texto fijo, sin la variable.

### Verificación

```bash
mvn test -Dtest='HasherPbkdf2Test,PasswordDAOTest,PasswordEliminacionServiceTest'
mvn test
```

### Criterio de salida

- [ ] La password inicial **no** aparece en claro en ningún archivo de `src/` (`grep -rn "aptium'" src/main/resources/db` → nada)
- [ ] Comparación con `MessageDigest.isEqual`; `PBEKeySpec.clearPassword()` en `finally`
- [ ] La semilla de `V29` verifica contra la password documentada (test)
- [ ] Commit: `feat: password de eliminacion con hash pbkdf2`

---

## Paso 3 — Eliminar un equipo del CDE (Ortopedias y Otros)

### Contexto (autocontenido)

Ver Equipos va a poder borrar un `equipos` o un `equipo_otros` en **cualquier estado**. Este paso
hace la lectura del resumen previo y la transacción de borrado, sin UI ni password: eso llega en los
Pasos 6 y 8. Requiere el Paso 1.

La transacción: **(1) bloquear todo, (2) verificar la guarda y los bloqueos, (3) leer el snapshot y
archivarlo, (4) borrar la cabecera con CAS.** Los materiales, los movimientos y
`lote_otros_volumenes` caen por `CASCADE`. `salidas_lavadero.equipo_otros_id` pasa a `NULL`.

### Tareas

1. **Modelo común** en `common/eliminacion/`:
   - `record IngresoAEliminar(ModuloIngreso modulo, int id)`;
   - `sealed interface Bloqueo` con `LoteEnCurso(String loteIdNegocio)`,
     `CicloEnCurso(int lavarropasNumero)`, `DerivadoCompartido(int equipoOtrosId, List<Integer>
     otrosIngresosLavadero)` y `DerivadoEnLoteEnCurso(int equipoOtrosId, String loteIdNegocio)`. Los
     dos últimos se usan en el Paso 4; se definen acá para que el tipo esté cerrado desde el
     principio;
   - `EliminacionBloqueadaException extends BusinessException`, que lleva `List<Bloqueo>` y arma su
     mensaje con `TextoBloqueos.describir(List<Bloqueo>)`;
   - **`TextoBloqueos`**, clase plana: una línea por bloqueo, con plantillas en
     `Constantes.Mensajes.ELIMINAR_BLOQUEO_*` que dicen **qué hacer**. Por ejemplo: "El material está
     en el lote 2026-15, todavía en curso. Finalizá ese lote y volvé a intentar.";
   - `sealed interface ResumenEliminacion permits ResumenEquipo, ResumenIngresoLavadero`:
     - `ResumenEquipo(IngresoAEliminar ingreso, String clienteNombre, LocalDateTime fechaIngreso,
       String estado, int version, List<LineaMaterial> materiales, List<Bloqueo> bloqueos,
       Integer ingresoLavaderoOrigen)`;
     - `LineaMaterial(String descripcion, int cantidad, String estado, String loteIdNegocio)`;
     - `ResumenIngresoLavadero` lo completa el Paso 4. Se puede dejar declarado con sus campos
       mínimos.
2. **`equipos/ortopedias/dao/EliminadorEquipoOrtopedia`** (nuevo):
   - **`ResumenEquipo resumir(int equipoId)`**: lectura sin transacción. Cabecera con cliente,
     institución, paciente, estado y `version`; materiales con descripción, cantidad, estado y el
     `id_negocio` de su lote; y los bloqueos (`LoteEnCurso` por cada lote con `fecha_fin IS NULL`).
     Sin fila → `ResourceNotFoundException`, con un mensaje que dice que el ingreso ya no existe
     (otro lo borró).
   - **`void eliminar(int equipoId, int versionVista, String motivo, String puesto)`** con
     `TransactionalConnection`, **en este orden y documentado en el javadoc**:
     1. `SELECT id, lote_id FROM equipo_materiales WHERE equipo_id = ? ORDER BY id FOR UPDATE`;
     2. `SELECT version FROM equipos WHERE id = ? FOR UPDATE`. Sin fila o con otra versión →
        `ConflictoConcurrenciaException(CONFLICTO_ELIMINACION)`;
     3. **recién acá, las lecturas no bloqueantes:** los lotes en curso entre los `lote_id` de (1)
        (`SELECT id_negocio FROM lotes WHERE id IN (…) AND fecha_fin IS NULL`). Si hay alguno →
        `EliminacionBloqueadaException`;
     4. el snapshot: cabecera con los nombres, materiales con su `id_negocio` de lote y todos sus
        `material_movimientos`. Archivar con `ArchivoIngresosDAO.archivar`;
     5. `DELETE FROM equipos WHERE id = ? AND version = ?` → `exigirFilaAfectada(…,
        CONFLICTO_ELIMINACION)`;
     6. `commit`. `SQLException`: `esContencionDeLock` → `ConflictoConcurrenciaException
        (CONFLICTO_ELIMINACION)`; si no, `DatabaseException`.
   - **Javadoc del orden** (la parte que ningún test defiende):
     - **materiales → cabecera** es el orden de `aplicarMovimientos`, de las entregas, de
       `lanzarLote` y de `finalizarLote`, así que con ellos no se cruza;
     - **Correcciones es el único al revés** (`bumpVersionConGuarda`: cabecera → materiales). Un
       borrado contra una corrección simultánea del mismo equipo puede dar un deadlock, que MySQL
       resuelve abortando a uno: el borrado lo informa como conflicto. Se acepta: las dos son
       operaciones esporádicas;
     - **`lotes` se lee sin bloquear, y a propósito.** `finalizarLote` bloquea `lotes` →
       materiales, así que un `FOR UPDATE` sobre `lotes` después de los materiales cruzaría los
       órdenes. Leerlo sin bloquear es **conservador**: un lote que se está finalizando sin commitear
       se ve en curso, y el borrado se rechaza. Uno ya finalizado no se reabre nunca. Y ningún
       material puede **entrar** a un lote nuevo mientras sus filas están bloqueadas: `lanzarLote`
       las toma `FOR UPDATE`;
     - por qué el `FOR UPDATE` de (1) va por `equipo_id` y no por id: toma el rango del índice
       `idx_equipo_material_equipo`, así que también frena el `INSERT` de un split que
       `lanzarLote`/`aplicarMovimientos` harían sobre ese equipo.
3. **`equipos/otros/dao/EliminadorEquipoOtros`** (nuevo), con **fases públicas sobre una
   `Connection` ajena**, para que Lavadero las reuse en su transacción (Paso 4):
   - `ResumenEquipo resumir(int equipoOtrosId)`, igual que ortopedias. `ingresoLavaderoOrigen` se
     llena si alguna `salidas_lavadero` apunta a este equipo. Un REMITO sin filas se resume con
     `remito_cantidad`.
   - `BloqueoEquipoOtros bloquear(Connection, int id)`: **(0)** `SELECT id FROM salidas_lavadero
     WHERE equipo_otros_id = ? ORDER BY id FOR UPDATE` → **(1)** materiales `ORDER BY id FOR
     UPDATE` → **(2)** `SELECT version FROM equipo_otros WHERE id = ? FOR UPDATE`. Devuelve un
     record con `version`, `loteIds` y `salidaIds`. Si la cabecera no existe, devuelve un resultado
     vacío y quien llama decide.
     - Javadoc de **(0)**: el `DELETE` de la cabecera escribe `salidas_lavadero` por el `SET NULL`
       de la FK. Tomarla **primero** deja el orden salidas → materiales → cabecera, compatible con
       el borrado de Lavadero, que llega a estas salidas desde su lado. Sin esto, los dos borrados
       podrían cruzarse.
   - `List<Bloqueo> verificar(Connection, BloqueoEquipoOtros)`: lotes en curso, **sin bloquear**
     (misma razón que ortopedias).
   - `void archivarYBorrar(Connection, int id, int versionEsperada, String motivo, String puesto,
     Integer archivoPadreId)`: snapshot (cabecera, materiales, movimientos, `lote_otros_volumenes`
     con el `id_negocio` del lote, salidas de Lavadero que apuntaban acá) → archivar → `DELETE … AND
     version = ?` con `exigirFilaAfectada`.
   - `void eliminar(int id, int versionVista, String motivo, String puesto)`: la transacción propia.
     `bloquear` → conflicto si falta la cabecera o si la versión difiere → `verificar` → si hay
     bloqueos, `EliminacionBloqueadaException` → `archivarYBorrar` → `commit`, con la misma
     traducción de la contención.
   - **Javadoc de clase:** "el segundo punto donde Lavadero escribe en tablas del CDE, junto con
     `DerivadorIngresoCDE`: las fases existen para que ninguna consulta de borrado del CDE se
     copie en Lavadero".
4. **`Constantes.Mensajes`**: `CONFLICTO_ELIMINACION` ("Otro operador modificó este ingreso
   mientras lo mirabas. No se eliminó nada: revisá la versión actualizada y volvé a intentar."),
   `INGRESO_YA_ELIMINADO` y los `ELIMINAR_BLOQUEO_*`.
5. **Tests** (H2 con Flyway; los datos con los DAOs existentes o con SQL directo, como el resto):
   - `EliminadorEquipoOrtopediaTest`:
     - `resumir_listaMaterialesConLoteYBloqueoSiHayLoteEnCurso`;
     - `resumir_inexistente_resourceNotFound`;
     - `eliminar_entregado_borraYArchivaConMaterialesYMovimientos`, con el JSON parseado;
     - `eliminar_conMaterialEnLoteEnCurso_rechazaYNoBorraNada`;
     - `eliminar_conLoteFinalizado_borraYElLoteQuedaSinSusMateriales`: el lote sigue existiendo,
       con `capacidad_usada` intacta;
     - `eliminar_conVersionVieja_conflictoYNoArchivaNada`;
     - `eliminar_yaEliminado_conflicto`;
     - `eliminar_fallaElArchivo_noBorra` (con un `motivo` inválido forzado a nivel DAO, o un
       archivador que falla, para verificar la atomicidad).
   - `EliminadorEquipoOtrosTest`: los equivalentes, más:
     - `eliminar_conLitrosEnLoteCompartido_soloDesapareceSuFilaDeLoteOtrosVolumenes`;
     - `eliminar_derivadoDeLavadero_laSalidaQuedaConDestinoYEquipoNull`;
     - `eliminar_remitoSinFilas_archivaRemitoCantidad`;
     - `resumir_derivadoDeLavadero_informaElIngresoDeOrigen`.
   - **`ConcurrenciaOptimistaTest`**, con la forma *A lee → B modifica y commitea → A escribe → se
     rechaza, y el estado final es exactamente el de B*:
     - `eliminarOrtopediaQueOtroMetioEnUnLote`: A resume; B lanza un lote con un material
       (`LoteDAO.lanzarLote`); A elimina → `EliminacionBloqueadaException`. El equipo sigue, y el
       material, `ESTERILIZANDO` en el lote de B;
     - `eliminarOtrosQueOtroMetioEnUnLote`;
     - `eliminarOrtopediaQueOtroAvanzo`: B avanza un material (`aplicarMovimientos`); A elimina con
       la versión vieja → conflicto, y queda el estado de B;
     - `eliminarOrtopediaYaEliminadaPorOtro`: B elimina, y A, con la misma versión, choca. Queda
       **una** fila en `ingresos_eliminados`.

### Verificación

```bash
mvn test -Dtest='EliminadorEquipoOrtopediaTest,EliminadorEquipoOtrosTest,ConcurrenciaOptimistaTest'
mvn test
```

### Criterio de salida

- [ ] En las dos transacciones, todos los `FOR UPDATE` van antes de la primera lectura no bloqueante (revisar a ojo y dejarlo en el javadoc)
- [ ] Ningún `FOR UPDATE` sobre `lotes`
- [ ] Archivo y `DELETE` en la misma transacción; el test de atomicidad en verde
- [ ] Commit: `feat: eliminar equipo del cde con archivo y guardas`

---

## Paso 4 — Eliminar un ingreso de Lavadero (incluido su derivado al CDE)

### Contexto (autocontenido)

El borrado más delicado: siete tablas propias con tres FKs `RESTRICT`, ciclos compartidos con otros
ingresos, un `equipo_otros` derivado que puede estar en el CDE (en cualquier estado, o en un lote), y
cinco escritores concurrentes con órdenes de bloqueo distintos. Requiere los Pasos 1 y 3 (usa las
fases de `EliminadorEquipoOtros`).

**Reglas del usuario:**
- se rechaza si algún elemento del ingreso está en un **ciclo sin finalizar**;
- se rechaza si el derivado está en un **lote en curso**;
- se rechaza si el derivado es **compartido** con otro ingreso de Lavadero;
- un ciclo **finalizado** que queda sin elementos se borra.

### Tareas

1. **`ResumenIngresoLavadero`** (en `common/eliminacion/`): `IngresoAEliminar ingreso`,
   `clienteNombre`, `fechaIngreso`, `EstadoIngresoLavadero estado`, `BigDecimal pesoTotalKg`,
   `List<LineaElemento> elementos` (nombre, cantidad clasificada), `List<DerivadoCde> derivados`
   (`equipoOtrosId`, estado, cantidad de materiales) y `List<Bloqueo> bloqueos`. El token de guarda
   es `estado` + `Set<Integer> idsDerivados()`.
2. **`lavadero/dao/EliminadorIngresoLavadero`** (nuevo), con `EliminadorEquipoOtros` y
   `ArchivoIngresosDAO` inyectados:
   - `ResumenIngresoLavadero resumir(int ingresoId)`: lectura sin transacción, con los mismos
     bloqueos que va a verificar la transacción:
     - `CicloEnCurso(lavarropas)`;
     - `DerivadoCompartido(equipoOtrosId, otros ingresos de Lavadero)`;
     - `DerivadoEnLoteEnCurso`, delegando en `EliminadorEquipoOtros.resumir`.
   - **`void eliminar(int ingresoId, EstadoIngresoLavadero estadoVisto, Set<Integer> derivadosVistos,
     String motivo, String puesto)`**. En una `TransactionalConnection`, **en este orden**:

     **Fase A — bloquear todo (ninguna lectura no bloqueante antes del final de esta fase):**
     1. `SELECT estado FROM ingresos_lavadero WHERE id = ? FOR UPDATE`. Sin fila o con otro estado →
        `ConflictoConcurrenciaException(CONFLICTO_ELIMINACION)`;
     2. las líneas: `SELECT id FROM elementos_clasificacion_lavadero WHERE ingreso_id = ? ORDER BY id
        FOR UPDATE`;
     3. las tandas: `SELECT id, ciclo_id, instancia_equipo_id FROM elementos_ciclo_lavadero WHERE
        elemento_clasificacion_id IN (…) ORDER BY id FOR UPDATE`;
     4. las instancias: `SELECT id FROM instancias_equipo_ciclo WHERE elemento_clasificacion_id IN (…)
        ORDER BY id FOR UPDATE`;
     5. las salidas: `SELECT id, equipo_otros_id FROM salidas_lavadero WHERE elemento_ciclo_id IN (…)
        OR instancia_equipo_id IN (…) ORDER BY id FOR UPDATE`;
     6. los derivados = los `equipo_otros_id` distintos y no nulos de (5). Si **no** son exactamente
        `derivadosVistos` → conflicto. Para cada uno, en orden ascendente,
        `EliminadorEquipoOtros.bloquear(conn, id)`. Si la cabecera falta (otro lo borró desde Ver
        Equipos) → conflicto.

     **Fase B — verificar (acá empiezan las lecturas no bloqueantes):**

     7. los ciclos en curso entre los `ciclo_id` de (3) (`fecha_fin IS NULL`) → `CicloEnCurso`;
     8. por derivado: sus `salidaIds` de `bloquear` (que ya incluyen las de **otros** ingresos,
        porque el paso 0 de `bloquear` toma todas las que apuntan al equipo) menos las de (5). Si
        sobra alguna → `DerivadoCompartido`, con los ingresos de Lavadero de esas salidas. Después,
        `EliminadorEquipoOtros.verificar` → `DerivadoEnLoteEnCurso`;
     9. si hay bloqueos → `EliminacionBloqueadaException` con **todos** (no sólo el primero).

     **Fase C — archivar y borrar:**

     10. el snapshot del ingreso: cabecera con cliente y peso, bolsas, líneas con el nombre del
         elemento, tandas con ciclo, lavarropas, fechas y configuración, instancias y salidas con
         destino y fechas. Archivar → `archivoId`;
     11. por derivado, `EliminadorEquipoOtros.archivarYBorrar(conn, id, versión de su bloquear,
         motivo, puesto, archivoId)`;
     12. `DELETE FROM salidas_lavadero WHERE id IN (…)`. Si no hay derivados, las salidas siguen ahí;
         si los hubo, el paso 11 ya les puso `NULL` por `SET NULL`, y las filas siguen existiendo
         igual. Es una guarda: `exigirFilasAfectadas` con el total de (5), usando `executeUpdate()`,
         nunca `executeBatch()`;
     13. `DELETE FROM elementos_ciclo_lavadero WHERE id IN (…)` → `exigirFilasAfectadas`;
     14. `DELETE FROM instancias_equipo_ciclo WHERE id IN (…)` → `exigirFilasAfectadas`;
     15. `DELETE FROM ingresos_lavadero WHERE id = ? AND estado = ?` → `exigirFilaAfectada`. Las
         líneas y las bolsas caen por `CASCADE`;
     16. los ciclos finalizados que quedaron vacíos: `DELETE FROM ciclos_lavadero WHERE id IN
         (ciclos de (3)) AND fecha_fin IS NOT NULL AND NOT EXISTS (SELECT 1 FROM
         elementos_ciclo_lavadero e WHERE e.ciclo_id = ciclos_lavadero.id)`. Sin guarda de conteo:
         que otro ingreso siga en el ciclo es legítimo. Los insumos caen por `CASCADE`;
     17. `commit`. La contención → conflicto; cualquier otra `SQLException` → `DatabaseException`.

   - **Javadoc del orden: la matriz de entrelazados.** Es lo que H2 no delata y ningún test
     defiende. La sesión la **razona y la deja escrita**, una fila por operación de la tabla
     "Órdenes de bloqueo existentes". Lo que ya se razonó al escribir el plan, para verificar:
     - **Clasificación** (`ingresos_lavadero` → `INSERT` líneas): el borrado también toma primero
       el ingreso. Se serializan;
     - **`lanzarTanda`** (`lavarropas` → ciclos (gap) → líneas): si la tanda toma las líneas
       primero, el borrado espera en (2) y después ve el ciclo nuevo en (7) (la vista se fija
       **después** de todos los bloqueos, así que lo ve) → `CicloEnCurso`. Si el borrado las toma
       primero, la tanda espera, y al commitear el borrado sus líneas ya no existen → saldo 0 →
       `SaldoConsumidoException` → la tanda descarta el staging, que es lo correcto. **Caso
       residual:** el `DELETE` de (16) sobre el ciclo finalizado más viejo de un lavarropas puede
       chocar con el gap lock de `SQL_CICLO_ACTIVO_DE_LAVARROPAS` de una tanda que a su vez espera
       nuestras líneas. Es un deadlock que MySQL resuelve abortando a uno, y el borrado lo informa
       como conflicto. Se acepta y se documenta;
     - **`finalizarCiclo`** (ciclos → ingreso): si está finalizando un ciclo con elementos nuestros
       sin haber commiteado, el borrado lo lee en curso en (7) → rechaza y suelta. Nunca bloquea
       `ciclos_lavadero` antes de (16);
     - **`marcarListo`** (tandas → instancias → `INSERT salidas`): no toma ni el ingreso ni las
       líneas. El borrado espera en (3). Sin ciclo;
     - **`derivar`** (`INSERT equipo_otros` → salidas → ingreso): **se cruza** con ingreso →
       salidas. Da un deadlock resoluble, y el Paso 5 hace que `derivar` lo informe como conflicto.
       Se documenta como caso aceptado;
     - **borrado de un derivado desde Ver Equipos** (salidas → materiales → cabecera): el borrado
       de Lavadero toma salidas (5) → materiales → cabecera (6) en el mismo orden relativo;
     - **Registrar Estado / entregas / `lanzarLote` sobre el derivado** (materiales → cabecera):
       mismo orden que (6).
3. **`Constantes.Mensajes`**: los bloqueos nuevos (`CicloEnCurso`: "Hay ropa de este ingreso en el
   lavarropas N, con el ciclo sin finalizar. Finalizá ese ciclo y volvé a intentar.";
   `DerivadoCompartido`: "El ingreso del CDE #X también tiene ropa de los ingresos de Lavadero #A,
   #B. Eliminalo primero desde Ver Equipos → Otros y volvé a intentar.").
4. **Tests** (H2): armar los escenarios con los DAOs reales (`ClasificacionLavaderoDAO.guardar`,
   `CicloLavaderoDAO.lanzarTanda`/`finalizarCiclo`, `SalidaLavaderoDAO.marcarListo`/`derivar`).
   - `EliminadorIngresoLavaderoTest`:
     - `eliminar_pendiente_borraIngresoYBolsasYArchiva`;
     - `eliminar_clasificado_borraLineas`;
     - `eliminar_conCicloEnCurso_rechazaYNoBorraNada`;
     - `eliminar_finalizadoConSalidasFueraDeFlujo_borraTodo`;
     - `eliminar_conDerivadoPropio_borraElEquipoOtrosYArchivaDosFilasEnlazadas`;
     - `eliminar_conDerivadoEntregado_loBorraIgual` (cualquier estado);
     - `eliminar_conDerivadoEnLoteEnCurso_rechaza`;
     - `eliminar_conDerivadoCompartido_rechazaYNombraAlOtroIngreso`;
     - `eliminar_cicloCompartidoConOtroIngreso_elCicloQuedaConLoDelOtro`;
     - `eliminar_cicloQueQuedaVacio_seBorraConSusInsumos`;
     - `eliminar_conFraccionesDeEquipo_borraInstanciasYSalidasDeInstancia`;
     - `eliminar_variosBloqueos_losInformaTodos`;
     - `eliminar_derivadosDistintosALosVistos_conflicto`;
     - `resumir_informaDerivadosYBloqueos`;
     - `eliminar_despues_disponiblesYSalidasYHistorialNoLoMuestran`: las consultas existentes de
       `CicloLavaderoDAO.obtenerElementosDisponiblesParaCiclo`, de `SalidaLavaderoDAO` y de
       `HistorialLavaderoDAO` no lo devuelven. Es el test del "no puede volver a verse".
   - **`ConcurrenciaOptimistaTest`**:
     - `eliminarIngresoLavaderoQueOtroMetioEnUnCiclo`: A resume; B lanza una tanda con una línea;
       A elimina → `EliminacionBloqueadaException(CicloEnCurso)`. Queda el ciclo de B;
     - `eliminarIngresoLavaderoCuyoDerivadoOtroMetioEnUnLote`;
     - `eliminarIngresoLavaderoQueOtroDerivoDespuesDeLeer`: A resume sin derivados; B deriva; A
       elimina → conflicto. Queda el `equipo_otros` de B;
     - `eliminarIngresoLavaderoQueOtroClasifico`: A resume `PENDIENTE`; B clasifica; A → conflicto.

### Verificación

```bash
mvn test -Dtest='EliminadorIngresoLavaderoTest,ConcurrenciaOptimistaTest,CicloLavaderoDAOTest,SalidaLavaderoDAOTest,HistorialLavaderoDAOTest'
mvn test
```

### Criterio de salida

- [ ] Fase A completa antes de la primera lectura no bloqueante (javadoc + revisión a ojo)
- [ ] La matriz de entrelazados escrita en el javadoc, con los casos residuales nombrados
- [ ] Ninguna consulta de borrado del CDE copiada en Lavadero (todo por `EliminadorEquipoOtros`)
- [ ] Conteos de guarda con `executeUpdate()`
- [ ] Commit: `feat: eliminar ingreso de lavadero con su derivado al cde`

---

## Paso 5 — Quien escribe sobre un ingreso borrado choca, no falla

### Contexto (autocontenido)

Hasta ahora, lo único que borraba un ingreso era Correcciones, y sólo en estado `NUEVO`. Con esta
feature cualquier ingreso puede desaparecer mientras otro operador lo tiene en pantalla o en un
staging. Seis escrituras tratan hoy "la fila ya no existe" como `SQLException`, y le muestran al
operador un error técnico por algo que es un choque de concurrencia. Este paso las pasa a
`ConflictoConcurrenciaException` con el mensaje de su flujo.

**No depende de ningún otro paso:** los tests borran con SQL directo
(`ejecutarSinChecked("DELETE FROM equipos WHERE id = ?")`). Para Lavadero se borra en el orden de
las FKs, con un helper del test.

### Tareas

1. **Re-verificar la lista** con `grep -n "throw new SQLException" src/main/java/com/example/features`
   y leer cada una. Las conocidas:
   - `MaterialDAO:132-133` → `ConflictoConcurrenciaException(CONFLICTO_MATERIAL)`;
   - `EquipoOtrosDAO:643` (cabecera) y `:707` (material) → `CONFLICTO_MATERIAL`;
   - `LoteDAO:772`, `:927` y `:995` → `CONFLICTO_LOTE`.

   Las que **no** son "fila borrada" se dejan como están: `No se generó ID…` y `No se encontró el
   lote recién insertado` son invariantes de la propia transacción.
2. **Cómo propaga cada una.** La `ConflictoConcurrenciaException` es `RuntimeException` y atraviesa
   el `catch (SQLException)` del método. Verificar, en cada método, que ningún `catch (Exception)` o
   `catch (RuntimeException)` intermedio la convierta en otra cosa, y que la transacción revierta
   (el `close()` sin `commit`).
3. **`SalidaLavaderoDAO.derivar`**: agregar al `catch (SQLException)` la misma traducción de
   `marcarListo`: `esContencionDeLock` → `ConflictoConcurrenciaException(CONFLICTO_SALIDA)`, con el
   `log.warn`. Es el cruce ingreso ↔ salidas con el borrado de Lavadero (ver Paso 4).
4. **Re-verificar los que ya chocan bien**: Clasificación, `lanzarTanda`, `marcarListo`,
   `volverALavado`, `estamparDestino`, entregas y Correcciones. Un test por flujo abajo. Si alguno
   **no** choca, arreglarlo acá.
5. **Tests en `ConcurrenciaOptimistaTest`** (*A lee → B borra → A escribe → conflicto, y no queda
   nada escrito*):
   - `registrarEstadoOrtopediasSobreEquipoBorrado`, `registrarEstadoOtrosSobreEquipoBorrado`;
   - `lanzarLoteConMaterialDeEquipoBorrado` (ortopedias y otros);
   - `entregaDeEquipoBorrado`;
   - `correccionSobreEquipoBorrado`;
   - `clasificarIngresoBorrado`;
   - `lanzarTandaConLineaDeIngresoBorrado`, que tiene que ser `SaldoConsumidoException`;
   - `marcarListoDeIngresoBorrado`;
   - `derivarSalidaDeIngresoBorrado`.

   En cada uno se verifica que **no** queda nada escrito. En `lanzarLote`, que no queda la fila de
   `lotes`.

### Verificación

```bash
mvn test -Dtest='ConcurrenciaOptimistaTest,MaterialDAOTest,EquipoOtrosDAOTest,LoteDAOTest,SalidaLavaderoDAOTest'
mvn test
```

### Criterio de salida

- [ ] Ninguna escritura sobre una fila de otro operador sale como `DatabaseException` porque la fila ya no existe
- [ ] Un test por flujo en `ConcurrenciaOptimistaTest`
- [ ] Commit: `fix: escribir sobre un ingreso borrado por otro es conflicto, no error tecnico`

---

## Paso 6 — El service de eliminación y la lógica del diálogo en clases planas

### Contexto (autocontenido)

Une la password (Paso 2) con los tres eliminadores (Pasos 3 y 4) detrás de un service, y deja en
clases planas testeadas todo lo que después el diálogo y los controllers **no** deben calcular. No
hay Swing en este paso. Requiere los Pasos 2, 3 y 4.

### Tareas

1. **`features/eliminaciones/service/EliminacionIngresosService`** (sin JDBC), con los tres
   eliminadores y `PasswordEliminacionService` inyectados:
   - `ResumenEliminacion resumir(IngresoAEliminar)`: despacha por `modulo`;
   - `boolean passwordEsInicial()`;
   - `void eliminar(SolicitudEliminacion s, char[] password)`, en este orden:
     1. validar el motivo (builder: obligatorio, ≤ 500);
     2. `passwordService.verificar(password)`;
     3. despachar al eliminador con el token de guarda del resumen y el `PuestoDeTrabajo.actual()`.
   - `record SolicitudEliminacion(ResumenEliminacion resumen, String motivo)`. **La password no va en
     el record**: viaja aparte como `char[]`, y **el service no la limpia** (es de quien la creó).
   - Javadoc: por qué la password se verifica **antes** y fuera de la transacción del borrado (no
     hace falta atomicidad: verificar no escribe nada).
2. **`features/eliminaciones/controller/helpers/TextoEliminacion`** (plano):
   - `String confirmacion(ResumenEliminacion)`: qué se elimina, con cliente, fecha, estado y
     materiales o elementos. En Lavadero, **cada derivado del CDE por separado** ("También se
     elimina el ingreso del CDE #X, estado …"). En Otros derivado: "Vino de Lavadero (ingreso #N);
     ese ingreso no se elimina". Cierra con "No se puede deshacer";
   - `String bloqueos(ResumenEliminacion)`, que delega en `TextoBloqueos`;
   - las plantillas, en `Constantes.Mensajes`.
3. **`features/eliminaciones/controller/helpers/DecisionDialogoEliminacion`** (plano), la máquina de
   decisiones del flujo:
   - `Paso siguientePaso(ResumenEliminacion r)` → `MostrarBloqueos(texto)` si hay bloqueos (no se
     pide la password), o `PedirConfirmacion(texto, avisoPasswordInicial)`;
   - `Paso trasError(Throwable e, String motivoTipeado)`:
     - `PasswordIncorrectaException` → `ReabrirDialogo(motivoTipeado, mensaje)`. El motivo se
       conserva y la password no;
     - `EliminacionBloqueadaException` → `MostrarBloqueos`;
     - `ConflictoConcurrenciaException` / `ResourceNotFoundException` → `AvisarYRecargar(mensaje)`;
     - `ValidationException` → `ReabrirDialogo(motivoTipeado, mensaje)`;
     - cualquier otra → `MostrarError`.
   - `sealed interface Paso` con esos records.
4. **`AppContext`**: construir los tres eliminadores (`EliminadorIngresoLavadero` recibe el
   `EliminadorEquipoOtros` **ya construido**, no uno nuevo) y el service. Exponer
   `getEliminacionIngresosService()`.
5. **Tests:**
   - `EliminacionIngresosServiceTest` (Mockito):
     - `eliminar_motivoVacio_validationYNoVerificaNiBorra`;
     - `eliminar_passwordIncorrecta_noLlamaAlEliminador`;
     - `eliminar_ortopedia_despachaConLaVersionDelResumen`;
     - `eliminar_lavadero_despachaConEstadoYDerivadosDelResumen`;
     - `eliminar_noModificaElCharArrayDeLaPassword`;
     - `resumir_despachaPorModulo`.
   - `TextoEliminacionTest`:
     - `lavaderoConDerivados_nombraCadaIngresoDelCde`;
     - `otrosDerivado_diceQueElIngresoDeLavaderoNoSeElimina`;
     - `siempreTerminaConNoSePuedeDeshacer`;
     - `bloqueos_unaLineaPorBloqueoConQueHacer`.
   - `DecisionDialogoEliminacionTest`: un caso por rama de `siguientePaso` y de `trasError`, y
     `passwordIncorrecta_conservaElMotivo`.

### Verificación

```bash
mvn test -Dtest='EliminacionIngresosServiceTest,TextoEliminacionTest,DecisionDialogoEliminacionTest'
mvn test
```

### Criterio de salida

- [ ] Ninguna clase de `controller/helpers` importa Swing; cobertura ≥ 90 %
- [ ] La password no forma parte de ningún record ni campo
- [ ] Commit: `feat: service de eliminacion de ingresos y decisiones del dialogo`

---

## Paso 7 — Ajustes: cambiar la password de eliminación

### Contexto (autocontenido)

Una pestaña nueva en Ajustes para cambiar la password, pidiendo la actual. Requiere el Paso 2. Es
independiente de los Pasos 3 a 6.

### Tareas

1. **`ajustes/view/PanelPasswordEliminacion`**:
   - tres `JPasswordField` (actual, nueva, repetir) y un botón "Cambiar contraseña";
   - un aviso visible mientras la password sea la inicial ("La contraseña de eliminación es la
     inicial: cambiala.");
   - `limpiarCampos()`, que hace `setText("")` en los tres.
2. **`PantallaAjustes`**: agregar la pestaña "Seguridad".
3. **`ajustes/controller/PasswordAjustesController`** (molde: `LavarropasAjustesController`), con
   `PasswordEliminacionService` en el constructor:
   - al mostrarse la pestaña, lee `esInicial()` por `TareaUI` y pinta el aviso;
   - al cambiar, toma los tres `char[]` con `getPassword()` y corre `cambiar` en `TareaUI`. En un
     **`finally` dentro del mismo lambda de `leer`**, `Arrays.fill(…, '\0')` en los tres. En
     `pintar`: mensaje de éxito, `limpiarCampos()` y el aviso se apaga. En `siFalla`: el mensaje de
     la excepción y `limpiarCampos()`;
   - el nombre de la `TareaUI` es fijo (`"ajustes-cambiar-password"`), sin datos.
4. **`UiCoordinator`**: construirlo con `context.getPasswordEliminacionService()`.
5. **Clase plana `ValidadorCambioPassword`** (en `ajustes/controller/helpers/`), para el feedback
   inmediato en el EDT **sin** hashear: campos vacíos y nueva ≠ repetida. El service revalida igual.
   Con su test (`vacios_…`, `distintas_…`, `ok`).
6. **Smoke:**
   1. cambiar con la actual incorrecta → mensaje, y nada cambia;
   2. cambiar bien → el aviso de inicial desaparece;
   3. volver a entrar a Ajustes → el aviso sigue apagado;
   4. el `UPDATE` de reseteo (Paso 9) → el aviso vuelve.

### Verificación

```bash
mvn test -Dtest='ValidadorCambioPasswordTest'
mvn test
```

### Criterio de salida

- [ ] Ningún `String` se construye con la password (sólo `char[]`, limpiados en `finally`)
- [ ] Commit: `feat: cambiar la password de eliminacion desde ajustes`

---

## Paso 8 — La UI: eliminar desde Ver Equipos y desde Historial de Lavadero

### Contexto (autocontenido)

Se agrega el botón en las tres grillas y el flujo completo, que los controllers **orquestan sin
calcular**: resumen por `TareaUI` → diálogo → eliminación por `TareaUI` → relectura. Requiere los
Pasos 5, 6 y 7 (el Paso 7, para el smoke del aviso de password inicial).

### Tareas

1. **`features/eliminaciones/view/EliminarIngresoDialog`** (modal):
   - un `JTextArea` no editable con la confirmación, dentro de un `JScrollPane` de tamaño acotado
     (`Estilos.Dimensiones`, como la confirmación de entregas);
   - si corresponde, el aviso de password inicial;
   - el motivo (`JTextArea`, precargable) y la password (`JPasswordField`);
   - los botones "Eliminar" y "Cancelar";
   - devuelve `Optional<Datos(String motivo, char[] password)>`. **`Datos` no es un record** (su
     `toString` imprimiría el array): es una clase final con `toString` que no lo muestra, y con
     `limpiar()`.
2. **`PantallaVerEquipos`**: un botón "Eliminar ingreso…" en la barra de cada grilla, habilitado con
   **exactamente una** fila seleccionada. `setOnEliminarOrtopedia(Runnable)` y
   `setOnEliminarOtros(Runnable)`.
3. **`PantallaHistorialLavadero`**: lo mismo, con `setOnEliminar(Runnable)`.
4. **Un solo flujo compartido, `features/eliminaciones/controller/FlujoEliminacion`**, que los dos
   controllers instancian con el service, un `Component` padre y un `Runnable alTerminar`:
   ```
   iniciar(IngresoAEliminar):
     TareaUI leer   → service.resumir(ingreso)
             pintar → abrir(resumen, motivoPrevio = "")
             siFalla→ ResourceNotFound: avisar + alTerminar.run(); otra: error
   abrir(resumen, motivoPrevio):
     paso = DecisionDialogoEliminacion.siguientePaso(resumen)
     MostrarBloqueos   → mensaje; fin (sin password)
     PedirConfirmacion → datos = dialog.mostrar(texto, aviso, motivoPrevio); vacío → fin
                         TareaUI leer    → try { service.eliminar(solicitud, datos.password) }
                                           finally { datos.limpiar() }
                                 pintar  → aviso de éxito; alTerminar.run()
                                 siFalla → paso = trasError(e, datos.motivo)
                                           ReabrirDialogo  → abrir(resumen, motivo) con el mensaje
                                           AvisarYRecargar → mensaje; alTerminar.run()
                                           MostrarBloqueos → mensaje; alTerminar.run()
                                           MostrarError    → error
   ```
   - `resumir` en `TareaUI` también lee `passwordEsInicial()`: son dos lecturas en el mismo
     `leer`, secuenciales y sin anidar conexiones (el invariante del semáforo).
   - Los nombres de las `TareaUI` son fijos: `"eliminar-resumen"` y `"eliminar-ingreso"`.
5. **`VerEquiposController`** y **`HistorialLavaderoController`**: reciben en el constructor
   `EliminacionIngresosService` y un `Runnable refrescarOperativo`. Declaran su alcance en el javadoc
   del constructor.
   - `alTerminar` = `publicarYPedir(consulta.recontando())` + `refrescarOperativo.run()`.
   - Al iniciar se toma la fila seleccionada (con `convertRowIndexToModel`, como `abrirDetalle`) y
     se arma el `IngresoAEliminar`.
6. **Página fuera de rango después de recontar**, en los dos `pintar`:
   - si `pagina.numeroPagina() > max(1, pagina.totalPaginas())`, se publica la consulta en la
     última página y se pide de nuevo, **sin pintar** la vacía;
   - la regla va en `ConsultaEquipos` y `ConsultaHistorial` como método puro (p. ej.
     `Optional<ConsultaHistorial> reubicadaSi(Pagina<?>)`), con test. En Ver Equipos, **cada
     grilla por separado**.
7. **`UiCoordinator`**: pasar `context.getEliminacionIngresosService()` y `operativo::solicitar` a
   los dos controllers.
8. **Tests:**
   - `ConsultaHistorialTest` / `ConsultaEquiposTest`: `paginaMasAllaDeLaUltima_reubicaEnLaUltima`,
     `paginaDentroDeRango_noReubica`, `totalCero_quedaEnLaPrimera`;
   - los tests de controller que existan se adaptan al constructor nuevo.
9. **Smoke manual** (sin `-Daptium.edt.strict=true`):
   1. Ver Equipos → Ortopedias: un equipo `ENTREGADO` → Eliminar → la confirmación lista sus
      materiales → motivo + password → desaparece, y el total baja;
   2. un equipo con un material en un lote **en curso** → el mensaje de bloqueo **sin** pedir la
      password;
   3. password incorrecta → mensaje; el diálogo se reabre con el motivo tipeado y la password vacía;
   4. motivo vacío → no deja confirmar;
   5. con la password inicial vigente → el aviso aparece; después de cambiarla en Ajustes → no;
   6. Otros: un ingreso derivado de Lavadero → la confirmación dice que el ingreso de Lavadero no se
      elimina; después, en Historial, esa salida sigue con destino CDE;
   7. Historial de Lavadero: un ingreso `FINALIZADO` con un derivado propio → la confirmación nombra
      el ingreso del CDE; al eliminar, desaparece de Historial **y** de Ver Equipos → Otros;
   8. un ingreso con ropa en un ciclo sin finalizar → bloqueo que nombra el lavarropas;
   9. un ingreso cuyo derivado es compartido → bloqueo que nombra el otro ingreso;
   10. eliminar la única fila de la última página → la pantalla muestra la página anterior, no una
       vacía;
   11. **conflicto con dos ventanas:** A abre la confirmación; B avanza un material de ese equipo; A
       confirma → cartel de conflicto, y la grilla se relee;
   12. en MySQL: `SELECT modulo, ingreso_id_original, puesto, LEFT(snapshot, 200) FROM
       ingresos_eliminados` → las filas, y `grep` de `app.log` y `error.log` por la password
       tipeada → nada.

### Verificación

```bash
mvn test -Dtest='ConsultaHistorialTest,ConsultaEquiposTest'
mvn test
```

### Criterio de salida

- [ ] Los controllers no arman textos ni deciden ramas: todo pasa por `DecisionDialogoEliminacion` y `TextoEliminacion`
- [ ] El `char[]` se limpia en el `finally` del `leer`
- [ ] Los 12 puntos del smoke
- [ ] Commit: `feat: eliminar ingresos desde ver equipos e historial de lavadero`

---

## Paso 9 — Revisión, seguridad, documentación y cierre

### Tareas

1. **`/code-review high`** sobre `main..EliminarIngresos`. Aplicar CRITICAL y HIGH; anotar acá los
   MEDIUM que no se toquen, con el motivo.
2. **Revisión de seguridad de la password** (agente `security-reviewer`, o a mano con esta lista):
   - hash PBKDF2-HMAC-SHA256, salt aleatorio de 16 bytes por hash, 600 000 iteraciones guardadas en
     la fila;
   - comparación con `MessageDigest.isEqual`;
   - `PBEKeySpec.clearPassword()` y `Arrays.fill` de cada `char[]` en un `finally`, en el hasher, el
     flujo y Ajustes;
   - ningún `String` construido con la password (`new String(char[])`, `String.valueOf(char[])`):
     `grep`;
   - nada en logs, mensajes de excepción, `toString`, nombres de `TareaUI` ni el snapshot JSON:
     `grep` + la prueba del punto 12 del smoke;
   - SQL con parámetros en `PasswordDAO`;
   - la inicial no aparece en claro en `src/` ni en el JAR (`unzip -p target/aptium.jar
     'db/migration/V29*' | grep -i aptium` → sólo el comentario, sin la password);
   - el mensaje de password incorrecta no distingue "vacía" de "distinta";
   - el límite honesto, escrito en el javadoc: quien tiene las credenciales de la base puede todo.
3. **`mvn verify`** + JaCoCo: clases planas ≥ 90 %; eliminadores con cada rama (ok, bloqueo,
   conflicto y contención si se puede provocar) cubierta.
4. **`CLAUDE.md`**, una sección nueva **"Eliminar ingresos"**, con:
   - dónde se puede y qué borra en cada módulo; borrado físico con archivo en la misma transacción,
     y por qué no baja lógica;
   - las reglas de rechazo (lote/ciclo en curso, derivado compartido) y por qué un rechazo **no** es
     un conflicto;
   - la guarda (`version` en el CDE; `estado` + derivados en Lavadero) y el orden de bloqueo de cada
     transacción, con los casos residuales de deadlock aceptados;
   - que **Lavadero ahora sí se borra**: el comentario de `V17` quedó desactualizado y no se toca;
   - que **no hay refresco global**: qué grupos se piden y por qué;
   - **password**: dónde vive, qué protege y qué no, el valor inicial (`aptium`) y el **`UPDATE` de
     reseteo** literal, con el salt y el hash de `V29`:
     ```sql
     UPDATE passwords
        SET algoritmo = 'PBKDF2WithHmacSHA256', iteraciones = 600000,
            salt = '<salt de V29>', hash = '<hash de V29>', es_inicial = TRUE,
            actualizado_en = CURRENT_TIMESTAMP
      WHERE proposito = 'ELIMINAR_INGRESO';
     ```
   - "Dónde hay guarda hoy": sumar la eliminación de ingresos y el cambio de password;
   - "Lavadero → CDE": `EliminadorIngresoLavadero` es el segundo punto de escritura cruzada, vía
     `EliminadorEquipoOtros`;
   - "Tests": actualizar el número; sumar `TextoEliminacion`, `DecisionDialogoEliminacion`,
     `TextoBloqueos` y `ValidadorCambioPassword` a la lista de clases planas de ejemplo.
5. **Memoria:** `project-eliminar-ingresos.md` (`type: project`), con:
   - las decisiones del usuario, incluidas las cuatro de la ronda de preguntas;
   - el hallazgo del derivado compartido;
   - los seis escritores que fallaban en vez de chocar;
   - que no hay refresco global.

   Enlazar `[[project-bloqueo-optimista]]`, `[[project-salidas-lavadero-cde]]`,
   `[[project-conexiones-y-paginacion]]` y `[[project-architecture]]`, y agregar la línea en
   `MEMORY.md`.
6. Marcar este plan como **✅ CERRADO**, con los SHAs de cada paso.

### Criterio de salida

- [ ] `mvn verify` en verde; cobertura según el punto 3
- [ ] Revisión de seguridad sin hallazgos abiertos
- [ ] `CLAUDE.md` y memoria actualizados
- [ ] Commit: `docs: eliminar ingresos y password de eliminacion`

---

## Catálogo de anti-patrones para este plan

| Anti-patrón | Por qué está mal acá |
|---|---|
| Baja lógica "por las dudas" | Decisión del usuario: son ~30 consultas a filtrar, y cada olvido hace reaparecer un ingreso borrado. |
| Archivar después del `DELETE`, fuera de la transacción (como Correcciones) | El archivo es lo único que queda. Si falla, se perdió el ingreso sin copia. |
| Reusar `EquipoDAO.eliminarConVersion` / `EquipoOtrosDAO.eliminarEquipo` | Abren su propia conexión: no pueden ir en la transacción del archivo ni en la de Lavadero. |
| Una lectura no bloqueante antes del último `FOR UPDATE` | Bajo `REPEATABLE READ` la vista se fija ahí: un ciclo o un lote lanzado después sería invisible para la verificación. H2 no lo delata. |
| `SELECT … FROM lotes … FOR UPDATE` en el borrado | Cruza el orden de `finalizarLote` (`lotes` → materiales). Leer `fecha_fin` sin bloquear es conservador y suficiente. |
| Bloquear la cabecera antes que los materiales en el CDE | Cruza el orden de Registrar Estado, entregas y lotes, que son los caminos calientes. |
| Borrar un derivado de Lavadero sin tomar antes sus salidas | El `SET NULL` escribe `salidas_lavadero` al final y cruza el orden del borrado de Lavadero. |
| Copiar en Lavadero las consultas de borrado del CDE | Dos lugares que mantener para lo mismo. Lavadero usa las fases de `EliminadorEquipoOtros`. |
| Borrar el derivado compartido, o "descontar su parte" | Decisión del usuario: se rechaza. Descontar es ambiguo (materiales sumados por nombre y partidos por estado o lote). |
| Tratar el lote o el ciclo en curso como `ConflictoConcurrenciaException` | No es "otro se te adelantó": el operador tiene que finalizar algo. Mismo texto venga del resumen o de la transacción. |
| Desarmar el lote o el ciclo automáticamente | Decisión del usuario: nunca. |
| Borrar el lote que queda vacío | Decisión del usuario: queda vacío (numeración y constancia del autoclave). |
| Guardar sólo con `estado` en Lavadero | Una derivación parcial no mueve el estado y crea un ingreso del CDE que el operador no vio. |
| `version` en `ingresos_lavadero` | Política de `CLAUDE.md`: la guarda natural es su estado. Para lo que falta, se agregan los derivados al token. |
| Contar las filas de un `DELETE` guardado con `executeBatch()` | `SUCCESS_NO_INFO`: la guarda rechaza borrados válidos. |
| Dejar la contención de locks como `DatabaseException` | Un deadlock entre el borrado y otra operación es un choque. El operador tiene que leer "volvé a intentar", no "error técnico". |
| Pedir un "refresco global" | No existe. Se pide el grupo propio con `recontando()` más `operativo`. |
| Pedir la relectura sin `recontando()` | El total arrastrado deja una página fantasma. |
| Pintar una página vacía más allá de la última | Borrar la única fila de la última página es ahora un caso común. Se reubica. |
| `new String(passwordField.getPassword())` o `getText()` | El `String` no se puede limpiar y queda en el heap. Siempre `char[]` + `Arrays.fill`. |
| La password dentro de un `record` | El `toString` automático la expone (aunque sea el array), y la tienta a viajar más lejos de lo necesario. |
| Loguear "password incorrecta para X" o incluir la candidata | Nada derivado de la password va a un log. |
| Comparar hashes con `Arrays.equals` | No es de tiempo constante. `MessageDigest.isEqual`. |
| Hardcodear la password, o su hash, en Java | El JAR es público. El hash vive en la base; la inicial, sólo en la migración y documentada. |
| Verificar la password en el EDT | PBKDF2 con 600 000 iteraciones tarda a propósito: congelaría la UI y `EdtGuard` no lo detecta (no es I/O). |
| FK de `ingresos_eliminados` a `clientes` | Rompe `FusionClientesDAOTest`, y un archivo no puede impedir borrar o fusionar clientes. |
| Modificar `V17` para corregir su comentario | Nunca se modifica una migración. Se documenta en `CLAUDE.md`. |

---

## Protocolo si el plan tiene que cambiar

- **Si un hecho de "Estado del que parte" resulta falso**, PARAR y avisar antes de adaptar el
  paso. Anotar la corrección en esa sección, con la fecha.
- **Si aparece una duda de diseño que el plan no cubre**, preguntar (instrucción del `CLAUDE.md`).
  No decidir en silencio.
- **Partir un paso**: si un paso no entra en una sesión, cortarlo en `Na`/`Nb` con su propio commit,
  y anotar acá qué quedó en cada uno.
- **Saltear o reordenar**: sólo si el grafo lo permite. Anotar el motivo acá.

---

## Plan de sesiones

Nueve pasos, **siete sesiones**. Cada sesión arranca en frío.

| Sesión | Pasos | Modelo | Effort | Fast mode | Por qué |
|---|---|---|---|---|---|
| 1 | 1 y 2 | **Opus 5.5** | **alto** | ❌ no | Crea la rama. El archivo es chico, pero la password es la parte de **seguridad**: PBKDF2, tiempo constante, `char[]` limpiado, la semilla de la migración que tiene que verificar contra lo que se documenta, y nada en los logs. |
| 2 | 3 | **Opus 5.5** | **alto** | ❌ no | El orden de bloqueo del CDE contra Registrar Estado, entregas, lotes y Correcciones, y por qué `lotes` se lee sin bloquear. Las fases de `EliminadorEquipoOtros` fijan la interfaz que usa Lavadero. **H2 no delata nada de esto.** |
| 3 | 4 | **Opus 5.5** | **muy alto (xhigh)** | ❌ no | Lo más delicado del plan: tres FKs `RESTRICT`, la matriz de entrelazados contra cinco escritores, los derivados compartidos y los casos residuales de deadlock que hay que razonar y dejar escritos. |
| 4 | 5 | **Opus 5.5** | alto | ❌ no | Cambia el comportamiento de error de seis escrituras en producción. Hay que verificar que ningún `catch` intermedio se trague el conflicto y que cada transacción revierta. |
| 5 | 6 y 7 | **Sonnet 5.5** | alto | ➖ opcional | Con los eliminadores hechos, es unir y testear clases planas, y una pestaña de Ajustes con molde existente. El effort alto es por el manejo del `char[]`. |
| 6 | 8 | **Sonnet 5.5** | alto | ➖ opcional | Cableado de UI con `TareaUI`, el flujo compartido y el reubicado de página. El smoke tiene 12 puntos, uno con dos ventanas. |
| 7 | 9 | **Opus 5.5** | alto | ❌ no | Cierre: abre con `/code-review high` y la revisión de seguridad, que es donde está el juicio. Después, docs y memoria. |

### Paralelizar

- **La Sesión 4 (Paso 5) no depende de nada** y puede correr a la vez que las Sesiones 1, 2 o 3. No
  pueden compartir la rama en dos checkouts, así que se usa un `git worktree` con una rama hija:
  `git worktree add ../Aptium-choques -b EliminarIngresos-choques EliminarIngresos`. Al terminar, se
  mergea a `EliminarIngresos` y se borran el worktree y la rama hija.
  - Comparte con los Pasos 3 y 4 `ConcurrenciaOptimistaTest` y `Constantes.Mensajes`: los
    conflictos de merge son triviales pero seguros.
  - Toca `EquipoOtrosDAO` y `SalidaLavaderoDAO`, que los Pasos 3 y 4 **no** modifican (usan clases
    nuevas).
- **El Paso 7 sólo depende del Paso 2.** Si conviene, se puede adelantar a una sesión propia en
  paralelo con las Sesiones 2 y 3. Toca `ajustes/` y `UiCoordinator`, que ningún otro paso toca antes
  del 8.
- **Los Pasos 3 y 4 son secuenciales:** el 4 usa las fases del 3.
- Orden recomendado para una sola persona: 1 → 2 → 3 → 4 → 5 → 6 → 7.

---

### Sesión 1 — Pasos 1 y 2: archivo y password

**Opus 5.5 · effort alto · sin fast mode**

```
ANTES DE TOCAR CUALQUIER ARCHIVO, creá la rama y movete a ella:
  git checkout main && git pull && git checkout -b EliminarIngresos
Confirmá con `git branch --show-current` que estás en EliminarIngresos.

Después ejecutá los Pasos 1 y 2 de plans/eliminar-ingresos.md, en ese orden, con un commit por paso.

Leé antes, del plan: "Decisiones tomadas con el usuario" (filas Formato del archivo, Password y
Password inicial), "Decisiones de diseño tomadas por el plan" (Una fila de archivo por ingreso,
puesto, Dónde vive cada cosa), "Estado del que parte" (los puntos de migraciones y de
FusionClientesDAOTest), los Pasos 1 y 2 enteros y el catálogo de anti-patrones. Del CLAUDE.md:
"Concurrencia — bloqueo optimista" (la parte de ControlConcurrencia) y "Tests". En el código:
EquipoOtrosDAO.guardar(Connection, …) (el molde de un DAO sobre una conexión ajena),
FusionClientesDAOTest y AppContext.

Lo que esta sesión tiene que dejar bien, y ningún otro paso lo va a revisar hasta el cierre:
1. re-verificá que V28 y V29 están libres. NUNCA modifiques una migración existente;
2. ingresos_eliminados SIN FK a clientes: FusionClientesDAOTest tiene que seguir verde;
3. ArchivoIngresosDAO.archivar trabaja sobre la Connection del llamador y no abre nada;
4. PBKDF2WithHmacSHA256, salt aleatorio de 16 bytes, 600000 iteraciones guardadas en la fila,
   MessageDigest.isEqual, PBEKeySpec.clearPassword() en finally;
5. la semilla de V29 es el hash de la password inicial "aptium", generado con TU hasher. El test
   semillaDeLaMigracion_verificaConLaPasswordInicialDocumentada lo garantiza. La password en claro
   NO va en la migración ni en ningún .java de src/main;
6. ningún log, mensaje ni toString contiene nada derivado de la password: hacé el grep del Paso 2,
   tarea 9, y anotá el resultado en el mensaje del commit.

Terminá con mvn test en verde y los dos commits.
```

### Sesión 2 — Paso 3: eliminar un equipo del CDE

**Opus 5.5 · effort alto · sin fast mode**

```
Verificá que estás en la rama EliminarIngresos (`git branch --show-current`) y que los commits de
los Pasos 1 y 2 están (`git log --oneline -5`). Si no, PARÁ y avisame.

Ejecutá el Paso 3 de plans/eliminar-ingresos.md: el modelo común de bloqueos y resumen,
EliminadorEquipoOrtopedia, EliminadorEquipoOtros con fases públicas sobre una Connection ajena, sus
tests y los casos nuevos de ConcurrenciaOptimistaTest.

Leé antes, del plan: las dos tablas de decisiones (sobre todo Guarda del CDE, Resumen previo,
Mismos bloqueos, Rechazo por bloqueo ≠ conflicto, El archivo va dentro de la transacción y
Lavadero borra en el CDE reusando el eliminador de Otros), en "Estado del que parte" la tabla de
FKs, "Órdenes de bloqueo existentes" y el punto sobre el borrado de Correcciones, el Paso 3 entero
y el catálogo. Del CLAUDE.md: "Concurrencia — bloqueo optimista" completo. En el código:
ControlConcurrencia, TransactionalConnection, MaterialDAO.aplicarMovimientos, LoteDAO.finalizarLote
y lanzarLote (sus órdenes de bloqueo), EquipoOtrosMaterialHelper.bumpVersionConGuarda, y
EquipoCorreccionService.eliminarEquipo (lo que NO se reusa, y por qué).

Lo que nada más va a delatar:
1. el orden materiales → cabecera y todos los FOR UPDATE antes de la primera lectura no
   bloqueante. H2 no lo reproduce: escribilo en el javadoc con el razonamiento;
2. `lotes` se lee SIN bloquear, a propósito (finalizarLote bloquea lotes → materiales). Ningún
   FOR UPDATE sobre lotes;
3. en Otros, las salidas_lavadero del equipo se bloquean PRIMERO (el SET NULL las escribe);
4. el archivo y el DELETE en la MISMA transacción, con un test de atomicidad;
5. el lote o ciclo en curso es EliminacionBloqueadaException (BusinessException), NO un conflicto;
   la versión vieja y la contención de locks SÍ son ConflictoConcurrenciaException;
6. las fases de EliminadorEquipoOtros son la interfaz que usa el Paso 4: bloquear / verificar /
   archivarYBorrar sobre una Connection ajena, sin abrir ni commitear nada.

Terminá con mvn test en verde y el commit.
```

### Sesión 3 — Paso 4: eliminar un ingreso de Lavadero

**Opus 5.5 · effort muy alto (xhigh) · sin fast mode**

```
Verificá que estás en la rama EliminarIngresos y que el commit del Paso 3 está. Si no, PARÁ.

Ejecutá el Paso 4 de plans/eliminar-ingresos.md: EliminadorIngresoLavadero (resumen + transacción
en tres fases), sus tests y los casos nuevos de ConcurrenciaOptimistaTest.

Leé antes, del plan: "Decisiones tomadas con el usuario" (Qué se borra en Lavadero, equipo_otros
derivado compartido, Lote o ciclo en curso), la fila Guarda de Lavadero, en "Estado del que parte"
la tabla de FKs, el punto "Un equipo_otros derivado puede mezclar ingresos" y la tabla "Órdenes de
bloqueo existentes", el Paso 4 entero y el catálogo. Del CLAUDE.md: "Lavadero — fracciones de
equipo", "Lavadero → CDE" y "Concurrencia — bloqueo optimista" (en especial el orden
lavarropas → ciclos_lavadero y "se toman todos los bloqueos antes de la primera lectura no
bloqueante"). En el código: CicloLavaderoDAO (javadoc de SQL_BLOQUEAR_LINEA, SQL_LAVARROPAS_ACTIVO,
SQL_CICLO_ACTIVO_DE_LAVARROPAS, lanzarTanda y finalizarCiclo), SalidaLavaderoDAO (bloquearAfectados,
marcarListo, derivar), ClasificacionLavaderoDAO.guardar, ConstructorIngresoCDE y las fases de
EliminadorEquipoOtros del Paso 3.

Lo que nada más va a delatar:
1. la Fase A (bloquear TODO, incluidos los derivados vía EliminadorEquipoOtros.bloquear) termina
   antes de la primera lectura no bloqueante. El ciclo en curso y el lote en curso se leen DESPUÉS;
2. la matriz de entrelazados contra Clasificación, lanzarTanda, finalizarCiclo, marcarListo,
   derivar, el borrado desde Ver Equipos y Registrar Estado/entregas/lotes sobre el derivado. Tiene
   que quedar ESCRITA en el javadoc, con los dos casos residuales de deadlock (DELETE de ciclo vacío
   vs gap lock de lanzarTanda; derivar) nombrados y aceptados. Si encontrás un cruce que el plan
   no vio, PARÁ y preguntame antes de cambiar el orden;
3. el orden de borrado lo dictan las FKs RESTRICT: salidas → elementos_ciclo → instancias →
   ingreso → ciclos finalizados vacíos. Conteos con executeUpdate, nunca executeBatch;
4. el derivado compartido se RECHAZA nombrando los otros ingresos. Ninguna consulta de borrado del
   CDE se copia acá: todo por EliminadorEquipoOtros;
5. la guarda es estado + el conjunto de derivados vistos, y el test
   eliminarIngresoLavaderoQueOtroDerivoDespuesDeLeer lo fija;
6. el test "no puede volver a verse": Disponibles, Salidas e Historial no devuelven el ingreso
   borrado.

Terminá con mvn test en verde y el commit.
```

### Sesión 4 — Paso 5: los escritores concurrentes chocan, no fallan

**Opus 5.5 · effort alto · sin fast mode**

```
Verificá la rama: EliminarIngresos, o EliminarIngresos-choques si corrés en un worktree paralelo
(ver "Paralelizar" en el plan). Si no estás en ninguna de las dos, PARÁ.

Ejecutá el Paso 5 de plans/eliminar-ingresos.md: las escrituras que tratan "la fila ya no existe"
como SQLException pasan a ConflictoConcurrenciaException, derivar traduce la contención de locks, y
va un test por flujo en ConcurrenciaOptimistaTest. Este paso NO depende de los eliminadores: los
tests borran con SQL directo.

Leé antes, del plan: la fila "Los escritores concurrentes chocan, no fallan", en "Estado del que
parte" la tabla de los seis escritores y la de órdenes de bloqueo, el Paso 5 entero y el catálogo.
Del CLAUDE.md: "Concurrencia — bloqueo optimista". En el código: cada línea de la tabla, y
SalidaLavaderoDAO.marcarListo (el molde de la traducción de la contención).

Tres cosas:
1. re-verificá la lista con grep antes de tocar nada. Las que son invariantes de la propia
   transacción ("No se generó ID…") NO cambian;
2. en cada método, confirmá que ningún catch intermedio convierte el conflicto en otra cosa y que
   la transacción revierte;
3. un test por flujo, que verifique que NO queda nada escrito (en lanzarLote, ni la fila de lotes).

Terminá con mvn test en verde y el commit. Si corriste en worktree: mergeá a EliminarIngresos,
corré mvn test ahí y borrá el worktree y la rama hija.
```

### Sesión 5 — Pasos 6 y 7: service, decisiones del diálogo y Ajustes

**Sonnet 5.5 · effort alto · fast mode opcional**

```
Verificá que estás en la rama EliminarIngresos y que los commits de los Pasos 1 a 4 están. Si no,
PARÁ.

Ejecutá los Pasos 6 y 7 de plans/eliminar-ingresos.md, en ese orden, con un commit por paso:
EliminacionIngresosService, TextoEliminacion y DecisionDialogoEliminacion (clases planas con tests),
el cableado en AppContext, y la pestaña Seguridad de Ajustes para cambiar la password.

Leé antes, del plan: las filas Resumen previo, Mismos bloqueos y Rechazo por bloqueo ≠ conflicto,
los Pasos 6 y 7 enteros y el catálogo (las filas de la password). Del CLAUDE.md: "Instrucciones del
usuario", la regla de extensión de controllers y el patrón de clases planas en "Tests". En el
código: ui/common/GuardaRefresco (molde de clase plana), LavarropasAjustesController (molde de
pestaña con TareaUI), PasswordEliminacionService y los tres eliminadores.

Cuatro cosas:
1. la password NUNCA es parte de un record ni de un campo: viaja como char[] y la limpia quien la
   creó, en un finally dentro del mismo lambda de leer;
2. ningún String construido con la password (ni new String(char[]) ni getText());
3. las clases de controller/helpers no importan Swing, con cobertura ≥ 90 %;
4. una password incorrecta reabre el diálogo CONSERVANDO el motivo (DecisionDialogoEliminacion lo
   decide, con test).

Cerrá el Paso 7 con su smoke de 4 puntos. mvn test en verde y los dos commits.
```

### Sesión 6 — Paso 8: la UI de eliminación

**Sonnet 5.5 · effort alto · fast mode opcional**

```
Verificá que estás en la rama EliminarIngresos y que los commits de los Pasos 1 a 7 están. Si no,
PARÁ.

Ejecutá el Paso 8 de plans/eliminar-ingresos.md: EliminarIngresoDialog, los botones en las dos
grillas de Ver Equipos y en Historial de Lavadero, FlujoEliminacion (compartido), el cableado en
VerEquiposController, HistorialLavaderoController y UiCoordinator, y el reubicado de página.

Leé antes, del plan: las filas Refresco después de borrar y Página fuera de rango, en "Estado del
que parte" el punto "No existe un refresco global", el Paso 8 entero (con el pseudocódigo del
flujo) y el catálogo. Del CLAUDE.md: "Cinco grupos de refresco" y "Dos pantallas paginan en SQL"
(recontando(), publicar y pedir juntos). En el código: VerEquiposController, HistorialLavaderoController,
ConsultaEquipos, ConsultaHistorial, Pagina, TareaUI y la confirmación con scroll de
EquiposParaEntregarController/PantallaEquiposParaEntregar.

Lo que no puede salir mal:
1. después de eliminar: publicarYPedir(consulta.recontando()) + refrescarOperativo.run(). No
   inventes un refresco global: no existe;
2. una página más allá de la última se reubica en la última SIN pintar la vacía (método puro con
   test). En Ver Equipos, cada grilla por separado;
3. los controllers orquestan: las ramas las decide DecisionDialogoEliminacion y los textos los arma
   TextoEliminacion;
4. el char[] de la password se limpia en el finally del leer. Los nombres de las TareaUI son fijos.

Cerrá con los 12 puntos del smoke (SIN -Daptium.edt.strict=true); el 11 es el conflicto con dos
ventanas y el 12, el grep de los logs. mvn test en verde y el commit.
```

### Sesión 7 — Paso 9: cierre

**Opus 5.5 · effort alto · sin fast mode**

```
Verificá que estás en la rama EliminarIngresos y que los commits de los Pasos 1 a 8 están. Si no,
PARÁ.

Ejecutá el Paso 9 de plans/eliminar-ingresos.md (revisión, seguridad, documentación y cierre).

Empezá por /code-review high sobre main..EliminarIngresos. Aplicá CRITICAL y HIGH; los MEDIUM que no
toques van anotados en el plan con el motivo. Después, la revisión de seguridad de la password con
la lista del punto 2 (podés usar el agente security-reviewer). Todos los puntos se verifican con
grep o con el JAR, no de memoria. Después, mvn verify con los umbrales del punto 3.

En CLAUDE.md, la sección nueva "Eliminar ingresos": qué borra cada módulo, archivo en la misma
transacción, rechazos vs conflictos, guardas y órdenes de bloqueo con los casos residuales, que
Lavadero ahora sí se borra (V17 quedó desactualizado y no se toca), que no hay refresco global, y
la password: qué protege y qué no, la inicial ("aptium") y el UPDATE de reseteo LITERAL, con el salt
y el hash copiados de V29. Actualizá "Dónde hay guarda hoy", "Lavadero → CDE" y "Tests". Memoria:
project-eliminar-ingresos.md + la línea en MEMORY.md. Marcá el plan como CERRADO con los SHAs y
commiteá.
```
