# Blueprint — Multi-selección en el drag-and-drop de materiales (Gestionar Lotes)

> Plan de construcción autocontenido. Cada paso incluye su propio *context brief*
> para que un agente pueda ejecutarlo en frío, sin haber leído los pasos previos.

## Objetivo

Permitir **seleccionar y arrastrar varias filas a la vez** en el drag-and-drop de
materiales de la pantalla *Gestionar Lotes*, en **ambas direcciones** (Disponibles →
Autoclave y Autoclave → Disponibles) y en el botón *Quitar*. La implementación debe
quedar **integrada** (sin parches), con el mecanismo multi-fila **extraído a un
componente genérico reutilizable** (aunque hoy solo lo consuma Lotes).

## Decisiones de diseño (confirmadas con el usuario)

1. **Diálogo de cantidad:** al arrastrar N materiales hacia el autoclave se abre
   **un diálogo por material, secuencialmente** (spinner + checkbox "Todos", igual
   que hoy). Cancelar un diálogo **saltea solo ese material** y continúa con el resto.
2. **Abstracción:** **genérico reutilizable, cableado solo en Lotes.** Se crea un
   paquete `com.example.ui.common.dnd` con un `Transferable` y un `TransferHandler`
   parametrizados por tipo. Sin políticas de drop enchufables (eso sería sobre-diseño
   para un único consumidor — YAGNI).
3. **Consistencia:** **todo multi-selección.** Ambas tablas pasan a
   `MULTIPLE_INTERVAL_SELECTION`; el arrastre inverso y el botón *Quitar* operan
   sobre todas las filas seleccionadas.

## Contexto arquitectónico común (leer una vez)

- App de escritorio **Swing / Java 11**, sin DI (cableado manual en `AppContext`).
- **Lotes es el único feature que usa DnD de Swing en toda la app** (verificado por
  grep de `TransferHandler`/`Transferable`).
- Archivos involucrados hoy:
  - `src/main/java/com/example/features/lotes/controller/LotesController.java`
    — contiene `DisponiblesTransferHandler`, `AutoclaveTransferHandler`, el
    `DataFlavor` `MATERIAL_LOTE_FLAVOR`, y la lógica de negocio
    (`agregarMaterial`, `quitarMaterialDePendientes`, `quitarMaterial`, etc.).
  - `src/main/java/com/example/features/lotes/controller/helpers/MaterialLoteTransferable.java`
    — `Transferable` que hoy lleva **un** `MaterialLoteItem`. **Se elimina** al
    migrar al genérico.
  - `src/main/java/com/example/features/lotes/view/helpers/PanelLotesContenido.java`
    — construye las dos `JTable` (`tablaDisponibles`, `tablaAutoclave`), hoy en
    `SINGLE_SELECTION`; expone `getMaterialDisponibleSeleccionado()` /
    `getMaterialAutoclaveSeleccionado()` (una fila).
  - `src/main/java/com/example/features/lotes/view/helpers/MaterialLoteTableModel.java`
    — modelo con `getItemAt(row)`.
  - `src/main/java/com/example/features/lotes/view/helpers/MaterialLoteItem.java`
    — `Serializable`, mutable en `cantidad`.
  - `src/main/java/com/example/ui/dialogs/CantidadDialogHelper.java`
    — `pedirCantidad(parent, descripcion, max, configurarTodos)`; devuelve
    `Integer` (null = cancelado). Si `max <= 1` devuelve el máximo sin diálogo.
  - `src/main/java/com/example/ui/common/TableStyler.java` — `applyStandard` NO
    instala `RowSorter` (índice vista == índice modelo hoy; convertir igual por
    robustez).
- **Estilo del repo:** inmutabilidad preferida, archivos < 800 líneas, funciones
  < 50 líneas, sin nesting profundo. Lógica de negocio embebida en Swing se
  **extrae a clases planas** y se testea en aislamiento (ver `AgrupadorIngresosLote`,
  `SincronizadorVolumenFinal`). Tests: JUnit 5 + Mockito + H2.
- **Git:** rama actual `MultiSelectYCursorInfo` (árbol limpio). Modo directo:
  cada paso es un commit en esta rama. Verificación por paso: `mvn -q -DskipTests
  compile` (rápida) y, donde aplique, `mvn -q test`. Al final `mvn clean package`.

