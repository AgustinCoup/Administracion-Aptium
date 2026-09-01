# Plan — Historial de Lavadero

**Objetivo:** botón nuevo **"Historial"** en el menú de Lavadero (que pasa de 5 botones en columna a
una grilla **2×3**), que abre una pantalla de consulta con la información de BD de todo el proceso de
lavadero: una tabla maestra de **ingresos** con filtros, y **doble clic → diálogo modal** con la
trazabilidad completa de ese ingreso (elemento → lavarropas → fecha de lavado → fecha listo → destino).

**Rama base:** `RetoquesFinalesL` · **Modo:** directo (todos los pasos en la misma rama, commit por
paso; `gh` no está instalado, no hay PRs)
**Fecha de creación:** 2026-09-01

> ⚠️ El árbol de trabajo **no está limpio** al crear este plan (cambios de `CategoriaElementoLavadero`
> y clasificación en curso). Antes del Paso 1: o se commitean esos cambios, o se ejecuta este plan
> sabiendo que `git diff` mezcla ambos trabajos. **Ningún paso de este plan toca esos archivos.**

---

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Forma de la pantalla | **Maestro-detalle con doble clic**, exactamente como *Ver Equipos*: una sola tabla visible (ingresos) y un `JDialog` modal con el detalle. **No** dos tablas apiladas ni pestañas. |
| Qué es "fuera del flujo" | **Los ingresos en estado `FINALIZADO`.** Es el corte que la BD ya tiene persistido (`ingresos_lavadero.estado`), así que decidirlo no cuesta una sola línea de SQL nueva. |
| Filtro por defecto | Al entrar, el combo de estados viene con **`PENDIENTE`, `CLASIFICADO`, `LAVADO` marcados y `FINALIZADO` desmarcado**. El usuario puede marcarlo para ver el archivo completo. |
| Refresco al entrar | **Releer datos de BD + resetear los filtros al default.** `componentShown` → restablecer filtros (sin notificar) + `solicitarRefresco.run()`. Entrar a Historial siempre da la misma vista. |
| Filtros | Cliente (texto, substring), Desde/Hasta (`JDateChooser` sobre fecha de ingreso), Estado (`CheckableComboBox` multi), Elemento (texto, substring) y Lavarropas (numérico exacto). |
| Acciones | **Ninguna. Pantalla de sólo lectura.** Historial no muta nada; mutar es trabajo de Clasificación / Ciclos / Salidas. |

### Decisiones de diseño tomadas por el plan (con su porqué)

| Tema | Decisión | Por qué |
|---|---|---|
| Elemento y Lavarropas como **campos de texto**, no combos | El filtro de elemento es substring sobre el nombre; el de lavarropas es número exacto | Un `CheckableComboBox` de elementos habría que repoblarlo en cada `pintar()`, y repoblarlo **borra la selección del usuario**. Además obligaría a inyectar un service de catálogo que la pantalla no necesita para nada más. Ya hay precedente exacto de las dos formas: `PantallaVerEquipos.txtCliente` (substring) y `PantallaVerCiclos.txtFiltroNumero` (numérico exacto, `RestriccionesCampo.soloNumeros`). |
| El **detalle se lee bajo demanda**, no en el snapshot | El snapshot trae sólo las filas maestras; el doble clic dispara una segunda lectura de ese ingreso | Traer el detalle de *todos* los ingresos en cada refresco es leer el histórico entero de cuatro tablas para mostrar una fila. Cada `pintar` costaría O(historia completa). |
| Esa segunda lectura va por **`TareaUI`** | El diálogo se construye dentro de `.pintar`, nunca en el `mouseClicked` | Regla dura del repo: ningún acceso a BD en el EDT. **Aquí NO se copia `VerEquiposController.abrirDetalleOtros()`**, que llama `equipoOtrosService.obtenerPorId()` sincrónico en el EDT — eso es una violación preexistente que `EdtGuard` delata, no un patrón a replicar. |
| Filtros de elemento/lavarropas resueltos **en memoria** | `IngresoHistorial` carga `Set<String> elementos` y `Set<Integer> lavarropas` como agregados | Toda la app filtra en memoria sobre un snapshot (`AbstractFilterController`). Hacer estos dos filtros en SQL rompería el patrón y obligaría a una consulta parametrizada por combinación de filtros. Los dos sets salen de la **misma** consulta maestra, sin viaje extra. |
| **DAO nuevo**, no métodos en `IngresoLavaderoDAO` | `HistorialLavaderoDAO` en `lavadero/dao/` | `IngresoLavaderoDAO` es el DAO de escritura del ingreso y de la cola "sin clasificar". El historial cruza clasificación + ciclos + instancias + salidas: meterlo ahí lo convierte en un cajón. Precedente: `SalidaLavaderoDAO` también cruza esas tablas y vive aparte. |
| **Agrupador propio** para las fracciones de equipo | `AgrupadorLineasHistorial`, con el texto de lavarropas **extraído a un helper compartido** | `AgrupadorInstanciasSalida` filtra a propósito las instancias **incompletas y las ya marcadas** — que es justo lo que el historial *tiene* que mostrar. Reusarlo obligaría a un flag booleano que invierte su regla de negocio. Lo único genuinamente común es armar `"3, 5"` a partir de las fracciones: **eso sí se extrae** a `TextoLavarropas` y lo usan los dos. |
| El detalle se ancla en **clasificación**, no en salidas | La consulta parte de `elementos_clasificacion_lavadero` con `LEFT JOIN` hacia ciclos y salidas | Anclarla en `salidas_lavadero` perdería las líneas clasificadas que todavía no se lavaron — exactamente las que un ingreso `CLASIFICADO` tiene para mostrar. |

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.

### Reglas duras del repo que este plan debe respetar

1. **Ningún acceso a BD en el EDT.** `TareaUI` (`ui/common/`) es el único mecanismo de trabajo en
   fondo: `.leer` hace el I/O, `.pintar` vuelve al EDT, `.siFalla` maneja el error. No hay
   `new Thread()` ni `SwingWorker`. `EdtGuard` grita en el log si alguien vuelve a poner I/O en el EDT.
