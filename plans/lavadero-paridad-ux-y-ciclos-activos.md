# Blueprint — Paridad UX Lavadero (hotkey, duplicados, restricciones) + paridad completa Ver Ciclos / Ver Lotes

> Plan de construcción autocontenido. Cada paso incluye su propio *context brief*
> para que un agente pueda ejecutarlo en frío, sin haber leído los pasos previos.

## Objetivo

Cerrar 3 de las 4 brechas identificadas entre el módulo **Lavadero** y el módulo de
referencia con mejor UX (**`equipos/ortopedias`**, no "CDE" — ver nota más abajo):

1. Hotkey **Ctrl++/Ctrl+-** para agregar/quitar bolsas en el ingreso de Lavadero.
2. Paridad de utilidades UX: resaltado de duplicados en la pantalla de Clasificación
   y restricciones de teclado en los campos numéricos que hoy aceptan cualquier tecla.
3. **Paridad completa** (filtrado **y** arquitectura de refresco) entre **Ver Ciclos**
   y **Ver Lotes**: filtro de estado + color-coding, y `VerCiclosController` migrado
   al mismo mecanismo `AbstractFilterController` + `Disparador`/`RefrescadorPantallas`
   que ya usa `VerLotesController`, en vez de auto-buscar sus datos de forma aislada.

## Nota — un punto del pedido original quedó fuera de este plan

