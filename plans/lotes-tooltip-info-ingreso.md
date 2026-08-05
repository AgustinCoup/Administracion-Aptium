# Blueprint — Tooltip con info del ingreso al hacer hover sobre materiales (Gestionar Lotes)

> Plan de construcción autocontenido. Cada paso incluye su propio *context brief*
> para que un agente pueda ejecutarlo en frío, sin haber leído los pasos previos.

## Objetivo

Al pasar el cursor por encima de una fila de material en las tablas de *Gestionar
Lotes*, mostrar un **tooltip** con la información del **ingreso** (equipo de origen)
de ese material. La solución debe quedar **encapsulada** y con el mecanismo de
tooltip por fila **extraído a un componente genérico reutilizable** en `ui/common`
(aunque hoy solo lo consuma Lotes), siguiendo el mismo patrón que el paquete
`ui/common/dnd` (glue de Swing genérico + lógica de dominio pura y testeada).

## Decisiones de diseño (confirmadas con el usuario)

1. **Contenido por tipo:**
   - **Ortopedias:** nombre del material (encabezado) + **Cliente, Profesional,
     Paciente, Institución, Fecha y hora de ingreso**.
   - **Otros:** nombre del material (encabezado) + **Cliente, Remito (solo si el
     ingreso es de tipo REMITO), Fecha y hora de ingreso**.
2. **Campos vacíos (ortopedias):** los campos médicos opcionales
   (Profesional/Paciente/Institución) y la fecha se muestran **siempre**, con
   **guión `—`** cuando faltan. (La línea *Remito* de "otros" es la excepción: solo
   aparece cuando `tipoIngreso == REMITO`; en DETALLES no se muestra.)
3. **Encabezado:** el tooltip arranca con el **nombre del material** de la fila
   señalada, luego los datos del ingreso.
4. **Alcance:** **ambas** tablas de materiales de *Gestionar Lotes* (Disponibles y
   Materiales cargados), incluidos los materiales de un **lote ya lanzado** (se
   resuelve el ingreso por `equipoId` igual que los pendientes). Solo esta pantalla
   por ahora; el componente genérico queda listo para reusar en otras.

## Contexto arquitectónico común (leer una vez)

- App de escritorio **Swing / Java 11**, sin DI (cableado manual en `AppContext`).
- **Toda la data ya existe en los modelos** (verificado); **no hacen falta queries
  nuevas**:
  - `Equipo` (ortopedias) expone `getClienteNombre()`, `getProfesionalNombre()`,
    `getPacienteNombre()`, `getInstitucionNombre()`, `getFechaIngreso()`
    (`LocalDateTime`). El DAO de lectura los llena (`EquipoDAO` mapRow, la consulta
    de `obtenerTodosLosEquipos` hace JOIN a profesional/institución y trae
    `fecha_ingreso`).
  - `EquipoOtros` expone `getClienteNombre()`, `getTipoIngreso()`
    (`TipoIngresoOtros.REMITO|DETALLES`), `getRemitoId()` (`ddmmaaaa-{id}`),
    `getFechaIngreso()`.
- Estado relevante hoy en `LotesController.cargarDatos()`:
  - Carga **todos** los equipos y otros: `model.obtenerTodosLosEquipos()` /
    `model.obtenerTodosLosEquiposOtros()`.
  - Retiene los `EquipoOtros` **completos** en `equiposOtrosPorId`
    (`Map<Integer, EquipoOtros>`), pero de ortopedias **solo** guarda el cliente en
    `clientesPorEquipo` (`Map<Integer,String>`); los `Equipo` completos se descartan.
    → El Paso 3 debe **retener también** la info de ingreso de ortopedias por
    `equipoId`.
  - Rama `equipoContexto != null` (panel embebido para un equipo): solo mete
    `equipoContexto` en `clientesPorEquipo` y `equiposOtros` queda vacío. El lookup
    debe cubrir también esta rama (construir la info desde `equipoContexto`).