2. **El estado mutable de un controller se lee y escribe sólo en el EDT.**
3. **Un controller declara en su constructor los services que usa.** No hay fachada; si necesita algo
   nuevo, se agrega un parámetro y `UiCoordinator` lo provee desde `AppContext`.
4. **Los services no tienen JDBC.** Validan y delegan (ver `SalidaLavaderoService`).
5. **Lógica de negocio embebida en Swing → clase plana sin Swing, testeada en aislamiento**
   (`AgrupadorInstanciasSalida`, `ConstructorVistaCiclos`, `StagingCiclos`).
6. **Una migración ya escrita no se toca.** *(Este plan no necesita ninguna migración: sólo lee.)*
7. **Nada del lavadero se borra nunca** — no hay un solo `DELETE` sobre ingresos/clasificación/ciclos.

### Esquema relevante (todo ya existe, V7→V20)

```
clientes(id, nombre)
ingresos_lavadero(id, cliente_id, fecha_ingreso, peso_total_kg, estado)   -- PENDIENTE|CLASIFICADO|LAVADO|FINALIZADO
bolsas_lavadero(id, ingreso_id, peso_kg)
catalogo_elementos_lavadero(id, nombre)
elementos_clasificacion_lavadero(id, ingreso_id, elemento_id, cantidad)
ciclos_lavadero(id, lavarropas_numero, tipo_jabon, ..., fecha_inicio, fecha_fin, estado, tipo_lavado)
elementos_ciclo_lavadero(id, ciclo_id, elemento_clasificacion_id, cantidad, instancia_equipo_id NULL)
instancias_equipo_ciclo(id, elemento_clasificacion_id, total_partes)
salidas_lavadero(id, elemento_ciclo_id NULL, instancia_equipo_id NULL, cantidad,
                 fecha_listo, destino NULL, equipo_otros_id NULL, fecha_salida NULL)
```

**Invariante de fracciones (crítico):** un `Equipo*` repartido en N lavarropas consume **1** unidad de
su línea de clasificación (no N), genera **1** fila de salida y **1** elemento en el CDE. En
`elementos_ciclo_lavadero` aparece como N filas que comparten `instancia_equipo_id`. **El historial
debe mostrarlo como UNA línea** con los N lavarropas en una celda (`"3, 5"`), igual que Salidas.

**`salidas_lavadero.destino`:** `NULL` = lista, sin destino todavía (estado legítimo, no dato
faltante) · `FUERA_DE_FLUJO` = devuelta al cliente · `CDE_OTROS` = derivada al CDE.

### Archivos de referencia (leer antes de escribir código nuevo)

| Para | Leer |
|---|---|
| Pantalla de consulta con filtros | `features/lavadero/view/PantallaVerCiclos.java` |
| Controller de consulta con filtros | `features/lavadero/controller/VerCiclosController.java` |
| Strategy de filtrado | `features/lavadero/controller/helpers/CicloFilterStrategy.java` |
| Doble clic → diálogo de detalle | `features/equipos/controller/VerEquiposController.java` (líneas 71-91, 169-189) |
| Diálogo de detalle | `features/equipos/view/helpers/DetalleOtrosDialog.java` |
| Reset de filtros al entrar | `VerEquiposController` `componentShown` → `panel.aplicarFiltroInicial()` |
| DAO de sólo lectura | `features/lavadero/dao/IngresoLavaderoDAO.findSinClasificar()` |
| SQL que cruza las tablas del lavadero | `features/lavadero/dao/SalidaLavaderoDAO.java` (las constantes `SQL_*`) |
| Agrupación de fracciones | `features/lavadero/dao/helpers/AgrupadorInstanciasSalida.java` |
| Cableado de un grupo de refresco | `app/ui/UiCoordinator.java` (`Disparador`, `crearRefrescadorHistorialCiclos`) |
| Trabajo en fondo | `ui/common/TareaUI.java` · `features/lavadero/controller/SalidasLavaderoController.java` |
| Tests de DAO con H2 | `src/test/java/com/example/AbstractDAOTest.java` |

### Comandos

```bash
mvn clean package                                  # target/aptium.jar
mvn test                                           # unitarios
mvn verify                                         # tests + cobertura JaCoCo
mvn test -Dtest=NombreDeClase
mvn test -Dtest=NombreDeClase#nombreDelMetodo
```

---

## Grafo de dependencias

```
Paso 1 (modelo + DAO)
   ├──► Paso 2 (service + filtros)  ─┐
   └──► Paso 3 (vista + diálogo)    ─┤   Pasos 2 y 3 en PARALELO
                                     │   (no comparten ningún archivo)
Paso 4 (botón 2×3)  ── independiente, sólo necesita la constante de Botones del Paso 3
                                     │
                    2,3,4 ───────────┴──► Paso 5 (controller + cableado)
                                                    │
                                                    ▼
                                            Paso 6 (smoke + docs)
```

| Paso | Modelo sugerido | Archivos que toca (exclusivos salvo aviso) |
|---|---|---|
| 1 | Opus (SQL de fracciones, la parte con trampa) | `lavadero/model/`, `lavadero/dao/`, `lavadero/dao/helpers/`, tests |
| 2 | Sonnet | `lavadero/service/`, `lavadero/controller/helpers/`, tests |
| 3 | Sonnet | `lavadero/view/`, `lavadero/view/helpers/`, `Constantes` |
| 4 | Sonnet | `lavadero/view/PantallaLavadero.java`, `Estilos` |
| 5 | Opus (cableado transversal) | `lavadero/controller/`, `AppContext`, `PantallaPrincipal`, `UiCoordinator` |
| 6 | Sonnet | `CLAUDE.md`, memoria, este archivo |

**Invariantes verificados después de CADA paso:**
- [ ] `mvn test` en verde (o, si el paso todavía no compila la app entera, `mvn -q compile`)
- [ ] Cero `new Thread()` / `SwingWorker` nuevos
- [ ] Cero JDBC fuera de un DAO
- [ ] Cero SQL construida por concatenación de input del usuario (siempre `PreparedStatement` con `?`)

