> ✅ **CERRADO el 2026-09-21.** SHAs de cada paso:
> - Paso 1 (V24): `a82e6cb`
> - Paso 2 (modelo): `a5556fc`
> - Paso 3 (DAO): `48ce4a1`
> - Paso 4 (service): `c8d4977`
> - Paso 5 (card): `4936e97`
> - Paso 6 (controller/Ver Ciclos/cableado): `20f9d7e`, aprobación del smoke: `da1702f`
> - Paso 7 (revisión, cobertura, docs, cierre): ver commit de este mismo cambio

# Plan A — Configuración del ciclo de lavado: fuera los litros totales, adentro los insumos extra

**Objetivo:** sacar **"L Tot."** (litros totales) de la configuración de un ciclo, y reemplazar los
dos checkboxes fijos **Suavizante / Potenciador** por una **lista dinámica de insumos extra** que
sale de un catálogo. El jabón y sus mililitros quedan exactamente como están.

**Rama:** `PrimeraRevisionLavadero` (creada, parte de `main`, árbol limpio) · **Modo:** directo, un
commit por paso, sin PRs (`gh` no está en la máquina)
**Fecha de creación:** 2026-09-21
**Siguiente plan:** [`ajustes-lavadero-catalogos.md`](ajustes-lavadero-catalogos.md) — se ejecuta
**después** de éste, y depende de él (el jabón automático y el copiar/pegar necesitan la lista de
insumos que este plan crea).

---

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Litros totales | **Se borra la columna** `ciclos_lavadero.litros_totales`, no se oculta. Un dato que nadie mira y que sigue siendo obligatorio para lanzar es peor que no tenerlo. |
| Insumos extra | Cada fila es **sólo el insumo**, sin mililitros. Los mL siguen existiendo únicamente para el jabón. |
| Datos existentes | Los ciclos con `suavizante`/`potenciador` en `TRUE` **se migran** a filas de insumos. No se pierde historia. |
| Catálogo inicial | `Suavizante` y `Potenciador`, cargados como insumos por la migración. |

### Decisiones de diseño tomadas por el plan (con su porqué)

| Tema | Decisión | Por qué |
|---|---|---|
| Tabla puente `insumos_ciclo_lavadero`, no una columna `VARCHAR` con nombres separados por coma | PK compuesta `(ciclo_id, insumo_id)` | La coma no tiene FK: un insumo renombrado deja los ciclos viejos con el nombre viejo y uno borrado no se puede detectar. Además la PK compuesta **es** la regla "un insumo no se repite en un ciclo", sin código que la sostenga. |
| `catalogo_insumos` nace ya con `activo BOOLEAN NOT NULL DEFAULT TRUE` | Aunque en este plan nadie la lee | El plan B da de baja insumos. Crear la columna acá cuesta una línea; agregarla después cuesta otra migración sobre una tabla que este plan acaba de crear. |
| `InsumoCatalogo` es un `record`, a diferencia de `JabonCatalogo` que es clase | `record InsumoCatalogo(int id, String nombre, boolean activo)` con `toString()` → `nombre` | La card tiene que **rechazar duplicados** en su lista, y eso es comparar por identidad de valor. Un `record` trae `equals`/`hashCode` gratis; `JabonCatalogo` no los tiene y por eso el combo de jabón compara por referencia (le alcanza, porque sus ítems salen todos de la misma lectura). Aun así, **la deduplicación de la card se hace por `id()`, no por `equals`** — ver el anti-patrón correspondiente. |
| Los insumos de un ciclo se leen en una **consulta aparte**, agrupada en Java | Nunca un `GROUP_CONCAT` ni un `LEFT JOIN` a la consulta maestra | `GROUP_CONCAT`/`STRING_AGG` se comportan distinto entre H2 (tests) y MySQL (producción) — es la misma razón escrita en el javadoc de `TextoLavarropas`. Y un `LEFT JOIN` multiplica las filas de ciclo, que es el bug que `HistorialLavaderoDAO` documenta para `cantBolsas`. |
| Esa consulta aparte trae **todos** los insumos de un tirón, sin `IN (?,?,…)` | Un `SELECT` sobre `insumos_ciclo_lavadero` filtrado por el mismo criterio que la consulta de ciclos (todos / activos) | Armar un `IN` con N parámetros obliga a construir SQL por concatenación y rompe el `PreparedStatement` cacheable. El volumen es el mismo que ya trae el snapshot de ciclos, que es lo que Ver Ciclos lee entero de todos modos. |
| `tieneConfiguracionCompleta()` pasa de 4 campos a **3** | Tipo, jabón y mL de jabón | Los insumos extra son **opcionales**: un ciclo sin suavizante es un ciclo válido y hoy lo es. Exigirlos convertiría una lista vacía en un error que no existe. |
| La migración de datos va **antes** del `DROP COLUMN`, en la misma migración | Orden: crear catálogo → crear puente → `INSERT … SELECT` → `DROP` | Al revés, el `DROP` se lleva los datos y el `INSERT … SELECT` posterior no falla: inserta cero filas, en silencio. Es un error que no deja rastro. |

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.

### Reglas duras del repo que este plan debe respetar

1. **Ningún acceso a BD en el EDT.** `TareaUI` (`ui/common/`) es el único mecanismo de trabajo en
   fondo. `EdtGuard` grita en el log si alguien vuelve a poner I/O en el EDT.
2. **El estado mutable de un controller se lee y escribe sólo en el EDT.** En Ciclos eso incluye el
   `staging`, los `ciclosActivos` y toda la configuración de las cards.
3. **Una migración ya escrita no se toca.** Si hace falta otro cambio, migración nueva.
4. **Lanzar es todo o nada.** `CicloLavaderoDAO.lanzarTanda` es la **única** escritura de
   lanzamiento y corre en una sola transacción. Los insumos se escriben **adentro** de ella.
5. **Ninguna operación mantiene dos conexiones abiertas a la vez.** El semáforo de
   `ConnectionPool` reparte `MAX_POOL − RESERVA` = 5 permisos; si una operación anidara dos
   conexiones serían 5 × 2 = 10 > 8 y el pool se agotaría **con el techo puesto**.
   `HistorialLavaderoDAO` toma cuatro, pero **secuencialmente**. Este plan agrega una segunda
   lectura a tres métodos del DAO de ciclos: **tiene que ser secuencial**, nunca anidada.
6. **Lógica de negocio embebida en Swing → clase plana sin Swing, testeada en aislamiento**
   (`ConstructorVistaCiclos`, `SincronizadorVolumenFinal`, `AgrupadorInstanciasSalida`).
7. **`recargar()` de Ciclos NO resetea la configuración que el operador está tipeando.** Sólo
   `abrirPantalla()` la resetea, y únicamente en las cards libres. La lista de insumos es
   configuración: le aplica la misma regla.
8. **Nada del lavadero se borra nunca** — no hay un solo `DELETE` sobre ingresos/clasificación/ciclos.
   El `ON DELETE CASCADE` de `insumos_ciclo_lavadero` existe por simetría con
   `elementos_ciclo_lavadero`, no porque alguien borre ciclos.

### Esquema relevante (estado actual, antes de este plan)

```sql
-- V10
lavarropas(numero PK, capacidad_litros)
ciclos_lavadero(id PK, lavarropas_numero FK, litros_jabon, suavizante,
                litros_totales NULL, fecha_inicio, fecha_fin NULL, estado)
elementos_ciclo_lavadero(id PK, ciclo_id FK CASCADE, elemento_clasificacion_id FK RESTRICT, cantidad)
-- V12: DROP tipo_jabon; ADD jabon_id FK→catalogo_jabones RESTRICT; ADD potenciador
catalogo_jabones(id PK, nombre UNIQUE)          -- seeds: 'Skip', 'Lider'
-- V15: ADD tipo_lavado VARCHAR(20) NOT NULL    -- LIMPIO | SUCIO, persistido por name()
-- V19/V20: instancias_equipo_ciclo + elementos_ciclo_lavadero.instancia_equipo_id
-- V22: idx_ciclos_lavarropas_fin (lavarropas_numero, fecha_fin)
-- V23: índices de consulta
```

**La próxima migración es la V24.** La última es `V23__indices_consulta.sql`.

### Archivos de referencia (leer antes de escribir código nuevo)

| Para | Leer |
|---|---|
| Patrón de migración compatible H2 + MySQL | `V12__catalogo_jabones_potenciador.sql` — ALTER separados, sin `AFTER` |
| Segunda consulta agrupada en memoria | `HistorialLavaderoDAO` (`SQL_BOLSAS` y su cruce por id) |
| Por qué no `GROUP_CONCAT` | javadoc de `lavadero/dao/helpers/TextoLavarropas.java` |
| Modelo de catálogo análogo | `lavadero/model/JabonCatalogo.java` |
| La transacción de lanzamiento | `CicloLavaderoDAO.lanzarTanda` y sus privados |
| La card | `lavadero/view/LavarropasCard.java` |
| Armado de la config y validación de faltantes | `CiclosController.prepararLanzamiento` (~628-666) |
| Validación de negocio con builder | `CicloLavaderoService.validar` |
| Tabla de Ver Ciclos | `lavadero/view/PantallaVerCiclos.java` (`COLUMNAS`, `actualizarCiclos`) |
| Setup de H2 + Flyway en tests | `src/test/java/com/example/AbstractDAOTest.java` (fíjate en `target("3")`) |

### Comandos

```bash
mvn clean package                                  # target/aptium.jar
mvn test                                           # ~1309 @Test en 120 clases
mvn verify                                         # tests + cobertura JaCoCo
mvn test -Dtest=NombreDeClase
mvn test -Dtest=NombreDeClase#nombreDelMetodo
```

---

## Grafo de dependencias

```
Paso 1 (V24: migración + test de migración)
   │
   ▼
Paso 2 (modelo: InsumoCatalogo, ConfiguracionCiclo, CicloLavadero)
   │
   ├──► Paso 3 (DAO: escritura y lectura de insumos)  ─┐
   └──► Paso 4 (service: validación)                   │   3 y 4 en PARALELO
                                                        │   (archivos distintos)
   └──► Paso 5 (card: lista dinámica de insumos)       ─┘   también paralelo a 3 y 4
                                                        │
                        3 + 4 + 5 ─────────────────────┴──► Paso 6 (controller + Ver Ciclos)
                                                                        │
                                                                        ▼
                                                              Paso 7 (revisión, docs, cierre)
```

