# Plan — Salidas de Lavadero: marcar "Listo" y derivar al CDE

**Objetivo:** pantalla nueva en el módulo Lavadero que toma los elementos ya **lavados** (los que
pasaron por un ciclo finalizado), permite marcarlos como **Listo** una vez secados y doblados, y
después decidir el **destino** de cada salida: *sale del flujo de la app* o *ingresa al CDE como un
equipo "Otros"*, reflejado inmediatamente en las pantallas operativas.

**Rama base:** `ConexionConCDE` · **Modo:** directo (todos los pasos en la misma rama, commit por paso; `gh` no está instalado, no hay PRs)
**Fecha de creación:** 2026-08-13

---

## Decisiones tomadas con el usuario

| Tema | Decisión |
|---|---|
| Granularidad del "Listo" | **Parcial por cantidad.** Cada fila lavada muestra su cantidad pendiente y un spinner: se marcan N de M. Consistente con cómo los ciclos ya reparten una línea de clasificación en varias tandas. |
| Marcado de varias filas a la vez | **Multi-selección con "marcar todo lo pendiente".** Con **una** fila seleccionada el spinner marca una cantidad parcial; con **varias**, el botón marca el total pendiente de cada una. Cubre "terminé de doblar todo este ingreso" sin meter el concepto de "ingreso" en una pantalla que trabaja por elemento. Todo el marcado va en **una sola transacción**: o entran todas las filas o ninguna. |
| Granularidad al derivar | **La salida se deriva entera; no se parte.** Si al doblar se marcaron 10 Batas como una tanda, esas 10 van todas al mismo destino. Para separarlas hay que "Volver a Lavado" y remarcar en dos tandas. **Consecuencia operativa a comunicar en la UI:** conviene marcar Listo en las tandas en que se piensa despachar. |
| Identificación de una fila | Elemento solo no alcanza: el operador necesita saber **de qué cliente es, en qué lavarropas y cuándo se lavó**. Ambas tablas muestran cliente, lavarropas y fecha de fin de ciclo, no sólo el nombre del elemento. |
| Forma de la pantalla | **Una sola pantalla con dos tablas.** Izquierda: *Lavados (pendientes de secado y doblado)*. Derecha: *Listos (pendientes de destino)*. Un solo botón nuevo en el menú de Lavadero. |
| Cómo se arma el ingreso en CDE | **Un `equipo_otros` por cliente, tipo `DETALLES`**, con un `MaterialOtros` por elemento (descripción = nombre del elemento de lavadero, cantidad = la derivada). La entrada en `catalogo_otros` se auto-crea, igual que en el ingreso manual de Otros. |
| Estado inicial en CDE | **`NUEVO` con `requiereLavado = false`.** La máquina de estados ya contempla el salto: `calcularSiguienteEstado(NUEVO, false, true)` devuelve `EMPAQUETADO`. No se toca nada del flujo de CDE. |
| Persistencia | **Tabla única `salidas_lavadero`.** Una fila por cantidad marcada Listo, con `destino`/`equipo_otros_id`/`fecha_salida` en NULL hasta que se derive. Queda trazado qué ciclo lavó cada cosa y a qué ingreso de CDE fue a parar. |
| Reversión | **Sólo se puede desmarcar "Listo" mientras la salida no tenga destino.** Una vez derivada (a CDE o fuera del flujo) es definitiva: deshacer un ingreso de CDE ya creado puede chocar con un equipo ya loteado o avanzado de estado. |
| Cliente del ingreso en CDE | **Se elige al derivar: el cliente original o APTIUM.** Un mismo material puede entrar al CDE conservando su cliente o a nombre de APTIUM. La elección es **por operación**, no por fila: todas las salidas seleccionadas van del mismo modo. |
| Cómo se elige | **Un solo botón "Ingresar al CDE" que abre un diálogo con las dos opciones y confirma en el mismo paso.** La derivación ya requería confirmación por ser irreversible, así que se fusionan los dos diálogos: un clic, una decisión explícita, y el panel derecho se queda con 3 botones. |
| Cómo se registra | **`destino = 'CDE_OTROS'` en los dos casos.** No se distingue en `salidas_lavadero` cómo se asignó el cliente; el dato queda en el `nro_cliente` del `equipo_otros` creado. Consecuencia: la elección es una **acción de UI**, no un destino persistido — por eso `AccionSalida` y `DestinoSalida` son dos enums distintos (ver Paso 1). |
| Cliente APTIUM | **Ya existe en la tabla `clientes` con ese nombre.** Se resuelve **por nombre**, nunca por id hardcodeado (depende del `AUTO_INCREMENT`). La migración deja igual un `INSERT IGNORE` por si una BD de desarrollo no lo tiene. |
| Agrupación con APTIUM | Se sigue agrupando por **cliente asignado**, así que derivar a APTIUM salidas de tres clientes distintos produce **un solo** `equipo_otros` a nombre de APTIUM. Sale gratis del diseño: no hay una regla de agrupación aparte. |
| Estado del ingreso de lavadero | **Nuevo valor `FINALIZADO`.** Cuando todas las cantidades clasificadas de un ingreso tienen destino asignado, `ingresos_lavadero.estado` pasa de `LAVADO` a `FINALIZADO`, con el mismo patrón que ya usa `CicloLavaderoDAO` para pasar a `LAVADO`. |
| Salida "fuera del flujo" | **Sin motivo ni observación.** Es la salida normal: la ropa se devuelve al cliente. |

---

## Contexto compartido (leer una vez por sesión)

App de escritorio **Swing, Java 17, Maven**, sin framework de DI. Capas por feature:
`model → dao → service → view/controller`. Todo se cablea a mano en `AppContext` y `UiCoordinator`.

### Cómo funciona hoy el lavadero (imprescindible para entender el plan)

```
Ingreso        → ingresos_lavadero (estado PENDIENTE) + bolsas_lavadero
Clasificación  → elementos_clasificacion_lavadero (ingreso_id, elemento_id, cantidad)
                 y el ingreso pasa a CLASIFICADO
Ciclos         → ciclos_lavadero + elementos_ciclo_lavadero (ciclo_id, elemento_clasificacion_id, cantidad)
Finalizar ciclo→ ciclos_lavadero.fecha_fin = NOW(); si TODO el ingreso quedó procesado,
                 ingresos_lavadero.estado = 'LAVADO'
```

**Punto clave:** hoy **no existe ningún estado por elemento**. `elementos_ciclo_lavadero` es sólo el
vínculo "esta cantidad de esta línea de clasificación pasó por este ciclo". Lo que agrega este plan es
la primera noción de estado a nivel de cantidad lavada.

**Un elemento está "lavado"** cuando existe una fila en `elementos_ciclo_lavadero` cuyo ciclo tiene
`fecha_fin IS NOT NULL`. No hay una columna que lo diga: se deriva del ciclo.

### Archivos del alcance

| Archivo | Rol |
|---|---|
| [CicloLavaderoDAO.java](../src/main/java/com/example/features/lavadero/dao/CicloLavaderoDAO.java) | JDBC de ciclos. **Referencia canónica**: `finalizarCiclo` + `actualizarEstadoIngresosAfectados` es exactamente el patrón a copiar para el paso a `FINALIZADO` |
| [ClasificacionLavaderoDAO.java](../src/main/java/com/example/features/lavadero/dao/ClasificacionLavaderoDAO.java) | JDBC de clasificación; escribe el literal `'CLASIFICADO'` |
| [IngresoLavaderoDAO.java](../src/main/java/com/example/features/lavadero/dao/IngresoLavaderoDAO.java) | JDBC de ingresos y bolsas |
| [CicloLavaderoService.java](../src/main/java/com/example/features/lavadero/service/CicloLavaderoService.java) | **Referencia de estilo de service**: sólo validación con `ValidationException.builder()`, cero JDBC |
| [PantallaCiclos.java](../src/main/java/com/example/features/lavadero/view/PantallaCiclos.java) | Vista con `JSplitPane` (tabla arriba, grilla abajo) |
| [PantallaClasificacionLavadero.java](../src/main/java/com/example/features/lavadero/view/PantallaClasificacionLavadero.java) | **Referencia de vista simple**: sólo widgets + getters + `mostrarError`/`mostrarInfo` |
| [ElementoDisponibleTableModel.java](../src/main/java/com/example/features/lavadero/view/helpers/ElementoDisponibleTableModel.java) | Molde exacto para los `TableModel` nuevos |
| [PantallaLavadero.java](../src/main/java/com/example/features/lavadero/view/PantallaLavadero.java) | Menú del módulo; hoy `GridLayout(2,2)` con 4 botones |
| [EquipoOtrosDAO.java](../src/main/java/com/example/features/equipos/otros/dao/EquipoOtrosDAO.java) | `guardar(EquipoOtros)` abre su propia conexión y transacción (líneas 87-206) |
| [EquipoOtros.java](../src/main/java/com/example/features/equipos/otros/model/EquipoOtros.java) | Modelo del ingreso "otros" |
| [UiCoordinator.java](../src/main/java/com/example/app/ui/UiCoordinator.java) | Cableado de controllers y de los 4 grupos de refresco |
| [AppContext.java](../src/main/java/com/example/app/AppContext.java) | Único lugar donde se construyen DAOs y Services |
| [Constantes.java](../src/main/java/com/example/common/constants/Constantes.java) | `Pantallas`, `Titulos`, `Botones`, `Mensajes`, `Textos` |
| [PantallaPrincipal.java](../src/main/java/com/example/ui/shell/PantallaPrincipal.java) | Registra cada pantalla en el `CardLayout` |

### Infra a reutilizar (ya existe, **no reescribir**)

| Archivo | Qué aporta |
|---|---|
| [TransactionalConnection.java](../src/main/java/com/example/infrastructure/db/TransactionalConnection.java) | `begin()` / `get()` / `commit()`, rollback automático al cerrar sin commit |
| [TareaUI.java](../src/main/java/com/example/ui/common/TareaUI.java) | `TareaUI.<T>nueva().nombre(..).leer(..).pintar(..).siFalla(..).lanzar()` — lectura fuera del EDT |
| [TableStyler.java](../src/main/java/com/example/ui/common/TableStyler.java) | `applyStandard(JTable)`, `centerColumns(JTable, int...)` |
| [PanelHeader.java](../src/main/java/com/example/ui/common/PanelHeader.java) | Header con botón "volver": `new PanelHeader(titulo, navegador, contenedor, pantallaDestino)` |
| [Estilos.java](../src/main/java/com/example/ui/common/Estilos.java) | `Fuentes.BOTON`, `Fuentes.LABEL`, `Espaciados.BORDE_PRINCIPAL` |
| [ValidationException.java](../src/main/java/com/example/common/exception/ValidationException.java) | `ValidationException.builder().addErrorIf(cond, msg).throwIfHasErrors()` |
| [ConstructorEquipo.java](../src/main/java/com/example/features/equipos/ortopedias/controller/helpers/ConstructorEquipo.java) | Precedente de "clase plana que arma un modelo a partir de datos de UI", testeable sin Swing ni BD |

### Migraciones

Flyway sobre `src/main/resources/db/migration/`. Última: **`V16__catalogo_ortopedias.sql`** → la nueva
es **`V17`**. Los DDL tienen que correr igual en **H2 (tests)** y **MySQL (producción)**:
un `ALTER TABLE` por sentencia, sin `AFTER` (ver `V12` y `V15` como precedentes).

### Tests

JUnit 5 + Mockito + H2 en memoria. Más de 500 tests, un `*Test.java` por DAO/Service/helper.
Patrón del repo para lógica dentro de clases Swing: **extraerla a una clase plana sin dependencias
de Swing y testearla en aislamiento** (`AgrupadorIngresosLote`, `ReconciliadorPendientes`,
`StagingCiclos`, `DuplicadoHighlighter`).

### Deuda conocida que NO hay que copiar

- `CiclosController.cargarDatos()` consulta la BD **directamente en el EDT** (`CiclosController.java:125-142`).
  Es deuda pendiente (`plans/refactor-concurrencia-edt.md`), no un patrón. El controller nuevo de este
  plan usa **`TareaUI`** para todas sus lecturas.
- `EquipoOtrosDAO.guardar` traga la `SQLException` y devuelve `false`. El paso 3 extrae una variante
  que **propaga**; el método público conserva su comportamiento actual para no romper a sus llamadores.

---

## Invariantes (verificar al cerrar CADA paso)

1. `mvn clean package -q` compila sin errores ni warnings nuevos.
2. `mvn test` en verde; ningún test existente se modifica para que pase.
3. **Cero JDBC (`java.sql.*`) en la capa `service/`.** Toda transacción vive en un DAO.
4. **Cero `javax.swing` en `model/`, `dao/` y `service/`.**
5. Ningún literal de pantalla, título o botón fuera de `Constantes`.
6. **Ningún SQL de `equipo_otros` duplicado**: el ingreso a CDE se crea siempre a través de `EquipoOtrosDAO`.
7. El flujo existente de Lavadero (Ingreso → Clasificación → Ciclos → Ver Ciclos) sigue funcionando
   sin cambios de comportamiento observables.