---

## Paso 1 — Modelo de lectura + `HistorialLavaderoDAO`

### Contexto (autocontenido)

Se necesita leer dos cosas de la BD del lavadero, **sin escribir nada**:

- **Resumen** (fila maestra): un registro por ingreso, con lo que se muestra en la tabla y lo que
  hace falta para filtrar en memoria.
- **Detalle** (bajo demanda): las líneas de trazabilidad de UN ingreso.

Leé antes: `IngresoLavaderoDAO.findSinClasificar()` (forma de un DAO de lectura),
`SalidaLavaderoDAO` (las constantes `SQL_*` — este paso rehace recorridos casi idénticos) y
`AgrupadorInstanciasSalida` (agrupación en memoria de fracciones).

### Tareas

1. **`lavadero/model/IngresoHistorial.java`** — `record` inmutable:
   ```java
   public record IngresoHistorial(
       int id, String clienteNombre, LocalDateTime fechaIngreso,
       BigDecimal pesoTotalKg, int cantBolsas, EstadoIngresoLavadero estado,
       Set<String> elementos, Set<Integer> lavarropas) { ... }
   ```
   - Constructor compacto que hace `Set.copyOf(...)` de los dos sets (regla de inmutabilidad del repo).
   - Javadoc explicando **por qué** existen `elementos`/`lavarropas`: son agregados para que
     `HistorialFilterStrategy` (Paso 2) resuelva esos dos filtros en memoria. Nunca se muestran en
     la tabla maestra.

2. **`lavadero/model/LineaHistorial.java`** — `record` de una línea del detalle:
   ```java
   public record LineaHistorial(
       String elementoNombre, int cantidad, String lavarropas,   // "3" | "3, 5" | null (sin lavar)
       Integer totalPartes,                                       // null si no es fracción de equipo
       LocalDateTime fechaLavado, LocalDateTime fechaListo,
       DestinoSalida destino)                                     // null = sin destino todavía
   ```
   - `lavarropas` es **String** a propósito: una instancia de equipo ocupa varios (`"3, 5"`).
   - Javadoc: cada campo `null` significa "todavía no llegó a esa etapa", no "falta el dato".

3. **`lavadero/dao/helpers/TextoLavarropas.java`** — clase utilitaria final, no instanciable:
   ```java
   public static <T> String de(List<T> grupo, ToIntFunction<T> numero)
   ```
   Es el cuerpo actual de `AgrupadorInstanciasSalida.lavarropasTexto`, **movido tal cual**.
   Después, **modificar `AgrupadorInstanciasSalida`** para que delegue en él y borrar su copia
   privada. Javadoc: por qué no se usa `GROUP_CONCAT`/`STRING_AGG` (se comportan distinto entre H2 y
   MySQL) — ese comentario ya está en `AgrupadorInstanciasSalida`, **moverlo** con el código.

4. **`lavadero/dao/helpers/FilaHistorialCruda.java`** — `record` interno de transporte para las filas
   de instancia antes de agruparse (espejo de `FilaInstanciaEquipo`): `instanciaEquipoId`,
   `totalPartes`, `elementoNombre`, `lavarropasNumero`, `fechaFinCiclo`, `fechaListo`, `destino`.

5. **`lavadero/dao/helpers/AgrupadorLineasHistorial.java`** — clase plana, sin JDBC ni Swing:
   ```java
   public List<LineaHistorial> agrupar(List<FilaHistorialCruda> filas)
   ```
   - Agrupa por `instanciaEquipoId`, arma `lavarropas` con `TextoLavarropas.de(...)`.
   - `fechaLavado` = **máximo** `fechaFinCiclo` del grupo; `null` si alguna fracción sigue en un ciclo
     activo (la instancia no está lavada hasta que terminan todas).
   - `cantidad` siempre **1** (un equipo es un equipo).
   - **A diferencia de `AgrupadorInstanciasSalida`, NO descarta** instancias incompletas ni ya
     marcadas: el historial las muestra. Dejar esa diferencia escrita en el Javadoc de la clase.