| Paso | Modelo sugerido | Archivos que toca (exclusivos salvo aviso) |
|---|---|---|
| 1 | **Opus**, effort alto | `db/migration/V24__*.sql`, un test de migración nuevo |
| 2 | Sonnet | `lavadero/model/` |
| 3 | **Opus**, effort alto | `lavadero/dao/CicloLavaderoDAO.java`, DAO nuevo de insumos, tests |
| 4 | Sonnet | `lavadero/service/CicloLavaderoService.java`, service nuevo, tests |
| 5 | Sonnet | `lavadero/view/LavarropasCard.java`, helper nuevo, tests |
| 6 | **Opus**, effort alto | `CiclosController`, `DatosCiclos`, `PantallaVerCiclos`, `AppContext`, `UiCoordinator` |
| 7 | Sonnet | `CLAUDE.md`, memoria, este archivo |

**Invariantes verificados después de CADA paso:**
- [ ] `mvn test` en verde (o `mvn -q compile` si el paso todavía no cierra la app entera)
- [ ] Cero `new Thread()` / `SwingWorker` nuevos
- [ ] Cero JDBC fuera de un DAO
- [ ] Cero SQL construida por concatenación de input del usuario
- [ ] Ninguna operación abre dos conexiones a la vez

---

## Paso 1 — `V24`: catálogo de insumos, tabla puente, migración de datos y los tres `DROP`

> ⚠️ **Mutado el 2026-09-21** — ver "Mutaciones aplicadas". Los tres `DROP` **no** van en la V24
> sino en una **V25** que escribe el Paso 3. La V24 que se commiteó sólo crea y copia.

### Contexto (autocontenido)

Hay que crear el catálogo de insumos y la tabla que los liga a un ciclo, mudar lo que hoy vive en
los dos booleanos, y recién ahí borrar las tres columnas que dejan de existir.

La migración corre en **MySQL (producción)** y en **H2 en modo MySQL (tests)**. El patrón del repo
para eso es `V12`: un `ALTER TABLE` por sentencia y **sin `AFTER`**, que H2 no soporta.

`DatabaseInitializer` aborta el arranque si la base está en una versión más nueva que la que trae el
JAR (`EsquemaDesactualizadoException`), así que el caso "JAR viejo contra base migrada" ya está
cubierto: el cliente viejo ni siquiera llega a leer las columnas borradas.

### Tareas

1. **`src/main/resources/db/migration/V24__insumos_ciclo_lavadero.sql`**, en este orden exacto:

   ```sql
   -- 1. Catálogo de insumos extra de un ciclo de lavado.
   --    Nace con `activo` aunque todavía nadie lo lea: el plan de Ajustes da de baja insumos,
   --    y agregar la columna después costaría otra migración sobre una tabla recién creada.
   CREATE TABLE catalogo_insumos (
       id     INT AUTO_INCREMENT PRIMARY KEY,
       nombre VARCHAR(100) NOT NULL UNIQUE,
       activo BOOLEAN      NOT NULL DEFAULT TRUE
   );

   INSERT INTO catalogo_insumos (nombre) VALUES ('Suavizante'), ('Potenciador');

   -- 2. Qué insumos lleva cada ciclo.
   --    PK compuesta: un insumo no se repite dentro de un ciclo, y eso lo sostiene la base,
   --    no la card. FK del ciclo en CASCADE (igual que elementos_ciclo_lavadero) y la del
   --    insumo en RESTRICT: un insumo usado por algún ciclo no se puede borrar del catálogo.
   CREATE TABLE insumos_ciclo_lavadero (
       ciclo_id  INT NOT NULL,
       insumo_id INT NOT NULL,
       PRIMARY KEY (ciclo_id, insumo_id),
       FOREIGN KEY (ciclo_id)  REFERENCES ciclos_lavadero(id)  ON DELETE CASCADE,
       FOREIGN KEY (insumo_id) REFERENCES catalogo_insumos(id) ON DELETE RESTRICT
   );

   -- 3. Migración de datos, ANTES de los DROP.
   --    Al revés el DROP se lleva los datos y estos INSERT insertan cero filas sin fallar:
   --    un error silencioso que no deja rastro en ningún log.
   --    El id del insumo se resuelve por nombre porque es AUTO_INCREMENT y no se puede hardcodear
   --    (mismo razonamiento que Constantes.Lavadero.CLIENTE_APTIUM).
   INSERT INTO insumos_ciclo_lavadero (ciclo_id, insumo_id)
   SELECT cl.id, ci.id
   FROM ciclos_lavadero cl
   JOIN catalogo_insumos ci ON ci.nombre = 'Suavizante'
   WHERE cl.suavizante = TRUE;

   INSERT INTO insumos_ciclo_lavadero (ciclo_id, insumo_id)
   SELECT cl.id, ci.id
   FROM ciclos_lavadero cl
   JOIN catalogo_insumos ci ON ci.nombre = 'Potenciador'
   WHERE cl.potenciador = TRUE;

   -- 4. Recién ahora, los tres DROP. ALTER separados y sin AFTER, para H2 y MySQL.
   ALTER TABLE ciclos_lavadero DROP COLUMN suavizante;
   ALTER TABLE ciclos_lavadero DROP COLUMN potenciador;
   ALTER TABLE ciclos_lavadero DROP COLUMN litros_totales;
   ```

   ⚠️ **La V24 no es atómica, y hay que escribir cómo recuperarla ANTES del incidente.** MySQL hace
   **commit implícito en cada DDL**: si el `INSERT … SELECT` falla por lo que sea (una colación rara
   en `nombre`, un timeout, una fila corrupta), `catalogo_insumos` con sus dos seeds e
   `insumos_ciclo_lavadero` ya quedaron commiteadas y Flyway marca la V24 como *failed*. El reintento
   **no arranca**: muere en `CREATE TABLE catalogo_insumos` porque ya existe. Y el orden que este
   paso defiende con razón —migrar antes de dropear— es justamente el que deja esa ventana abierta
   más tiempo. El procedimiento va **en el encabezado del `.sql`**, no en este plan, porque es donde
   lo va a leer quien tenga el problema:

   ```
   -- RECUPERACIÓN si esta migración queda en estado `failed`:
   --   1. flyway repair            (borra la fila fallida de flyway_schema_history)
   --   2. DROP TABLE IF EXISTS insumos_ciclo_lavadero;
   --      DROP TABLE IF EXISTS catalogo_insumos;
   --   3. reintentar la migración
   -- Hace falta hacerlo a mano porque MySQL hace commit implícito en cada DDL: esta
   -- migración NO se revierte sola, y un reintento sin el paso 2 muere en el CREATE TABLE.
   -- Si el fallo ocurrió DESPUÉS de los DROP COLUMN, restaurar del backup: los booleanos
   -- ya no están y no hay de dónde recalcularlos.
   ```

   El encabezado de comentarios del archivo tiene que decir **por qué** se borra `litros_totales`
   (dato que nadie consulta y que sin embargo bloquea el lanzamiento) y **por qué** los dos
   booleanos se vuelven filas (porque la lista de insumos crece y un booleano por insumo obliga a
   una migración por insumo nuevo). El repo escribe las migraciones así — ver `V15` y `V16`.

2. **Test de la migración de datos** —
   `src/test/java/com/example/infrastructure/db/MigracionV24Test.java`.

   Es el único test que puede probar esto: el H2 de `AbstractDAOTest` se construye **entero** desde
   cero, así que nunca existen filas con `suavizante = TRUE` esperando a ser migradas.

   Forma:
   - `HikariDataSource` propio con una **URL de H2 distinta** —
     `jdbc:h2:mem:aptium_mig_v24;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE`.
   - ⚠️ **No llamar a `ConnectionPool.setDataSourceForTesting(...)`.** `AbstractDAOTest` lo usa para
     apuntar el pool global al H2 compartido; pisarlo desde acá deja al resto de la suite leyendo la
     base equivocada según el orden en que Surefire corra las clases. Este test habla con su
     `DataSource` directo, sin pasar por `ConnectionPool`.
   - Replicar las **fases 1 y 2** de `AbstractDAOTest.setupH2()` (el `target("3")` + el
     `ALTER`/`INSERT IGNORE` sintético de V4), y después migrar con `.target("23")`.
   - Insertar a mano **tres ciclos**, y nada más: `ciclos_lavadero` sólo tiene FK a `lavarropas`
     (sembrado 1..13 por V7) y a `catalogo_jabones` (sembrado por V12). **Cliente, ingreso y
     clasificación no hacen falta** — hacen falta para `elementos_ciclo_lavadero`, que este test no
     toca. Lo que sí hay que respetar son las columnas `NOT NULL` que la tabla tiene en V23:
     `lavarropas_numero`, `litros_jabon`, `tipo_lavado` (desde V15, **sin default**: un `INSERT` que
     la omita falla), y `jabon_id`, que se resuelve con
     `SELECT id FROM catalogo_jabones WHERE nombre = 'Skip'` — **por nombre, no hardcodeado**, el
     mismo argumento que usa la migración para `catalogo_insumos`.
   - Los tres ciclos: uno con `suavizante = TRUE, potenciador = FALSE`, otro con los dos en `TRUE`,
     otro con los dos en `FALSE`.
   - Migrar sin `target` (llega a V24).
   - Aserciones: el primero tiene **1** fila en `insumos_ciclo_lavadero` y es `Suavizante`; el
     segundo tiene **2**; el tercero **0**; `catalogo_insumos` tiene las dos filas con
     `activo = TRUE`; y las columnas `suavizante`, `potenciador`, `litros_totales` **ya no existen**
     (consultar `INFORMATION_SCHEMA.COLUMNS`, no atrapar el `SQLException` de un `SELECT`).

### Verificación

```bash
mvn test -Dtest=MigracionV24Test
mvn test                          # toda la suite: Flyway corre V24 en el H2 compartido
```

### Criterio de salida

- [ ] `mvn test` en verde con la V24 aplicada en el H2 de toda la suite
- [ ] El test de migración prueba los tres casos (uno, dos, ninguno) y la desaparición de las columnas
- [ ] `MigracionV24Test` **no** toca `ConnectionPool.setDataSourceForTesting`
- [ ] La migración no tiene ningún `AFTER` ni sentencia específica de MySQL
- [ ] Commit: `feat: V24 catalogo de insumos y tabla puente de ciclos`

---

## Paso 2 — Modelo: `InsumoCatalogo`, `ConfiguracionCiclo`, `CicloLavadero`

