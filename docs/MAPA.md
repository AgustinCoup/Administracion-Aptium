# Mapa de la aplicación

Guía de navegación: "quiero tocar X → los archivos son estos".
Complementa a `CLAUDE.md`, que explica *por qué* está armado así; esto dice *dónde está*.

---

## 1. El recorrido de un dato, de punta a punta

Toda la app hace lo mismo cinco veces con nombres distintos. Si entendés este
recorrido una vez, entendés las nueve features:

```
Usuario hace clic en una Pantalla*  (features/<f>/view/)
        ↓  el listener lo cableó el Controller
Controller valida y arma el modelo   (features/<f>/controller/)
        ↓
Service aplica reglas de negocio     (features/<f>/service/)
        ↓
DAO ejecuta el SQL                   (features/<f>/dao/)
        ↓
MySQL
        ↓  al terminar, el controller llama al Runnable de refresco
Refrescador relee y reparte           (app/ui/RefrescadorPantallas)
        ↓
Controller.pintar(datos) → la Pantalla se actualiza
```

**El refresco es la pieza menos obvia y la que más confunde.** No hay un refresco
global: hay **cuatro grupos independientes**, definidos en
[UiCoordinator.java:67-70](../src/main/java/com/example/app/ui/UiCoordinator.java#L67-L70):

| Grupo | Se dispara cuando | Alimenta a |
|---|---|---|
| `operativo` | cualquier guardado | RegistrarEstado, EquiposParaEntregar, Lotes |
| `historialEquipos` | se abre Ver Equipos / Estado de procesos | VerCDEv2, VerEquipos |
| `historialLotes` | se abre Ver Lotes | VerLotes |
| `historialCiclos` | se abre Ver Ciclos | VerCiclos |

Si una pantalla "no se actualiza sola", el bug casi siempre está en qué
`Disparador` recibió ese controller, no en el DAO.

---

## 2. Los tres archivos que son el índice de todo

Cuando no sepas por dónde empezar, abrí estos en orden:

| Archivo | Qué te dice |
|---|---|
| [AppContext.java](../src/main/java/com/example/app/AppContext.java) | Qué services existen y de qué DAOs dependen. **Es el catálogo de capacidades de la app.** |
| [UiCoordinator.java](../src/main/java/com/example/app/ui/UiCoordinator.java) | Qué controller maneja qué pantalla y con qué services. **Es el índice pantalla→lógica.** |
| [PantallaPrincipal.java](../src/main/java/com/example/ui/shell/PantallaPrincipal.java) | Qué pantallas existen y su nombre en el CardLayout. |

Regla práctica: **de la pantalla al código se llega por `UiCoordinator`, no adivinando el nombre del archivo.**
Los nombres no siempre coinciden (`PantallaVerCDEv2` la maneja `EstadoProcesosController`).

---

## 3. Índice: pantalla → archivos

Ruta del usuario desde el menú, y qué tocar para cada una.

### Centro de Esterilización

| Pantalla (lo que ve el usuario) | Vista | Controller | Services que usa |
|---|---|---|---|
| Ingresar → Ortopedia | `equipos/ortopedias/view/PantallaIngresoOrtopedia` | `IngresoOrtopediaController` | Cliente, Catalogo, Profesional, Institucion, Equipo |
| Ingresar → Otros | `equipos/otros/view/PantallaIngresoOtros` | `OtrosInputController` | Cliente, CatalogoOtros, EquipoOtros |
| Registrar estado | `equipos/ortopedias/view/PantallaRegistrarEstado` | `common/controller/RegistrarEstadoController` | EquipoOtros, Material, EstadoValidator |
| Para entregar | `equipos/ortopedias/view/PantallaEquiposParaEntregar` | `common/controller/EquiposParaEntregarController` | EquipoOtros, Material, EstadoValidator |
| Ver equipos | `equipos/view/PantallaVerEquipos` | `VerEquiposController` | EquipoOtros, Cliente, Institucion, EquipoReporte, EquipoOtrosReporte |
| Estado de procesos | `equipos/ortopedias/view/PantallaVerCDEv2` | `EstadoProcesosController` | (solo lee del refrescador) |
| Correcciones | `equipos/ortopedias/view/PantallaCorrecciones` | `CorreccionesController` | EquipoCorreccion, EquipoOtrosCorreccion, CatalogoOtros |
| Auditoría | `equipos/ortopedias/view/PantallaAuditoria` | `CorreccionesController` (la inyecta, ver [línea 127](../src/main/java/com/example/app/ui/UiCoordinator.java#L127)) | — |
| Gestionar lotes | `lotes/view/PantallaLotes` | `LotesController` | Lote |
| Ver lotes | `lotes/view/PantallaVerLotes` | `VerLotesController` | LoteReporte |

### Lavadero

| Pantalla | Vista | Controller | Services |
|---|---|---|---|
| Menú lavadero | `lavadero/view/PantallaLavadero` | — (solo navega; los 5 botones se cablean en [UiCoordinator:187-214](../src/main/java/com/example/app/ui/UiCoordinator.java#L187-L214)) | — |
| Ingreso | `lavadero/view/PantallaIngresoLavadero` | `LavaderoController` | Cliente, Lavadero |
| Clasificación | `lavadero/view/PantallaClasificacionLavadero` | `ClasificacionController` | Lavadero, ClasificacionLavadero |
| Ciclos | `lavadero/view/PantallaCiclos` | `CiclosController` | CicloLavadero, Lavarropas, CatalogoJabones |
| Salidas | `lavadero/view/PantallaSalidasLavadero` | `SalidasLavaderoController` | SalidaLavadero |
| Ver ciclos | `lavadero/view/PantallaVerCiclos` | `VerCiclosController` | (solo lee del refrescador) |

**Salidas es la única pantalla de Lavadero cableada al grupo `operativo`** y no a
`refrescarEquipos` ([UiCoordinator:206-209](../src/main/java/com/example/app/ui/UiCoordinator.java#L206-L209)):
derivar al CDE crea ingresos en la cola operativa, y las tres pantallas del CDE tienen que verlos
aparecer sin recargar.

### Otras

| Pantalla | Vista | Controller |
|---|---|---|
| Ajustes / clientes / actualizaciones | `ajustes/view/PantallaAjustes` | `AjustesController` |
| Menú, Esterilización, EsOrtopedia | `ui/shell/Pantalla*` | — (solo `navegador.show(...)`) |

**Pantalla muerta:** `PantallaVerCDEv1` está registrada en el CardLayout pero
`Constantes.Pantallas.VER_CDE` no se referencia desde ningún botón. Es código
inalcanzable — candidata a borrar.

---

## 4. Recetas

### "Un botón hace algo mal"
1. Buscá la pantalla en la tabla de arriba → tenés vista y controller.
2. El listener del botón está en el **controller**, no en la vista. Las
   `Pantalla*` solo construyen widgets y los exponen con getters.
3. Si el dato que se guarda está mal → seguí al service. Si el dato que se
   *muestra* está mal → mirá el `Lector*` del grupo de refresco correspondiente
   (`app/ui/Lector*.java`).

### "Quiero agregar una pantalla nueva"
1. `Constantes.Pantallas.MI_PANTALLA` — el nombre para el CardLayout.
2. `features/<feature>/view/PantallaMiCosa.java` — solo widgets + getters, sin lógica.
3. `PantallaPrincipal` — instanciarla y `contenedor.add(...)`.
4. `features/<feature>/controller/MiCosaController.java` — recibe la vista y **solo
   los services que necesita** por constructor.
5. `UiCoordinator.inicializar()` — instanciarlo pasándole `context.getXService()`,
   y decidir a qué grupo de refresco pertenece.

### "Quiero agregar un campo a una entidad"
El orden es siempre de abajo hacia arriba:
`schema.sql` (o migración Flyway) → `model/` → `dao/` (SELECT, INSERT, UPDATE y el
mapeo de `ResultSet`) → `service/` (validación) → `view/` (widget) → `controller/` (leerlo y pasarlo).

### "Quiero testear algo que está adentro de una clase de Swing"
Patrón establecido del repo: extraé la lógica a una clase plana sin Swing y testeala
sola. Ejemplos que ya existen: `AgrupadorIngresosLote`, `DuplicadoHighlighter`,
`SincronizadorVolumenFinal`, `ReconciliadorPendientes`.

---

## 5. Piezas transversales (dónde está lo compartido)

| Necesito… | Está en |
|---|---|
| Constantes, nombres de pantalla, textos | `common/constants/Constantes.java` |
| Excepciones del dominio | `common/exception/` |
| CRUD genérico | `common/dao/DAO.java`, `SimpleEntityDAO.java` |
| Validaciones sueltas | `common/util/Validador.java` |
| Estilos, colores, fuentes | `ui/common/Estilos.java` |
| Tablas con formato | `ui/common/TableStyler.java`, `RowTooltipTable.java` |
| Autocompletado de campos | `ui/common/AutocompleteListener.java` |
| Ejecutar algo fuera del EDT | `ui/common/TareaUI.java` |
| Header con botón "volver" | `ui/common/PanelHeader.java` |
| Pool de conexiones / arranque BD | `infrastructure/db/ConnectionPool.java` |
| Transacciones | `infrastructure/db/TransactionalConnection.java` |
| Guard anti-query-en-el-EDT | `infrastructure/db/EdtGuard.java` |

---

## 6. Dónde está la masa (y dónde duele)

Reparto de las ~31.100 líneas (remedido el 2026-08-27, después de Salidas y del refactor de EDT):

| Capa | LOC | % |
|---|---|---|
| Vistas y UI compartida | 10.800 | 35% |
| DAOs | 5.960 | 19% |
| Controllers (+ helpers) | 5.870 | 19% |
| Services | 3.280 | 11% |
| Modelos | 2.340 | 8% |

Los archivos más grandes, que concentran la complejidad y son los primeros
candidatos a partir:

| Archivo | LOC |
|---|---|
| `lotes/dao/LoteDAO.java` | 948 |
| `equipos/otros/dao/EquipoOtrosDAO.java` | 886 |
| `lavadero/controller/CiclosController.java` | 727 |
| `lavadero/dao/SalidaLavaderoDAO.java` | 660 |
| `lotes/controller/LotesController.java` | 652 |
| `equipos/ortopedias/view/PantallaCorrecciones.java` | 626 |
| `lotes/view/helpers/PanelLotesContenido.java` | 488 |
| `common/constants/Constantes.java` | 487 |
| `equipos/ortopedias/dao/MaterialDAO.java` | 466 |
| `equipos/ortopedias/dao/EquipoDAO.java` | 455 |

---

## 7. Cosas que confunden y por qué

Anotadas para que no pierdas tiempo dos veces con lo mismo:

1. **`features/equipos/` tiene cinco raíces** (`common/`, `controller/`, `view/`,
   `ortopedias/`, `otros/`). Es la única feature con esa forma: `VerEquiposController`
   y `PantallaVerEquipos` viven en `equipos/` a secas porque sirven a los dos tipos,
   mientras el resto está bajo `ortopedias/` u `otros/`.
2. **Las vistas de los controllers compartidos siguen bajo `ortopedias/`.**
   `RegistrarEstadoController` y `EquiposParaEntregarController` ya viven en
   `equipos/common/controller/` porque manejan los dos tipos de equipo
   polimórficamente vía `EquipoRegistrableInterface`, pero sus vistas
   (`PantallaRegistrarEstado`, `PantallaEquiposParaEntregar`) todavía están en
   `ortopedias/view/`. Ahí la carpeta sigue mintiendo — pendiente de mover.
3. **`OtrosInputController` es el último nombre en inglés** de la capa de
   controllers. El resto ya está en español.
4. **Las `Pantalla*` no tienen lógica.** Si buscás qué hace un botón y solo ves
   `getBtnX()`, estás en el archivo correcto pero en la capa equivocada: subí al controller.
5. **Lavadero y CDE se tocan en un solo punto: `DerivadorIngresoCDE`.** Es el **único** lugar de
   la app donde una feature escribe en las tablas de otra: crea un `equipo_otros` con
   `requiereLavado = false` usando `EquipoOtrosDAO.guardar(Connection, ...)` **dentro de la
   transacción de la derivación**, así que si la creación del ingreso falla, `salidas_lavadero`
   queda intacta. Vive en `lavadero/dao/derivadores/`, no en `controller/helpers/`: su único
   consumidor es un DAO, y ponerlo arriba dejaba un import `dao → controller`, al revés del flujo.
   La misma clase sirve para las dos acciones — derivar al cliente original o a nombre de APTIUM —
   cambiando sólo el `AsignadorClienteCDE` que recibe.
6. **`AccionSalida` no es `DestinoSalida`.** La primera es lo que el operador **elige**; la segunda
   es lo que se **persiste** en `salidas_lavadero.destino`, y no son 1:1: `CDE_CLIENTE` y
   `CDE_APTIUM` son dos acciones con el mismo destino `CDE_OTROS`. Lo que las diferencia queda en
   el `nro_cliente` del ingreso creado, no en la salida. La asimetría es deliberada. Además,
   `destino` es **nullable**: `NULL` significa "marcada como Listo, sin destino todavía", que es un
   estado legítimo — por eso `DestinoSalida.desdeBD(null)` devuelve `null` en vez de un default.
