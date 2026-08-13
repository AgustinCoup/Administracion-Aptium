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
| Cliente del ingreso en CDE | **Siempre el del ingreso de lavadero.** No se elige nada; por eso se agrupa por cliente al derivar. |
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

2. **`features/lavadero/model/EstadoIngresoLavadero.java`** — enum `PENDIENTE`, `CLASIFICADO`,
   `LAVADO`, `FINALIZADO`. Se persiste `name()`. Incluir `desdeBD(String)` con el mismo criterio
   defensivo que `TipoLavado.desdeBD` (log a WARN + default `PENDIENTE` ante valor nulo o desconocido).

3. **`features/lavadero/model/DestinoSalida.java`** — enum con `FUERA_DE_FLUJO("Sale del flujo")` y
   `CDE_OTROS("Ingresa al CDE")`, `getNombre()` y `toString()` devolviendo el texto visible.
   **Ojo:** a diferencia de `TipoLavado`, la columna es *nullable* y NULL significa "sin destino
   todavía", que es un estado legítimo. Por eso `desdeBD(null)` devuelve `null` (no un default) y
   así está documentado en el Javadoc.

4. Reemplazar los literales de estado en `ClasificacionLavaderoDAO` y `CicloLavaderoDAO` por
   concatenación del enum (`... SET estado = '" + EstadoIngresoLavadero.CLASIFICADO + "'`). Son
   constantes de compilación, no entrada de usuario: no hay riesgo de inyección. **No** tocar las
   migraciones ya aplicadas.

5. Tests:
   - `EstadoIngresoLavaderoTest` y `DestinoSalidaTest`: `desdeBD` con valor válido, minúsculas,
     desconocido y `null`.

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
- [ ] `EstadoIngresoLavadero` y `DestinoSalida` existen con sus tests.
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
inserta. Si alguna falla, lanza `BusinessException` y **no se inserta ninguna** — marcar 8 filas y que
entren 5 sin aviso sería peor que no marcar nada. El mensaje tiene que ser accionable e identificar la
fila culpable ("Batas (Hosp. A, lavarropas 4) ya no tiene 10 disponibles. Refrescá la pantalla.").
Sin este chequeo, dos marcados seguidos sobre un snapshot viejo dejan la tabla sobregirada.

`volverALavado` hace `DELETE FROM salidas_lavadero WHERE id = ? AND destino IS NULL`; si afecta 0
filas lanza `BusinessException` (o la salida ya se derivó, o ya no existe).

3. **Errores:** este DAO **no** traga excepciones. Un fallo de SQL en la lectura lanza
   `DataAccessException`; en la escritura, `DatabaseException`. (Los DAOs viejos del lavadero loguean y
   devuelven lista vacía; ese comportamiento es deuda y no se replica — ver
   `plans/hallazgos-arquitectura-pendientes.md`.)

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
| `nroCliente` | el `clienteId` de las salidas del grupo | el cliente viaja con el elemento |
| `tipoIngreso` | `DETALLES` | conserva qué elemento es cada cosa |
| `estado` | `NUEVO` (el default del constructor) | — |
| `requiereLavado` | **`false`** | ya se lavó; `calcularSiguienteEstado(NUEVO, false, true)` → `EMPAQUETADO` |
| `requiereEmpaque` | `true` | sigue el flujo normal de CDE |
| materiales | un `MaterialOtros(descripcion = elementoNombre, cantidad)` por salida | `catalogo_otros` se auto-crea en el DAO |

Detalle a resolver: dos salidas del mismo cliente con el **mismo nombre de elemento** (p. ej. "Batas"
lavadas en dos ciclos distintos). Se **suman en un solo `MaterialOtros`**: es lo mismo que hace
`EquipoOtros.unificarEnMemoria` y evita dos filas idénticas en la cola de CDE.

### Tareas

1. **`features/lavadero/controller/helpers/ConstructorIngresoCDE.java`** — clase plana, sin estado:

```java
/**
 * Arma los ingresos de CDE que corresponden a un conjunto de salidas listas.
 *
 * <p>Agrupa por cliente (un {@link EquipoOtros} por cliente) y, dentro de cada cliente,
 * unifica las salidas del mismo elemento sumando cantidades.
 *
 * <p>Sin dependencias de Swing ni de JDBC: es lógica de armado y se testea sola.
 */
public final class ConstructorIngresoCDE {

    /** @return un ingreso por cliente, en orden estable por clienteId */
    public List<EquipoOtros> construir(List<SalidaLista> salidas) { ... }

    /** Qué salidas alimentaron cada ingreso, para poder estampar equipo_otros_id después. */
    public Map<Integer, List<Integer>> salidaIdsPorCliente(List<SalidaLista> salidas) { ... }
}
```