> Depende del Paso 1 sólo conceptualmente (el modelo describe el esquema nuevo). Después de este
> paso **la app no compila** hasta el Paso 6: es esperado, y por eso los pasos 3, 4 y 5 son
> paralelos entre sí pero todos posteriores a éste.

### Contexto (autocontenido)

`ConfiguracionCiclo` es el `record` con el que viaja la configuración de un ciclo desde la card
hasta el `INSERT`. Hoy es
`(tipoLavado, jabon, litrosJabon, suavizante, potenciador, litrosTotales)`. `CicloLavadero` es el
modelo de lectura, con dos constructores (uno sin materiales que delega en el otro).

### Tareas

1. **`lavadero/model/InsumoCatalogo.java`** — `record`:
   ```java
   public record InsumoCatalogo(int id, String nombre, boolean activo) {
       @Override public String toString() { return nombre; }
   }
   ```
   Javadoc: por qué es `record` y `JabonCatalogo` no (la card deduplica insumos, y eso es
   comparación por valor); y que `activo` existe para el plan de Ajustes — hoy todas las lecturas
   lo devuelven en `true`.

2. **`lavadero/model/ConfiguracionCiclo.java`**:
   ```java
   public record ConfiguracionCiclo(TipoLavado tipoLavado, JabonCatalogo jabon,
                                    BigDecimal litrosJabon, List<InsumoCatalogo> insumos) {
       public ConfiguracionCiclo {
           insumos = insumos == null ? List.of() : List.copyOf(insumos);
       }
   }
   ```
   - El constructor compacto normaliza `null` a lista vacía: **los insumos son opcionales** y un
     `null` que se cuela desde la card no tiene que reventar en el DAO.
   - Actualizar el javadoc: `@param insumos` dice *"insumos extra del ciclo; puede estar vacía —
     un ciclo sin suavizante es un ciclo válido"*. Borrar los `@param` de los tres campos que se van.

3. **`lavadero/model/CicloLavadero.java`**:
   - Sacar los campos `suavizante`, `potenciador`, `litrosTotales` y sus getters.
   - Agregar `private final List<InsumoCatalogo> insumos;` con `getInsumos()` que devuelve
     `List.copyOf(insumos)` (defensiva, como `getMateriales()`).
   - Los dos constructores pierden tres parámetros y ganan uno. El constructor corto (el que hoy
     delega pasando `new ArrayList<>()` de materiales) sigue delegando.
   - `insumos == null` → `List.of()` en ambos constructores, por lo mismo que arriba.

4. **`CicloLavaderoTest`** — es el único test que este paso rompe *y* arregla: assertea los tres
   getters que se van (`getLitrosTotales()`, `isSuavizante()`, `isPotenciador()`). Reescribirlo acá,
   con un caso nuevo para `getInsumos()` (defensiva: modificar la lista devuelta no toca el modelo) y
   otro para `insumos == null` → lista vacía. Los **otros siete** tests que construyen estos dos
   tipos los ajusta el Paso 3 — ver su lista literal.

### Verificación

```bash
mvn -q compile 2>&1 | head -40     # va a fallar en DAO/card/controller: es lo esperado
mvn test -Dtest=CicloLavaderoTest  # este sí tiene que pasar al terminar el paso
```

### Criterio de salida

- [ ] `ConfiguracionCiclo` y `CicloLavadero` ya no nombran `suavizante`, `potenciador` ni `litrosTotales`
- [ ] Los dos aceptan `null` en `insumos` y lo normalizan a lista vacía
- [ ] El javadoc dice que los insumos son opcionales
- [ ] `CicloLavaderoTest` reescrito y en verde
- [ ] Commit: `refactor: el modelo del ciclo lleva insumos en vez de dos booleanos`

---

## Paso 3 — DAO: escribir los insumos en la transacción y leerlos sin N+1

> **Paralelo con los Pasos 4 y 5.** No comparte archivos con ninguno. Depende del Paso 2.

### Contexto (autocontenido)

`CicloLavaderoDAO.lanzarTanda` es la **única** escritura de lanzamiento de la app y corre en una
sola transacción (`TransactionalConnection`): crea las instancias de equipo repartido y todos los
ciclos de la tanda. Si algo falla, no queda nada escrito. Los insumos de cada ciclo se escriben
**adentro** de esa misma transacción, justo después del `insertarCiclo` que devuelve el id — por el
mismo motivo que los elementos: un ciclo lanzado con la mitad de su configuración es un ciclo que
nadie puede corregir.

Del lado de la lectura hay **tres** métodos que devuelven ciclos y los tres tienen que traer sus
insumos:

| Método | Quién lo usa | Alcance |
|---|---|---|
| `obtenerCiclosActivosPorLavarropas()` | Pantalla de Ciclos (`DatosCiclos`) | los `fecha_fin IS NULL` |
| `obtenerCiclosFinalizados()` | — | los `fecha_fin IS NOT NULL` |
| `obtenerTodosLosCiclos()` | Ver Ciclos (snapshot completo, pagina en memoria) | todos |

### Tareas

0. **`V25__drop_booleanos_y_litros_totales_ciclo.sql`** (heredado del Paso 1, ver "Mutaciones
   aplicadas"): los tres `ALTER TABLE ciclos_lavadero DROP COLUMN` (`suavizante`, `potenciador`,
   `litros_totales`), separados y sin `AFTER`, en el **mismo commit** que saca esas columnas del SQL
   de `CicloLavaderoDAO` y de `SembradorRendimiento`. Encabezado: si falla *después* de algún
   `DROP`, restaurar del backup — los booleanos ya no están y no hay de dónde recalcularlos.
   Extender `MigracionV24Test` con la aserción que el Paso 1 no pudo hacer: las tres columnas ya
   no existen (`INFORMATION_SCHEMA.COLUMNS`, no atrapar el `SQLException`), y subir
   `DatabaseInitializerTest.sanityMaximoLocal` a 25.

1. **`lavadero/dao/CatalogoInsumosDAO.java`** — nuevo, misma forma que `CatalogoJabonesDAO`:
   ```java
   public List<InsumoCatalogo> findAll()        // ORDER BY nombre
   ```
   Hoy no filtra por `activo` (todavía no hay quien lo ponga en `false`); el plan B agrega
   `findActivos()`. Manejo de errores **como `SalidaLavaderoDAO`, no como `CatalogoJabonesDAO`**:
   un fallo de SQL sale como `DatabaseException`, no como lista vacía. Una lista vacía le dice a la
   card "no hay insumos configurados", que no es lo mismo que "no pude leer el catálogo".

2. **`CicloLavaderoDAO`** — escritura:
   - `SQL_INSERTAR_CICLO` pierde `suavizante`, `potenciador`, `litros_totales`:
     ```sql
     INSERT INTO ciclos_lavadero (lavarropas_numero, jabon_id, litros_jabon, tipo_lavado,
                                  fecha_inicio, estado)
     VALUES (?, ?, ?, ?, NOW(), 'ACTIVO')
     ```
     `insertarCiclo` baja de 7 binds a 4 y pierde el `setNull(6, Types.DECIMAL)`.
   - Constante nueva:
     ```sql
     SQL_INSERTAR_INSUMO = "INSERT INTO insumos_ciclo_lavadero (ciclo_id, insumo_id) VALUES (?, ?)"
     ```
   - Privado nuevo `insertarInsumos(Connection conn, int cicloId, List<InsumoCatalogo> insumos)`,
     con `addBatch`/`executeBatch` — **el batch es correcto acá porque a nadie le importa el conteo
     de filas**: no hay guarda de concurrencia sobre los insumos (ver la regla del `executeBatch`
     en `CLAUDE.md`). Si la lista viene vacía, no abre el `PreparedStatement`.
   - En `lanzarTanda`, dentro del `for`:
     ```java
     int cicloId = insertarCiclo(conn, ciclo.lavarropasNumero(), ciclo.config());
     insertarMovimientos(conn, cicloId, movimientosDe(ciclo, instancias));
     insertarInsumos(conn, cicloId, ciclo.config().insumos());
     ```

3. **`CicloLavaderoDAO`** — lectura. Los tres `SQL_ACTIVOS` / `SQL_FINALIZADOS` / `SQL_TODOS`
   pierden `cl.suavizante, cl.potenciador, cl.litros_totales` de su `SELECT`, y los dos mappers
   (`mapearCiclo`, `mapearCicloCompleto`) pierden esos tres `rs.get*`. Los mappers pasan a recibir
   los insumos ya resueltos:
   ```java
   private CicloLavadero mapearCiclo(ResultSet rs, Map<Integer, List<InsumoCatalogo>> insumos)
   ```

   Consulta de insumos — **tres constantes, una por alcance**, todas sin parámetros:
   ```sql
   SQL_INSUMOS_TODOS =
     "SELECT icl.ciclo_id, ci.id, ci.nombre, ci.activo " +
     "FROM insumos_ciclo_lavadero icl " +
     "JOIN catalogo_insumos ci ON ci.id = icl.insumo_id " +
     "ORDER BY icl.ciclo_id, ci.nombre"

   SQL_INSUMOS_DE_ACTIVOS = SQL_INSUMOS_TODOS + un JOIN a ciclos_lavadero con fecha_fin IS NULL
   SQL_INSUMOS_DE_FINALIZADOS = ídem con fecha_fin IS NOT NULL
   ```
   ⚠️ **El `JOIN` a `catalogo_insumos` es interno y NO filtra por `activo`.** Un insumo dado de baja
   tiene que seguir mostrando su nombre en los ciclos viejos: es un join **histórico**. Dejarlo
   escrito en el javadoc de la constante, porque el plan B va a pasar por acá buscando dónde poner
   el `WHERE activo = TRUE` y éste es el lugar donde **no** va.

   Privado nuevo:
   ```java
   /** ciclo_id → sus insumos. Consulta propia, agrupada en memoria. */
   private Map<Integer, List<InsumoCatalogo>> leerInsumos(String sql)
   ```

   ⚠️ **La lectura de insumos va en su propio `try-with-resources`, DESPUÉS de cerrar el de los
   ciclos.** No adentro. `ConnectionPool` reparte 5 permisos y el invariante del que depende esa
   aritmética es que ninguna operación mantenga dos conexiones abiertas a la vez. La forma correcta
   es la de `HistorialLavaderoDAO.obtenerHistorial()`: cuatro lecturas, **secuenciales**. Concretamente:

   ```java
   public List<CicloLavadero> obtenerTodosLosCiclos() {
       List<FilaCicloCruda> filas = new ArrayList<>();
       try (Connection conn = ConnectionPool.getConnection(); ...) {                  // conexión 1
           while (rs.next()) filas.add(leerFila(rs));
       }                                                                              // cerrada
       Map<Integer, List<InsumoCatalogo>> insumos = leerInsumos(SQL_INSUMOS_TODOS);   // conexión 2
       return filas.stream().map(f -> mapearCicloCompleto(f, insumos)).toList();
   }
   ```
   **Los ciclos van primero, y el orden importa.** Las dos lecturas son secuenciales y no comparten
   transacción, así que un ciclo lanzado en el medio cae en una de dos:
   - **Ciclos primero (correcto):** ese ciclo **no está** en la primera lectura, así que no se pinta;
     sus insumos quedan en el mapa sin usarse. Inocuo.
   - **Insumos primero (incorrecto):** ese ciclo **sí** sale en la lista de la segunda lectura, pero
     el mapa de la primera no lo conoce → se pinta con lista vacía, `"—"` en Ver Ciclos y sin
     insumos en la card. Es un **dato faltante disfrazado de "ciclo sin insumos"**, que es la peor
     forma de fallar: no se parece a un error y nadie lo reporta.

   El invariante del semáforo (una conexión por vez) se cumple igual en las dos formas; lo que
   decide el orden es cuál de las dos huerfaniza el dato. Por eso el mapeo se hace **después** de
   cerrar la primera conexión, sobre filas crudas ya leídas.