## Invariantes (verificar tras CADA paso)

- `mvn -q -DskipTests compile` pasa (compila).
- La suite existente sigue verde: `mvn -q test` (>500 tests). Ningún test roto por
  cambio de firma. Si un test referencia una firma modificada, **actualizar el test**
  al nuevo contrato (no borrar cobertura).
- No queda código muerto: si `MaterialLoteTransferable` o un getter de una sola fila
  dejan de usarse, se eliminan en el mismo paso que los deja huérfanos.
- El comportamiento de arrastre **simple** (una fila) se mantiene idéntico al actual.

## Grafo de dependencias

```
Paso 1 (infra genérica)  ┐
                          ├──► Paso 3 (cableado en LotesController) ──► Paso 4 (verificación)
Paso 2 (API del panel)   ┘
```

- **Paso 1 ∥ Paso 2** son **paralelos** (archivos disjuntos: `ui/common/dnd/*`
  nuevo vs. `PanelLotesContenido.java`).
- **Paso 3** depende de 1 **y** 2.
- **Paso 4** depende de 3.

| Paso | Descripción | Tier modelo | Paralelo con |
|------|-------------|-------------|--------------|
| 1 | Infra DnD multi-fila genérica + tests | **strongest** (diseño de API genérica) | Paso 2 |
| 2 | API multi-selección en el panel | default | Paso 1 |
| 3 | Cablear handlers genéricos, carga/quita múltiple, Quitar múltiple | **strongest** (integración, preservar comportamiento) | — |
| 4 | Verificación end-to-end (build + tests + corrida manual) | default | — |

---

## Paso 1 — Infra DnD multi-fila genérica y reutilizable

### Context brief
Crear un mecanismo genérico de arrastre de **varias filas** de una `JTable`,
reutilizable por cualquier feature, sin dependencias de Lotes. Vive en el paquete
UI común `com.example.ui.common.dnd` (junto a `Estilos`, `TableStyler`,
`LabelFactory`). Es código **nuevo**, no toca nada existente. Referencia del
patrón actual a generalizar: `MaterialLoteTransferable` (lleva 1 ítem) y los dos
`TransferHandler` internos de `LotesController` (uno COPY origen + destino, otro
MOVE origen + destino).

### Tareas
1. Crear `src/main/java/com/example/ui/common/dnd/MultiRowTransferable.java`:
   - `public final class MultiRowTransferable<T> implements Transferable`.
   - Campos `final`: `List<T> items` (copia defensiva inmutable con
     `List.copyOf`) y `DataFlavor flavor`.
   - `getTransferData` devuelve la lista; `isDataFlavorSupported` compara contra
     `flavor`; `getTransferDataFlavors` devuelve `new DataFlavor[]{flavor}`.
   - Lanzar `UnsupportedFlavorException` si el flavor no coincide (igual que hoy).
2. Crear `src/main/java/com/example/ui/common/dnd/TableSelectionSupport.java`
   (clase de utilidades estáticas, no instanciable):
   - `static <T> List<T> selectedItems(JTable table, IntFunction<T> itemAtModelRow)`:
     recorre `table.getSelectedRows()`, convierte cada índice con
     `table.convertRowIndexToModel(viewRow)`, mapea con `itemAtModelRow`, filtra
     nulls, devuelve lista en orden de vista. **Testeable sin display.**
   - `static void enableMultiSelection(JTable table)`: setea
     `MULTIPLE_INTERVAL_SELECTION`. (Conveniencia; el panel puede llamarlo o
     hacerlo inline — decidir en Paso 2.)