- `MaterialLoteItem` (fila de las tablas) expone `getDescripcion()`, `getEquipoId()`
  e `isEsOtros()`. Con `(esOtros, equipoId)` se resuelve el ingreso sin ambigüedad
  (mismo criterio de discriminación que `ReconciliadorPendientes.claveItem`, pero
  clavado por `equipoId` en vez de `materialId`).
- Las dos tablas de materiales las construye
  `PanelLotesContenido` (`tablaDisponibles`, `tablaAutoclave`), ambas con modelo
  `MaterialLoteTableModel` (`getItemAt(modelRow)`). Se acceden desde el controller
  vía `getTablaDisponibles()` / `getTablaAutoclave()`. No hay `RowSorter`; aun así
  convertir índice vista→modelo por robustez.
- **Estilo del repo:** inmutabilidad preferida, archivos < 800 líneas, funciones
  < 50 líneas. Lógica embebida en Swing se **extrae a clases planas** y se testea en
  aislamiento (ver `AgrupadorIngresosLote`, `SincronizadorVolumenFinal`,
  `ReconciliadorPendientes`). Tests: JUnit 5 + Mockito.
- **Git:** rama actual `MultiSelectYCursorInfo`. Modo directo: cada paso es un
  commit. Verificación por paso: `mvn -q -DskipTests compile` y, donde aplique,
  `mvn -q test`. Al final `mvn clean package`.

## Formato del tooltip (especificación exacta)

HTML envuelto en `<html>…</html>` con `<br>`. Fecha con
`DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")`; `null` → `—`.

**Ortopedia:**
```
<b>{descripcion}</b>
Cliente: {clienteNombre|—}
Profesional: {profesionalNombre|—}
Paciente: {pacienteNombre|—}
Institución: {institucionNombre|—}
Ingreso: {fecha|—}
```
**Otros:**
```
<b>{descripcion}</b>
Cliente: {clienteNombre|—}
Remito: {remitoId}      ← SOLO si tipoIngreso == REMITO
Ingreso: {fecha|—}
```
Si no se encuentra el ingreso (`info == null`, p.ej. equipo ya no cargado): mostrar
solo `<b>{descripcion}</b>` + una línea `Sin datos de ingreso`.

## Invariantes (verificar tras CADA paso)

- `mvn -q -DskipTests compile` pasa.
- La suite existente sigue verde: `mvn -q test`. Ningún test roto por cambio de
  firma; si un test referencia una firma modificada, actualizarlo al nuevo contrato.
- Sin código muerto nuevo.
- **Chequeo de boundary:** `IngresoInfo` e `IngresoTooltipFormatter` **sin imports
  de Swing/AWT** (`javax.swing`, `java.awt`) — grep debe dar cero. El componente
  reutilizable `RowTooltipTable` es el único glue de Swing.

## Grafo de dependencias

```
Paso 1 (dominio puro: IngresoInfo + formatter + tests)       ┐
                                                              ├─► Paso 3 (cableado en LotesController) ─► Paso 4 (verificación)
Paso 2 (componente reutilizable RowTooltipTable + API panel) ┘
```

- **Paso 1 ∥ Paso 2** (archivos disjuntos: `features/lotes/view/helpers/*` nuevos vs.
  `ui/common/RowTooltipTable.java` + `PanelLotesContenido.java`).
- **Paso 3** depende de 1 **y** 2. **Paso 4** depende de 3.

| Paso | Descripción | Paralelo con |
|------|-------------|--------------|
| 1 | `IngresoInfo` (value type) + `IngresoTooltipFormatter` (puro) + tests | Paso 2 |
| 2 | `RowTooltipTable` reutilizable + tablas del panel lo usan + setters | Paso 1 |
| 3 | Controller: lookup de ingreso por equipo + wiring de los tooltips | — |
| 4 | Verificación end-to-end (build + tests + corrida manual del hover) | — |

---

## Paso 1 — Núcleo de dominio: `IngresoInfo` + `IngresoTooltipFormatter` (puros)

### Context brief
Crear la representación inmutable de la info de ingreso y el formateador de texto
del tooltip, **sin dependencias de Swing**, testeados en aislamiento (patrón del
repo). Es código nuevo, no toca nada existente. La fila la representa
`MaterialLoteItem` (`getDescripcion()`, `getEquipoId()`, `isEsOtros()`).