4. **Tests** — `CicloLavaderoDAOTest` (extiende `AbstractDAOTest`):
   - Los casos existentes que pasan `suavizante`/`potenciador`/`litrosTotales` se reescriben.
   - `limpiarTablas()` tiene que borrar `insumos_ciclo_lavadero` **antes** que `ciclos_lavadero`
     (orden inverso a las FK), aunque el `CASCADE` lo cubriría: la convención del repo es explicitar.
   - Casos nuevos:
     - lanzar una tanda con dos insumos → `insumos_ciclo_lavadero` tiene 2 filas para ese ciclo;
     - lanzar con lista vacía → 0 filas, y `lanzarTanda` no falla;
     - `obtenerTodosLosCiclos()` devuelve los insumos de cada ciclo, y un ciclo sin insumos trae
       lista vacía (no `null`);
     - `obtenerCiclosActivosPorLavarropas()` **también** los trae (es el que alimenta la card);
     - **dos ciclos con insumos distintos no se mezclan** — el test que atrapa un agrupamiento mal
       hecho;
     - un lanzamiento que falla (saldo consumido) **no deja filas** en `insumos_ciclo_lavadero`, que
       es la prueba de que la escritura está adentro de la transacción.
   - `CatalogoInsumosDAOTest`: `findAll()` trae `Suavizante` y `Potenciador` del seed de V24.
   - `ConcurrenciaOptimistaTest`: el caso de lanzamiento cambia de firma al construir la
     `ConfiguracionCiclo`. La guarda no cambia.
   - `perf/SembradorRendimiento`: ajustar el `INSERT` sintético de ciclos.

5. **Los otros siete tests que construyen `ConfiguracionCiclo` o `CicloLavadero`** y que hoy no
   compilan. Es cambio mecánico de firma, pero **si no se hace acá la suite queda rota hasta el
   Paso 6** y los `mvn test -Dtest=…` de los Pasos 4 y 5 dan verde sobre una suite que no compila —
   una señal falsa que manda al ejecutor al paso siguiente creyendo que sólo le falta cablear.
   Lista literal, verificada:

   | Construye | Archivo |
   |---|---|
   | `ConfiguracionCiclo` | `lavadero/dao/HistorialLavaderoDAOTest.java` |
   | `ConfiguracionCiclo` | `lavadero/dao/SalidaLavaderoDAOTest.java` |
   | `ConfiguracionCiclo` | `lavadero/dao/SalidaLavaderoDerivacionTest.java` |
   | `ConfiguracionCiclo` | `lavadero/EquipoSubdivididoIntegracionTest.java` |
   | `CicloLavadero` | `lavadero/controller/helpers/CicloFilterStrategyTest.java` |
   | `CicloLavadero` | `lavadero/controller/helpers/ConstructorVistaCiclosTest.java` |
   | `CicloLavadero` | `lavadero/controller/VerCiclosControllerTest.java` |

   (`lavadero/model/CicloLavaderoTest.java` ya lo reescribió el Paso 2.) En los siete el arreglo es
   el mismo: sacar los tres argumentos que se van, pasar `List.of()` de insumos. **Ninguno cambia de
   intención** — si alguno necesita insumos para probar algo, es que el caso pertenece a
   `CicloLavaderoDAOTest`.

### Verificación

```bash
mvn -q test-compile          # PRIMERO: es lo único que delata los siete tests de arriba
mvn test -Dtest=CicloLavaderoDAOTest
mvn test -Dtest=CatalogoInsumosDAOTest
mvn test -Dtest=ConcurrenciaOptimistaTest
mvn test                     # la suite entera tiene que quedar en verde al cerrar este paso
```

### Criterio de salida

- [ ] `lanzarTanda` escribe los insumos dentro de su transacción, y el test del fallo lo prueba
- [ ] Las lecturas traen **los ciclos primero y los insumos después**, cada uno con su conexión
- [ ] El `JOIN` a `catalogo_insumos` no filtra por `activo`, y el javadoc dice por qué
- [ ] Cero `GROUP_CONCAT` / `STRING_AGG`
- [ ] `mvn test` en verde con la **suite entera**, no sólo con los `-Dtest` puntuales
- [ ] Commit: `feat: persistencia de los insumos extra de un ciclo`

---

## Paso 4 — Service: validación

> **Paralelo con los Pasos 3 y 5.** Depende del Paso 2.

### Contexto (autocontenido)

`CicloLavaderoService.validar(LanzamientoCiclo, ValidationException.Builder)` rechaza una tanda
entera antes de tocar la base, con mensajes prefijados por lavarropas (`"Lavarropas #3: …"`) porque
en una tanda "falta el jabón" solo no ubica. Los services del lavadero **no tienen JDBC**.

### Tareas

1. **`CicloLavaderoService`**:
   - En `validar`, nada que sacar: `litrosTotales` nunca se validaba ahí (la exigencia vivía sólo en
     la card y en `CiclosController.prepararLanzamiento`). **Verificarlo antes de tocar nada.**
   - Agregar la validación de los insumos, que es la única regla nueva:
     ```java
     if (config != null && config.insumos() != null) {
         v.addErrorIf(config.insumos().stream().anyMatch(Objects::isNull),
             prefijo + "hay un insumo vacío en la lista.");
         v.addErrorIf(config.insumos().stream().map(InsumoCatalogo::id).distinct().count()
                          != config.insumos().size(),
             prefijo + "hay insumos repetidos.");
     }
     ```
     **No** se valida que la lista sea no-vacía: los insumos son opcionales.
     El duplicado se valida acá aunque la card ya lo impida y la PK compuesta también: la card es una
     de dos entradas posibles (la otra la crea el plan B, con el pegado), y un `INSERT` que viola la
     PK sale como `DatabaseException` técnica en vez de como mensaje accionable.

2. **`lavadero/service/CatalogoInsumosService.java`** — nuevo, calcado de `CatalogoJabonesService`:
   constructor que rechaza `null`, `obtenerTodos()` que delega. Cero JDBC.

3. **Tests** — `CicloLavaderoServiceTest`:
   - Los casos existentes se ajustan a la firma nueva de `ConfiguracionCiclo`.
   - Nuevos: tanda con lista de insumos vacía → **válida**; con un `null` adentro → error; con el
     mismo insumo dos veces → error, y en los dos casos **el DAO no se toca**
     (`verifyNoInteractions`).
   - `CatalogoInsumosServiceTest`: delegación, y constructor con `null` → `IllegalArgumentException`.

### Verificación

```bash
mvn test -Dtest=CicloLavaderoServiceTest
mvn test -Dtest=CatalogoInsumosServiceTest
```

### Criterio de salida

- [ ] Una tanda sin insumos es válida
- [ ] Insumo repetido y insumo `null` se rechazan **antes** de llamar al DAO
- [ ] `CatalogoInsumosService` no importa nada de `java.sql`
- [ ] Commit: `feat: validacion de los insumos extra del ciclo`

---

## Paso 5 — La card: fuera los checkboxes y "L Tot.", adentro la lista dinámica

> **Paralelo con los Pasos 3 y 4.** Depende del Paso 2.

### Contexto (autocontenido)

`LavarropasCard` es un `JPanel` que vive en una grilla de **3 columnas** (`LAVARROPAS_POR_FILA`),
dentro de columnas independientes con `BoxLayout` ("masonry"). Tiene un `getMaximumSize()`
sobreescrito para no estirarse a lo alto. **Tiene que seguir siendo compacta**: cada píxel de alto
se multiplica por las ~5 cards de su columna.

Su panel de config es un `BoxLayout.Y_AXIS` de filas `rowPanel("Etiqueta:", campo)`. Todos los
campos notifican por **un solo canal**, `notificarConfiguracionChanged()`, que dispara
`onConfiguracionChanged` diferido — el controller lo usa para recalcular si "Lanzar" se enciende.

Hoy el panel tiene: Tipo, Jabón, mL Jabón, una fila con los dos checkboxes, y "L Tot.".

### Tareas

1. **`lavadero/view/helpers/PanelInsumosCard.java`** — nuevo, `JPanel` compacto:
   - Arriba una fila con `JComboBox<InsumoCatalogo>` + un botón `"+"` chico.
   - Debajo, una fila por insumo elegido: la etiqueta con su nombre y un botón `"×"` que la quita.
   - `setCatalogo(List<InsumoCatalogo>)` — repuebla **sólo el combo**, sin tocar las filas ya
     elegidas. Es la misma trampa que el javadoc de `LavarropasCard.setJabones` documenta para el
     jabón, y en el plan B (que recarga el catálogo en cada apertura) se vuelve crítica.
   - `getSeleccionados()` → `List<InsumoCatalogo>` en el orden en que se agregaron.
   - `limpiar()` — vacía las filas.
   - `setOnCambio(Runnable)` — un solo canal, que `LavarropasCard` engancha a
     `notificarConfiguracionChanged()`.
   - **Rechazo de duplicados por `id()`, no por `equals` ni por referencia.** Si el insumo ya está
     en la lista, el `"+"` no hace nada (o mejor: el combo no ofrece los ya elegidos). Ver el
     anti-patrón sobre identidad de catálogo.
   - Cero I/O, cero services: recibe la lista ya leída.