El pedido original tenía un 4º punto ("manejar la conexión a la BD de Lavadero
con la conexión única de CDE"). La investigación previa a este plan encontró que
**no existe tal brecha**: tanto `equipos/ortopedias` como `lavadero` ya usan
exactamente `ConnectionPool.getConnection()` / `TransactionalConnection` de la
misma forma (ver `src/main/java/com/example/infrastructure/db/ConnectionPool.java:315`
y `TransactionalConnection.java:45`). Tampoco existe un módulo literal llamado
"CDE" — ese prefijo solo aparece en `CDEViewController`/`PantallaVerCDEv2`, una
pantalla de monitoreo en tiempo real (`ESTADO DE PROCESOS EN TIEMPO REAL`), no la
pantalla de ingreso. **Confirmado con el usuario: se descarta, sin paso de
construcción.**

## Decisiones de diseño (confirmadas con el usuario)

1. **Alcance de paridad UX (punto 2):** `DuplicadoHighlighter` aplica a la pantalla
   de **Clasificación** (`PanelElementosClasificacion` — duplicado = mismo
   `ElementoCatalogo` elegido en dos `JComboBox` de filas distintas, no texto
   tipeado). `RestriccionesCampo` aplica a **todo Lavadero** donde corresponda:
   campos numéricos/decimales sí, campos de texto libre genuino (ej. `txtCliente`
   en el ingreso) no — mismo criterio que su precedente en ortopedias
   (`PantallaIngresoOrtopedia.txtCliente` tampoco está restringido).
2. **Filtro de estado en Ver Ciclos:** replica la arquitectura de Ver Lotes
   (`CheckableComboBox` + `FilterCriteria`/`FilterStrategy`), no solo el resultado.
   `CicloLavadero` tiene 2 estados (`ACTIVO`/`FINALIZADO`, no 3 como Lotes) — el
   combo refleja eso.
3. **El renderer de color NO se comparte entre features.** `features/lotes/view/helpers/EstadoCellRenderer.java`
   queda **intacto, sin tocar**. Ver Ciclos recibe su propio
   `CicloEstadoCellRenderer` en `features/lavadero/view/helpers/`, con su propio
   mapa color→estado (`ACTIVO`/`FINALIZADO`). Es una decisión explícita del usuario
   de mantener cada feature dueña de su propia lógica de presentación en vez de
   generalizar una clase compartida entre dos dominios que no se solapan — el costo
   es ~20 líneas de estructura similar duplicadas entre las dos clases.
4. **Paridad completa de arquitectura de refresco (no solo el filtrado):**
   `VerCiclosController` pasa a extender `AbstractFilterController<CicloLavadero>`
   y se integra al mecanismo `Disparador`/`RefrescadorPantallas` de `UiCoordinator`
   — igual que `VerLotesController` — en vez de auto-buscar sus datos en el
   constructor y en su propio listener de `componentShown`. **Esto implica tocar
   `UiCoordinator.java`, el composition root de toda la app** (agrega un cuarto
   grupo de refresco junto a `operativo`/`historialEquipos`/`historialLotes`). Es
   una superficie de cambio mayor que el resto del plan — inherente a pedir
   paridad *completa*, no un efecto colateral no buscado. Como beneficio adicional
   (no el objetivo, pero corrige algo que hoy es un problema real): la lectura a
   la base deja de bloquear el EDT en cada apertura de la pantalla.

## Contexto arquitectónico común (leer una vez)

- App de escritorio **Swing / Java 17**, sin DI (cableado manual en `AppContext`).
- Utilidades UI compartidas viven en `src/main/java/com/example/ui/common/`:
  `Hotkeys`, `DuplicadoHighlighter`, `RestriccionesCampo`, `CheckableComboBox`,
  `Estilos`, `PanelHeader`, `TableStyler`, `FilterUiHelper`.
- **`Hotkeys.registrarMateriales(JPanel, Runnable onAgregar, Runnable onQuitar)`**
  (`Hotkeys.java:32-47`) ya está listo para usar tal cual — liga Ctrl+Plus/Ctrl+Minus
  (+ teclado numérico) a los `Runnable` que le pases. Ya lo usan
  `PanelMateriales.java:91-94` (ortopedias) y `PanelMaterialesOtros.java:89` (otros).
- **`DuplicadoHighlighter.marcar(List<JTextField>, Function<String,String>, Color,
  String)`** (`DuplicadoHighlighter.java:35-57`) solo entiende `JTextField` hoy.
  Dos llamadores existentes que **no se pueden regresionar**:
  `PanelMateriales.tieneDuplicados()` (ortopedias, línea 117-126) y
  `PanelMaterialesOtros.tieneDuplicados()` (otros, línea 118). Tests de regresión:
  `PanelMaterialesTest`, `PanelMaterialesOtrosTest`, `DuplicadoHighlighterTest`.
- **`RestriccionesCampo.soloNumeros(JTextField)` / `soloLetrasYEspacios(JTextField)`**
  (`RestriccionesCampo.java:27-55`) — `KeyAdapter` que descarta teclas no válidas
  en `keyTyped`. Sin test hoy (`RestriccionesCampoTest` no existe).
- **`CheckableComboBox<E>`** (`ui/common/CheckableComboBox.java`) — combo
  multi-selección con checkboxes; API relevante: `getSelectedItems()`,
  `clearSelection()`, `setOnSelectionChange(Runnable)`.
- **Patrón de filtrado de Lotes** (referencia a replicar para Ciclos):
  `features/lotes/controller/helpers/LotesFilterCriteria.java` (holder inmutable) +
  `LotesFilterStrategy.java` (`implements FilterStrategy<Lote, LotesFilterCriteria>`,
  interfaz en `common/util/FilterStrategy.java`) + `VerLotesController.aplicarFiltros()`
  (línea 58-70, construye el criteria y llama `filterStrategy.filter(getCache(), criteria)`).
  `EstadoCellRenderer` (`features/lotes/view/helpers/`) **no se toca ni se reutiliza**
  (decisión de diseño #3) — sirve solo de referencia de forma.
- **Patrón de refresco de Lotes** (referencia a replicar para Ciclos —
  `src/main/java/com/example/app/ui/UiCoordinator.java`):
  - Tres `Disparador` privados (`operativo`, `historialEquipos`, `historialLotes`,
    línea 64-66) — cada uno es un `Runnable` handle vacío (`solicitar()` es
    no-op hasta que se le hace `cablear(...)`), necesario porque un controller no
    puede recibir por constructor un refrescador que a su vez lo necesita a él.
  - Cada grupo se cablea a un `RefrescadorPantallas<T>` (`RefrescadorPantallas.java`)
    construido por un método factory (`crearRefrescadorHistorialLotes(...)`,
    línea 254-264): recibe un `Supplier<T>` que lee de la BD **fuera del EDT** y
    un `Consumer<T>` que pinta el resultado **en el EDT**, con debounce de 150ms
    y cancelación de lecturas en vuelo (evita resultados fuera de orden).
  - `VerLotesController` (`VerLotesController.java:31-45`) recibe el `Disparador`
    como `Runnable solicitarRefresco` por constructor; su único listener de
    `componentShown` llama `solicitarRefresco.run()` — **no lee la BD él mismo**.
    Expone `public void pintar(HistorialLotes datos)` (línea 48-56), que el
    `RefrescadorPantallas` invoca tras leer, y que llama a `recargarCache(...)`
    (heredado de `AbstractFilterController`).
  - `HistorialLotes` es un `record` DTO que existe **solo** porque
    `PantallaVerLotes` tiene un combo de autoclaves que hay que poblar aparte de
    la lista de lotes (`LectorHistorialLotes.java`). **`PantallaVerCiclos` no
    tiene ningún combo equivalente** (barrido completo confirmado — su única API
    de datos es `actualizarCiclos(List<CicloLavadero>)`), así que Ciclos **no
    necesita un DTO nuevo**: el snapshot es directamente `List<CicloLavadero>`.
- **`AbstractFilterController<T>`** (`common/util/AbstractFilterController.java`,
  19 líneas) — `recargarCache(List<T>)` (guarda + llama `aplicarFiltros()`),
  `getCache()`, `aplicarFiltros()` abstracto y **`protected`** (obligatorio
  respetar ese modificador al implementarlo).
- **Estado actual de Ver Ciclos:** `CicloLavaderoDAO.SQL_TODOS` (sin `WHERE`) ya
  trae ciclos activos e históricos — **no hay que tocar el DAO ni el service**.
  `VerCiclosController.filtrar(...)` (estático, package-private, línea 47-59) filtra
  por número/fechas con lógica inline (sin `FilterStrategy`). `VerCiclosController`
  hoy recibe `CicloLavaderoService` directo y lee la BD de forma síncrona **en el
  EDT** en tres puntos: constructor, su propio listener de `componentShown`, y una
  llamada redundante en el botón "Ver Ciclos" de `UiCoordinator.java:198` (redundante
  porque `show(...)` ya dispara `componentShown`). `PantallaVerCiclos` no tiene
  filtro de estado ni columna Estado.
- **Estilo del repo:** inmutabilidad preferida, archivos < 800 líneas, sin nesting
  profundo. Lógica de negocio embebida en Swing se extrae a clases planas
  testeables. Tests: JUnit 5 + Mockito + H2, estilo `assertEquals` plano (no
  AssertJ — así está toda la suite existente).
- **Git:** rama actual `ConexionConCDE` (HEAD == `main`, sin commits propios
  todavía). Modo directo: cada paso es un commit en esta rama. Verificación por
  paso: `mvn -q -DskipTests compile` (rápida) y, donde aplique, `mvn -q test`. Al
  final `mvn clean package`.

## Invariantes (verificar tras CADA paso)

- `mvn -q -DskipTests compile` pasa.
- La suite existente sigue verde (`mvn -q test`). Si un test referencia una firma
  modificada, **actualizar el test** al nuevo contrato (no borrar cobertura).
- Los dos llamadores existentes de `DuplicadoHighlighter.marcar` (ortopedias, otros)
  siguen compilando **sin cambiar su código** — el overload de 4 argumentos con
  `List<JTextField>` se preserva intacto (delega al nuevo overload genérico).
- `features/lotes/**` no se toca en ningún paso de este plan (decisión de diseño #3).
- No queda código muerto: si `VerCiclosController.filtrar(...)` o `cargarDatos()`
  dejan de tener llamadores, se eliminan en el mismo paso que los deja huérfanos.
- **A partir del Paso 5**, correr la suite completa (`mvn -q test`, no solo el
  paquete de lavadero) — ese paso toca `UiCoordinator.java`, la composition root
  de toda la app; un error de cableado ahí puede romper el arranque de cualquier
  pantalla, no solo Lavadero.

## Grafo de dependencias

```
Paso 1 (hotkey ingreso)         ┐
Paso 2 (duplicados clasific.)   ├── independientes entre sí
Paso 3 (RestriccionesCampo)     ┘
                                       │
                                       ▼ (Paso 4 toca PantallaVerCiclos, igual que Paso 3)
                                  Paso 4 (filtro estado + renderer propio, Ver Ciclos)
                                       │
                                       ▼ (Paso 5 reescribe VerCiclosController, ya con
                                       │  el filtrado del Paso 4 en su lugar)
                                  Paso 5 (paridad de arquitectura de refresco)
                                       │
                                       ▼
                                  Paso 6 (verificación final)
```

| Paso | Descripción | Tier modelo | Paralelo con |
|------|-------------|-------------|---------------|
| 1 | Ctrl++/Ctrl+- en ingreso Lavadero (`PanelBolsas`) | default | 2, 3 |
| 2 | `DuplicadoHighlighter` genérico + resaltado en Clasificación | **strongest** (API compartida, 2 llamadores existentes) | 1, 3 |
| 3 | `RestriccionesCampo` — nuevo método decimal + aplicar en Lavadero | default | 1, 2 |
| 4 | Filtro de estado + renderer propio en Ver Ciclos | **strongest** (nuevo patrón `FilterStrategy`, migra tests) | — (depende de 3) |
| 5 | Paridad de arquitectura de refresco (`AbstractFilterController` + `UiCoordinator`) | **strongest** (toca la composition root, mayor blast radius de todo el plan) | — (depende de 4) |
| 6 | Verificación final (build completo + checklist manual) | default | — |

---

## Paso 1 — Hotkey Ctrl++/Ctrl+- en el ingreso de Lavadero

### Context brief
`Hotkeys.registrarMateriales(JPanel, Runnable onAgregar, Runnable onQuitar)`
(`src/main/java/com/example/ui/common/Hotkeys.java:32-47`) ya liga Ctrl+Plus/Ctrl+Minus
(WHEN_IN_FOCUSED_WINDOW, incluye teclado numérico) a los `Runnable`s que le pasás —
no hay que tocar esa clase. El ingreso de Lavadero (`PanelBolsas.java`) agrega/quita
filas de bolsas solo con los botones `btnAgregar`/`btnEliminar`
(`src/main/java/com/example/features/lavadero/view/PanelBolsas.java:55-64`); no usa
`Hotkeys` en absoluto. El patrón exacto a copiar está en
`src/main/java/com/example/features/equipos/ortopedias/view/helpers/PanelMateriales.java:91-94`:

```java
Hotkeys.registrarMateriales(this,
    () -> { agregarFilaMaterial(); listaMaterialesPanel.revalidate(); listaMaterialesPanel.repaint(); },
    () -> eliminarUltimaFilaMaterial()
);
```

### Tareas
1. En `PanelBolsas.java`, importar `com.example.ui.common.Hotkeys`.
2. Al final del constructor (después del bloque `btnEliminar.addActionListener(...)`,
   línea 60-64), agregar:
   ```java
   Hotkeys.registrarMateriales(this,
       () -> { agregarFila(); listPanel.revalidate(); listPanel.repaint(); },
       this::eliminarUltimaFila
   );
   ```
   (`eliminarUltimaFila()` ya llama `eliminarFila(...)`, que ya hace
   `revalidate()`/`repaint()` internamente — no hace falta repetirlo, igual que en
   el callback `onQuitar` de ortopedias.)

### Verificación
- `mvn -q -DskipTests compile`
- Smoke manual (no hay test unitario razonable para un binding de teclado sobre un
  `JPanel` sin ventana): abrir Ingreso Lavadero, Ctrl++ agrega una fila, Ctrl+-
  quita la última.

### Criterio de salida
`PanelBolsas` responde a Ctrl++/Ctrl+- igual que `PanelMateriales`. Commit:
`feat: hotkey Ctrl++/Ctrl+- para bolsas en ingreso Lavadero`.

### Rollback
Quitar el bloque `Hotkeys.registrarMateriales(...)` agregado y el import.

---

## Paso 2 — `DuplicadoHighlighter` genérico + resaltado en Clasificación Lavadero

### Context brief
`DuplicadoHighlighter.marcar(List<JTextField> campos, Function<String,String>
normalizador, Color colorNormal, String tooltipDuplicado)`
(`src/main/java/com/example/ui/common/DuplicadoHighlighter.java:35-57`) solo acepta
`JTextField`. En Clasificación Lavadero el "duplicado" no es texto tipeado: es el
mismo `ElementoCatalogo` elegido en el `JComboBox<ElementoCatalogo>` de dos filas
distintas de `PanelElementosClasificacion`
(`src/main/java/com/example/features/lavadero/view/PanelElementosClasificacion.java`,
clase interna `ElementoFila`, campo `cmbElemento`, línea 15). Nada impide hoy elegir
el mismo elemento dos veces — `agregarFila()` (línea 47-65) crea cada combo con
índice 0 sin verificar contra las filas existentes.

**Importante:** `ClasificacionLavaderoService.guardar()`
(`src/main/java/com/example/features/lavadero/service/ClasificacionLavaderoService.java:39-54`)
**ya bloquea el guardado si hay elementos repetidos**, vía
`ValidationException` con el mensaje "No puede haber elementos repetidos...". Este
paso **no cambia esa validación** — agrega el mismo feedback visual inmediato
(resaltado rojo + bloqueo antes de tocar el service) que ya tiene ortopedias, para
no depender de la excepción como único mecanismo de aviso.

Dos llamadores existentes de `DuplicadoHighlighter.marcar` que **no deben
regresionar**: `PanelMateriales.tieneDuplicados()` (ortopedias, línea 117-126) y
`PanelMaterialesOtros.tieneDuplicados()` (otros, línea 118). La solución es agregar
un overload genérico y hacer que el overload de `JTextField` delegue en él —
firma pública existente intacta, cero cambios en esos dos archivos.

### Tareas

1. **`DuplicadoHighlighter.java`** — agregar el overload genérico e importar
   `javax.swing.JComponent`:
   ```java
   public static <T extends JComponent> boolean marcar(List<T> componentes,
                                                          Function<T, String> extractorValor,
                                                          Function<String, String> normalizador,
                                                          Color colorNormal,
                                                          String tooltipDuplicado) {
       List<String> valores = new ArrayList<>();
       for (T componente : componentes) {
           valores.add(normalizador.apply(extractorValor.apply(componente)));
       }
       Set<String> duplicados = Validador.detectarDuplicados(valores);
       for (int i = 0; i < componentes.size(); i++) {
           T componente = componentes.get(i);
           if (duplicados.contains(valores.get(i))) {
               componente.setBackground(COLOR_DUPLICADO);
               componente.setToolTipText(tooltipDuplicado);
           } else {
               componente.setBackground(colorNormal);
               componente.setToolTipText(null);
           }
       }
       return !duplicados.isEmpty();
   }
   ```
   Redefinir el overload existente de `JTextField` para que delegue (mismo
   comportamiento, cero cambios de firma pública):
   ```java
   public static boolean marcar(List<JTextField> campos,
                                 Function<String, String> normalizador,
                                 Color colorNormal,
                                 String tooltipDuplicado) {
       return marcar(campos, JTextField::getText, normalizador, colorNormal, tooltipDuplicado);
   }
   ```

2. **`PanelElementosClasificacion.java`**:
   - Importar `com.example.ui.common.DuplicadoHighlighter`,
     `java.util.function.Function`, `java.awt.event.ItemEvent`.
   - Agregar campo `private Color colorNormal = null;`.
   - En `agregarFila()`, justo después de la línea `cmb.setFont(...)` (línea 50,
     con `cmb` ya poblado y configurado), capturar el color y agregar el listener
     de actualización en vivo:
     ```java
     if (colorNormal == null) colorNormal = cmb.getBackground();
     cmb.addItemListener(e -> {
         if (e.getStateChange() == ItemEvent.SELECTED) tieneDuplicados();
     });
     ```
     **Importante:** el `ItemListener` solo dispara con un cambio de selección
     *posterior*. Como cada combo nuevo arranca en el índice 0 (primer elemento
     del catálogo) sin que el usuario lo toque, agregar una segunda fila puede
     crear un duplicado invisible hasta que alguien interactúe con algún combo.
     Para que el resaltado sea inmediato, llamar también `tieneDuplicados()` al
     final de `agregarFila()` (después de `reconstruirPanel()`, línea 64) y al
     final de `eliminarFila()` (después de `reconstruirPanel()`, línea 69) —
     ambos métodos ya tienen la lista `filas` actualizada en ese punto.
   - Agregar método público:
     ```java
     public boolean tieneDuplicados() {
         List<JComboBox<ElementoCatalogo>> combos = new ArrayList<>();
         for (ElementoFila f : filas) combos.add(f.cmbElemento);
         return DuplicadoHighlighter.marcar(
             combos,
             cmb -> {
                 Object sel = cmb.getSelectedItem();
                 return sel instanceof ElementoCatalogo e ? String.valueOf(e.getId()) : "";
             },
             Function.identity(),
             colorNormal,
             "Elemento duplicado: unifique estas filas antes de guardar.");
     }
     ```

3. **`ClasificacionController.java`** — en `guardar()`, justo después del chequeo
   `filas.isEmpty()` (línea 59-62) y antes de construir `elementos`:
   ```java
   if (panel.getPanelElementos().tieneDuplicados()) {
       panel.mostrarError("Hay elementos repetidos.\nUnifique las filas marcadas en rojo antes de guardar.");
       return;
   }
   ```

4. **Tests** — agregar a `DuplicadoHighlighterTest.java` casos nuevos para el
   overload genérico usando `JComboBox<String>` sin mostrar en pantalla (mismo
   patrón headless que ya usa el archivo con `JTextField`):
   - dos combos con la misma selección → ambos se marcan, `getBackground()`
     distinto de `NORMAL`, tooltip seteado.
   - combos con selección distinta → ninguno se marca.
   - combo con `setSelectedItem(null)` (o índice -1) → se ignora como "vacío",
     igual que un `JTextField` en blanco hoy.
   No hace falta tocar `PanelMaterialesTest`/`PanelMaterialesOtrosTest`: correrlos
   solo confirma que no hubo regresión (overload de 4 args intacto).

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test -Dtest=DuplicadoHighlighterTest,PanelMaterialesTest,PanelMaterialesOtrosTest`

### Criterio de salida
Elegir el mismo elemento en dos filas de Clasificación las resalta en rojo con
tooltip y bloquea Guardar; los dos llamadores existentes de `DuplicadoHighlighter`
siguen intactos y sus tests pasan. Commit:
`feat: resaltado de elementos duplicados en Clasificación Lavadero`.

### Rollback
`git revert` del commit del paso.

---

## Paso 3 — `RestriccionesCampo`: nuevo método decimal + aplicar en Lavadero

### Context brief
`RestriccionesCampo.soloNumeros(JTextField)` / `soloLetrasYEspacios(JTextField)`
(`src/main/java/com/example/ui/common/RestriccionesCampo.java:27-55`) son
`KeyAdapter`s que descartan teclas no válidas. Ningún archivo de
`features/lavadero/` los usa hoy. Los campos de texto libre candidatos (barrido
completo del árbol `lavadero/view` + `lavadero/view/helpers`):

| Campo | Archivo:línea | Uso | Restricción |
|---|---|---|---|
| `txtFiltroNumero` | `PantallaVerCiclos.java:66` | filtro "Lavarropas #", `Integer.parseInt` | `soloNumeros` (existe) |
| `txtLitrosJabon` | `LavarropasCard.java:35` | mL jabón, `BigDecimal` con `.replace(",", ".")` | **decimal — nuevo método** |
| `txtLitrosTotales` | `LavarropasCard.java:38` | mL totales, mismo parseo decimal | **decimal — nuevo método** |
| `txtCliente` | `PantallaIngresoLavadero.java:50` | búsqueda de cliente por nombre | **ninguna** — precedente: `PantallaIngresoOrtopedia.txtCliente` tampoco está restringido (es búsqueda libre, no dato validado) |

`soloNumeros` descarta comas y puntos, lo cual rompería la entrada decimal de
`txtLitrosJabon`/`txtLitrosTotales` (el parser hace `.replace(",", ".")` antes de
`new BigDecimal(...)`, `LavarropasCard.java:240-257`) — por eso hace falta un
método nuevo, no reusar `soloNumeros` ahí.

### Tareas

1. **`RestriccionesCampo.java`** — agregar:
   ```java
   /**
    * Restringe el campo a dígitos con hasta un separador decimal (coma o punto),
    * igual al formato que aceptan los parsers de litros de Lavadero (reemplazan
    * "," por "." antes de BigDecimal).
    */
   public static void soloNumerosDecimales(JTextField campo) {
       campo.addKeyListener(new KeyAdapter() {
           @Override
           public void keyTyped(KeyEvent e) {
               char c = e.getKeyChar();
               boolean esSeparador = c == ',' || c == '.';
               boolean yaTieneSeparador = campo.getText().indexOf(',') >= 0
                   || campo.getText().indexOf('.') >= 0;
               if (esSeparador && yaTieneSeparador) {
                   e.consume();
                   return;
               }
               if (!Character.isDigit(c) && !esSeparador && c != KeyEvent.VK_BACK_SPACE) {
                   e.consume();
               }
           }
       });
   }
   ```

2. **`LavarropasCard.java`** (ruta completa:
   `src/main/java/com/example/features/lavadero/view/LavarropasCard.java`, está
   en `view/`, no en `view/helpers/`) — importar
   `com.example.ui.common.RestriccionesCampo`;
   en el constructor, tras la configuración de `txtLitrosJabon`/`txtLitrosTotales`
   (cerca de línea 110-120), agregar:
   ```java
   RestriccionesCampo.soloNumerosDecimales(txtLitrosJabon);
   RestriccionesCampo.soloNumerosDecimales(txtLitrosTotales);
   ```

3. **`PantallaVerCiclos.java`** — importar `com.example.ui.common.RestriccionesCampo`;
   en `crearPanelFiltros()`, después de `txtFiltroNumero.setFont(...)` (línea 67):
   ```java
   RestriccionesCampo.soloNumeros(txtFiltroNumero);
   ```

4. **No tocar** `PantallaIngresoLavadero.txtCliente` — queda sin restricción a
   propósito (ver tabla arriba).

5. **Tests** — crear `src/test/java/com/example/ui/common/RestriccionesCampoTest.java`
   (no existe hoy ningún test de esta clase). Cubrir solo el método nuevo
   `soloNumerosDecimales` disparando `keyTyped` directamente sobre el
   `KeyListener` registrado (mismo patrón headless que el resto de la suite —
   no requiere mostrar el componente):
   ```java
   JTextField campo = new JTextField();
   RestriccionesCampo.soloNumerosDecimales(campo);
   KeyListener listener = campo.getKeyListeners()[0];
   KeyEvent evento = new KeyEvent(campo, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0,
       KeyEvent.VK_UNDEFINED, ',');
   listener.keyTyped(evento);
   assertFalse(evento.isConsumed()); // primera coma: permitida
   ```
   Casos: dígito permitido; primera coma permitida (campo vacío); segunda coma
   consumida — **requiere `campo.setText("1,")` antes de disparar el segundo
   evento**, porque el guard lee `campo.getText()` (el texto ya presente), no el
   carácter pendiente; letra consumida; backspace permitido.

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test -Dtest=RestriccionesCampoTest`

### Criterio de salida
`txtLitrosJabon`/`txtLitrosTotales` aceptan dígitos + un separador decimal;
`txtFiltroNumero` en Ver Ciclos solo acepta dígitos. Commit:
`feat: restricciones de teclado en campos numéricos de Lavadero`.

### Rollback
Revertir el commit. `soloNumerosDecimales` puede quedar sin llamadores sin romper
nada si se revierte parcialmente.

---

## Paso 4 — Filtro de estado + renderer propio en Ver Ciclos (paridad de filtrado con Ver Lotes)

**Depende del Paso 3** (ambos modifican `PantallaVerCiclos.java`; ejecutar en ese
orden para evitar conflictos de edición, no por acoplamiento de diseño).

### Context brief
Ver Lotes ya resuelve "mostrar activos junto a históricos" con: `CheckableComboBox`
multi-selección de estado + `LotesFilterCriteria`/`LotesFilterStrategy`
(`implements FilterStrategy<Lote, LotesFilterCriteria>`). Referencia completa:
`src/main/java/com/example/features/lotes/view/PantallaVerLotes.java` (campo
`cmbFiltroEstado` línea 38, instanciación línea 116, wiring línea 159, limpieza
línea 169, getter línea 231-233) y `VerLotesController.aplicarFiltros()`
(línea 58-70).

Ver Ciclos (Lavadero) **ya trae ciclos activos** en la consulta —
`CicloLavaderoDAO.SQL_TODOS` no tiene `WHERE` — así que no hay que tocar el DAO ni
el service. Lo que falta es pura paridad de UI: filtro de estado + columna
coloreada. `CicloLavadero` (`model/CicloLavadero.java`) solo tiene 2 estados
posibles en toda la base: `"ACTIVO"` / `"FINALIZADO"` (ver `getEstado()` y
`estaActivo() { return fechaFin == null; }`) — a diferencia de los 3 de Lotes.

**El renderer no se comparte (decisión de diseño #3):** en vez de mover/generalizar
`features/lotes/view/helpers/EstadoCellRenderer.java` (que queda intacto), se crea
una clase nueva e independiente en Lavadero con su propio mapa de colores.

`VerCiclosController.filtrar(...)` es hoy un método estático package-private
(`VerCiclosController.java:47-59`) con lógica de filtro inline, testeado
directamente por `VerCiclosControllerFiltrosTest` (9 casos). Se reemplaza por el
mismo patrón `FilterStrategy` que usa Lotes. **Este paso no toca todavía el
constructor ni el campo `cache` de `VerCiclosController`** — eso es el Paso 5.

### Tareas

1. **Crear** `src/main/java/com/example/features/lavadero/view/helpers/CicloEstadoCellRenderer.java`
   (no toca `features/lotes/view/helpers/EstadoCellRenderer.java`):
   ```java
   package com.example.features.lavadero.view.helpers;

   import javax.swing.JTable;
   import javax.swing.table.DefaultTableCellRenderer;
   import java.awt.Color;
   import java.awt.Component;

   public class CicloEstadoCellRenderer extends DefaultTableCellRenderer {

       @Override
       public Component getTableCellRendererComponent(JTable table, Object value,
                                                        boolean isSelected, boolean hasFocus,
                                                        int row, int column) {
           Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

           if (value != null && !isSelected) {
               String estado = value.toString().trim().toUpperCase();
               switch (estado) {
                   case "ACTIVO":
                       c.setBackground(new Color(173, 216, 230)); // Azul claro
                       break;
                   case "FINALIZADO":
                       c.setBackground(new Color(211, 211, 211)); // Gris claro
                       break;
                   default:
                       c.setBackground(Color.WHITE);
                       break;
               }
           } else if (isSelected) {
               c.setBackground(table.getSelectionBackground());
           }

           setHorizontalAlignment(CENTER);
           return c;
       }
   }
   ```

2. **Crear** `src/main/java/com/example/features/lavadero/controller/helpers/CicloFilterCriteria.java`:
   ```java
   package com.example.features.lavadero.controller.helpers;

   import java.time.LocalDate;
   import java.util.Collections;
   import java.util.List;

   public class CicloFilterCriteria {
       private final Integer     numeroLavarropas;
       private final List<String> estados;
       private final LocalDate    fechaDesde;
       private final LocalDate    fechaHasta;

       public CicloFilterCriteria(Integer numeroLavarropas, List<String> estados,
                                   LocalDate fechaDesde, LocalDate fechaHasta) {
           this.numeroLavarropas = numeroLavarropas;
           this.estados          = estados != null ? estados : Collections.emptyList();
           this.fechaDesde       = fechaDesde;
           this.fechaHasta       = fechaHasta;
       }

       public Integer     getNumeroLavarropas() { return numeroLavarropas; }
       public List<String> getEstados()         { return estados; }
       public LocalDate    getFechaDesde()      { return fechaDesde; }
       public LocalDate    getFechaHasta()      { return fechaHasta; }
   }
   ```

3. **Crear** `src/main/java/com/example/features/lavadero/controller/helpers/CicloFilterStrategy.java`:
   ```java
   package com.example.features.lavadero.controller.helpers;

   import com.example.common.util.FilterStrategy;
   import com.example.features.lavadero.model.CicloLavadero;

   import java.time.LocalDate;
   import java.util.List;
   import java.util.stream.Collectors;

   public class CicloFilterStrategy implements FilterStrategy<CicloLavadero, CicloFilterCriteria> {

       @Override
       public List<CicloLavadero> filter(List<CicloLavadero> source, CicloFilterCriteria criteria) {
           if (source == null || source.isEmpty()) return List.of();
           return source.stream()
               .filter(c -> cumpleNumero(c, criteria.getNumeroLavarropas()))
               .filter(c -> cumpleEstado(c, criteria.getEstados()))
               .filter(c -> cumpleFechas(c, criteria.getFechaDesde(), criteria.getFechaHasta()))
               .collect(Collectors.toList());
       }

       private boolean cumpleNumero(CicloLavadero c, Integer numero) {
           return numero == null || c.getLavarropasNumero() == numero;
       }

       private boolean cumpleEstado(CicloLavadero c, List<String> estados) {
           return estados.isEmpty() || estados.stream().anyMatch(e -> e.equalsIgnoreCase(c.getEstado()));
       }

       /** Los ciclos activos no tienen fecha de fin: siempre pasan el filtro de fecha (igual que hoy). */
       private boolean cumpleFechas(CicloLavadero c, LocalDate desde, LocalDate hasta) {
           if (c.estaActivo()) return true;
           LocalDate fechaFin = c.getFechaFin().toLocalDate();
           if (desde != null && fechaFin.isBefore(desde)) return false;
           if (hasta != null && fechaFin.isAfter(hasta))  return false;
           return true;
       }
   }
   ```
   Nota: `cumpleNumero`/`cumpleFechas` preservan **exactamente** el comportamiento
   actual de `VerCiclosController.filtrar` (los 9 tests existentes deben seguir
   pasando una vez migrados, ver tarea 6). `cumpleEstado` es la lógica nueva.

4. **`VerCiclosController.java`** — cambios acotados a filtrado, **sin tocar
   todavía** constructor/campo `cache` (eso es el Paso 5):
   - Quitar el método estático `filtrar(...)` (su lógica se mudó al paso 3).
   - Agregar campo:
     `private final FilterStrategy<CicloLavadero, CicloFilterCriteria> filterStrategy = new CicloFilterStrategy();`
   - Reescribir `aplicarFiltros()` (sigue `private` en este paso; el Paso 5 lo
     vuelve `protected @Override`):
     ```java
     private void aplicarFiltros() {
         CicloFilterCriteria criteria = new CicloFilterCriteria(
             pantalla.getFiltroNumero(),
             pantalla.getFiltroEstados(),
             pantalla.getFiltroFechaDesde(),
             pantalla.getFiltroFechaHasta()
         );
         pantalla.actualizarCiclos(filterStrategy.filter(cache, criteria));
     }
     ```
   - Importar `com.example.common.util.FilterStrategy`,
     `com.example.features.lavadero.controller.helpers.CicloFilterCriteria`,
     `com.example.features.lavadero.controller.helpers.CicloFilterStrategy`.
   - Quitar del archivo los imports que queden sin uso al borrar `filtrar(...)`
     (`java.time.LocalDate` y `java.util.stream.Collectors` — su lógica se mudó a
     `CicloFilterStrategy`, que ya los importa por su cuenta).

5. **`PantallaVerCiclos.java`**:
   - Importar `com.example.ui.common.CheckableComboBox`,
     `com.example.features.lavadero.view.helpers.CicloEstadoCellRenderer`.
   - Agregar campo `private CheckableComboBox<String> cmbFiltroEstado;`.
   - En `crearPanelFiltros()`, después del bloque de `lblNumero`/`txtFiltroNumero`:
     ```java
     JLabel lblEstado = new JLabel(Constantes.Textos.FILTRO_ESTADO);
     lblEstado.setFont(Estilos.Fuentes.LABEL);
     cmbFiltroEstado = new CheckableComboBox<>(new String[]{"ACTIVO", "FINALIZADO"});
     cmbFiltroEstado.setFont(Estilos.Fuentes.INPUT);
     cmbFiltroEstado.setPreferredSize(new Dimension(130, 25));
     ```
     Agregarlo a `fila` (antes de `lblDesde`, mismo orden que Ver Lotes:
     dato/estado, luego fechas): `fila.add(lblEstado); fila.add(cmbFiltroEstado);`.
     Vincular cambio: `cmbFiltroEstado.setOnSelectionChange(this::notificarCambio);`.
   - En `limpiarFiltros()`, agregar `cmbFiltroEstado.clearSelection();` antes de
     `notificarCambio()`.
   - Agregar getter: `public List<String> getFiltroEstados() { return cmbFiltroEstado.getSelectedItems(); }`.
   - En `modeloTabla`, agregar `"Estado"` al final del array de nombres de columna
     (índice 9). En `actualizarCiclos()`, agregar `c.getEstado()` al final del
     `Object[]` de cada fila.
   - En el constructor, después de `TableStyler.centerColumns(...)` (línea 56),
     agregar: `tablaCiclos.getColumnModel().getColumn(9).setCellRenderer(new CicloEstadoCellRenderer());`.

6. **Tests** — mover
   `src/test/java/com/example/features/lavadero/controller/VerCiclosControllerFiltrosTest.java`
   → `src/test/java/com/example/features/lavadero/controller/helpers/CicloFilterStrategyTest.java`.
   Esto es un `git mv` + edición, no una copia literal: además de cambiar la
   ubicación del archivo hay que actualizar **dos cosas dentro de él**:
   - línea 1: `package com.example.features.lavadero.controller;` →
     `package com.example.features.lavadero.controller.helpers;`
   - línea 15: `public class VerCiclosControllerFiltrosTest` →
     `public class CicloFilterStrategyTest`
   (si el nombre de clase pública no coincide con el nombre de archivo, no
   compila). Portar los 9 casos existentes para instanciar
   `new CicloFilterStrategy().filter(ciclos, new CicloFilterCriteria(numero, List.of(), desde, hasta))`
   en vez de `VerCiclosController.filtrar(...)` (los helpers `ciclo(...)`/`cicloActivo(...)`
   no cambian). Agregar 3 casos nuevos para `cumpleEstado`:
   - `filtroEstadoActivoDevuelveSoloActivos()`
   - `filtroEstadoFinalizadoExcluyeActivos()`
   - `filtroEstadoIgnoraMayusculasMinusculas()` (o equivalente, cubriendo
     `equalsIgnoreCase`)

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test -Dtest=CicloFilterStrategyTest`

### Criterio de salida
Ver Ciclos filtra por estado igual que Ver Lotes (multi-selección, vacío = todos);
la tabla colorea ACTIVO (celeste) / FINALIZADO (gris) con un renderer propio de
Lavadero; `features/lotes/**` no fue tocado; `VerCiclosController.filtrar` ya no
existe (código muerto eliminado); suite completa en verde. Commit:
`feat: filtro de estado y color-coding en Ver Ciclos, paridad de filtrado con Ver Lotes`.

### Rollback
`git revert` del commit del paso — no hay archivos de otra feature involucrados.

---

## Paso 5 — Paridad de arquitectura de refresco (`AbstractFilterController` + `UiCoordinator`)

**Depende del Paso 4** (reescribe `VerCiclosController.java`, ya con el filtrado
del Paso 4 en su lugar; también se apoya en el contexto de `Disparador`/
`RefrescadorPantallas` descripto en "Contexto arquitectónico común").

### Context brief
`VerLotesController` no busca sus propios datos: recibe un `Runnable
solicitarRefresco` (en la práctica, un `Disparador` de `UiCoordinator`) y expone
`pintar(HistorialLotes)`, que `RefrescadorPantallas` invoca después de leer la BD
en un hilo de fondo. `VerCiclosController` hoy hace exactamente lo contrario: recibe
`CicloLavaderoService` y lee la BD **de forma síncrona en el EDT** en tres lugares
(constructor, su propio `componentShown`, y una llamada redundante en el botón
"Ver Ciclos" — `UiCoordinator.java:196-199`, redundante porque `show(...)` ya
dispara `componentShown`). Ese es el gap real de "paridad completa".

`PantallaVerCiclos` no tiene ningún combo con datos externos que poblar (a
diferencia del combo de autoclaves de Ver Lotes) — por eso **no hace falta** un
DTO tipo `HistorialLotes` ni una clase `LectorHistorialCiclos`: el snapshot es
directamente `List<CicloLavadero>`, y `RefrescadorPantallas<T>` es genérico, así
que `RefrescadorPantallas<List<CicloLavadero>>` funciona sin tipos nuevos.

### Tareas

1. **`VerCiclosController.java`** — reescritura:
   - Cambiar la declaración de clase a
     `public class VerCiclosController extends AbstractFilterController<CicloLavadero> {`
   - Quitar el campo `private List<CicloLavadero> cache = List.of();` (lo maneja
     la superclase) y el campo `CicloLavaderoService cicloLavaderoService`.
   - Reemplazar el constructor completo:
     ```java
     public VerCiclosController(PantallaVerCiclos pantalla, Runnable solicitarRefresco) {
         this.pantalla = pantalla;
         Objects.requireNonNull(solicitarRefresco, "solicitarRefresco");

         pantalla.setOnFiltrosChanged(this::aplicarFiltros);
         pantalla.setOnLimpiar(pantalla::limpiarFiltros);

         pantalla.addComponentListener(new ComponentAdapter() {
             @Override public void componentShown(ComponentEvent e) { solicitarRefresco.run(); }
         });
     }
     ```
     (Se elimina la llamada a `cargarDatos()` que hoy tiene el constructor — la
     pantalla arranca vacía hasta el primer refresco, igual que Ver Lotes; ver el
     comentario en `UiCoordinator.java:206-209` sobre por qué las pantallas de
     consulta ya no leen en su constructor.)
   - Eliminar el método `cargarDatos()` (código muerto tras este cambio:
     `pintar(...)` + `recargarCache(...)` heredado lo reemplazan).
   - Agregar:
     ```java
     public void pintar(List<CicloLavadero> ciclos) {
         recargarCache(ciclos);
     }
     ```
   - `aplicarFiltros()` pasa a `protected @Override` (la superclase lo declara
     `protected abstract`) y usa `getCache()` en vez del campo `cache` que ya no
     existe:
     ```java
     @Override
     protected void aplicarFiltros() {
         CicloFilterCriteria criteria = new CicloFilterCriteria(
             pantalla.getFiltroNumero(),
             pantalla.getFiltroEstados(),
             pantalla.getFiltroFechaDesde(),
             pantalla.getFiltroFechaHasta()
         );
         pantalla.actualizarCiclos(filterStrategy.filter(getCache(), criteria));
     }
     ```
   - Importar `com.example.common.util.AbstractFilterController`,
     `java.awt.event.ComponentAdapter`, `java.awt.event.ComponentEvent`,
     `java.util.Objects` (los que falten — `ComponentAdapter`/`ComponentEvent` es
     probable que ya estén, confirmar antes de duplicar el import).

2. **`UiCoordinator.java`**:
   - Junto a la declaración de los otros `Disparador` (línea 64-66), agregar un
     cuarto grupo:
     ```java
     Disparador historialCiclos  = new Disparador();
     ```
     Actualizar el comentario de línea 58-63 ("Son tres grupos con disparadores
     distintos") a cuatro, agregando la línea
     `· historial ciclos → al abrir "Ver Ciclos".`
   - Cambiar la construcción de `VerCiclosController` (línea 193-194):
     ```java
     VerCiclosController verCiclosController = new VerCiclosController(
         vista.getPantallaVerCiclos(), historialCiclos);
     ```
   - Simplificar el listener del botón "Ver Ciclos" (línea 196-199), quitando la
     llamada ahora redundante a `cargarDatos()`:
     ```java
     vista.getPantallaLavadero().getBtnVerCiclos().addActionListener(e ->
         vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.VER_CICLOS_LAVADERO));
     ```
   - Cerca de línea 133 (donde se cablean los otros grupos), agregar:
     ```java
     historialCiclos.cablear(crearRefrescadorHistorialCiclos(verCiclosController));
     ```
   - Agregar el método factory, junto a `crearRefrescadorHistorialLotes`
     (línea 254-264):
     ```java
     /** La pantalla que consulta el histórico de ciclos de lavado. */
     private RefrescadorPantallas<List<CicloLavadero>> crearRefrescadorHistorialCiclos(
         VerCiclosController verCiclos
     ) {
         return new RefrescadorPantallas<>(
             "refresco-historial-ciclos",
             context.getCicloLavaderoService()::obtenerTodosLosCiclos,
             verCiclos::pintar,
             this::mostrarErrorDeRefresco);
     }
     ```
     Sin DTO nuevo: el `Supplier` es directamente la referencia al método del
     service, que ya devuelve `List<CicloLavadero>`.
   - Importar `com.example.features.lavadero.model.CicloLavadero` y
     `java.util.List` si no están ya en `UiCoordinator.java`.

3. **Verificación de impacto en tests** — confirmado por búsqueda en todo el
   repo: el único lugar que instancia `VerCiclosController` es `UiCoordinator.java`
   (ningún test lo construye directamente), y `CicloFilterStrategyTest` (Paso 4)
   solo ejercita `CicloFilterStrategy`, no el controller — este paso no requiere
   tocar ningún test adicional más allá de lo ya hecho en el Paso 4.

### Verificación
- `mvn -q -DskipTests compile`
- `mvn -q test` (suite **completa**, no solo lavadero — este paso toca la
  composition root de la app entera)
- Smoke manual obligatorio (un error de cableado acá no lo agarra JUnit, no existe
  un test de arranque de `UiCoordinator`): correr la app (`mvn clean package` y
  ejecutar el jar, o `mvn exec:java` si está configurado) y confirmar que arranca
  sin diálogo de error. Ir a Lavadero → Ver Ciclos: la tabla se puebla (puede haber
  un parpadeo breve de tabla vacía — la lectura ahora es asíncrona con debounce de
  150ms, a diferencia del bloqueo síncrono anterior). Abrir/cerrar la pantalla
  varias veces seguidas no debe mezclar ni duplicar resultados.

### Criterio de salida
`VerCiclosController` extiende `AbstractFilterController<CicloLavadero>` y se
refresca a través del mismo mecanismo `Disparador`/`RefrescadorPantallas` que
`VerLotesController` — arquitectura idéntica, no solo resultado equivalente. La
lectura a la base ya no bloquea el EDT. La app arranca y navega sin errores.
Commit: `refactor: paridad de arquitectura de refresco entre Ver Ciclos y Ver Lotes`.

### Rollback
`git revert` del commit del paso. Si algo del cableado en `UiCoordinator.java`
queda mal, la app puede no arrancar — por eso este paso exige correr la suite
completa **y** un arranque real antes de darlo por cerrado, no alcanza con
`mvn test`.

---

## Paso 6 — Verificación final

### Context brief
Los pasos 1-5 ya verifican su propio alcance al cerrar (el Paso 5 en particular ya
exige un arranque real de la app). Este paso corre el build completo una vez más
sobre el resultado acumulado y deja una checklist de humo manual — la app es Swing
de escritorio, no hay forma de automatizar la verificación visual desde este
entorno de agente.

### Tareas
1. `mvn clean package` (compila + corre toda la suite + genera `target/aptium.jar`).
2. Entregar al usuario esta checklist de verificación manual:
   - **Ingreso Lavadero:** Ctrl++ agrega una fila de bolsa, Ctrl+- quita la última.
   - **Clasificación Lavadero:** elegir el mismo elemento en dos filas → ambos
     combos se resaltan en rojo con tooltip; "Guardar" queda bloqueado con mensaje
     hasta corregir la duplicación.
   - **LavarropasCard (pantalla de Ciclos):** "mL Jabón"/"mL Tot." aceptan un
     separador decimal (coma o punto) y rechazan letras o un segundo separador.
   - **Ver Ciclos:** aparece la columna "Estado" coloreada (celeste ACTIVO / gris
     FINALIZADO); el combo de filtro de estado (multi-selección, "Todos" por
     defecto) funciona igual que en Ver Lotes; los ciclos activos siguen
     apareciendo aunque haya un filtro de fecha aplicado; la pantalla se puebla al
     abrirla (puede haber un parpadeo breve, es normal — lectura asíncrona nueva).
   - **Arranque general de la app:** ninguna otra pantalla (Ver Lotes, Ver Equipos,
     Estado de Procesos) quedó afectada por el cambio en `UiCoordinator.java`.

### Verificación
- `mvn clean package` en verde (compilación + suite completa + empaquetado).

### Criterio de salida
Jar generado en `target/aptium.jar`, suite completa en verde, checklist manual
entregada al usuario para que la corra en su entorno gráfico.

### Rollback
N/A — paso de solo verificación, no modifica código.
