# Blueprint — Las fracciones de `Equipo*` tienen que existir en la base

**Estado:** diagnóstico cerrado, **las cuatro decisiones (A, B, C, D) cerradas el 2026-08-25**.
Blueprint ejecutable, multi-sesión, modo directo (sin `gh`, commit por paso sobre `ConexionConCDE`).

**Origen:** observaciones 5 y 6 del smoke manual del Paso 8 de
[`salidas-lavadero-listo-y-derivacion-cde.md`](salidas-lavadero-listo-y-derivacion-cde.md) (2026-08-19).
**Rama:** `ConexionConCDE`.

---

## 1. Qué pasa hoy (verificado en código)

Un `Equipo*` (elemento de categoría `EQUIPO` en `catalogo_elementos_lavadero`) se puede **subdividir**:
el operador lo arrastra a un lavarropas, elige en cuáles se reparte (`EquipoSubdivisionDialog`), y el
staging crea **una fila por lavarropas**, todas con el mismo `instanciaId` y `cantidadEnCiclo = 1`. La
card muestra `1/4`.

Ese `instanciaId` es un contador **en memoria** (`CiclosController.java:47`,
`AtomicInteger nextInstanciaId`). Al lanzar el ciclo, lo único que se persiste es
`(elementoClasificacionId, cantidad)` (`CiclosController.java:405`); `elementos_ciclo_lavadero` (V10) no
tiene columna de instancia. **La fracción no se pierde al lanzar: nunca se guardó.**

Consecuencia: un equipo de cantidad 1 repartido en 4 lavarropas escribe 4 filas de `cantidad = 1` contra
una línea de clasificación de cantidad 1. `SQL_DISPONIBLES` calcula `ya_procesada = 4 > 1`; el `HAVING`
esconde el síntoma hasta que Salidas (rama `ConexionConCDE`) suma esas 4 filas y multiplica por 4 lo que
manda al CDE.

## 2. La semántica correcta, confirmada con el usuario

> **Un `Equipo*` repartido en N lavarropas sigue siendo UN equipo.** Las N fracciones consumen **1
> unidad** de la línea de clasificación, no N. Al salir generan **una** fila de Salidas y **un**
> elemento en el ingreso del CDE. **No aparece en Salidas hasta que las N partes pasaron por un ciclo
> finalizado.**

## 3. Las cuatro decisiones

### A — ¿Hace falta persistir la identidad de la instancia? ✅ CERRADA (2026-08-19): **sí**

Se evaluaron tres formas y se descartaron dos: no guardar nada nuevo (el sistema pierde el rastro de las
fracciones que no lanzaron todavía) y una columna `partes` sin identidad (se rompe si dos equipos de la
misma línea se reparten distinto — caso real, confirmado con el usuario). Elegida: tabla
`instancias_equipo_ciclo (id, elemento_clasificacion_id, total_partes)` + columna
`instancia_equipo_id INT NULL` en `elementos_ciclo_lavadero`. Es la única que sobrevive al caso
mezclado. **No reabrir esta decisión.**

### B — ¿Cuándo se crea la instancia si las partes lanzan en momentos distintos? ✅ CERRADA (2026-08-25): **B2-débil**

Se verificó en código que `lanzarTodos()` (`CiclosController.java:417`) y `CicloLavaderoDAO.lanzarCiclo`
(`CicloLavaderoDAO.java:144`) **ya no son atómicos entre lavarropas hoy**: cada lavarropas es su propio
`TransactionalConnection`, con su propia config (jabón, tipo, litros) — son máquinas físicas distintas, y
un fallo en el lavarropas 3 no revierte lo que ya se guardó del 1. El brief asumía una transacción única
que agrupara el lanzamiento conjunto; no existe.

**Decisión:** se mantienen las transacciones separadas por lavarropas (como hoy). Se agrega una
restricción operativa nueva: **si un lavarropas tiene una fracción de un equipo repartido en más de un
lavarropas, "Lanzar" individual de esa card queda bloqueado** — sólo se puede lanzar con "Lanzar Todos".
Antes de lanzar cualquier ciclo del grupo, se valida que **todas** las cards del grupo tengan su config
completa (jabón, tipo, litros); si falta alguna, no se lanza nada del grupo y se avisa cuál falta. La fila
de `instancias_equipo_ciclo` se crea una vez por instancia, antes del loop de lanzamiento, con
`total_partes` ya conocido (nunca cambia: todas las fracciones de una instancia se crean juntas en
`EquipoSubdivisionDialog` y no se pueden devolver parcialmente — `StagingCiclos.quitarInstanciaEquipo`
ya lo garantiza).

**Riesgo residual aceptado:** si una de las N inserciones falla a mitad del loop de "Lanzar Todos" (error
de conexión, no de config — eso ya se valida antes), esa fracción queda en el staging (no se limpia,
como ya pasa hoy) y es reintentable. Si el operador abandona sin reintentar, queda una instancia con
`total_partes` mayor a las filas que realmente la referencian — no corrompe ninguna suma (nada la cuenta
hasta que **todas** sus partes tengan ciclo finalizado, Paso 5), y la detecta el paso de la decisión D si
hace falta auditarla. Es un caso raro (fallo técnico, no de flujo normal) que no amerita una transacción
cruzada entre N ciclos con configuraciones independientes — ver el intercambio completo en el historial de
diseño de esta sesión (2026-08-25) si hace falta revisar el razonamiento.

**Equipos NO repartidos (un solo lavarropas) no llevan esta restricción**: se lanzan individualmente como
siempre; su instancia (`total_partes = 1`) se crea igual, dentro del mismo flujo de lanzamiento de esa
card.

**Qué se rompería con B2-fuerte (una transacción que agrupe los N ciclos):** bloquearía **todo** el grupo
hasta que **cada** lavarropas tuviera su config completa — incluidos lavarropas con ítems regulares ya
listos para lavar, sólo porque comparten grupo con un equipo repartido. Descartada por
desproporcionada frente al riesgo real.

### C — Aritmética de `cantidad` en las fracciones. ✅ CERRADA (2026-08-25): **cantidad = 1 por fracción**

Verificado: `LavarropasCardTableModel.java:34` muestra `cantidadEnCiclo` tal cual en la columna "Cant.".
Con `cantidad = 0` cada fracción mostraría "0" en la card (dato falso) y cualquier código futuro que sume
`eci.cantidad` sin conocer la regla especial de equipos daría "0 unidades lavadas" en silencio.

**Decisión:** cada fila-fracción sigue con `cantidad = 1` (igual que hoy). El saldo se calcula como:

```sql
SUM(CASE WHEN eci.instancia_equipo_id IS NULL THEN eci.cantidad ELSE 0 END)
  + COUNT(DISTINCT eci.instancia_equipo_id)
```

SQL estándar, corre igual en H2 y MySQL.

### D — Qué se hace con los datos ya escritos. ✅ CERRADA (2026-08-25)

**Confirmado con el usuario:** el bug sólo afectó bases de **desarrollo**; `ConexionConCDE` no está en
producción todavía, no hay ingresos reales de CDE con equipos duplicados.

**Decisión:** la migración es defensiva (agrega columnas `NULL`, no intenta backfill — es imposible sin
saber qué filas viejas eran fracciones de la misma instancia, esa información nunca se guardó). El
blueprint incluye un paso de detección (líneas con `ya_procesada > cantidad` bajo la fórmula de C), sin
reparación automática — sirve para que cualquier base de desarrollo sucia (no sólo la de quien ejecuta
este plan) se delate en vez de fallar en silencio. La limpieza real (resetear la base de desarrollo local)
es un paso operativo del usuario, **fuera** de este blueprint.

## 4. Hallazgo de diseño que el brief no cubría: el impacto en Salidas es mayor al estimado