2. **`LavarropasCard`**:
   - Borrar `chkSuavizante`, `chkPotenciador`, `txtLitrosTotales`, sus getters
     (`isSuavizante`, `isPotenciador`, `getLitrosTotales`), el `chkRow`, la fila `"L Tot.:"`, la
     `RestriccionesCampo.soloNumerosDecimales(txtLitrosTotales)` y el `DocumentListener` sobre él.
   - Agregar `private final PanelInsumosCard panelInsumos` en `buildConfigPanel()`, después de
     "mL Jabón", con su `setOnCambio(this::notificarConfiguracionChanged)`.
   - `public void setInsumos(List<InsumoCatalogo> catalogo)` → delega en
     `panelInsumos.setCatalogo(...)`. Javadoc: **repoblar el catálogo no borra lo ya elegido**, a
     diferencia de `setJabones`, que deja el combo sin selección.
   - `public List<InsumoCatalogo> getInsumosSeleccionados()` → delega.
   - `resetConfiguracion()` — agregar `panelInsumos.limpiar()`. Actualizar su javadoc, que hoy
     enumera "suavizante, potenciador, litros totales".
   - `tieneConfiguracionCompleta()` → `getTipoLavado() != null && getJabon() != null &&
     getLitrosJabon() != null`. **Actualizar su javadoc**, que hoy dice "los cuatro campos
     obligatorios … y litros totales" y pasaría a mentir. Ese javadoc además afirma que es la
     *única* definición de "config completa" de la pantalla y que `CiclosController` la usa para los
     grupos repartidos: eso sigue siendo cierto y hay que preservarlo.
   - `notificarConfiguracionChanged()` — su javadoc también nombra los cuatro campos viejos.

3. **Tests** — `LavarropasCardTest`:
   - Los casos de `tieneConfiguracionCompleta()` que hoy setean litros totales se reescriben: con
     tipo + jabón + mL **ya está completa**.
   - Nuevos: `resetConfiguracion()` vacía la lista de insumos; agregar dos insumos y leerlos
     devuelve los dos en orden; agregar el mismo dos veces deja **uno**; `setInsumos` con un
     catálogo nuevo **no borra** los ya elegidos.
   - `PanelInsumosCardTest` propio para la lógica de agregar/quitar/deduplicar, si al escribirlo
     queda más limpio que probarla a través de la card.

### Verificación

```bash
mvn test -Dtest=LavarropasCardTest
mvn -q compile 2>&1 | head -20      # el controller todavía no compila: Paso 6
```

### Criterio de salida

- [ ] La card no nombra `suavizante`, `potenciador` ni `litrosTotales` en ningún lado
- [ ] Los tres javadoc de `LavarropasCard` que nombran los campos de la config —
      `resetConfiguracion()`, `tieneConfiguracionCompleta()` y `notificarConfiguracionChanged()` —
      describen el estado nuevo. (Ojo: sólo los dos últimos usan la frase *"los cuatro campos
      obligatorios"*; el de `resetConfiguracion` enumera **seis** cosas. Buscar la frase literal
      encuentra dos, no tres — buscar por método encuentra los tres.) El **cuarto**, el de
      `CiclosController.prepararLanzamiento`, lo arregla el Paso 6
- [ ] `setInsumos` no borra lo elegido; `setJabones` sigue dejando el combo sin selección
- [ ] La card sigue entrando cómoda en una columna de 3 (inspección visual en el Paso 6)
- [ ] Commit: `feat: lista dinamica de insumos en la card de lavarropas`

---

## Paso 6 — Controller, `DatosCiclos`, Ver Ciclos y cableado

> Depende de los Pasos 2, 3, 4 y 5. Es el paso que vuelve a poner la app en verde.

### Contexto (autocontenido)

`CiclosController.prepararLanzamiento(num, faltantes)` lee la config de la card y arma la
`ConfiguracionCiclo`; los campos que faltan se reportan con un mensaje por lavarropas y ese
lavarropas se saltea (las líneas ~628-666). Hoy pide cuatro campos, el último de ellos
`litrosTotales`.

`recargar()` lee todo en una sola tarea de fondo (`leerDatos(boolean conJabones)` →
`DatosCiclos`) y `pintar(DatosCiclos)` vuelca. El catálogo de jabones se lee **una sola vez**
(`jabonesCargados`), y la decisión de traerlo se toma en el EDT antes de lanzar la tarea justamente
para que una cancelación no deje el flag mal puesto. **En este plan los insumos copian ese patrón
tal cual**; el plan B lo cambia a "releer en cada apertura" para los tres catálogos a la vez.

`PantallaVerCiclos` muestra 11 columnas, tres de las cuales se van.

### Tareas

1. **`lavadero/controller/helpers/DatosCiclos.java`** — agregar
   `List<InsumoCatalogo> insumos` como último componente, con su `@param` explicando que llega vacía
   cuando esta carga no lo pidió (mismo texto que el de `jabones`).

   ⚠️ Es un `record`: agregarle un componente cambia su **constructor canónico**, y eso arrastra a
   **`ConstructorVistaCiclos`** (lo recibe en `construir`, `descartarStagingDeLavarropasOcupados`,
   `mapearLavarropas` y `armarCards`) y a **`ConstructorVistaCiclosTest`**. Los dos se tocan **sólo
   por la firma**: su lógica de descarte de staging **no cambia**, porque los insumos elegidos son
   configuración de card y no staging. Dejarlo escrito ahí mismo — es la clase donde un agente en
   frío, viendo que llegan insumos nuevos, se tienta con "resetear" algo.

2. **`CiclosController`**:
   - Constructor: un parámetro más, `CatalogoInsumosService catalogoInsumosService`, y su campo.
     Es la regla de extensión del repo — el alcance del controller se declara en su firma y
     `UiCoordinator` lo provee desde `AppContext`.
   - Campo `private boolean insumosCargados = false;` junto a `jabonesCargados`.
   - `recargar()`: `boolean conCatalogos = !jabonesCargados || !insumosCargados;` — **una sola
     decisión para los dos**, tomada en el EDT. Dos flags independientes que se resuelven por
     separado dentro de `leerDatos` es justo lo que el comentario existente advierte que no se haga.
     Más simple: pasar `conJabones` y `conInsumos` por separado y que `leerDatos` los respete;
     cualquiera de las dos formas sirve mientras la decisión quede en el EDT.
   - `leerDatos(...)`: `conInsumos ? catalogoInsumosService.obtenerTodos() : List.of()`.
   - `pintar(datos)`: junto al bloque de jabones,
     ```java
     if (!datos.insumos().isEmpty()) {
         cards.values().forEach(card -> card.setInsumos(datos.insumos()));
         insumosCargados = true;
     }
     ```
     ⚠️ `setInsumos` **no** borra lo que el operador ya eligió — es el invariante de refresco de esta
     pantalla, el mismo que hace que `recargar()` no llame a `resetConfiguracion()`.
   - `prepararLanzamiento`: **reescribir su javadoc**, que hoy dice *"Los cuatro campos que se piden
     acá son los mismos que enciende `tieneConfiguracionCompleta()`"* — son tres, y es justamente el
     javadoc que documenta el acoplamiento con ese método que este paso insiste en preservar. Es el
     **cuarto** de los javadoc que enumeran los campos obligatorios; los otros tres están en la card
     y los arregló el Paso 5.
   - `prepararLanzamiento`: borrar el bloque de `litrosTotales` (y su mensaje
     `"ingrese los litros totales"`), y armar
     ```java
     ConfiguracionCiclo config = new ConfiguracionCiclo(
         tipoLavado, jabon, litrosJabon, card.getInsumosSeleccionados());
     ```
   - **`validarConfigDeGruposRepartidos` y `motivoBloqueoPorInstanciaRepartida` no se tocan.** Se
     apoyan en `card.tieneConfiguracionCompleta()`, que el Paso 5 ya ajustó. Verificarlo y dejarlo
     escrito en el mensaje de commit: es el lugar donde un agente en frío mete un cambio que no hace
     falta.

3. **`PantallaVerCiclos`**:
   - `COLUMNAS`: sacar `"Suavizante"`, `"Potenciador"`, `"L Totales"`; poner **una** columna
     `"Insumos"` en su lugar. Quedan 9. `COL_ESTADO` es derivado (`List.of(COLUMNAS).indexOf`), así
     que no hay índice que corregir a mano — ése es el motivo por el que está escrito así.
   - `actualizarCiclos`: la celda es el texto de los insumos.
     ```java
     c.getInsumos().isEmpty() ? "—"
         : c.getInsumos().stream().map(InsumoCatalogo::nombre).collect(joining(", "))
     ```
     El `"—"` es la convención de la app para "no hay dato" (ver `DetalleHistorialDialog`), y es
     distinto de una celda vacía, que se lee como error de carga.
   - Revisar el ancho de columnas: `Constantes` tiene anchos por columna para algunas tablas.

4. **`AppContext`** — son **cinco** lugares, y **ningún test atrapa el que falte** (`new AppContext(`
   sólo aparece en `createDefault`; no hay test que lo construya). Un parámetro agregado sin sumarlo
   a la guarda de `null` compila, pasa toda la suite y explota con un `NullPointerException`
   diferido recién cuando alguien abre Ciclos:
   1. el parámetro en el constructor explícito (~línea 91);
   2. la cadena de `|| … == null` que rechaza dependencias faltantes (~línea 127);
   3. la asignación del campo (~línea 152);
   4. la construcción en `createDefault()` (~línea 236), junto a los de jabones;
   5. el getter `getCatalogoInsumosService()`.

5. **`UiCoordinator`** — el `new CiclosController(...)` recibe un argumento más:
   `context.getCatalogoInsumosService()`.

6. **`HistorialLavaderoDAO` / `DetalleHistorialDialog`** — **verificado: no muestran ninguno de los
   tres campos que se van.** No hay que tocarlos. Confirmarlo con
   `grep -rn "suavizante\|potenciador\|litros_totales"` antes de darlo por cerrado; si aparece algo,
   es un archivo que se agregó después de escrito este plan.

### Verificación

```bash
mvn -q compile
mvn test
mvn clean package && java -jar target/aptium.jar
```

Smoke manual (**sin** `-Daptium.edt.strict=true` — los cinco autocompletados síncronos de otras
pantallas lanzarían; leer los WARN del log en su lugar):