6. **`lavadero/dao/HistorialLavaderoDAO.java`** — sin estado salvo el agrupador; sólo lectura.

   `SQL_RESUMEN` — una consulta que devuelve **filas crudas** `(ingreso…, elemento, lavarropas)` y se
   pliega en memoria a un `IngresoHistorial` por `id`. **No** usar subconsultas correlacionadas por
   fila ni `GROUP_CONCAT`:
   ```sql
   SELECT il.id, c.nombre AS cliente, il.fecha_ingreso, il.peso_total_kg, il.estado,
          cel.nombre AS elemento, cl.lavarropas_numero
   FROM ingresos_lavadero il
   JOIN clientes c                                 ON c.id   = il.cliente_id
   LEFT JOIN elementos_clasificacion_lavadero ecl  ON ecl.ingreso_id = il.id
   LEFT JOIN catalogo_elementos_lavadero cel       ON cel.id = ecl.elemento_id
   LEFT JOIN elementos_ciclo_lavadero eci          ON eci.elemento_clasificacion_id = ecl.id
   LEFT JOIN ciclos_lavadero cl                    ON cl.id  = eci.ciclo_id
   ORDER BY il.fecha_ingreso DESC, il.id DESC
   ```
   ⚠️ **`cantBolsas` va en una consulta aparte** (`SELECT ingreso_id, COUNT(*) FROM bolsas_lavadero
   GROUP BY ingreso_id`) y se cruza en memoria por id. Meter `bolsas_lavadero` en el `LEFT JOIN` de
   arriba multiplicaría las filas y un `COUNT` daría un número inflado — es el bug clásico de esta
   consulta. Los `LEFT JOIN` restantes no rompen nada porque el resultado se pliega a **sets**.

   `SQL_DETALLE_REGULAR` (parametrizada por `ingreso_id`) — líneas **no** fraccionadas, incluyendo las
   clasificadas y todavía sin lavar:
   ```sql
   SELECT cel.nombre AS elemento,
          COALESCE(eci.cantidad, ecl.cantidad) AS cantidad,
          cl.lavarropas_numero, cl.fecha_fin, sl.fecha_listo, sl.destino
   FROM elementos_clasificacion_lavadero ecl
   JOIN catalogo_elementos_lavadero cel  ON cel.id = ecl.elemento_id
   LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id
                                          AND eci.instancia_equipo_id IS NULL
   LEFT JOIN ciclos_lavadero cl           ON cl.id  = eci.ciclo_id
   LEFT JOIN salidas_lavadero sl          ON sl.elemento_ciclo_id = eci.id
   WHERE ecl.ingreso_id = ?
   ORDER BY cel.nombre
   ```
   `SQL_DETALLE_INSTANCIA` (parametrizada por `ingreso_id`) — las fracciones, una fila por lavarropas,
   que después agrupa `AgrupadorLineasHistorial`:
   ```sql
   SELECT eci.instancia_equipo_id, ie.total_partes, cel.nombre AS elemento,
          cl.lavarropas_numero, cl.fecha_fin, sl.fecha_listo, sl.destino
   FROM elementos_ciclo_lavadero eci
   JOIN instancias_equipo_ciclo ie            ON ie.id  = eci.instancia_equipo_id
   JOIN elementos_clasificacion_lavadero ecl  ON ecl.id = eci.elemento_clasificacion_id
   JOIN catalogo_elementos_lavadero cel       ON cel.id = ecl.elemento_id
   JOIN ciclos_lavadero cl                    ON cl.id  = eci.ciclo_id
   LEFT JOIN salidas_lavadero sl              ON sl.instancia_equipo_id = eci.instancia_equipo_id
   WHERE ecl.ingreso_id = ? AND eci.instancia_equipo_id IS NOT NULL
   ```
   `findDetalle(int ingresoId)` corre las dos y concatena: regulares + agrupadas.

   ⚠️ **Casos parciales — leer antes de dar por buena la consulta regular.** Una línea de
   clasificación se puede lanzar a medias (10 clasificadas, 5 en un ciclo). Con el `LEFT JOIN` de
   arriba, esa línea rinde **una sola** fila de cantidad 5 y las otras 5 desaparecen del detalle.
   Hay que emitir además una línea *"pendiente de lavar"* por la diferencia, cuando es positiva:
   ```sql
   SELECT cel.nombre AS elemento,
          ecl.cantidad - COALESCE(SUM(eci.cantidad), 0) AS cantidad_sin_lanzar
   FROM elementos_clasificacion_lavadero ecl
   JOIN catalogo_elementos_lavadero cel   ON cel.id = ecl.elemento_id
   LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id
   WHERE ecl.ingreso_id = ?
   GROUP BY ecl.id, cel.nombre, ecl.cantidad
   HAVING cantidad_sin_lanzar > 0
   ```
   Esas filas se emiten como `LineaHistorial` con `lavarropas`, `fechaLavado`, `fechaListo` y
   `destino` en `null`. Con esto la suma de cantidades del detalle **siempre cierra contra lo
   clasificado**, que es la propiedad que hace confiable a un historial. Va con test propio.

   Limitación aceptada y **documentada en el Javadoc del DAO**: si una tanda lavada se marcó Listo
   parcialmente (5 lavadas, 3 marcadas), la línea muestra cantidad 5 con `fechaListo` puesta. El
   historial trabaja al grano de la tanda, no de la unidad doblada; abrirlo a ese grano duplicaría el
   modelo de Salidas sin que nadie lo haya pedido.

   **Manejo de errores:** seguir a `SalidaLavaderoDAO`, **no** a los DAOs viejos del lavadero — un
   fallo de SQL sale como `DataAccessException`/`DatabaseException`, nunca como lista vacía.
   Devolver vacío ante un error le miente a la pantalla, que mostraría "no hay historial".

7. **Tests:**
   - `AgrupadorLineasHistorialTest` (unitario, sin BD): instancia completa de 2 partes → 1 línea con
     `"3, 5"`; instancia con una parte en ciclo activo → 1 línea con `fechaLavado == null`; instancia
     ya derivada → conserva su `destino`; lista vacía → lista vacía.
   - `HistorialLavaderoDAOTest extends AbstractDAOTest`: ingreso sin clasificar → 1 fila con sets
     vacíos; **ingreso con 3 bolsas y 2 elementos → `cantBolsas == 3`** (el test que atrapa el bug del
     `JOIN`); ingreso con equipo fraccionado en 2 lavarropas → el detalle trae **1** línea, no 2;
     línea clasificada sin lavar → aparece con `lavarropas == null`; salida sin destino →
     `destino == null` y `fechaListo != null`.
   - Reusar el patrón de `limpiarTablas()` con prefijos `Test%` de `CatalogoElementosLavaderoDAOTest`.

### Verificación

```bash
mvn test -Dtest=AgrupadorLineasHistorialTest
mvn test -Dtest=HistorialLavaderoDAOTest
mvn test -Dtest=AgrupadorInstanciasSalidaTest   # no debe romperse con la extracción de TextoLavarropas
mvn test
```

### Criterio de salida

- [ ] `HistorialLavaderoDAO` no tiene un solo `INSERT`/`UPDATE`/`DELETE`
- [ ] Todo parámetro va por `?`; cero concatenación de strings en SQL
- [ ] `AgrupadorInstanciasSalida` ya no tiene su copia de `lavarropasTexto`, y sus tests siguen verdes
- [ ] El test de `cantBolsas` pasa con un ingreso de 3 bolsas y 2 elementos
- [ ] Commit: `feat: lectura del historial de lavadero (modelo + DAO)`

---

## Paso 2 — Service + estrategia de filtrado

> **Paralelo con el Paso 3.** No comparten archivos. Depende del Paso 1 (usa `IngresoHistorial`).

### Contexto (autocontenido)

