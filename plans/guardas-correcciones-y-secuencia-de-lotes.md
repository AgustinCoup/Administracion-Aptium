# Guardas de Correcciones, secuencia de lotes y ABM alcanzables (hallazgo #9)

Cierra los tres huecos que dejó abiertos [`bloqueo-optimista-concurrencia.md`](bloqueo-optimista-concurrencia.md),
anotados como **#9** en [`hallazgos-arquitectura-pendientes.md`](hallazgos-arquitectura-pendientes.md).

**Estado: plan escrito y revisado contra el código el 2026-09-04, sin ejecutar.** Pensado para
correrse en un chat limpio: cada paso es autocontenido y lleva su propio commit.

**Tres rondas de revisión adversarial cerradas.** Lo que sobrevivió y por qué importa, para que nadie
lo "simplifique" al ejecutar:

| Dónde | Qué se corrigió |
|---|---|
| Paso 7 | La discriminación de la clase `23` va **fuera** de la transacción fallida. Adentro lee el snapshot viejo y el reintento no se dispara nunca en MySQL — y H2 no lo delata. |
| Paso 7 | Clase `23` en el `INSERT INTO lotes` también la produce la FK a `autoclaves`, no sólo el `UNIQUE`. |
| Paso 1 | Lo que limpia la selección es `limpiarPantalla()` en el `pintar`, **no** `fireTableDataChanged()`. La rama viva es `sel == null`; `tipo` + `id` es invariante barata, no el mecanismo. |
| Pasos 3 y 4 | Tres rutas commiteaban un bump en el camino de 0 filas, no dos; y las dos de `DELETE` por clave necesitan su propio `exigirFilaAfectada` o el Paso 5 escribe auditoría de una eliminación que no ocurrió. |
| Paso 2 | El helper de "otros" escribe `equipo_otros`, no `equipos`. |
| Paso 3 | `MaterialDAO.actualizarCantidad`/`actualizarCodigo` no scopean por `equipo_id`; con la firma cambiando, sale gratis. |
| Paso 11 | `EquipoDAO.actualizar` **no** tenía un javadoc falso: la cadena entera está muerta. |

---

## Decisiones cerradas (2026-09-04)

| Tema | Decisión | Por qué |
|---|---|---|
| Guarda de Correcciones | **`version` del agregado** (`AND version = ?`) | Corrección **reemplaza** el valor entero desde un snapshot, no consume por cantidad. Es el consumidor previsto de la V21. |
| Origen de la `version` | **La del equipo seleccionado en la lista, verificando que siga siendo el mismo equipo** | Es la que el operador realmente vio. La verificación no es opcional — ver "De dónde sale la `version`, y qué la protege". |
| Plomería | **El controller lee el equipo seleccionado y saca de ahí la `version`** | Los callbacks de `PantallaCorrecciones` no cambian; la view expone el equipo, no una `version`, así que sigue sin saber de concurrencia. |
| Validación de `NUEVO` | **Queda como pre-chequeo de usabilidad, no como guarda** | Toda transición que saca un equipo de `NUEVO` pasa por `recalcularEstadoEquipo`, que bumpea `version`: la guarda ya lo cubre. Un `FOR UPDATE` ahí no serviría — ese re-read abre su propia conexión y suelta el lock antes del `UPDATE`. |
| Orden auditoría ↔ `DELETE` | **Dato primero, auditoría después**, y un fallo de auditoría **no** se reporta como "no se eliminó" | Alinea con la sutileza #1 de [`sacar-sql-de-equipo-otros-correccion-service.md`](sacar-sql-de-equipo-otros-correccion-service.md): el dato no es rehén de la auditoría. Ver el Paso 5. |
| Secuencia de lotes | **Reintento sobre la violación de UNIQUE**, discriminada **fuera** de la transacción fallida | Sin cambio de esquema. La transacción de `lanzarLote` no deja nada committeado, así que el reintento arranca limpio. La discriminación va afuera porque adentro leería el snapshot viejo — ver el Paso 7. |
| ABM | **Sólo las dos rutas alcanzables**: `eliminarCliente` y `fusionarClientes` | Ver la medición de la Parte C: el resto es `INSERT`, upsert sin llamadores, o código muerto. |
| Conflicto en fusión | **Que cambie el nombre de origen o destino** | Es lo que el operador eligió en los dos combos. |

### Lo que este plan cambia de la doctrina escrita

`CLAUDE.md` dice hoy: *"`equipos` y `equipo_otros` sí tienen `version` (V21), se mantiene, y **NO**
se usa como guarda"*. Este plan **la activa**, sólo en las rutas de `Correcciones`. La distinción
que hay que preservar al documentarlo:

- **Registrar Estado sigue guardando por `estado` del material.** Ahí la `version` sería un falso
  positivo: dos operadores avanzando materiales distintos del mismo equipo no se pisan en nada.
- **Correcciones guarda por `version` del agregado.** Ahí el falso positivo es **aceptado a
  propósito**: dos operadores corrigiendo materiales distintos del mismo equipo chocan. Se acepta
  porque Correcciones es una pantalla de excepción, de uso esporádico y auditado, donde "otro tocó
  este equipo mientras lo mirabas" es información que el operador quiere ver — no ruido.

Esa asimetría es el corazón del cambio y tiene que quedar escrita, o el próximo lector la lee como
una inconsistencia.

---

## De dónde sale la `version`, y qué la protege (leer antes del Paso 1)

La versión anterior de este plan decía "el controller lee `panel.getVersionEquipoSeleccionado()`
antes de llamar al service". Eso **no alcanza**: entre que la view captura el equipo y que la
operación llega al controller hay diálogos modales, y **un modal bombea la cola del EDT**, así que un
`pintar` de `cargarEquiposNuevos` que estaba en vuelo puede correr con el diálogo abierto.
`PantallaCorrecciones.solicitarEliminacion` (`:476-488`) captura el equipo y **después** abre el
`showInputDialog` del motivo; `CorreccionesController.eliminarEquipo` abre además un
`showConfirmDialog`.

**Qué pasa hoy exactamente cuando ese refresco corre** — no lo que decía la versión anterior de este
plan. `fireTableDataChanged()` **no** limpia la selección de la `JTable`: construye un
`TableModelEvent` con `firstRow = 0`, y `JTable.tableChanged` llama a `clearSelectionAndLeadAnchor()`
sólo cuando `firstRow == HEADER_ROW`, que es lo que emite `fireTableStructureChanged()`. Lo que sí la
limpia es **una línea del `pintar`**: `CorreccionesController:161` llama a `panel.limpiarPantalla()`
→ `limpiarFormulario()` → `panelTablas.limpiarSeleccion()`. Así que hoy, tras un refresco, la
selección queda en `null` y lo que dispara es la rama **`sel == null`**. Ésa es la rama viva y es la
que hay que documentar en el código.

**Por qué el chequeo de `tipo` + `id` se pone igual.** Porque lo que sostiene el párrafo anterior no
es una garantía de Swing: es esa línea de `limpiarPantalla()`. Sacarla es un pedido de UX
perfectamente plausible ("no me borres la selección cuando refresca"), y el día que alguien la saque,
`PanelEquipoMaterial.getEquipoSeleccionado` (`:109-112`) resuelve por **índice de fila** contra el
modelo vivo y `EquipoTableModel.actualizarDatos` (`:51-79`) **reordena por estado**, así que el mismo
índice pasa a apuntar a otro equipo aunque la lista traiga exactamente los mismos. Ahí:

