# Plan: sacar el SQL de `EquipoOtrosCorreccionService`

**Estado:** aprobado, sin ejecutar.
**Origen:** diagnóstico de arquitectura hexagonal del 2026-07-22. Era el punto 4 de 5.
Los puntos 2 (`Color` fuera de `EstadoEquipo`) y 3 (romper el ciclo `AppModel ↔ *ReporteService`)
ya están commiteados. El punto 1 (inyectar `DataSource`) se descartó: el seam de testing
ya existe vía `ConnectionPool.setDataSourceForTesting()` y el costo (~25 archivos) no se paga.

---

## Problema

[`EquipoOtrosCorreccionService`](../src/main/java/com/example/features/equipos/otros/service/EquipoOtrosCorreccionService.java)
tiene `import java.sql.*` y ~10 sentencias SQL crudas. Es un service que es un DAO.

No viola solo la arquitectura hexagonal — viola **la regla del propio repo**
(`model → dao → service`, documentada en CLAUDE.md) que las otras 7 features respetan.
Es la única clase de la capa `service/` con `ResultSet`.

## Objetivo

Mover todo el SQL a `EquipoOtrosDAO`, dejando el service con solo validación,
orquestación y auditoría.

---

## Decisión tomada (importante)

**Refactor + arreglo de los fallos silenciosos en el mismo paso.**

Se evaluó hacer un refactor puro (preservar los `catch` mudos) y se descartó.
Los tres `catch` que hoy tragan `SQLException` se convierten en `DatabaseException`,
siguiendo la convención del repo (55 casos ya lo hacen así).

**Esto cambia comportamiento observable.** Ver la tabla de abajo — está aceptado.

---

## Inventario del SQL a mover

### Métodos públicos con SQL embebido

| Método | Líneas aprox. | SQL |
|---|---|---|
| `modificarCantidadRemito(int, int, String)` | 83-94 | `UPDATE equipo_otros SET remito_cantidad = ? WHERE id = ?` |
| `modificarCantidadMaterial(int, int, int, String)` | 120-135 | `UPDATE equipo_otros_materiales SET cantidad = ? WHERE id = ? AND equipo_otros_id = ?` |
| `agregarMaterial(int, String, int, String)` | 159-214 | **Transacción:** `INSERT equipo_otros_materiales` + `INSERT otros_material_movimientos` |
| `eliminarMaterial(int, String, String)` | 240-251 | `DELETE FROM equipo_otros_materiales WHERE equipo_otros_id = ? AND descripcion = ?` |
| `eliminarEquipo(int, String)` | 284-294 | `DELETE FROM equipo_otros WHERE id = ?` |

### Helpers privados con SQL (los tres bugs viven acá)

| Helper | Líneas | SQL |
|---|---|---|
| `tieneFilasMateriales(int)` | 316-328 | `SELECT COUNT(*) FROM equipo_otros_materiales WHERE equipo_otros_id = ?` |
| `obtenerCantidadMaterial(int, int)` | 330-343 | `SELECT cantidad FROM equipo_otros_materiales WHERE id = ? AND equipo_otros_id = ?` |
| `obtenerMaterialesPorDescripcion(int, String)` | 345-372 | `SELECT id, catalogo_otros_id, descripcion, cantidad, estado FROM equipo_otros_materiales WHERE equipo_otros_id = ? AND descripcion = ?` |

### Limpio, no tocar

- `obtenerEquiposOtrosNuevos()` — ya delega al DAO.
- `cargarYValidarNuevo(int)` — sin SQL, usa `equipoOtrosDAO.obtenerPorId()`.

### Se eliminan al migrar

`rollback(Connection, Exception)` (374-381) y `closeConn(Connection)` (383-389) —
los reemplaza `TransactionalConnection`.

---

## Los tres fallos silenciosos: antes → después

### A — `tieneFilasMateriales()`, líneas 324-327 (el más serio)

```java
} catch (SQLException e) {
    log.error("Error al verificar filas de materiales equipo={}", equipoId, e);
    return false;   // ← falla ABIERTO
}
```

Es el guard de `modificarCantidadRemito`. Hoy, si la BD falla, la app cree que el
remito no tuvo movimientos y **permite modificar la cantidad de un remito que sí los tuvo**.

**Después:** `throw new DatabaseException("Error al verificar movimientos del remito")`.
**Cambio visible:** ante error de BD la operación pasa a bloquearse en vez de proceder.

### B — `obtenerCantidadMaterial()`, líneas 339-342

