# 📋 GUÍA DE DEPLOY - Administración Aptium

**Última actualización**: 24/02/2026

---

## 📑 Tabla de Contenidos

1. [ARQUITECTURA Y ESTRUCTURA](#1-arquitectura-y-estructura)
2. [REQUISITOS PREVIOS](#2-requisitos-previos)
3. [CONFIGURACIÓN DE BASE DE DATOS](#3-configuración-de-base-de-datos)
4. [INSTALACIÓN EN PRODUCCIÓN](#4-instalación-en-producción)
5. [VALIDACIÓN ANTES DE DEPLOY](#5-validación-antes-de-deploy)
6. [STARTUP Y SHUTDOWN](#6-startup-y-shutdown)
7. [MONITOREO Y TROUBLESHOOTING](#7-monitoreo-y-troubleshooting)
8. [CHECKLIST DE DEPLOY](#8-checklist-de-deploy)

---

## 1. ARQUITECTURA Y ESTRUCTURA

### 1.1 Componentes Principales

```
APLICACIÓN JAVA (JAR)
├── Connection Pool (HikariCP - OPTIMIZADO PARA PRODUCCIÓN)
│   ├── Variables de Entorno (Mayor prioridad)
│   ├── config.properties (Si existe)
│   └── Valores por defecto (Dev only)
│   │
│   ├── CONFIGURACIÓN DEL POOL:
│   │   ├── Max Conexiones: 10
│   │   ├── Min Idle: 5 (pre-conectadas)
│   │   ├── Connection Timeout: 30s
│   │   ├── Idle Timeout: 10min
│   │   ├── Max Lifetime: 30min
│   │   ├── Leak Detection: 60s (detecta conexiones no cerradas)
│   │   └── Test Query: SELECT 1 (valida conexión antes de usar)
│   │
│   └── CICLO DE VIDA:
│       ├── Startup: Crea 5 conexiones idle automáticamente
│       ├── Uso: Obtiene conexión del pool o espera (timeout 30s)
│       ├── Devolución: try-with-resources cierra automáticamente
│       └── Shutdown: ConnectionPool.shutdown() cierra todas
│
├── Features (Feature-based modular)
│   ├── equipos (DAO, Service, Model, View)
│   ├── lotes
│   ├── autoclaves
│   ├── catalogo
│   ├── clientes
│   ├── profesionales
│   └── instituciones
├── UI Layer (Swing)
│   ├── PantallaPrincipal (Main window)
│   ├── UiCoordinator (Wiring)
│   └── Componentes (Vistas específicas)
└── Persistencia (MySQL + HikariCP)
```

### 1.2 Archivos Generados

```
target/
└── aptium.jar   (JAR EJECUTABLE con dependencias)
```

### 1.3 Gestión de Conexiones y Pooling

La aplicación utiliza **HikariCP** (Connection Pool de última generación) para:

- ✅ **Reutilización de conexiones**: Evita overhead de crear/cerrar conexiones
- ✅ **Pool de 10 conexiones**: Máximo concurrente configurable
- ✅ **5 conexiones siempre activas**: Reduce latencia inicial
- ✅ **Leak detection**: Detecta conexiones no cerradas (60s threshold)
- ✅ **Validation queries**: Verifica que la conexión sea válida (SELECT 1)
- ✅ **Timeouts configurados**: 30s para obtener conexión, 10min idle

**Típicos mejores patrones de uso:**

```java
// ✅ CORRECTO: Try-with-resources (conexión se devuelve automáticamente)
try (Connection conn = ConnectionPool.getConnection()) {
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT ...");
    // ...
} // La conexión se cierra automáticamente y se devuelve al pool

// ❌ INCORRECTO: Sin try-with-resources (connection leak!)
Connection conn = ConnectionPool.getConnection();
Statement stmt = conn.createStatement();
// ... if algo falla, la conexión nunca se devuelve al pool
```

**Estadísticas del pool (visible en logs):**

```log
[INFO] Pool Stats: Total=10, Activas=3, Idle=7, Esperando=0
```

Estas métricas indican:
- `Total=10`: Pool con 10 conexiones totales
- `Activas=3`: 3 en uso en este momento
- `Idle=7`: 7 disponibles
- `Esperando=0`: 0 conexiones esperando (si > 0, necesitas más conexiones)

**Shutdown graceful:**

La aplicación registra un shutdown hook (`Runtime.getRuntime().addShutdownHook()`) que:
1. Corre cuando se presiona Ctrl+C o se cierran los servicios
2. Llama a `ConnectionPool.shutdown()`
3. Cierra todas las conexiones de forma ordenada
4. Libera los recursos del pool

---

## 2. REQUISITOS PREVIOS

### 2.1 En el Servidor de Producción

```
Java:       OpenJDK 17+ (requerido: el build compila a bytecode 17)
MySQL:      8.0+ (o MariaDB 10.5+)
Memoria:    Mínimo 1GB, recomendado 2GB
Disco:      50MB para aplicación + espacio para BD
Puertos:    3306 (MySQL), puerto interno Java disponible
```

### 2.2 Verificar Java y Maven Localmente

```powershell
# Verificar Java
java -version

# Verificar Maven
mvn --version

# Si Maven no está disponible, instalar desde https://maven.apache.org/download.cgi
```

---

## 3. CONFIGURACIÓN DE BASE DE DATOS

### 3.1 Estructura de Configuración

La aplicación usa este orden de precedencia:

```
1. VARIABLES DE ENTORNO (Sistema Operativo)
   ├─ DB_HOST      → Host/IP MySQL
   ├─ DB_PORT      → Puerto MySQL (default 3306)
   ├─ DB_NAME      → Nombre base de datos
   ├─ DB_USER      → Usuario MySQL
   └─ DB_PASS      → Contraseña MySQL
   
2. ARCHIVO config.properties
   ├─ C:\Aptium\config.properties       (Ubicación recomendada)
   └─ .\config.properties               (Directorio actual - DEV)
   
3. VALORES POR DEFECTO (Desarrollo ONLY)
   └─ localhost:3306, usuario: root, BD: sistema_empresa
```

### 3.2 Variables de Entorno (RECOMENDADO PARA PRODUCCIÓN - WINDOWS)

#### Opción A: Command Prompt

```batch
REM Establecer variables de entorno (requiere Admin)
setx DB_HOST "192.168.1.100"
setx DB_PORT "3306"
setx DB_NAME "sistema_empresa"
setx DB_USER "aptium_user"
setx DB_PASS "TuContraseñaFuerte123!"

REM Verificar (en nueva terminal):
echo %DB_HOST%
```

#### Opción B: PowerShell (recomendado)

```powershell
# Ejecutar como Administrator
[System.Environment]::SetEnvironmentVariable("DB_HOST", "192.168.1.100", "Machine")
[System.Environment]::SetEnvironmentVariable("DB_PORT", "3306", "Machine")
[System.Environment]::SetEnvironmentVariable("DB_NAME", "sistema_empresa", "Machine")
[System.Environment]::SetEnvironmentVariable("DB_USER", "aptium_user", "Machine")
[System.Environment]::SetEnvironmentVariable("DB_PASS", "TuContraseñaFuerte123!", "Machine")

# Verificar en nueva sesión PowerShell:
$env:DB_HOST
```

**Nota**: Después de establecer variables de entorno, iniciar una nueva terminal para que se apliquen.

### 3.3 Método Alternativo: Archivo config.properties (Windows)

Si prefieres usar archivo en lugar de variables de entorno:

```powershell
# Crear directorio
mkdir C:\Aptium -Force

# Crear archivo config.properties
@"
db.ip=192.168.1.100
db.port=3306
db.name=sistema_empresa
db.user=aptium_user
db.pass=TuContraseñaFuerte123!
"@ | Set-Content C:\Aptium\config.properties -Encoding UTF8

# Establecer permisos restrictivos (ejecutar como Admin)
icacls C:\Aptium\config.properties /inheritance:r /grant:r "$env:USERNAME`:F"
```

### 3.4 Preparar Base de Datos MySQL

La aplicación crea automáticamente la BD, pero debes:

1. **Crear usuario específico para la aplicación:**

```sql
-- En MySQL como root:
CREATE USER 'aptium_user'@'%' IDENTIFIED BY 'TuContraseñaFuerte123!';

-- Permisos mínimos necesarios
GRANT ALL PRIVILEGES ON sistema_empresa.* TO 'aptium_user'@'%';

-- Si MySQL está en servidor remoto, permitir conexión remota:
GRANT ALL PRIVILEGES ON sistema_empresa.* TO 'aptium_user'@'192.168.1.x' IDENTIFIED BY 'TuContraseñaFuerte123!';

FLUSH PRIVILEGES;
```

2. **Verificar conectividad desde servidor de aplicación Windows:**

```batch
REM Instalar MySQL Client tools primero (si no está disponible)
REM O usar MySQL Workbench

REM Desde Command Prompt:
mysql -h 192.168.1.100 -u aptium_user -p

REM Te pedirá contraseña, ingresala
REM Si funciona, verás "mysql>" en la terminal

REM Ejecutar query de prueba
SELECT VERSION();

REM Salir
exit
```

### 3.5 Logging y Monitoreo

La aplicación utiliza **Logback** (SLF4J) con rotación automática de logs.

#### Configuración de Logs Producción-Ready:

- ✅ **Rotación diaria + por tamaño**: Archivos máximo 20MB, se comprimen en .gz
- ✅ **Historial automático**: 30 días para logs generales, 90 para errores
- ✅ **Límite de espacio**: Máximo 1GB logs generales, 2GB errores
- ✅ **Separación de archivos**: 
  - `app.log` → INFO y superiores
  - `error.log` → Solo ERROR y FATAL with stack traces
- ✅ **Ubicación segura**: `C:\Logs\Aptium\` (automáticamente detectada)

#### Archivos de Log Generados:

```
C:\Logs\Aptium\
├── app.log                          (Log actual - INFO+)
├── error.log                        (Log actual - ERROR+)
├── app.2026-02-24.1.log.gz         (Histórico comprimido)
├── error.2026-02-24.1.log.gz       (Histórico comprimido)
└── ...más archivos con rotación
```

#### Ver Logs en Tiempo Real (PowerShell):

```powershell
# Ver últimas 50 líneas
Get-Content C:\Logs\Aptium\app.log -Tail 50

# Seguir logs en vivo
Get-Content C:\Logs\Aptium\app.log -Wait

# Buscar errores
Select-String "ERROR|EXCEPTION" C:\Logs\Aptium\error.log

# Buscar patrón en logs
Select-String "palabra" C:\Logs\Aptium\app.log
```

#### Cambiar Nivel de Log (DEV vs PROD):

```batch
REM Por defecto: INFO (producción)
java -jar app.jar

REM Con DEBUG (desarrollo)
java -Dlogback.level=DEBUG -jar app.jar

REM INFO (verbose, pero sin debug)
java -Dlogback.level=INFO -jar app.jar
```

#### Monitoreo de Logs Importantes:

La aplicación loguea eventos críticos:

```log
# STARTUP exitoso
[INFO] Connection Pool inicializado correctamente
[INFO] Pool: AptiumPool
[INFO] Max conexiones: 10
[INFO] Min idle: 5

# Error de conexión
[ERROR] No se pudo verificar/crear la base de datos

# Connection leak detectado
[WARN] Connection Pool estima leak: conexión no cerrada desde hace 60s

# Shutdown graceful
[INFO] Cerrando aplicación...
[INFO] Connection Pool cerrado correctamente
```

#### Alertas para Monitoreo (Windows):

```powershell
REM Buscar estos strings en logs para alertar:
- "ERROR" o "EXCEPTION" → Problema crítico
- "Connection refused" → BD inaccesible
- "OutOfMemoryError" → Falta memoria
- "Leak detection" → Conexiones no se cierran
- "DatabaseInitializationException" → Problema con esquema BD

# Buscar en PowerShell:
Select-String "ERROR|EXCEPTION|Connection refused" C:\Logs\Aptium\error.log
```

---

## 4. INSTALACIÓN EN PRODUCCIÓN

### 4.1 Compilar Localmente (Una sola vez)

```batch
REM En tu máquina local o servidor CI/CD
cd C:\Trabajo\Administracion-Aptium

REM Limpiar y compilar todo
mvn clean package

REM Resultado: target\aptium.jar
REM Este JAR contiene TODO (código + dependencias) - es autosuficiente
```

**IMPORTANTE**: El JAR `aptium.jar` es completamente independiente y no requiere Maven ni código fuente en producción.

### 4.2 Transferir JAR a Producción (Windows - Código Cerrado)

Solo necesitas copiar el JAR compilado. **No copies código fuente**.

#### Opción A: En servidor compartido en red

```batch
REM Desde tu máquina local:
xcopy target\aptium.jar \\servidor-produccion\compartir\aptium\
```

#### Opción B: Por email, USB, o herramienta de deploy

```batch
REM Simplemente copia el archivo:
target\aptium.jar
```

**QUÉ NO COPIAR a producción:**
- ❌ Carpeta `src/` (código fuente)
- ❌ `pom.xml` (config de compilación)
- ❌ `.git/` (repositorio)
- ❌ `target/` completo (solo necesitas el JAR)

**QUÉ COPIAR a producción:**
- ✅ `aptium.jar` (SOLO ESTO)

### 4.3 Crear Estructura de Directorios en Servidor Windows

```batch
REM Crear directorio principal
md C:\Aptium
md C:\Aptium\app
md C:\Logs\Aptium
md C:\Aptium\backup

REM Establecer permisos (ejecutar como Admin)
icacls C:\Aptium /grant "%USERNAME%":F /T

REM Copiar JAR compilado
copy C:\Ruta\donde\transferiste\aptium.jar C:\Aptium\app\aptium.jar

REM Verificar que el JAR está ahí
dir C:\Aptium\app\
REM Crear archivo ejecutar.bat
echo @echo off > C:\Aptium\app\ejecutar.bat
echo cd /d "C:\Aptium\app" >> C:\Aptium\app\ejecutar.bat
echo if not exist "aptium.jar" ( >> C:\Aptium\app\ejecutar.bat
echo     echo Error: No se encuentra C:\Aptium\app\aptium.jar >> C:\Aptium\app\ejecutar.bat
echo     echo Verifique que el JAR fue copiado correctamente al servidor. >> C:\Aptium\app\ejecutar.bat
echo     pause >> C:\Aptium\app\ejecutar.bat
echo     exit /b 1 >> C:\Aptium\app\ejecutar.bat
echo ) >> C:\Aptium\app\ejecutar.bat
echo java -jar "aptium.jar" >> C:\Aptium\app\ejecutar.bat
echo if errorlevel 1 ( >> C:\Aptium\app\ejecutar.bat
echo     echo. >> C:\Aptium\app\ejecutar.bat
echo     echo Error: Verifique que Java esté instalado correctamente o revise C:\Logs\Aptium\error.log >> C:\Aptium\app\ejecutar.bat
echo     pause >> C:\Aptium\app\ejecutar.bat
echo ) >> C:\Aptium\app\ejecutar.bat
```

### 4.4 Error Handling y Recuperación

La aplicación implementa **3 capas de manejo de errores**:

#### Capa 1: DataAccess (DAOs)
```java
// ❌ VIEJO (silencia errores)
try {
    return dao.obtenerTodos();
} catch (SQLException e) {
    log.error("Error", e);
    return List.of();  // Oculta el problema
}

// ✅ NUEVO (propaga errores correctamente)
try {
    return dao.obtenerTodos();
} catch (SQLException e) {
    log.error("No se pudo obtener datos", e);
    throw DataAccessException.queryFallida(sql, e);
}
```

#### Capa 2: Business (Services)
```java
// Services atrapan DataAccessException y convierten a BusinessException si es necesario
try {
    return dao.guardar(equipo);
} catch (DataAccessException e) {
    throw new BusinessException("INSERT_FAILED", "No se pudo guardar equipo", e.toString());
}
```

#### Capa 3: Presentation (Controllers y UI)
```java
// Controllers atrapan excepciones y notifican al usuario
try {
    service.guardarEquipo(equipo);
    JOptionPane.showMessageDialog(null, "Equipo guardado exitosamente", "Éxito", JOptionPane.INFORMATION_MESSAGE);
} catch (BusinessException e) {
    log.error("Error de negocio", e);
    JOptionPane.showMessageDialog(null, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
} catch (DataAccessException e) {
    log.error("Error de acceso a datos", e);
    JOptionPane.showMessageDialog(null, "Error en base de datos", "Error", JOptionPane.ERROR_MESSAGE);
}
```

#### Excepciones Personalizadas Disponibles:

```
AptiumException (base)
├── DataAccessException (problemas de BD)
│   ├── DB_CONNECTION_FAILED: No se conecta a BD
│   ├── DB_QUERY_FAILED: Query falló
│   ├── DB_INSERT_FAILED: Insert falló
│   └── DB_NOT_FOUND: Registro no encontrado
│
└── BusinessException (problemas de lógica)
    ├── VALIDATION_FAILED: Validación falló
    ├── BUSINESS_RULE_VIOLATED: Regla violada
    └── ESTADO_INVALIDO: Cambio de estado inválido
```

#### Estrategia de Errores en Producción:

**NUNCA hacer esto:**
```java
try {
    // código
} catch (Exception e) {
    // Silenciar sin logguear
}
```

**SIEMPRE propagar ALGUNO de estos:**
```java
// Opción 1: Loguear y relanzar
try { ... }
catch (Exception e) {
    log.error("Descripción del error", e);
    throw new DataAccessException("OPERATION_FAILED", "Descripción para usuario", "Contexto", e);
}

// Opción 2: Loguear y retornar error conocido
try { ... }
catch (Exception e) {
    log.error("Descripción del error", e);
    return false;  // Solo si el método está diseñado para esto
}

// Opción 3: Loguear y continuar (solo si es seguro)
try { ... }
catch (Exception e) {
    log.warn("Problema minor, continuando...", e);
    // seguir
}
```

---

## 5. VALIDACIÓN ANTES DE DEPLOY

### 5.1 Tests Unitarios y de Integración

```batch
REM En máquina local (antes de deploy)
mvn clean test

REM Resultado esperado:
REM Tests run: 252, Failures: 0, Errors: 0, Skipped: 0
```

### 5.2 Test de Compilación con Coverage

```batch
REM Verificar cobertura de código
mvn clean verify jacoco:report

REM Reportes en: target\site\jacoco\index.html
```

### 5.3 Verificar JAR Generado

```batch
REM Verificar que el JAR contiene todo
jar tf target\Administracion-Aptium-1.0-SNAPSHOT-jar-with-dependencies.jar | findstr "com\\example\\app" | more

REM Buscar clases principales
jar tf target\Administracion-Aptium-1.0-SNAPSHOT-jar-with-dependencies.jar | findstr "HikariCP"
```

### 5.4 Test en Servidor de Staging (IMPORTANTE!)

```batch
REM 1. Establecer variables de entorno STAGING (Windows)
setx DB_HOST "server-staging"
setx DB_NAME "sistema_empresa_staging"
REM ... etc (abrir nueva terminal para que se apliquen)

REM 2. Ejecutar JAR en modo test
java -jar C:\Aptium\app.jar

REM 3. Verificar logs en PowerShell
Get-Content C:\Logs\Aptium\app.log -Wait

REM 4. Buscar errores críticos
Select-String "ERROR|EXCEPTION" C:\Logs\Aptium\error.log
```

### 4.5 Inicialización Robusta de la Aplicación

La clase `App.java` implementa **startup secuencial con error handling**:

```
PASO 1: Registrar shutdown hook
  └─ Cierra Connection Pool gracefully al terminar

PASO 2: Conectar a Base de Datos
  └─ ConnectionPool se inicializa automáticamente
  └─ Lee variables de entorno/config.properties
  └─ Valida credenciales

PASO 3: Inicializar Esquema BD
  └─ Crea tablas si no existen
  └─ Carga datos iniciales

PASO 4: Crear Contexto de Dependencias
  └─ Instancia todos los DAOs
  └─ Instancia todos los Services
  └─ Inyecta dependencias

PASO 5: Crear AppModel
  └─ Encapsula toda la lógica de negocio

PASO 6: Crear AppController
  └─ Wirea controllers a vistas

PASO 7: Iniciar UI
  └─ Muestra ventana principal
```

#### Salida esperada en Logs:

```log
╔════════════════════════════════════════════════════════════╗
║  Iniciando Administración de Aptium                        ║
║  Versión 1.0 - PRODUCTION READY                           ║
╚════════════════════════════════════════════════════════════╝
[INFO] PASO 1/4: Conectando a base de datos...
[INFO] ✓ Connection Pool inicializado
[INFO] Pool Stats: Total=10, Activas=0, Idle=5, Esperando=0
[INFO] PASO 2/4: Inicializando esquema de base de datos...
[INFO] ✓ Esquema BD verificado/creado
[INFO] PASO 3/4: Creando contexto de dependencias...
[INFO] ✓ Contexto creado con DAOs y Services
[INFO] ✓ AppModel creado
[INFO] PASO 4/4: Iniciando interfaz de usuario...
[INFO] ✓ AppController creado
[INFO] ═══════════════════════════════════════════════════════════
[INFO] Aplicación inicializada correctamente
[INFO] ═══════════════════════════════════════════════════════════
```

#### Si algo falla en PASO 1-6:

```log
╔════════════════════════════════════════════════════════════╗
║  ✗ ERROR DE STARTUP                                       ║
╚════════════════════════════════════════════════════════════╝
[ERROR] ✗ Error inicializando esquema BD
[ERROR] Error en Base de Datos: Not able to get a connection, pool error Timeout waiting for idle object
```

**El usuario ve diálogo de error y la aplicación termina con exit code 1.**

---

## 6. STARTUP Y SHUTDOWN

### 6.1 Ejecutar la Aplicación en Windows

#### Opción A: Ejecución Simple (Terminal Interactiva)

```batch
REM Navegar al directorio
cd C:\Aptium\app

REM Ejecutar JAR
java -jar aptium.jar

REM O especificar nivel de log
java -Dlogback.level=INFO -jar aptium.jar
```

#### Opción B: Ejecución en Background (PowerShell)

```powershell
REM Ejecutar en background
Start-Process -NoNewWindow -FilePath "java" -ArgumentList "-jar C:\Aptium\app\aptium.jar" -RedirectStandardOutput "C:\Logs\Aptium\output.log"

REM Ver procesos java
Get-Process java
```

#### Opción C: Ejecutar como Servicio de Windows (NSSM - RECOMENDADO)
> **Nota:** El servicio Windows (NSSM) debe instalarse únicamente en la **PC host** (servidor central), donde se ejecuta la aplicación y acceden los usuarios. Esta PC debe estar encendida y funcionando todo el tiempo para que la aplicación esté disponible. Si la PC host se apaga o reinicia, la aplicación y el servicio dejan de estar accesibles para los clientes.

**Paso 1**: Descargar NSSM desde [https://nssm.cc](https://nssm.cc/)

```batch
REM Extraer nssm.exe en C:\nssm\
REM O agregarlo al PATH

REM Instalar servicio
C:\nssm\nssm.exe install AptiumService "java" "-jar C:\Aptium\app\aptium.jar"

REM Establecer directorio de trabajo
C:\nssm\nssm.exe set AptiumService AppDirectory C:\Aptium\app

REM Establecer variables de entorno del servicio
C:\nssm\nssm.exe set AptiumService AppEnvironmentExtra DB_HOST=192.168.1.100
C:\nssm\nssm.exe set AptiumService AppEnvironmentExtra DB_PORT=3306
C:\nssm\nssm.exe set AptiumService AppEnvironmentExtra DB_NAME=sistema_empresa
C:\nssm\nssm.exe set AptiumService AppEnvironmentExtra DB_USER=aptium_user
C:\nssm\nssm.exe set AptiumService AppEnvironmentExtra DB_PASS=TuPassword

REM Redireccionar salida a logs
C:\nssm\nssm.exe set AptiumService AppStdout C:\Logs\Aptium\app.log
C:\nssm\nssm.exe set AptiumService AppStderr C:\Logs\Aptium\error.log

REM Configurar reinicio automático
C:\nssm\nssm.exe set AptiumService RestartOnReboot SERVICE_AUTO_START
C:\nssm\nssm.exe set AptiumService AppRestartDelay 5000

REM Iniciar servicio
net start AptiumService

REM Ver estado
sc query AptiumService

REM Parar servicio
net stop AptiumService

REM Desinstalar servicio
C:\nssm\nssm.exe remove AptiumService confirm
```

### 6.2 Graceful Shutdown

La aplicación se cierra correctamente recibiendo señal de terminación:

```batch
REM Si la aplicación corre en terminal: Presiona Ctrl+C

REM Si está como servicio Windows:
net stop AptiumService

REM O via NSSM:
C:\nssm\nssm.exe stop AptiumService

REM O taskkill:
taskkill /IM java.exe /F

REM Ver logs de shutdown en PowerShell:
Get-Content C:\Logs\Aptium\app.log | Select-String "Cerrando|shutdown|closed"
```

---

## 7. MONITOREO Y TROUBLESHOOTING

### 7.1 Procesos en Ejecución

```powershell
# Ver procesos Java en PowerShell
Get-Process java

# Ver puerto 3306 en uso (MySQL)
Get-NetTCPConnection -LocalPort 3306 -ErrorAction SilentlyContinue

# O en Command Prompt:
tasklist | findstr java.exe
netstat -ano | findstr :3306
```

### 7.2 Ver Logs (PowerShell)

```powershell
# Últimas 50 líneas
Get-Content C:\Logs\Aptium\app.log -Tail 50

# Seguir logs en tiempo real
Get-Content C:\Logs\Aptium\app.log -Wait

# Buscar errores
Select-String "ERROR|EXCEPTION" C:\Logs\Aptium\error.log

# Contar errores
@(Select-String "ERROR" C:\Logs\Aptium\error.log).Count
```

### 7.3 Problemas Comunes

#### ❌ "Connection refused" o "Connection timeout"

**Causa**: No puede conectar a MySQL

**Solución**:

```batch
REM 1. Verificar que MySQL está corriendo
wmic service get name,state | find /i "mysql"

REM 2. Verificar variables de entorno
echo %DB_HOST%
echo %DB_PORT%

REM 3. Ver logs de error en PowerShell
Select-String "Connection refused|timeout|database" C:\Logs\Aptium\error.log
```

#### ❌ "OutOfMemoryError"

**Causa**: Aplicación corre sin memoria suficiente

**Solución**: Aumentar heap size en el comando de startup

```batch
REM En lugar de:
java -jar aptium.jar

REM Usar (hasta 1GB de memoria):
java -Xmx1024m -Xms512m -jar aptium.jar

REM -Xmx1024m: Máximo 1GB
REM -Xms512m: Mínimo 512MB
```

#### ❌ "Access Denied: User 'aptium_user'"

**Causa**: Credenciales incorrectas o host no permitido

**Solución**:

```sql
-- En MySQL (conectarse como administrador):
SHOW GRANTS FOR 'aptium_user'@'%';

-- Si es necesario crear/re-crear:
CREATE USER 'aptium_user'@'%' IDENTIFIED BY 'TuContraseñaFuerte123!';
GRANT ALL PRIVILEGES ON sistema_empresa.* TO 'aptium_user'@'%';
FLUSH PRIVILEGES;
```

#### ❌ Logs no se generan

**Causa**: Directorio de logs no existe o sin permisos

**Solución**:

```batch
REM Crear directorio
md C:\Logs\Aptium

REM Establecer permisos (ejecutar como Admin)
icacls C:\Logs\Aptium /grant "%USERNAME%":F /T

REM Verificar
dir C:\Logs\Aptium
```

### 7.4 Estadísticas del Pool de Conexiones

La aplicación loguea estadísticas del pool:

```log
[2026-02-24 18:43:08] Connection Pool inicializado correctamente
[2026-02-24 18:43:08] Pool: AptiumPool
[2026-02-24 18:43:08] Max conexiones: 10
[2026-02-24 18:43:08] Min idle: 5
```

Buscar en PowerShell:

```powershell
Get-Content C:\Logs\Aptium\app.log -Wait | Select-String "Pool Stats"
```

-- Si no existe, crearla:
CREATE USER 'aptium_user'@'%' IDENTIFIED BY 'TuContraseñaFuerte123!';
GRANT ALL PRIVILEGES ON sistema_empresa.* TO 'aptium_user'@'%';
FLUSH PRIVILEGES;
```

---

## 8. CHECKLIST DE DEPLOY

### Pre-Deploy (24 horas antes)

- [ ] Código compilado sin errores: `mvn clean package`
- [ ] Todos los tests pasan: `mvn clean test` (252 tests)
- [ ] JAR generado: `target\aptium.jar`
- [ ] Se actualizó la fecha en este documento
- [ ] Se creó backup de BD de producción
- [ ] Se probó en ambiente staging
- [ ] Se verificó que MySQL está accesible desde servidor de aplicación

### Deploy Day (Windows)

- [ ] Variables de entorno establecidas (DB_HOST, DB_USER, DB_PASS, etc.)
- [ ] config.properties existe (si se usa método archivo) con permisos restrictivos
- [ ] Directorio C:\Aptium creado con permisos adecuados
- [ ] JAR copiado a C:\Aptium\app\
- [ ] Permisos correctos en archivos (ejecutar como Admin):
  - [ ] `icacls C:\Aptium\app\aptium.jar /grant "%USERNAME%":F`
- [ ] Se probó startup manual: `java -jar C:\Aptium\app\aptium.jar`
- [ ] Se verificó que se conecta a BD (ver logs)
- [ ] Se creó servicio Windows usando NSSM
- [ ] Se probó startup/stop del servicio varias veces
- [ ] Se verifican los logs después de startup
- [ ] Se confirma que MySQL está escribiendo datos

### Post-Deploy (primeras 24 horas)

- [ ] Aplicación está corriendo: `tasklist | findstr java.exe`
- [ ] Logs muestran startup exitoso en C:\Logs\Aptium\app.log
- [ ] No hay "ERROR" o "EXCEPTION" en logs
- [ ] Connection Pool inicializó correctamente
- [ ] Se puede conectar a la aplicación UI
- [ ] Se prueba funcionalidad básica (crear equipo, etc.)
- [ ] Se monitora consumo de memoria (Task Manager)
- [ ] Se verifica que el pool no tiene memory leaks

### Rollback Plan (si algo sale mal)

```batch
REM 1. Detener servicio
net stop AptiumService

REM 2. Restaurar versión anterior
copy C:\Aptium\app\aptium.jar.backup C:\Aptium\app\aptium.jar

REM 3. Iniciar servicio
net start AptiumService

REM 4. Restaurar BD desde backup (si es necesario)
REM mysql -u root < backup_20260224.sql

REM 5. Notificar al equipo
```

---

## 9. SEGURIDAD EN PRODUCCIÓN

### 9.1 Manejo de Credenciales

**NUNCA hacer esto:**
```
✗ config.properties con credenciales reales versionado en Git
✗ Hardcoder contraseñas en el código
✗ Transmitir contraseñas en comandos visibles (tasklist)
✗ Permitir que cualquiera acceda a los archivos de config
```

**SIEMPRE hacer esto:**
```
✓ Variables de entorno: DB_HOST, DB_USER, DB_PASS establecidas en servidor
✓ Archivo config.properties con permisos restrictivos (NTFS: solo usuario)
✓ Usuario específico ejecutando la aplicación (NO SYSTEM)
✓ Credenciales de BD limitadas a tabla/BD específica (NO root globales)
```

### 9.2 Acceso a Base de Datos

**Usuario MySQL en Producción:**

```sql
CREATE USER 'aptium_prod'@'192.168.1.%' IDENTIFIED BY 'StrongPassword123!@#';
GRANT SELECT, INSERT, UPDATE, DELETE ON sistema_empresa.* TO 'aptium_prod'@'192.168.1.%';
FLUSH PRIVILEGES;
```

**QUÉ NO HACER:**
```sql
✗ GRANT ALL PRIVILEGES ON *.* TO 'aptium_user'@'%';  -- Puede modificar cualquier BD
✗ CREATE USER 'aptium'@'%' IDENTIFIED BY 'root';     -- Acceso desde cualquier host
```

### 9.3 Permisos de Archivos en Servidor Windows

Establecer permisos restrictivos en NTFS (ejecutar como Administrator):

```batch
REM Directorio principal
icacls C:\Aptium /grant:r "Administrators:F" "SYSTEM:F" /inheritance:r
icacls C:\Aptium /remove:d "Everyone" "Users" 2>nul

REM Logs
mkdir C:\Logs\Aptium
icacls C:\Logs\Aptium /grant:r "SYSTEM:F" "Administrators:F" /inheritance:r

REM Configuración (SECRETO - solo usuario ejecutante)
icacls C:\Aptium\config.properties /grant "%USERNAME%:M" /inheritance:r
icacls C:\Aptium\config.properties /remove:d "Everyone" "Users" 2>nul

REM JAR
icacls C:\Aptium\app\aptium.jar /grant "Administrators:F" /inheritance:r

REM Verificar
icacls C:\Aptium\config.properties
```

**Permisos esperados:**
```
C:\Aptium
  NT AUTHORITY\SYSTEM:(OI)(CI)(F)
  BUILTIN\Administrators:(OI)(CI)(F)
```

### 9.4 MySQL Remota con Certificados SSL/TLS

Si MySQL está en servidor remoto (recomendado en producción):

```batch
REM 1. Generaractificados (en servidor MySQL):
REM ... Usar herramientas de MySQL para generar certs

REM 2. Configurar en config.properties o env vars:
REM useSSL=true
REM serverSslMode=REQUIRED
REM (InnoDBCluster o MySQL Enterprise Edition soporte SSL nativo)

REM 3. En ConnectionPool.java, agregar:
REM datasource.setUseSSL(true);
REM datasource.setServerSslMode("REQUIRED");
```

**QUÉ NO HACER:**
```sql
✗ GRANT ALL PRIVILEGES ON *.* TO 'user'@'%';   -- Acceso global a todo
✗ Usuario sin SSL en red remota                -- Credenciales en texto plano
✗ Raíz (root) como usuario de la app           -- Acceso sin restricciones
```

### 9.5 Auditoría y Logs en Producción

**Logs de aplicación (Windows):**
- Guardar en `C:\Logs\Aptium\` con rotación automática (logback)
- Mantener 30 días de logs generales (app.log)
- Mantener 90 días de errores (error.log)
- Revisar regularmente por "ERROR" y "EXCEPTION"

**Ver logs en PowerShell:**
```powershell
# Errores en las últimas 24 horas
Get-Content C:\Logs\Aptium\error.log | Select-String "$(Get-Date -Format 'yyyy-MM-dd')"

# Contar errores
@(Select-String "ERROR|EXCEPTION" C:\Logs\Aptium\error.log).Count

# Exportar para análisis
Get-Content C:\Logs\Aptium\app.log | Out-File C:\Temp\logs_backup.txt
```

**Logs de acceso BD (MySQL):**
```sql
-- En MySQL (como root):
SET GLOBAL general_log = 'ON';
SET GLOBAL log_output = 'TABLE';
SELECT * FROM mysql.general_log WHERE command_type = 'Query' LIMIT 100;

-- O desactivar después de auditoría:
SET GLOBAL general_log = 'OFF';
```

### 9.6 Actualizaciones de Seguridad

**Cadencia mensual:**
- [ ] Revisar dependencias con vulnerabilidades conocidas
- [ ] Actualizar MySQL Server si hay patches de seguridad
- [ ] Actualizar OpenJDK si hay vulnerabilidades críticas
- [ ] Revisar logs de error.log por patrones sospechosos

```batch
REM Escanear vulnerabilidades en pom.xml (si usas Maven plugins):
mvn dependency-check:check

REM O manualmente revisar:
mvn clean dependency:tree | findstr "RELEASE\|SNAPSHOT"
```

---

## 📞 Soporte y Contacto en Producción

**Errores comunes y soluciones:**
- **Errores de compilación**: Revisar `target\maven-status\` o ejecutar `mvn clean -e compile`
- **Errores de BD**: Ver `C:\Logs\Aptium\error.log` y `C:\Logs\Aptium\app.log`
- **Problemas de Performance**: Revisar logs para estadísticas del pool de conexiones
- **Startup lento**: Aumentar memoria: `java -Xmx2g -jar aptium.jar`
- **Memory leaks**: Ver advertencias "Leak detection" en error.log

**Pasos de diagnóstico rápido:**
1. Verificar si MySQL está corriendo: `wmic service get name,state | find /i "mysql"`
2. Ver proceso Java: `tasklist | findstr java.exe`
3. Revisar logs recientes: `Get-Content C:\Logs\Aptium\app.log -Tail 100`
4. Buscar errores: `Select-String "ERROR" C:\Logs\Aptium\error.log`

---

## 📊 Especificaciones Técnicas

### Stack Tecnológico:
- **Lenguaje**: Java 17+
- **BD**: MySQL 8.0+ / MariaDB 10.5+
- **UI**: Swing (AWT)
- **Build**: Maven 3.6+
- **Pool Conexiones**: HikariCP 5.x
- **Logging**: Logback / SLF4J
- **Testing**: JUnit 4, Mockito

### Arquitectura:
- **Pattern**: Feature-based modular organization
- **Layers**: DAO → Service → Controller → View
- **DI**: Manual (sin Spring)
- **Transactions**: Auto-commit por DAO
- **Error Handling**: AptiumException, DataAccessException, BusinessException

### Performance y Límites:
- **Max usuarios concurrentes**: 10 (limit de pool conexiones)
- **Max queries simultáneas**: 10
- **Memoria recomendada**: 1-2GB
- **Disco para BD**: Depende de volumen de datos
- **RPM máximo**: ~1000 por minuto (HDD)

---

## 📅 Registros de Cambios

### Versión 1.0 (24/02/2026)
- ✅ Arquitectura feature-based implementada
- ✅ HikariCP Connection Pool optimizado
- ✅ Logback logging con rotación automática
- ✅ Error handling multilayer
- ✅ Startup robusta con JSON-style logging
- ✅ Variables de entorno para configuración
- ✅ Deploy guide completa

### Próximas mejoras (v1.1+):
- [ ] Migration a Spring Boot para DI
- [ ] Interceptors de transacción
- [ ] REST API (JSON)
- [ ] WebSocket para actualización en tiempo real
- [ ] Docker/K8s support
- [ ] Monitoring con Prometheus/Grafana

---

**Documento finalizado**: 24/02/2026  
**Versión**: 1.0 PRODUCTION READY  
**Estado**: ✅ Listo para deploy

Para reportar problemas o sugerencias, crear issue en repositorio.

