# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Instrucciones del usuario

Explicar todos los cambios minimizando el consumo de tokens.
Al buscar debilidades arquitectónicas: ser crítico y explicar *por qué* son problemas. Si el código no tiene debilidades graves, decirlo — es una respuesta válida.
Al añadir nuevas funcionalidades, priorizar la preservación de la buena arquitectura y el código limpio y legible.
Ante cualquier duda de diseño o sobre cómo proceder con un cambio, preguntar.

## Build y ejecución

```bash
mvn clean package          # genera target/aptium.jar (fat JAR)
mvn test                   # tests unitarios
mvn verify                 # tests + reporte de cobertura JaCoCo
```

Configuración de BD: variables de entorno `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`, o archivo `config.properties` (ver `config.example.properties`).

## Arquitectura

Aplicación de escritorio Swing (Java 11) para gestión de equipos médicos y lotes de esterilización. Sin framework de DI — todo se cablea manualmente en el arranque.

**Flujo de arranque** (`App.main`):
1. `ConnectionPool` (HikariCP singleton, MySQL)
2. `DatabaseInitializer` (schema.sql + seeds)
3. `AppContext` — instancia todos los DAOs y Services
4. `AppModel` — fachada de negocio que expone los services a los controllers
5. `AppController` → `UiCoordinator` → `PantallaPrincipal` (CardLayout)

**Capas por feature** (en `features/`):
```
model → dao (DAO<T,ID>) → service → view/controller
```
Cada feature (equipos/ortopedias, equipos/otros, lotes, autoclaves, catalogo, clientes, instituciones, profesionales) sigue este mismo stack vertical.

**Clases clave:**
- `AppContext` — único lugar donde se construyen dependencias
- `AppModel` — único punto de acceso de la UI a la lógica de negocio
- `UiCoordinator` — cablea pantallas y listeners de eventos entre sí
- `Constantes` — todas las constantes de la app en un solo lugar
- `AptiumException` y subclases — jerarquía de excepciones del dominio

**Navegación UI:** `PantallaPrincipal` usa `CardLayout`; los nombres de los paneles están en `Constantes`.

**Patrones presentes:** Facade (`AppModel`), Strategy (`FilterStrategy`, `IMaterialFilter`, `ICapacidadCalculator`), DAO genérico, Observer (listeners `OnEquipo*`).