### Tareas
1. Crear `src/main/java/com/example/features/lotes/view/helpers/IngresoInfo.java`:
   value type **inmutable** (clase final, campos `final`, getters) con:
   `boolean esOtros`, `String clienteNombre`, `String profesionalNombre`,
   `String pacienteNombre`, `String institucionNombre`, `boolean esRemito`,
   `String remitoId`, `LocalDateTime fechaIngreso`. Dos *factory methods* estáticos
   para claridad: `deOrtopedia(cliente, profesional, paciente, institucion, fecha)`
   y `deOtros(cliente, tipoEsRemito, remitoId, fecha)`. (Sin lógica; solo datos.)
2. Crear `src/main/java/com/example/features/lotes/view/helpers/IngresoTooltipFormatter.java`
   (clase de utilidades estáticas, no instanciable):
   - `static String format(MaterialLoteItem item, IngresoInfo info)` → devuelve el
     HTML según la **especificación exacta** de arriba.
   - Constante privada `DateTimeFormatter FMT = ofPattern("dd/MM/yyyy HH:mm")`.
   - Helper privado `orGuion(String)` → devuelve `—` si null/blank.
   - Escapar `<`, `>`, `&` en `descripcion`/valores para no romper el HTML.
   - `info == null` → `<html><b>{desc}</b><br>Sin datos de ingreso</html>`.
3. Tests `IngresoTooltipFormatterTest` (JUnit 5, sin Swing):
   - Ortopedia completa → contiene material, Cliente, Profesional, Paciente,
     Institución, Ingreso con fecha formateada.
   - Ortopedia con profesional/paciente/institución nulos → esas líneas muestran `—`
     (y siguen apareciendo).
   - Otros REMITO → incluye línea `Remito: ddmmaaaa-…`; **no** incluye Profesional/
     Paciente/Institución.
   - Otros DETALLES → **no** incluye línea Remito.
   - `info == null` → contiene `Sin datos de ingreso`.
   - Descripción con `<` / `&` → aparece escapada (no rompe el HTML).

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test -Dtest=IngresoTooltipFormatterTest`
- Grep de Swing/AWT en `IngresoInfo.java` e `IngresoTooltipFormatter.java` → cero.

### Criterio de salida
Núcleo puro compila y testea. Ninguna clase existente modificada. Commit:
`feat: value type y formateador puro del tooltip de ingreso de lotes`.

### Rollback
Borrar los dos archivos main + el test. Sin otros efectos.

---

## Paso 2 — Componente reutilizable `RowTooltipTable` + API en `PanelLotesContenido`

### Context brief
Crear una `JTable` reutilizable que muestre un **tooltip por fila** provisto por una
función inyectada, y hacer que las dos tablas de materiales del panel la usen.
Reutilizable por cualquier feature (vive en `com.example.ui.common`, junto a
`TableStyler`/`LabelFactory`). No conoce el dominio: recibe
`IntFunction<String>` (índice de fila de **modelo** → texto de tooltip, o `null`).
Archivos: nuevo `ui/common/RowTooltipTable.java` + `PanelLotesContenido.java`.

### Tareas
1. Crear `src/main/java/com/example/ui/common/RowTooltipTable.java`:
   - `public class RowTooltipTable extends JTable`.
   - Constructor `RowTooltipTable(TableModel model)` → `super(model)`.
   - Campo `transient IntFunction<String> rowTooltipProvider` (default `null`).
   - `public void setRowTooltipProvider(IntFunction<String> provider)`.
   - `@Override public String getToolTipText(MouseEvent e)`: si el provider es
     `null` → `super.getToolTipText(e)`; si no, `int viewRow = rowAtPoint(e.getPoint());`
     `if (viewRow < 0) return null;` `return rowTooltipProvider.apply(convertRowIndexToModel(viewRow));`.
   - En el constructor, registrar la tabla en el ToolTipManager para que el tooltip
     dinámico se dispare aun sin texto estático:
     `ToolTipManager.sharedInstance().registerComponent(this);`.
   - Javadoc: las reglas de contenido van en el provider; la clase es solo glue.
   - **Nota de test:** es glue de Swing (como `MultiRowTableTransferHandler`);
     no requiere unit test propio (la lógica testeada vive en el formatter del
     Paso 1). Cobertura baja aceptable.
2. En `PanelLotesContenido`:
   - Cambiar la construcción de las **dos** tablas de materiales de
     `new JTable(modeloXxx)` a `new RowTooltipTable(modeloXxx)`. Tipar los campos
     `tablaDisponibles`/`tablaAutoclave` como `RowTooltipTable` (evita casts en los
     setters). **No** tocar `tablaAutoclaves` (equipos).
   - Agregar dos métodos públicos que adaptan `MaterialLoteItem → String` a
     `modelRow → String` usando el modelo interno:
     - `public void setTooltipDisponibles(Function<MaterialLoteItem,String> textoPorItem)`
     - `public void setTooltipAutoclave(Function<MaterialLoteItem,String> textoPorItem)`
     Implementación (idéntica salvo tabla/modelo):
     ```java
     tablaDisponibles.setRowTooltipProvider(row -> {
         MaterialLoteItem it = modeloDisponibles.getItemAt(row);
         return it == null ? null : textoPorItem.apply(it);
     });
     ```
   - Mantener el resto del panel intacto (multi-selección, DnD, anchos de columna).
     `getTablaDisponibles()/getTablaAutoclave()` pueden seguir devolviendo `JTable`
     (covariante ok) para no romper `LotesController.configurarDnD()`.

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test` (no debe romper nada).