1. Lavadero → Ciclos: las cards ya no muestran checkboxes ni "L Tot.", y sí el combo de insumos.
2. Arrastrar ropa a una card, elegir tipo + jabón + mL **sin agregar ningún insumo** → "Lanzar" se
   enciende. Lanzar. El ciclo queda activo.
3. Otra card: agregar Suavizante y Potenciador, lanzar. Ver Ciclos muestra `"Potenciador, Suavizante"`.
4. Intentar agregar dos veces el mismo insumo → no se duplica.
5. Con una card a medio configurar (tipo y dos insumos elegidos, sin mL), apretar **F5**: el cartel
   avisa, y al aceptar **la configuración y los insumos siguen ahí**. Éste es el invariante de la
   tabla "Qué le pasa a lo pendiente" del `CLAUDE.md`.
6. Volver al menú y entrar de nuevo a Ciclos → ahora **sí** se resetea (es `abrirPantalla()`).
7. Ver Ciclos: 9 columnas, un ciclo sin insumos muestra `"—"`.
8. Log: ningún WARN de `EdtGuard` atribuible a lo nuevo.

### Criterio de salida

- [ ] Los 8 puntos del smoke pasan, el 5 en particular
- [ ] `mvn test` en verde, suite completa
- [ ] `grep -rn "suavizante\|potenciador\|litros_totales\|litrosTotales\|LitrosTotales" src/` no
      devuelve nada fuera de `V10`, `V12` y `V24` (las migraciones son historia y no se tocan)
- [ ] `validarConfigDeGruposRepartidos` quedó igual
- [ ] Commit: `feat: la pantalla de ciclos usa insumos extra y deja de pedir litros totales`

---

## Paso 7 — Revisión, cobertura, documentación y cierre

### Tareas

1. `/code-review high` sobre el diff completo del plan (todos los commits contra el punto de partida
   de la rama). Aplicar CRITICAL y HIGH; anotar el MEDIUM que se decida no tocar, con el motivo.
2. `mvn verify` y revisar JaCoCo: `CatalogoInsumosService`, `PanelInsumosCard` (si quedó con lógica
   propia) y las ramas nuevas de `CicloLavaderoService` ≥ 80 %. Las clases Swing sin lógica quedan
   sin cubrir, por convención del repo.
3. **`CLAUDE.md`**:
   - Sección **Lavadero**: agregar que un ciclo lleva **tipo, jabón, mL de jabón y una lista de
     insumos extra**, y que los insumos son opcionales; que se persisten en `insumos_ciclo_lavadero`
     y se escriben dentro de la transacción de `lanzarTanda`.
   - La frase *"toda operación mantiene una sola conexión a la vez"* menciona hoy sólo a
     `HistorialLavaderoDAO.obtenerHistorial()`. Sumar los tres métodos de `CicloLavaderoDAO`, que
     ahora también toman dos **secuencialmente**.
   - Sección **Tests**: el número de tests sube; actualizar el "~1310".
4. **Memoria** — crear
   `~/.claude/projects/c--Trabajo-Administracion-Aptium/memory/project-configuracion-ciclo-insumos.md`
   (`type: project`) con las dos decisiones que no se deducen del código: el orden
   migrar-antes-de-dropear, y que los insumos son opcionales (`tieneConfiguracionCompleta` son tres
   campos, no cuatro). Enlazar `[[project-lavadero-ciclos]]`, `[[project-architecture]]` y
   `[[feedback-nunca-modificar-migraciones]]`. Agregar la línea a `MEMORY.md`.
5. Marcar este plan como **✅ CERRADO** arriba de todo, con los SHAs de cada paso.

### Criterio de salida

- [ ] `mvn verify` en verde y cobertura ≥ 80 % en las clases planas nuevas
- [ ] `CLAUDE.md` describe los insumos y la nota de las dos conexiones secuenciales
- [ ] Memoria e índice actualizados
- [ ] Commit: `docs: configuracion del ciclo con insumos extra`

---

## Catálogo de anti-patrones para este plan

Cosas que un agente ejecutando esto en frío hace mal si no se le avisa:

| Anti-patrón | Por qué está mal acá |
|---|---|
| `DROP COLUMN` antes del `INSERT … SELECT` | El `INSERT` posterior inserta **cero filas sin fallar**. Se pierde la historia en silencio. |
| `GROUP_CONCAT` / `STRING_AGG` para los insumos | Se comportan distinto entre H2 (tests) y MySQL (producción). El repo agrupa en memoria a propósito — ver `TextoLavarropas`. |
| `LEFT JOIN insumos_ciclo_lavadero` en `SQL_TODOS` | Multiplica las filas de ciclo. Es el mismo bug que `HistorialLavaderoDAO` documenta para `cantBolsas`. |
| Leer los insumos **dentro** del `try-with-resources` de los ciclos | Dos conexiones abiertas a la vez. Rompe la aritmética del semáforo (5 × 2 > 8) y el pool se agota **con el techo puesto**. |
| Filtrar por `activo = TRUE` al leer los insumos **de un ciclo** | Es un join histórico: un insumo dado de baja tiene que seguir mostrando su nombre en los ciclos viejos. El `WHERE activo` va en el catálogo que alimenta el combo, no acá. |
| Exigir al menos un insumo para lanzar | Los insumos son **opcionales**. Un ciclo sin suavizante es válido y hoy lo es. |
| Que `setInsumos` limpie las filas ya elegidas | Rompe el invariante de refresco de Ciclos: `recargar()` no pisa lo que el operador está tipeando. Es lo contrario de `setJabones`, que sí deja el combo sin selección — y por eso hay que documentar la diferencia. |
| Deduplicar insumos por `equals` del objeto o por referencia | Dos lecturas del catálogo dan objetos distintos con el mismo `id`. En el plan B, que recarga el catálogo en cada apertura, eso deja duplicar un insumo ya elegido. **Siempre por `id()`.** |
| `executeBatch()` para algo cuyo conteo de filas importa | Acá está bien (nadie cuenta las filas de insumos), pero si alguien le agrega una guarda, `SUCCESS_NO_INFO` (`-2`) da un total sin sentido. Ver la regla en `CLAUDE.md`. |
| Escribir los insumos fuera de `lanzarTanda` | Un ciclo lanzado a medias con su configuración incompleta. "Lanzar es todo o nada". |
| Tocar `validarConfigDeGruposRepartidos` | Se apoya en `tieneConfiguracionCompleta()`, que el Paso 5 ya ajustó. Cambiarla además es duplicar la regla. |
| Que `MigracionV24Test` llame a `ConnectionPool.setDataSourceForTesting` | Pisa el `DataSource` global que usa `AbstractDAOTest`, y el resto de la suite pasa a leer la base equivocada según el orden de Surefire. |
| Cambiar `jabonesCargados` a "releer siempre" en este plan | Es trabajo del plan B, que lo hace para los tres catálogos a la vez y con el cuidado de no borrar lo elegido. Adelantarlo acá duplica el cambio. |
| Modificar `V10` o `V12` para "limpiar" las columnas | Una migración ya escrita no se toca. Eso es lo que hace la V24. |
| Aplicar la convención de `limpiarTablas()` a un solo test de DAO | `HistorialLavaderoDAOTest`, `SalidaLavaderoDAOTest` y `SalidaLavaderoDerivacionTest` también borran `ciclos_lavadero`. El `ON DELETE CASCADE` los cubre, pero el plan invoca *"la convención del repo es explicitar"*: o se agrega `insumos_ciclo_lavadero` a los **cuatro**, o a ninguno. Media aplicación deja tres tests inconsistentes sin que nadie se entere. |
| Confundir `DatosCiclos.insumos()` con los insumos **elegidos** de una card | Son dos cosas con el mismo nombre en el mismo paso. `DatosCiclos` es lo que viene de la base (el **catálogo**); lo elegido es estado de card, sólo EDT. Tratar lo segundo como lo primero es el camino directo a que `pintar()` pise lo que el operador tipeó — el único punto del smoke que este plan marca como el que de verdad se puede romper. |
| "Arreglar" el `grep` del criterio de salida del Paso 6 agregándole `-i` | Es **case-sensitive a propósito**: con `-i` matchea `'Suavizante'` y `'Potenciador'` del seed de la V24 y de los tests nuevos, que tienen que seguir ahí. Un grep que falla no significa que quede trabajo. |

---

## Plan de sesiones

Siete pasos, **cinco sesiones**. Cada sesión arranca en frío: el prompt inicial es autosuficiente.

| Sesión | Pasos | Modelo | Effort | Fast mode | Por qué |
|---|---|---|---|---|---|
| 1 | Paso 1 | **Opus 5** | **alto** | ❌ no | El orden de la migración y el test que la prueba son la parte irreversible: una migración mal ordenada borra datos de producción y no deja rastro. |
| 2 | Pasos 2 + 3 | **Opus 5** | **alto** | ❌ no | El modelo es mecánico, pero el DAO tiene las dos trampas del plan: la transacción y las dos conexiones. Van juntos porque el 3 no compila sin el 2. |
| 3 | Pasos 4 + 5 | **Sonnet 5** | medio | ✅ sí | Validación dictada por el plan, y Swing declarativo con la card como referencia. Ciclo de iteración visual corto. |
| 4 | Paso 6 | **Opus 5** | **alto** | ❌ no | Cableado transversal (5 archivos) + el smoke de 8 puntos, con el punto 5 (F5 no pisa la config) como el que de verdad se puede romper. |
| 5 | Paso 7 | **Sonnet 5** | medio | ➖ opcional | Cobertura y documentación, salvo el `/code-review` que abre la sesión. |

**Regla de effort:** alto donde todavía hay decisiones (1, 2, 4); medio donde el plan ya las tomó.

**Paralelizar (opcional):** los Pasos 4 y 5 no comparten un solo archivo con el 3 y los tres dependen
sólo del 2. Con `git worktree` se podrían correr a la vez; para una sola persona, secuencial suele
salir mejor.

---

### Sesión 1 — Paso 1: la migración V24

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 1 de plans/configuracion-ciclo-lavadero.md (migración V24 + test de migración).

Antes de escribir nada leé del plan: "Contexto compartido", "Decisiones de diseño tomadas
por el plan" y "Catálogo de anti-patrones". Después leé V12__catalogo_jabones_potenciador.sql
(patrón de migración compatible con H2 y MySQL) y AbstractDAOTest (fijate en el target("3")).