La sección 5 original decía "SalidaLavaderoDAO: agrupar por instancia y filtrar instancias con partes sin
lavar" como si fuera un WHERE nuevo. Verificado en código (`SalidaLavaderoDAO.java` completo, Pasos 2-8
del plan de Salidas, ya en producción de esta rama): `salidas_lavadero.elemento_ciclo_id` apunta a **una
sola** fila de `elementos_ciclo_lavadero`. Un equipo repartido en 4 lavarropas son 4 filas en 4 ciclos con
4 `fecha_fin` posiblemente distintas — no hay una sola fila que represente "la tanda lavada" del equipo.

**Diseño acordado con el usuario (2026-08-25):**
- Migración nueva **V20**: `salidas_lavadero.elemento_ciclo_id` pasa a `NULL`-able (precedente exacto:
  `V2__equipos_otros.sql:73`, `MODIFY COLUMN codigo_catalogo INT NULL`); se agrega
  `instancia_equipo_id INT NULL` (mismo patrón de V19). Exactamente una de las dos columnas está
  poblada por fila — se valida en el DAO, no con `CHECK` (no hay ninguno en el repo).
- `ElementoLavadoPendiente` y `SalidaLista` cambian su campo `lavarropasNumero: int` por
  `lavarropas: String` (uno o varios números, ya formateados) — así ningún consumidor (dos
  `TableModel`, el diálogo de cantidad, los mensajes de error del DAO) tiene que saber armar la lista
  él mismo.
- **Los derivadores y `ConstructorIngresoCDE` (Pasos 3-6 del plan anterior) no cambian nada**: ya
  consumen `SalidaLista` de forma agnóstica a si es equipo o regular. El límite de abstracción que ese
  plan puso ahí aguanta.
- **`SalidasLavaderoController` tampoco cambia**: `marcarListo` ya pregunta cantidad sólo si
  `pendiente > 1` (`SalidasLavaderoController.java:181`); una instancia de equipo siempre tiene
  `cantidadPendiente() == 1`, así que el spinner nunca se abre para ella — el camino existente ya hace
  lo correcto sin tocarlo.
- **`StagingCiclos` tampoco cambia**: `fraccionesPorInstancia()` ya devuelve, por cada instancia en
  staging, tanto su `total_partes` como (por construcción — cada lavarropas aporta como máximo una
  fracción por instancia) la cantidad de lavarropas que la tienen. No hace falta ningún método nuevo.

## 5. Alcance real (reemplaza la tabla estimada del brief original)

| Área | Qué se toca |
|---|---|
| Migración | `V19` (tabla `instancias_equipo_ciclo` + columna en `elementos_ciclo_lavadero`), `V20` (columnas en `salidas_lavadero`). Dos migraciones nuevas; **ninguna existente se toca**. |
| Modelo | `ElementoCicloMovimiento` gana `instanciaEquipoId` nullable; `ElementoLavadoPendiente`/`SalidaLista` cambian `lavarropasNumero`→`lavarropas` y ganan `instanciaEquipoId`, pierden el campo muerto `cicloId`. Clase nueva `AgrupadorInstanciasSalida` (plana, testeable). |
| DAO | `CicloLavaderoDAO`: `crearInstanciaEquipo`, `SQL_INSERTAR_ELEMENTO`, `SQL_DISPONIBLES`. `SalidaLavaderoDAO`: las dos consultas de lectura, `marcarListo`, `volverALavado`, `derivar`. |
| Service | `CicloLavaderoService.crearInstanciaEquipo` (validación). |
| Controller | `CiclosController`: orquesta la creación de instancias antes de lanzar, bloquea "Lanzar" individual de cards con fracciones repartidas, valida config completa del grupo. |
| Vista | `ElementoLavadoTableModel`, `SalidaListaTableModel`, `PantallaSalidasLavadero.pedirCantidadListo` — todo por el cambio `lavarropasNumero`→`lavarropas`. |
| Datos | Paso de detección de líneas sobregiradas (sin reparación automática). |
| Tests | `CicloLavaderoDAOTest`, `AgrupadorInstanciasSalidaTest`, `SalidaLavaderoDAOTest`, `CicloLavaderoDAOTest`/`SalidaLavaderoDAOTest` de detección, test de integración end-to-end. |

Cruza cuatro capas de dos features (Ciclos y Salidas), tiene dos migraciones y toca camino crítico de dos
pantallas ya en producción de la rama.

---

## Contexto compartido (leer una vez por sesión)

App de escritorio Swing, Java 17, Maven, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.

### Archivos del alcance

| Archivo | Rol |
|---|---|
| [CicloLavaderoDAO.java](../src/main/java/com/example/features/lavadero/dao/CicloLavaderoDAO.java) | JDBC de ciclos. `SQL_DISPONIBLES` (línea 64) y `lanzarCiclo` (línea 144) son el foco de los Pasos 1-2. |
| [CicloLavaderoService.java](../src/main/java/com/example/features/lavadero/service/CicloLavaderoService.java) | Sólo validación, cero JDBC — patrón a seguir para el método nuevo. |
| [CiclosController.java](../src/main/java/com/example/features/lavadero/controller/CiclosController.java) | `lanzarCiclo`/`lanzarTodos`/`ejecutarLanzamiento` (líneas 378-427) es el foco del Paso 3. |
| [StagingCiclos.java](../src/main/java/com/example/features/lavadero/controller/helpers/StagingCiclos.java) | **No se modifica.** `fraccionesPorInstancia()` (línea 173) y `lavarropasDeInstancia()` (línea 140) ya alcanzan. |
| [ElementoCicloItem.java](../src/main/java/com/example/features/lavadero/model/ElementoCicloItem.java) | `instanciaId` (staging, en memoria) — no confundir con `instanciaEquipoId` (DB, Pasos 1-3). |
| [ElementoCicloMovimiento.java](../src/main/java/com/example/features/lavadero/model/ElementoCicloMovimiento.java) | Foco del Paso 1. |
| [SalidaLavaderoDAO.java](../src/main/java/com/example/features/lavadero/dao/SalidaLavaderoDAO.java) | JDBC de salidas, completo. Foco del Paso 5. |
| [ElementoLavadoPendiente.java](../src/main/java/com/example/features/lavadero/model/ElementoLavadoPendiente.java) / [SalidaLista.java](../src/main/java/com/example/features/lavadero/model/SalidaLista.java) | Foco del Paso 4. |
| [SalidasLavaderoController.java](../src/main/java/com/example/features/lavadero/controller/SalidasLavaderoController.java) | **No se modifica** (ver §4). Leer para confirmarlo antes de tocar nada alrededor. |
| [ElementoLavadoTableModel.java](../src/main/java/com/example/features/lavadero/view/helpers/ElementoLavadoTableModel.java) / [SalidaListaTableModel.java](../src/main/java/com/example/features/lavadero/view/helpers/SalidaListaTableModel.java) | Foco del Paso 6. |
| [PantallaSalidasLavadero.java](../src/main/java/com/example/features/lavadero/view/PantallaSalidasLavadero.java) | `pedirCantidadListo` (línea 157) usa `lavarropasNumero()` en el header del diálogo — foco del Paso 6. |
| [EquipoSubdivisionDialog.java](../src/main/java/com/example/features/lavadero/view/EquipoSubdivisionDialog.java) | **No se modifica.** Sigue devolviendo la lista de lavarropas elegidos; el controller decide qué hacer con eso. |

### Migraciones

Última aplicada: `V18__cliente_aptium.sql`. Las nuevas son **V19** (Paso 1) y **V20** (Paso 4).
Los DDL corren igual en H2 (tests, `MODE=MySQL`) y MySQL (producción): un `ALTER TABLE` por sentencia,
sin `AFTER`. Precedentes exactos ya verificados en este repo:
- `ADD CONSTRAINT ... FOREIGN KEY` como sentencia separada: `V12__catalogo_jabones_potenciador.sql:18-19`.
- `MODIFY COLUMN ... NULL` para aflojar una columna existente: `V2__equipos_otros.sql:72-73`.