3. Crear `src/main/java/com/example/ui/common/dnd/MultiRowTableTransferHandler.java`:
   - `public class MultiRowTableTransferHandler<T> extends TransferHandler`.
   - Construcción por **inyección funcional** (sin subclasear por uso). Usar un
     `Builder` estático (patrón del repo) con:
     - `DataFlavor flavor` (requerido)
     - `int sourceActions` (default `COPY`)
     - `Supplier<List<T>> selectionSupplier` (qué filas se arrastran)
     - `Predicate<TransferHandler.TransferSupport> canImportExtra` (default `s -> true`;
       chequeos adicionales del destino, p.ej. "hay autoclave y no está ocupado")
     - `Consumer<List<T>> onImport` (qué hacer con las filas soltadas)
     - `IntConsumer onExportDone` (opcional; se invoca **siempre** en `exportDone`
       recibiendo el `action`, incluso si el drag se aborta con `action == NONE`).
       **Motivo:** cualquier flag/estado que el consumidor active en
       `selectionSupplier` (p.ej. "arrastrando desde sí mismo") debe poder
       resetearse aunque el drop no se concrete — un hook solo-MOVE lo dejaría
       atascado. El consumidor decide dentro del callback qué hacer según el
       `action` (resetear siempre, refrescar solo si `MOVE`).
   - `getSourceActions` → `sourceActions`.
   - `createTransferable`: `List<T> sel = selectionSupplier.get();` si vacía →
     `null`; si no → `new MultiRowTransferable<>(sel, flavor)`.
   - `canImport`: `support.isDrop() && support.isDataFlavorSupported(flavor) &&
     canImportExtra.test(support)`; setear `setShowDropLocation` acorde.
   - `importData`: si `!canImport` → false; extraer
     `(List<T>) support.getTransferable().getTransferData(flavor)`; invocar
     `onImport.accept(items)`; devolver true. Capturar excepciones, loguear con
     SLF4J y devolver false (igual que hoy).
   - `exportDone`: si `onExportDone != null` → `onExportDone.accept(action)`
     (siempre, sin condicionar a MOVE).
   - **Manejo de "drag desde sí mismo":** el handler no debe conocer esa regla;
     se implementa vía `canImportExtra` en el consumidor (Paso 3) o vía un flag
     que el consumidor controle. Documentar en Javadoc que las reglas de negocio
     van en los callbacks.
4. Tests en `src/test/java/com/example/ui/common/dnd/`:
   - `MultiRowTransferableTest`: soporta el flavor propio; rechaza otro flavor
     (lanza `UnsupportedFlavorException`); `getTransferData` devuelve exactamente
     los ítems; la lista es inmutable (copia defensiva — mutar el origen no la
     cambia).
   - `TableSelectionSupportTest`: con un `JTable` + `DefaultTableModel` **no
     mostrado**, seleccionar filas 0 y 2 y verificar que `selectedItems` devuelve
     los ítems esperados en orden; selección vacía → lista vacía; mapper que
     devuelve null → se filtra.

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test -Dtest=MultiRowTransferableTest,TableSelectionSupportTest`

### Criterio de salida
Paquete `ui/common/dnd` compila y sus tests pasan. Ninguna clase existente
modificada. Commit: `feat: infra genérica de drag-and-drop multi-fila reutilizable`.

### Rollback
Borrar el paquete `com.example.ui.common.dnd` (main + test). No hay otros efectos.

---

## Paso 2 — API multi-selección en `PanelLotesContenido`

### Context brief
El panel `PanelLotesContenido` construye las dos tablas de materiales
(`tablaDisponibles`, `tablaAutoclave`) y hoy las fija en
`ListSelectionModel.SINGLE_SELECTION` (líneas ~74 y ~93). Expone getters de **una
sola fila**: `getMaterialDisponibleSeleccionado()` y
`getMaterialAutoclaveSeleccionado()` (leen `getSelectedRow()`). Este paso habilita
multi-selección y agrega getters de **lista**. No toca el controller todavía; por
eso **se conservan** los getters de una fila hasta el Paso 3 (que los elimina al
dejarlos huérfanos). Archivo único: `PanelLotesContenido.java`.

### Tareas
1. Cambiar ambas tablas de materiales a
   `ListSelectionModel.MULTIPLE_INTERVAL_SELECTION` (reemplazar las dos líneas
   `setSelectionMode(... SINGLE_SELECTION)` de `tablaDisponibles` y
   `tablaAutoclave`). **No** cambiar `tablaAutoclaves` (la de equipos), que sigue
   en `SINGLE_SELECTION`.
2. Agregar dos métodos públicos:
   - `public List<MaterialLoteItem> getMaterialesDisponiblesSeleccionados()`
   - `public List<MaterialLoteItem> getMaterialesAutoclaveSeleccionados()`
   Implementarlos recorriendo `getSelectedRows()` del `JTable` correspondiente,
   convirtiendo con `convertRowIndexToModel`, mapeando con
   `modeloXxx.getItemAt(row)`, filtrando nulls. (Puede delegar en
   `TableSelectionSupport.selectedItems(tabla, modelo::getItemAt)` del Paso 1 si
   ya está disponible; si se ejecuta en paralelo al Paso 1, implementar inline con
   la misma lógica y el Paso 3 unifica.)
3. Mantener por ahora `getMaterialDisponibleSeleccionado()` /
   `getMaterialAutoclaveSeleccionado()` sin cambios.

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test` (no debe romper nada; el panel no tiene test propio de selección).