- las `version` arrancan todas en `0` (V21 las crea `NOT NULL DEFAULT 0`), así que la del equipo
  equivocado es muy probable que **coincida** con la del destino, y la guarda pasaría en falso;
- y comparar sólo el `id` tampoco cerraría: la grilla mezcla los dos tipos
  (`CorreccionesController:153-154` concatena las dos listas) y `equipos` / `equipo_otros` tienen
  `AUTO_INCREMENT` **independientes**, así que `Equipo#7` y `EquipoOtros#7` conviven y el chequeo
  devolvería la `version` de la fila de la otra tabla.

Los dos términos juntos sí son identidad completa: el `id` es PK dentro de cada tabla y cada equipo
aparece una sola vez en la lista. **Cuestan un `&&`**, y convierten un invariante que hoy sostiene
una línea removible en uno que sostiene el compilador. Eso es de otra especie que la defensa que la
Parte C descarta: allá se trata de blindar métodos **sin ninguna ruta de llamada**; acá la ruta se
ejecuta en cada corrección.

**Regla que sale de esto, y que vale para las diez rutas:** la `version` y el `equipoId` tienen que
venir del **mismo objeto**, y el controller tiene que verificar `tipo` **y** `id` contra el equipo
que la operación va a tocar. Si la selección se perdió o cambió, eso **es** un conflicto: el
snapshot que el operador estaba mirando ya no existe.

---

## Estado del terreno (verificado el 2026-09-04)

Lo que **ya está** y no hay que construir:

- `equipos.version` y `equipo_otros.version` (V21), leídas en `EquipoDAO:242` y `EquipoOtrosDAO:835`,
  expuestas por `Equipo.getVersion()` / `EquipoOtros.getVersion()`. Las dos listas de Correcciones
  las traen: `obtenerEquiposNuevos` de ortopedias pasa por `obtenerEquiposConJoin` →
  `mapearEquipoBase`, y la de "otros" por `listar` → `mapearEquipo`.
- El bump **no tiene agujeros nuevos**. Verificado con
  `grep -rn "UPDATE equipos\|UPDATE equipo_otros " src/main/java`: los únicos `UPDATE` que no
  bumpean son los dos de `FusionClientesDAO` (**los cierra el Paso 9**), el `SET remito_id` de
  `EquipoOtrosDAO:164` (misma transacción que el `INSERT` que crea la fila) y el
  `SET volumen_equipo` de `LoteDAO:956` (cubierto por atomicidad). Coincide con la auditoría del
  javadoc de `EquipoOtrosMaterialHelper.recalcularEstadoEquipo`.
- `ControlConcurrencia.exigirFilaAfectada(int, String)` y `ConflictoConcurrenciaException`.
- `TransactionalConnection.close()` hace rollback ante `RuntimeException`, así que un
  `exigirFilaAfectada` que lanza adentro de la transacción la revierte entera.
- `CorreccionesController.aplicarCorreccion` ya recarga la lista (`cargarEquiposNuevos()`) tras cada
  éxito, así que el snapshot de `version` de la pantalla se refresca solo. **Sin esto el plan no
  cerraría**: dos correcciones seguidas sobre el mismo equipo chocarían contra sí mismas.
- `PanelEquipoMaterial.getEquipoSeleccionado()` resuelve contra el modelo vivo, no contra un campo
  retenido: después de una recarga devuelve la instancia nueva, con la `version` fresca.
- `DatabaseInitializer` ya aborta si la base está adelantada respecto del JAR.
- `UNIQUE (id_negocio)` existe desde `V1__baseline.sql:50`.
- `ConcurrenciaOptimistaTest` tiene hoy 8 casos con la forma que este plan reusa.

Jerarquía de excepciones, verificada porque el Paso 6 depende de ella:

```
ApplicationException (RuntimeException)
├── BusinessException → ConflictoConcurrenciaException
├── DatabaseException → ReferentialIntegrityException, EsquemaDesactualizadoException
├── ValidationException
└── ResourceNotFoundException
```

`ValidationException` **no** es `BusinessException`, así que poner la rama de `BusinessException`
primero en `describirError` no tapa la de validación.

Lo que **falta** y por eso hay pasos:

- `EquipoRegistrableInterface` no expone `getVersion()`.
- `PantallaCorrecciones` no tiene accesor público del equipo seleccionado (lo usa internamente,
  `panelTablas.getEquipoSeleccionado()`).
- `CorreccionesController.describirError` no ramifica por `BusinessException`: hoy un conflicto se
  mostraría como *"Error inesperado: …"*.
- `aplicarCorreccion` no recarga la lista cuando falla.
- `SimpleEntityDAO.esViolacionDeIntegridad` (`:147`) es **privado**: el Paso 7 no puede reusarlo tal
  cual.

---

## Parte A — Correcciones

Diez operaciones, cinco de ortopedias y cinco de "otros" — una por callback de
`PantallaCorrecciones` (`setOnModificarCantidad`, `setOnModificarCodigo`, `setOnAgregarMaterial`,
`setOnEliminarEquipo`, `setOnEliminarMaterial`, y los cinco gemelos de "otros"). Todas siguen la
misma forma:

```java
// dentro de la transacción de la escritura, ANTES de tocar el detalle:
UPDATE equipos SET version = version + 1 WHERE id = ? AND version = ?
ControlConcurrencia.exigirFilaAfectada(filas, Mensajes.CONFLICTO_CORRECCION);
// … recién ahora el UPDATE/DELETE/INSERT del material
```

**El bump guardado va primero, no último.** Toma el lock de la fila de `equipos` al principio, así
que el segundo operador se bloquea ahí y no hace trabajo que va a descartar. Con la guarda al final
el resultado sería igual de correcto, pero con más escrituras tiradas.

**Cuando la fila del agregado se borra, el `DELETE` es la guarda.** `eliminarEquipo` no bumpea nada:
`DELETE FROM equipos WHERE id = ? AND version = ?` es CAS de una sola sentencia.

### Paso 1 — Plomería y mensaje

1. `EquipoRegistrableInterface`: agregar `int getVersion();`. Las dos implementaciones ya lo tienen
   (`Equipo:234`, `EquipoOtros:253`), así que no hay cambio de comportamiento.
2. `PantallaCorrecciones`: accesor público **del equipo**, no de la version:

   ```java
   /** Equipo seleccionado en la grilla, o {@code null} si la selección se perdió. */
   public EquipoRegistrableInterface getEquipoSeleccionado() {
       return panelTablas.getEquipoSeleccionado();
   }
   ```

   Devolver el equipo y no un `int` es deliberado: la view sigue sin saber qué es una guarda, y el
   controller queda con el objeto entero, que es lo que necesita para verificar identidad.
   **`null` es alcanzable** (`limpiarPantalla()` llama a `panelTablas.limpiarSeleccion()`), así que
   no alcanza con documentar la precondición: hay que manejarlo.
