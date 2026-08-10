# Plan — Ciclos de Lavadero: Tipo de Lavado, grilla 3×N y DnD multi-selección

**Objetivo:** en la pantalla *Ciclos* (cards de Lavarropas) — quitar las columnas `Ingreso #`,
mostrar las cards en filas de a 3, agregar el dato **Tipo de Lavado** (Limpio / Sucio / Podrido)
persistido por ciclo, y reutilizar el drag-and-drop multi-fila que ya existe en Gestionar Lotes
para mover varios elementos entre la tabla de disponibles y los lavarropas en ambos sentidos.

**Rama base:** `ConexionConCDE` · **Modo:** directo (todos los pasos en la misma rama, commit por paso)
**Fecha de creación:** 2026-08-06

---

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Modelo de Tipo de Lavado | **Enum Java `TipoLavado` + columna `VARCHAR(20)`**, igual que `TipoIngresoOtros`. No tabla catálogo: las 3 opciones son fijas y no tienen ABM. |
| Obligatoriedad | **Obligatorio, sin default en la UI ni en la BD.** El combo arranca vacío y `Lanzar` queda deshabilitado hasta elegirlo (mismo criterio que `mL Jabón`). Validación en `CicloLavaderoService`. **No hay backfill**: en producción las migraciones `V7`–`V14` (todo lavadero) todavía no se aplicaron, así que no existe ninguna fila de `ciclos_lavadero` en ningún entorno. |
| Devolver un equipo subdividido | **Devolver una fracción deshace la subdivisión completa**: se quitan todas las fracciones con el mismo `instanciaId` en todas las cards. Evita dejar un `1/3` huérfano inconsistente. |
| Ver Ciclos | **Se agrega la columna "Tipo de Lavado".** *No* se agrega filtro por tipo. |
| Semántica del tipo | **Es sólo un dato**: se elige, se guarda y se muestra. No valida nada, no condiciona el jabón ni los litros, no restringe qué se puede mezclar en un lavarropas. Si alguna vez tiene que disparar comportamiento, es otro plan. |
| Multi-drag de equipos | **Cascada de diálogos secuenciales**, paridad exacta con Gestionar Lotes: spinner de unidades + un `EquipoSubdivisionDialog` por unidad, cancelar uno saltea sólo ese ítem. No se rediseña el diálogo para manejar la tanda entera. |
| Arrastre card → card | **Bloqueado.** Para mover un elemento entre lavarropas hay que devolverlo a disponibles y volver a arrastrarlo. Evita tener que decidir qué pasa con una fracción de un equipo subdividido que se mueve sola. |

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.

Archivos del alcance:

| Archivo | Rol |
|---|---|
| [PantallaCiclos.java](src/main/java/com/example/features/lavadero/view/PantallaCiclos.java) | Vista: tabla de disponibles arriba + grilla de `LavarropasCard` abajo, en un `JSplitPane` |
| [LavarropasCard.java](src/main/java/com/example/features/lavadero/view/LavarropasCard.java) | Card de un lavarropas: título colapsable, tabla de ítems, panel de config (jabón, mL, suavizante, potenciador, L totales), botón Lanzar/Finalizar |
| [CiclosController.java](src/main/java/com/example/features/lavadero/controller/CiclosController.java) | Orquesta staging (`pendientesPorLavarropas`), DnD, lanzar/finalizar |
| [ElementoDisponibleTableModel.java](src/main/java/com/example/features/lavadero/view/helpers/ElementoDisponibleTableModel.java) | Modelo de la tabla de disponibles |
| [LavarropasCardTableModel.java](src/main/java/com/example/features/lavadero/view/helpers/LavarropasCardTableModel.java) | Modelo de la tabla dentro de cada card |
| [CicloLavadero.java](src/main/java/com/example/features/lavadero/model/CicloLavadero.java) | Modelo del ciclo (inmutable, estado derivado de `fechaFin`) |
| [ElementoCicloItem.java](src/main/java/com/example/features/lavadero/model/ElementoCicloItem.java) | Ítem transportado en el DnD; `instanciaId` sólo para equipos en staging |
| [CicloLavaderoDAO.java](src/main/java/com/example/features/lavadero/dao/CicloLavaderoDAO.java) | JDBC de `ciclos_lavadero` / `elementos_ciclo_lavadero` |
| [CicloLavaderoService.java](src/main/java/com/example/features/lavadero/service/CicloLavaderoService.java) | Validación con `ValidationException.builder()` |
| [PantallaVerCiclos.java](src/main/java/com/example/features/lavadero/view/PantallaVerCiclos.java) | Listado histórico de ciclos |

**Infra a reutilizar (ya existe, no reescribir):**