La app filtra **siempre en memoria** sobre un snapshot: `AbstractFilterController<T>` guarda la lista
completa y `FilterStrategy<T, C>` la filtra con un criterio. Los services del lavadero **no tienen
JDBC**: validan y delegan al DAO. Leé `CicloFilterStrategy` + `CicloFilterCriteria` (el espejo exacto
de lo que hay que escribir) y `SalidaLavaderoService` (forma de un service que sólo delega).

### Tareas

1. **`lavadero/service/HistorialLavaderoService.java`**
   - Constructor recibe `HistorialLavaderoDAO`, rechaza `null` con `IllegalArgumentException`.
   - `List<IngresoHistorial> obtenerHistorial()` → delega.
   - `List<LineaHistorial> obtenerDetalle(int ingresoId)` → valida `ingresoId > 0` con
     `ValidationException.builder().addErrorIf(...).throwIfHasErrors()` y delega.
   - **Cero JDBC.**

2. **`lavadero/controller/helpers/HistorialFilterCriteria.java`** — `record`:
   `String cliente, List<String> estados, LocalDate desde, LocalDate hasta, String elemento,
   Integer lavarropas`. Constructor compacto con `List.copyOf(estados)`.

3. **`lavadero/controller/helpers/HistorialFilterStrategy.java`**
   `implements FilterStrategy<IngresoHistorial, HistorialFilterCriteria>`, un método privado por
   filtro (misma forma que `CicloFilterStrategy`):

   | Filtro | Regla |
   |---|---|
   | Cliente | vacío → pasa; si no, `contains` insensible a mayúsculas sobre `clienteNombre` |
   | Estados | lista vacía → pasa; si no, `estado.name()` debe estar en la lista (`equalsIgnoreCase`) |
   | Fechas | `fechaIngreso.toLocalDate()` dentro de `[desde, hasta]`; extremo `null` = abierto; `fechaIngreso == null` sólo pasa si **ambos** extremos son `null` (misma regla que `VerEquiposController.cumpleFecha`) |
   | Elemento | vacío → pasa; si no, **algún** nombre de `elementos()` contiene el texto (insensible a mayúsculas) |
   | Lavarropas | `null` → pasa; si no, `lavarropas().contains(n)` |

   `source` `null` o vacía → `List.of()`.

4. **Tests:**
   - `HistorialFilterStrategyTest` — un test por regla, más: filtros vacíos devuelven todo; los cinco
     filtros combinados se aplican en AND; un ingreso `FINALIZADO` **no** pasa cuando la lista de
     estados es `[PENDIENTE, CLASIFICADO, LAVADO]` (**el default de la pantalla**); ingreso con
     `elementos` vacío no pasa el filtro de elemento.
   - `HistorialLavaderoServiceTest` (Mockito) — delegación en los dos métodos; `obtenerDetalle(0)` y
     `obtenerDetalle(-1)` lanzan `ValidationException` **sin tocar el DAO** (`verifyNoInteractions`).

### Verificación

```bash
mvn test -Dtest=HistorialFilterStrategyTest
mvn test -Dtest=HistorialLavaderoServiceTest
mvn test
```

### Criterio de salida

- [ ] `HistorialLavaderoService` no importa nada de `java.sql`
- [ ] El test del default (`FINALIZADO` excluido) existe y pasa
- [ ] Commit: `feat: service y filtros del historial de lavadero`

---

## Paso 3 — Pantalla + diálogo de detalle

> **Paralelo con el Paso 2.** No comparten archivos. Depende del Paso 1 (usa `IngresoHistorial` y
> `LineaHistorial`).

### Contexto (autocontenido)

Pantalla de consulta = `JPanel` con `PanelHeader` arriba, panel de filtros debajo del header, y una
`JTable` al centro. **Copiar la estructura de `PantallaVerCiclos`** (`crearPanelFiltros`,
`TableStyler.applyStandard`, `FilterUiHelper.bind*`, `notificarCambio`, `setOnFiltrosChanged`).
La vista **no conoce el controller**: expone getters de filtros y setters de callbacks.

El diálogo de detalle copia la estructura de `DetalleOtrosDialog`: panel `GridBagLayout` de datos
arriba, `JTable` al centro, botón Cerrar abajo, modal, `setLocationRelativeTo(parent)`.

### Tareas

1. **`Constantes`** — agregar:
   - `Pantallas.HISTORIAL_LAVADERO = "HISTORIAL_LAVADERO"`
   - `Titulos.HISTORIAL_LAVADERO   = "HISTORIAL DE LAVADERO"`
   - `Botones.HISTORIAL            = "Historial"`
   - Etiquetas de filtro nuevas (`"Elemento:"`, etc.) si no hay una reutilizable. `Textos.FILTRO_ESTADO` **ya existe** — usarlo.

