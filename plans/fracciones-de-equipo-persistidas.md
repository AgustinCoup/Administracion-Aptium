# Brief — Las fracciones de `Equipo*` tienen que existir en la base

**Estado:** diagnóstico cerrado, diseño **abierto**. Todavía **no es un plan ejecutable**: faltan
cuatro decisiones (§4). Cuando estén tomadas, esto se convierte en un blueprint multi-sesión.

**Origen:** observaciones 5 y 6 del smoke manual del Paso 8 de
[`salidas-lavadero-listo-y-derivacion-cde.md`](salidas-lavadero-listo-y-derivacion-cde.md) (2026-08-19).
**Rama:** `ConexionConCDE`.

---

## 1. Qué pasa hoy (verificado en código, no inferido)

Un `Equipo*` (elemento de categoría `EQUIPO` en `catalogo_elementos_lavadero`) se puede **subdividir**:
el operador lo arrastra a un lavarropas, elige en cuáles se reparte
(`EquipoSubdivisionDialog`), y el staging crea **una fila por lavarropas**, todas con el mismo
`instanciaId` y `cantidadEnCiclo = 1`. La card muestra `1/4`.

Ese `instanciaId` es un contador **en memoria**:

```java
// CiclosController.java:46
private final AtomicInteger nextInstanciaId = new AtomicInteger(1);
```

Al lanzar el ciclo, lo único que se persiste es:

```java
// CiclosController.java:398-400
movimientos.add(new ElementoCicloMovimiento(
    item.getElementoClasificacionId(), item.getCantidadEnCiclo()));
```

`ElementoCicloMovimiento` no tiene campo de instancia y `elementos_ciclo_lavadero` (V10) tampoco tiene
columna donde ponerlo. **La fracción no se pierde al lanzar: nunca se guardó.**

## 2. Las tres consecuencias, en orden de gravedad

**a) La base queda sobregirada.** Un `Equipo*` de cantidad 1 repartido en 4 lavarropas escribe **4
filas de `cantidad = 1`** contra una línea de clasificación de `cantidad = 1`. `StagingCiclos` cuenta
por instancia y en memoria consume 1 unidad
(`instanciasEquipoPorElemento(...).size()`, `StagingCiclos.java:201`), pero
`CicloLavaderoDAO.SQL_DISPONIBLES` hace `COALESCE(SUM(eci.cantidad), 0) AS ya_procesada` y obtiene
**4 contra 1**. El `HAVING ya_procesada < ecl.cantidad` tapa el síntoma —el elemento desaparece de
disponibles, que es lo que el operador espera— así que **el dato inconsistente no se ve por ningún
lado hasta que alguien lo suma.**

**b) Salidas cuenta 4 donde hay 1.** El Paso 2 de Salidas construyó su consulta sobre la premisa
"una fila de `elementos_ciclo_lavadero` = una tanda lavada". Es cierta para elementos regulares y
falsa para equipos subdivididos. La pantalla muestra 4 filas de ese equipo, una por lavarropas.

**c) El CDE recibe 4 equipos.** Derivar esas 4 filas crea 4 `MaterialOtros` de cantidad 1. **Esto ya
cruza la frontera del lavadero**: un dato que antes sólo estaba raro adentro de una feature ahora se
convierte en un ingreso de esterilización equivocado, que se lotea, se esteriliza y se entrega.

**d) Además, se derivan partes sin terminar.** Si de las 4 fracciones sólo 2 pasaron por un ciclo
finalizado, esas 2 ya aparecen en Salidas y se pueden mandar al CDE mientras las otras 2 siguen
lavándose. **Confirmado con el usuario (2026-08-19): un `Equipo*` no debe aparecer en Salidas hasta
que todas sus partes estén lavadas.**

## 3. La semántica correcta, confirmada con el usuario

> **Un `Equipo*` repartido en N lavarropas sigue siendo UN equipo.**
> Las N fracciones consumen **1 unidad** de la línea de clasificación, no N.
> Al salir generan **una** fila de Salidas y **un** elemento en el ingreso del CDE.
> **No aparece en Salidas hasta que las N partes pasaron por un ciclo finalizado.**