| Archivo | Qué aporta |
|---|---|
| [MultiRowTableTransferHandler.java](src/main/java/com/example/ui/common/dnd/MultiRowTableTransferHandler.java) | `TransferHandler` genérico multi-fila configurado por Builder (`sourceActions`, `selectionSupplier`, `canImportExtra`, `onImport`, `onExportDone`) |
| [MultiRowTransferable.java](src/main/java/com/example/ui/common/dnd/MultiRowTransferable.java) | `Transferable` que transporta una `List<T>` dentro de la misma JVM |
| [TableSelectionSupport.java](src/main/java/com/example/ui/common/dnd/TableSelectionSupport.java) | `enableMultiSelection(JTable)` y `selectedItems(JTable, IntFunction<T>)` |
| [LotesController.java:315-430](src/main/java/com/example/features/lotes/controller/LotesController.java#L315-L430) | **Referencia de uso canónica**: cómo Lotes cablea los dos handlers, el flag anti-rebote y los diálogos secuenciales |

**Migraciones:** Flyway sobre `src/main/resources/db/migration/`. Última: `V14__autoclave_ch9.sql`
→ la nueva es **`V15`**. Los DDL deben ser compatibles con **H2 (tests)** *y* **MySQL (producción)**:
un `ALTER TABLE` por sentencia, sin `AFTER` (ver `V12__catalogo_jabones_potenciador.sql` como precedente).

**Cableado:** ningún paso agrega un DAO ni un Service nuevo, así que **`AppContext` y
`UiCoordinator` no se tocan en todo el plan**. Si un paso parece necesitarlo, es señal de que el
diseño se desvió — parar y revisar.

**Tests:** JUnit 5 + Mockito + H2. Patrón del repo para lógica dentro de clases Swing: **extraerla a
una clase plana sin dependencias de Swing y testearla en aislamiento** (`AgrupadorIngresosLote`,
`ReconciliadorPendientes`, `DuplicadoHighlighter`, `SincronizadorVolumenFinal`).

---

## Invariantes (verificar al cerrar CADA paso)

1. `mvn clean package` compila sin errores ni warnings nuevos.
2. `mvn test` en verde — la suite completa (>500 tests), no sólo los tocados.
3. Ningún archivo del alcance supera las **800 líneas**; ninguna función supera las **50**.
4. Sin `System.out.println`, sin `printStackTrace`, sin SQL por concatenación de strings.
5. Los errores se propagan o se loguean con contexto; nunca se tragan en silencio.
6. Cero duplicación nueva de la infra de DnD: si algo se necesita en Ciclos y ya existe en
   `ui/common/dnd`, se usa; si hace falta generalizarlo, se generaliza ahí, no se copia.

---

## Grafo de dependencias

```
S1 (quitar Ingreso #) ─┐
                       ├─→ (independientes entre sí, mismo archivo PantallaCiclos.java)
S2 (grilla 3 por fila)─┘

S3 (ConfiguracionCiclo) ──→ S4 (TipoLavado end-to-end) ──→ S5 (columna en Ver Ciclos)

S6 (extraer StagingCiclo) ──→ S7 (DnD múltiple →cards) ──→ S8 (DnD múltiple cards→)
```

**Orden de ejecución recomendado:** S1 → S2 → S3 → S4 → S5 → S6 → S7 → S8.

**Paralelizables** (si se trabaja en ramas separadas): `{S1, S2}` con `{S3}` con `{S6}`.
S1 y S2 tocan ambos el constructor de `PantallaCiclos` (líneas distintas: S1 la línea 43,
S2 las líneas 53-73) → conflicto trivial si se separan; en una sola rama, secuencial, sin fricción.
S3 y S6 tocan ambos `CiclosController` pero en regiones disjuntas (`ejecutarLanzamiento` vs. el
bloque de staging).

**Modelo sugerido por paso:** default para S1, S2, S5. Modelo fuerte para S3, S4, S6, S7, S8
(refactors con riesgo de regresión y semántica de DnD).

---

# Paso 1 — Quitar las columnas "Ingreso #"

**Depende de:** nada · **Modelo:** default · **Tamaño:** XS

### Contexto

En la pantalla *Ciclos* hay dos tablas que muestran una columna `Ingreso #`:

- La tabla de **elementos disponibles** (`ElementoDisponibleTableModel`), columnas actuales:
  `{"Elemento", "Disponible", "Ingreso #", "Cliente"}` — `Ingreso #` es el índice **2**.
- La tabla **dentro de cada card** (`LavarropasCardTableModel`), columnas actuales:
  `{"Elemento", "Cant.", "Fracción", "Ingreso #", "Cliente"}` — `Ingreso #` es el índice **3**.

`ElementoCicloItem.getIngresoId()` **sigue siendo necesario** en el dominio (viene del DAO y se usa
para ordenar y para la reconciliación); esto es un cambio **exclusivamente de presentación**: se saca
la columna de los dos modelos, no el campo del modelo de dominio.

### Tareas

1. `ElementoDisponibleTableModel`: sacar `"Ingreso #"` del array `columnas`; renumerar el `switch`
   de `getValueAt` (`Cliente` pasa de 3 a 2); corregir `getColumnClass` — hoy devuelve `Integer` para
   las columnas 1 y 2; debe quedar `Integer` sólo para la 1.
2. `LavarropasCardTableModel`: sacar `"Ingreso #"` de `COLUMNAS`; renumerar `getValueAt`
   (`Cliente` pasa de 4 a 3); `getColumnClass` debe devolver `Integer` sólo para la columna 1
   (hoy 1 y 3).
3. `PantallaCiclos:43` — `buildTable(modeloDisponibles, 1, 2)`: las columnas centradas pasan a ser
   sólo `1` (`Disponible`).
4. `LavarropasCard:79` — `TableStyler.centerColumns(tabla, 1, 3)`: hoy centra `Cant.`(1) e
   `Ingreso #`(3); `Fracción`(2) **no** está centrada. La traducción fiel es dejar sólo `1`.
   (Centrar también `Fracción` se ve mejor, pero es un cambio cosmético aparte: si se hace,
   decirlo en el commit, no colarlo como "renumeración".)

### Verificación

```bash
mvn test -Dtest=ElementoCicloItemTest
mvn clean package
```

Grep de control — no debe quedar ninguna referencia a la columna en esta pantalla:

```bash
grep -rn "Ingreso #" src/main/java/com/example/features/lavadero/
```

### Criterio de salida

- Los índices de columna usados en `centerColumns` y en `getColumnClass` son coherentes con los
  arrays de nombres nuevos (fuente típica de `ArrayIndexOutOfBounds` silencioso al renderizar).
- `mvn test` en verde.
- **Smoke manual:** abrir *Lavadero → Ciclos*; la tabla de disponibles muestra 4→3 columnas y las
  cards 5→4, sin columnas vacías ni desplazadas.

---

# Paso 2 — Grilla de cards de a 3 por fila

**Depende de:** nada (recomendado después de S1 por tocar el mismo constructor) · **Modelo:** default · **Tamaño:** S

### Contexto

`PantallaCiclos` construye la grilla de cards con un `GridBagLayout` y **cuatro números mágicos**
en el constructor (líneas 53-73):

```java
gbc.weightx = 0.25;                      // ← 1/4 columnas
for (int i = 1; i <= 13; i++) {          // ← 13 lavarropas hardcodeado
    gbc.gridx = (i - 1) % 4;             // ← 4 columnas
    gbc.gridy = (i - 1) / 4;
    ...
}
// relleno manual de las 3 celdas vacías de la última fila
gbc.gridx = 1; gbc.gridy = 3; panelCards.add(new JPanel(), gbc);
gbc.gridx = 2; gbc.gridy = 3; panelCards.add(new JPanel(), gbc);
gbc.gridx = 3; gbc.gridy = 3; panelCards.add(new JPanel(), gbc);
```

El relleno de la última fila está escrito a mano para el caso exacto 13/4. Con 3 columnas, 13 cards
dan **5 filas** y la última tiene 1 card + 2 rellenos — hay que **calcularlo**, no volver a hardcodearlo.

El `13` también aparece hardcodeado en `CicloLavaderoService:36` (`lavarropasNumero < 1 || > 13`).
Es la misma constante de negocio en dos lugares.

> El comentario existente sobre `fill=HORIZONTAL` + `weighty=0` explica por qué cada card conserva su
> alto preferido y se ancla al tope de su celda. **Ese comportamiento no debe cambiar**; sólo cambia
> el número de columnas.

### Tareas

1. Agregar a `Constantes.Defaults` (o a un bloque `Constantes.Lavadero` nuevo si queda más cohesivo):
   - `CANTIDAD_LAVARROPAS = 13`
   - `LAVARROPAS_POR_FILA = 3`
2. `PantallaCiclos`: reemplazar el bucle y el relleno manual por una construcción parametrizada:
   - `gbc.weightx = 1.0 / LAVARROPAS_POR_FILA;`
   - `gridx = (i - 1) % LAVARROPAS_POR_FILA; gridy = (i - 1) / LAVARROPAS_POR_FILA;`
   - relleno calculado: `int sobrantes = (LAVARROPAS_POR_FILA - CANTIDAD_LAVARROPAS % LAVARROPAS_POR_FILA) % LAVARROPAS_POR_FILA;`
     y un bucle que agregue esos `JPanel` vacíos en la última fila.
   - Si el constructor supera lo razonable, extraer un método privado `construirGrillaDeCards()`.
3. `CicloLavaderoService`: usar `Constantes.Defaults.CANTIDAD_LAVARROPAS` en lugar del `13` literal.

### Verificación

```bash
mvn test -Dtest=CicloLavaderoServiceTest
mvn clean package
```

### Criterio de salida

- No queda ningún `4`, `13` ni `0.25` literal en la construcción de la grilla.
- Cambiar `LAVARROPAS_POR_FILA` a otro valor produce una grilla correcta sin tocar nada más
  (verificable mentalmente: el relleno es una fórmula, no un `if`).
- **Smoke manual:** las 13 cards se ven en 5 filas de 3 (última fila con 1 card alineada a la
  izquierda, sin estirarse). Al colapsar todas, cada fila mide ~28px y no aparece scroll horizontal.

---

# Paso 3 — Refactor: objeto de configuración `ConfiguracionCiclo`

**Depende de:** nada · **Modelo:** fuerte · **Tamaño:** M · **Sin cambio de comportamiento**

### Contexto

`lanzarCiclo` ya arrastra **7 parámetros posicionales** repetidos en tres capas:

```java
// CicloLavaderoService:31 y CicloLavaderoDAO:152
lanzarCiclo(int lavarropasNumero, JabonCatalogo jabon, BigDecimal litrosJabon,
            boolean suavizante, boolean potenciador, BigDecimal litrosTotales,
            List<ElementoCicloMovimiento> movimientos)
```

Los dos `boolean` consecutivos son intercambiables sin que el compilador lo note. El paso 4 agrega
un octavo dato (`TipoLavado`) — hacerlo sobre esta firma empeora el problema. **Este paso prepara
el terreno y no cambia ninguna funcionalidad.**

### Tareas

1. Crear `features/lavadero/model/ConfiguracionCiclo.java` como **`record`** (Java 17, alineado con
   las reglas del repo: records para value types):

   ```java
   public record ConfiguracionCiclo(JabonCatalogo jabon, BigDecimal litrosJabon,
                                    boolean suavizante, boolean potenciador,
                                    BigDecimal litrosTotales) { }
   ```
   `litrosTotales` es nullable (la columna es `NULL`able) — documentarlo en el Javadoc.
   **No** poner validación en el record: la validación vive en el service (patrón del repo).

2. `CicloLavaderoService.lanzarCiclo(int lavarropasNumero, ConfiguracionCiclo config, List<ElementoCicloMovimiento> movimientos)`
   — mismas reglas de `ValidationException.builder()`, leyendo de `config`. Agregar
   `v.addErrorIf(config == null, "Debe configurar el ciclo.")` **antes** de desreferenciarlo, y hacer
   `throwIfHasErrors()` temprano o guardas con cortocircuito para no romper con NPE.

3. `CicloLavaderoDAO.lanzarCiclo(int, ConfiguracionCiclo, List<ElementoCicloMovimiento>)` e
   `insertarCiclo(Connection, int, ConfiguracionCiclo)` — la transacción y el SQL no cambian.

4. `CiclosController.ejecutarLanzamiento`: construir el `ConfiguracionCiclo` a partir de la card y
   pasarlo. La validación local de "mL Jabón vacío" antes de llamar al service se mantiene.

5. **Tests:** adaptar `CicloLavaderoServiceTest` (los ~10 casos de `lanzarCiclo`) y
   `CicloLavaderoDAOTest` (6 llamadas). Introducir un helper privado en los tests
   (`configValida()`, `config(jabon, litros, ...)`) para no repetir el constructor completo en cada
   caso. Los `verify(dao).lanzarCiclo(eq(1), eq(SKIP), any(), eq(false), ...)` pasan a
   `verify(dao).lanzarCiclo(eq(1), eq(configEsperada), anyList())` — el `record` da `equals` gratis.
   ⚠️ **`BigDecimal.equals` es sensible a la escala**: `new BigDecimal("1.5")` ≠ `new BigDecimal("1.50")`.
   El `equals` autogenerado del record los compara con `equals`, no con `compareTo`. El
   `configEsperada` del test tiene que construirse con **el mismo literal** que el del acto, o el
   `verify` falla por un motivo que no tiene nada que ver con lo que se está probando.

### Verificación

```bash
mvn test -Dtest=CicloLavaderoServiceTest
mvn test -Dtest=CicloLavaderoDAOTest
mvn clean package
```

### Criterio de salida

- `lanzarCiclo` tiene 3 parámetros en service y DAO.
- **Ningún test cambió de aserción** — sólo la forma de construir los argumentos. Si un test tuvo que
  aflojar una aserción para pasar, el refactor cambió comportamiento: revertir e investigar.
- La cobertura de `CicloLavaderoService` no baja.

---

# Paso 4 — "Tipo de Lavado" end-to-end (dominio → BD → card)

**Depende de:** S3 · **Modelo:** fuerte · **Tamaño:** L

### Contexto

Nuevo dato por ciclo: **Limpio / Sucio / Podrido**, obligatorio, elegido en un combo dentro de cada
`LavarropasCard`, persistido en `ciclos_lavadero`.

Precedente exacto de enum persistido como texto: [TipoIngresoOtros.java](src/main/java/com/example/features/equipos/otros/model/TipoIngresoOtros.java)
— constante `nombre`, getter, y `desdeBD(String)` tolerante a nulos/desconocidos.

La card ya tiene el patrón para un campo obligatorio que habilita el botón: `txtLitrosJabon` con
un `DocumentListener` → `onLitrosJabonChanged` → `actualizarBtnAccion()`, y
`actualizarBtnAccion()` hace `btnAccion.setEnabled(tieneItems() && getLitrosJabon() != null)`.
El combo de tipo de lavado se engancha al **mismo** mecanismo; renombrar el callback a algo
neutro (`onConfiguracionChanged`) evita tener dos listeners paralelos que hagan lo mismo.

`panelConfig` se oculta cuando la card pasa a `setModoActivo` — el combo va **dentro** de
`panelConfig` para heredar ese comportamiento sin código extra.

### Tareas

1. **Enum** `features/lavadero/model/TipoLavado.java`:
   ```java
   public enum TipoLavado {
       LIMPIO("Limpio"), SUCIO("Sucio"), PODRIDO("Podrido");
       // nombre para UI; name() es lo que se persiste
       public String getNombre();
       public static TipoLavado desdeBD(String valor);   // null/desconocido → SUCIO, con log de warning
       @Override public String toString() { return nombre; }  // para que el JComboBox lo muestre lindo
   }
   ```
   Persistir `name()` (`LIMPIO`/`SUCIO`/`PODRIDO`) y no el nombre de UI: desacopla el texto visible
   del valor almacenado.

2. **Migración** `src/main/resources/db/migration/V15__ciclo_tipo_lavado.sql`:
   ```sql
   -- Tipo de lavado por ciclo. Sin DEFAULT a propósito: no hay filas que backfillear
   -- (las migraciones de lavadero V7-V14 todavía no se aplicaron en producción) y el único
   -- INSERT sobre esta tabla es el del DAO, que siempre setea la columna. Sin DEFAULT, un
   -- INSERT que la olvide falla ruidosamente en vez de inventar un valor.
   ALTER TABLE ciclos_lavadero ADD COLUMN tipo_lavado VARCHAR(20) NOT NULL;
   ```
   Una sola sentencia, sin `AFTER` (compatibilidad H2 + MySQL).
   **No editar `V10` para meter la columna ahí**, por más que no esté aplicada en producción:
   sí está aplicada en la BD de desarrollo y en cualquier entorno local, y cambiar una migración
   ya corrida rompe la validación de checksum de Flyway al arrancar.

3. **Modelo** `CicloLavadero`: campo `final TipoLavado tipoLavado` + getter. Ajustar **ambos**
   constructores (el corto delega en el largo).

4. **`ConfiguracionCiclo`**: agregar el componente `TipoLavado tipoLavado`.

5. **DAO** `CicloLavaderoDAO`:
   - `SQL_INSERTAR_CICLO`: agregar la columna y su `?`.
   - `insertarCiclo`: `ps.setString(n, config.tipoLavado().name())`.
   - `SQL_ACTIVOS`, `SQL_FINALIZADOS`, `SQL_TODOS`: agregar `cl.tipo_lavado` al `SELECT`.
   - `mapearCiclo` y `mapearCicloCompleto`: `TipoLavado.desdeBD(rs.getString("tipo_lavado"))`.

6. **Service** `CicloLavaderoService.lanzarCiclo`: `v.addErrorIf(config.tipoLavado() == null, "Debe seleccionar el tipo de lavado.")`.

7. **Vista** `LavarropasCard`:
   - `JComboBox<TipoLavado> cmbTipoLavado`, poblado con `TipoLavado.values()` y
     **`setSelectedItem(null)`** para que arranque vacío (obligatorio sin default).
   - Agregarlo a `panelConfig` vía `rowPanel("Tipo:", cmbTipoLavado)`, arriba de `Jabón:`.
   - Getter `getTipoLavado()` que devuelve `(TipoLavado) cmbTipoLavado.getSelectedItem()` (puede ser null).
   - `ActionListener` en el combo → dispara el mismo callback que `txtLitrosJabon`.
   - `actualizarBtnAccion()`: `btnAccion.setEnabled(tieneItems() && getLitrosJabon() != null && getTipoLavado() != null)`.

8. **Controller** `CiclosController.ejecutarLanzamiento`: incluir `card.getTipoLavado()` en el
   `ConfiguracionCiclo`, y agregar el mensaje de error específico si viniera null (paridad con el
   chequeo de `litrosJabon`).

9. **Tests:**
   - `TipoLavadoTest` (nuevo): `desdeBD` con valor válido, con minúsculas, con null, con desconocido.
   - `CicloLavaderoServiceTest`: caso `lanzarCiclo_tipoLavadoNull_lanzaValidation`.
   - `CicloLavaderoDAOTest`: round-trip — el tipo elegido se persiste y se relee igual, en los tres
     caminos de lectura (`obtenerCiclosActivosPorLavarropas`, `obtenerCiclosFinalizados`,
     `obtenerTodosLosCiclos`). Los tres tienen su propio `SELECT` y su propio mapper: es exactamente
     donde se olvida una de las tres.
   - `CicloLavaderoTest` y `CicloFilterStrategyTest`: adaptar los constructores.

### Verificación

```bash
mvn test -Dtest=TipoLavadoTest
mvn test -Dtest=CicloLavaderoDAOTest
mvn test -Dtest=CicloLavaderoServiceTest
mvn test -Dtest=CicloLavaderoTest
mvn test          # suite completa: la migración corre sobre H2 en cada test de DAO
mvn clean package
```

### Criterio de salida

- `mvn test` en verde — si Flyway falla, el error aparece en **todos** los tests de DAO a la vez:
  es la señal de que el DDL no es compatible con H2.
- El combo arranca vacío y `Lanzar` está deshabilitado hasta elegir tipo **y** cargar mL de jabón.
- Al pasar la card a modo activo (`setModoActivo`), el combo se oculta junto con el resto de la config.
- **Smoke manual sobre la BD de desarrollo:** lanzar un ciclo con cada uno de los 3 tipos y
  verificar los valores en `ciclos_lavadero.tipo_lavado` (deben ser `LIMPIO`/`SUCIO`/`PODRIDO`,
  el `name()`, no el nombre de UI). No hay nada que verificar sobre ciclos previos: no existen.

---

# Paso 5 — Columna "Tipo de Lavado" en Ver Ciclos

**Depende de:** S4 · **Modelo:** default · **Tamaño:** XS

### Contexto

`PantallaVerCiclos` usa un `DefaultTableModel` con 10 columnas:
`{"ID", "Lavarropas", "Jabón", "mL Jabón", "Suavizante", "Potenciador", "L Totales", "Inicio", "Fin", "Estado"}`.

Hay **dos índices acoplados a ese orden** que se rompen al insertar una columna:

- `TableStyler.centerColumns(tablaCiclos, 0, 1, 3, 4, 5, 6)` (línea 60)
- `tablaCiclos.getColumnModel().getColumn(9).setCellRenderer(new CicloEstadoCellRenderer())` (línea 61)
  — el `9` es `Estado`.

### Tareas

1. Insertar `"Tipo de Lavado"` en el array de columnas — sugerido **después de `"Lavarropas"`**
   (índice 2), donde queda junto a los datos de configuración del ciclo.
2. En `actualizarCiclos`, agregar `c.getTipoLavado().getNombre()` en la posición correspondiente.
3. Corregir los dos índices acoplados. Con `Tipo de Lavado` en el índice 2, el orden queda
   `0 ID · 1 Lavarropas · 2 Tipo de Lavado · 3 Jabón · 4 mL Jabón · 5 Suavizante · 6 Potenciador ·
   7 L Totales · 8 Inicio · 9 Fin · 10 Estado`, de modo que:
   - `centerColumns(tablaCiclos, 0, 1, 3, 4, 5, 6)` → **`0, 1, 2, 4, 5, 6, 7`**
     (las mismas de antes con los índices corridos, más la nueva columna centrada).
   - `getColumn(9)` del `CicloEstadoCellRenderer` → **`getColumn(10)`**.
   En vez de dejar otro literal suelto, definir `private static final int COL_ESTADO = 10;`
   (o derivarlo con un `indexOf` sobre el array de nombres) para que el próximo cambio de columnas
   no vuelva a romperse en silencio.

### Verificación

```bash
mvn clean package
mvn test
```

### Criterio de salida

- **Smoke manual:** *Lavadero → Ver Ciclos* muestra la columna con el nombre legible
  (`Limpio`/`Sucio`/`Podrido`, no `LIMPIO`), el color-coding de `Estado` sigue aplicándose a la
  columna correcta, y las columnas centradas son las mismas de antes.
- Los filtros (lavarropas, estado, fechas) siguen funcionando — no se tocan.

---

# Paso 6 — Extraer el staging de pendientes a una clase plana testeable

**Depende de:** nada (recomendado después de S4 por tocar `CiclosController`) · **Modelo:** fuerte · **Tamaño:** M · **Sin cambio de comportamiento**

### Contexto

`CiclosController` tiene **488 líneas** y mezcla cuatro responsabilidades: cableado de eventos Swing,
handlers de DnD, diálogos, y **la aritmética del staging**. Los pasos 7 y 8 agregan más DnD y más
aritmética (la baja con la regla de fracciones). Sin extraer primero, el controller se vuelve
intestable e ilegible.

La aritmética a extraer está en estos cuatro métodos, ninguno de los cuales toca Swing:

- `computarFracciones()` (líneas 171-181) — cuenta fracciones por `instanciaId`
- `aplicarPendientesEnDisponibles(List)` (183-219) — descuenta lo staged de la lista de disponibles
- `agregarPendiente(int, ElementoCicloItem, int)` (361-382) — alta de un regular (acumula si ya existe)
- `agregarPendienteEquipo(int, ElementoCicloItem)` (355-359) — alta de una fracción de equipo

Su único estado es `Map<Integer, List<ElementoCicloItem>> pendientesPorLavarropas`.

**Precedente directo en el repo:** `features/lotes/controller/helpers/ReconciliadorPendientes.java`
+ `EstadoStaging.java` hacen exactamente esto para Gestionar Lotes. Leerlos antes de diseñar la API
— la meta es que un lector que conoce Lotes reconozca el patrón, no inventar uno nuevo.

> **Ojo con la mutabilidad:** `aplicarPendientesEnDisponibles` **muta** los `ElementoCicloItem` que
> recibe (`d.setCantidadEnCiclo(staged)`) y devuelve una lista filtrada. Ese comportamiento
> es del que dependen la tabla de disponibles y `procesarDropRegular` (`getCantidadDisponible() -
> getCantidadEnCiclo()`). **No "arreglar" la mutabilidad en este paso** — es un refactor de
> ubicación, no de semántica. Si se quiere hacer inmutable, es otro plan.

### Tareas

1. Crear `features/lavadero/controller/helpers/StagingCiclos.java` — clase plana, **sin ningún import
   de `javax.swing`**, dueña del `Map<Integer, List<ElementoCicloItem>>`. API mínima:
   - `void agregarRegular(int lavarropas, ElementoCicloItem origen, int cantidad)`
   - `void agregarFraccionEquipo(int lavarropas, ElementoCicloItem fraccion)`
   - `List<ElementoCicloItem> pendientesDe(int lavarropas)` (lista no modificable o copia)
   - `Map<Integer,Integer> fraccionesPorInstancia()`
   - `List<ElementoCicloItem> aplicarSobreDisponibles(List<ElementoCicloItem> dbDisponibles)`
   - `boolean hayPendientes()` / `void limpiar()` / `void limpiarLavarropas(int)`
2. Mover los cuatro métodos tal cual (misma lógica, mismos nombres de variables donde ayude a
   revisar el diff) y dejar `CiclosController` delegando.
3. Borrar de `CiclosController` el campo `pendientesPorLavarropas` y todos sus accesos directos.
4. **Tests nuevos** `StagingCiclosTest` — sin Swing, cubriendo:
   - alta de un regular nuevo / alta acumulativa sobre uno existente del mismo `elementoClasificacionId`
   - que un equipo (`isEquipo()`) nunca se acumula: cada fracción es una fila propia
   - `fraccionesPorInstancia` con un equipo repartido en 3 lavarropas → `{instanciaId: 3}`
   - `aplicarSobreDisponibles`: descuento parcial (el ítem queda con `cantidadEnCiclo` seteado) y
     descuento total (el ítem desaparece de la lista)
   - `aplicarSobreDisponibles` con un `elementoClasificacionId` staged que ya no está en la BD
     (no debe explotar)

### Verificación

```bash
mvn test -Dtest=StagingCiclosTest
mvn test
mvn clean package
```

### Criterio de salida

- `CiclosController` bajó de ~488 líneas y ya no contiene aritmética de cantidades.
- `StagingCiclosTest` cubre los 5 escenarios de arriba y pasa.
- **Smoke manual de no-regresión:** arrastrar un regular parcial, arrastrar un equipo y subdividirlo
  en 3 lavarropas, verificar que la columna `Fracción` muestra `1/3` en las tres cards y que la
  tabla de disponibles descuenta bien. Descartar todo y verificar que vuelve al estado inicial.

---

# Paso 7 — DnD múltiple: tabla de disponibles → cards

**Depende de:** S6 · **Modelo:** fuerte · **Tamaño:** M

### Contexto

Hoy el DnD de Ciclos es **de a una fila**:

- `PantallaCiclos:97` y `LavarropasCard:77` — `setSelectionMode(SINGLE_SELECTION)`.
- `CiclosController.ELEMENTO_CICLO_FLAVOR` transporta **un** `ElementoCicloItem`.
- `DisponiblesTransferHandler` (232-243) y `CicloTransferHandler` (247-282) están escritos a mano y
  duplican lo que `MultiRowTableTransferHandler` ya resuelve de forma genérica.
- `ElementoCicloTransferable` (`controller/helpers/`) es una copia de `MultiRowTransferable` para un
  solo ítem — queda **muerta** después de este paso y hay que **borrarla** (su única referencia es
  `CiclosController:239`).

Referencia canónica de cómo se cablea: `LotesController.configurarDnD()` +
`crearHandlerDisponibles()` / `crearHandlerAutoclave()` (líneas 315-364).

**Sobre el `DataFlavor`:** Lotes usa `javaJVMLocalObjectMimeType;class="java.util.List"`. Ciclos hoy
usa `class="…ElementoCicloItem"`. Si Ciclos pasa a transportar una `List`, lo honesto es declarar
`java.util.List` — pero entonces **ambos flavors son iguales** (`DataFlavor.equals` compara la
representation class, no el nombre presentable). En la práctica no colisionan: `PantallaCiclos` y
`PanelLotesContenido` viven en tarjetas distintas del `CardLayout` y nunca están visibles a la vez,
así que un arrastre no puede empezar en una y terminar en la otra. **Decisión: declarar
`java.util.List`, igual que Lotes, y dejar la razón escrita en un comentario** — no inventar un
flavor "falso" con `class=ElementoCicloItem` transportando una lista.

Esos dos bloques `static { … }` idénticos en `LotesController` y `CiclosController` son duplicación
real: extraerlos a un factory de una línea en `ui/common/dnd` (p. ej.
`LocalObjectFlavors.forList()`), que loguee y devuelva `null` ante el `ClassNotFoundException`
imposible, y usarlo desde ambos controllers.

**Semántica del drop múltiple** (paridad con `LotesController.agregarMateriales`): por cada ítem
soltado se abre su diálogo **en secuencia**; cancelar uno **saltea sólo ese ítem** y sigue con el
resto; se refresca la vista **una sola vez** al final. En Ciclos hay dos diálogos según el tipo:
`seleccionarSubcantidad` (spinner) para regulares y `EquipoSubdivisionDialog` para equipos —
`procesarDropRegular` / `procesarDropEquipo` ya encapsulan cada caso.

### Tareas

1. `ui/common/dnd/LocalObjectFlavors.java` (nuevo): factory del `DataFlavor` de lista local.
   Migrar el bloque `static` de `LotesController` **y** el de `CiclosController` a usarlo.
2. `PantallaCiclos`: `TableSelectionSupport.enableMultiSelection(tablaDisponibles)` (reemplaza el
   `SINGLE_SELECTION` de `buildTable`) y nuevo método
   `getElementosDisponiblesSeleccionados()` → `TableSelectionSupport.selectedItems(tablaDisponibles, modeloDisponibles::getItemAt)`.
   Mantener `getElementoDisponibleSeleccionado()` sólo si queda algún consumidor; si no, borrarlo.
3. `LavarropasCard`: `TableSelectionSupport.enableMultiSelection(tabla)` y
   `getItemsSeleccionados()` → `selectedItems(tabla, tableModel::getItemAt)`
   (nombres en paridad con `PanelLotesContenido.getMateriales*Seleccionados()`).
4. `CiclosController`:
   - Borrar las clases internas `DisponiblesTransferHandler` y `CicloTransferHandler`.
   - `crearHandlerDisponibles()`: `Builder<ElementoCicloItem>` con `sourceActions(COPY)` y
     `selectionSupplier(pantalla::getElementosDisponiblesSeleccionados)`. Por ahora no importa nada
     (`canImportExtra` → `false`); el import se agrega en S8.
   - `crearHandlerCard(int num)`: `sourceActions(NONE)` por ahora,
     `canImportExtra(support -> !ciclosActivos.containsKey(num))`,
     `onImport(items -> SwingUtilities.invokeLater(() -> procesarDrop(items, num)))`.
   - Nuevo `procesarDrop(List<ElementoCicloItem> items, int num)`: recorre los ítems, delega en
     `procesarDropRegular` / `procesarDropEquipo` **sin** que estos refresquen, y llama a
     `refrescarDisponiblesYCards()` **una vez** al final.
     ⚠️ Hoy `procesarDropRegular` y `procesarDropEquipo` terminan cada uno con
     `refrescarDisponiblesYCards()` — hay que sacarlo de ahí o se refresca N veces (y peor: el
     refresco recalcula `cantidadEnCiclo` sobre ítems que el bucle todavía está usando).
   - ⚠️ **`cantidadDisponible` dentro del bucle:** `procesarDropRegular` calcula
     `max = item.getCantidadDisponible() - item.getCantidadEnCiclo()` sobre el ítem **arrastrado**.
     Si el usuario selecciona dos filas del mismo `elementoClasificacionId` (no debería poder, son
     filas distintas del modelo) o si el mismo ítem se soltara dos veces, el cálculo se desactualiza.
     Verificar contra el staging (`StagingCiclos`), no contra el objeto arrastrado.
5. `configurarDnD()`: cablear el handler de disponibles y, en `inicializarEventos()`, el de cada card.
   Mantener el `ComponentAdapter` con el flag `dndConfigurado` tal como está (resuelve un problema
   real de orden de inicialización).
6. **Borrar** `controller/helpers/ElementoCicloTransferable.java`.

### Verificación

```bash
mvn test -Dtest=MultiRowTransferableTest
mvn test -Dtest=TableSelectionSupportTest
mvn test -Dtest=StagingCiclosTest
mvn test
mvn clean package
```

Confirmar que la clase muerta no dejó referencias:

```bash
grep -rn "ElementoCicloTransferable" src/
```

### Criterio de salida

- **Smoke manual:**
  1. Ctrl+click sobre 3 elementos regulares → arrastrarlos a una card → aparecen 3 diálogos de
     cantidad en secuencia; cancelar el segundo deja los otros dos cargados.
  2. Seleccionar 1 regular + 1 equipo → el equipo abre `EquipoSubdivisionDialog` y el regular el spinner.
  3. Arrastrar a una card con ciclo activo (`[OCUPADO]`) → el drop se rechaza (no hay feedback de drop).
  4. Arrastrar sin selección → no pasa nada, sin excepción en el log.
  5. La tabla de disponibles queda descontada correctamente después de la tanda (una sola actualización).
- No hay ninguna clase `TransferHandler` escrita a mano en `features/lavadero/`.

---

# Paso 8 — DnD múltiple inverso: cards → tabla de disponibles

**Depende de:** S7 · **Modelo:** fuerte · **Tamaño:** M

### Contexto

Hoy **no existe** forma de sacar elementos de una card por drag: `CicloTransferHandler.getSourceActions`
devolvía `NONE` y `DisponiblesTransferHandler.canImport` devolvía `false`. La única salida es
"Descartar todos", que vacía **todo** el staging. Este paso agrega la devolución selectiva.

Paridad con Lotes (`crearHandlerAutoclave` / `quitarMaterialesDePendientes`, líneas 344-429):

- El origen (la card) usa `sourceActions(MOVE)`.
- Un flag de instancia `arrastrandoDesdeCard` evita que un arrastre iniciado en una card sea
  aceptado como drop por otra card. `onExportDone` lo resetea **incondicionalmente** (también cuando
  `action == NONE`, es decir cuando el arrastre se aborta) — si se resetea sólo en `MOVE`, el flag
  queda pegado y bloquea todos los drops siguientes.
- La devolución (`onImport` de la tabla de disponibles) refresca la vista una sola vez.

**Regla de negocio decidida — equipos subdivididos:** devolver **una** fracción quita **todas** las
fracciones con el mismo `instanciaId`, en **todas** las cards. Motivo: un equipo repartido en 3
lavarropas que queda en 2 mostraría `1/3` en dos cards (dato falso) o exigiría renumerar fracciones
en cascada. Deshacer la subdivisión entera es la única semántica sin estados inconsistentes.
**La UI debe avisarlo**: si la selección incluye una fracción de un equipo repartido en más de un
lavarropas, pedir confirmación (`"Este equipo está repartido en N lavarropas. Se quitará de todos."`)
antes de aplicar.

**Cards con ciclo activo:** sus ítems vienen de la BD (`obtenerElementosDeCiclo`), no del staging.
**No se pueden devolver.** El `selectionSupplier` de una card activa debe devolver lista vacía —
si no, el arrastre "funciona" visualmente y no quita nada, que es peor que no permitirlo.

### Tareas

1. `StagingCiclos` (creado en S6) — agregar la aritmética de baja, **sin Swing**:
   - `void quitarRegular(int lavarropas, ElementoCicloItem item, int cantidad)` — resta y elimina la
     fila si queda en 0.
   - `void quitarInstanciaEquipo(Integer instanciaId)` — recorre **todos** los lavarropas y elimina
     toda fracción con ese `instanciaId`.
   - `void quitar(Collection<ElementoCicloItem> seleccionados, int lavarropasOrigen)` — orquesta:
     para cada ítem, si `isEquipo() && instanciaId != null` → `quitarInstanciaEquipo`, si no →
     `quitarRegular` por su `cantidadEnCiclo` completa.
   - `int lavarropasDeInstancia(Integer instanciaId)` — cuántas cards contienen esa instancia
     (lo necesita el diálogo de confirmación; reutiliza el conteo de `fraccionesPorInstancia`).
2. **Tests** en `StagingCiclosTest`:
   - quitar un regular completo → desaparece de los pendientes
   - quitar una fracción de un equipo repartido en 3 → las 3 desaparecen, de las 3 cards
   - quitar un ítem que ya no está → no-op, sin excepción
   - `hayPendientes()` vuelve a `false` después de quitar todo
   - después de una baja, `aplicarSobreDisponibles` vuelve a mostrar el ítem con su cantidad completa
3. `CiclosController`:
   - **Un solo campo de estado del arrastre**, no dos: `private Integer lavarropasArrastre = null;`
     — guarda de qué card salió el arrastre en curso; `null` significa "no se está arrastrando desde
     una card". Cubre las dos necesidades a la vez (el flag anti-rebote **y** el número de origen que
     `StagingCiclos.quitar(seleccionados, lavarropasOrigen)` necesita) sin poder desincronizarse.
     Es la diferencia con Lotes, donde un `boolean` alcanza porque hay un solo autoclave seleccionado.
   - `crearHandlerCard(int num)`: pasar a `sourceActions(MOVE)`;
     `selectionSupplier(() -> seleccionCardParaArrastre(num))` — devuelve lista vacía si
     `ciclosActivos.containsKey(num)`, y si no, setea `lavarropasArrastre = num` y devuelve
     `card.getItemsSeleccionados()`;
     ampliar `canImportExtra` con `&& lavarropasArrastre == null`;
     `onExportDone(action -> lavarropasArrastre = null)` — **incondicional**, sin mirar el `action`.
   - `crearHandlerDisponibles()`: `canImportExtra(support -> lavarropasArrastre != null)` y
     `onImport(items -> { int origen = lavarropasArrastre; SwingUtilities.invokeLater(() -> devolverADisponibles(items, origen)); })`.
     ⚠️ Capturar `lavarropasArrastre` en una variable local **antes** del `invokeLater`: `exportDone`
     corre antes de que se ejecute el runnable diferido, así que leerlo adentro da siempre `null`.
   - `devolverADisponibles(List<ElementoCicloItem>, int lavarropasOrigen)`: pedir confirmación si hay
     equipos repartidos, delegar en `StagingCiclos.quitar(...)`, y `refrescarDisponiblesYCards()`
     una sola vez.