8. Toda cantidad es un entero > 0 y nunca supera el saldo disponible; el chequeo se hace **dentro de
   la transacción**, no sólo contra el snapshot que tiene la pantalla.

---

## Grafo de dependencias

```
Paso 1 (migración + enums)          Paso 3 (guardar(Connection,..) en EquipoOtrosDAO)
        │                                    │
        ▼                                    │
Paso 2 (SalidaLavaderoDAO: lectura + Listo)  │
        │            │                       │
        │            ▼                       │
        │      Paso 4 (ConstructorIngresoCDE)│
        │            │                       │
        │            └───────┬───────────────┘
        │                    ▼
        │              Paso 5 (Derivadores + DAO.derivar + FINALIZADO)
        │                    │
        │                    ▼
        │              Paso 6 (SalidaLavaderoService)
        │                    │
        ├──► Paso 7 (Vista + TableModels + Constantes + menú)
        │                    │
        └────────────────────┴──► Paso 8 (Controller + AppContext + UiCoordinator)
                                          │
                                          ▼
                                    Paso 9 (docs)
```

**Paralelizables:** `{1, 3}` de entrada · `{4, 7}` · `{5, 7}` · `{6, 7}`.
El paso 7 no toca ningún archivo de los pasos 3-6 (sólo `view/`, `Constantes`, `PantallaPrincipal`,
`PantallaLavadero`), así que puede ir en paralelo con toda la rama de backend una vez cerrado el paso 2.

**Modelo por paso:** el más fuerte (Opus) para los pasos **2, 3, 5 y 8** (SQL de saldos, refactor de
transacción cross-feature, transacción que cruza dos features, cableado y refresco). Modelo por
defecto para 1, 4, 6, 7 y 9.

---

# Paso 1 — Migración `V17` + vocabulario del dominio

**Modelo:** por defecto · **Depende de:** nada · **Paralelo con:** Paso 3

### Contexto

No existe ninguna tabla que registre el secado/doblado ni el destino de lo lavado. Este paso crea el
almacenamiento y el vocabulario, sin ninguna lógica todavía.

Además hay tres literales de estado de ingreso (`'PENDIENTE'`, `'CLASIFICADO'`, `'LAVADO'`) repartidos
en SQL de tres DAOs distintos. Este plan agrega un cuarto (`'FINALIZADO'`): antes de sumarlo, se
convierte el conjunto en un enum para no multiplicar la magic string.

Estado actual de los literales:
- `ClasificacionLavaderoDAO.java` — `UPDATE ingresos_lavadero SET estado = 'CLASIFICADO'`
- `CicloLavaderoDAO.java` — `WHERE il.estado = 'CLASIFICADO'` (en `SQL_DISPONIBLES`) y `UPDATE ingresos_lavadero SET estado = 'LAVADO'`
- `V10__ciclos_lavadero.sql` — `DEFAULT 'PENDIENTE'` y el backfill a `'CLASIFICADO'`

### Tareas

1. **`src/main/resources/db/migration/V17__salidas_lavadero.sql`**:

```sql
-- Salidas del lavadero: cantidades ya secadas y dobladas ("Listo") y su destino final.
--
-- Una fila = una cantidad de una tanda lavada (elementos_ciclo_lavadero) marcada como Listo.
-- destino/equipo_otros_id/fecha_salida quedan en NULL hasta que se decide el destino:
--   NULL            -> lista, todavía sin destino (se puede revertir a Lavado)
--   'FUERA_DE_FLUJO'-> se devuelve al cliente, no entra al CDE
--   'CDE_OTROS'     -> se creó un ingreso equipo_otros; equipo_otros_id apunta a él
--
-- Las dos FK tienen políticas distintas a propósito:
--
-- elemento_ciclo_id -> RESTRICT. Nada del lavadero se borra nunca (no hay un solo
--   DELETE sobre ingresos/clasificación/ciclos en todo el código), así que RESTRICT
--   no bloquea ningún flujo real y protege la trazabilidad si alguien toca la BD a mano.
--
-- equipo_otros_id   -> SET NULL. Los equipos "otros" SÍ se borran: Correcciones permite
--   eliminar un ingreso mientras está en estado Nuevo (EquipoOtrosDAO.eliminarEquipo),
--   que es justamente el estado en el que nace un ingreso derivado del lavadero. Con
--   RESTRICT, eliminar desde Correcciones un ingreso venido del lavadero fallaría con
--   "Error al eliminar el equipo". Con SET NULL la salida conserva destino='CDE_OTROS'
--   y pierde sólo el puntero: la historia queda contada correctamente ("se derivó al CDE,
--   y ese ingreso después se eliminó"), y la eliminación en sí ya queda auditada en
--   equipos_eliminados.

CREATE TABLE salidas_lavadero (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    elemento_ciclo_id INT NOT NULL,
    cantidad          INT NOT NULL,
    fecha_listo       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    destino           VARCHAR(20) NULL,
    equipo_otros_id   INT         NULL,
    fecha_salida      TIMESTAMP   NULL,
    FOREIGN KEY (elemento_ciclo_id) REFERENCES elementos_ciclo_lavadero(id) ON DELETE RESTRICT,
    FOREIGN KEY (equipo_otros_id)   REFERENCES equipo_otros(id)             ON DELETE SET NULL
);

CREATE INDEX idx_salidas_elemento_ciclo ON salidas_lavadero (elemento_ciclo_id);
CREATE INDEX idx_salidas_destino        ON salidas_lavadero (destino);
```

No hace falta DDL para `FINALIZADO`: `ingresos_lavadero.estado` ya es `VARCHAR(20)`.

> **Regla de migraciones, sin excepciones:** una migración ya escrita **no se toca nunca más**, ni
> siquiera dentro de este mismo plan. Si un paso posterior necesita otro cambio de BD, crea una
> migración nueva con el número siguiente. Por eso la siembra del cliente APTIUM que hace falta en el
> Paso 5 va en `V18`, y no se agrega acá aunque en este momento `V17` todavía no se haya aplicado en
> ningún lado.

2. **`features/lavadero/model/EstadoIngresoLavadero.java`** — enum `PENDIENTE`, `CLASIFICADO`,
   `LAVADO`, `FINALIZADO`. Se persiste `name()`. Incluir `desdeBD(String)` con el mismo criterio
   defensivo que `TipoLavado.desdeBD` (log a WARN + default `PENDIENTE` ante valor nulo o desconocido).

3. **`features/lavadero/model/DestinoSalida.java`** — enum con `FUERA_DE_FLUJO("Sale del flujo")` y
   `CDE_OTROS("Ingresa al CDE")`, `getNombre()` y `toString()` devolviendo el texto visible.
   **Ojo:** a diferencia de `TipoLavado`, la columna es *nullable* y NULL significa "sin destino
   todavía", que es un estado legítimo. Por eso `desdeBD(null)` devuelve `null` (no un default) y
   así está documentado en el Javadoc.

4. **`features/lavadero/model/AccionSalida.java`** — lo que el operador **elige**, que no es lo mismo
   que lo que se **guarda**:

```java
/**
 * Qué decide hacer el operador con un conjunto de salidas listas.
 *
 * <p>No confundir con {@link DestinoSalida}, que es lo que se persiste en
 * {@code salidas_lavadero.destino}: dos acciones distintas pueden guardar el mismo destino.
 * Derivar al CDE conservando el cliente y derivar al CDE a nombre de APTIUM son dos acciones
 * con el mismo destino registrado {@code CDE_OTROS}; lo que las diferencia queda en el
 * {@code nro_cliente} del ingreso creado, no en la salida.
 */
public enum AccionSalida {
    FUERA_DE_FLUJO(DestinoSalida.FUERA_DE_FLUJO, "Sale del flujo"),
    CDE_CLIENTE   (DestinoSalida.CDE_OTROS,      "Ingresa al CDE con su cliente"),
    CDE_APTIUM    (DestinoSalida.CDE_OTROS,      "Ingresa al CDE como APTIUM");

    private final DestinoSalida destinoPersistido;
    private final String        nombre;
    // getters
}
```

Es la pieza que evita el atajo feo: sin este enum, la elección "cliente original o APTIUM" tendría que
viajar como un parámetro suelto por toda la cadena (`derivar(destino, seleccion, ¿boolean esAptium?)`),
ensuciando también al destino "fuera del flujo", al que no le importa. Con `AccionSalida` el registry
del Paso 5 se indexa por acción y cada acción tiene su derivador ya configurado.

5. Reemplazar los literales de estado en `ClasificacionLavaderoDAO` y `CicloLavaderoDAO` por
   concatenación del enum (`... SET estado = '" + EstadoIngresoLavadero.CLASIFICADO + "'`). Son
   constantes de compilación, no entrada de usuario: no hay riesgo de inyección. **No** tocar las
   migraciones ya aplicadas.

6. Tests:
   - `EstadoIngresoLavaderoTest` y `DestinoSalidaTest`: `desdeBD` con valor válido, minúsculas,
     desconocido y `null`.
   - `AccionSalidaTest`: cada acción devuelve el `DestinoSalida` esperado, y **las dos acciones de
     CDE devuelven `CDE_OTROS`** (es la aserción que documenta la decisión de no distinguirlas en BD).

### Verificación

```bash
mvn test -Dtest=EstadoIngresoLavaderoTest+DestinoSalidaTest
mvn test -Dtest=ClasificacionLavaderoDAOTest+CicloLavaderoDAOTest
mvn clean package -q
```

Y confirmar que la migración corre en H2: los `*DAOTest` de lavadero levantan el schema completo, así
que si `V17` es incompatible con H2 fallan ahí.

### Criterio de salida

- [ ] `V17__salidas_lavadero.sql` existe y los tests de DAO de lavadero pasan (prueba de que H2 la aplica).
- [ ] `EstadoIngresoLavadero`, `DestinoSalida` y `AccionSalida` existen con sus tests.
- [ ] La migración `V17` **no** siembra el cliente APTIUM (eso es `V18`, en el Paso 5).
- [ ] `grep -rn "'CLASIFICADO'\|'LAVADO'\|'PENDIENTE'" src/main/java` no devuelve nada.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `feat: tabla salidas_lavadero y enums de estado/destino de lavadero`

---

# Paso 2 — `SalidaLavaderoDAO`: lectura de saldos y marcar/desmarcar "Listo"

**Modelo:** el más fuerte · **Depende de:** Paso 1

### Contexto

Este paso implementa la mitad izquierda de la pantalla: qué está lavado y pendiente de secado, y el
marcado parcial de cantidades. Todavía sin destinos.

Los dos saldos que hay que calcular:

- **Pendiente de Listo**, por fila de `elementos_ciclo_lavadero` cuyo ciclo está finalizado:
  `eci.cantidad − SUM(salidas_lavadero.cantidad de esa fila)`.
- **Lista sin destino**: filas de `salidas_lavadero` con `destino IS NULL`.

`CicloLavaderoDAO.SQL_DISPONIBLES` (líneas 63-75) es el molde exacto para el primero: mismo patrón de
`LEFT JOIN` + `GROUP BY` + `HAVING <alias> < <total>`, que ya se sabe que funciona en H2 y MySQL en
este proyecto.

### Tareas

1. **Modelos de lectura** (`features/lavadero/model/`), como `record` (Java 17, precedente: `ConfiguracionCiclo`):

```java
public record ElementoLavadoPendiente(
        int elementoCicloId, int cicloId, int lavarropasNumero, int ingresoId,
        int clienteId, String clienteNombre, String elementoNombre,
        int cantidadLavada, int cantidadYaLista, LocalDateTime fechaFinCiclo) {

    public int cantidadPendiente() { return cantidadLavada - cantidadYaLista; }
}

public record SalidaLista(
        int salidaId, int cicloId, int lavarropasNumero, int ingresoId,
        int clienteId, String clienteNombre, String elementoNombre,
        int cantidad, LocalDateTime fechaFinCiclo, LocalDateTime fechaListo) { }

/** Una fila de la selección a marcar como Listo. Une el qué con el cuánto. */
public record MarcaListo(ElementoLavadoPendiente item, int cantidad) { }
```

**Por qué los dos records arrastran `lavarropasNumero` y `fechaFinCiclo`:** el nombre del elemento no
identifica nada por sí solo — puede haber "Batas" de tres clientes lavadas en tres lavarropas distintos
el mismo día. El operador necesita ver **cliente, lavarropas y cuándo se lavó** para saber qué está
tocando, tanto al doblar como al decidir el destino. `SalidaLista` los conserva aunque ya esté marcada
como lista, para que la tabla derecha no pierda ese contexto.