### Criterio de salida
Las dos tablas de materiales son `RowTooltipTable`; existen los setters de tooltip;
todavía sin proveedor cableado (no muestran nada aún). Commit:
`feat: RowTooltipTable reutilizable y API de tooltip en PanelLotesContenido`.

### Rollback
Revertir `PanelLotesContenido.java` (volver a `new JTable`) y borrar
`RowTooltipTable.java`.

---

## Paso 3 — Cablear el lookup de ingreso y los proveedores de tooltip en `LotesController`

### Context brief
Paso de integración. Depende del Paso 1 (formatter puro) y del Paso 2 (setters del
panel). Objetivo: construir en `cargarDatos()` un lookup `equipoId → IngresoInfo`
(para ortopedias; los "otros" ya están en `equiposOtrosPorId`), y cablear una única
vez los proveedores de tooltip de ambas tablas con
`IngresoTooltipFormatter.format(item, resolver(item))`.

Estado actual: `cargarDatos()` limpia y rellena `clientesPorEquipo` y
`equiposOtrosPorId`. En la rama `equipoContexto == null` itera `equipos` (ortopedias)
y `equiposOtros`. En la rama `equipoContexto != null` solo mete ese equipo.

### Tareas
1. **Nuevo estado:** `private Map<Integer, IngresoInfo> ingresoOrtopediaPorEquipo =
   new HashMap<>();` (poblado en `cargarDatos()`; los "otros" se resuelven desde
   `equiposOtrosPorId`).
2. **Poblar en `cargarDatos()`** (junto a los `clear()` existentes):
   `ingresoOrtopediaPorEquipo.clear();`
   - Rama `equipoContexto != null`:
     `ingresoOrtopediaPorEquipo.put(equipoContexto.getId(), IngresoInfo.deOrtopedia(...))`.
   - Rama `else`: por cada `Equipo eq : equipos`,
     `ingresoOrtopediaPorEquipo.put(eq.getId(), IngresoInfo.deOrtopedia(eq.getClienteNombre(),
     eq.getProfesionalNombre(), eq.getPacienteNombre(), eq.getInstitucionNombre(),
     eq.getFechaIngreso()))`.
   (Los "otros" no se pre-mapean: se leen de `equiposOtrosPorId` en el resolver.)