Traga la `SQLException` y cae en `throw new ValidationException("El material no existe en el equipo")`.
Un corte de BD se le muestra al usuario como "el material no existe".

**Después:** distinguir los dos casos.
- `SQLException` → `DatabaseException`.
- `rs.next() == false` (la fila realmente no está) → **sigue siendo** `ValidationException("El material no existe en el equipo")`.

### C — `obtenerMaterialesPorDescripcion()`, líneas 368-370

Devuelve lista vacía ante `SQLException`. El llamador (`eliminarMaterial`) convierte
el vacío en `ValidationException("No hay materiales con esa descripción en el equipo")`.

**Después:** distinguir los dos casos.
- `SQLException` → `DatabaseException`.
- Resultado genuinamente vacío → **sigue siendo** `ValidationException("No hay materiales con esa descripción en el equipo")`.

> El patrón en B y C es el mismo: hoy "error de BD" y "no encontrado" colapsan en el
> mismo mensaje. Después del cambio solo el "no encontrado" real conserva la `ValidationException`.

---

## Sutilezas que NO se deben romper

1. **La auditoría corre FUERA de la transacción, después del `commit()`.**
   En los cinco métodos, `auditoriaDAO.registrarCambio(...)` se llama después de que
   el dato ya está confirmado. Si un fallo de auditoría revirtiera el dato, sería un
   cambio de atomicidad. **No unificar las transacciones "porque queda más prolijo".**

2. **`eliminarMaterial` escribe los snapshots de auditoría ANTES del `DELETE`** (líneas 232-238).
   Preservar ese orden.

3. **`agregarMaterial` usa `catalogoOtrosDAO.obtenerOCrear(conn, descripcion)`** — le pasa
   la `Connection` de la transacción en curso. Ese overload ya existe
   (`CatalogoOtrosDAO:85`). Al migrar a `TransactionalConnection`, el `obtenerOCrear`
   tiene que seguir recibiendo **la misma** conexión, o se pierde la atomicidad entre
   el alta en catálogo y el alta del material.

4. **`agregarMaterial` no restaura `autoCommit=true` antes de cerrar.** Hoy no rompe
   porque HikariCP resetea la conexión al devolverla al pool. `TransactionalConnection`
   ya maneja bien el ciclo de vida — no hace falta replicar el comportamiento actual.

5. **`modificarCantidadMaterial` lanza `ValidationException` con `rows == 0`** dentro del
   try-with-resources (línea 128). Como `autoCommit` está ON, el `UPDATE` ya se aplicó —
   pero `rows == 0` significa que no tocó ninguna fila, así que no hay dato inconsistente.
   Preservar la semántica.

---

## Red de seguridad

- **`EquipoOtrosCorreccionServiceIntegrationTest`** — 9 tests contra H2, con DAOs reales.
  Es la red principal. Extenderlo con los casos de los tres bugs antes de mover nada.
- **`EquipoOtrosCorreccionServiceTest`** — 33 tests con mocks de
  `EquipoOtrosDAO`, `AuditoriaDAO`, `CatalogoOtrosDAO`.
  **Ojo:** este archivo no se leyó durante el diagnóstico. Al mover SQL al DAO, los
  colaboradores que se invocan cambian, así que es muy probable que necesite stubs nuevos
  (p. ej. `when(equipoOtrosDAO.tieneFilasMateriales(...))`). **Revisarlo primero** — es
  probablemente el grueso del trabajo del refactor.

## Orden sugerido

1. Leer `EquipoOtrosCorreccionServiceTest` y evaluar cuántos mocks se ven afectados.
2. Extender `EquipoOtrosCorreccionServiceIntegrationTest` con los casos de A, B y C.
3. Agregar los métodos nuevos a `EquipoOtrosDAO` (con `DatabaseException`, ya es la convención de la clase).
4. Reescribir el service para delegar; borrar `import java.sql.*`, `rollback()` y `closeConn()`.
5. Adaptar los mocks de `EquipoOtrosCorreccionServiceTest`.

## Verificación

```bash
mvn clean compile
mvn test        # baseline actual: 526 tests, 0 fallos
```

Además, chequeo de que el SQL efectivamente se fue:

```bash
grep -n "java.sql\|ResultSet\|PreparedStatement" \
  src/main/java/com/example/features/equipos/otros/service/EquipoOtrosCorreccionService.java
# esperado: sin resultados

grep -rln "java.sql" src/main/java --include=*.java | grep -i service
# esperado: sin resultados (queda toda la capa service limpia de JDBC)
```