2. **`features/lavadero/dao/SalidaLavaderoDAO.java`** con:

```java
List<ElementoLavadoPendiente> obtenerLavadosPendientesDeListo();
List<SalidaLista>             obtenerListasSinDestino();
void marcarListo(List<MarcaListo> marcas);   // valida cada saldo DENTRO de la transacción
void volverALavado(int salidaId);            // sólo si destino IS NULL
```

SQL de pendientes (agrupa por fila de ciclo, no por línea de clasificación: así se ve qué ciclo lavó qué):

```sql
SELECT eci.id AS elemento_ciclo_id, eci.ciclo_id, cl.lavarropas_numero, ecl.ingreso_id,
       il.cliente_id, c.nombre AS cliente, cel.nombre AS elemento,
       eci.cantidad, COALESCE(SUM(sl.cantidad), 0) AS ya_lista, cl.fecha_fin
FROM elementos_ciclo_lavadero eci
JOIN ciclos_lavadero cl                    ON cl.id  = eci.ciclo_id
JOIN elementos_clasificacion_lavadero ecl  ON ecl.id = eci.elemento_clasificacion_id
JOIN catalogo_elementos_lavadero cel       ON cel.id = ecl.elemento_id
JOIN ingresos_lavadero il                  ON il.id  = ecl.ingreso_id
JOIN clientes c                            ON c.id   = il.cliente_id
LEFT JOIN salidas_lavadero sl              ON sl.elemento_ciclo_id = eci.id
WHERE cl.fecha_fin IS NOT NULL
GROUP BY eci.id, eci.ciclo_id, cl.lavarropas_numero, ecl.ingreso_id, il.cliente_id, c.nombre,
         cel.nombre, eci.cantidad, cl.fecha_fin
HAVING ya_lista < eci.cantidad
ORDER BY cl.fecha_fin, c.nombre, cel.nombre
```

El de listas sin destino tiene que hacer el mismo recorrido hacia atrás (`sl → eci → cl` y
`eci → ecl → il → clientes`) para traer `lavarropas_numero`, `fecha_fin` y el cliente. No es un
`SELECT * FROM salidas_lavadero`.

`marcarListo` recibe **la selección entera** y la resuelve en **una sola transacción**: por cada
marca hace el `SELECT` del saldo actual de ese `elemento_ciclo_id` y **sólo si `saldo >= cantidad`**
inserta. Ese `SELECT` exige además `cl.fecha_fin IS NOT NULL` (añadido al ejecutar): una tanda de un
ciclo todavía activo no está lavada, no devuelve fila y se trata como saldo cero, en vez de colarse
por no estar en la pantalla de la que salió. Si alguna falla, lanza `BusinessException` y **no se inserta ninguna** — marcar 8 filas y que
entren 5 sin aviso sería peor que no marcar nada. El mensaje tiene que ser accionable e identificar la
fila culpable ("Batas (Hosp. A, lavarropas 4) ya no tiene 10 disponibles. Refrescá la pantalla.").
Sin este chequeo, dos marcados seguidos sobre un snapshot viejo dejan la tabla sobregirada.

`volverALavado` hace `DELETE FROM salidas_lavadero WHERE id = ? AND destino IS NULL`; si afecta 0
filas lanza `BusinessException` (o la salida ya se derivó, o ya no existe).

3. **Errores:** este DAO **no** traga excepciones. Un fallo de SQL lanza `DatabaseException`, tanto en
   lectura como en escritura. (Los DAOs viejos del lavadero loguean y devuelven lista vacía; ese
   comportamiento es deuda y no se replica — ver `plans/hallazgos-arquitectura-pendientes.md`.)

   > **Corrección aplicada al ejecutar (2026-08-18):** el plan nombraba `DataAccessException` y
   > `BusinessException`, que **no existían** en el repo. Decidido con el usuario:
   > - `BusinessException extends ApplicationException` **se creó** (`common/exception/`). Es el
   >   vocabulario que usan también los pasos 5, 7, 8 y 9, y ahora es un tipo real.
   > - `DataAccessException` **no** se creó: los fallos de SQL de lectura usan `DatabaseException`,
   >   igual que `CicloLavaderoDAO.obtenerTodosLosCiclos()`. Nadie catchea lectura y escritura por
   >   separado, así que el segundo tipo no aportaba nada.

4. **Tests** `SalidaLavaderoDAOTest` (H2, mismo armado que `CicloLavaderoDAOTest`):
   - Un elemento de un ciclo **activo** no aparece como pendiente de Listo.
   - Al finalizar el ciclo, aparece con `cantidadPendiente() == cantidad del ciclo`.
   - `marcarListo` parcial: el pendiente baja, la salida aparece en `obtenerListasSinDestino`.
   - `marcarListo` de la cantidad total: el elemento desaparece de pendientes.
   - `marcarListo` por encima del saldo → `BusinessException` y **ninguna fila insertada**.
   - `marcarListo` con cantidad 0 o negativa → `BusinessException`, nada insertado.
   - `marcarListo` con **tres marcas** válidas: entran las tres en una transacción.
   - `marcarListo` con tres marcas donde **la del medio** sobregira → `BusinessException` y
     **cero filas insertadas** (ni la primera, que era válida). Es el test del todo-o-nada.
   - Tanto los pendientes como las listas traen `lavarropasNumero` y `fechaFinCiclo` correctos
     (dos elementos iguales lavados en lavarropas distintos se distinguen en la lista).
   - `volverALavado` de una salida sin destino: vuelve al pendiente.
   - `volverALavado` de una salida con destino → `BusinessException` y la fila sigue ahí.
   - Un elemento repartido en dos ciclos aparece como **dos filas** con sus saldos independientes.

### Verificación

```bash
mvn test -Dtest=SalidaLavaderoDAOTest
mvn test
```

### Criterio de salida

- [ ] `SalidaLavaderoDAO` con los 4 métodos y sus tests en verde.
- [ ] Ningún `catch (SQLException) { return lista vacía }` en el archivo nuevo.
- [ ] El sobregiro es imposible aun llamando dos veces seguidas con el mismo snapshot (hay test).
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `feat: SalidaLavaderoDAO con saldos de lavado y marcado parcial de Listo`

---

# Paso 3 — Extraer `EquipoOtrosDAO.guardar(Connection, EquipoOtros)`

**Modelo:** el más fuerte · **Depende de:** nada · **Paralelo con:** Pasos 1 y 2

### Contexto

Derivar salidas al CDE tiene que ser atómico: crear el `equipo_otros` **y** estampar el destino en
`salidas_lavadero` en la misma transacción. Si se parten en dos:

- crear primero y estampar después → si falla lo segundo, queda un ingreso huérfano en CDE y las
  salidas siguen figurando como pendientes: se pueden derivar de nuevo y se duplica el ingreso;
- estampar primero y crear después → si falla lo segundo, las salidas figuran derivadas y en CDE no
  entró nada: mercadería perdida sin rastro.

Hoy `EquipoOtrosDAO.guardar(EquipoOtros)` (líneas 87-206) toma su propia conexión, hace
`setAutoCommit(false)`, commitea y cierra. **Refactor puro, sin cambio de comportamiento:** extraer el
cuerpo a un método que recibe la `Connection` y propaga `SQLException`, y dejar el público como
envoltorio que abre/commitea/cierra exactamente igual que ahora (incluido el `rollback` + `return false`
ante error, que sus llamadores actuales esperan).

### Tareas

1. En `EquipoOtrosDAO`, extraer:

```java
/**
 * Persiste un equipo "otros" dentro de una transacción ya abierta por el llamador.
 * No commitea ni cierra: eso es responsabilidad de quien abrió la conexión.
 * Propaga SQLException para que el llamador pueda abortar toda la transacción.
 *
 * @return id generado de equipo_otros (también seteado en el modelo)
 */
public int guardar(Connection conn, EquipoOtros equipo) throws SQLException { ... }
```

Contenido: los pasos 1, 2a y 2b actuales, sin `setAutoCommit`, sin `commit`, sin `rollback`, sin
`close`. Devuelve `equipoId`.

2. El método público queda:

```java
public boolean guardar(EquipoOtros equipo) {
    Connection conn = null;
    try {
        conn = ConnectionPool.getConnection();
        conn.setAutoCommit(false);
        int equipoId = guardar(conn, equipo);
        conn.commit();
        log.info("EquipoOtros guardado: ID={}, tipo={}", equipoId, equipo.getTipoIngreso());
        return true;
    } catch (SQLException e) {
        rollback(conn, e);
        return false;
    } finally {
        close(conn);
    }
}
```

3. **No cambiar nada más.** Ni firmas de `EquipoOtrosService`, ni llamadores, ni el manejo de errores
   del método público. Este paso es puramente estructural.

4. **Tests:** los de `EquipoOtrosDAOTest` que ya existen tienen que pasar **sin tocarlos** — es la
   prueba de que el refactor no cambió comportamiento. Agregar uno nuevo: `guardar(conn, equipo)`
   dentro de una transacción que después se hace **rollback** no deja filas en `equipo_otros` ni en
   `equipo_otros_materiales`.

### Verificación

```bash
mvn test -Dtest=EquipoOtrosDAOTest
mvn test -Dtest='*Otros*'
mvn test
```

### Criterio de salida

- [ ] Existe `guardar(Connection, EquipoOtros)` público que propaga `SQLException`.
- [ ] `git diff` muestra que ningún test existente fue modificado.
- [ ] El test nuevo prueba que el rollback del llamador deshace el ingreso.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `refactor: EquipoOtrosDAO.guardar acepta una Connection del llamador`

---

# Paso 4 — `ConstructorIngresoCDE`: de salidas listas a `EquipoOtros`

**Modelo:** por defecto · **Depende de:** Paso 2 · **Paralelo con:** Paso 7

### Contexto

Convertir una selección de salidas listas en ingresos de CDE es **lógica pura**: agrupar por cliente,
mapear cada elemento a un `MaterialOtros` y setear las banderas correctas. No necesita BD ni Swing, así
que va en una clase plana testeable en aislamiento — el mismo patrón que `ConstructorEquipo` en
ortopedias y que `StagingCiclos` en lavadero.

Reglas del ingreso que se crea (decididas con el usuario):

| Campo | Valor | Por qué |
|---|---|---|
| `nroCliente` | **lo que decida el `AsignadorClienteCDE`**: el `clienteId` de la salida, o el de APTIUM | es lo único que varía entre las dos acciones de CDE |
| `tipoIngreso` | `DETALLES` | conserva qué elemento es cada cosa |
| `estado` | `NUEVO` (el default del constructor) | — |
| `requiereLavado` | **`false`** | ya se lavó; `calcularSiguienteEstado(NUEVO, false, true)` → `EMPAQUETADO` |
| `requiereEmpaque` | `true` | sigue el flujo normal de CDE |
| materiales | un `MaterialOtros(descripcion = elementoNombre, cantidad)` por salida | `catalogo_otros` se auto-crea en el DAO |

Detalle a resolver: dos salidas del mismo cliente con el **mismo nombre de elemento** (p. ej. "Batas"
lavadas en dos ciclos distintos). Se **suman en un solo `MaterialOtros`**: es lo mismo que hace
`EquipoOtros.unificarEnMemoria` y evita dos filas idénticas en la cola de CDE.

### Tareas

1. **`features/lavadero/controller/helpers/AsignadorClienteCDE.java`** — la única variación entre
   derivar "con su cliente" y derivar "como APTIUM":

```java
/** Bajo qué cliente entra al CDE una salida de lavadero. */
@FunctionalInterface
public interface AsignadorClienteCDE {

    int clientePara(SalidaLista salida);

    /** Conserva el cliente que trajo la ropa. */
    AsignadorClienteCDE CLIENTE_ORIGINAL = SalidaLista::clienteId;
}
```

La implementación de APTIUM necesita resolver el id por nombre contra la BD, así que **no** vive acá:
es `AsignadorClienteAptium` y se define en el Paso 5, junto con su migración de siembra.

2. **`features/lavadero/controller/helpers/ConstructorIngresoCDE.java`** — clase plana, sin estado:

```java
/**
 * Arma los ingresos de CDE que corresponden a un conjunto de salidas listas.
 *
 * <p>Agrupa por el cliente que decide el {@link AsignadorClienteCDE} (un {@link EquipoOtros}
 * por cliente asignado) y, dentro de cada grupo, unifica las salidas del mismo elemento
 * sumando cantidades. Con el asignador de APTIUM todas las salidas caen en el mismo grupo,
 * vengan del cliente que vengan: la agrupación no necesita una regla aparte.
 *
 * <p>Sin dependencias de Swing ni de JDBC: es lógica de armado y se testea sola.
 */
public final class ConstructorIngresoCDE {

    /** @param asignador de dónde sale el nro_cliente de cada ingreso */
    public IngresosCDE construir(List<SalidaLista> salidas, AsignadorClienteCDE asignador) { ... }
}

/**
 * Los ingresos armados y de qué salidas salió cada uno.
 * Van juntos a propósito: se calculan en una sola pasada y no pueden desincronizarse.
 */
public record IngresosCDE(List<EquipoOtros> ingresos, Map<EquipoOtros, List<Integer>> salidaIds) { }
```