4. Botón/menú opcional de paridad: no se agrega nada nuevo — el DnD y "Descartar todos" alcanzan.

### Verificación

```bash
mvn test -Dtest=StagingCiclosTest
mvn test
mvn clean package
```

### Criterio de salida

- **Smoke manual:**
  1. Cargar 3 regulares en una card, seleccionar 2, arrastrarlos a la tabla de disponibles → vuelven
     los 2 con su cantidad completa; el tercero queda en la card.
  2. Subdividir un equipo en 3 lavarropas, arrastrar una fracción a disponibles → aparece la
     confirmación mencionando los 3 lavarropas; al aceptar, las 3 fracciones desaparecen y el equipo
     reaparece entero en disponibles.
  3. Cancelar esa confirmación → no cambia nada y **los drops siguientes siguen funcionando**
     (el flag se reseteó).
  4. Intentar arrastrar desde una card `[OCUPADO]` → no arranca el arrastre.
  5. Arrastrar desde una card a **otra card** → rechazado.
  6. Abortar un arrastre soltando en el vacío → los drops siguientes siguen funcionando.
- `mvn test` en verde y `CiclosController` sigue bajo las 800 líneas.

---

## Cierre del plan

Cuando los 8 pasos estén hechos:

```bash
mvn clean verify        # tests + reporte de cobertura JaCoCo
```

- Verificar que la cobertura de `features/lavadero` **no bajó** respecto del baseline previo al plan.
- Smoke manual completo del flujo: *Lavadero → Ciclos* → cargar elementos por DnD múltiple →
  elegir tipo de lavado y jabón → Lanzar → *Ver Ciclos* muestra el ciclo con su tipo → Finalizar.
- Actualizar `CLAUDE.md` si la sección de Lavadero necesita mencionar `TipoLavado` o `StagingCiclos`.
- Actualizar la memoria del proyecto (`project-lavadero-*`) con el estado final.

## Protocolo de mutación del plan

Si durante la ejecución un paso resulta más grande de lo previsto o aparece un bloqueo:

- **Dividir** un paso: numerarlo `Sn.a` / `Sn.b` y anotar acá el motivo. No agrandar el alcance de un paso.
- **Saltear** un paso: sólo si sus dependientes no lo necesitan. Anotar acá qué queda sin hacer.
- **Reordenar**: permitido dentro de las restricciones del grafo de dependencias, nunca en contra.
- Cualquier cambio de una de las 4 decisiones de arriba requiere confirmación del usuario, no
  criterio del agente que ejecuta.