2. **`lavadero/view/PantallaHistorialLavadero.java`**
   - Columnas: `{"ID", "Cliente", "Fecha ingreso", "Peso (kg)", "Bolsas", "Estado"}`.
   - `COL_ESTADO` derivado con `List.of(COLUMNAS).indexOf("Estado")` (mismo truco que `PantallaVerCiclos`).
   - `public void actualizarIngresos(List<IngresoHistorial> ingresos)` — repuebla el `DefaultTableModel`
     (`setRowCount(0)` + `addRow`), como `PantallaVerCiclos.actualizarCiclos`. Es el método que
     llama el controller desde `aplicarFiltros()`.
   - **Guardar en un campo la misma lista que se pintó** (`List<IngresoHistorial> filasVisibles`,
     asignada dentro de `actualizarIngresos`) y exponer `getIngresoAt(int modelRow)`, igual que
     `PantallaVerEquipos.getEquipoOtrosAt`. Sin esto el controller no puede resolver el doble clic a
     un id. Devolver `null` si el índice está fuera de rango.
   - `tabla.setRowSelectionAllowed(true)` (⚠️ `PantallaVerCiclos` lo pone en `false`; acá hace falta).
   - Fechas con `DateTimeDisplayUtils.formatForUi`.
   - Filtros: `txtCliente`, `cmbEstado` (`CheckableComboBox` con los 4 valores de
     `EstadoIngresoLavadero`), `dateDesde`, `dateHasta`, `txtElemento`, `txtLavarropas`
     (`RestriccionesCampo.soloNumeros`), `btnLimpiar`.
   - Cableado: `FilterUiHelper.bindOnTextChange(...)`, `bindOnDateChange(...)`,
     `cmbEstado.setOnSelectionChange(...)`.
   - `public void restablecerFiltrosPorDefecto()` — limpia todos los campos y hace
     `cmbEstado.setSelectedItems(List.of("PENDIENTE","CLASIFICADO","LAVADO"))`
     (`CheckableComboBox.setSelectedItems` ya existe). **No notifica**: el controller llama a esto y
     después a `solicitarRefresco`, y `pintar()` es el único que filtra y repinta — así no hay flash
     con datos viejos (mismo razonamiento que el comentario de `VerEquiposController` línea 73).
   - `limpiarFiltros()` (el botón) sí notifica, y deja el **mismo default** que
     `restablecerFiltrosPorDefecto` — "Limpiar" devuelve al estado de entrada, no a "todo visible".
   - Getters: `getFiltroCliente()`, `getFiltroEstados()`, `getFiltroDesde()`, `getFiltroHasta()`,
     `getFiltroElemento()`, `getFiltroLavarropas()` (parseo defensivo a `Integer`, `null` si no parsea),
     `getTablaIngresos()`.

3. **`lavadero/view/helpers/DetalleHistorialDialog.java`**
   - `DetalleHistorialDialog(Window parent, IngresoHistorial ingreso, List<LineaHistorial> lineas)`
   - Título: `"Historial del ingreso #" + ingreso.id()`.
   - Panel de datos (`GridBagLayout`): Cliente, Fecha de ingreso, Peso, Bolsas, Estado.
   - Tabla de líneas: `{"Elemento", "Cantidad", "Lavarropas", "F. lavado", "F. listo", "Destino"}`.
     - `lavarropas == null` → `"—"`; con `totalPartes != null` → `"3, 5 (2 partes)"`.
     - `destino == null` → `"Sin destino"`; si no, `destino.getNombre()`.
     - Fechas `null` → `"—"`.
   - `lineas` vacía → en vez de la tabla, un `JLabel` centrado *"Este ingreso todavía no fue
     clasificado."* (un ingreso `PENDIENTE` no tiene ninguna línea; una tabla vacía no lo explica).
   - Botón Cerrar, `dispose()`.
   - **Cero I/O**: el diálogo recibe los datos ya leídos.

### Verificación

```bash
mvn -q compile
mvn test
```

Compilación solamente: la pantalla todavía no está registrada ni cableada (Pasos 4 y 5).

### Criterio de salida

- [ ] `PantallaHistorialLavadero` no importa ningún service ni DAO
- [ ] `DetalleHistorialDialog` no importa ningún service ni DAO
- [ ] `restablecerFiltrosPorDefecto()` deja `FINALIZADO` desmarcado y **no** dispara el callback
- [ ] Commit: `feat: pantalla y diálogo de detalle del historial de lavadero`

---

## Paso 4 — Menú de Lavadero: de columna a grilla 2×3

> Depende del Paso 3 sólo por `Constantes.Botones.HISTORIAL`.

### Contexto (autocontenido)

`PantallaLavadero` hoy apila 5 botones en `GridLayout(0, 1, 0, SEPARACION_MENU)` dentro de un panel
`GridBagLayout` centrado, con ancho fijo `Estilos.Dimensiones.ANCHO_MENU` (560). Tiene este comentario:

```java
// Menú en columna: con 5 opciones una grilla deja una fila incompleta,
// así que todos los botones van uno debajo del otro, del mismo tamaño.
```

Con el sexto botón esa razón deja de ser cierta: **6 botones llenan una grilla 2×3 exacta**. El
comentario hay que reescribirlo, no borrarlo — si queda como está, miente.

La convención del repo es `GridLayout(filas, columnas)`: `PantallaMenu` usa `GridLayout(2, 2, 15, 15)`.

### Tareas

1. **`Estilos.Dimensiones`** — agregar:
   ```java
   /** Ancho del menú de Lavadero, que a diferencia del resto va en grilla de 3 columnas. */
   public static final int ANCHO_MENU_GRILLA = 840;
   ```
   Con `ANCHO_MENU` (560) repartido en 3 columnas cada botón queda en ~180 px y "Ver Ciclos" se corta
   con `Fuentes.BOTON`. Constante nombrada, no un `* 3 / 2` en la vista.

2. **`PantallaLavadero`**
   - `GridLayout(0, 1, ...)` → `GridLayout(2, 3, Estilos.Espaciados.SEPARACION_MENU, Estilos.Espaciados.SEPARACION_MENU)`.
   - Renombrar la variable `columna` a `grilla` (ya no es una columna).
   - `setPreferredSize` con `ANCHO_MENU_GRILLA`.
   - Orden, siguiendo el flujo real del lavadero:
     ```
     Ingresar   Clasificar   Ciclos
     Salidas    Ver Ciclos   Historial
     ```
   - Campo `private JButton btnHistorial;` + `crearBoton(Constantes.Botones.HISTORIAL)` +
     `public JButton getBtnHistorial()`. **Sin `ActionListener` acá**: la navegación la cablea
     `UiCoordinator` (Paso 5), igual que `btnCiclos`/`btnSalidas`/`btnVerCiclos`.
   - **Reescribir el comentario** por algo como: *"Seis opciones llenan una grilla 2×3 exacta; con
     cinco quedaba una celda vacía y por eso antes iban en columna."*

### Verificación

```bash
mvn -q compile
mvn clean package && java -jar target/aptium.jar   # inspección visual del menú de Lavadero
```

### Criterio de salida