### Criterio de salida
Ambas tablas de materiales permiten selección múltiple con Ctrl/Shift; existen los
getters de lista. Commit: `feat: multi-selección y getters de lista en PanelLotesContenido`.

### Rollback
Revertir `PanelLotesContenido.java` (volver a `SINGLE_SELECTION`, quitar getters de
lista).

---

## Paso 3 — Cablear el DnD multi-fila y la lógica múltiple en `LotesController`

### Context brief
Es el paso de integración y el más delicado: **debe preservar exactamente** el
comportamiento de arrastre simple actual y añadir el múltiple. Depende del Paso 1
(infra `ui/common/dnd`) y del Paso 2 (getters de lista del panel).

**Objetivo arquitectónico (dirección de dependencias hacia adentro):** hoy
`LotesController` es un adapter de entrada gordo que mezcla Swing (`TransferHandler`,
`JOptionPane`) con lógica de negocio (reconciliación disponibles↔pendientes,
acumulación de cantidades, cálculo de capacidad). Este paso **extrae toda esa lógica
a un componente plano de aplicación sin dependencias de Swing** (`ReconciliadorPendientes`)
y deja al controller como **adapter delgado** que solo: (a) traduce eventos de Swing,
(b) muestra diálogos/avisos, (c) invoca el componente puro, (d) refresca la vista.
La lógica de negocio del multi-drag **nace** en el componente puro y se testea
aislada — no dentro del adapter. Es el patrón que el repo ya usa (`AgrupadorIngresosLote`,
`SincronizadorVolumenFinal`).

Estado actual relevante de `LotesController.java`:
- `MATERIAL_LOTE_FLAVOR`: `DataFlavor` estático (mime `javaJVMLocalObjectMimeType`,
  clase `MaterialLoteItem`). Con el genérico, el flavor debe representar una
  **`List`** (representationClass `java.util.List`), porque el transfer data pasa a
  ser `List<MaterialLoteItem>`.
- `configurarDnD()`: setea `dragEnabled`, `DropMode.ON`, `fillsViewportHeight`, y
  asigna `new DisponiblesTransferHandler()` / `new AutoclaveTransferHandler()`.
- `DisponiblesTransferHandler` (COPY, origen + destino): al soltar aquí,
  `quitarMaterialDePendientes(item)` + `cargarDatos()`.
- `AutoclaveTransferHandler` (MOVE, destino + origen): usa flag `draggingFromSelf`
  para que el drop no se acepte sobre sí misma; `canImport` exige autoclave
  seleccionado y no ocupado; al soltar, `agregarMaterial(item)` (diálogo de
  cantidad por ítem, chequeo de capacidad, `ajustarDisponibles` + `agregarPendiente`,
  `cargarDatos`); `exportDone` con MOVE refresca.
- Botón *Quitar* → `quitarMaterial()` (una fila vía
  `getMaterialAutoclaveSeleccionado()`).

### Tareas
1. **Flavor de lista:** redefinir `MATERIAL_LOTE_FLAVOR` como
   `new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" +
   java.util.List.class.getName() + "\"")`. (El objeto transferido es la lista.)