Lo único irreversible de todo el plan está acá, y son dos cosas:
1. el INSERT ... SELECT que migra los booleanos va ANTES de los tres DROP COLUMN. Al revés,
   el DROP se lleva los datos y el INSERT posterior inserta cero filas SIN FALLAR;
2. el test de migración NO puede llamar a ConnectionPool.setDataSourceForTesting: eso pisa
   el DataSource global que usa el resto de la suite. Usa su propia URL de H2 en memoria.

No toques V10 ni V12: una migración ya escrita no se toca.
Terminá con mvn test en verde y el commit del criterio de salida.
```

### Sesión 2 — Pasos 2 y 3: modelo y DAO

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá los Pasos 2 y 3 de plans/configuracion-ciclo-lavadero.md, en ese orden (modelo:
InsumoCatalogo / ConfiguracionCiclo / CicloLavadero; después el DAO: escritura en la
transacción y lectura sin N+1).

Leé antes del plan: "Contexto compartido" (sobre todo las reglas duras 4 y 5) y el
"Catálogo de anti-patrones". El Paso 1 ya está commiteado: la V24 existe, usala tal cual.

Tres cosas que este paso resuelve y que son la razón de que la sesión exista:
1. los insumos se escriben DENTRO de la transacción de lanzarTanda — hay un test que lo
   prueba lanzando una tanda que falla por saldo y verificando que no quedó ninguna fila;
2. se leen los CICLOS PRIMERO y los INSUMOS DESPUÉS, cada uno en su propio
   try-with-resources. Dos cosas distintas: (a) dos conexiones abiertas a la vez rompen la
   aritmética del semáforo del pool (5 x 2 = 10 > 8) — mirá HistorialLavaderoDAO.
   obtenerHistorial(); (b) el ORDEN decide qué pasa con un ciclo lanzado entre las dos
   lecturas: con los ciclos primero no se pinta (inocuo), con los insumos primero se pinta
   SIN insumos, que es un dato faltante disfrazado de "ciclo sin insumos";
3. el JOIN a catalogo_insumos NO filtra por activo: es un join histórico. Dejalo escrito
   en el javadoc, porque el plan siguiente va a pasar por ahí buscando dónde poner el WHERE.

Nada de GROUP_CONCAT ni de LEFT JOIN a la consulta maestra de ciclos.
Después del Paso 2 la app no compila: es esperado. Terminá con el Paso 3 y mvn test en verde
para los tests de DAO, con un commit por paso.
```

### Sesión 3 — Pasos 4 y 5: service y card

**Sonnet 5 · effort medio · fast mode recomendado**

```
Ejecutá los Pasos 4 y 5 de plans/configuracion-ciclo-lavadero.md (validación en
CicloLavaderoService + CatalogoInsumosService; después la card: PanelInsumosCard y
LavarropasCard).

Leé antes del plan: "Contexto compartido" y el "Catálogo de anti-patrones". Los Pasos 1 a 3
ya están commiteados: usá InsumoCatalogo y ConfiguracionCiclo tal como quedaron.

Cuidado con cuatro cosas:
- los insumos son OPCIONALES: una lista vacía es válida, no un error;
- la deduplicación es por id(), nunca por equals del objeto ni por referencia;
- setInsumos repuebla el combo pero NO borra las filas ya elegidas (al revés que
  setJabones, que sí deja el combo sin selección: documentá la diferencia);
- hay TRES javadoc en LavarropasCard que describen los campos de la config y que van a
  mentir: los de resetConfiguracion(), tieneConfiguracionCompleta() y
  notificarConfiguracionChanged(). Hay que reescribirlos, no borrarlos. Buscalos POR MÉTODO,
  no por la frase "los cuatro campos obligatorios": esa frase literal aparece sólo en dos
  (el de resetConfiguracion enumera seis cosas), y si buscás el texto vas a creer que te
  falta un archivo.

La card vive en una grilla de 3 columnas: tiene que seguir siendo compacta.
El controller todavía no compila — es lo esperado hasta la sesión que viene.
Un commit por paso, con los mensajes de los criterios de salida.
```

### Sesión 4 — Paso 6: controller, Ver Ciclos y cableado

**Opus 5 · effort alto · sin fast mode**

```
Ejecutá el Paso 6 de plans/configuracion-ciclo-lavadero.md (CiclosController, DatosCiclos,
PantallaVerCiclos, AppContext, UiCoordinator). Es el paso que vuelve a poner la app en verde.

Leé antes del plan: "Contexto compartido", las "Decisiones de diseño" y el "Catálogo de
anti-patrones". Los Pasos 1 a 5 ya están commiteados.

El invariante que gobierna esta sesión: recargar() / F5 NO pisa la configuración que el
operador está tipeando, y la lista de insumos es configuración. Sólo abrirPantalla() resetea,
y sólo las cards libres. Si al terminar F5 borra los insumos elegidos, el paso está mal.

Los insumos copian el patrón de jabonesCargados (leer una sola vez, con la decisión tomada
en el EDT antes de lanzar la tarea). NO lo cambies a "releer siempre": eso es trabajo del
plan de Ajustes, que lo hace para los tres catálogos a la vez.

No toques validarConfigDeGruposRepartidos ni motivoBloqueoPorInstanciaRepartida: se apoyan en
tieneConfiguracionCompleta(), que el Paso 5 ya ajustó.

Cerrá con los 8 puntos del smoke manual, corriendo la app SIN -Daptium.edt.strict=true, y con
el grep del criterio de salida. Terminá con el commit.
```

### Sesión 5 — Paso 7: revisión, cobertura y cierre

**Sonnet 5 · effort medio**

```
Cerrá plans/configuracion-ciclo-lavadero.md.

1. /code-review high sobre el diff completo del plan (todos los commits contra el punto de
   partida de PrimeraRevisionLavadero). Aplicá lo CRITICAL y lo HIGH; anotá lo MEDIUM que
   decidas no tocar, con el motivo.
2. Ejecutá el Paso 7: mvn verify + JaCoCo sobre las clases planas nuevas.
3. Actualizá CLAUDE.md (sección Lavadero, la nota de las dos conexiones secuenciales, y el
   número de tests) y la memoria del proyecto como pide el paso.
4. Marcá el plan como CERRADO arriba de todo, con los SHAs de cada paso.

Terminá con el commit del criterio de salida.
```

---

## Protocolo de mutación del plan

Si al ejecutar aparece algo que el plan no previó:

- **Dividir un paso** → agregarlo como `Paso N.5` con su propio contexto y criterio de salida.
- **Saltear un paso** → dejar escrito *por qué* en una sección "Mutaciones aplicadas" al final; no
  borrarlo.
- **Cambiar una decisión de la tabla de arriba** → tacharla (`~~...~~`) y escribir la nueva con
  fecha. Las decisiones tomadas con el usuario **no se cambian sin preguntarle**.

---

## Riesgos y preguntas abiertas

### Riesgos

| Riesgo | Mitigación |
|---|---|
| **La migración corre sobre datos reales de producción.** Los booleanos se van y no vuelven. | El Paso 1 migra antes de dropear y lo prueba con un test dedicado. Aun así: **hacer backup de la base antes de desplegar el JAR con la V24.** Es el único paso de los dos planes con pérdida de datos posible. |
| **La V24 no es atómica** (MySQL hace commit implícito en cada DDL): un fallo a mitad de camino la deja *failed*, con las tablas ya creadas, y el reintento muere en el `CREATE TABLE` | El procedimiento de recuperación (`flyway repair` + los dos `DROP TABLE`) va **en el encabezado del `.sql`**, no acá: es información que sólo sirve si está escrita antes del incidente. Ver el Paso 1. El backup cubre la pérdida de datos; esto cubre el arranque bloqueado del cliente, que es otra cosa. |
| **Dar de baja un insumo va a chocar con las tandas en vuelo** — handoff al plan B | El `INSERT` en `insumos_ciclo_lavadero` toma un *shared lock* por FK sobre la fila de `catalogo_insumos` **dentro de la transacción de `lanzarTanda`**. Hoy es inofensivo porque nadie escribe esa tabla; en cuanto el plan B agregue `UPDATE catalogo_insumos SET activo = FALSE`, esa baja se va a quedar esperando detrás de toda tanda en vuelo, hasta los 50 s del `innodb_lock_wait_timeout`. **La baja de insumos tiene que pasar por `ControlConcurrencia.esContencionDeLock`**, como las guardas del lavadero, o el choque sale como error técnico. Está anotado también en el Paso 4 del plan B. |
| Un JAR anterior a la V24 corriendo contra una base ya migrada | Cubierto: `DatabaseInitializer` aborta con `EsquemaDesactualizadoException` comparando máximos. Vale la pena verificarlo con el JAR viejo una vez, porque este plan es el primero que borra columnas (los anteriores sólo agregaban). |
| El panel de insumos infla la card y la grilla de 3 columnas se vuelve incómoda | Punto 1 del smoke del Paso 6. Si molesta, la fila del combo y el `"+"` se pueden apretar más, o la lista de elegidos puede ser una sola línea con `"Suavizante ×  Potenciador ×"` en vez de una fila por insumo. |
| Ver Ciclos pagina **en memoria** sobre el snapshot completo, y este plan le agrega una segunda lectura de tamaño proporcional a la historia | Medido en el plan de rendimiento: Ver Ciclos a 2 000 filas resolvía en 8-11 ms. La segunda consulta es de una tabla más chica y sin `JOIN` caro. Si alguna vez molesta, la palanca es la misma que ya está documentada (pasar a SQL), no deshacer esto. |

### Preguntas abiertas — **para el usuario**

1. 🚧 **BLOQUEANTE DEL PASO 1 — ¿el orden de los insumos en un ciclo importa?** El plan los guarda
   sin columna de orden (la PK es `(ciclo_id, insumo_id)`) y los muestra alfabéticamente en Ver
   Ciclos (`ORDER BY ci.nombre`, Paso 3), mientras que la card los devuelve **en el orden en que se
   agregaron** (Paso 5). O sea que el operador carga `Potenciador, Suavizante` y después del
   lanzamiento la pantalla le devuelve `Suavizante, Potenciador`.
   **No es una pregunta para validar después:** las dos mitades de la decisión viven en pasos
   distintos y en **sesiones distintas** (3 y 5), y si la respuesta es "sí importa" hace falta una
   columna `orden` en `insumos_ciclo_lavadero` — o sea tocar el **Paso 1**, que para entonces ya
   está commiteado, y agregar otra migración sobre una tabla recién creada.
   *Supuesto del plan: no importa.* **Confirmarlo antes de ejecutar el Paso 1.**
   ✅ **Respondida el 2026-09-21: no importa.** Sin columna `orden`.