- [ ] Los 6 botones se ven en 2 filas × 3 columnas, del mismo tamaño, sin texto cortado
- [ ] El comentario del layout describe la situación actual
- [ ] "Historial" todavía no navega a ningún lado (se cablea en el Paso 5) — es lo esperado
- [ ] Commit: `feat: menú de Lavadero en grilla 2x3 con botón Historial`

---

## Paso 5 — Controller + cableado completo

> Depende de los Pasos 1, 2, 3 y 4.

### Contexto (autocontenido)

`UiCoordinator` es el único punto de la UI que ve el `AppContext` completo. Hay **cuatro grupos de
refresco**, cada uno con su `Disparador` (handle diferido que rompe el ciclo
`controller → refrescador → controller`) y su `RefrescadorPantallas<T>`:

- `operativo` → en cada guardado
- `historialEquipos` → al abrir "Ver Equipos" / "Estado de procesos"
- `historialLotes` → al abrir "Ver Lotes"
- `historialCiclos` → al abrir "Ver Ciclos"

El historial de lavadero es un **quinto grupo**: nadie más consume esos datos, y meterlo en otro grupo
haría que abrir esa otra pantalla leyera además el historial entero del lavadero.

`RefrescadorPantallas` corre la lectura en fondo vía `TareaUI` y reparte el snapshot en el EDT.

### Tareas

1. **`lavadero/controller/HistorialLavaderoController extends AbstractFilterController<IngresoHistorial>`**
   - Constructor: `(PantallaHistorialLavadero pantalla, HistorialLavaderoService service, Runnable solicitarRefresco)`.
     Alcance declarado en la firma: pintar la grilla desde el refresco + leer el detalle bajo demanda.
   - `pantalla.setOnFiltrosChanged(this::aplicarFiltros)`.
   - `componentShown` →
     ```java
     pantalla.restablecerFiltrosPorDefecto();   // sin notificar
     solicitarRefresco.run();                   // pintar() filtra y repinta
     ```
   - Doble clic en la tabla (`MouseAdapter`, `e.getClickCount() == 2`) →
     `convertRowIndexToModel` → `pantalla.getIngresoAt(modelRow)` → si `null`, salir.
   - **El detalle se lee con `TareaUI`, nunca en el EDT:**
     ```java
     TareaUI.<List<LineaHistorial>>nueva()
         .nombre("detalle-historial-lavadero")
         .leer(() -> service.obtenerDetalle(ingreso.id()))
         .pintar(lineas -> new DetalleHistorialDialog(
                 SwingUtilities.getWindowAncestor(pantalla), ingreso, lineas).setVisible(true))
         .siFalla(this::mostrarErrorDetalle)
         .lanzar();
     ```
     (Firma verificada contra `TareaUI.java`: `nueva()` / `nombre` / `leer(Callable<T>)` /
     `pintar(Consumer<T>)` / `siFalla(Consumer<Throwable>)` / `lanzar()`. `SalidasLavaderoController`
     es la implementación de referencia del patrón.)
   - `public void pintar(List<IngresoHistorial> historial)` → `recargarCache(historial)`. Sin I/O.
   - `aplicarFiltros()` arma el `HistorialFilterCriteria` desde los getters de la pantalla y llama
     `pantalla.actualizarIngresos(strategy.filter(getCache(), criteria))`.
   - `mostrarErrorDetalle(Throwable)` → `JOptionPane` de error. **No** tragar la excepción.

2. **`AppContext`**
   - Campos `HistorialLavaderoDAO` y `HistorialLavaderoService`, construidos en `createDefault()`
     junto a los demás del lavadero.
   - `public HistorialLavaderoService getHistorialLavaderoService()`.
   - Agregar el service al constructor explícito de `AppContext` si ese constructor los enumera
     (verificar; hay un constructor largo además de `createDefault`).

3. **`PantallaPrincipal`**
   - Campo `pantallaHistorialLavadero`, instanciación con `(navegador, contenedor)`,
     `contenedor.add(..., Constantes.Pantallas.HISTORIAL_LAVADERO)`, y el getter.
   - Mantener el orden/alineación de los bloques del lavadero que ya hay en el archivo.

4. **`UiCoordinator.inicializar()`**
   - `Disparador historialLavadero = new Disparador();` junto a los otros cuatro, y **actualizar el
     comentario del bloque** que hoy enumera cuatro grupos → cinco, con la línea
     `· historial lavadero → al abrir "Historial"`.
   - ```java
     HistorialLavaderoController historialLavaderoController = new HistorialLavaderoController(
         vista.getPantallaHistorialLavadero(),
         context.getHistorialLavaderoService(),
         historialLavadero);
     ```
   - `historialLavadero.cablear(crearRefrescadorHistorialLavadero(historialLavaderoController));`
     junto a los otros `cablear(...)`.
   - Método privado nuevo, calcado de `crearRefrescadorHistorialCiclos`:
     ```java
     /** La pantalla que consulta el historial completo del lavadero. */
     private RefrescadorPantallas<List<IngresoHistorial>> crearRefrescadorHistorialLavadero(
         HistorialLavaderoController historial
     ) {
         return new RefrescadorPantallas<>(
             "refresco-historial-lavadero",
             context.getHistorialLavaderoService()::obtenerHistorial,
             historial::pintar,
             this::mostrarErrorDeRefresco);
     }
     ```
   - Navegación, junto a la de `btnVerCiclos`:
     ```java
     vista.getPantallaLavadero().getBtnHistorial().addActionListener(e ->
         vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.HISTORIAL_LAVADERO));
     ```
     Sin llamada de carga extra: `componentShown` del controller ya dispara el refresco (mismo patrón
     que `btnVerCiclos`, a diferencia de `btnCiclos`/`btnSalidas` que sí llaman a un método del
     controller).
   - **No** agregar el nuevo disparador al primer pintado del arranque: como las otras pantallas de
     consulta, lee al abrirse.

### Verificación

```bash
mvn -q compile
mvn test
mvn clean package && java -jar target/aptium.jar
```