El asignador es un **parámetro del método**, no del constructor: `ConstructorIngresoCDE` sigue siendo
una sola instancia sin estado, y quien elige la variante es el derivador que la llama.

3. **Tests `ConstructorIngresoCDETest`** (sin BD, sin Swing). Salvo aclaración, con
   `CLIENTE_ORIGINAL`:
   - Una salida de un cliente → un ingreso con un material, cantidad correcta.
   - Tres salidas de dos clientes → dos ingresos, cada uno con el material de su cliente.
   - Dos salidas del mismo cliente y mismo elemento → **un** material con la suma.
   - Dos salidas del mismo cliente y distinto elemento → un ingreso con dos materiales.
   - **Con un asignador que devuelve siempre 99:** tres salidas de tres clientes distintos → **un
     solo** ingreso con `nroCliente == 99` y los tres materiales. Es el test de la variante APTIUM,
     sin necesitar la BD.
   - **Con un asignador que devuelve siempre 99:** dos salidas de clientes distintos pero del mismo
     elemento → **un** material con la suma (la unificación es por grupo, no por cliente original).
   - Todo ingreso creado tiene `tipoIngreso == DETALLES`, `requiereLavado == false`,
     `requiereEmpaque == true`, `estado == NUEVO`.
   - **Test de la regla de negocio real:** para cada ingreso construido,
     `equipo.getSiguienteEstado(EstadoEquipo.NUEVO) == EstadoEquipo.EMPAQUETADO`. Es la aserción que
     protege la decisión de diseño de que lo lavado no se vuelve a lavar en CDE.
   - Lista vacía → lista vacía, sin `NullPointerException`.

### Verificación

```bash
mvn test -Dtest=ConstructorIngresoCDETest
mvn clean package -q
```

### Criterio de salida

- [ ] `ConstructorIngresoCDE` no importa `java.sql` ni `javax.swing`.
- [ ] Existe el test que verifica el salto `NUEVO → EMPAQUETADO`.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `feat: ConstructorIngresoCDE arma los ingresos otros desde las salidas de lavadero`

---

# Paso 5 — Derivadores de destino + `derivar` transaccional + paso a `FINALIZADO`

**Modelo:** el más fuerte · **Depende de:** Pasos 2, 3 y 4 · **Paralelo con:** Paso 7

### Contexto

Es el corazón del plan y el paso con más riesgo: una transacción que escribe en tablas de **dos
features** (`salidas_lavadero` y `equipo_otros`).

Lo que varía entre destinos es una sola cosa: **qué se crea en otro lado, si es que se crea algo**.
Lo común (estampar `destino`/`fecha_salida` y recalcular el estado de los ingresos afectados) es
idéntico. Eso pide una interfaz chica con una implementación por destino: agregar un tercer destino
en el futuro (p. ej. "ingresa como ortopedia") es una clase nueva + una constante del enum + una
entrada en el registry, sin tocar nada de lo existente.

**Dónde vive el derivador y por qué:** en `dao/`, no en `service/`. Recibe una `Connection` porque
tiene que participar de la transacción del llamador, y en este repo **los services no ven JDBC**
(regla ya establecida: ver `plans/sacar-sql-de-equipo-otros-correccion-service.md`). El derivador de
CDE depende de `EquipoOtrosDAO`, que es de otra feature; es la dependencia mínima posible para que la
atomicidad sea real, y queda declarada en el constructor y cableada en `AppContext`.

El paso a `FINALIZADO` copia literalmente el patrón de
`CicloLavaderoDAO.actualizarEstadoIngresosAfectados` (líneas 226-250): por cada ingreso tocado,
verificar con un `SELECT` si ya está todo cubierto y recién ahí hacer el `UPDATE`.

### Tareas

1. **`features/lavadero/dao/derivadores/DerivadorSalidas.java`**:

```java
/**
 * Qué hace la aplicación con un conjunto de salidas de lavadero ya listas.
 * Una instancia por {@link AccionSalida}.
 *
 * <p>Recibe la {@link Connection} de la transacción abierta por {@code SalidaLavaderoDAO}:
 * lo que el derivador escriba y el estampado del destino tienen que ser atómicos.
 */
public interface DerivadorSalidas {

    /** La acción que este derivador atiende. El destino a persistir sale de ella. */
    AccionSalida accion();

    /**
     * @return equipo_otros creado para cada salida (salidaId → equipoOtrosId).
     *         Vacío si esta acción no genera nada en el CDE.
     */
    Map<Integer, Integer> derivar(Connection conn, List<SalidaLista> salidas) throws SQLException;
}
```

Se indexa por **acción** y no por destino porque las dos variantes de CDE comparten el destino
persistido (`CDE_OTROS`) y se diferencian sólo en el cliente que asignan: un mapa por destino no
podría tener las dos.

2. **`DerivadorFueraDeFlujo`** — `accion()` devuelve `FUERA_DE_FLUJO`; `derivar` devuelve `Map.of()`.
   Sin estado, sin dependencias. Existe para que el orquestador no tenga un `if` sobre el enum.

3. **`AsignadorClienteAptium(ClienteDAO)`** — resuelve el id de APTIUM **por nombre**, nunca
   hardcodeado (depende del `AUTO_INCREMENT`). Usa `clienteDAO.buscarPorNombre(...)` y se queda con la
   coincidencia **exacta** (`equalsIgnoreCase`), porque ese método busca por coincidencia parcial y
   "APTIUM" podría traer varias filas. Resuelve una vez y cachea el id. **Si no encuentra el cliente,
   lanza `ResourceNotFoundException`** con un mensaje que diga qué falta y cómo arreglarlo: derivar en
   silencio a un cliente equivocado sería mucho peor que fallar.
   El nombre vive en `Constantes.Lavadero.CLIENTE_APTIUM = "APTIUM"`.

4. **Migración `V18__cliente_aptium.sql`** — una migración **nueva**, no un agregado a `V17`:

```sql
-- El cliente bajo el que se derivan al CDE los materiales que no conservan su cliente original.
-- En producción ya existe; esto es para las BD de desarrollo y para los tests.
-- clientes.nombre es UNIQUE desde V4, así que INSERT IGNORE es idempotente.
INSERT IGNORE INTO clientes (nombre) VALUES ('APTIUM');
```

5. **`DerivadorIngresoCDE(AccionSalida, ConstructorIngresoCDE, AsignadorClienteCDE, EquipoOtrosDAO)`**
   — **una sola clase, dos instancias**: `(CDE_CLIENTE, …, CLIENTE_ORIGINAL, …)` y
   `(CDE_APTIUM, …, asignadorAptium, …)`. Arma los ingresos con el asignador que le tocó, los persiste
   con `equipoOtrosDAO.guardar(conn, equipo)` y devuelve el mapa `salidaId → equipoOtrosId`. Loguea a
   INFO qué ingreso se creó, para qué cliente y con cuántos elementos.
   No hay dos clases casi iguales: lo único que cambia entre las variantes es el asignador.

6. **`SalidaLavaderoDAO.derivar(DerivadorSalidas derivador, List<SalidaLista> salidas)`**:

```
try (TransactionalConnection tx = TransactionalConnection.begin()) {
    conn = tx.get();
    1. verificarSinDestino(conn, salidaIds)      // relee destino IS NULL; si falta alguna → BusinessException
    2. Map<Integer,Integer> equipos = derivador.derivar(conn, salidas);
    3. estamparDestino(conn, salidaIds, derivador.accion().destinoPersistido(), equipos);
         UPDATE salidas_lavadero
            SET destino = ?, equipo_otros_id = ?, fecha_salida = NOW()
          WHERE id = ? AND destino IS NULL
         -- si affectedRows != 1 en alguna → SQLException, la transacción entera se cae
    4. finalizarIngresosCompletos(conn, ingresoIds);
    tx.commit();
}
```

`ingresoIds` sale de la propia selección: `salidas.stream().map(SalidaLista::ingresoId)
.collect(toSet())`. No hace falta consultarlo, `SalidaLista` ya lo trae.

El paso 1 (releer antes de escribir) es lo que impide derivar dos veces desde una pantalla no
refrescada; el `AND destino IS NULL` del paso 3 es el cinturón además de los tirantes.

7. **`finalizarIngresosCompletos(Connection, Set<Integer> ingresoIds)`** — por cada ingreso:

```sql
SELECT (SELECT COALESCE(SUM(ecl.cantidad), 0)
          FROM elementos_clasificacion_lavadero ecl
         WHERE ecl.ingreso_id = ?)                                          AS total,
       (SELECT COALESCE(SUM(sl.cantidad), 0)
          FROM salidas_lavadero sl
          JOIN elementos_ciclo_lavadero eci           ON eci.id  = sl.elemento_ciclo_id
          JOIN elementos_clasificacion_lavadero ecl2  ON ecl2.id = eci.elemento_clasificacion_id
         WHERE ecl2.ingreso_id = ? AND sl.destino IS NOT NULL)              AS derivado
```

Si `derivado >= total` → `UPDATE ingresos_lavadero SET estado = '<FINALIZADO>' WHERE id = ?`.

8. **Tests `SalidaLavaderoDerivacionTest`** (H2, ingreso + clasificación + ciclo finalizado + salidas
   listas como fixture):
   - Derivar con `FUERA_DE_FLUJO`: las salidas quedan con destino y `equipo_otros_id` NULL; **no** se
     creó ninguna fila en `equipo_otros`.
   - Derivar con `CDE_CLIENTE`: se creó **un** `equipo_otros` por cliente, con `requiere_lavado = 0`,
     `tipo_ingreso = 'DETALLES'` y un `equipo_otros_materiales` por elemento; las salidas apuntan a él,
     y su `nro_cliente` es el del ingreso de lavadero.
   - Derivar con `CDE_APTIUM` salidas de **dos clientes distintos** → **un solo** `equipo_otros` cuyo
     `nro_cliente` es el id de APTIUM resuelto por nombre, con los materiales de los dos.
   - Las dos acciones de CDE dejan `salidas_lavadero.destino = 'CDE_OTROS'`: en la salida **no** se
     distingue cuál se usó (es la decisión tomada; el test la fija).
   - `AsignadorClienteAptium` con la tabla `clientes` sin APTIUM → `ResourceNotFoundException`, y la
     transacción no deja nada escrito.
   - Derivar dos clientes de una con `CDE_CLIENTE` → dos ingresos distintos, cada salida apuntando al suyo.
   - Derivar una salida **ya derivada** → `BusinessException` y **nada** cambia (ni salidas ni `equipo_otros`).
   - **Atomicidad:** con un `EquipoOtrosDAO` que lanza `SQLException` al guardar, `derivar` falla y
     `salidas_lavadero` queda intacta (destino sigue NULL). Es el test que justifica todo el paso 3.
   - Derivar **todo** un ingreso → `ingresos_lavadero.estado == 'FINALIZADO'`.
   - Derivar **parte** de un ingreso → sigue en `'LAVADO'`.
   - Derivar el resto después → recién ahí pasa a `'FINALIZADO'`.
   - **Convivencia con Correcciones:** derivar a CDE y después llamar a
     `equipoOtrosDAO.eliminarEquipo(id)` sobre el ingreso creado **no lanza excepción**, y la salida
     queda con `destino = 'CDE_OTROS'` y `equipo_otros_id` en NULL. Es el test que verifica la
     política `ON DELETE SET NULL` de la migración; con `RESTRICT` la eliminación desde Correcciones
     se rompería.

### Verificación

```bash
mvn test -Dtest=SalidaLavaderoDerivacionTest
mvn test -Dtest='*Lavadero*'
mvn test
```

### Criterio de salida

- [ ] `DerivadorSalidas` con **dos clases y tres instancias** (una por `AccionSalida`); ningún
      `switch`/`if` sobre `AccionSalida` ni sobre `DestinoSalida` en el DAO.
- [ ] `V18__cliente_aptium.sql` es una migración nueva; `V17` quedó **sin tocar** desde el Paso 1.
- [ ] El id de APTIUM se resuelve por nombre; `grep -rn "CLIENTE_APTIUM" src/main/java` no muestra
      ningún id numérico hardcodeado.
- [ ] Existe el test de atomicidad (fallo al crear el ingreso ⇒ salidas intactas) y está en verde.
- [ ] Existen los tres tests de `FINALIZADO` (parcial / completo / completado después).
- [ ] Los invariantes 1-8 se cumplen; en particular, **cero JDBC fuera de `dao/`**.
- [ ] Commit: `feat: derivación transaccional de salidas de lavadero a CDE o fuera del flujo`