2. **CORAZÓN DEL PASO — Componente puro de aplicación `ReconciliadorPendientes`**
   (nuevo, en `features/lotes/controller/helpers/`, **sin ningún import de Swing**):
   Concentra toda la lógica de negocio del staging que hoy está dispersa en el
   controller. Debe usar **transformaciones inmutables** (recibe estado, devuelve
   estado nuevo; no muta listas compartidas). Mover/replicar aquí:
   - `claveItem(item)` (discriminación ortopedia/otros por clave compuesta).
   - La lógica de `ajustarDisponibles` + `agregarPendiente` (alta de un ítem con
     cantidad: descuenta de disponibles, acumula en pendientes si la clave ya existe).
   - La lógica de `quitarMaterialDePendientes` (baja: quita de pendientes y devuelve
     la cantidad a disponibles, creando la fila si no existía).
   - El cálculo de capacidad (`calcularCapacidad` sobre una lista de pendientes;
     los "otros" no suman).
   API sugerida (ajustar nombres al gusto, todo inmutable):
   - `EstadoStaging alta(EstadoStaging estado, MaterialLoteItem item, int cantidad)`
     → devuelve nuevo `(disponibles, pendientes)`.
   - `EstadoStaging baja(EstadoStaging estado, List<MaterialLoteItem> aQuitar)`.
   - `int capacidadUsada(List<MaterialLoteItem> pendientes)`.
   donde `EstadoStaging` es un value type (record o clase inmutable) con las dos
   listas. El controller mantiene `pendientesPorAutoclave`/`materialesDisponibles`
   pero **delega toda mutación** a este componente y reemplaza el estado con el que
   devuelve.
   **Tests obligatorios** `ReconciliadorPendientesTest` (JUnit 5, sin Swing):
   "alta de 3 ítems distintos", "alta de ítem con clave repetida acumula cantidad",
   "alta descuenta de disponibles y elimina la fila al llegar a 0", "baja de
   múltiples devuelve cantidades a disponibles", "baja recrea la fila si no existía",
   "capacidadUsada ignora los otros". Cobertura de la ruta múltiple aquí, no en el
   adapter.
3. **`agregarMateriales(List<MaterialLoteItem> items)`** en el controller — **adapter
   delgado**: iterar en orden; por cada ítem:
   - `pedirCantidad(...)` (Swing) con el mismo callback de checkbox "Todos"; si
     devuelve `null` (cancelado) → **saltear solo ese ítem** (`continue`).
   - delegar el alta a `ReconciliadorPendientes.alta(...)` acumulando el estado; el
     aviso de sobre-capacidad (Swing) se decide comparando
     `reconciliador.capacidadUsada(pendientesTrasAlta)` contra la capacidad total.
   - Tras el bucle, un único `cargarDatos()`.
   - `agregarMaterial(item)` pasa a delegar en `agregarMateriales(List.of(item))`.
   - El controller **no** debe contener ya la aritmética de reconciliación (vive en
     el componente puro).
4. **`quitarMaterialesDePendientes(List<MaterialLoteItem> items)`** en el controller:
   delega la baja a `ReconciliadorPendientes.baja(...)`, reemplaza el estado y hace
   un único `cargarDatos()`. `quitarMaterialDePendientes(item)` delega en la versión
   lista.
5. **Botón Quitar:** `quitarMaterial()` usa
   `panel.getMaterialesAutoclaveSeleccionados()`; si vacío → advertencia "Seleccione
   al menos un material para quitar."; si no → `quitarMaterialesDePendientes(sel)`.
6. **Reemplazar los dos `TransferHandler` internos** por instancias de
   `MultiRowTableTransferHandler<MaterialLoteItem>` (Paso 1), construidas en
   `configurarDnD()`:
   - **Disponibles** (origen COPY + destino): `sourceActions = COPY`;
     `selectionSupplier = panel::getMaterialesDisponiblesSeleccionados`;
     `onImport = items -> quitarMaterialesDePendientes(items)`
     (el refresco lo hace `quitarMaterialesDePendientes`). `canImportExtra` default.
   - **Autoclave** (destino + origen MOVE): `sourceActions = MOVE`;
     `selectionSupplier = panel::getMaterialesAutoclaveSeleccionados`;
     `canImportExtra` = predicado que exige `autoclaveSeleccionado != null &&
     !autoclaveSeleccionado.isOcupado()` **y** que el drag no venga de la misma
     tabla. Para "desde sí misma": conservar el flag `draggingFromSelf`; envolver
     el `selectionSupplier` de la tabla autoclave para setear el flag a `true` al
     iniciar.
     `onImport = items -> SwingUtilities.invokeLater(() -> agregarMateriales(items))`
     (preservar el `invokeLater` actual para que el diálogo no bloquee el EDT del
     drop).
     `onExportDone = action -> { draggingFromSelf = false; if (action == MOVE)
     SwingUtilities.invokeLater(this::cargarDatos); }` — **reset incondicional del
     flag** (replica el `exportDone` actual, que lo resetea antes de chequear MOVE;
     un reset solo-MOVE dejaría el flag atascado si el drag se aborta).
   - Mantener los avisos actuales de "seleccione autoclave" / "ya tiene lote".