3. **Resolver** (método privado del controller):
   ```java
   private IngresoInfo resolverIngreso(MaterialLoteItem item) {
       if (item.isEsOtros()) {
           EquipoOtros eo = equiposOtrosPorId.get(item.getEquipoId());
           return eo == null ? null : IngresoInfo.deOtros(
               eo.getClienteNombre(),
               eo.getTipoIngreso() == TipoIngresoOtros.REMITO,
               eo.getRemitoId(), eo.getFechaIngreso());
       }
       return ingresoOrtopediaPorEquipo.get(item.getEquipoId()); // null si no está
   }
   ```
4. **Cablear los proveedores una sola vez** (en `inicializarEventos()`, tras
   `panel.setOn...`):
   ```java
   panel.setTooltipDisponibles(item -> IngresoTooltipFormatter.format(item, resolverIngreso(item)));
   panel.setTooltipAutoclave(item  -> IngresoTooltipFormatter.format(item, resolverIngreso(item)));
   ```
   Los closures leen los campos del controller de forma perezosa en cada hover, así
   que sobreviven a los `cargarDatos()` (que reemplazan los modelos y repueblan los
   mapas). No hace falta re-cablear en cada refresh.
5. Imports nuevos en el controller: `IngresoInfo`, `IngresoTooltipFormatter`,
   `TipoIngresoOtros` (si no estaba). Sin código muerto.

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test` (suite completa verde).
- Grep: `ingresoOrtopediaPorEquipo` se limpia y repuebla en `cargarDatos()` (no
  queda stale entre refrescos).

### Criterio de salida
El hover sobre cualquier fila de material (Disponibles o Cargados, lote pendiente o
lanzado) muestra el tooltip con la info del ingreso según la especificación. La
lógica de formato vive en el componente puro; el controller solo arma el lookup y
cablea. Commit: `feat: tooltip con info del ingreso al pasar el cursor en lotes`.

### Rollback
Revertir `LotesController.java` (quitar mapa, resolver y wiring). Panel y núcleo
puro quedan (inertes sin proveedor).

---

## Paso 4 — Verificación end-to-end

### Context brief
El tooltip es interactivo y no se cubre con unit tests; validar build, tests y
corrida real. Depende del Paso 3.

### Tareas
1. `mvn clean package` — genera `target/aptium.jar` sin errores.
2. `mvn verify` — tests + cobertura JaCoCo; verificar que `IngresoTooltipFormatter`
   tenga cobertura razonable (el glue `RowTooltipTable` puede quedar bajo).
3. **Corrida manual** de *Gestionar Lotes* (requiere BD). Casos:
   - Hover sobre un material **ortopédico** en *Disponibles* → tooltip con material,
     Cliente, Profesional, Paciente, Institución, Ingreso (fecha/hora); campos
     faltantes muestran `—`.
   - Hover sobre un material **"otros" REMITO** → incluye línea *Remito*; sin
     Profesional/Paciente/Institución.
   - Hover sobre un material **"otros" DETALLES** → sin línea *Remito*.
   - Hover en *Materiales cargados* con lote **pendiente** y con lote **lanzado** →
     ambos muestran el tooltip correcto.
   - Multi-selección y DnD siguen funcionando igual (el override de tooltip no
     interfiere con el arrastre).
4. Si algo falla, volver al Paso 3 (no parchear en el Paso 4).

### Verificación
- `mvn clean package` y `mvn verify` verdes.
- Checklist manual completo sin regresiones.

### Criterio de salida
Feature validada end-to-end. Rama lista para merge según el flujo del usuario.

### Rollback
N/A (verificación). Defectos se corrigen en el Paso 3.

---

## Notas de mutación del plan
- Si en la corrida manual (Paso 4) el tooltip **no aparece** pese al override, la
  causa típica es el registro en `ToolTipManager`: confirmar
  `ToolTipManager.sharedInstance().registerComponent(tabla)` en el constructor de
  `RowTooltipTable` (Paso 2). Documentar el ajuste aquí.
- Si se pide sumar el tooltip a otras pantallas (Equipos), reutilizar
  `RowTooltipTable` + un formatter propio de ese dominio; no duplicar el glue.