## 4. Las cuatro decisiones que faltan

**A — Dónde vive la identidad de la instancia.**
Recomendado: **tabla propia** `instancias_equipo_ciclo (id, elemento_clasificacion_id, total_partes)`
+ columna `instancia_equipo_id INT NULL` en `elementos_ciclo_lavadero`. La alternativa (sólo la
columna, sin tabla) obliga a inferir `total_partes` contando filas, y contar filas es justamente lo
que no se puede hacer si el ciclo se lanza en varias tandas. `total_partes` explícito es lo que
permite responder "¿ya están todas lavadas?" sin adivinar.

**B — Cuándo se crea la instancia, si las partes se lanzan en momentos distintos.**
Hoy se puede lanzar el lavarropas 1 y dejar las otras 3 fracciones en el staging (o descartarlas).
Dos caminos:
1. **Persistir la instancia al confirmar la subdivisión.** Registra la intención completa, pero mete
   estado de staging en la base y obliga a limpiar las instancias que después se descartan.
2. **Exigir que las N fracciones de una instancia se lancen juntas** (bloquear "Lanzar" individual
   cuando el lavarropas tiene fracciones repartidas en otros; obligar a "Lanzar todos"), y crear la
   instancia dentro de esa transacción. El staging sigue siendo efímero y la base nunca ve una
   instancia a medio nacer. **Recomendado**, a costa de una restricción operativa nueva.

**C — Qué pasa con la aritmética de `cantidad`.**
Recomendado: la fila-fracción conserva `cantidad = 1` y lo que cambia es **cómo se suma**:
`SUM(cantidad)` de las filas sin instancia **+** `COUNT(DISTINCT instancia_equipo_id)`. Poner
`cantidad = 0` en las fracciones sería más simple de sumar pero rompe `SQL_ELEMENTOS_DE_CICLO`, que
muestra esa cantidad en la card, y haría aparecer "0" en pantalla.

**D — Qué se hace con los datos ya escritos.**
Hay bases (al menos la de desarrollo) con `ya_procesada > ecl.cantidad`, y posiblemente ingresos ya
derivados al CDE con equipos multiplicados. ¿La migración intenta repararlos —imposible sin saber qué
filas eran fracciones de la misma instancia—, se limpian a mano, o la base de desarrollo se resetea?
**Sin respuesta, la migración se escribe defensiva y el plan incluye un paso de detección** (una
consulta que liste las líneas sobregiradas) sin reparación automática.

## 5. Alcance estimado, una vez tomadas las decisiones

| Área | Qué se toca |
|---|---|
| Migración | `V19` (tabla + columna + índice). **Migración nueva; no se toca ninguna existente.** |
| Modelo | `ElementoCicloMovimiento` gana la instancia; record nuevo para la instancia |
| DAO | `CicloLavaderoDAO`: `SQL_DISPONIBLES`, `SQL_ELEMENTOS_DE_CICLO`, el insert de movimientos, y la creación de la instancia dentro de la transacción de lanzamiento |
| Controller | `CiclosController` deja de generar ids en memoria; `StagingCiclos` reporta el reparto al lanzar |
| Salidas | `SalidaLavaderoDAO`: agrupar por instancia y filtrar las instancias con partes sin lavar |
| Datos | Paso de detección de líneas sobregiradas |
| Tests | DAO de ciclos, DAO de salidas, `StagingCiclos`, y un test de integración del ciclo completo con un equipo subdividido |

Cruza cuatro capas, tiene una migración y toca el camino crítico de dos pantallas ya en producción de
la rama. **Es un blueprint multi-sesión, no un paso.**

## 6. Mientras tanto

La pantalla de Salidas queda con el defecto conocido. La decisión de si se bloquea la derivación de
`Equipo*` al CDE hasta que esto se resuelva, o se documenta y se convive con ella, forma parte de la
decisión **D**.
