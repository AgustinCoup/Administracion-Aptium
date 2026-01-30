# Database Scripts

Este directorio contiene los scripts SQL para inicializar y poblar la base de datos.

## Estructura

- **schema.sql**: Define la estructura de todas las tablas
- **seed_clientes.sql**: Datos iniciales de clientes
- **seed_instituciones.sql**: Datos iniciales de instituciones (prueba)
- **seed_profesionales.sql**: Datos iniciales de profesionales (prueba)
- **seed_catalogo.sql**: Catálogo de materiales

## Orden de Ejecución

Los scripts se ejecutan automáticamente en este orden al iniciar la aplicación:

1. `schema.sql` - Crea todas las tablas
2. `seed_clientes.sql` - Si la tabla `clientes` está vacía
3. `seed_instituciones.sql` - Si la tabla `instituciones` está vacía
4. `seed_profesionales.sql` - Si la tabla `profesionales` está vacía
5. `seed_catalogo.sql` - Si la tabla `catalogo_descripciones` está vacía

## Uso

### Desde la Aplicación

La clase `DatabaseInitializer` ejecuta automáticamente estos scripts:

```java
DatabaseInitializer.inicializar();
```

### Manualmente (opcional)

Puedes ejecutar estos scripts directamente en MySQL:

```bash
mysql -u usuario -p sistema_empresa < src/main/resources/db/schema.sql
mysql -u usuario -p sistema_empresa < src/main/resources/db/seed_clientes.sql
# etc...
```

## Modificar Datos

Para agregar nuevos clientes, instituciones o profesionales:

1. Edita el archivo SQL correspondiente
2. Elimina los registros existentes en la tabla (o todo el schema)
3. Reinicia la aplicación

## Ventajas de Este Enfoque

- ✅ Datos separados del código fuente
- ✅ Scripts SQL versionables en Git
- ✅ Reutilizables en otras herramientas (MySQL Workbench, CLI, etc.)
- ✅ Fácil mantenimiento y actualización
- ✅ Idempotente (se puede ejecutar múltiples veces sin errores)

## Futuras Mejoras

Considerar migración a herramientas de versionado de BD:
- **Flyway**: Migraciones versionadas
- **Liquibase**: Mayor flexibilidad y rollback