---

# Paso 6 — `SalidaLavaderoService`

**Modelo:** por defecto · **Depende de:** Pasos 2 y 5 · **Paralelo con:** Paso 7

### Contexto

La capa que consume el controller. En este repo un service **sólo valida y delega**: mirar
`CicloLavaderoService` como molde exacto (constructor con null-check, `ValidationException.builder()`,
delegación al DAO, cero JDBC).

La elección del derivador según la acción es un `Map<AccionSalida, DerivadorSalidas>` armado en el
propio service a partir de la colección que recibe (`toMap(DerivadorSalidas::accion, d -> d)`). El
service no conoce las clases concretas: recibe la lista y busca. Agregar una acción no lo modifica.

### Tareas

1. **`features/lavadero/service/SalidaLavaderoService.java`**:

```java
public SalidaLavaderoService(SalidaLavaderoDAO dao, List<DerivadorSalidas> derivadores)
```

Construye el mapa por acción y **falla en el constructor** si falta un derivador para algún valor de
`AccionSalida` o si hay dos para la misma acción. Una acción sin derivador es un bug de cableado:
tiene que explotar al arrancar la app, no cuando el operador hace clic.

API:

```java
List<ElementoLavadoPendiente> obtenerLavadosPendientesDeListo();
List<SalidaLista>             obtenerListasSinDestino();
void marcarListo(List<MarcaListo> marcas);
void volverALavado(int salidaId);
void derivar(AccionSalida accion, List<SalidaLista> seleccion);
```

`MarcaListo` lleva el `ElementoLavadoPendiente` completo (no sólo el id) para poder validar sin
consultar: `cantidad > 0` y `cantidad <= item.cantidadPendiente()`. Es validación de UI amable; la
verdad la sigue teniendo el chequeo transaccional del DAO.

**Una sola firma para los dos modos de la pantalla**: marcado parcial de una fila es una lista de una
`MarcaListo` con la cantidad del spinner; marcado masivo es una lista con
`item.cantidadPendiente()` en cada una. El service y el DAO no saben cuál de los dos fue.

Validaciones con `ValidationException.builder()`:
- `marcarListo`: lista nula o vacía / alguna marca con item nulo / cantidad ≤ 0 / cantidad > pendiente
  (el mensaje de error nombra el elemento, el cliente y el lavarropas, no el id) / dos marcas del
  mismo `elementoCicloId` en la misma lista.
- `derivar`: acción nula / selección nula o vacía / cantidad ≤ 0 en alguna salida.
- `volverALavado`: `salidaId <= 0`.

2. **Tests `SalidaLavaderoServiceTest`** (Mockito, DAO mockeado):
   - Cada validación lanza `ValidationException` con el mensaje esperado y **no** llega al DAO
     (`verifyNoInteractions`).
   - El camino feliz delega con los argumentos correctos.
   - `derivar(CDE_CLIENTE, ...)` y `derivar(CDE_APTIUM, ...)` llaman al DAO con derivadores
     **distintos**, cada uno con la `accion()` que corresponde. Es el test que prueba que las dos
     variantes no se confunden pese a compartir el destino persistido.
   - Constructor con la lista de derivadores incompleta (falta `CDE_APTIUM`) → `IllegalArgumentException`.
   - Constructor con dos derivadores de la misma acción → `IllegalArgumentException`.

### Verificación

```bash
mvn test -Dtest=SalidaLavaderoServiceTest
mvn clean package -q
grep -rn "java.sql" src/main/java/com/example/features/lavadero/service/   # sin resultados
```

### Criterio de salida

- [ ] El service no importa `java.sql` ni `javax.swing`.
- [ ] El registry se valida en el constructor, con tests de los dos modos de fallo.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `feat: SalidaLavaderoService con validación y registry de derivadores`

---

# Paso 7 — Pantalla `Salidas de Lavadero` (sólo vista)

**Modelo:** por defecto · **Depende de:** Paso 2 (usa los records) · **Paralelo con:** Pasos 4, 5 y 6

### Contexto

Vista pura: widgets, getters, `refrescar(...)`, `mostrarError`/`mostrarInfo`. **Cero lógica** — es la
regla del repo (`PantallaClasificacionLavadero` es el molde). Los listeners los cablea el controller
en el paso 8.

Layout: `JSplitPane` horizontal, mitad y mitad.

```
┌─ SALIDAS DE LAVADERO ──────────────────────────────────────────────────── [← Volver] ─┐
│ ┌─ Lavados — pendientes de secado y doblado ──┐ ┌─ Listos — pendientes de destino ───┐ │
│ │ Elemento │Pend.│ Cliente │Lav.│ Lavado el   │ │ Elemento │Cant│ Cliente │Lav.│Lavado│ │
│ │ Batas    │  12 │ Hosp. A │  4 │ 12/08 14:30 │ │ Toallón  │  6 │ Hosp. A │  4 │ 12/08│ │
│ │ Batas    │   5 │ Hosp. B │  7 │ 12/08 16:10 │ │ ...                                 │ │
│ │ ...            (multi-selección)            │ │        (multi-selección)            │ │
│ ├─────────────────────────────────────────────┤ ├────────────────────────────────────┤ │
│ │ Cantidad: [ 12 ▲▼ ]       [ Marcar Listo ]  │ │ [Volver a Lavado] [Sale del flujo] │ │
│ │ (el spinner sólo se habilita con 1 fila)    │ │                  [Ingresar al CDE] │ │
│ └─────────────────────────────────────────────┘ └────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

Las dos "Batas" del ejemplo son el motivo de las columnas Cliente y Lavarropas: sin ellas las filas
son indistinguibles y el operador no sabe cuál está marcando.

### Tareas

1. **`Constantes`**:
   - `Pantallas.SALIDAS_LAVADERO = "SALIDAS_LAVADERO"`
   - `Titulos.SALIDAS_LAVADERO = "SALIDAS DE LAVADERO"`
   - `Botones.SALIDAS = "Salidas"`, `Botones.MARCAR_LISTO = "Marcar Listo"`,
     `Botones.VOLVER_A_LAVADO = "Volver a Lavado"`, `Botones.SALE_DEL_FLUJO = "Sale del flujo"`,
     `Botones.INGRESAR_A_CDE = "Ingresar al CDE"`
   - `Textos.TABLA_LAVADOS_TITULO = "Lavados — pendientes de secado y doblado"`,
     `Textos.TABLA_LISTOS_TITULO = "Listos — pendientes de destino"`
   - `Mensajes.CONFIRMAR_SALE_DEL_FLUJO` (irreversible → `JOptionPane.showConfirmDialog`).
   - `Mensajes.ELEGIR_CLIENTE_CDE` — el texto del diálogo de derivación al CDE, que **pregunta y
     confirma en un solo paso**: "Se van a derivar N elemento(s) al CDE. ¿A nombre de quién ingresan?"
     Las dos opciones salen de `AccionSalida.CDE_CLIENTE.getNombre()` y
     `AccionSalida.CDE_APTIUM.getNombre()`, así que el texto de los botones no se duplica en `Constantes`.
   - `Constantes.Lavadero.CLIENTE_APTIUM = "APTIUM"` (lo usa el Paso 5; si el Paso 5 ya se ejecutó, ya está).

2. **`view/helpers/ElementoLavadoTableModel.java`** — columnas `Elemento | Pendiente | Cliente |
   Lavarropas | Lavado el`. Calcado de `ElementoDisponibleTableModel`: `setItems(List<...>)`,
   `getItemAt(int)`, `isCellEditable` siempre `false`, `getColumnClass` para que `Pendiente` y
   `Lavarropas` ordenen como número.

3. **`view/helpers/SalidaListaTableModel.java`** — columnas `Elemento | Cantidad | Cliente |
   Lavarropas | Lavado el | Listo el`. Mismo contexto que la tabla izquierda: la decisión de destino
   también se toma mirando de quién es y de dónde salió.

4. **`view/PantallaSalidasLavadero.java`**:
   - `PanelHeader(Constantes.Titulos.SALIDAS_LAVADERO, navegador, contenedor, Constantes.Pantallas.LAVADERO)`.
   - `JSplitPane` con las dos tablas (`TableStyler.applyStandard`, columnas numéricas centradas con
     `TableStyler.centerColumns`).
   - **Las dos tablas** en `MULTIPLE_INTERVAL_SELECTION`.
   - `JSpinner` de cantidad con `SpinnerNumberModel(1, 1, 1, 1)`. El controller le reajusta el máximo
     y lo **deshabilita cuando hay más de una fila seleccionada** (con varias filas la acción es
     "marcar todo lo pendiente de cada una", así que una cantidad única no significaría nada).
     Métodos `setMaximoCantidad(int)` y `setSpinnerHabilitado(boolean)` en la vista.
   - Un `JLabel` de ayuda bajo la tabla derecha con el texto
     `Constantes.Textos.AYUDA_SALIDA_ENTERA` — "Una salida se deriva entera. Si una parte va al CDE y
     otra no, volvela a Lavado y marcala en dos tandas." Es la consecuencia operativa de que la salida
     no se parta; si no está escrita en la pantalla, el operador la descubre equivocándose.
   - Getters: `getTablaLavados()`, `getTablaListos()`, `getSpnCantidad()`, `getBtnMarcarListo()`,
     `getBtnVolverALavado()`, `getBtnSaleDelFlujo()`, `getBtnIngresarACde()`,
     `getSeleccionLavados()` → `List<ElementoLavadoPendiente>`,
     `getSeleccionListos()` → `List<SalidaLista>`.
   - `refrescar(List<ElementoLavadoPendiente>, List<SalidaLista>)`.
   - `mostrarError` / `mostrarInfo` / `confirmar(String)` calcados de `PantallaClasificacionLavadero`.
   - **`AccionSalida elegirAccionCde(int cantidadFilas)`** — un `JOptionPane.showOptionDialog` con las
     dos opciones de CDE y Cancelar; devuelve la `AccionSalida` elegida o **`null` si se canceló**.
     Es presentación pura (arma un diálogo y traduce el índice del botón), así que puede vivir en la
     vista sin romper la regla de "cero lógica": no decide nada, sólo pregunta. El controller es quien
     interpreta el `null` como "no hacer nada".

5. **`PantallaLavadero`**: agregar `btnSalidas` con su getter. Con 5 botones, `GridLayout(2, 2, 15, 0)`
   ya no alcanza → pasar a `new GridLayout(0, 3, 15, 10)` (dos filas: 3 + 2). El botón nuevo va
   **después de "Ciclos"**, que es el orden del flujo real: Ingresar → Clasificar → Ciclos → Salidas →
   Ver Ciclos.

6. **`PantallaPrincipal`**: campo, instanciación, `contenedor.add(pantallaSalidasLavadero,
   Constantes.Pantallas.SALIDAS_LAVADERO)` y getter, siguiendo el bloque de lavadero (líneas 95-99,
   116-120, 137-141).

7. **Tests:** los `TableModel` son lógica de presentación testeable sin levantar ventanas —
   `ElementoLavadoTableModelTest` y `SalidaListaTableModelTest`: `getRowCount`/`getValueAt` para cada
   columna, `getItemAt` fuera de rango devuelve `null`, `setItems(null)` deja la tabla vacía.
   La `Pantalla*` no se testea (no tiene lógica; si necesitara un test, es señal de que se le metió
   lógica que iba en el controller).

### Verificación

```bash
mvn test -Dtest=ElementoLavadoTableModelTest+SalidaListaTableModelTest
mvn clean package -q
```

Smoke manual: `java -jar target/aptium.jar` → Lavadero → el menú muestra 5 botones bien distribuidos
y "Salidas" abre una pantalla vacía con las dos tablas y el botón de volver funcionando.

### Criterio de salida

- [ ] `PantallaSalidasLavadero` no tiene ningún `addActionListener` ni llamada a service.
- [ ] Cero literales de texto fuera de `Constantes`.
- [ ] La pantalla abre y vuelve al menú; las 5 opciones del menú se ven bien.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `feat: pantalla Salidas de Lavadero (vista y modelos de tabla)`

---

# Paso 8 — `SalidasLavaderoController` + cableado + refresco inmediato

**Modelo:** el más fuerte · **Depende de:** Pasos 6 y 7

### Contexto

Une todo y cumple el requisito de "que se refleje inmediatamente": al derivar a CDE hay que disparar
el grupo de refresco **`operativo`**, que es el que alimenta *Registrar estado*, *Para entregar* y
*Gestionar lotes* (`UiCoordinator.java:67-70` y `crearRefrescadorOperativo`). Sin ese disparo el
ingreso nuevo existe en la BD pero no aparece en pantalla hasta el próximo guardado de otra cosa.

La pantalla nueva se carga **al abrirse**, como Ciclos y Clasificación (el listener del botón del menú
navega y después llama a `cargarDatos()`), no en cada guardado: es una pantalla de trabajo puntual.

**Concurrencia:** todas las lecturas van por `TareaUI` (fuera del EDT). No copiar el acceso directo de
`CiclosController.cargarDatos()`, que es deuda registrada.

### Tareas

1. **`features/lavadero/controller/SalidasLavaderoController.java`**:

```java
public SalidasLavaderoController(PantallaSalidasLavadero pantalla,
                                 SalidaLavaderoService salidaLavaderoService,
                                 Runnable refrescoOperativo)