3. `CorreccionesController`: helper privado, **la única puerta** por la que la `version` entra a las
   diez operaciones:

   ```java
   /**
    * Version del agregado que el operador tenía a la vista, para usar como guarda, o vacío si la
    * selección se perdió o pasó a ser otro equipo.
    *
    * <p><b>La rama que se dispara hoy es {@code sel == null}.</b> Entre que la view captura el
    * equipo y que la operación llega acá hay diálogos modales, y un modal bombea la cola del EDT:
    * un {@code pintar} de {@code cargarEquiposNuevos} en vuelo puede correr con el diálogo
    * abierto, y termina en {@code panel.limpiarPantalla()}, que limpia la selección. El operador
    * confirma sobre un snapshot que ya no existe, y eso es un conflicto.
    *
    * <p><b>Por qué igual se comparan {@code tipo} e {@code id}.</b> Que la selección quede en
    * {@code null} lo sostiene esa llamada a {@code limpiarPantalla()}, no Swing:
    * {@code fireTableDataChanged()} <b>no</b> limpia la selección de la {@code JTable}. Si algún
    * día se deja de limpiar, la selección sobrevive por <b>índice de fila</b> mientras
    * {@code EquipoTableModel.actualizarDatos} reordena por estado, y devolvería otro equipo. Las
    * {@code version} arrancan todas en {@code 0}, así que la del equipo equivocado es muy probable
    * que coincida; y el {@code id} solo tampoco alcanza, porque la grilla mezcla ortopedias y
    * "otros" y las dos tablas tienen {@code AUTO_INCREMENT} independientes ({@code Equipo#7} y
    * {@code EquipoOtros#7} conviven). Los dos términos juntos son identidad completa y cuestan un
    * {@code &&}: no los saque por parecer redundantes hoy.
    *
    * <p>Devuelve vacío en vez de lanzar porque el llamador corre en el EDT desde un
    * {@code ActionListener}: una excepción acá subiría al manejador del EDT y el operador no
    * vería nada. El early return con {@link #avisarSnapshotPerdido()} sí le muestra el cartel.
    */
   private OptionalInt versionDelEquipoAlaVista(TipoEquipo tipo, Integer equipoId) {
       EquipoRegistrableInterface sel = panel.getEquipoSeleccionado();
       return (sel != null && sel.getTipo() == tipo && Objects.equals(sel.getId(), equipoId))
           ? OptionalInt.of(sel.getVersion())
           : OptionalInt.empty();
   }

   /** Mismo cartel y misma recarga que un conflicto de base: para el operador es lo mismo. */
   private void avisarSnapshotPerdido() {
       panel.mostrarError(Constantes.Mensajes.CONFLICTO_CORRECCION);
       cargarEquiposNuevos();
   }
   ```

4. `Constantes.Mensajes`: agregar en el bloque *"Conflictos de concurrencia"*, con el mismo tono que
   los seis existentes (qué cambió + qué hacer):

   ```java
   public static final String CONFLICTO_CORRECCION =
       "Otro usuario modificó este equipo mientras preparabas la corrección.\n"
           + "La corrección no se aplicó. La pantalla se actualizó: revisá el equipo y volvé a corregirlo.";
   ```

**Commit:** `feat: plomería de version para las guardas de Correcciones`

### Paso 2 — Los dos helpers de bump guardado

Uno en cada helper, junto a su `bumpVersion`. **Son dos métodos con la misma forma y tablas
distintas** — el helper de ortopedias escribe `equipos`, el de "otros" escribe `equipo_otros`
(`EquipoOtrosMaterialHelper:143`). No copiar el bloque de abajo en los dos sin cambiar el `FROM`:

```java
// EquipoMaterialHelper
public static void bumpVersionConGuarda(Connection conn, int equipoId, int versionEsperada)
        throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE equipos SET version = version + 1 WHERE id = ? AND version = ?")) {
        ps.setInt(1, equipoId);
        ps.setInt(2, versionEsperada);
        ControlConcurrencia.exigirFilaAfectada(ps.executeUpdate(), Mensajes.CONFLICTO_CORRECCION);
    }
}

// EquipoOtrosMaterialHelper — idéntico salvo la tabla
//     "UPDATE equipo_otros SET version = version + 1 WHERE id = ? AND version = ?"
```

No se unifican en un helper común parametrizado por tabla: el nombre de tabla concatenado en el SQL
es justo lo que el resto del repo evita, y cada helper ya es el dueño único de las escrituras de su
agregado.

Actualizar el javadoc de `bumpVersion` en los dos, que hoy dice *"Siguen sin llevar guarda — eso está
fuera del alcance acordado"*. Deja de ser cierto.

**Commit:** `feat: bumpVersionConGuarda en los dos helpers de agregado`

### Paso 3 — Ortopedias: las cinco rutas

Firma nueva: cada método de service recibe `int versionEsperada`; cada método de DAO lo propaga.

| Service (`EquipoCorreccionService`) | DAO | Dónde va la guarda |
|---|---|---|
| `modificarCantidadMaterial` :58 | `MaterialDAO.actualizarCantidad` :291 | ya tiene `TransactionalConnection`: reemplazar `bumpVersionDelEquipoDe` por el bump guardado, movido al principio |
| `modificarCodigoMaterial` :95 | `MaterialDAO.actualizarCodigo` :343 | idem |
| `agregarMaterialAEquipo` :249 | `MaterialDAO.agregarMaterial` :400 | ya tiene tx; agregar el bump guardado como primera sentencia |
| `eliminarMaterial` :189 | `MaterialDAO.eliminarMaterialesPorCodigo` :476 | ya tiene tx; idem |
| `eliminarEquipo` :144 | `EquipoDAO` — **método nuevo**, ver abajo | `DELETE FROM equipos WHERE id = ? AND version = ?` + `exigirFilaAfectada` |

**`EquipoDAO.eliminar(String)` (`:381`) no se toca: es `@Override` de `DAO<Equipo,String>`.** Meterle
un parámetro rompe el contrato de la interfaz. Va un método nuevo al lado, igual que el criterio que
el Paso 8 fija para clientes:

```java
/**
 * Borrado guardado para {@code Correcciones}: CAS de una sola sentencia contra la {@code version}
 * que la pantalla tenía a la vista. No bumpea nada — la fila desaparece, así que no queda token
 * que invalidar.
 *
 * <p>El {@code eliminar(String)} de la interfaz sigue siendo el borrado ciego. No lo use ninguna
 * ruta de Correcciones.
 */
public void eliminarConVersion(int equipoId, int versionEsperada) { … }
```

`bumpVersionDelEquipoDe` (`MaterialDAO:318`) existe sólo porque esas rutas recibían nada más que el
`materialId`. Con la `version` viajando también viaja el `equipoId`, así que el helper privado
**se borra** y la resolución por sub-`SELECT` desaparece.

**Y de paso, scopear el `WHERE` por equipo.** `MaterialDAO.actualizarCantidad` (`:291`) y
`actualizarCodigo` (`:343`) filtran hoy sólo por `WHERE id = ?`. Con el `equipoId` viajando, un par
`equipoId`/`materialId` desalineado bumpearía el equipo A y escribiría en un material de B — un
cruce que la guarda no ve, porque cada una de las dos sentencias es válida por separado. El gemelo
de "otros" (`EquipoOtrosDAO.actualizarCantidadMaterial`) ya lo hace bien:
`WHERE id = ? AND equipo_otros_id = ?`. Agregar `AND equipo_id = ?` a las dos de ortopedias es una
línea y deja la simetría entre los dos lados:

```sql
UPDATE equipo_materiales SET cantidad = ? WHERE id = ? AND equipo_id = ?
UPDATE equipo_materiales SET codigo_catalogo = ? WHERE id = ? AND equipo_id = ?
```

**Ojo con `agregarMaterial` (`MaterialDAO:400`):** ya llama a `EquipoMaterialHelper.recalcularEstadoEquipo`,
que bumpea por su cuenta. Con el bump guardado al principio, la `version` sube **2** en esa ruta. Es
inocuo — el token es un CAS, no un contador de cambios — pero dejarlo escrito en el javadoc para que
nadie lo "arregle" sacando uno de los dos.

En el controller, las diez operaciones toman la misma forma de tres líneas. **Cada método sabe de
qué tipo es** — `modificarCantidadMaterial` es ortopedia, `modificarCantidadMaterialOtros` es otros —
así que el `TipoEquipo` va literal y el compilador no deja olvidarlo:

```java
private void modificarCantidadMaterial(Integer equipoId, Integer materialId,
                                       Integer cantidadNueva, String motivo) {
    OptionalInt version = versionDelEquipoAlaVista(TipoEquipo.ORTOPEDIA, equipoId);
    if (version.isEmpty()) { avisarSnapshotPerdido(); return; }
    aplicarCorreccion("modificar-cantidad",
        () -> correccionService.modificarCantidadMaterial(
                  equipoId, materialId, cantidadNueva, version.getAsInt(), motivo),
        "Cantidad modificada correctamente", "No se pudo modificar la cantidad");
}
```

**Leerla fuera del lambda, no adentro:** dentro corre en el hilo de fondo, y el estado mutable del
controller y de la view se toca **sólo en el EDT** (regla dura de `CLAUDE.md`).

**En las dos rutas de eliminación, leerla ANTES del `showConfirmDialog`**, no después. El chequeo de
identidad ya cubre el caso, pero leer antes achica la ventana en lugar de agrandarla:

```java
private void eliminarEquipo(Integer equipoId, String motivo) {
    OptionalInt version = versionDelEquipoAlaVista(TipoEquipo.ORTOPEDIA, equipoId);  // ← antes del modal
    if (version.isEmpty()) { avisarSnapshotPerdido(); return; }
    if (!confirmarEliminacionDeEquipo()) return;
    aplicarCorreccion("eliminar-equipo",
        () -> correccionService.eliminarEquipo(equipoId, version.getAsInt(), motivo), …);
}
```

**Alcance real de este paso y del siguiente.** Cambiar las diez firmas de service rompe todas las
llamadas de los tests: `EquipoCorreccionServiceTest`, `EquipoOtrosCorreccionServiceTest`,
`EquipoOtrosCorreccionServiceIntegrationTest`, `VersionAgregadoTest` y los tests de los DAO
(`MaterialDAOTest`, `EquipoDAOTest`, `EquipoOtrosDAOTest`) — del orden de **cien puntos de
llamada**. No es peligroso ni exige pensar: las filas nacen con `version = 0` (V21 crea la columna
`NOT NULL DEFAULT 0`), así que pasar `0` alcanza en todos los casos que no estén probando la guarda.
Pero es mecánico y voluminoso, y hace que los commits de los pasos 3 y 4 sean bastante más grandes
que el diff de producción. Contar con eso al planificar la sesión, y no confundir el volumen con
complejidad.

**Commit:** `feat: guarda de version en las correcciones de ortopedias`

### Paso 4 — Otros: las cinco rutas

| Service (`EquipoOtrosCorreccionService`) | DAO (`EquipoOtrosDAO`) | Dónde va la guarda |
|---|---|---|
| `modificarCantidadRemito` :60 | `actualizarCantidadRemito` :672 | ya hace `SET remito_cantidad = ?, version = version + 1`: sólo agregar `AND version = ?` a esa misma sentencia + `exigirFilaAfectada`. Es la ruta más barata del plan. |
| `modificarCantidadMaterial` :93 | `actualizarCantidadMaterial` :695 | ya tiene tx y bump: convertirlo en bump guardado y moverlo al principio |
| `agregarMaterial` :123 | `insertarMaterial` :722 | ya tiene tx; bump guardado como primera sentencia |
| `eliminarMaterial` :145 | `eliminarMaterialesPorDescripcion` :780 | idem |
| `eliminarEquipo` :179 | `eliminarEquipo` :806 | sin tx y sin bump (la fila desaparece): `DELETE FROM equipo_otros WHERE id = ? AND version = ?` + `exigirFilaAfectada` |

Acá `eliminarEquipo` **sí** se modifica en sitio: no es `@Override` de nada, así que agregarle el
parámetro no rompe ningún contrato.

**Con `filas == 0` no se commitea.** Es un choque entre este paso y el Paso 6, y hay que arreglarlo
acá. `actualizarCantidadMaterial` (`:696-708`) hoy hace `if (filas > 0) bump; tx.commit();` — o sea
commitea igual — y el service tira `ValidationException("Material no encontrado en el equipo")`
recién después (`EquipoOtrosCorreccionService:106-107`). Con el bump guardado **adelante**, ese
camino deja la `version` incrementada y **committeada**; y como el Paso 6 decide (bien) no recargar
salvo conflicto, la pantalla se queda con la `version` vieja y **la próxima corrección sobre ese
equipo choca por falso positivo**. El arreglo es un `return` temprano antes del commit, dejando que
el rollback del try-with-resources actúe:

```java
if (filas == 0) return 0;   // ← sin commit: el bump guardado se revierte con la transacción
tx.commit();
return filas;
```

**La regla, que vale para las diez rutas: nunca se commitea un bump que no acompañó un cambio de
dato.** Hay **tres** lugares más con el mismo `tx.commit()` en el camino de 0 filas, todos en
ortopedias, todos del Paso 3:

| Ruta | Hoy |
|---|---|
| `MaterialDAO.actualizarCantidad` (`:291`) | `tx.commit(); return false;` |
| `MaterialDAO.actualizarCodigo` (`:343`) | idem |
| `MaterialDAO.eliminarMaterialesPorCodigo` (`:499-502`) | `if (idsMateriales.isEmpty()) { tx.commit(); return false; }` |

En las dos primeras el efecto es sólo ruido: el service ignora el retorno y la pantalla recarga
igual. Mismo `return` temprano sin commit.

**Las dos rutas de `DELETE` por clave son distintas y hay que cerrarlas más fuerte**
(`eliminarMaterialesPorCodigo` de ortopedias y `eliminarMaterialesPorDescripcion` de "otros"):
después de un bump guardado que **sí** matcheó, un `DELETE` de 0 filas es contradictorio — la
`version` dice que nadie tocó el equipo, pero las filas que la pantalla mostraba no están. Si se deja
pasar, el service sigue de largo (ninguno de los dos mira el retorno) y con el reordenamiento del
Paso 5 escribiría **auditoría de una eliminación que no ocurrió**, que es exactamente lo que el Paso
5 viene a impedir. Van con su propio `exigirFilaAfectada` sobre el conteo del `DELETE`, mismo
`CONFLICTO_CORRECCION`: no commitea el bump y nada aguas abajo se ejecuta.