### Tests

JUnit 5 + Mockito + H2 en memoria (`MODE=MySQL`, ver `AbstractDAOTest.java`). Patrón del repo para lógica
no trivial embebida en flujos de Swing: extraerla a una clase plana testeable
(`AgrupadorIngresosLote`, `StagingCiclos`) — el Paso 4 sigue ese patrón con `AgrupadorInstanciasSalida`.

---

## Invariantes (verificar al cerrar CADA paso)

1. `mvn clean package -q` compila sin errores ni warnings nuevos.
2. `mvn test` en verde; ningún test existente se modifica para que pase.
3. Cero JDBC (`java.sql.*`) en `service/`. Toda transacción vive en un DAO.
4. Cero `javax.swing` en `model/`, `dao/` y `service/`.
5. Ningún literal de pantalla, título o mensaje fuera de `Constantes`.
6. Ninguna migración existente se modifica. Cambios de esquema nuevos van en `V19`/`V20`.
7. El flujo existente de Ciclos y de Salidas sigue funcionando sin cambios de comportamiento
   observables para elementos regulares (no-equipo).
8. Toda cantidad es un entero > 0 y nunca supera el saldo disponible; el chequeo se hace dentro de la
   transacción.
9. Un equipo repartido en N lavarropas consume 1 unidad de su línea de clasificación, nunca N — hay
   test que lo prueba en cada capa que lo toca (Ciclos y Salidas).

---

## Grafo de dependencias

```
Paso 1 (V19 + ElementoCicloMovimiento)
   │
   ├──► Paso 2 (CicloLavaderoDAO: crear instancia, persistir, SQL_DISPONIBLES)
   │        │
   │        ▼
   │    Paso 3 (CiclosController: orquestación, bloqueo, validación de grupo)
   │        │
   ├──► Paso 4 (V20 + AgrupadorInstanciasSalida + records)
   │        │
   │        ▼
   │    Paso 5 (SalidaLavaderoDAO: lectura agrupada, marcarListo/volverALavado/derivar)
   │        │
   │        ▼
   │    Paso 6 (Vista: TableModels + PantallaSalidasLavadero)
   │
   └──► Paso 7 (detección de datos sucios — sólo depende de V19)

Paso 3, Paso 6, Paso 7 ──► Paso 8 (test de integración end-to-end)
                                    │
                                    ▼
                              Paso 9 (documentación)
```

**Paralelizables:** `{2, 4}` de entrada (una vez cerrado el Paso 1) — no comparten archivos ni tablas.
`{3, 6}` en paralelo una vez que sus respectivas cadenas (2→3 y 4→5→6) estén cerradas. `7` es
paralelizable con toda la rama, sólo depende de `1`.

**Modelo por paso:** el más fuerte (Opus) para **2, 3, 4 y 5** (SQL de saldos con agregación por
instancia, orquestación de transacciones múltiples, agrupamiento con casos borde). Modelo por defecto
para **1, 6, 7, 8 y 9**.

---

# Paso 1 — Migración `V19` + `ElementoCicloMovimiento` gana instancia

**Modelo:** por defecto · **Depende de:** nada · **Paralelo con:** nada (es la raíz)

### Contexto

No existe ninguna tabla que identifique una instancia de equipo repartido. Este paso crea el
almacenamiento y prepara el modelo que va a viajar desde el controller hasta el INSERT, sin tocar
todavía ningún DAO ni controller (eso es Paso 2 y 3).

### Tareas

1. **`src/main/resources/db/migration/V19__instancias_equipo_ciclo.sql`**:

```sql
-- Identidad persistida de un Equipo* repartido entre lavarropas. Antes vivía sólo en un
-- AtomicInteger en memoria de CiclosController y se perdía al lanzar el ciclo: N fracciones
-- de un mismo equipo se contaban como N equipos distintos en cualquier suma sobre
-- elementos_ciclo_lavadero (ver plans/fracciones-de-equipo-persistidas.md, decisión A).
--
-- total_partes se fija al lanzar (decisión B): todas las fracciones de una instancia lanzan
-- juntas o no lanza ninguna sin retomar el staging, así que el número final de partes se
-- conoce de entrada y nunca cambia después de creada la fila.
CREATE TABLE instancias_equipo_ciclo (
    id                        INT AUTO_INCREMENT PRIMARY KEY,
    elemento_clasificacion_id INT NOT NULL,
    total_partes              INT NOT NULL,
    FOREIGN KEY (elemento_clasificacion_id) REFERENCES elementos_clasificacion_lavadero(id) ON DELETE RESTRICT
);

-- NULL para elementos regulares y para las filas ya existentes (no hay forma de saber cuáles
-- eran fracciones de un mismo equipo repartido; ver decisión D — sin backfill).
ALTER TABLE elementos_ciclo_lavadero ADD COLUMN instancia_equipo_id INT NULL;

ALTER TABLE elementos_ciclo_lavadero
    ADD CONSTRAINT fk_eci_instancia FOREIGN KEY (instancia_equipo_id)
    REFERENCES instancias_equipo_ciclo(id) ON DELETE RESTRICT;

CREATE INDEX idx_eci_instancia ON elementos_ciclo_lavadero (instancia_equipo_id);
```

2. **`ElementoCicloMovimiento.java`** — agregar campo `Integer instanciaEquipoId` (nullable) y un
   constructor de 3 argumentos; conservar el de 2 argumentos delegando con `null`:

```java
public ElementoCicloMovimiento(int elementoClasificacionId, int cantidad) {
    this(elementoClasificacionId, cantidad, null);
}

public ElementoCicloMovimiento(int elementoClasificacionId, int cantidad, Integer instanciaEquipoId) {
    this.elementoClasificacionId = elementoClasificacionId;
    this.cantidad                = cantidad;
    this.instanciaEquipoId       = instanciaEquipoId;
}

public Integer getInstanciaEquipoId() { return instanciaEquipoId; }
```

3. **Tests:** no hace falta un test dedicado nuevo — la migración se verifica corriendo la suite
   existente de lavadero (H2 levanta el schema completo; si `V19` fuera incompatible, fallarían ahí).
   Si se agrega algo, un `ElementoCicloMovimientoTest` mínimo: el constructor de 2 args deja
   `instanciaEquipoId == null`; el de 3 args lo conserva.

### Verificación

```bash
mvn test -Dtest='*Lavadero*'
mvn clean package -q
```

### Criterio de salida

- [ ] `V19__instancias_equipo_ciclo.sql` existe; los tests de DAO de lavadero pasan (prueba de que H2
      la aplica).
- [ ] `ElementoCicloMovimiento` tiene los dos constructores; el código existente que usa el de 2 args
      compila sin cambios.
- [ ] Los invariantes 1-9 se cumplen (9 todavía no tiene test — recién en Paso 2).
- [ ] Commit: `feat: tabla instancias_equipo_ciclo y ElementoCicloMovimiento gana instancia`

---

# Paso 2 — `CicloLavaderoDAO`: crear instancia, persistir al lanzar, arreglar el saldo

**Modelo:** el más fuerte · **Depende de:** Paso 1 · **Paralelo con:** Paso 4

### Contexto

Dos cosas separadas: (a) un método nuevo para crear la fila de instancia, que el controller (Paso 3) va
a llamar antes de lanzar; (b) la corrección de `SQL_DISPONIBLES` (decisión C), que hoy cuenta cada
fracción como una unidad completa.

### Tareas

1. **`CicloLavaderoDAO.crearInstanciaEquipo(int elementoClasificacionId, int totalPartes) -> int`**:

```java
private static final String SQL_INSERTAR_INSTANCIA =
    "INSERT INTO instancias_equipo_ciclo (elemento_clasificacion_id, total_partes) VALUES (?, ?)";

public int crearInstanciaEquipo(int elementoClasificacionId, int totalPartes) {
    try (TransactionalConnection tx = TransactionalConnection.begin()) {
        Connection conn = tx.get();
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERTAR_INSTANCIA, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, elementoClasificacionId);
            ps.setInt(2, totalPartes);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                tx.commit();
                return id;
            }
        }
    } catch (SQLException e) {
        throw new DatabaseException("Error al crear la instancia de equipo", e);
    }
}
```

Es una transacción propia y separada de `lanzarCiclo` a propósito (decisión B: no hay una transacción
única que agrupe N lavarropas con configs distintas).

2. **`CicloLavaderoService.crearInstanciaEquipo`** — wrapper con `ValidationException.builder()`:
   `elementoClasificacionId > 0`, `totalPartes > 0`.

3. **`SQL_INSERTAR_ELEMENTO`** gana la columna:

```java
private static final String SQL_INSERTAR_ELEMENTO =
    "INSERT INTO elementos_ciclo_lavadero (ciclo_id, elemento_clasificacion_id, cantidad, instancia_equipo_id) VALUES (?, ?, ?, ?)";
```

`insertarMovimientos` setea el 4° parámetro con `ps.setInt(4, m.getInstanciaEquipoId())` o
`ps.setNull(4, Types.INTEGER)` según corresponda.

4. **`SQL_DISPONIBLES`** (decisión C):

```java
private static final String SQL_DISPONIBLES =
    "SELECT ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, " +
    "       COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL THEN eci.cantidad ELSE 0 END), 0) " +
    "         + COUNT(DISTINCT eci.instancia_equipo_id) AS ya_procesada, " +
    "       c.nombre AS cliente, cel.categoria " +
    "FROM elementos_clasificacion_lavadero ecl " +
    "JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id " +
    "JOIN ingresos_lavadero il            ON il.id  = ecl.ingreso_id " +
    "JOIN clientes c                      ON c.id   = il.cliente_id " +
    "LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id " +
    "WHERE il.estado = '" + EstadoIngresoLavadero.CLASIFICADO + "' " +
    "GROUP BY ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad, c.nombre, cel.categoria " +
    "HAVING ya_procesada < ecl.cantidad " +
    "ORDER BY il.id, cel.nombre";
```

5. **Tests `CicloLavaderoDAOTest`** (agregar a los existentes):
   - `crearInstanciaEquipo` devuelve un id > 0 y la fila queda con el `total_partes` pedido.
   - Un elemento con 4 filas en `elementos_ciclo_lavadero`, las 4 con el mismo
     `instancia_equipo_id` → `ya_procesada == 1` en `SQL_DISPONIBLES` (antes daba 4).
   - Dos equipos de la misma línea de clasificación, cada uno con su propia instancia (una sin
     repartir, otra repartida en 3) → `ya_procesada == 2`.
   - Una línea con fracciones de equipo **y** unidades regulares del mismo `elemento_clasificacion_id`
     mezcladas (si el dominio lo permite; si no, dejar constancia en el test de por qué no aplica) →
     la suma combina ambos términos correctamente.
   - `lanzarCiclo` con movimientos que incluyen `instanciaEquipoId` no nulo persiste la columna
     (`SELECT instancia_equipo_id FROM elementos_ciclo_lavadero WHERE ...` después de lanzar).

### Verificación

```bash
mvn test -Dtest=CicloLavaderoDAOTest
mvn test -Dtest='*Lavadero*'
```

### Criterio de salida

- [ ] `crearInstanciaEquipo` en DAO y Service, con validación.
- [ ] `SQL_DISPONIBLES` usa la fórmula de la decisión C; el test que antes daba 4 ahora da 1.
- [ ] `lanzarCiclo` persiste `instancia_equipo_id` cuando el movimiento lo trae.
- [ ] Invariante 9 tiene test en esta capa.
- [ ] Commit: `feat: CicloLavaderoDAO persiste la instancia de equipo y corrige el saldo de disponibles`

---

# Paso 3 — `CiclosController`: orquestar la creación de instancias al lanzar

**Modelo:** el más fuerte · **Depende de:** Paso 2

### Contexto

`StagingCiclos` **no se toca** (ver §4 del blueprint): `fraccionesPorInstancia()` ya da, por cada
instancia en staging, cuántas fracciones tiene — que es exactamente `total_partes`, y por construcción
(`EquipoSubdivisionDialog` no deja elegir el mismo lavarropas dos veces para una instancia) también
es la cantidad de lavarropas que la tienen. Este paso sólo cablea el controller para: (1) bloquear
"Lanzar" individual de una card con una fracción repartida en más de un lavarropas, (2) validar que
todo el grupo tenga config completa antes de lanzar nada del grupo, (3) crear las instancias en la BD
antes del loop de lanzamiento y usar los ids reales al construir los `ElementoCicloMovimiento`.

**No hay `CiclosControllerTest`** (no existe hoy, es un controller Swing sin test dedicado — patrón del
repo). La verificación de este paso es manual/smoke, más lo que ejercite el Paso 8 (test de
integración a nivel service, sin pasar por el controller de Swing).

### Tareas

1. **Bloqueo de "Lanzar" individual** — al principio de `lanzarCiclo(int num)`:

```java
private void lanzarCiclo(int num) {
    String bloqueo = motivoBloqueoPorInstanciaRepartida(num);
    if (bloqueo != null) { pantalla.mostrarError(bloqueo); return; }
    if (!pantalla.confirmar(Constantes.Mensajes.CONFIRMAR_LANZAR_CICLO,
            Constantes.Mensajes.TITULO_LANZAR_LOTE)) return;
    Map<Integer, Integer> instancias = resolverInstancias(List.of(num));
    ejecutarLanzamiento(num, instancias);
    cargarDatos();
}

/** null si se puede lanzar solo; mensaje accionable si tiene una fracción repartida en más de un lavarropas. */
private String motivoBloqueoPorInstanciaRepartida(int num) {
    Map<Integer, Integer> fracciones = staging.fraccionesPorInstancia();
    for (ElementoCicloItem item : staging.pendientesDe(num)) {
        if (item.isEquipo() && item.getInstanciaId() != null
                && fracciones.getOrDefault(item.getInstanciaId(), 1) > 1) {
            return "Lavarropas #" + num + " tiene una fracción de " + item.getElementoNombre()
                 + " repartida en otros lavarropas. Usá \"Lanzar Todos\".";
        }
    }
    return null;
}
```

(Mover el string literal a `Constantes.Mensajes` — invariante 5.)

2. **Validación de config completa del grupo, en `lanzarTodos()`**, antes de crear cualquier
   instancia o lanzar cualquier ciclo:

```java
private void lanzarTodos() {
    List<Integer> conPendientes = staging.lavarropasConPendientes();
    if (conPendientes.isEmpty()) return;
    String faltaConfig = validarConfigDeGruposRepartidos(conPendientes);
    if (faltaConfig != null) { pantalla.mostrarError(faltaConfig); return; }
    if (!pantalla.confirmar(...)) return;
    Map<Integer, Integer> instancias = resolverInstancias(conPendientes);
    for (int num : conPendientes) ejecutarLanzamiento(num, instancias);
    cargarDatos();
}
```

`validarConfigDeGruposRepartidos`: para cada lavarropas de `conPendientes` que tenga una fracción
repartida (mismo criterio que el bloqueo de arriba), verificar que su card tenga `getTipoLavado()` y
`getLitrosJabon()` no nulos; si falta alguno, devolver un mensaje que identifique el lavarropas y el
equipo, y **no lanzar nada** de ese `lanzarTodos()` (ni siquiera los lavarropas que sí estaban listos —
simplifica el caso y evita lanzar la mitad de un grupo repartido mientras el resto espera). Lavarropas
sin fracciones repartidas conservan el comportamiento actual (se saltean individualmente si les falta
config, `ejecutarLanzamiento` ya lo maneja).