7. **Eliminar código muerto:**
   - Borrar `MaterialLoteTransferable.java` (helper) y su import.
   - Borrar los getters de una fila del panel
     (`getMaterialDisponibleSeleccionado`, `getMaterialAutoclaveSeleccionado`) si
     ya no se usan (grep primero). Ajustar `PanelLotesContenido` en consecuencia.
   - Tras extraer la reconciliación al componente puro (tarea 2), el controller no
     debe conservar métodos privados de aritmética de staging duplicados
     (`ajustarDisponibles`, `agregarPendiente`, `claveItem`, `calcularCapacidad`):
     borrarlos del controller una vez que delega en `ReconciliadorPendientes`.
   - Grep `MaterialLoteTransferable` y los getters en `src/test` por si algún test
     los referencia; actualizar.

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test` (suite completa verde, incluidos `ReconciliadorPendientesTest`).
- Grep de confirmación: `MaterialLoteTransferable` no aparece en `src/`.
- **Chequeo de boundary (hexagonal):** grep de imports de Swing en
  `ReconciliadorPendientes.java` → **debe dar cero** (`javax.swing`, `java.awt`).
  El componente de aplicación no depende del framework de UI.

### Criterio de salida
El DnD funciona multi-fila en ambas direcciones y el botón Quitar opera sobre la
selección; el arrastre simple se comporta idéntico al original; **la lógica de
reconciliación vive en `ReconciliadorPendientes` (puro, testeado) y el controller
quedó como adapter delgado**; no queda código muerto. Commit:
`feat: multi-selección en drag-and-drop de materiales de lotes`.

### Rollback
Revertir `LotesController.java`, `PanelLotesContenido.java` y borrar
`ReconciliadorPendientes.java`(+test); restaurar `MaterialLoteTransferable.java`. La
infra `ui/common/dnd` puede quedar (no molesta).

---

## Paso 4 — Verificación end-to-end

### Context brief
Confirmar la feature completa a nivel build, tests y corrida real, ya que el DnD de
Swing es interactivo y no se cubre con unit tests. Depende del Paso 3.

### Tareas
1. `mvn clean package` — genera `target/aptium.jar` sin errores.
2. `mvn verify` — tests + cobertura JaCoCo; verificar que los paquetes nuevos
   (`ui/common/dnd`) y el helper de reconciliación tengan cobertura razonable.
3. **Corrida manual** de la pantalla *Gestionar Lotes* (requiere BD; ver
   `config.example.properties`). Casos a validar:
   - Seleccionar varias filas (Ctrl+click y Shift+click) en *Disponibles* y
     arrastrarlas al autoclave → aparece un diálogo de cantidad por material, en
     orden; cancelar uno saltea solo ese.
   - Capacidad/volumen se actualizan correctamente tras la tanda.
   - Seleccionar varias filas en *Materiales cargados* y arrastrarlas de vuelta a
     *Disponibles* → vuelven todas con sus cantidades.
   - Seleccionar varias y usar el botón *Quitar* → se quitan todas.
   - Arrastre **simple** (una fila) sigue igual que antes.
   - Autoclave ocupado / sin autoclave seleccionado → mismos avisos que hoy.
4. Si algo falla, volver al Paso 3 (protocolo de mutación: no parchear en este paso).

### Verificación
- `mvn clean package` y `mvn verify` verdes.
- Checklist manual completo sin regresiones.

### Criterio de salida
Feature validada end-to-end. Rama lista para merge según el flujo del usuario
(`MultiSelectYCursorInfo` → rama de trabajo → `main`).

### Rollback
N/A (paso de verificación). Los defectos se corrigen en el Paso 3.

---

## Notas de mutación del plan
- Si el "drag desde sí misma" resulta difícil de expresar limpio en la API genérica
  del Paso 1, está permitido **insertar un Paso 3.5** que ajuste
  `MultiRowTableTransferHandler` (p.ej. exponer el componente/origen del
  `TransferSupport` al `canImportExtra`) — documentar el cambio aquí.
- Si el diálogo secuencial por material se percibe molesto en la corrida manual
  (Paso 4) para tandas grandes, **no** cambiar de enfoque sin consultar al usuario
  (la decisión fue explícita).
