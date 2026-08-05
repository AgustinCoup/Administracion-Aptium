# Plan: híbrido para las dos strategies muertas (IMaterialFilter + ICapacidadCalculator)

**Estado:** aprobado, sin ejecutar.
**Origen:** revisión de arquitectura del 2026-07-22. Es el hallazgo #2 del diagnóstico.
El hallazgo #1 (fallos silenciosos en ortopedias) YA se ejecutó y quedó en verde
(533 tests) — ver más abajo. Este plan cubre el #2 en **dos pasos separados y
verificables**. Faltan por atacar #3 (`AppModel`), #4 (`Object[]`→records),
#5 (`common`↔Swing), #6 (concurrencia/EDT).

---

## Diagnóstico (por qué)

Ambas interfaces se instancian en `AppContext`, se exponen con getter, y **no tienen
consumidores en producción** (solo sus tests). Nacieron en el commit "Emprolijación de
arquitectura" como abstracciones especulativas; el código que las debía usar las esquivó
o se reescribió después. **Las dos NO son simétricas** — lo más limpio las trata distinto:

- **`IMaterialFilter`/`MaterialFilterImpl`**: wrapper delgado sobre `EstadoValidator`
  (que sí está vivo y se usa). Su regla "¿es entregable?" ya vive en
  `EstadoValidator.esEntregable`. Mantenerla = dos dueños de la misma regla. → **borrar**.
  La duplicación real: `EquiposParaEntregarController` inlinea
  `getEstado().getOrden() >= ESTERILIZADO.getOrden()` en vez de llamar a `esEntregable`.

- **`ICapacidadCalculator`/`CapacidadCalculatorImpl`**: concepto cohesivo legítimo
  (volumen total, cabe, %, umbral 80%, espacio disponible) con duplicación **real**:
  el umbral vive como `0.8` mágico en dos sitios + constante enterrada en la clase muerta.
  → **adoptarla** como único dueño (no borrarla).

---

## PASO 1 — Materiales: borrar MaterialFilter, rutear a EstadoValidator

**Chico, bajo riesgo. Ejecutar y verificar antes del paso 2.**

### 1a. Borrar archivos
- `src/main/java/com/example/features/equipos/ortopedias/service/IMaterialFilter.java`
- `src/main/java/com/example/features/equipos/ortopedias/service/MaterialFilterImpl.java`
- `src/test/java/com/example/features/equipos/ortopedias/service/MaterialFilterImplTest.java`

### 1b. Quitar cableado en `AppContext` (todas las refs a `materialFilter`)
Líneas actuales: imports 21-22, campo 48, param constructor 68, null-check 81,
asignación 97, `createDefault` local+instanciación 140, uso en el `new AppContext(...)` 157,
getter 205-207. **Ojo:** el constructor de `AppContext` pierde un parámetro → ajustar la
llamada de `createDefault`. `AppModel` NO tiene campo `materialFilter` (no lo toca).
Verificar que ningún test construya `AppContext` directamente (grep dio: nadie lo hace).

### 1c. Exponer `esEntregable` en `AppModel`
`AppModel` ya tiene el campo `estadoValidator` y el método `esAvanzableManualmente`.
Agregar, en la sección `ESTADO VALIDATOR`:
```java
public boolean esEntregable(EstadoEquipo estado) {
    return estadoValidator.esEntregable(estado);
}
```

### 1d. Rutear `EquiposParaEntregarController` a `model.esEntregable(...)`
Reemplazar los 4 checks inline por la única fuente de verdad. Semántica idéntica
(`esEntregable` = `estado.getOrden() >= ESTERILIZADO.getOrden()`, con null→false):

| Lugar | Hoy | Después |
|---|---|---|
| `equipoEsEntregable` (155-160) | `equipo.calcularEstado().getOrden() >= ESTERILIZADO.getOrden()` | `equipo != null && model.esEntregable(equipo.calcularEstado())` |
| `materialEsEntregable` (162-167) | `material.getEstado().getOrden() >= ESTERILIZADO.getOrden()` | `material != null && model.esEntregable(material.getEstado())` |
| L106 (otros, negado) | `equipo.calcularEstado().getOrden() < ESTERILIZADO.getOrden()` continue | `if (!model.esEntregable(equipo.calcularEstado())) continue;` |
| L143 (material otros, negado) | `m.getEstado().getOrden() < ESTERILIZADO.getOrden()` continue | `if (!model.esEntregable(m.getEstado())) continue;` |