*(Si al implementar resulta más limpio devolver un único record `IngresosCDE(List<EquipoOtros>,
Map<...>)` en una sola pasada, hacerlo: lo que no puede pasar es recorrer y agrupar dos veces con dos
criterios que se puedan desincronizar.)*

2. **Tests `ConstructorIngresoCDETest`** (sin BD, sin Swing):
   - Una salida de un cliente → un ingreso con un material, cantidad correcta.
   - Tres salidas de dos clientes → dos ingresos, cada uno con el material de su cliente.
   - Dos salidas del mismo cliente y mismo elemento → **un** material con la suma.
   - Dos salidas del mismo cliente y distinto elemento → un ingreso con dos materiales.
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
 * Una implementación por {@link DestinoSalida}.
 *
 * <p>Recibe la {@link Connection} de la transacción abierta por {@code SalidaLavaderoDAO}:
 * lo que el derivador escriba y el estampado del destino tienen que ser atómicos.
 */
public interface DerivadorSalidas {

    DestinoSalida destino();

    /**
     * @return equipo_otros creado para cada salida (salidaId → equipoOtrosId).
     *         Vacío si este destino no genera nada en el CDE.
     */
    Map<Integer, Integer> derivar(Connection conn, List<SalidaLista> salidas) throws SQLException;
}
```

2. **`DerivadorFueraDeFlujo`** — `destino()` devuelve `FUERA_DE_FLUJO`; `derivar` devuelve
   `Map.of()`. Sin estado, sin dependencias. Existe para que el orquestador no tenga un `if` sobre el
   enum.

3. **`DerivadorIngresoCDE(ConstructorIngresoCDE, EquipoOtrosDAO)`** — arma los ingresos por cliente,
   los persiste con `equipoOtrosDAO.guardar(conn, equipo)` y devuelve el mapa
   `salidaId → equipoOtrosId`. Loguea a INFO qué ingreso se creó para qué cliente y con cuántos
   elementos.

4. **`SalidaLavaderoDAO.derivar(DerivadorSalidas derivador, List<SalidaLista> salidas)`**:

```
try (TransactionalConnection tx = TransactionalConnection.begin()) {
    conn = tx.get();
    1. verificarSinDestino(conn, salidaIds)      // relee destino IS NULL; si falta alguna → BusinessException
    2. Map<Integer,Integer> equipos = derivador.derivar(conn, salidas);
    3. estamparDestino(conn, salidaIds, derivador.destino(), equipos);
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

5. **`finalizarIngresosCompletos(Connection, Set<Integer> ingresoIds)`** — por cada ingreso:

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

6. **Tests `SalidaLavaderoDerivacionTest`** (H2, ingreso + clasificación + ciclo finalizado + salidas
   listas como fixture):
   - Derivar a `FUERA_DE_FLUJO`: las salidas quedan con destino y `equipo_otros_id` NULL; **no** se
     creó ninguna fila en `equipo_otros`.
   - Derivar a `CDE_OTROS`: se creó **un** `equipo_otros` por cliente, con `requiere_lavado = 0`,
     `tipo_ingreso = 'DETALLES'` y un `equipo_otros_materiales` por elemento; las salidas apuntan a él.
   - Derivar dos clientes de una → dos ingresos distintos, cada salida apuntando al suyo.
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

- [ ] `DerivadorSalidas` con dos implementaciones; ningún `switch`/`if` sobre `DestinoSalida` en el DAO.
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

La elección del derivador según el destino es un `Map<DestinoSalida, DerivadorSalidas>` armado en
`AppContext` a partir de las implementaciones (`toMap(DerivadorSalidas::destino, d -> d)`). El service
no conoce las clases concretas: recibe la colección y busca. Agregar un destino no lo modifica.

### Tareas

1. **`features/lavadero/service/SalidaLavaderoService.java`**:

```java
public SalidaLavaderoService(SalidaLavaderoDAO dao, List<DerivadorSalidas> derivadores)
```

Construye el mapa por destino y **falla en el constructor** si falta un derivador para algún valor del
enum o si hay dos para el mismo destino. Un destino sin derivador es un bug de cableado: tiene que
explotar al arrancar la app, no cuando el operador hace clic.

API:

```java
List<ElementoLavadoPendiente> obtenerLavadosPendientesDeListo();
List<SalidaLista>             obtenerListasSinDestino();
void marcarListo(List<MarcaListo> marcas);
void volverALavado(int salidaId);
void derivar(DestinoSalida destino, List<SalidaLista> seleccion);
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
- `derivar`: destino nulo / selección nula o vacía / cantidad ≤ 0 en alguna salida.
- `volverALavado`: `salidaId <= 0`.

2. **Tests `SalidaLavaderoServiceTest`** (Mockito, DAO mockeado):
   - Cada validación lanza `ValidationException` con el mensaje esperado y **no** llega al DAO
     (`verifyNoInteractions`).
   - El camino feliz delega con los argumentos correctos.
   - `derivar(CDE_OTROS, ...)` llama al DAO con el derivador cuyo `destino()` es `CDE_OTROS`.
   - Constructor con la lista de derivadores incompleta → `IllegalArgumentException`.
   - Constructor con dos derivadores del mismo destino → `IllegalArgumentException`.

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
   - `Mensajes.CONFIRMAR_DERIVAR_CDE` y `Mensajes.CONFIRMAR_SALE_DEL_FLUJO` (texto de confirmación;
     ambas acciones son irreversibles, así que van con `JOptionPane.showConfirmDialog`).

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
- `saleDelFlujo()` e `ingresarACde()`: `confirmar(...)` primero — son irreversibles —, después
  `service.derivar(destino, seleccion)`, recargar, y **sólo en `CDE_OTROS`** disparar
  `refrescoOperativo.run()`. Mostrar un `mostrarInfo` con el resumen ("Se crearon 2 ingresos en el CDE").
- Manejo de errores: `ValidationException` → `mostrarError(String.join("\n", ex.getValidationErrors()))`;
  `BusinessException` → `mostrarError(ex.getMessage())` **y recargar** (el mensaje siempre es
  "tu pantalla está vieja", así que recargar es parte de la respuesta).

2. **`AppContext`**: construir `SalidaLavaderoDAO`, `ConstructorIngresoCDE`, los dos derivadores
   (`DerivadorIngresoCDE` recibe el `EquipoOtrosDAO` que ya se construye ahí) y
   `SalidaLavaderoService`; agregar el campo, el parámetro del constructor, el null-check y el getter,
   siguiendo el bloque de lavadero (líneas 181-195, 211-215, 276-293).

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
   - Derivar a `CDE_OTROS` dispara el `Runnable` de refresco **exactamente una vez**.
   - Derivar a `FUERA_DE_FLUJO` **no** lo dispara (nada cambió en CDE).
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
6. Seleccionar las Batas → "Ingresar al CDE" → confirmar.
7. **Sin salir de la app**: Esterilización → Registrar estado → el ingreso del cliente aparece con
   10 Batas y el siguiente estado ofrecido es **Empaquetado** (no Lavando).
8. Volver a Salidas → seleccionar los Toallón → "Sale del flujo" → confirmar. La tabla derecha queda vacía.
9. Verificar en BD: `SELECT estado FROM ingresos_lavadero WHERE id = <el del paso 1>` → `FINALIZADO`.

### Criterio de salida

- [ ] El flujo completo del smoke manual funciona, incluido el punto 7 (reflejo inmediato) y el 9.
- [ ] Ninguna consulta a BD ocurre en el EDT (arrancar con `-Daptium.edt.strict=true` y navegar la
      pantalla nueva sin que explote).
- [ ] `SalidasLavaderoControllerTest` en verde.
- [ ] Los invariantes 1-8 se cumplen.
- [ ] Commit: `feat: pantalla Salidas de Lavadero operativa con derivación inmediata al CDE`

---

# Paso 9 — Documentación

**Modelo:** por defecto · **Depende de:** Paso 8

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
     carga manual o derivación desde Lavadero — y que el segundo entra con `requiereLavado = false`.
   - Sección nueva o ampliación de la de lavadero con el ciclo de vida completo del ingreso:
     `PENDIENTE → CLASIFICADO → LAVADO → FINALIZADO`, y la aclaración de que "Listo" (secado y
     doblado) vive por cantidad en `salidas_lavadero`, no en el ingreso.

3. Actualizar el índice de memoria del proyecto con el estado final de este plan.

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

## Rollback

Modo directo, un commit por paso sobre `ConexionConCDE`: `git revert <sha>` alcanza para los pasos
2, 4, 5, 6, 7, 8 y 9.

Dos excepciones:
- **Paso 1:** revertir el commit borra la migración `V17`, pero la tabla ya creada sigue en la BD de
  desarrollo y en el historial de Flyway. Hay que hacer además
  `DROP TABLE salidas_lavadero;` y borrar la fila de `flyway_schema_history`.
- **Paso 3:** es un refactor del que dependen los pasos 5 en adelante. Revertirlo aislado rompe la
  compilación; hay que revertir del 5 para arriba primero.