```

- Cablea los 4 botones y el listener de selección de la tabla izquierda: con **una** fila ajusta el
  máximo (`setMaximoCantidad(item.cantidadPendiente())`) y habilita el spinner; con **cero o varias**
  lo deshabilita.
- `marcarListo()` arma la `List<MarcaListo>` según el modo: una fila → la cantidad del spinner;
  varias → `item.cantidadPendiente()` de cada una. **Un solo llamado al service** con la lista
  entera, así el todo-o-nada lo garantiza la transacción del DAO y no el controller.
- `cargarDatos()`: una `TareaUI` que lee las **dos** listas y pinta ambas de una sola vez, para que no
  queden desincronizadas entre sí.
- `marcarListo()`: valida que haya selección, llama al service, recarga, sin diálogo de éxito (es una
  acción de alta frecuencia; el feedback es que la fila se mueve de tabla).
- `volverALavado()`: idem, sobre la selección de la derecha (una fila a la vez).
- `saleDelFlujo()`: `confirmar(...)` — es irreversible —, después `service.derivar(FUERA_DE_FLUJO,
  seleccion)` y recargar. **No** dispara el refresco: en el CDE no cambió nada.
- `ingresarACde()`: `pantalla.elegirAccionCde(seleccion.size())`; si devuelve `null` (cancelado) no
  pasa nada. Si no, `service.derivar(accion, seleccion)`, recargar y disparar
  `refrescoOperativo.run()`. Ese diálogo **es** la confirmación: no se pide una segunda.
  Después, `mostrarInfo` con el resumen, que tiene que decir **a nombre de quién** entraron
  ("Se creó 1 ingreso en el CDE a nombre de APTIUM" / "Se crearon 2 ingresos en el CDE").
- Manejo de errores: `ValidationException` → `mostrarError(String.join("\n", ex.getValidationErrors()))`;
  `BusinessException` → `mostrarError(ex.getMessage())` **y recargar** (el mensaje siempre es
  "tu pantalla está vieja", así que recargar es parte de la respuesta).

2. **`AppContext`**: construir `SalidaLavaderoDAO`, `ConstructorIngresoCDE`,
   `AsignadorClienteAptium` (recibe el `ClienteDAO` que ya se construye ahí) y los **tres**
   derivadores — `DerivadorFueraDeFlujo`, y dos `DerivadorIngresoCDE` que sólo se diferencian en la
   acción y el asignador:

```java
List<DerivadorSalidas> derivadores = List.of(
    new DerivadorFueraDeFlujo(),
    new DerivadorIngresoCDE(AccionSalida.CDE_CLIENTE, constructorIngresoCDE,
                            AsignadorClienteCDE.CLIENTE_ORIGINAL, equipoOtrosDAO),
    new DerivadorIngresoCDE(AccionSalida.CDE_APTIUM, constructorIngresoCDE,
                            new AsignadorClienteAptium(clienteDAO), equipoOtrosDAO));
```

   Después `SalidaLavaderoService`; agregar el campo, el parámetro del constructor, el null-check y el
   getter, siguiendo el bloque de lavadero (líneas 181-195, 211-215, 276-293). Esta lista es **el
   único lugar** donde se decide qué acciones existen.

3. **`UiCoordinator`**: instanciar el controller pasándole `context.getSalidaLavaderoService()` y el
   `Disparador operativo`, y cablear el botón del menú:

```java
SalidasLavaderoController salidasController = new SalidasLavaderoController(
    vista.getPantallaSalidasLavadero(), context.getSalidaLavaderoService(), operativo);

vista.getPantallaLavadero().getBtnSalidas().addActionListener(e -> {
    vista.getNavegador().show(vista.getContenedor(), Constantes.Pantallas.SALIDAS_LAVADERO);
    salidasController.cargarDatos();
});
```

4. **Tests `SalidasLavaderoControllerTest`** (Mockito sobre el service y la pantalla, como
   `EstadoProcesosControllerTest`):
   - Derivar al CDE dispara el `Runnable` de refresco **exactamente una vez**.
   - Derivar a `FUERA_DE_FLUJO` **no** lo dispara (nada cambió en CDE).
   - El diálogo devuelve `CDE_CLIENTE` → el service recibe `CDE_CLIENTE`; devuelve `CDE_APTIUM` → el
     service recibe `CDE_APTIUM`.
   - El diálogo devuelve `null` (cancelado) → **nada** llega al service y no se dispara el refresco.
   - Sin selección, ninguna acción llega al service y se muestra el error.
   - Con **una** fila seleccionada, el service recibe una `MarcaListo` con la cantidad del spinner.
   - Con **tres** filas seleccionadas, el service recibe **un solo** llamado con tres `MarcaListo`,
     cada una con el pendiente total de su fila, y el spinner quedó deshabilitado.
   - Si el usuario cancela la confirmación, no se llama al service.
   - `BusinessException` del service → se muestra el mensaje **y** se recarga.

### Verificación

```bash
mvn test -Dtest=SalidasLavaderoControllerTest
mvn test
mvn clean package
```

**Smoke manual (el que realmente valida el plan) — `java -jar target/aptium.jar`:**

1. Lavadero → Ingresar: cargar un ingreso para un cliente con 2 bolsas.
2. Clasificar: 10 "Batas" y 5 "Toallón".
3. Ciclos: arrastrar los dos elementos a un lavarropas, lanzar y finalizar el ciclo.
4. Salidas: aparecen las dos filas. Marcar 4 Batas como Listo → izquierda queda en 6, derecha muestra 4.
5. "Volver a Lavado" sobre esas 4 → vuelve a 10. Rehacer el marcado con las 10 y con los 5 Toallón.
6. Seleccionar las Batas → "Ingresar al CDE" → en el diálogo elegir **"Ingresa al CDE con su cliente"**.
7. **Sin salir de la app**: Esterilización → Registrar estado → el ingreso aparece a nombre del
   cliente original con 10 Batas y el siguiente estado ofrecido es **Empaquetado** (no Lavando).
8. Volver a Salidas → seleccionar los Toallón → "Ingresar al CDE" → esta vez elegir **"Ingresa al CDE
   como APTIUM"**. En Registrar estado el ingreso nuevo figura a nombre de **APTIUM**, no del cliente.
9. Repetir con un segundo ingreso de **otro** cliente y derivar a APTIUM las salidas de los dos
   clientes **en una sola operación**: tiene que crearse **un solo** ingreso de APTIUM con los
   elementos de ambos.
10. "Sale del flujo" con lo que quede. La tabla derecha queda vacía.
11. Verificar en BD:
    - `SELECT estado FROM ingresos_lavadero WHERE id = <el del paso 1>` → `FINALIZADO`.
    - `SELECT DISTINCT destino FROM salidas_lavadero` → sólo `CDE_OTROS` y `FUERA_DE_FLUJO`
      (las dos variantes de CDE **no** se distinguen acá; eso es lo esperado).

### Criterio de salida

- [ ] El flujo completo del smoke manual funciona, incluidos el punto 7 (reflejo inmediato), el 8
      (variante APTIUM), el 9 (dos clientes en un solo ingreso de APTIUM) y el 11.
- [ ] Ninguna consulta a BD ocurre en el EDT (arrancar con `-Daptium.edt.strict=true` y navegar la
      pantalla nueva sin que explote).
- [ ] `SalidasLavaderoControllerTest` en verde.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `feat: pantalla Salidas de Lavadero operativa con derivación inmediata al CDE`

---

> **Los pasos 7.5 a 8.7 nacieron del smoke manual del Paso 8 (2026-08-19).** Ninguno es un bug
> del código del Paso 8: el smoke no encontró nada mal en el controller ni en el cableado. Son
> tres decisiones del plan que no sobrevivieron al uso real (7.5 y 8.5) y dos arreglos de la
> pantalla de Ciclos que el smoke destapó y el usuario decidió traer adelante (8.6 y 8.7).
> El triage completo, incluidas las dos observaciones que **no** se arreglan acá, está en
> [Mutaciones aplicadas](#mutaciones-aplicadas).

# Paso 7.5 — El menú de Lavadero con 5 botones

**Modelo:** por defecto · **Depende de:** Paso 7 (ya commiteado) · **Paralelo con:** 8.5, 8.6, 8.7

### Contexto

El Paso 7 decidió `new GridLayout(0, 3, 15, 10)` para acomodar el quinto botón: dos filas de 3 + 2.
El código hace exactamente eso ([`PantallaLavadero.java:28`](../src/main/java/com/example/features/lavadero/view/PantallaLavadero.java#L28)),
y el resultado es una fila de tres botones y una de dos que quedan estirados y descentrados debajo.
La decisión fue tomada sobre un dibujo ASCII, no sobre la pantalla.

Los otros menús de la app son la referencia de qué se ve bien acá: hay que mirarlos antes de elegir,
no inventar un layout nuevo para esta pantalla sola.

### Tareas

1. Mirar cómo resuelven el problema los menús que ya existen (`PantallaEsterilizacion`,
   `PantallaPrincipal`, el propio menú raíz) y **copiar el que mejor se vea con 5 opciones**.
   Alternativas razonables, en orden de preferencia:
   - `GridLayout(0, 1)` de 5 filas dentro de un contenedor de ancho fijo y centrado — es el patrón de
     menú vertical y no depende de que la cantidad de botones sea múltiplo de nada.
   - `GridLayout(0, 2)` con 5 botones (3 + 2) y el último ocupando las dos columnas vía `GridBagLayout`.
2. **Cero literales nuevos fuera de `Constantes`** (invariante 5). Si el layout necesita medidas,
   van a `Estilos.Espaciados`, no hardcodeadas en la pantalla.
3. No tocar el orden de los botones: `Ingresar → Clasificar → Ciclos → Salidas → Ver Ciclos` es el
   orden del flujo real y esa parte de la decisión del Paso 7 sigue siendo correcta.

### Verificación

```bash
mvn clean package -q
```

Smoke: `java -jar target/aptium.jar` → Lavadero. Los 5 botones se ven parejos y el menú no queda
visualmente distinto de los otros menús de la app (abrirlos y compararlos).

### Criterio de salida

- [ ] El menú de Lavadero se ve consistente con el resto de los menús de la app.
- [ ] `PantallaLavadero` no tiene literales de texto ni medidas hardcodeadas.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `fix: el menú de Lavadero acomoda sus 5 opciones`

---

# Paso 8.5 — Drag and drop entre las dos tablas de Salidas

**Modelo:** el más fuerte · **Depende de:** Paso 8 (commiteado) · **Paralelo con:** 7.5, 8.6, 8.7

### Contexto

El Paso 7 resolvió el movimiento entre tablas con botones + un `JSpinner` de cantidad. Funciona, pero
**es la única pantalla de la app donde mover cosas de un lado a otro no se hace arrastrando**: Ciclos
(disponibles ↔ cards) y Lotes (materiales ↔ lote) ya usan DnD multi-fila, con la infraestructura
compartida `MultiRowTableTransferHandler` + `LocalObjectFlavors.forList()`. La decisión de botones no
estaba mal en abstracto; está mal **en esta app**, y el costo de la inconsistencia lo paga el operador.

**Decisión tomada con el usuario (2026-08-19):** el DnD **reemplaza** al spinner. Al soltar se pregunta
la cantidad con un diálogo, y si se arrastran varias filas los diálogos van **en cadena**, uno por
fila. Es exactamente la mecánica que el operador ya conoce de Ciclos
(`CiclosController.seleccionarSubcantidad` + `abrirDialogoSubdivisionUnidad`, que también encadena un
diálogo por unidad).

**Los botones se quedan.** El DnD es aditivo: sacarlos dejaría la pantalla inoperable con teclado y
sin ninguna pista visible de qué se puede hacer. Botón y drop tienen que ejecutar **el mismo código**;
si divergen, el bug aparece en el camino que nadie prueba.

**Punto que no se negocia:** todas las marcas de una tanda —vengan de un drop de 4 filas o del botón
con 4 filas seleccionadas— se acumulan en **una sola** `List<MarcaListo>` y se mandan en **un solo**
llamado al service. El todo-o-nada lo garantiza la transacción del DAO (invariante 8 y decisión del
Paso 2), no el controller. Un bucle de N llamados rompería esa garantía en silencio.

### El problema de la dirección inversa

Hoy `volverALavado` es de a una salida por vez: `SalidaLavaderoService.volverALavado(int salidaId)`,
y el controller directamente rechaza la multi-selección con
`Constantes.Mensajes.VOLVER_A_LAVADO_UNA_SOLA`. Arrastrar N filas de derecha a izquierda con N
llamados sucesivos dejaría el mismo agujero que el Paso 2 cerró del otro lado: 4 filas arrastradas,
2 revertidas, error en la tercera, y la pantalla en un estado que nadie pidió.

Por eso este paso **agrega la variante transaccional en lote**, simétrica de `marcarListo`:

```java
// SalidaLavaderoDAO
void volverALavado(List<Integer> salidaIds);   // una sola transacción; si alguna ya tiene destino,
                                               // lanza BusinessException y no se borra ninguna