> Los dos helpers privados (`equipoEsEntregable`/`materialEsEntregable`) pueden quedarse
> como thin-wrappers sobre `model.esEntregable` (más legibles en los call-sites) o
> inlinearse. Preferencia: mantenerlos, solo cambiar su cuerpo.

### 1e. Verificar
```bash
mvn test          # baseline tras hallazgo #1: 533 tests, 0 fallos
grep -rn "MaterialFilter" src   # esperado: sin resultados
```

---

## PASO 2 — Capacidad: adoptar CapacidadCalculator como único dueño

**Más grande. Toca controller + view helpers. Hacer DESPUÉS del paso 1 en verde.**

### Métodos que ya ofrece `CapacidadCalculatorImpl`
`calcularVolumenTotal(List)`, `calcularPorcentajeUso(usada, total)`,
`cabeLaCapacidad(usada, agregar, total)`, `requiereAdvertencia(usada, total)`
(usa `UMBRAL_ADVERTENCIA = 80`), `calcularEspacioDisponible(usada, total)`.

### Sitios inline a rutear al calculator
| Archivo:línea | Hoy | Reemplazo |
|---|---|---|
| `LotesController:473` | `reconciliador.capacidadUsada(...) > autoclave.getCapacidad()` | comparación vía `calc.cabeLaCapacidad` |
| `LotesController:589` | `volumenManual > capacidadTotal` | `!calc.cabeLaCapacidad(0, volumenManual, capacidadTotal)` |
| `LotesController:611` | `porcentaje < 0.8` | `!calc.requiereAdvertencia(usada, total)` |
| `PanelLotesContenido:325` | `porcentaje > 100` | vía `calc.calcularPorcentajeUso` (clamp) |
| `SincronizadorVolumenFinal:55` | `porcentaje < 0.8` | `!calc.requiereAdvertencia(usada, total)` |

### Sutileza de capas que NO se debe romper
`ReconciliadorPendientes.capacidadUsada` (L52) suma `getVolumenTotal()` **excluyendo
los "otros"** (`if (!item.isEsOtros())`). Esa es una regla de negocio específica de lotes
que el calculator genérico NO conoce. Layering correcto: `ReconciliadorPendientes` sigue
siendo el adaptador específico de lotes (mantiene el filtro de "otros") y **delega la
aritmética pura** al calculator. No mover el filtro de "otros" al calculator.

### DECISIÓN ABIERTA (resolver al ejecutar): cómo llega el calculator a los consumidores
Hoy los controllers reciben `AppModel`, no `AppContext`. `SincronizadorVolumenFinal` y
`PanelLotesContenido` son helpers de Swing (inyectar un service ahí es incómodo).
Opciones:
- **(a)** Exponer los métodos de capacidad vía `AppModel` (como `estadoValidator`) y que
  los view-helpers reciban solo el resultado ya calculado desde el controller.
- **(b)** Inyectar `ICapacidadCalculator` en `LotesController`/`ReconciliadorPendientes`
  vía `UiCoordinator`, y en los view-helpers referenciar solo la **constante** del umbral
  (extraída a un lugar compartido) para no acoplarlos a un service.

Recomendación tentativa: **(b)** — mantiene a los view-helpers sin dependencia de service,
y el único dato que comparten (el 80%) pasa a ser una constante única en vez de `0.8`
mágico. Confirmar con el usuario antes de ejecutar.

### Verificar
```bash
mvn test
grep -rn "0\.8" src/main/java/com/example/features/lotes   # esperado: sin umbrales mágicos
grep -rn "getCapacidadCalculator\|ICapacidadCalculator" src/main/java     # ahora CON consumidores reales
```

---

## Contexto del hallazgo #1 (ya ejecutado, para referencia)
Se convirtieron 7 `catch (SQLException)` que tragaban el error (2 en `EquipoDAO`:
`actualizar`/`eliminar`; 5 en `AuditoriaDAO`: `registrarCambio`, `registrarEquipoEliminado`,
`registrarMaterialEliminado`, `obtenerPorEquipo`, `obtenerTodos`) a `throw DatabaseException`,
y `EquipoCorreccionService.eliminarEquipo` ahora verifica el boolean de `equipoDAO.eliminar`.
Test nuevo: `eliminarEquipo_borradoNoAfectaFilas_lanzaDatabaseException`. Suite: 533 verdes.
Sin commitear (había trabajo previo del plan "otros" en el working tree).