3. **`resolverInstancias(List<Integer> lavarropasEnEsteLanzamiento) -> Map<Integer, Integer>`**
   (staging id → id real de BD):

```java
private Map<Integer, Integer> resolverInstancias(List<Integer> lavarropasEnEsteLanzamiento) {
    Map<Integer, Integer> fracciones = staging.fraccionesPorInstancia();
    Map<Integer, Integer> resultado = new HashMap<>();
    for (int num : lavarropasEnEsteLanzamiento) {
        for (ElementoCicloItem item : staging.pendientesDe(num)) {
            if (!item.isEquipo() || item.getInstanciaId() == null) continue;
            int stagingId = item.getInstanciaId();
            if (resultado.containsKey(stagingId)) continue;
            int totalPartes = fracciones.getOrDefault(stagingId, 1);
            int dbId = cicloLavaderoService.crearInstanciaEquipo(item.getElementoClasificacionId(), totalPartes);
            resultado.put(stagingId, dbId);
        }
    }
    return resultado;
}
```

4. **`ejecutarLanzamiento(int num, Map<Integer, Integer> instancias)`** — al construir cada
   `ElementoCicloMovimiento`, si el item es una fracción de equipo usar el constructor de 3 args con
   `instancias.get(item.getInstanciaId())` (nunca debería ser `null` acá; si lo es, es un bug de
   `resolverInstancias` — dejar que reviente con `NullPointerException` en vez de silenciarlo con un
   `Objects.requireNonNull` mudo, o loguearlo y lanzar `IllegalStateException` con contexto).

5. Actualizar las firmas de `lanzarCiclo`/`ejecutarLanzamiento`/`lanzarTodos` de forma consistente;
   revisar que `staging.limpiarLavarropas(num)` siga llamándose sólo tras éxito (comportamiento actual,
   no cambia).

### Verificación

```bash
mvn clean package -q
mvn test -Dtest='*Lavadero*'
```

Smoke manual (no hay test automatizado del controller):
- Subdividir un equipo en 2 lavarropas; intentar "Lanzar" uno solo → bloqueado, mensaje claro.
- "Lanzar Todos" con los dos configurados → los dos ciclos se crean, ambas filas de
  `elementos_ciclo_lavadero` comparten `instancia_equipo_id`.
- "Lanzar Todos" con uno de los dos sin jabón configurado → no se lanza nada, mensaje identifica cuál.
- Un equipo sin repartir (1 lavarropas) se sigue lanzando individual sin cambios.

### Criterio de salida

- [ ] "Lanzar" individual bloqueado sólo para cards con fracción repartida en >1 lavarropas.
- [ ] "Lanzar Todos" no lanza nada de un grupo repartido si falta config en alguna de sus cards.
- [ ] Las instancias se crean con el `total_partes` correcto antes del loop de lanzamiento.
- [ ] Los 4 puntos del smoke manual, verificados y anotados en el commit o en un comentario del PR.
- [ ] Invariantes 1-9 se cumplen.
- [ ] Commit: `feat: CiclosController exige lanzamiento conjunto de equipos repartidos`

---

# Paso 4 — Migración `V20` + `AgrupadorInstanciasSalida` + rediseño de records

**Modelo:** el más fuerte · **Depende de:** Paso 1 · **Paralelo con:** Paso 2 y Paso 3

### Contexto

`salidas_lavadero.elemento_ciclo_id` (V17) apunta a una sola fila de `elementos_ciclo_lavadero`. Un
equipo repartido en N lavarropas son N filas en N ciclos, con N `fecha_fin` posiblemente distintas — no
hay una sola fila que represente "la tanda lavada" del equipo entero. Este paso agrega el
almacenamiento y la lógica de agrupamiento (clase plana, testeable sin BD); el Paso 5 la conecta al DAO.

**Por qué la agregación se hace en Java y no con `GROUP_CONCAT`/`STRING_AGG` en SQL:** para no
introducir una función de agregación de texto que pueda comportarse distinto entre H2 y MySQL. Más
seguro: cada fila cruda (una por fracción) se lee tal cual, y `AgrupadorInstanciasSalida` arma los
grupos en memoria.

### Tareas

1. **`src/main/resources/db/migration/V20__salidas_lavadero_instancia.sql`**:

```sql
-- Un equipo repartido en N lavarropas es UNA salida cuando las N partes terminan, no N
-- salidas (ver plans/fracciones-de-equipo-persistidas.md, decisiones B/C). elemento_ciclo_id
-- pasa a ser opcional porque una salida de instancia no corresponde a una sola fila de
-- elementos_ciclo_lavadero: corresponde a la instancia entera.
--
-- Exactamente una de las dos columnas está poblada por fila; se valida en SalidaLavaderoDAO,
-- no acá — no hay CHECK en ninguna migración de este repo, el patrón es validar en código.
--
-- ALTER separados, sin AFTER. Precedentes: V2 (MODIFY COLUMN ... NULL), V12/V19 (ADD
-- CONSTRAINT como sentencia separada).
ALTER TABLE salidas_lavadero MODIFY COLUMN elemento_ciclo_id INT NULL;
ALTER TABLE salidas_lavadero ADD COLUMN instancia_equipo_id INT NULL;
ALTER TABLE salidas_lavadero
    ADD CONSTRAINT fk_salidas_instancia FOREIGN KEY (instancia_equipo_id)
    REFERENCES instancias_equipo_ciclo(id) ON DELETE RESTRICT;
CREATE INDEX idx_salidas_instancia ON salidas_lavadero (instancia_equipo_id);
```

`ON DELETE RESTRICT` por el mismo motivo que las FKs de V17: nada del lavadero se borra nunca.

2. **Rediseñar `ElementoLavadoPendiente`** (quitar el campo muerto `cicloId` — verificado con
   `grep -rn "cicloId()" src/main/java` → sin resultados; cambiar `lavarropasNumero: int` por
   `lavarropas: String`; agregar `instanciaEquipoId`):

```java
public record ElementoLavadoPendiente(
        Integer elementoCicloId,      // null si es una instancia de equipo
        Integer instanciaEquipoId,    // null si es un elemento regular
        String lavarropas,            // "4" o "1, 2, 3, 4"
        int ingresoId,
        int clienteId,
        String clienteNombre,
        String elementoNombre,
        int cantidadLavada,
        int cantidadYaLista,
        LocalDateTime fechaFinCiclo) {

    public int cantidadPendiente() { return cantidadLavada - cantidadYaLista; }
    public boolean esInstanciaDeEquipo() { return instanciaEquipoId != null; }
}
```

3. **Rediseñar `SalidaLista`** igual (mismo criterio, sin `cantidadPendiente`):

```java
public record SalidaLista(
        int salidaId,
        Integer elementoCicloId,
        Integer instanciaEquipoId,
        String lavarropas,
        int ingresoId,
        int clienteId,
        String clienteNombre,
        String elementoNombre,
        int cantidad,
        LocalDateTime fechaFinCiclo,
        LocalDateTime fechaListo) {

    public boolean esInstanciaDeEquipo() { return instanciaEquipoId != null; }
}
```

4. **`features/lavadero/dao/helpers/FilaInstanciaEquipo.java`** — fila cruda, una por fracción, antes
   de agrupar:

```java
public record FilaInstanciaEquipo(
        int instanciaEquipoId, int elementoClasificacionId, int totalPartes,
        int elementoCicloId, int lavarropasNumero, LocalDateTime fechaFinCiclo,
        int ingresoId, int clienteId, String clienteNombre, String elementoNombre,
        int cantidadYaMarcada) { }   // 0 o 1: si ya existe una fila en salidas_lavadero para esta instancia
```

5. **`features/lavadero/dao/helpers/AgrupadorInstanciasSalida.java`** — clase plana, sin BD ni Swing:

```java
/**
 * Agrupa filas crudas de fracciones de equipo (una por lavarropas) en una sola fila lógica
 * por instancia, para las dos tablas de Salidas. Sin dependencias de JDBC ni Swing — se
 * testea sola, mismo patrón que StagingCiclos.
 */
public final class AgrupadorInstanciasSalida {

    /**
     * Sólo las instancias completas (todas sus partes presentes y con ciclo finalizado) y
     * todavía sin marcar como Listo.
     */
    public List<ElementoLavadoPendiente> agruparPendientes(List<FilaInstanciaEquipo> filas) {
        Map<Integer, List<FilaInstanciaEquipo>> porInstancia = filas.stream()
            .collect(Collectors.groupingBy(FilaInstanciaEquipo::instanciaEquipoId));
        List<ElementoLavadoPendiente> resultado = new ArrayList<>();
        for (List<FilaInstanciaEquipo> grupo : porInstancia.values()) {
            FilaInstanciaEquipo primera = grupo.get(0);
            boolean completa = grupo.size() == primera.totalPartes()
                && grupo.stream().allMatch(f -> f.fechaFinCiclo() != null);
            boolean yaMarcada = grupo.stream().anyMatch(f -> f.cantidadYaMarcada() > 0);
            if (!completa || yaMarcada) continue;
            resultado.add(new ElementoLavadoPendiente(
                null, primera.instanciaEquipoId(), lavarropasTexto(grupo),
                primera.ingresoId(), primera.clienteId(), primera.clienteNombre(),
                primera.elementoNombre(), 1, 0,
                grupo.stream().map(FilaInstanciaEquipo::fechaFinCiclo).max(Comparator.naturalOrder()).orElse(null)));
        }
        return resultado;
    }

    private static String lavarropasTexto(List<FilaInstanciaEquipo> grupo) {
        return grupo.stream().map(FilaInstanciaEquipo::lavarropasNumero).distinct().sorted()
            .map(String::valueOf).collect(Collectors.joining(", "));
    }
}
```

(El Paso 5 agrega un método simétrico para `SalidaLista` a partir de `salidas_lavadero` +
`elementos_ciclo_lavadero` de la instancia, o se decide ahí mismo si conviene una sobrecarga o un
método separado — dejarlo abierto a quien ejecute el Paso 5, con el mismo criterio.)

6. **`AgrupadorInstanciasSalidaTest`** (sin BD):
   - 4 filas, mismo `instanciaEquipoId`, `totalPartes=4`, las 4 con `fechaFinCiclo` no nula → 1
     resultado, `lavarropas == "1, 2, 3, 4"` (verificar orden), `fechaFinCiclo` = la más tardía.
   - 3 de 4 filas presentes (`totalPartes=4`) → no aparece (instancia incompleta, alguna fracción
     nunca se lanzó).
   - 4 de 4 presentes pero una con `fechaFinCiclo == null` (ciclo todavía activo) → no aparece.
   - 4 de 4, todas con `fechaFinCiclo`, pero `cantidadYaMarcada > 0` en alguna → no aparece (ya está
     lista, no pendiente).
   - Dos instancias distintas en la misma lista de filas → dos resultados independientes, sin mezclar
     lavarropas de una con la otra.
   - Lista vacía → lista vacía.

### Verificación

```bash
mvn test -Dtest=AgrupadorInstanciasSalidaTest
mvn test -Dtest='*Lavadero*'
mvn clean package -q
```

### Criterio de salida

- [ ] `V20` existe; los tests de DAO de lavadero pasan (H2 la aplica).
- [ ] `ElementoLavadoPendiente`/`SalidaLista` con las firmas nuevas — **esto rompe la compilación de
      `SalidaLavaderoDAO`, `SalidasLavaderoController`, las dos `TableModel` y
      `PantallaSalidasLavadero` hasta que se ejecuten los Pasos 5 y 6.** Es esperado: dejar anotado en
      el commit que el build queda roto a propósito hasta el Paso 5, o — si se prefiere no romper el
      build en un commit intermedio — fusionar Pasos 4 y 5 en una sola sesión/commit. Decidir al
      ejecutar y anotarlo en "Mutaciones aplicadas".
- [ ] `AgrupadorInstanciasSalida` y su test, con los 6 casos de arriba en verde.
- [ ] Commit: `feat: V20 + AgrupadorInstanciasSalida agrupa fracciones de equipo en Salidas`

---

# Paso 5 — `SalidaLavaderoDAO`: lectura agrupada y escritura por instancia

**Modelo:** el más fuerte · **Depende de:** Paso 4

### Contexto

Reescribe las dos consultas de lectura para incluir las instancias completas (vía
`AgrupadorInstanciasSalida`) y adapta `marcarListo`/`volverALavado`/`derivar` para poder operar sobre
una instancia entera en vez de un `elemento_ciclo_id`. Es el paso de mayor riesgo del blueprint junto
con el 2: toca las cuatro operaciones de escritura de una tabla que ya cruza a CDE.

### Tareas

1. **Query nueva para filas crudas de instancia** (para pendientes):

```sql
SELECT eci.id AS elemento_ciclo_id, eci.instancia_equipo_id, ie.total_partes,
       cl.lavarropas_numero, cl.fecha_fin, ecl.ingreso_id,
       il.cliente_id, c.nombre AS cliente, cel.nombre AS elemento,
       COALESCE((SELECT SUM(sl.cantidad) FROM salidas_lavadero sl
                  WHERE sl.instancia_equipo_id = eci.instancia_equipo_id), 0) AS ya_marcada
FROM elementos_ciclo_lavadero eci
JOIN instancias_equipo_ciclo ie          ON ie.id  = eci.instancia_equipo_id
JOIN ciclos_lavadero cl                  ON cl.id  = eci.ciclo_id
JOIN elementos_clasificacion_lavadero ecl ON ecl.id = eci.elemento_clasificacion_id
JOIN catalogo_elementos_lavadero cel     ON cel.id = ecl.elemento_id
JOIN ingresos_lavadero il                ON il.id  = ecl.ingreso_id
JOIN clientes c                          ON c.id   = il.cliente_id
WHERE eci.instancia_equipo_id IS NOT NULL
```

Mapear a `List<FilaInstanciaEquipo>`, pasar a `agrupador.agruparPendientes(...)`, y **unir** el
resultado con la lista de `ElementoLavadoPendiente` regulares existente (la query de siempre, con
`WHERE eci.instancia_equipo_id IS NULL` agregado a `SQL_PENDIENTES_DE_LISTO` para no duplicar filas que
ahora también aparecen en la consulta de instancias). Ordenar el resultado combinado por
`fechaFinCiclo` como hoy.

2. **Query simétrica para "listas sin destino"**: mismo patrón, pero arrancando desde
   `salidas_lavadero WHERE instancia_equipo_id IS NOT NULL AND destino IS NULL`, join a
   `elementos_ciclo_lavadero` (todas las filas de esa instancia, para armar `lavarropas`) y a
   `ciclos_lavadero` para `fecha_fin` de cada una (tomar el máximo). Agregar al agrupador un método
   `agruparListas(...)` simétrico a `agruparPendientes`, o adaptar el existente — decidir al ejecutar,
   dejar registrado en el commit.

3. **`marcarListo`** — para una marca cuyo `item.esInstanciaDeEquipo()` es verdadero:
   - El saldo no se relee con `SQL_SALDO_PENDIENTE` (que asume `elemento_ciclo_id`): se relee con una
     consulta simétrica sobre `instancia_equipo_id`, o se reusa la misma verificación de "todas las
     partes tienen `fecha_fin`" + "no hay ya una salida para esta instancia" (equivalente al filtro de
     `agruparPendientes`, pero ejecutado dentro de la transacción, sobre datos frescos).
   - La cantidad siempre es 1 (no hay "acumular" para una instancia: se marca entera o no se marca —
     a diferencia de un elemento regular, no tiene sentido "marcar 2 de 4 kilos" de un equipo).
   - El INSERT usa `instancia_equipo_id` en vez de `elemento_ciclo_id` (`NULL`).

