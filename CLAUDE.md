# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Instrucciones del usuario

Explicar todos los cambios minimizando el consumo de tokens.
Al buscar debilidades arquitectónicas: ser crítico y explicar *por qué* son problemas. Si el código no tiene debilidades graves, decirlo — es una respuesta válida.
Al añadir nuevas funcionalidades, priorizar la preservación de la buena arquitectura y el código limpio y legible.
Ante cualquier duda de diseño o sobre cómo proceder con un cambio, preguntar.

## Build y ejecución

```bash
mvn clean package                                        # genera target/aptium.jar (fat JAR)
mvn test                                                 # tests unitarios
mvn verify                                               # tests + reporte de cobertura JaCoCo
mvn test -Dtest=NombreDeClase                            # un solo test
mvn test -Dtest=NombreDeClase#nombreDelMetodo            # un método específico
```

Configuración de BD (precedencia descendente):
1. Variables de entorno: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`
2. Archivo `config.properties` (buscado en `/etc/aptium/`, `C:\Aptium\`, raíz del proyecto)
3. Defaults hardcodeados (solo desarrollo, emite warning en log)

Ver `config.example.properties` como referencia.

## Arquitectura

Aplicación de escritorio Swing (Java 17) para gestión de equipos médicos y lotes de esterilización. Sin framework de DI — todo se cablea manualmente en el arranque.

**Flujo de arranque** (`App.main`):
1. `ConnectionPool` — HikariCP singleton, crea la BD si no existe
2. `DatabaseInitializer` — ejecuta schema.sql + seeds
3. `AppContext.createDefault()` — instancia todos los DAOs, Services y Strategies
4. `AppModel` — fachada de negocio que expone los services a los controllers
5. `AppController` → `UiCoordinator` → `PantallaPrincipal` (CardLayout)

Si cualquier paso falla, aparece un diálogo de error y la app termina.

**Capas por feature** (en `features/`):
```
model → dao (DAO<T,ID>) → service → view/controller
```
Features: `equipos/ortopedias`, `equipos/otros`, `lotes`, `autoclaves`, `catalogo`, `clientes`, `instituciones`, `profesionales`.

**Clases clave:**
- `AppContext` — único lugar donde se construyen dependencias (new DAO, new Service, new Strategy)
- `AppModel` — único punto de acceso de la UI a la lógica de negocio; expone métodos semánticos, nunca servicios crudos (excepción documentada: `getEquipoCorreccionService()`)
- `UiCoordinator` — instancia todos los controllers, cablea listeners; crea un `Runnable` global de refresh que todos los controllers disparan al guardar datos
- `Constantes` — todas las constantes de la app (nombres de pantallas para CardLayout, anchos de columnas, etc.)
- `AptiumException` y subclases — jerarquía de excepciones del dominio

**Navegación UI:** `PantallaPrincipal` usa `CardLayout`; los nombres de los paneles están en `Constantes.Pantallas.*`.

## Ortopedias vs. Otros

Son dos tipos de equipo con modelos, tablas y flujos distintos pero comparten la misma máquina de estados. `RegistrarEstadoController` los maneja polimórficamente mediante `EquipoRegistrableInterface` (discrimina con `getTipo()`).

| | Ortopedias | Otros |
|---|---|---|
| Modelo | `Equipo` / `Material` | `EquipoOtros` / `MaterialOtros` |
| Tablas | `equipos`, `equipo_materiales` | `equipo_otros`, `equipo_otros_materiales` |
| Catálogo | `catalogo_descripciones` (códigos fijos) | `catalogo_otros` (crece con el uso) |
| Materiales | Identificados por código numérico | Texto libre → se auto-crea entrada en `catalogo_otros` |
| Tipo de ingreso | Único | DETALLES (por ítem) o REMITO (bulto con ID `ddmmaaaa-{id}`) |
| Datos extra | nroProfesional, pacienteNombre, nroInstitucion | Solo cliente |

## Máquina de estados (`EstadoEquipo`)

```
NUEVO → LAVANDO → LAVADO → EMPAQUETADO → ESTERILIZANDO → ESTERILIZADO → ENTREGADO
```

Los equipos pueden saltear LAVANDO y/o EMPAQUETADO según los flags `requiereLavado` / `requiereEmpaque`. La lógica de transición válida está en `IEstadoValidator` / `EstadoValidatorImpl`.

## Patrones e interfaces clave

**Strategies** (todas en `common/`):
- `IMaterialFilter` — filtra materiales por estado (qué necesita esterilizado, qué es entregable)
- `ICapacidadCalculator` — lógica de volúmenes de lotes
- `IEstadoValidator` — decide si un material puede avanzar de estado y cuál es el próximo
- `FilterStrategy<T,C>` — filtrado genérico de listas

**Validación con builder:**
```java
ValidationException.Builder builder = ValidationException.builder()
    .addErrorIf(condicion, "Mensaje de error");
builder.throwIfHasErrors();
```

**Transacciones:** `TransactionalConnection` (try-with-resources, commit/rollback manual). No hay framework de transacciones.

**Jerarquía de excepciones:** `AptiumException` → `BusinessException`, `DataAccessException`, `ValidationException` (con builder), `ResourceNotFoundException`, `DatabaseException`.

## Tests

JUnit 5 (Jupiter) + Mockito + H2 en memoria. Más de 500 tests en `src/test/java`,
reflejando la estructura de paquetes de `src/main/java` (un `*Test.java` por
DAO/Service/Controller/helper relevante).

Para lógica de negocio embebida en clases de Swing (diálogos, paneles), el
patrón del repo es extraerla a una clase plana sin dependencias de Swing y
testearla en aislamiento — ver `AgrupadorIngresosLote`, `DuplicadoHighlighter`
y `SincronizadorVolumenFinal` como ejemplos.