Con eso, la condición `if (filas > 0)` que hoy envuelve al bump en las rutas de "otros" desaparece:
el bump va primero y sin condición, y el `DELETE` de 0 filas ya no es un camino silencioso.

`cargarYValidarNuevo` **no se toca**: queda como pre-chequeo que da un mensaje temprano y específico.
Documentar en su javadoc que la correctitud **no** depende de él.

**Commit:** `feat: guarda de version en las correcciones de otros`

### Paso 5 — Reordenar los snapshots de auditoría en las rutas de eliminación

**Este paso arregla un daño que introducen los pasos 3 y 4, así que no es opcional.**

`EquipoCorreccionService.eliminarEquipo` :156-172 y `eliminarMaterial` :207-214, y sus equivalentes
de "otros" (:160-168, :186-199), escriben los snapshots de auditoría **antes** del `DELETE`, con este
comentario: *"Los snapshots se escriben ANTES del DELETE: después las filas ya no existen."*

Con guarda, el `DELETE` pasa a poder fallar por conflicto — y entonces quedan filas de auditoría de
una eliminación **que nunca ocurrió**. Es peor que el problema que la guarda resuelve: contamina el
registro que existe justamente para saber qué se tocó. Y con la guarda activa el conflicto deja de
ser raro, así que sería contaminación **rutinaria**.

El motivo del orden actual se preserva sin el efecto: las filas ya están **en memoria** antes de
escribir nada (`equipo.getMateriales()`, `obtenerMaterialesPorCodigo`,
`obtenerMaterialesPorDescripcion` — verificado en las cuatro rutas). Orden nuevo:

1. leer las filas a memoria (ya se hace),
2. `DELETE` con guarda,
3. escribir los snapshots desde memoria.

**Qué se invierte, y por qué se acepta.** Hoy, si falla la escritura de auditoría, el `if (!snap)
throw` impide el borrado: nunca hay dato borrado sin rastro. Con el orden nuevo, un fallo de
auditoría llega cuando el equipo **ya no está**. Se acepta porque es exactamente la sutileza #1 de
[`sacar-sql-de-equipo-otros-correccion-service.md`](sacar-sql-de-equipo-otros-correccion-service.md)
— *"si un fallo de auditoría revirtiera el dato, sería un cambio de atomicidad"* — y porque los dos
fallos no son igual de probables: el conflicto de guarda es esperable y frecuente, el fallo de
escritura de auditoría es un error de base, raro y correlacionado con que el `DELETE` también falle.

**Lo que sí hay que arreglar es el mensaje.** Un `throw new DatabaseException("No se pudo registrar
el snapshot…")` después de un `DELETE` exitoso le dice al operador que la operación falló cuando en
realidad se aplicó, y esa mentira es peor que la que la guarda vino a arreglar. En las cuatro rutas:

```java
// después del DELETE guardado
try {
    escribirSnapshots(...);
} catch (RuntimeException e) {
    log.error("El equipo {} se eliminó pero falló el registro de auditoría. Snapshot: {}",
        equipoId, snapshotEnMemoria, e);          // ← el payload queda recuperable del log
    throw new DatabaseException(
        "El equipo se eliminó, pero no se pudo registrar la auditoría. Avisá al administrador.", e);
}
```

Mismo criterio para los `if (!snap)` que hoy devuelven `false`: se convierten en ese `log.error` +
`DatabaseException` con el texto que dice **que el dato sí se borró**. No se silencia nada.

**Son tres rutas con `if (!snap)`, no cuatro.** `EquipoOtrosCorreccionService.eliminarMaterial`
(`:160-168`) ya **ignora** el booleano de `registrarMaterialEliminado`, así que ahí no hay nada que
convertir — sólo mover los snapshots después del `DELETE`. Ojo además con su `registrarCambio` de
`ELIMINACION_MATERIAL`, que ya está después del `DELETE`: al reordenar, los snapshots quedan
**junto** a él, no antes; el bloque de auditoría de esa ruta pasa a ser uno solo y contiguo.

**Esto no unifica la transacción de auditoría con la del dato** — la no-atomicidad auditoría↔dato es
deliberada. Sólo cambia el **orden** y el texto del error.

**Commit:** `fix: snapshots de auditoría después del DELETE guardado`

### Paso 6 — El conflicto tiene que llegar al operador con su cartel

`CorreccionesController.describirError` :280 ramifica por `ValidationException` y `DatabaseException`;
todo lo demás cae en *"Error inesperado: "*. `ConflictoConcurrenciaException extends BusinessException`
caería ahí.