2. **¿Un insumo puede llevar una cantidad alguna vez?** El pedido dice explícitamente "cada fila es
   SÓLO el insumo, sin ml". El plan lo toma literal y la tabla puente no tiene columna de cantidad.
   Si mañana hiciera falta, es una migración más — pero vale confirmarlo, porque el jabón sí lleva
   mL y la asimetría podría sorprender al operador.
   *Supuesto del plan: nunca lleva cantidad.*

3. **Ver Ciclos: ¿hace falta poder filtrar por insumo?** Hoy filtra por lavarropas, estado y fechas.
   El plan agrega la columna "Insumos" pero **no** un filtro. Es trabajo chico si se quiere
   (`CicloFilterStrategy` + un campo de texto, como el filtro de elemento del Historial), pero no
   estaba pedido.
   *Supuesto del plan: no hace falta.*

---

## Mutaciones aplicadas

### 2026-09-21 — Paso 1: los `DROP` se mudan a una V25 del Paso 3

**Qué:** la V24 crea `catalogo_insumos` e `insumos_ciclo_lavadero` y copia los booleanos, y nada
más. Los tres `DROP COLUMN` pasan a `V25`, tarea 0 del Paso 3.

**Por qué:** el criterio de salida del Paso 1 ("`mvn test` en verde con la V24 aplicada") era
incumplible con los `DROP` adentro: `CicloLavaderoDAO` todavía lee y escribe `suavizante`,
`potenciador` y `litros_totales`, y eso recién cambia en el Paso 3. La V24 entera habría dejado la
suite roja durante dos commits.

**Qué no cambia:** la regla irreversible ("migrar antes de dropear") se cumple igual y de forma más
fuerte — Flyway aplica por orden de versión, así que la V25 no puede correr sin la V24. En
producción las dos corren en la misma pasada, sin escrituras de la app en el medio. Lo que se
relaja es la decisión de diseño "en la misma migración", que era un medio y no el fin.

**Efecto colateral:** la V24 ya no tiene nada irrecuperable. El "restaurar del backup si falló
después de los `DROP`" de su encabezado se muda al de la V25.

**Tocado fuera de lo previsto:** `DatabaseInitializerTest.sanityMaximoLocal` fija la última versión
(23 → 24). Cada migración nueva lo mueve.

### 2026-09-21 — Paso 3: lo que la lista literal no tenía, y cómo se verificó

**Ocho tests con firma rota, no siete.** `lavadero/service/CicloLavaderoServiceTest.java` también
construye `ConfiguracionCiclo` y no estaba en la tabla. Se arregló acá (mecánico, sin cambiar
intención: el caso "con suavizante, potenciador y litros totales" pasó a "con insumos"). El Paso 4
lo encuentra ya compilando.

**`limpiarTablas()`: siete tests, no cuatro.** Además de los cuatro que nombra el anti-patrón,
`ConcurrenciaOptimistaTest`, `EquipoSubdivididoIntegracionTest` y `HistorialLavaderoDAOPaginacionTest`
también borran `ciclos_lavadero`. Se agregó `insumos_ciclo_lavadero` a los siete, por el mismo
argumento del anti-patrón (o a todos, o a ninguno).

**"`mvn test` en verde con la suite entera" no es commiteable en este paso**, y chocaba con el
Paso 2 ("la app no compila hasta el Paso 6"): `CiclosController` y `PantallaVerCiclos` siguen
usando los tres getters que se fueron. Se verificó con un parche **temporal y no commiteado** en
esos dos archivos (`List.of()` de insumos y `"—"` en las tres columnas): `mvn test` → 1339 tests,
0 fallos. El commit deja `main` sin compilar sólo en esos dos archivos, que son del Paso 6.

**Un test más que los pedidos:** el de "tanda que falla por saldo" es trivialmente verde aunque los
insumos se escribieran fuera de la transacción, porque la guarda de saldo corta antes de escribir
nada. La prueba que de verdad muerde es `lanzarTanda_siUnCicloFalla_noQuedaNadaEscrito`, que ahora
lanza el primer ciclo **con** insumos y verifica que vuelven atrás cuando el segundo viola la FK.
Se dejaron las dos.

**Lectura — manejo de errores:** `obtenerCiclosActivosPorLavarropas` y `obtenerCiclosFinalizados`
siguen tragándose un fallo de la maestra (mapa/lista vacía, como antes), pero un fallo al leer los
insumos sale como `DatabaseException`: pintar los ciclos sin insumos sería el "dato faltante
disfrazado" que el orden de lectura existe para evitar.

### 2026-09-21 — Paso 6: el grep de salida y el smoke

**El `grep` del criterio de salida no da vacío, y está bien.** Se escribió antes de la V25 y de los
tests del Paso 3. Fuera de `V10`/`V12`/`V24`/`V25` quedan tres familias de coincidencias, ninguna
de ellas código de los campos viejos: variables de `CicloLavaderoDAOTest` que nombran **insumos del
catálogo** (`suavizante`, `potenciador`), `MigracionV24Test` (que tiene que nombrar las columnas
viejas para sembrarlas y comprobar el `DROP`), y los javadoc de `InsumoCatalogo`/`ConfiguracionCiclo`
que las usan de ejemplo. Ningún getter, campo ni columna vieja sobrevive en `src/main`.

**`ConstructorVistaCiclos` no cambió de firma**: recibe `DatosCiclos` entero, así que el componente
nuevo sólo tocó el `new DatosCiclos(...)` de su test. Se le agregó igual la nota de clase ("los
catálogos no descartan nada").

**El smoke de 8 puntos no lo pudo correr la sesión** (sin computer use disponible). Verificado
automáticamente: suite completa (1382, 0 fallos) y arranque del JAR 45 s contra la base de
desarrollo sin ERROR ni WARN de `EdtGuard`. Los 8 puntos los corrió el usuario a mano el
2026-09-21: **pasan todos**, el 5 incluido (F5 conserva la config y los insumos elegidos).

### 2026-09-21 — Paso 7: `/code-review high` sobre el diff completo (`4216d6e..HEAD`)

**HIGH aplicado — condición de carrera "dato faltante disfrazado" en sentido inverso.**
`obtenerCiclosActivosPorLavarropas()` y `obtenerCiclosFinalizados()` acotaban su segunda lectura con
`SQL_INSUMOS_DE_ACTIVOS`/`SQL_INSUMOS_DE_FINALIZADOS` (mismo `WHERE fecha_fin` que la maestra). Como
las dos lecturas no comparten transacción, un ciclo finalizado en la ventana entre ambas quedaba
leído por la maestra (todavía `fecha_fin IS NULL` en ese momento) pero afuera del filtro de insumos
(ya no lo estaba) — se pintaba como "ciclo activo sin insumos", exactamente el dato faltante
disfrazado que el orden ciclos-antes-que-insumos existe para evitar, sólo que reintroducido por el
lado del filtro en vez del orden. **Arreglo:** las tres lecturas usan ahora la única
`SQL_INSUMOS_TODOS`, sin `WHERE fecha_fin`; el cruce en memoria (`FilaCiclo#conInsumos`) ya ignora
las entradas que no correspondan a un id leído por la maestra, así que acotar la consulta no
aportaba nada y sí abría la ventana. Se borraron las dos constantes y se extendió el javadoc de
`SQL_INSUMOS_TODOS` explicando por qué es la única que existe. `mvn test` en verde para
`CicloLavaderoDAOTest`, `CicloLavaderoServiceTest` y `ConcurrenciaOptimistaTest` después del cambio.

**MEDIUM anotado, sin tocar — cuatro hallazgos:**

| Hallazgo | Por qué no se tocó |
|---|---|
| Un fallo de SQL en la maestra ahora descarta las filas ya leídas (antes devolvía el mapa/lista parcial construido hasta el punto de falla) | Es un cambio de comportamiento real, pero **más seguro**, no menos: pintar un snapshot a medio leer es la misma clase de dato-a-medias que el resto del paso evita. No hay caso de negocio que dependa de la devolución parcial. |
| `PanelInsumosCard` tiene la lógica de agregar/quitar/deduplicar en la clase Swing, en vez de extraerla a una clase plana (patrón del repo: `AgrupadorIngresosLote`, `ConstructorVistaCiclos`, etc.) | El Paso 5 ya consideró esto explícitamente ("`PanelInsumosCardTest` propio ... si al escribirlo queda más limpio") y decidió que no. La lógica es un chip-list de ~15 líneas; extraerla movería la complejidad sin reducirla. Revisar si crece con el plan de Ajustes (copiar/pegar, catálogo con `activo`). |
| `jabonesCargados`/`insumosCargados` en `CiclosController` duplican la misma forma (flag + decisión en el EDT + ternario en `leerDatos` + bloque en `pintar`) en vez de un mecanismo genérico para "catálogo que se lee una sola vez" | Es exactamente el trabajo que el plan de Ajustes (`ajustes-lavadero-catalogos.md`) ya tiene previsto hacer para los tres catálogos a la vez, con más cuidado del que este plan necesita para uno solo. Generalizarlo acá es adelantar ese plan a medias. |
| `CatalogoInsumosDAO`/`CatalogoInsumosService` repiten el boilerplate de `CatalogoJabonesDAO`/`CatalogoJabonesService` byte a byte; `obtenerCiclosFinalizados()` y `obtenerTodosLosCiclos()` quedaron con la misma forma (leer fila → `leerInsumos` → `conInsumos`) | Dos catálogos con la misma forma no son todavía la presión real que justifica una abstracción (YAGNI) — recién con el tercer catálogo del plan de Ajustes se sabrá si vale la pena. Extraer un helper para dos métodos de 15 líneas que sólo cambian la consulta y el manejo de error tampoco simplifica una sola de las dos lecturas por separado. |

**Verificación de cierre:** `mvn verify` en verde — **1347 tests**, 0 fallos. Cobertura JaCoCo de las
clases planas nuevas: `CatalogoInsumosService` 100 %, `ConfiguracionCiclo` 100 %, `InsumoCatalogo`
100 %, `CicloLavaderoService` 94 %, `CatalogoInsumosDAO` 84 %, `PanelInsumosCard` 83 %,
`CicloLavaderoDAO` 88 % — todas por encima del 80 % mínimo del repo.