4. **`volverALavado`** — el `DELETE ... WHERE id = ? AND destino IS NULL` no cambia (opera sobre el id
   de `salidas_lavadero`, que existe igual para ambos tipos de fila).

5. **`derivar`** — `estamparDestino` tampoco cambia de forma (sigue siendo un `UPDATE` por `salidaId`).
   `finalizarIngresosCompletos` ya suma por `ingreso_id` a través del join existente; verificar que el
   join (`salidas_lavadero sl JOIN elementos_ciclo_lavadero eci ON eci.id = sl.elemento_ciclo_id`) siga
   funcionando para las filas regulares y **no** intente unir con una fila de instancia (`sl` con
   `elemento_ciclo_id IS NULL`) — puede necesitar un `UNION` con el camino
   `sl.instancia_equipo_id → elementos_ciclo_lavadero (cualquiera de las N filas, todas comparten
   ingreso_id si el equipo no se subdividió entre ingresos, lo cual no puede pasar: una instancia nace
   de una sola línea de clasificación, que pertenece a un solo ingreso)`.

6. **Tests** (agregar a `SalidaLavaderoDAOTest`, fixture: ingreso + clasificación de un `Equipo*` +
   varios ciclos finalizados con `instancia_equipo_id` compartido):
   - Instancia con sus 4 partes lavadas (4 `fecha_fin` no nulas) → aparece **una vez** en
     `obtenerLavadosPendientesDeListo()`, con `cantidadPendiente() == 1` y `lavarropas` con los 4
     números.
   - Instancia con 3 de 4 partes lavadas (una en ciclo activo) → **no aparece** en absoluto (ni
     parcial, ni con menos cantidad).
   - `marcarListo` de una instancia completa → pasa a `obtenerListasSinDestino()`, cantidad 1,
     desaparece de pendientes.
   - `marcarListo` de una instancia incompleta → `BusinessException`, nada insertado.
   - `volverALavado` de una salida de instancia sin destino → vuelve a pendientes.
   - `derivar` (cualquier acción) de una salida de instancia → funciona igual que una regular: destino
     estampado, `equipo_otros_id` si corresponde, ingreso pasa a `FINALIZADO` si correspondía.
   - Mezcla en la misma pantalla: una fila regular y una fila de instancia del mismo cliente, ambas
     pendientes, ambas se leen correctamente sin interferir.
   - Regresión: todos los tests ya existentes de `SalidaLavaderoDAOTest` para elementos regulares
     siguen pasando sin modificarlos.

### Verificación

```bash
mvn test -Dtest=SalidaLavaderoDAOTest
mvn test -Dtest='*Lavadero*'
mvn clean package -q
```

### Criterio de salida

- [ ] Las dos consultas de lectura combinan regulares + instancias sin duplicar filas.
- [ ] `marcarListo`/`volverALavado`/`derivar` operan correctamente sobre una instancia.
- [ ] Ningún test existente de elementos regulares se modificó.
- [ ] Invariante 9 tiene test en esta capa (Salidas).
- [ ] Commit: `feat: SalidaLavaderoDAO agrupa y opera sobre instancias de equipo completas`

---

# Paso 6 — Vista: `lavarropasNumero` → `lavarropas` en las dos tablas

**Modelo:** por defecto · **Depende de:** Paso 5

### Contexto

Paso mecánico y acotado: los tres consumidores de `lavarropasNumero()` (identificados por grep) pasan a
usar `lavarropas()` (`String`).

### Tareas

1. **`ElementoLavadoTableModel.java:37`** y **`SalidaListaTableModel.java:38`**: `item.lavarropasNumero()`
   → `item.lavarropas()`; en `getColumnClass`, esa columna deja de ser `Integer.class` (columna 3 en
   ambas) y pasa a `String.class` como el resto de las columnas de texto.

2. **`PantallaSalidasLavadero.java:157-160`** (`pedirCantidadListo`): cambia la firma de
   `DistribucionUnidadesDialog.paraSalidas(frame, elementoNombre, clienteNombre, lavarropasNumero, max)`
   si ese método toma un `int` — pasar el `String` de `lavarropas()` en su lugar. Revisar
   `DistribucionUnidadesDialog.paraSalidas` (si el parámetro es sólo para el header del diálogo, el
   cambio de tipo es directo; si se usa numéricamente en algún lado, avisar y resolver ahí — no debería,
   es sólo texto de encabezado).

3. **`SalidaLavaderoService.java:131-138`** y **`SalidaLavaderoDAO.java:466-468`** (`describir`): usan
   `.lavarropas()` en vez de construir `"lavarropas " + n`.

4. **Tests:** `ElementoLavadoTableModelTest` y `SalidaListaTableModelTest` (ya existen) — actualizar
   los fixtures que construyen `ElementoLavadoPendiente`/`SalidaLista` a mano para pasar `String` en
   vez de `int`, y agregar un caso: una fila con `lavarropas = "1, 2, 3, 4"` se muestra tal cual en la
   columna (sin parseo, sin truncar).

### Verificación

```bash
mvn test -Dtest='*Lavadero*'
mvn clean package -q
```

Smoke manual: abrir Salidas de Lavadero, confirmar que la columna "Lavarropas" muestra un número para
elementos regulares y una lista separada por comas para un equipo repartido (una vez que haya datos
reales generados por los Pasos 3 y 5).

### Criterio de salida

- [ ] Los tres consumidores de `lavarropasNumero()` migrados a `lavarropas()`.
- [ ] `ElementoLavadoTableModelTest`/`SalidaListaTableModelTest` actualizados y en verde, con el caso
      de lista multi-lavarropas.
- [ ] `mvn clean package -q` compila sin errores — cierra la ventana de build roto abierta en el
      Paso 4.
- [ ] Commit: `feat: la vista de Salidas muestra los lavarropas de una instancia repartida`

---

# Paso 7 — Detección de líneas sobregiradas (decisión D)

**Modelo:** por defecto · **Depende de:** Paso 1 (sólo necesita la columna nueva; no necesita el resto
de la cadena) · **Paralelo con:** Pasos 2-6

### Contexto

Decisión D: sin reparación automática, sólo detección. Bases de desarrollo (no sólo la del usuario)
pueden tener datos ya sucios (fracciones que hoy cuentan como equipos independientes). Este paso agrega
una consulta reusable para delatarlo, no una pantalla ni un reparador.

### Tareas

1. **`CicloLavaderoDAO.detectarLineasSobregiradas() -> List<LineaSobregirada>`** (o el DAO que se
   considere más apropiado; puede vivir en una clase de diagnóstico aparte si se prefiere no mezclar
   con el DAO operativo — decidir al ejecutar):

```sql
SELECT ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad,
       COALESCE(SUM(CASE WHEN eci.instancia_equipo_id IS NULL THEN eci.cantidad ELSE 0 END), 0)
         + COUNT(DISTINCT eci.instancia_equipo_id) AS ya_procesada
FROM elementos_clasificacion_lavadero ecl
JOIN catalogo_elementos_lavadero cel ON cel.id = ecl.elemento_id
LEFT JOIN elementos_ciclo_lavadero eci ON eci.elemento_clasificacion_id = ecl.id
GROUP BY ecl.id, ecl.ingreso_id, cel.nombre, ecl.cantidad
HAVING ya_procesada > ecl.cantidad
```

Es la misma fórmula de la decisión C, pero sin el filtro `il.estado = 'CLASIFICADO'` de
`SQL_DISPONIBLES` — acá interesa cualquier línea, esté o no todavía "disponible", porque el dato sucio
puede estar en una línea ya avanzada a `LAVADO`/`FINALIZADO`.

2. **Un record simple `LineaSobregirada(int elementoClasificacionId, int ingresoId, String elemento,
   int cantidad, int yaProcesada)`**.