```

El método de a uno se conserva delegando en el nuevo con `List.of(id)`, para no tocar a sus llamadores.

### Tareas

1. **`SalidaLavaderoDAO`**: `volverALavado(List<Integer>)` en una transacción
   (`TransactionalConnection`), mismo molde que `marcarListo`. Cada `DELETE ... WHERE id = ? AND
   destino IS NULL` tiene que afectar 1 fila; si alguno afecta 0, `BusinessException` con el mensaje
   accionable que identifique **cuál** salida falló, y rollback de todas.
2. **`SalidaLavaderoService`**: exponer `volverALavado(List<SalidaLista>)` con su validación
   (`ValidationException.builder()`: lista no vacía, sin ids repetidos). Cero JDBC (invariante 3).
3. **`view/DistribucionCantidadDialog.java`** (nuevo, en `lavadero/view/`): el diálogo de "cuántas de
   estas N marcás Listo". Presentación pura, mismo rol que `EquipoSubdivisionDialog`. Recibe nombre
   del elemento, cliente, lavarropas y máximo; devuelve la cantidad elegida o **0 si se canceló**.
   Incluye la checkbox **"Todas (N)"** del Paso 8.7 desde el vamos — es el mismo widget.
   Todos sus textos en `Constantes`.
4. **`PantallaSalidasLavadero`**:
   - **Eliminar** el `JSpinner` y sus tres métodos (`getSpnCantidad`, `setMaximoCantidad`,
     `setSpinnerHabilitado`), y el `JLabel` de ayuda del spinner si queda huérfano.
   - `setDragEnabled(true)` + `setDropMode(DropMode.ON)` + `MultiRowTableTransferHandler` en las dos
     tablas, con el `DataFlavor` de `LocalObjectFlavors.forList()`. Una tabla **no** acepta lo que
     salió de sí misma (el flag anti-rebote de `CiclosController.lavarropasArrastre` es el precedente).
   - Los handlers se **cablean desde el controller**, como en Ciclos: la vista expone las tablas, no
     decide qué pasa al soltar. La vista sigue sin `addActionListener` ni llamadas al service.
5. **`SalidasLavaderoController`**:
   - `marcarListo(List<ElementoLavadoPendiente>)` como **único** camino: lo llaman el botón (con
     `getSeleccionLavados()`) y el drop (con las filas arrastradas). Para cada fila, si
     `cantidadPendiente() == 1` no pregunta nada; si no, abre `DistribucionCantidadDialog`.
     Cancelar un diálogo **saltea esa fila y sigue con la siguiente** (mismo criterio que
     `procesarDropRegular`, que con `k <= 0` simplemente no agrega). Si al final no quedó ninguna
     marca, **no** se llama al service.
   - `volverALavado(List<SalidaLista>)`: idem, un solo llamado al nuevo método del service. Se borra
     el rechazo de multi-selección y `Constantes.Mensajes.VOLVER_A_LAVADO_UNA_SOLA` queda sin uso →
     se elimina.
   - `sincronizarSpinner()` y `cantidadDelSpinner()` desaparecen con el spinner, y con ellos el
     `ListSelectionListener` de la tabla izquierda.
   - Las escrituras siguen yendo por `TareaUI` a través del helper `ejecutar(...)` que ya existe. Los
     diálogos se abren **antes** de `ejecutar(...)`, en el EDT; el service se llama con la lista ya
     armada.
6. **Tests `SalidasLavaderoControllerTest`** — reemplazar los dos casos del spinner y agregar:
   - Un drop de 3 filas produce **un solo** llamado a `marcarListo` con 3 `MarcaListo`.
   - Cancelar el diálogo de la fila del medio → el service recibe 2 marcas, no 3.
   - Cancelar **todos** los diálogos → el service no se llama.
   - Una fila con `cantidadPendiente() == 1` no abre diálogo.
   - Botón y drop con la misma selección producen llamados idénticos al service.
   - Un drop de 3 filas de derecha a izquierda → **un solo** `volverALavado` con los 3 ids.
7. **Tests `SalidaLavaderoDAOTest`**: `volverALavado` de 3 salidas sin destino las revierte todas;
   con la del medio ya derivada → `BusinessException` y **las tres siguen existiendo**.

> **Tamaño:** este paso toca dao + service + view + controller y ronda el límite de ~400 líneas del
> protocolo de mutación. Si al ejecutarlo se pasa, partirlo en **8.5.a** (el `volverALavado` en lote:
> DAO + service + sus tests) y **8.5.b** (el DnD: vista + diálogo + controller), en ese orden.
>
> **Ejecutado así (2026-08-25):** 8.5.a en `681e0d7` (141 líneas) y 8.5.b en `48ac8b0` (268).
> Ver [Mutaciones aplicadas](#mutaciones-aplicadas) para las dos correcciones de diseño.

### Verificación

```bash
mvn test -Dtest=SalidasLavaderoControllerTest+SalidaLavaderoDAOTest
mvn test
mvn clean package
```

Smoke manual (`java -Daptium.edt.strict=true -jar target/aptium.jar`):

1. Arrastrar **una** fila de Lavados a Listos con pendiente 10 → pide cantidad → marcar 4. Izquierda 6,
   derecha 4.
2. Arrastrar **tres** filas juntas → tres diálogos en cadena; cancelar el segundo → entran la primera
   y la tercera, la segunda queda intacta.
3. Arrastrar una fila con pendiente 1 → no pregunta nada.
4. Arrastrar **dos** filas de Listos de vuelta a Lavados → las dos vuelven, sin diálogo.
5. El botón "Marcar Listo" con la misma selección se comporta igual que el arrastre.
6. Ninguna consulta a BD en el EDT (el flag `-Daptium.edt.strict=true` no dispara).

### Criterio de salida

- [ ] Las dos tablas mueven filas por arrastre, en las dos direcciones, de a una o varias.
      *(pendiente del smoke manual)*
- [x] Toda tanda de marcado y toda tanda de reversión es **un solo** llamado al service.
      Verificado por `marcarListo_dropDeTresFilas_unSoloLlamadoConTresMarcas` y
      `volverALavado_dropDeTresFilas_unSoloLlamado`, y garantizado abajo por la transacción del DAO
      (`volverALavado_siLaDelMedioYaTieneDestino_lanzaYLasTresSiguenExistiendo`).
- [x] El `JSpinner` ya no existe en la pantalla ni en el controller.
- [x] `PantallaSalidasLavadero` sigue sin lógica (ni un `addActionListener`).
- [x] Los invariantes 1-8 se cumplen: `mvn clean package` sin warnings nuevos, `mvn test` en 918
      verdes sin modificar tests existentes para que pasen, cero JDBC en `service/`, cero Swing en
      `model/dao/service/`, y ningún literal de UI fuera de `Constantes`.
- [x] Commits: `681e0d7` (8.5.a) y `48ac8b0` (8.5.b).

---

# Paso 8.6 — Ciclos: las cards no arrastran configuración entre visitas

**Modelo:** por defecto · **Depende de:** nada · **Paralelo con:** 7.5, 8.5, 8.7

### Contexto

**Esto es deuda preexistente de la pantalla de Ciclos, no de este plan.** El smoke la destapó y el
usuario decidió traerla adelante en vez de mandarla al backlog.

Diagnóstico verificado: `cargarDatos()` **sí** se llama al entrar a Ciclos
([`UiCoordinator.java:200`](../src/main/java/com/example/app/ui/UiCoordinator.java#L200)) y **sí**
repinta los ítems de cada card. Lo que nunca se resetea es la **configuración**: tipo de lavado,
jabón, mililitros, suavizante, potenciador y litros totales son widgets de `LavarropasCard` que se
completan una vez y no los toca nadie más. `LavarropasCard` no tiene ningún método de reset. Entonces
el operador entra, configura, se va, vuelve, y la card le ofrece la configuración del lavado anterior
como si fuera la de este — con el riesgo de lanzar un ciclo con el jabón y los mililitros equivocados.

El staging de elementos **no** es el problema: al volver ya está vacío, porque el guard de salida
(`setGuardVolver(this::tienePendientes, ..., this::descartarPendientes)`) lo descarta.

### Por qué no alcanza con resetear en `cargarDatos()`

`cargarDatos()` se llama también después de lanzar, de finalizar y de devolver elementos a
disponibles. Resetear ahí borraría la configuración **mientras el operador está cargando el
lavarropas de al lado**. El reset tiene que engancharse a **abrir la pantalla**, que es un evento
distinto.

### Tareas

1. **`LavarropasCard.resetConfiguracion()`**: deja los widgets de configuración en su estado inicial
   (tipo de lavado sin elegir, jabón sin elegir, ml y litros vacíos, checkboxes destildadas) y llama
   a `actualizarBtnAccion()` para que el botón refleje que ya no está configurada.
2. **`CiclosController.abrirPantalla()`** (público, nuevo): resetea la configuración de **las cards
   que no tienen ciclo activo** y después llama a `cargarDatos()`. Una card en modo activo muestra el
   ciclo que está corriendo: pisarle la configuración sería mentir sobre lo que hay adentro del
   lavarropas.
3. **`UiCoordinator`**: el listener del botón "Ciclos" pasa a llamar `ciclosController.abrirPantalla()`
   en vez de `cargarDatos()`. `cargarDatos()` queda como lo que siempre fue: recargar datos, sin
   efectos sobre lo que el operador tipeó.
4. **Test:** verificar primero si `LavarropasCard` se puede construir en headless (el repo ya tiene
   `EquipoSubdivisionDialogTest`, así que hay precedente de tests sobre clases de `view/`). Si sí,
   `LavarropasCardTest`: configurar la card, llamar `resetConfiguracion()`, y comprobar que
   `getTipoLavado()`, `getJabon()`, `getLitrosJabon()` y `getLitrosTotales()` vuelven a null/vacío y
   las checkboxes a `false`. Si no corre en headless, **decirlo en el commit** y cubrirlo sólo con el
   smoke — no inventar una abstracción para poder testear tres setters.

### Verificación

```bash
mvn test
mvn clean package -q
```

Smoke: Lavadero → Ciclos → configurar el lavarropas 2 (tipo, jabón, 500 ml, suavizante) → Volver →
Ciclos otra vez. La card 2 está **en blanco**. Repetir con un ciclo **activo** en el lavarropas 3: al
volver a entrar, la card 3 sigue mostrando su ciclo tal cual.

### Criterio de salida

- [ ] Entrar a Ciclos siempre presenta las cards libres sin configurar.
- [ ] Las cards con ciclo activo no se tocan.
- [ ] Lanzar, finalizar y devolver elementos **no** borran la configuración de las demás cards.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `fix: las cards de Ciclos no conservan la configuración de la visita anterior`

---

# Paso 8.7 — Ciclos: "todas las unidades" en el diálogo de distribución

**Modelo:** por defecto · **Depende de:** nada · **Paralelo con:** 7.5, 8.5, 8.6

### Contexto

También es una mejora sobre la pantalla de Ciclos, fuera del alcance original de este plan, traída
adelante por decisión del usuario.

El diálogo es el "¿Cuántas unidades distribuís ahora?" de
[`CiclosController.seleccionarSubcantidad`](../src/main/java/com/example/features/lavadero/controller/CiclosController.java#L279-L297):
un `JSpinner` que arranca en 1 aunque haya 40 disponibles. El caso más frecuente —"todas"— es el que
más clics cuesta. Es el mismo gesto que ya existe en varios diálogos del CDE.

Además el diálogo está **armado con Swing dentro del controller**, que es el smell que el repo evita
en todas partes (`EquipoSubdivisionDialog` es el precedente correcto: diálogo propio en `view/`, con
su test).

### Tareas

1. **Extraer `view/DistribucionUnidadesDialog.java`** del método `seleccionarSubcantidad`: mismo
   contenido, misma firma de resultado (cantidad elegida, o **0 si se canceló**), calcado del molde de
   `EquipoSubdivisionDialog`. `CiclosController` pasa a instanciarlo y leer el resultado.
2. **Checkbox "Todas (N)"**: tildarla lleva el spinner al máximo y lo deshabilita; destildarla lo
   vuelve a habilitar en el valor que tenía. Es el mismo widget que consume el Paso 8.5 en
   `DistribucionCantidadDialog` — si los dos diálogos terminan siendo el mismo, **unificarlos** y
   anotarlo como mutación; si no, que al menos compartan la constante del texto.
3. **Literales a `Constantes`** (invariante 5): el título del diálogo, "Unidades:", "Todas (%d)",
   "Confirmar" y "Cancelar" salen del código y van a `Constantes.Textos` / `Constantes.Botones`.
   `EquipoSubdivisionDialog` tiene los mismos literales hardcodeados; **arreglarlos también**, es el
   archivo de al lado y el mismo commit.
4. **Test `DistribucionUnidadesDialogTest`**: si corre en headless (ver Paso 8.6), que tildar la
   checkbox deje el spinner en el máximo y destildarla lo restaure. Si no, extraer esa regla a un
   método estático y testear ese.

### Verificación

```bash
mvn test
mvn clean package -q
```

Smoke: Ciclos → arrastrar un elemento con 40 disponibles a un lavarropas → el diálogo ofrece
"Todas (40)" → tildar → confirmar → las 40 quedan en la card.

### Criterio de salida

- [ ] El diálogo de unidades vive en `view/`, no dentro de `CiclosController`.
- [ ] "Todas (N)" funciona en los dos sentidos (tildar y destildar).
- [ ] Ningún literal de UI en `CiclosController`, `DistribucionUnidadesDialog` ni
      `EquipoSubdivisionDialog`.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `feat: el diálogo de distribución de unidades permite elegir todas`

---

# Paso 9 — Documentación

**Modelo:** por defecto · **Depende de:** Pasos 8, 7.5, 8.5, 8.6, 8.7 y el plan de fracciones

### Contexto

`docs/MAPA.md` es el índice pantalla→archivos y `CLAUDE.md` la explicación de por qué la app está
armada así. Los dos quedan desactualizados con este plan: hay una pantalla nueva, un estado nuevo en
el ciclo de vida del ingreso de lavadero y un puente entre dos features que antes no existía.

### Tareas

1. **`docs/MAPA.md`**:
   - Tabla "Lavadero" (§3): fila nueva `Salidas | lavadero/view/PantallaSalidasLavadero |
     SalidasLavaderoController | SalidaLavadero`.
   - §7 "Cosas que confunden": entrada nueva explicando que **Lavadero y CDE se tocan en un solo
     punto** — `DerivadorIngresoCDE`, que crea un `equipo_otros` con `requiereLavado = false` dentro
     de la transacción de la derivación — y que ese es el único lugar donde una feature escribe en las
     tablas de otra.
   - Si el conteo de LOC de §6 quedó muy corrido, actualizarlo; si no, dejarlo.

2. **`CLAUDE.md`**:
   - Sección "Ortopedias vs. Otros": nota de que un ingreso "Otros" puede nacer de dos lugares —
     carga manual o derivación desde Lavadero — que el segundo entra con `requiereLavado = false`, y
     que puede quedar a nombre del cliente original **o de APTIUM** según lo que se elija al derivar.
   - Aclarar la distinción `AccionSalida` (lo que el operador elige) vs `DestinoSalida` (lo que se
     persiste): dos acciones distintas guardan el mismo destino, y esa asimetría es deliberada.
   - Sección nueva o ampliación de la de lavadero con el ciclo de vida completo del ingreso:
     `PENDIENTE → CLASIFICADO → LAVADO → FINALIZADO`, y la aclaración de que "Listo" (secado y
     doblado) vive por cantidad en `salidas_lavadero`, no en el ingreso.

3. Si para entonces ya se ejecutó [`plans/fracciones-de-equipo-persistidas.md`](fracciones-de-equipo-persistidas.md),
   documentar también que un `Equipo*` repartido en varios lavarropas es **una sola** unidad y que no
   aparece en Salidas hasta que todas sus partes están lavadas. Si todavía no se ejecutó, **decirlo
   explícitamente** en `docs/MAPA.md` §7: la pantalla de Salidas cuenta de más los `Equipo*` y ese es
   un defecto conocido, no una sorpresa a descubrir.

4. Actualizar el índice de memoria del proyecto con el estado final de este plan.

### Verificación

```bash
git diff --stat docs/MAPA.md CLAUDE.md
```

Leer los dos archivos completos una vez: que un desarrollador nuevo pueda llegar de "quiero tocar la
pantalla de Salidas" a los archivos correctos usando sólo el mapa.

### Criterio de salida

- [ ] `docs/MAPA.md` lista la pantalla nueva y explica el puente Lavadero→CDE.
- [ ] `CLAUDE.md` documenta el ciclo de vida completo del ingreso de lavadero.
- [ ] Commit: `docs: pantalla Salidas de Lavadero y puente con el CDE`

---

## Protocolo de mutación del plan

- **Partir un paso:** si al ejecutarlo el diff supera ~400 líneas o toca más de una capa sin
  necesidad, partirlo en `N.a` / `N.b` y anotarlo acá con una línea de por qué.
- **Insertar un paso:** numerarlo `N.5` y declarar de qué pasos depende. No renumerar los existentes.
- **Saltear un paso:** sólo si su criterio de salida ya se cumple por otro camino. Anotarlo con la
  evidencia (comando + salida).
- **Abandonar:** si un paso resulta inviable, parar y consultar antes de improvisar un rodeo. Los
  pasos 3 y 5 son los que tienen supuestos más fuertes; si alguno se cae, el diseño de la derivación
  hay que rediscutirlo, no parchearlo.

### Mutaciones aplicadas

- **2026-08-19 — `ConstructorIngresoCDE` y compañía se mudan de `controller/helpers/` a
  `dao/derivadores/`.** El Paso 4 las ubicó en `features/lavadero/controller/helpers/` por analogía
  con `ConstructorEquipo`, pero al cerrar el Paso 5 quedó claro que la analogía no se sostiene:
  `ConstructorEquipo` lee un `JPanel` y lo llama un controller, mientras que `ConstructorIngresoCDE`
  no lo usa ningún controller — su único consumidor es `DerivadorIngresoCDE`, que vive en `dao/`.
  Eso dejaba un import `dao → controller`, al revés del flujo `model → dao → service →
  view/controller`. Se movieron `AsignadorClienteCDE`, `ConstructorIngresoCDE` e `IngresosCDE` (con
  su test) junto a los derivadores. Sin cambios de comportamiento: sólo `package` e `import`, y
  `mvn test` sigue en 861 verdes. **Consecuencia para los pasos que faltan:** el Paso 8 tiene que
  importar `AsignadorClienteCDE.CLIENTE_ORIGINAL` y `ConstructorIngresoCDE` desde
  `features.lavadero.dao.derivadores`, no desde `controller.helpers`.

- **2026-08-19 — Pasos 7.5, 8.5, 8.6 y 8.7, del smoke manual del Paso 8.** El smoke (11 puntos,
  `-Daptium.edt.strict=true`) dejó 6 observaciones. Triage, con la evidencia de cada una:

  | Obs | Clase | Destino | Evidencia |
  |---|---|---|---|
  | Menú de Lavadero antiestético con 5 botones | decisión del plan que falló | **Paso 7.5** | El Paso 7 pidió `GridLayout(0,3,15,10)` y `PantallaLavadero.java:28` lo hace |
  | Checkbox "todas" en el diálogo de distribución de Ciclos | mejora fuera de alcance, traída adelante por el usuario | **Paso 8.7** | `CiclosController.seleccionarSubcantidad` no es código de este plan |
  | DnD entre las tablas de Salidas | decisión del plan que falló | **Paso 8.5** | El Paso 7 eligió botones + spinner; Ciclos y Lotes ya usan `MultiRowTableTransferHandler` |
  | Cards de Ciclos con configuración de visitas previas | deuda preexistente, traída adelante por el usuario | **Paso 8.6** | `cargarDatos()` sí se llama al entrar y sí repinta ítems; `LavarropasCard` no tiene reset de configuración |
  | Se pierde la fracción al lanzar un ciclo | deuda preexistente, **fuera de este plan** | [`fracciones-de-equipo-persistidas.md`](fracciones-de-equipo-persistidas.md) | `instanciaId` es un `AtomicInteger` en memoria (`CiclosController.java:46`); `ElementoCicloMovimiento` sólo lleva `(elementoClasificacionId, cantidad)` y `elementos_ciclo_lavadero` (V10) no tiene columna de instancia |
  | Salidas multiplica los `Equipo*` | consecuencia de la anterior | idem | El Paso 2 asumió "una fila de `elementos_ciclo_lavadero` = una tanda", cierto para elementos regulares y falso para equipos subdivididos |

  **Ninguna observación fue un bug del código del Paso 8.** El controller, el cableado y las
  constantes hacen lo que el paso pedía; su criterio de salida se cumple y se commitea tal cual, con
  los arreglos encima como commits `fix:`/`feat:` separados.

  **Las dos últimas no se arreglan en este plan** y no son un `N.5`: la causa raíz está aguas arriba
  del alcance (persistencia de los ciclos), la corrección necesita migración, cambio de modelo,
  reparación de datos ya escritos y decisiones de dominio que no se pueden tomar de paso. Se van a un
  plan propio. **Consecuencia que hay que tener presente hasta que ese plan se ejecute: derivar un
  `Equipo*` subdividido al CDE crea de más.**

- **2026-08-25 — El Paso 8.5 se parte en `8.5.a` y `8.5.b`.** El paso tocaba dao + service + view +
  controller y el diff completo iba a superar el límite de ~400 líneas del protocolo, así que se
  ejecutó en dos commits, en el orden que el propio paso preveía:
  - **8.5.a** (`681e0d7`, 141 líneas) — `volverALavado(List<Integer>)` transaccional en el DAO,
    `volverALavado(List<SalidaLista>)` en el service, y sus tests. Sin tocar la UI.
  - **8.5.b** (`48ac8b0`, 268 líneas) — el DnD: vista, diálogo, controller y tests.

  Dos correcciones aplicadas al ejecutar:
  - **El diálogo no se duplica: se generaliza el que ya existe.** El paso pedía un
    `DistribucionCantidadDialog` nuevo "con la checkbox *Todas (N)* del Paso 8.7 — es el mismo
    widget". Como el 8.7 ya se había ejecutado y dejó `DistribucionUnidadesDialog` haciendo
    exactamente eso, crear la clase nueva habría duplicado ~85 líneas de spinner + checkbox
    condenadas a divergir. Decidido con el usuario: el diálogo pasa a tener constructor privado y
    dos fábricas — `paraCiclos(...)` (texto intacto) y `paraSalidas(...)`, que agrega el lavarropas
    al encabezado. Un solo widget, sin cambios de comportamiento en Ciclos.
  - **`setDragEnabled`/`setDropMode` los declara la vista; el controller sólo instala el
    `TransferHandler`.** El paso los ponía a los cuatro en el controller, pero
    `JTable.setDragEnabled(true)` lanza `HeadlessException`, y los tests del repo corren headless
    (Ciclos lo esquiva por accidente: configura su DnD desde un `ComponentListener` que en test
    nunca dispara). El reparto respeta igual la regla del paso — la vista declara que sus tablas
    arrastran y reciben, el controller decide qué significa soltar algo —, y de paso el
    controller queda construible en test.

  Además, `marcarListo(List)` y `volverALavado(List)` del controller quedaron **visibles en el
  paquete**: un drop no se puede simular sin entorno gráfico (`TransferSupport` no tiene
  constructor público para drops), así que el test ejercita el camino del arrastre llamándolos
  directamente. Y `SalidaLavaderoService.volverALavado(int)` se eliminó al quedar sin uso; el
  atajo de a uno sobrevive sólo en el DAO, como pedía el paso.

## Rollback

Modo directo, un commit por paso sobre `ConexionConCDE`: `git revert <sha>` alcanza para los pasos
2, 4, 5, 6, 7, 8 y 9.

Dos excepciones:
- **Pasos 1 y 5 (las migraciones):** revertir el commit borra el archivo `.sql`, pero lo que ya se
  aplicó sigue en la BD y en `flyway_schema_history`. Para el Paso 1 hay que hacer además
  `DROP TABLE salidas_lavadero;` y borrar su fila del historial. La `V18` del Paso 5 sólo agrega un
  cliente: **no** borrarlo si ya se usó en alguna derivación, y en producción ya existía de antes.
- **Paso 3:** es un refactor del que dependen los pasos 5 en adelante. Revertirlo aislado rompe la
  compilación; hay que revertir del 5 para arriba primero.
