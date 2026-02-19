# Suite de Tests - Administración Aptium

## Descripción General

Esta suite de tests proporciona cobertura completa de las principales funcionalidades de la aplicación de gestión de equipos médicos. Los tests están organizados en categorías siguiendo estrategias de caja blanca y caja negra.

## Estructura de Tests

### 1. Tests de Modelo (Caja Blanca)
Validan la lógica de negocio implementada en las entidades del dominio.

- **EquipoTest**: Prueba la lógica de equipos, estados, y gestión de materiales
- **MaterialTest**: Valida propiedades y comportamiento de materiales

### 2. Tests de Servicios (Caja Blanca)
Prueban la lógica de negocio de la capa de servicios usando Mockito para aislar dependencias.

- **LoteServiceTest**: Creación, finalización, y gestión de lotes
- **MaterialServiceTest**: Avance de estados, actualización de cantidades
- **EquipoServiceTest**: Ingreso de equipos, gestión de estados
- **AutoclaveServiceTest**: Disponibilidad y asignación de autoclaves
- **InstitucionServiceTest**: Entregas de materiales a instituciones

### 3. Tests de TableModels (Caja Negra)
Validan la correcta visualización de datos en las tablas de la interfaz.

- **AutoclaveTableModelTest**: Visualización de estado (Libre/Ocupado), nombre, capacidad
- **MaterialTableModelTest**: Visualización de código, descripción, cantidad, estado
- **LoteTableModelTest**: Visualización de número de lote, fecha, autoclave

### 4. Tests de Utilidades (Caja Negra)
Prueban funciones de validación y formato sin conocer implementación interna.

- **ValidadorTest**: Validación de textos, números positivos, códigos
- **GestorValidacionFormularioTest**: Validación de campos Swing (JTextField, JComboBox)

### 5. Tests de Integración
Prueban el flujo completo con base de datos H2 en memoria.

- **GestionLotesIntegrationTest**: Flujo completo desde creación de equipo hasta finalización de lote

## Cobertura de Funcionalidades

✅ **Ingreso de equipos**: EquipoServiceTest, EquipoTest  
✅ **Avance de estado**: MaterialServiceTest, EquipoTest  
✅ **Creación de lotes**: LoteServiceTest, GestionLotesIntegrationTest  
✅ **Finalización de lotes**: LoteServiceTest, GestionLotesIntegrationTest  
✅ **Entregas a instituciones**: InstitucionServiceTest  
✅ **Visualización de datos en tablas**: AutoclaveTableModelTest, MaterialTableModelTest, LoteTableModelTest  
✅ **Validación de formularios**: ValidadorTest, GestorValidacionFormularioTest  

## Ejecución de Tests

### Ejecutar todos los tests
```bash
mvn test
```

### Ejecutar la suite completa
```bash
mvn test -Dtest=AllTests
```

### Ejecutar un test específico
```bash
mvn test -Dtest=LoteServiceTest
mvn test -Dtest=GestionLotesIntegrationTest
```

### Ejecutar tests de una categoría específica
```bash
# Solo tests de servicios
mvn test -Dtest=*ServiceTest

# Solo tests de modelos
mvn test -Dtest=*TableModelTest

# Solo tests de utilidades
mvn test -Dtest=*ValidadorTest,*GestorValidacionFormularioTest
```

### Generar reporte de cobertura (si tienes JaCoCo configurado)
```bash
mvn clean test jacoco:report
```

## Dependencias Requeridas

Los tests requieren las siguientes dependencias ya configuradas en `pom.xml`:

- **JUnit 4.11**: Framework de testing
- **Mockito 5.11.0**: Mocking framework para caja blanca
- **H2 Database 2.2.224**: Base de datos en memoria para tests de integración

## Configuración del Test de Integración

El test de integración `GestionLotesIntegrationTest` usa H2 en memoria y crea automáticamente:

1. Schema completo (clientes, instituciones, profesionales, equipos, materiales, etc.)
2. Datos de prueba (autoclave, catálogo de materiales)
3. Conexión con HikariCP

**Nota**: El test crea y destruye la base de datos en cada ejecución para garantizar aislamiento.

## Interpretación de Resultados

### Resultado Exitoso
```
Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
```

### Análisis de Fallos
Si algún test falla, revisa:

1. **Test de Servicio fallando**: Verifica lógica de negocio o cambios en interfaces
2. **Test de TableModel fallando**: Verifica cambios en columnas o getValueAt()
3. **Test de Integración fallando**: Verifica schema SQL o configuración de H2
4. **Test de Validación fallando**: Verifica reglas de validación o cambios en Validador

## Mejores Prácticas

### Al agregar nuevos tests:

1. **Sigue la convención de nombres**: `nombreMetodo_condicion_resultadoEsperado`
2. **Usa comentarios JavaDoc**: Describe qué prueba cada test
3. **Separa Arrange/Act/Assert**: Usa comentarios para marcar secciones
4. **Un assert por concepto**: Cada test debe probar una sola cosa
5. **Limpia recursos**: Usa @Before y @After cuando sea necesario

### Ejemplo de test bien estructurado:
```java
/**
 * Test: Crear lote con materiales válidos debe retornar ID generado.
 */
@Test
public void crearLote_MaterialesValidos_RetornaId() {
    // Arrange
    Lote lote = new Lote();
    lote.setAutoclaveId(1);
    when(loteDAO.crear(any(Lote.class))).thenReturn(1);
    
    // Act
    int resultado = loteService.crearLote(lote);
    
    // Assert
    assertEquals("Debe retornar el ID del lote", 1, resultado);
    verify(loteDAO, times(1)).crear(lote);
}
```

## Mantenimiento

### Actualizar tests cuando:

- ✏️ Se agreguen nuevas columnas a tablas
- ✏️ Cambien las reglas de validación
- ✏️ Se modifiquen los flujos de estados
- ✏️ Se agreguen nuevas entidades o servicios

### Agregar nuevos tests para:

- ➕ Nuevas funcionalidades
- ➕ Bugs reportados (test de regresión)
- ➕ Casos extremos (edge cases)

## Estado Actual de Cobertura

| Componente | Cobertura | Tests |
|-----------|-----------|-------|
| Modelo | ✅ Alta | EquipoTest, MaterialTest |
| Servicios | ✅ Alta | 4 test classes |
| TableModels | ✅ Completa | 3 test classes |
| Utilidades | ✅ Alta | ValidadorTest, GestorValidacionFormularioTest |
| Integración | ✅ Flow completo | GestionLotesIntegrationTest |

## Contacto y Soporte

Para dudas sobre los tests o para reportar problemas:
- Revisa la documentación de cada test (comentarios JavaDoc)
- Ejecuta tests individuales para aislar problemas
- Verifica que todas las dependencias estén actualizadas

---

**Última actualización**: 2025  
**Total de tests**: 50+  
**Estrategia**: Caja Blanca (servicios) + Caja Negra (utilidades, UI)