3. **Cómo se expone:** no hace falta una pantalla. Alcanza con que el método exista, tenga test, y quede
   documentado en el `CLAUDE.md` (Paso 9) cómo correrlo — por ejemplo, desde una consola de
   diagnóstico o un test manual (`@Disabled` con instrucciones, o un `main` de una sola línea en un
   paquete de herramientas si el repo ya tiene precedente de eso; si no lo tiene, no inventar uno nuevo
   — un test JUnit que se corre a mano con `mvn test -Dtest=...` y loguea el resultado alcanza).

4. **Test:** con fixture de datos sucios (4 filas sin `instancia_equipo_id` contra una línea de
   cantidad 1) → aparece en el resultado. Con datos limpios (4 filas con `instancia_equipo_id`
   compartido) → no aparece. Con una línea exactamente en el límite (`ya_procesada == cantidad`) → no
   aparece (no está sobregirada, está completa).

### Verificación

```bash
mvn test -Dtest=CicloLavaderoDAOTest
```

### Criterio de salida

- [ ] El método de detección existe, con su record y su test con los 3 casos.
- [ ] No hay reparación automática en ningún lado de este paso.
- [ ] Commit: `feat: detección de líneas de clasificación sobregiradas`

---

# Paso 8 — Test de integración end-to-end

**Modelo:** por defecto · **Depende de:** Pasos 3, 6, 7 (todo el pipeline tiene que estar cerrado)

### Contexto

El escenario pedido: clasificar un equipo, repartirlo en N lavarropas, lanzar, finalizar, y verificar
que Salidas muestra **una** fila y sólo después de que las N partes terminaron. `CiclosController` es
Swing y no tiene test dedicado (Paso 3), así que este test opera a nivel `Service`/`DAO`, replicando lo
que el controller orquesta (crear instancias antes de lanzar) sin pasar por Swing.

### Tareas

1. **`SalidaLavaderoIntegracionTest`** (o el nombre que siga la convención de tests de integración del
   repo; revisar si existe alguno para tomarlo de molde — si no, seguir el patrón de
   `SalidaLavaderoDAOTest` con fixture más largo), en H2, con los DAOs/Services reales:

```java
// 1. Crear ingreso, clasificación de un Equipo* con cantidad 1.
// 2. Crear instancia: cicloLavaderoService.crearInstanciaEquipo(elementoClasificacionId, 3).
// 3. Lanzar 3 ciclos (uno por lavarropas), cada uno con 1 movimiento
//    ElementoCicloMovimiento(elementoClasificacionId, 1, instanciaId).
// 4. ANTES de finalizar ninguno: cicloLavaderoService.obtenerElementosDisponiblesParaCiclo()
//    NO debe volver a mostrar disponible esa línea (ya_procesada == 1 == cantidad).
// 5. Finalizar 2 de los 3 ciclos: salidaLavaderoService.obtenerLavadosPendientesDeListo()
//    sigue SIN mostrar la instancia (falta 1 parte).
// 6. Finalizar el 3er ciclo: AHORA aparece, una sola fila, cantidadPendiente() == 1,
//    lavarropas con los 3 números.
// 7. marcarListo → aparece en obtenerListasSinDestino(), una sola fila.
// 8. derivar(FUERA_DE_FLUJO o CDE_CLIENTE) → destino estampado, ingreso pasa a FINALIZADO
//    si correspondía, y (si CDE) un solo EquipoOtros con cantidad 1 para ese elemento.
```

2. Verificar explícitamente en el test, con comentarios, cada uno de los invariantes de negocio:
   "1 equipo repartido en N nunca se ve como N en ninguna consulta", y "no aparece en Salidas hasta que
   las N partes están lavadas".

### Verificación

```bash
mvn test -Dtest=SalidaLavaderoIntegracionTest
mvn test
```

### Criterio de salida

- [ ] El test cubre los 8 puntos de arriba y pasa en verde.
- [ ] `mvn test` (suite completa) sigue en verde.
- [ ] Commit: `test: integración end-to-end de un equipo subdividido, de clasificación a CDE`

---

# Paso 9 — Documentación

**Modelo:** por defecto · **Depende de:** todos los anteriores

### Tareas

1. **`CLAUDE.md`**: agregar a la sección de lavadero (o donde corresponda) una nota breve sobre la
   identidad de instancia de equipo — qué tabla, qué invariante garantiza (1 equipo repartido = 1
   unidad consumida, 1 salida), y el link a este documento.
2. **`plans/hallazgos-arquitectura-pendientes.md`**: actualizar la fila de **#7** en la tabla de estado
   (línea 16) de "pendiente" a "hecho", con el commit final, y agregar una línea al bloque de la
   sección `## #7` documentando el cierre (mismo formato que `#3`-`#6`).
3. Revisar que ningún comentario de código quedado de los pasos anteriores mencione "TODO" o
   "temporal" sin resolver.

### Verificación

```bash
mvn test
git log --oneline -10
```

### Criterio de salida

- [ ] `CLAUDE.md` documenta la instancia de equipo.
- [ ] `hallazgos-arquitectura-pendientes.md` #7 marcado hecho.
- [ ] Commit: `docs: cierre de la persistencia de fracciones de equipo (#7)`

---

## Protocolo de mutación del plan

Mismo protocolo que [`salidas-lavadero-listo-y-derivacion-cde.md`](salidas-lavadero-listo-y-derivacion-cde.md#protocolo-de-mutación-del-plan):

- **Partir un paso:** si el diff supera ~400 líneas o toca más de una capa sin necesidad, partirlo en
  `N.a`/`N.b` y anotarlo acá con el motivo. El Paso 5 es el candidato más probable (cuatro operaciones
  de escritura reescritas).
- **Insertar un paso:** numerarlo `N.5`, declarar dependencias, no renumerar los existentes.
- **Saltear un paso:** sólo si su criterio de salida ya se cumple por otro camino, con evidencia.
- **Abandonar:** si un paso resulta inviable, parar y consultar. Los Pasos 3 y 5 tienen los supuestos
  más fuertes (orquestación de transacciones múltiples, cuatro operaciones de escritura reescritas); si
  alguno se cae, el diseño de esa mitad hay que rediscutirlo, no parchearlo.
- **Paso 4 y su build roto a propósito:** si al ejecutar se prefiere no dejar un commit con el build
  roto (ver nota del criterio de salida del Paso 4), fusionar Pasos 4 y 5 en una sola sesión, con dos
  commits internos o uno solo — anotar la decisión acá.

### Mutaciones aplicadas

_(vacío — se completa a medida que se ejecuta)_

---

## Rollback

Modo directo, commit por paso sobre `ConexionConCDE`: `git revert <sha>` alcanza para los Pasos 3, 6, 7,
8 y 9.

Excepciones:
- **Pasos 1 y 4 (las migraciones):** revertir el commit borra el `.sql`, pero lo ya aplicado sigue en
  la BD y en `flyway_schema_history`. Para el Paso 1:
  `DROP TABLE elementos_ciclo_lavadero`... **no** — `elementos_ciclo_lavadero` no se puede dropear
  (tiene datos operativos); hay que `ALTER TABLE elementos_ciclo_lavadero DROP COLUMN instancia_equipo_id`
  y `DROP TABLE instancias_equipo_ciclo`, y borrar la fila del historial. Para el Paso 4:
  `ALTER TABLE salidas_lavadero DROP COLUMN instancia_equipo_id` y revertir el `MODIFY COLUMN` a `NOT NULL`
  **sólo si** no quedó ninguna fila con `elemento_ciclo_id IS NULL` (si ya se derivó algo por esta vía,
  no se puede revertir sin perder esas filas).
- **Pasos 2 y 5:** de los que dependen 3 y 6 respectivamente. Revertirlos aislados rompe la
  compilación; hay que revertir la cadena completa aguas abajo primero.