Agregar la rama de `BusinessException` **antes** de las otras dos y devolver `e.getMessage()` pelado:
el mensaje ya está redactado para el operador y prefijarlo con "Error" lo empeora. Es seguro ponerla
primero: `ValidationException` **no** hereda de `BusinessException` (ver la jerarquía en "Estado del
terreno"), así que no la tapa.

`aplicarCorreccion` **hoy no recarga cuando falla**, y el mensaje de conflicto promete *"La pantalla
se actualizó"*. Hay que recargar — pero **sólo ante `ConflictoConcurrenciaException`**, no ante
cualquier fallo:

```java
.siFalla(e -> {
    panel.mostrarError(describirError(e));
    if (e instanceof ConflictoConcurrenciaException) cargarEquiposNuevos();
})
```

Recargar ante todo fallo sería un bug nuevo: `cargarEquiposNuevos()` termina en
`panel.limpiarPantalla()` → `limpiarFormulario()`, así que un error de validación ("la cantidad debe
ser mayor a 0") le borraría al operador todo lo que tipeó, incluido el motivo. Ante un conflicto el
borrado es lo correcto — el snapshot ya no sirve; ante una validación es destructivo y gratuito.

**Commit:** `fix: los conflictos de Correcciones llegan con su propio mensaje`

---

## Parte B — Secuencia de lotes

### Paso 7 — Reintento sobre la violación de UNIQUE

`LoteDAO.obtenerSiguienteSecuencia` :449 es `SELECT COALESCE(MAX(secuencia), 0) + 1`. Dos lotes
lanzados a la vez calculan la misma secuencia; hoy los salva el `UNIQUE (id_negocio)` de la V1, que
los hace fallar con un error técnico feo.

**No es un lost update, es asignación de identidad**: no hay ningún dato leído por el operador que se
esté pisando. Por eso no lo resuelve el bloqueo optimista y no lleva `ConflictoConcurrenciaException`
salvo al agotar los reintentos.

Forma:

1. Extraer el cuerpo actual de `lanzarLote` :391 a un privado `intentarLanzarLote(...)`. **El
   `try (TransactionalConnection tx = …)` va adentro del privado**, no afuera: cada intento tiene que
   abrir su propia transacción, o el reintento releería el mismo `MAX(secuencia)` para siempre.
2. En `insertarLote`, envolver el `INSERT INTO lotes` y traducir la violación de clase `23` de
   SQLState a una excepción **interna y privada** (`SecuenciaDuplicadaException`), que lleve el
   `idNegocio` que se intentó y la `SQLException` original como causa. Acotar el `catch` a esa
   sentencia, para que una violación de FK de otra sentencia de la transacción no se confunda con
   esto.

   **La excepción tiene que extender `RuntimeException`, no `SQLException`.** `lanzarLote` ya tiene
   un `catch (SQLException e) { throw new DatabaseException(…) }` que se la comería antes de que el
   bucle de reintentos la vea, y el reintento no se dispararía nunca. Que sea `private static final
   class` anidada en `LoteDAO`: no debe escaparse del DAO.
3. **Clase `23` en ese `INSERT` no implica que el duplicado sea el de `id_negocio`.** La tabla
   `lotes` tiene su propia FK — `FOREIGN KEY (autoclave_nombre) REFERENCES autoclaves(nombre)`,
   `V1__baseline.sql:62` — así que un autoclave borrado o renombrado da clase `23` en **la misma
   sentencia**, y `esViolacionDeIntegridad` no distingue FK de UNIQUE (mira `startsWith("23")`). Sin
   discriminar, un problema de autoclave se llevaría los reintentos y terminaría con un cartel de
   "conflicto de secuencia" que diagnostica cualquier cosa menos el problema.

   **Dónde va la discriminación: afuera de la transacción fallida, sobre conexión nueva.** Éste es
   el punto que hay que entender antes de escribir la línea, porque la versión obvia no funciona en
   producción y **sí** pasa en los tests. Un `SELECT 1 FROM lotes WHERE id_negocio = ?` corrido
   dentro del intento fallido lee del snapshot que esa transacción fijó en su **primera** lectura —
   `obtenerSiguienteSecuencia` (`:449`) — así que bajo el `REPEATABLE READ` de MySQL la fila que el
   otro operador committeó es **invisible**: el chequeo daría vacío, concluiría "no era el
   duplicado" y el reintento no se dispararía nunca. Que el `INSERT` sí haya visto la fila no es
   contradicción: la verificación de unicidad del índice no pasa por el snapshot. Y **H2 no lo
   delata** — corre en `READ COMMITTED`, ve la fila, y el test del Paso 10 quedaría verde sobre un
   arreglo que en MySQL no hace nada. Es el mismo modo de fallo que `CLAUDE.md` ya documenta para
   las relecturas de guarda.

   Por eso la discriminación va en el bucle, **después** de que la transacción del intento revirtió,
   sobre una conexión nueva del pool: snapshot fresco por construcción y sin tomar locks sobre el
   lote ajeno.

   ```java
   for (int intento = 1; intento <= MAX_INTENTOS_SECUENCIA; intento++) {
       try {
           return intentarLanzarLote(...);           // abre y cierra su propia transacción
       } catch (SecuenciaDuplicadaException e) {
           // la transacción del intento ya revirtió: esta lectura ve el estado committeado
           if (!existeIdNegocio(e.getIdNegocio())) throw new DatabaseException(…, e.getCause());
           // era el duplicado de secuencia: siguiente intento
       }
   }
   throw new ConflictoConcurrenciaException(Mensajes.CONFLICTO_SECUENCIA_LOTE);
   ```

   `existeIdNegocio` abre su propia `Connection` del pool (no recibe la del intento, que ya está
   cerrada). El javadoc **tiene que decir por qué la lectura está afuera**, o el próximo lector la
   "simplifica" moviéndola adentro del `try` y reintroduce el bug sin que ningún test se ponga rojo.
4. `MAX_INTENTOS_SECUENCIA = 3`. Constante nombrada, no un `3` suelto.
5. Agotados los reintentos: `ConflictoConcurrenciaException` con un `CONFLICTO_SECUENCIA_LOTE` nuevo.

**Qué NO se reintenta:** `ConflictoConcurrenciaException` propaga en el acto. Si el reintento chocó
contra la guarda de materiales del lote, eso es un conflicto real y reintentar sería exactamente el
reintento automático que `CLAUDE.md` prohíbe. El bucle sólo captura `SecuenciaDuplicadaException`.

**Qué cubre y qué no, en MySQL real.** Con dos lanzamientos simultáneos, el segundo `INSERT`
**bloquea** en el índice único hasta que el primero termina; recién entonces sale la clase `23` y el
reintento hace su trabajo — que es el caso común, porque estas transacciones son cortas. Si el
primero tarda más que `innodb_lock_wait_timeout`, lo que sale es un *lock wait timeout*
(`40001`/`HY000`), que **no** se reintenta: se sigue reportando como `DatabaseException`. Reintentar
una espera de lock agotada es apilar minutos de espera sobre una base ya trabada. Dejarlo escrito en
el javadoc para que nadie lea el reintento como una cobertura que no da.

Detectar la clase `23` **sin códigos propietarios**, igual que `SimpleEntityDAO`: `SQLState` que
empiece con `23` o `SQLIntegrityConstraintViolationException`. Cubre MySQL (`23000`) y H2 (`23505`).
Pero `SimpleEntityDAO.esViolacionDeIntegridad` (`:147`) es **privado**, así que no se copia: se
**extrae** a `common/dao/ErroresSql.esViolacionDeIntegridad(SQLException)` — al lado de
`ControlConcurrencia` — y `SimpleEntityDAO` pasa a delegar ahí. Es un movimiento sin cambio de
comportamiento y deja una sola definición de "violación de integridad" en la app.

**Commit:** `fix: reintento de secuencia al lanzar lotes concurrentes`

---

## Parte C — ABM

### La medición que achicó el alcance (2026-09-04)

El hallazgo #9 nombra cinco pantallas. Medido sobre `src/main`, sólo dos operaciones son
alcanzables **y** pisables:

| Ruta | Estado real |
|---|---|
| `SimpleEntityDAO.actualizar` (renombrar cliente / institución / profesional) | **cero llamadores** — no existe pantalla de renombrado |
| `CatalogoDAO.guardarDescripcion` | es un **upsert** (`ON DUPLICATE KEY UPDATE`), no un `INSERT` puro, así que *sí* tiene superficie de lost update — pero **cero llamadores fuera de la propia feature**: sólo lo llama `CatalogoService.guardarDescripcion`, que a su vez no tiene llamador de UI. Inalcanzable, no inofensivo. |
| `CatalogoDAO.eliminar`, `guardar`, `actualizar` | **cero llamadores**; `guardar` y `actualizar` son stubs que devuelven `false` |
| `CatalogoOtrosDAO` | no tiene update ni delete: sólo lookup + `obtenerOCrear` (`INSERT IGNORE`) |
| `guardarCliente` / `guardarInstitucion` / `guardarProfesional` | `INSERT` — no hay lost update posible |
| **`ClienteService.eliminarCliente`** :95 (Ajustes) | escritura real, sin guarda |
| **`ClienteService.fusionarClientes`** :109 (Ajustes) | escritura real, sin guarda: mueve `equipos` + `equipo_otros` y borra el cliente origen |

**Decisión:** guardar sólo esas dos. El resto se documenta como *"sin ruta alcanzable"* — que no es
lo mismo que *"sin superficie de escritura"* (el upsert del catálogo la tiene) ni que *"pendiente"*.
Ponerle una guarda a `SimpleEntityDAO.actualizar` sería código defensivo sin caso de uso — justo lo
que el refactor-clean del 2026-08-27 vino borrando.

**Nota para quien ejecute:** ese código muerto es un hallazgo aparte, no de este plan. Anotarlo en
`hallazgos-arquitectura-pendientes.md` sin tocarlo: borrarlo toca la interfaz `DAO<T,ID>` y merece
su propia decisión.

### Paso 8 — `eliminarCliente` con CAS sobre el nombre

`DELETE FROM clientes WHERE id = ? AND nombre = ?`, con el nombre que mostraba la grilla de Ajustes.
Bloquea el caso feo: el operador cree que borra "Juan Pérez" y borra otra cosa porque alguien
renombró la fila.

Mensaje nuevo `CONFLICTO_CLIENTE`. **No** tocar `SimpleEntityDAO.eliminar`: la guarda va en un método
nuevo de `ClienteDAO`, para no cambiarle la firma a instituciones y profesionales, que no tienen
borrado alcanzable. Ese método nuevo **tiene que replicar el mapeo de FK** de `SimpleEntityDAO`
(`ReferentialIntegrityException`), reusando el `ErroresSql` que extrae el Paso 7 — si no, un cliente
referenciado dejaría de dar su mensaje propio.

El nombre viaja desde `AjustesController` :100, que ya tiene el `Cliente` seleccionado
(`cliente.getId()`); pasa a mandar también `cliente.getNombre()`.

Cuidado con la interacción con `ReferentialIntegrityException`: si el cliente está referenciado, el
`DELETE` falla por FK **antes** de que importe el CAS. Ese orden es el correcto y no hay que
cambiarlo — son dos mensajes distintos para dos causas distintas.

**El título del diálogo también miente en esta ruta.** `AjustesController:101` pasa
`tituloError = "No se puede eliminar"`, que era exacto cuando la única falla posible era la FK. Con
el CAS, el mismo diálogo se abre para "otro renombró este cliente" — donde sí se puede eliminar, con
datos frescos. Cambiarlo por algo neutro (`"Eliminar cliente"`) al tocar la ruta: el cuerpo del
mensaje ya dice qué pasó, el título no tiene que contradecirlo.

**Commit:** `feat: guarda de nombre al eliminar clientes`

### Paso 9 — `fusionarClientes` con verificación de los dos nombres

`FusionClientesDAO.fusionar` :11 ya corre en una `TransactionalConnection`. Agregar como **primera**
sentencia de esa transacción, antes de mover referencias:

```sql
SELECT id, nombre FROM clientes WHERE id IN (?, ?) FOR UPDATE
```

Si falta alguno, o si algún nombre difiere del que el operador vio en los combos, conflicto y
rollback. Acá el `FOR UPDATE` **sí** sirve, a diferencia del caso de Correcciones: la lectura está
dentro de la misma transacción que la escritura, así que el lock vive hasta el commit. `IN (?, ?)`
bloquea en orden de índice, no en el orden de los parámetros, así que dos fusiones cruzadas
(A→B y B→A) toman los locks en el mismo orden y no se traban entre sí.

Lo que **no** cuenta como conflicto: que hayan aparecido equipos nuevos a nombre del cliente origen
entre la lectura y el Fusionar. Mover esos equipos es justamente lo que la fusión quiere hacer.

**Cerrar acá el único agujero de bump que queda.** Las dos sentencias de la fusión mueven `equipos` y
`equipo_otros` **sin bumpear `version`** — es la excepción que el javadoc de
`EquipoOtrosMaterialHelper.recalcularEstadoEquipo` lista como "fuera del alcance acordado del plan".
Con la Parte A activa deja de ser aceptable: la fusión cambia el cliente que la grilla de
Correcciones muestra, y sin bump la guarda no se entera. Es una palabra en cada `UPDATE`:

```sql
UPDATE equipos       SET nro_cliente = ?, version = version + 1 WHERE nro_cliente = ?
UPDATE equipo_otros  SET nro_cliente = ?, version = version + 1 WHERE nro_cliente = ?
```

No lleva `exigirFilaAfectada`: cero filas es legítimo (un cliente sin equipos se fusiona igual).

`AjustesController` :142 pasa a mandar los dos nombres además de los dos ids.

**Y el conflicto tiene que llegar sin prefijo.** `AjustesController.mutar` arma el cartel como
`prefijoError + e.getMessage()`, y la fusión pasa `"Error al fusionar clientes: "` — que delante de
un mensaje ya redactado para el operador queda mal, el mismo criterio del Paso 6. En `mutar`:

```java
.siFalla(e -> mostrarError(
    e instanceof BusinessException ? e.getMessage() : prefijoError + e.getMessage(), tituloError))
```

**Commit:** `feat: guarda de nombres al fusionar clientes`

---

## Paso 10 — Tests

Agregar a `ConcurrenciaOptimistaTest` (`infrastructure/db/`), con la **misma forma** que los ocho
casos existentes — *A lee → B modifica y commitea → A escribe → conflicto, y el estado final es
exactamente el de B*:

| Caso | Qué prueba |
|---|---|
| `correccionOrtopediasChocaConVersionVieja` | dos correcciones sobre el mismo equipo; la segunda no aplica |
| `correccionOtrosChocaConVersionVieja` | idem en `equipo_otros` |
| `eliminarEquipoConVersionViejaNoBorra` | el `DELETE` guardado no encuentra fila y el equipo sobrevive |
| `conflictoDeCorreccionNoDejaAuditoriaHuerfana` | **el caso del Paso 5**: tras el conflicto, cero filas nuevas en auditoría |
| `fusionMueveEquiposYBumpeaVersion` | **el agujero del Paso 9**: tras fusionar, la `version` de los equipos movidos subió |
| `fusionarClientesConNombreCambiadoAborta` | los `equipos` no se movieron y el cliente origen sigue vivo |
| `eliminarClienteRenombradoNoBorra` | el CAS del Paso 8 |
| `materialAjenoAlEquipoNoBumpeaVersion` | **el choque del Paso 4**: con `filas == 0` la transacción se revierte entera y la `version` queda como estaba. Un caso por lado — con el `AND equipo_id = ?` del Paso 3, ortopedias y "otros" scopean igual y los dos tienen que pasar |
| `deleteDeCeroFilasConVersionValidaEsConflicto` | las dos rutas de `DELETE` por clave del Paso 4: la guarda matcheó pero no había filas → `ConflictoConcurrenciaException`, sin bump committeado y sin auditoría escrita |

Para el Paso 7 van **dos** tests en `LoteDAOTest`, no uno:

- el reintento: forzar la colisión insertando a mano un `id_negocio` y verificar que el segundo
  intento resuelve la secuencia siguiente;
- **la discriminación de la clase `23`**: lanzar un lote con un `autoclave_nombre` inexistente tiene
  que fallar en el acto con `DatabaseException`, **sin** reintentar y **sin** cartel de secuencia.
  Es lo que separa el arreglo del bug que reemplaza.

**Lo que estos dos tests NO pueden probar, y hay que escribirlo en la clase:** que la discriminación
esté **afuera** de la transacción. H2 corre en `READ COMMITTED`, así que la lectura ve la fila ajena
esté donde esté — los dos tests pasan igual con el `SELECT` movido adentro del `try`, que es la
versión rota en MySQL. La única defensa contra esa regresión es el javadoc del Paso 7. Un comentario
en el test diciendo exactamente esto vale más que un caso extra que no discrimina.

No van a `ConcurrenciaOptimistaTest`: esa clase verifica guardas, y la secuencia no es una guarda. Un
test de deadlock real pasaría en H2 y mentiría sobre producción — no escribirlo.

**Qué queda sin test automático, a propósito:** las dos ramas de `versionDelEquipoAlaVista`.

- La rama **`sel == null`**, que es la que se dispara hoy, pide un `pintar` de `TareaUI` entrando
  mientras un modal bombea el EDT: test de temporización de Swing, frágil y de los que mienten. Va
  al smoke manual de Verificación.
- La rama de **`tipo` + `id`** no es reproducible en absoluto mientras el `pintar` siga llamando a
  `limpiarPantalla()`: la selección se limpia antes de que el índice pueda repuntar. **No inventar un
  test ni un smoke para ella** — es un invariante que cubre un cambio futuro de una línea, no un
  camino vivo, y un test que finja ejercitarlo sería peor que ninguno. Lo que la protege es el
  javadoc del Paso 1, que dice por qué está y qué la haría necesaria.

**Commit:** `test: guardas de Correcciones, fusión de clientes y secuencia de lotes`

## Paso 11 — Documentación

1. **`CLAUDE.md`**, sección *"Concurrencia — bloqueo optimista"*: reescribir el párrafo
   *"`equipos` y `equipo_otros` sí tienen `version` (V21) … y NO se usa como guarda"* con la
   asimetría de arriba (Registrar Estado por `estado`, Correcciones por `version`, y **por qué**).
   Actualizar *"Dónde hay guarda hoy"* y *"Qué quedó afuera"*.
2. **`EquipoOtrosMaterialHelper.recalcularEstadoEquipo`**: actualizar la auditoría del javadoc —
   `FusionClientesDAO` deja de ser una excepción (Paso 9), y las rutas de Correcciones dejan de ser
   "escrituras ciegas".
3. **`EquipoDAO.actualizar`**: su javadoc dice *"hoy no tiene llamador de producción"*. Tiene uno
   (`EquipoService:123`), pero `EquipoService.actualizar` **tampoco tiene llamador** — ni en
   `src/main` ni en `src/test`: la cadena entera está muerta. O sea que el javadoc es correcto en
   sustancia y **no hay que "corregirlo"**. Si se toca, que sea para precisar la cadena
   (*"su único llamador es `EquipoService.actualizar`, que tampoco tiene llamador"*), que es el dato
   que necesita quien audite el bump mañana. Anotar la cadena muerta junto al código muerto de la
   Parte C, en el punto 4.
4. **`hallazgos-arquitectura-pendientes.md`**: cerrar #9 con commits, y anotar el código muerto de
   la Parte C como hallazgo nuevo (incluyendo que `CatalogoDAO.guardarDescripcion` es un upsert
   inalcanzable, no un `INSERT`).
5. **`bloqueo-optimista-concurrencia.md`**: una línea al pie apuntando acá. No reabrirlo: está
   marcado como ejecutado.

**Commit:** `docs: cierre del hallazgo #9`

---

## Verificación

- `mvn test` verde.
- `mvn clean package` OK.
- **Smoke manual de Correcciones con dos clientes contra la misma base**, sin `-Daptium.edt.strict=true`
  (los autocompletados de cliente lanzan en `strict`): A abre la lista, B corrige y guarda, A intenta
  corregir → tiene que ver `CONFLICTO_CORRECCION` y la lista recargada, no *"Error inesperado"*.
- **Smoke manual del snapshot perdido**, que es lo que el Paso 10 deja sin test: A selecciona un
  equipo y abre el diálogo de motivo de "Eliminar equipo"; con el diálogo abierto, entra un refresco
  de la lista. A confirma → tiene que ver el cartel de conflicto y la lista recargada, **no** una
  eliminación aplicada. Lo que este smoke ejercita es la rama `sel == null` (el refresco terminó en
  `limpiarPantalla()`), que es la única viva. **No intentar montar el caso `Equipo#7` /
  `EquipoOtros#7`**: mientras el `pintar` limpie la selección no es alcanzable, y armar la base con
  ids coincidentes no lo vuelve reproducible — daría el mismo conflicto por `null` y haría creer que
  se probó otra cosa.
- **Smoke manual de validación**: un error de validación (cantidad 0) **no** tiene que borrar el
  formulario — es la regresión que el Paso 6 evita.
- La suite corre en **H2, que es `READ COMMITTED`**. Las guardas de este plan son sentencias `UPDATE`
  y `DELETE` con condición en el `WHERE`, idénticas en los dos motores, así que el test es honesto.
  El valor esperado de la guarda viene del snapshot de la pantalla, no de una relectura, así que el
  problema de `REPEATABLE READ` que documenta `CLAUDE.md` no aplica acá. El `FOR UPDATE` del Paso 9
  **no** se comporta igual en H2 que en MySQL: ese test verifica el **resultado** (la fusión no
  ocurrió), nunca el bloqueo.
- **Dos puntos ciegos conocidos de la suite, los dos del Paso 7.** En H2 la discriminación de la
  clase `23` pasa esté dentro o fuera de la transacción (`READ COMMITTED` ve la fila ajena en los dos
  casos), y el bloqueo del índice único en el segundo `INSERT` tampoco se reproduce. Si hay ocasión
  de correr los dos tests de `LoteDAOTest` una vez contra un MySQL de desarrollo, esa es la única
  verificación real del Paso 7; si no la hay, dejarlo anotado como lo que es — no dar la suite verde
  por evidencia de que el reintento funciona en producción.

## Riesgos

- **El falso positivo de la Parte A es real y aceptado.** Dos operadores corrigiendo materiales
  distintos del mismo equipo van a chocar. Y como `CorreccionesController` **no está suscripto a
  ningún grupo de refresco** (sólo publica en `operativo`, `UiCoordinator:156`), su snapshot se
  refresca sólo al entrar a la pantalla y tras cada corrección propia: cuanto más tiempo quede la
  pantalla abierta sin usarse, más probable el choque. Si en el uso real resulta molesto, el arreglo
  no es quitar la guarda: es bajar a CAS por campo en las tres sustituciones de valor y dejar
  `version` sólo en las estructurales. Anotarlo si aparece; no anticiparlo.
- **El Paso 5 invierte cuál lado puede quedar huérfano.** Antes: nunca hay borrado sin auditoría.
  Después: puede haber borrado con auditoría incompleta, si falla la escritura de auditoría. Está
  decidido a conciencia (ver el paso), mitigado con `log.error` del payload, y el operador recibe un
  mensaje que dice la verdad. Si alguna vez el requisito pasa a ser "auditoría o nada", el cambio es
  pasarle la `Connection` de la transacción al `AuditoriaDAO` — y eso contradice una decisión
  vigente, así que se decide aparte, no de contrabando.
- **El reintento del Paso 7 es la única ruta con reintento automático de la app.** Que quede
  documentado en el javadoc de `lanzarLote` por qué es legítimo acá y no en las guardas, y qué caso
  (lock wait timeout) **no** cubre.