Smoke manual (**sin** `-Daptium.edt.strict=true` — los autocompletados síncronos de otras pantallas
lanzarían; leer los WARN del log en su lugar):

1. Lavadero → Historial: la tabla carga y el combo de estados muestra los 3 activos, sin `FINALIZADO`.
2. Marcar `FINALIZADO` → aparecen los ingresos archivados.
3. Volver al menú y entrar de nuevo → **los filtros volvieron al default** y los datos se releyeron.
4. Probar cada filtro por separado y dos combinados.
5. Doble clic en un ingreso `CLASIFICADO` → diálogo con líneas y `lavarropas == "—"`.
6. Doble clic en un ingreso con un equipo repartido en 2 lavarropas → **una** línea con `"3, 5 (2 partes)"`.
7. Doble clic en un ingreso `PENDIENTE` → el mensaje de "todavía no fue clasificado".
8. Revisar el log: **ningún WARN de `EdtGuard` atribuible a la pantalla nueva**.

### Criterio de salida

- [ ] Los 8 puntos del smoke pasan
- [ ] Cero WARN de `EdtGuard` desde `HistorialLavaderoController` / `HistorialLavaderoDAO`
- [ ] El comentario de los grupos de refresco en `UiCoordinator` dice cinco, no cuatro
- [ ] `mvn test` en verde
- [ ] Commit: `feat: historial de lavadero cableado en el menú`

---

## Paso 6 — Cobertura, documentación y cierre

### Tareas

1. `mvn verify` y revisar el reporte JaCoCo: `HistorialFilterStrategy`,
   `AgrupadorLineasHistorial` y `HistorialLavaderoService` deben estar **≥ 80 %**. Las clases Swing
   (`PantallaHistorialLavadero`, `DetalleHistorialDialog`) quedan sin cubrir — es la convención del
   repo, la lógica ya está extraída a clases planas.
2. **`CLAUDE.md`** — en la sección "Lavadero", agregar un párrafo corto:
   - Historial es una **pantalla de consulta de sólo lectura**; por defecto oculta los ingresos
     `FINALIZADO` (lo que ya salió del flujo) y al entrar resetea filtros + relee.
   - El detalle se lee **bajo demanda por `TareaUI`**, no en el snapshot.
   - Es el **quinto grupo de refresco** (`historial lavadero`).
3. **Memoria** — crear
   `~/.claude/projects/c--Trabajo-Administracion-Aptium/memory/project-historial-lavadero.md`
   (`type: project`) apuntando a este plan y a las dos decisiones no obvias: "fuera del flujo" =
   `FINALIZADO`, y detalle bajo demanda en vez de snapshot completo. Enlazar
   `[[project-fracciones-equipo-sin-persistir]]` y `[[project-architecture]]`. Agregar la línea al
   índice `MEMORY.md`.
4. Marcar este plan como **✅ CERRADO** arriba de todo, con los SHAs de los commits de cada paso.

### Criterio de salida

- [ ] `mvn verify` en verde y cobertura ≥ 80 % en las tres clases planas
- [ ] `CLAUDE.md` describe Historial
- [ ] Memoria e índice actualizados
- [ ] Commit: `docs: historial de lavadero`

---

## Catálogo de anti-patrones para este plan

Cosas que un agente ejecutando esto en frío hace mal si no se le avisa:

| Anti-patrón | Por qué está mal acá |
|---|---|
| Copiar `VerEquiposController.abrirDetalleOtros()` tal cual | Hace I/O sincrónico en el EDT. Es una violación preexistente, no un patrón. Usar `TareaUI`. |
| Contar bolsas en el `LEFT JOIN` de `SQL_RESUMEN` | Los joins de clasificación/ciclos multiplican filas y el `COUNT` sale inflado. Consulta aparte + cruce en memoria. |
| Reusar `AgrupadorInstanciasSalida` con un flag | Su regla de negocio (descartar incompletas y ya marcadas) es la **inversa** de la del historial. Sólo se comparte el texto de lavarropas. |
| Copiar `lavarropasTexto` en vez de extraerlo | Duplicación pura; el usuario pidió explícitamente no repetir código. |
| Mostrar N filas para un equipo repartido en N lavarropas | Rompe el invariante central del lavadero: un equipo repartido es **1** unidad. |
| `GROUP_CONCAT` / `STRING_AGG` en el SQL | Se comportan distinto entre H2 (tests) y MySQL (producción). El repo agrupa en memoria a propósito. |
| Devolver `List.of()` cuando el DAO falla | Le miente a la pantalla: "no hay historial" y "no pude leer" no son lo mismo. Propagar. |
| Que `restablecerFiltrosPorDefecto()` dispare el callback | Repinta con el snapshot viejo antes de que llegue el nuevo → flash de datos desactualizados. |
| Borrar el comentario del layout de `PantallaLavadero` | Explica una decisión; hay que **actualizarlo**, porque con 6 botones deja de ser cierto. |
| Agregar el disparador nuevo al `operativo.solicitar()` del arranque | Las pantallas de consulta leen al abrirse; ninguna está visible al arrancar. |
| Anclar el detalle en `salidas_lavadero` | Pierde las líneas clasificadas y todavía sin lavar. Anclar en clasificación. |
| Olvidar la línea "pendiente de lavar" de una clasificación lanzada a medias | Las cantidades del detalle no cierran contra lo clasificado y el historial deja de ser confiable. Ver el bloque *Casos parciales* del Paso 1. |
| Escribir una migración | Este plan **no necesita ninguna**: sólo lee tablas que ya existen (V7→V20). |

---

## Protocolo de mutación del plan

Si al ejecutar aparece algo que el plan no previó:

- **Dividir un paso** → agregarlo como `Paso N.5` con su propio contexto y criterio de salida.
- **Saltear un paso** → dejar escrito *por qué* en una sección "Mutaciones aplicadas" al final; no borrarlo.
- **Cambiar una decisión de la tabla de arriba** → tacharla (`~~...~~`) y escribir la nueva con fecha.
  Las decisiones tomadas con el usuario **no se cambian sin preguntarle**.
