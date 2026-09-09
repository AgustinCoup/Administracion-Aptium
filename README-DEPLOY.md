# Guía de deploy — Administración Aptium

**Última actualización**: 09/09/2026
**Válida para**: el salto desde `v1.1.6.1` (última versión en producción) al release que incluye Lavadero, bloqueo optimista y TLS obligatorio.

> Este documento son **instrucciones para ejecutar**, no documentación de arquitectura.
> Para cómo está construida la app, ver [CLAUDE.md](CLAUDE.md) y `plans/`.
> Para la conexión remota a MySQL por Tailscale, ver
> [docs/conexion-remota-mysql-tailscale.md](docs/conexion-remota-mysql-tailscale.md).

---

## 0. Cómo leer esto

Hay **dos documentos** y se usan en **dos máquinas distintas**. Nunca hacen falta los dos
completos en la misma PC.

### Si estás en la PC servidor (la que ya tiene la app y la base)

Leer **en este orden**, de arriba abajo, sin saltear:

| Orden | Dónde | Qué hacés |
|---|---|---|
| 1 | **§2** de este doc | Entender qué cambia. 5 minutos de lectura, sin tocar nada |
| 2 | **§3** de este doc | Pre-deploy: los 4 bloqueantes (TLS, grants, backup, disco) |
| 3 | **§1 completa** del [runbook de Tailscale](docs/conexion-remota-mysql-tailscale.md) | Convertir esta PC en servidor: bind-address, usuario MySQL, firewall, permanencia del nodo |
| 4 | **§4.1 a §4.5** de este doc (Tramo A) | Publicar el release, cerrar la app, actualizar el JAR → **acá se migra la base** |
| 5 | **§5** de este doc | Verificar antes de tocar ninguna otra PC |

Si algo falla en el paso 4 → **§9** (troubleshooting) y, si hace falta, **§6** (rollback).
No sigas con las PCs nuevas hasta que el paso 5 esté en verde.

> Esta PC **no** usa la §2 del runbook de Tailscale: sigue conectando por `localhost`.

### Si estás en una PC nueva

El Tramo A ya tiene que estar terminado y verificado. Después:

| Orden | Dónde | Qué hacés |
|---|---|---|
| 1 | **§3.8** de este doc | Requisitos previos: Java 17 y Tailscale instalados |
| 2 | **§2** del [runbook de Tailscale](docs/conexion-remota-mysql-tailscale.md) | Apuntar la PC al servidor (Opción A: `config.properties`) |
| 3 | **§4.6 a §4.8** de este doc (Tramo B) | Crear el config, copiar el JAR, `ejecutar.bat`, primer arranque |
| 4 | **§5.1** de este doc | Confirmar que el arranque quedó limpio |

Si no conecta → **§4** del runbook de Tailscale, y después **§9.3** de este doc.

> Una PC nueva **no** toca nada de la §1 del runbook (eso es del servidor) ni migra la base.

### Convención de rutas

Todo lo que aparece entre `<...>` **hay que reemplazarlo por el valor real de tu máquina**.
Las rutas que sí están fijas y no se eligen son sólo dos:

- `C:\Aptium\config.properties` — la busca el código, está hardcodeada
- `logs\` relativo al directorio del JAR — lo define `logback.xml`

Cualquier otra ruta de este documento (dónde vive el JAR, dónde van los backups, dónde
está `mysqldump.exe`) es una **convención sugerida**, no un dato observado de tu
instalación: verificala en la máquina antes de usarla.

---

## Índice

0. [Cómo leer esto](#0-cómo-leer-esto)
1. [Topología real](#1-topología-real)
2. [Qué cambia en este deploy](#2-qué-cambia-en-este-deploy)
3. [Pre-deploy — hacer HOY](#3-pre-deploy--hacer-hoy)
4. [Deploy day — paso a paso](#4-deploy-day--paso-a-paso)
5. [Verificación post-deploy](#5-verificación-post-deploy)
6. [Rollback](#6-rollback)
7. [Configuración de la app](#7-configuración-de-la-app)
8. [Logs](#8-logs)
9. [Troubleshooting](#9-troubleshooting)
10. [Seguridad](#10-seguridad)
11. [Actualizaciones futuras](#11-actualizaciones-futuras)
12. [Backups](#12-backups)

---

## 1. Topología real

**Es una aplicación de escritorio Swing, no un servidor.** Una copia del JAR corre en
**cada puesto**, y todas apuntan a **una sola base MySQL** compartida.

### 1.1 Hoy: una sola PC

```
PC única ──► MySQL en localhost
(app + base en la misma máquina, sin red de por medio)
```

### 1.2 Después de este deploy: esa PC pasa a ser el servidor

```
PC actual  ──► MySQL local (localhost)   ← sigue siendo un puesto de trabajo,
   │                ▲                       y además aloja la base
   │                │  Tailscale
Puesto nuevo 1 ─────┤
Puesto nuevo N ─────┘
```

La PC actual cumple **dos roles a la vez**: sigue siendo un puesto donde se trabaja, y
además es el servidor de base de datos de los demás. Eso implica:

- **Tiene que estar encendida** mientras haya alguien trabajando en cualquier otro puesto.
  Si se apaga o se suspende, los demás pierden la base en el acto.
- Conviene **desactivar la suspensión automática** en esa PC
  (Configuración → Sistema → Inicio/apagado → Suspensión: Nunca).
- Ella misma sigue conectando por `localhost`, no por Tailscale.

### 1.3 Consecuencias del modelo

Consecuencias prácticas, todas relevantes para el deploy:

- **No se instala como servicio de Windows.** Un servicio corre en la sesión 0, sin
  escritorio: la ventana de Swing no se vería. Cada puesto la abre con `ejecutar.bat`
  como cualquier programa. *(Si alguna vez se instaló un `AptiumService` con NSSM
  siguiendo una versión anterior de este documento, desinstalarlo:
  `nssm remove AptiumService confirm`.)*
- **El pool de 10 conexiones es por puesto**, no del sistema. Con 6 puestos son ~60
  conexiones contra MySQL (5 idle por puesto apenas abre). Verificar que
  `max_connections` del servidor las banque (default 151 → alcanza hasta ~15 puestos).
- **Todos los puestos comparten el esquema**, así que la versión del JAR tiene que ser
  la misma en todos. Ver §2.
- **Este deploy estrena el uso concurrente.** Hasta hoy hubo un solo operador, así que
  todas las guardas de concurrencia (V21, los `FOR UPDATE` del lavadero, los CAS por
  estado) nunca se ejercitaron contra dos personas reales. No es un riesgo de arranque
  —está cubierto por tests— pero sí lo primero a mirar si aparece algo raro en las
  primeras semanas: ver el smoke de dos puestos en §5.3.

---

## 2. Qué cambia en este deploy

Son **132 commits** desde `v1.1.6.1`. Tres cosas cambian el procedimiento respecto de
cualquier actualización anterior:

### 2.1 La base salta de V16 a V22 (13 migraciones)

Producción tiene aplicadas V1–V6, V13, V14, V16. Este build trae hasta **V22**, así que
al primer arranque Flyway aplica, en una sola pasada:

| Migraciones | Qué traen |
|---|---|
| V7–V12, V15, V17 | Todo el feature de **Lavadero** (ingresos, clasificación, ciclos, salidas) |
| V18 | Cliente APTIUM (derivación de ropa lavada al CDE) |
| V19, V20 | Fracciones de equipo repartidas entre lavarropas |
| **V21** | Columna `version` en `equipos` / `equipo_otros` — **bloqueo optimista** |
| V22 | Índice de ciclos de lavarropas abiertos |

V7–V12 y V15 se aplican **fuera de orden** (`outOfOrder(true)` en `DatabaseInitializer`),
que es exactamente el caso para el que esa opción está activada. No requiere nada especial.

**Las migraciones no tienen rollback.** El único camino de vuelta es restaurar un dump.
Por eso el backup del §3.4 no es opcional.

### 2.2 Un JAR viejo contra la base nueva escribe sin guardas

Desde este build, si la base está más adelantada que el JAR, la app **aborta el arranque**
(`EsquemaDesactualizadoException`) y ofrece actualizarse sola. Pero ese chequeo **recién
existe a partir de este build**: los puestos que sigan en `v1.1.6.1` después de que la base
migre a V22 **no se enteran de nada** y siguen escribiendo sin las guardas de concurrencia
que V21 introdujo.

> **Regla del día:** todos los puestos se actualizan en la misma jornada. No se deja
> ninguno "para mañana". A partir del próximo deploy, la app misma lo va a impedir.

### 2.3 TLS pasó a ser obligatorio

`ConnectionPool` arma las URLs JDBC con `sslMode=REQUIRED`
([ConnectionPool.java:55](src/main/java/com/example/infrastructure/db/ConnectionPool.java#L55)).
Si el MySQL de producción no ofrece TLS, la app **no conecta** — falla en el primer paso
del arranque, en todos los puestos a la vez. Nunca corrió así en producción, así que hay
que verificarlo **antes** (§3.2), no descubrirlo mañana.

---

## 3. Pre-deploy — hacer HOY

### 3.1 Compilar y verificar el build

```powershell
mvn clean package
```

Esperado: `BUILD SUCCESS`, **1138 tests, 0 failures, 0 errors**, y el artefacto en
`target\aptium.jar` (~24 MB). El plugin `antrun` deja además una copia en `app\aptium.jar`.

> El JAR generado localmente lleva `app.version=dev-SNAPSHOT`. **No es el que se instala**
> — el que va a producción es el que publica el release de GitHub (§4.1), que lleva la
> versión real embebida y su `.sha256`.

### 3.2 Verificar que MySQL ofrece TLS ← **bloqueante**

En la PC que aloja MySQL:

```sql
SHOW GLOBAL VARIABLES LIKE 'have_ssl';    -- tiene que decir YES
```

- `YES` → listo, no hay nada que hacer (MySQL 8 genera certificados autofirmados y
  habilita TLS al inicializar el datadir).
- `DISABLED` → hay que habilitar TLS **en el servidor** (`ssl_cert` / `ssl_key` en
  `my.ini` y reiniciar el servicio). **No** bajar el modo en el cliente.

Si querés confirmarlo de punta a punta antes de mañana, conectá un puesto de prueba y
mirá la línea `SSL:` de `STATUS;`.

### 3.3 Verificar los permisos del usuario MySQL ← **bloqueante**

La app hace `CREATE DATABASE IF NOT EXISTS` y **Flyway ejecuta DDL** (CREATE TABLE,
ALTER, índices). Un usuario con solo SELECT/INSERT/UPDATE/DELETE **rompe el arranque en
la migración**.

```sql
SHOW GRANTS FOR 'usuario_app'@'host';
```

Tiene que incluir, como mínimo:

```sql
GRANT ALL PRIVILEGES ON sistema_empresa.* TO 'usuario_app'@'<host_o_subred>';
FLUSH PRIVILEGES;
```

`ALL` acotado **a ese schema** (nunca `ON *.*`) es lo correcto acá: incluye el DDL que
Flyway necesita y el CREATE de la base, sin dar acceso a nada más.

### 3.4 Backup de la base ← **bloqueante**

Es el único rollback que existe para las migraciones. **Este dump es aparte de tus backups
automáticos** — es el punto de retorno exacto de este deploy, y conviene que esté en una
carpeta que no toque ninguna tarea programada.

```powershell
# Ubicar mysqldump (la ruta depende de la versión de MySQL instalada)
$dump = (Get-ChildItem "C:\Program Files\MySQL" -Filter mysqldump.exe -Recurse -ErrorAction SilentlyContinue |
         Select-Object -First 1).FullName
$dump    # confirmar que encontró algo

$destino = "<CARPETA_PARA_ESTE_DUMP>\sistema_empresa_pre_v1.2.sql"

& $dump -h localhost -u <USUARIO> -p `
  --single-transaction --routines --triggers `
  sistema_empresa > $destino
```

Verificar que no quedó vacío y que **termina completo** — un dump truncado por disco lleno
es un archivo que existe y no sirve:

```powershell
Get-Item $destino | Select-Object Length
Get-Content $destino -Tail 3      # tiene que aparecer "-- Dump completed"
```

### 3.5 Espacio en disco de la PC servidor ← **bloqueante**

Una migración que se queda sin disco a mitad de un `ALTER TABLE` es la peor forma de
fallar: deja el historial de Flyway con una fila en `success = 0` y hay que restaurar.

```powershell
Get-PSDrive C | Select-Object Used, Free
```

Regla práctica: dejar libre **al menos 3× el tamaño de la base** antes de migrar
(las migraciones que agregan columnas reescriben la tabla, y el dump del §3.4 ocupa lo suyo).

Si hay **tareas programadas de backup** en esa PC, revisarlas ahora: una copia diaria sin
política de retención llena el disco con el tiempo, y este es justo el día en que eso
importa. Ver §3.7.

### 3.6 Dónde vive el JAR hoy

```powershell
Get-ChildItem C:\ -Filter "aptium.jar" -Recurse -ErrorAction SilentlyContinue -Depth 4 |
  Select-Object FullName, LastWriteTime
```

La ruta histórica de instalación es `C:\Sistema\app\aptium.jar`, lanzado por un
`ejecutar.bat` que hace `cd /d` a esa carpeta. **No hace falta moverlo**: la app resuelve
su propia ruta en runtime (`RutaJarResolver`), así que el auto-update funciona esté donde
esté. Importa saber la ruta real para el backup del JAR y para encontrar los logs (§8).

Hacer copia del JAR actual antes de tocarlo:

```powershell
Copy-Item C:\Sistema\app\aptium.jar C:\Sistema\app\aptium.jar.v1.1.6.1
```

### 3.7 Tareas de backup existentes

Si ya hay tareas programadas de backup en la PC servidor, verificar que **siguen
corriendo** (son una red de seguridad extra para mañana) y que **no están llenando el
disco**:

```powershell
# Listar las tareas propias (no las de Windows)
Get-ScheduledTask | Where-Object { $_.TaskPath -notlike '\Microsoft\*' } |
  Select-Object TaskPath, TaskName, State

# Para cada una: qué ejecuta y cuándo
$t = Get-ScheduledTask -TaskName "<NOMBRE>"
$t.Actions   | Format-List Execute, Arguments, WorkingDirectory
$t.Triggers  | Format-List
Get-ScheduledTaskInfo -TaskName "<NOMBRE>" |
  Select-Object LastRunTime, LastTaskResult, NextRunTime   # LastTaskResult 0 = OK

# Peso de la carpeta de destino y antigüedad de los archivos
$dir = "<CARPETA_DE_BACKUPS>"
"{0:N2} GB" -f ((Get-ChildItem $dir -Recurse -File | Measure-Object Length -Sum).Sum / 1GB)
Get-ChildItem $dir -File | Sort-Object LastWriteTime |
  Select-Object -First 3 Name, LastWriteTime, @{n='MB';e={[math]::Round($_.Length/1MB,1)}}
Get-ChildItem $dir -File | Measure-Object | Select-Object Count
```

Si la carpeta creció sin límite, para **hoy** alcanza con liberar espacio a mano y que la
migración tenga aire. La solución de fondo (retención) es aparte del deploy — ver §12.

### 3.8 Preparar las PCs nuevas (puede hacerse hoy)

En cada puesto nuevo, antes del día del deploy:

- [ ] **Java 17 o superior instalado** (`java -version`). Es el único requisito previo real
      de la app; si falta, el JAR no arranca. OpenJDK/Temurin 17 alcanza — no hace falta
      Maven ni nada más.
- [ ] **Tailscale instalado**, unido al mismo tailnet y con arranque automático.
- [ ] Ping al servidor por su IP de Tailscale (`tailscale status` y
      `Test-NetConnection <IP_SERVIDOR> -Port 3306`).

### 3.9 Checklist pre-deploy

**En la PC servidor (la actual):**
- [ ] `have_ssl = YES`
- [ ] Usuario de app con `ALL PRIVILEGES ON sistema_empresa.*`
- [ ] Dump de la base hecho y verificado (§3.4)
- [ ] Espacio libre ≥ 3× el tamaño de la base
- [ ] Tareas de backup existentes revisadas (§3.7)
- [ ] Copia del JAR actual (`aptium.jar.v1.1.6.1`)
- [ ] Suspensión automática desactivada
- [ ] Tailscale instalado, con expiración de clave desactivada y arranque automático

**En cada PC nueva:**
- [ ] Java 17+ instalado
- [ ] Tailscale instalado y conectado al tailnet
- [ ] Llega al puerto 3306 del servidor

**En tu máquina de desarrollo:**
- [ ] `mvn clean package` en verde (1138 tests)

---

## 4. Deploy day — paso a paso

### 4.1 Publicar el release

```powershell
git tag v1.2.0
git push origin v1.2.0
```

El workflow [`.github/workflows/release.yml`](.github/workflows/release.yml) compila con
`-Dapp.version=1.2.0`, y publica `aptium.jar` + `aptium.jar.sha256` como assets del release.

Esperar a que el workflow termine en verde y confirmar que el release aparece en GitHub con
**los dos** assets. El nombre del asset tiene que ser exactamente `aptium.jar` (sin versión
en el nombre) — de eso depende el auto-update.

> El número de versión es decisión tuya; `v1.2.0` es coherente con el tamaño del cambio
> (la última fue `v1.1.6.1`). El único requisito técnico es que sea **mayor** que la que
> corre hoy, comparada segmento por segmento.

El deploy tiene **dos tramos**, y el orden entre ellos importa: primero se actualiza la PC
actual (que es la que migra la base) y recién cuando eso está verde se suman los puestos
nuevos. Un problema a la vez.

---

## Tramo A — la PC actual pasa a ser servidor

### 4.2 Preparar la red, con la app vieja todavía andando

Aplicar la **sección 1 completa** del
[runbook de Tailscale](docs/conexion-remota-mysql-tailscale.md): `bind-address`, usuario
MySQL dedicado, TLS, regla de firewall, y la permanencia del nodo (§1.8 de ese doc).

Se hace **antes** de actualizar la app, y a propósito: son cambios de red reversibles que
se pueden verificar con la versión vieja todavía en producción. Después de cada cambio,
abrir la app actual y confirmar que sigue funcionando.

> Dos avisos para esta PC en particular:
> - Sigue conectando por **`localhost`**, no por su IP de Tailscale. No hace falta cambiarle
>   el `db.ip`.
> - Si hoy corre con los defaults (`root`/`root`) y creás el usuario dedicado, **también
>   hay que actualizarle la configuración a esta PC** (§7), o deja de conectar. El WARN
>   `⚠️ USANDO CREDENCIALES DE DESARROLLO` en su log de arranque te dice si está con los
>   defaults.
> - `sslMode=REQUIRED` aplica **también a localhost**: con el JAR nuevo, esta PC tampoco
>   conecta si `have_ssl` no da `YES`.

### 4.3 Cerrar la app

Antes de tocar la base:

```powershell
Get-Process java -ErrorAction SilentlyContinue | Select-Object Id, Path
```

### 4.4 Actualizar la PC servidor — acá se migra la base

Es la PC que tiene la base, así que es la que aplica las 13 migraciones.

**Opción A — desde la app (recomendada).** El JAR `v1.1.6.1` ya tiene el botón:
> Ajustes → **Buscar actualizaciones** → confirmar → la app se cierra, se reemplaza sola y
> se vuelve a abrir con la versión nueva.

Puede pedir permiso de administrador (UAC) si el JAR está en una carpeta protegida — es
esperado, hay que aceptarlo. Si se cancela, el JAR queda intacto y la app se relanza con la
versión vieja.

**Opción B — manual.** Descargar `aptium.jar` del release, verificar el checksum contra el
`.sha256` publicado, y reemplazar el archivo:

```powershell
(Get-FileHash .\aptium.jar -Algorithm SHA256).Hash.ToLower()
Get-Content .\aptium.jar.sha256      # los dos valores tienen que coincidir
Copy-Item C:\Sistema\app\aptium.jar C:\Sistema\app\aptium.jar.v1.1.6.1
Copy-Item .\aptium.jar C:\Sistema\app\aptium.jar -Force
```

**Al primer arranque, Flyway aplica las 13 migraciones.** Puede tardar. En el log tiene
que aparecer:

```
PASO 2/4: Inicializando esquema de base de datos...
Migraciones Flyway aplicadas (schema + seeds)
✓ Esquema BD verificado/creado
```

Si falla acá, **parar el deploy** y ver §9.1 antes de seguir con las PCs nuevas.

### 4.5 Verificar la base migrada

```sql
SELECT MAX(version + 0) AS version_maxima FROM sistema_empresa.flyway_schema_history
WHERE success = 1;                          -- tiene que dar 22

SELECT version, description, success FROM sistema_empresa.flyway_schema_history
ORDER BY installed_rank DESC LIMIT 15;      -- ninguna con success = 0
```

Confirmar también que V21 quedó aplicada:

```sql
SHOW COLUMNS FROM sistema_empresa.equipos LIKE 'version';
SHOW COLUMNS FROM sistema_empresa.equipo_otros LIKE 'version';
```

Antes de seguir, abrir la app en esta PC y hacer el smoke de §5.2. **Si algo no anda, es el
momento de frenar**: todavía no hay puestos nuevos que revertir.

---

## Tramo B — instalar los puestos nuevos

Recién cuando el Tramo A está verde. En cada PC nueva:

### 4.6 Configuración

Con Java 17 y Tailscale ya instalados (§3.8):

```powershell
# 1. Carpeta de configuración
New-Item -ItemType Directory -Force C:\Aptium | Out-Null

# 2. config.properties apuntando al servidor por su IP de Tailscale
@"
db.ip=<IP_TAILSCALE_DEL_SERVIDOR>
db.port=3306
db.name=sistema_empresa
db.user=<USUARIO_DEDICADO>
db.pass=<PASSWORD>
"@ | Set-Content C:\Aptium\config.properties -Encoding UTF8

# 3. Permisos restrictivos
icacls C:\Aptium\config.properties /inheritance:r /grant:r "$env:USERNAME:F"
```

⚠️ Las claves van en **minúscula con puntos** (`db.ip`, `db.user`, …). Con los nombres de
las variables de entorno (`DB_HOST`, `DB_USER`) el archivo se ignora **en silencio** y el
puesto cae a `localhost` — donde no hay ninguna base. Ver §7.2.

### 4.7 Instalar la app

```powershell
# Carpeta de la app (misma estructura que el servidor, para que no haya dos convenciones)
New-Item -ItemType Directory -Force C:\Sistema\app | Out-Null

# Copiar el aptium.jar descargado del release (no un build local: ver §3.1)
Copy-Item <ruta>\aptium.jar C:\Sistema\app\aptium.jar

# ejecutar.bat — el cd /d es lo que hace que los logs queden junto al JAR (§8)
@"
@echo off
cd /d "C:\Sistema\app"
if not exist "aptium.jar" (
    echo Error: no se encuentra C:\Sistema\app\aptium.jar
    pause
    exit /b 1
)
java -jar "aptium.jar"
if errorlevel 1 (
    echo.
    echo Error al ejecutar. Revise C:\Sistema\app\logs\error.log
    pause
)
"@ | Set-Content C:\Sistema\app\ejecutar.bat -Encoding OEM
```

Crear un acceso directo a `ejecutar.bat` en el escritorio del usuario.

### 4.8 Primer arranque de cada puesto nuevo

Abrir la app y confirmar en el log (§5.1):

- Aparece `config.properties cargado desde: C:\Aptium\config.properties`
- **No** aparece `⚠️ USANDO CREDENCIALES DE DESARROLLO`
- La secuencia de arranque llega a `Aplicación inicializada correctamente`
- Los datos que se ven son los mismos que en la PC servidor

En este puesto la migración **no corre**: la base ya está en V22 y sólo se verifica que el
build la conoce.

### 4.9 Checklist del día

**Tramo A — PC servidor:**
- [ ] Release publicado con `aptium.jar` y `aptium.jar.sha256`
- [ ] Sección 1 del runbook de Tailscale aplicada, con la app vieja andando
- [ ] App cerrada
- [ ] JAR actualizado y migración aplicada sin errores
- [ ] `flyway_schema_history` en V22, sin filas con `success = 0`
- [ ] Smoke de §5.2 en verde en esta PC

**Tramo B — cada PC nueva:**
- [ ] `C:\Aptium\config.properties` con las claves `db.*` y la IP de Tailscale
- [ ] JAR del release + `ejecutar.bat` + acceso directo
- [ ] Arranque limpio, sin el WARN de credenciales de desarrollo
- [ ] Ve los mismos datos que el servidor
- [ ] Ningún puesto quedó en `v1.1.6.1`

---

## 5. Verificación post-deploy

### 5.1 Arranque limpio

En el log del puesto (§8) tiene que aparecer la secuencia completa:

```
PASO 1/4: Conectando a base de datos...
✓ Connection Pool inicializado
Pool Stats: Total=..., Activas=..., Idle=..., Esperando=0
PASO 2/4: Inicializando esquema de base de datos...
✓ Esquema BD verificado/creado
PASO 3/4: Creando contexto de dependencias...
✓ Contexto creado con DAOs y Services
PASO 4/4: Iniciando interfaz de usuario...
Aplicación inicializada correctamente
```

Y **no** tiene que aparecer `⚠️ USANDO CREDENCIALES DE DESARROLLO` — si sale, el puesto
está apuntando a `localhost` con `root/root` (§7.3).

### 5.2 Smoke funcional — en la PC servidor, cerrando el Tramo A

Con la app sola, antes de sumar ningún puesto nuevo:

- [ ] Los datos que ya existían siguen ahí (clientes, equipos, lotes)
- [ ] Cargar un ingreso de ortopedias y uno de "otros"
- [ ] Registrar Estado: avanzar un material
- [ ] Lanzar un lote
- [ ] Lavadero completo: ingreso → clasificación → lanzar tanda → finalizar ciclo → salida
- [ ] Derivar una salida de lavadero al CDE y confirmar que aparece en las pantallas del CDE
- [ ] Correcciones sobre un equipo (es la pantalla que usa la guarda de `version` de V21)
- [ ] Historial de Lavadero: abrir, filtrar, y doble clic en un ingreso para ver el detalle

Lavadero e Historial son features **nuevas en producción**: nunca corrieron acá. Si algo
va a aparecer, aparece en estos dos.

### 5.3 Concurrencia — al terminar el Tramo B

Recién se puede probar con dos máquinas. Vale la pena hacerlo una vez, porque es lo único
de este deploy que **nunca se ejercitó con dos personas reales** (hasta hoy hubo un solo
operador):

- [ ] Los dos puestos ven los mismos datos, y lo que carga uno aparece en el otro al
      refrescar con **F5**
- [ ] **Conflicto esperado:** A abre Correcciones sobre un equipo, B modifica y guarda ese
      mismo equipo, A guarda. A tiene que recibir un aviso de conflicto y recargar — no
      pisar el cambio de B en silencio. Ese es el comportamiento que V21 introduce
- [ ] En Lavadero, dos operadores no pueden cargar ropa en el mismo lavarropas: el segundo
      recibe el aviso de lavarropas ocupado

### 5.4 Primeras horas

- [ ] Sin `ERROR` ni `EXCEPTION` nuevos en `logs\error.log` de ningún puesto
- [ ] Sin advertencias de `leak detection`
- [ ] `Esperando=0` en las estadísticas del pool

---

## 6. Rollback

**El orden importa.** El JAR viejo contra la base migrada arranca pero escribe sin guardas,
así que un rollback parcial es peor que no hacer nada.

### Si falla la migración (§4.4) y todavía no se operó

Es el caso bueno: no hay trabajo nuevo que perder.

```powershell
$bin = Split-Path (Get-ChildItem "C:\Program Files\MySQL" -Filter mysqldump.exe -Recurse |
                   Select-Object -First 1).FullName

# 1. Cerrar la app
# 2. Restaurar la base desde el dump del §3.4
& "$bin\mysql.exe" -h localhost -u <USUARIO> -p sistema_empresa < "<RUTA_DEL_DUMP_DEL_3.4>"

# 3. Restaurar el JAR viejo
Copy-Item <RUTA_DEL_JAR>\aptium.jar.v1.1.6.1 <RUTA_DEL_JAR>\aptium.jar -Force
```

Después de restaurar, la base vuelve a estar en V16 y el JAR viejo vuelve a ser el
correcto para ella. Nada queda desalineado.

### Si ya se operó con la versión nueva

Restaurar el dump **descarta todo el trabajo hecho desde el backup**. Antes de decidirlo,
hacer un dump del estado actual para no perderlo:

```powershell
& "$bin\mysqldump.exe" -h localhost -u <USUARIO> -p --single-transaction `
  sistema_empresa > "<CARPETA>\sistema_empresa_rollback_$(Get-Date -Format yyyyMMdd_HHmm).sql"
```

Y volver atrás **todos** los puestos junto con la base, nunca sólo algunos: un JAR viejo
contra una base V22 escribe sin las guardas.

> Casi siempre es mejor arreglar hacia adelante que revertir. Antes de restaurar, mirá §9
> — la mayoría de los fallos de este deploy (TLS, grants, config mal leída) se resuelven
> sin tocar la base.

---

## 7. Configuración de la app

### 7.1 Precedencia

```
1. Variables de entorno   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS
2. config.properties      buscado en este orden:
                            /etc/aptium/config.properties      (Linux)
                            C:\Aptium\config.properties        (Windows)
                            .\config.properties                (relativo al cwd)
                            .\Administracion-Aptium\config.properties
3. Defaults               localhost:3306 / root / root  ← solo dev, loguea un WARN
```

Las variables de entorno **ganan siempre**, clave por clave, sobre el archivo.

### 7.2 Las claves del archivo NO son las de las variables de entorno

```properties
# C:\Aptium\config.properties
db.ip=<IP_DEL_SERVIDOR_MYSQL>
db.port=3306
db.name=sistema_empresa
db.user=<USUARIO>
db.pass=<PASSWORD>
```

⚠️ En el archivo van en **minúscula con puntos** (`db.ip`, `db.user`, …), no `DB_HOST` /
`DB_USER`. Si se escriben con los nombres de las variables de entorno, `ConnectionPool`
**los ignora en silencio** y cae a los defaults de desarrollo sin ningún error visible.
La única señal es el WARN de credenciales de desarrollo en el log.

Nótese que la ruta `C:\Aptium\config.properties` está **fija en el código**: el JAR puede
vivir en cualquier carpeta, pero el archivo de configuración se busca ahí (o en el cwd).

Permisos, como Administrador:

```powershell
icacls C:\Aptium\config.properties /inheritance:r /grant:r "$env:USERNAME:F"
```

### 7.3 Diagnóstico de configuración

```powershell
# ¿Qué hay seteado a nivel usuario y máquina?
[System.Environment]::GetEnvironmentVariable("DB_HOST","User")
[System.Environment]::GetEnvironmentVariable("DB_HOST","Machine")

# ¿Existe el archivo, y qué dice?
Get-Content C:\Aptium\config.properties -ErrorAction SilentlyContinue

# ¿Hay más de un config.properties dando vueltas?
Get-ChildItem C:\ -Filter "config.properties" -Recurse -ErrorAction SilentlyContinue -Depth 4
```

En el log del arranque tiene que aparecer **una** de estas dos líneas:

```
config.properties cargado desde: <ruta>
DB_HOST cargado desde variable de entorno
```

Si no aparece ninguna, el puesto está corriendo con los defaults de desarrollo.

---

## 8. Logs

**Los logs se escriben en `logs\` relativo al directorio de trabajo del proceso**, no en
una ruta absoluta ([logback.xml:24](src/main/resources/logback.xml#L24)). Como
`ejecutar.bat` hace `cd /d` a la carpeta del JAR, quedan al lado del JAR:

```
<carpeta del JAR>\logs\
├── app.log        INFO+   (rota a diario o a los 20 MB, 30 días, tope 1 GB)
├── error.log      ERROR+  con stack traces (90 días, tope 2 GB)
└── *.log.gz       históricos comprimidos
```

> Por eso conviene lanzar siempre desde `ejecutar.bat` y no desde un acceso directo con
> otro "Iniciar en": si el cwd cambia, los logs aparecen en otra carpeta.

```powershell
$app = "C:\Sistema\app"          # ajustar a la ruta real del puesto

Get-Content $app\logs\app.log -Tail 50
Get-Content $app\logs\app.log -Wait                       # en vivo
Select-String "ERROR|EXCEPTION" $app\logs\error.log
@(Select-String "ERROR" $app\logs\error.log).Count
```

Nivel de log (default `INFO`):

```powershell
java -Dlogback.level=DEBUG -jar aptium.jar
```

> **No** usar `-Daptium.edt.strict=true` en producción: convierte en excepción lo que hoy
> es un WARN, y los cinco autocompletados por tecla son síncronos a propósito.

---

## 9. Troubleshooting

### 9.1 Falla la migración de Flyway

El log de `error.log` dice qué migración y qué sentencia. Antes de reintentar:

```sql
SELECT version, description, success, installed_on
FROM sistema_empresa.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

Una fila con `success = 0` deja el historial bloqueado: Flyway se niega a seguir hasta que
se resuelva. **No editar `flyway_schema_history` a mano** — restaurar el dump (§6) y
diagnosticar con calma.

### 9.2 "Actualización requerida" al arrancar

> *Esta versión de la aplicación es más vieja que la base de datos.*

Ese puesto quedó con un JAR anterior al de la base. Es el chequeo del §2.2 funcionando.
La app ofrece actualizarse sola; si se rechaza, se cierra. Solución: actualizar ese puesto.

### 9.3 No conecta a MySQL

En orden:

```powershell
# 1. ¿El servicio está arriba? (en la PC del servidor)
Get-Service -Name "*mysql*"

# 2. ¿Responde el puerto desde el puesto?
Test-NetConnection -ComputerName <IP_SERVIDOR> -Port 3306      # TcpTestSucceeded: True

# 3. ¿Tailscale está conectado en los dos extremos?
tailscale status

# 4. ¿Qué dice el log?
Select-String "Connection refused|timeout|SSL|Access denied" <carpeta>\logs\error.log
```

Causas por orden de probabilidad en este deploy:

| Síntoma en el log | Causa |
|---|---|
| Error de SSL / TLS al conectar | El servidor no ofrece TLS (§3.2) — habilitarlo en el servidor, no bajar el modo en el cliente |
| `Access denied for user` | Credenciales, o el host del cliente no está en los grants |
| `Connection refused` | MySQL caído, `bind-address` en `127.0.0.1`, firewall, o Tailscale abajo |
| Arranca con `localhost` sin que nadie lo haya pedido | Configuración no leída (§7.2) |

### 9.4 `Access denied` al ejecutar DDL

Grants insuficientes (§3.3). La app conecta pero muere en la migración.

### 9.5 OutOfMemoryError

```powershell
java -Xmx1024m -Xms512m -jar aptium.jar
```

### 9.6 El auto-update no encuentra actualizaciones

- El repo tiene que ser **público** (el cliente pega a la API de GitHub sin autenticar).
- El asset tiene que llamarse exactamente `aptium.jar`, con su `aptium.jar.sha256`.
- Límite no autenticado de GitHub: 60 requests/hora por IP. Con varios puestos detrás de
  la misma salida a internet chequeando seguido, se puede tocar el techo — esperar.
- El staging vive en `%LOCALAPPDATA%\Aptium\updates\`. Ahí queda también `reemplazo.log`,
  que es lo primero que hay que mirar si un reemplazo falló.

---

## 10. Seguridad

### 10.1 Credenciales

- Nunca commitear `config.properties` con datos reales (ya está en `.gitignore`).
- Nunca `root` como usuario de la app en producción.
- `config.properties` con permisos restrictivos (§7.2), o variables de entorno.

### 10.2 Usuario de MySQL

```sql
CREATE USER 'aptium_prod'@'<host_o_subred>' IDENTIFIED BY '<password_fuerte>';
GRANT ALL PRIVILEGES ON sistema_empresa.* TO 'aptium_prod'@'<host_o_subred>';
FLUSH PRIVILEGES;
```

`ALL` **acotado a `sistema_empresa.*`** — no menos (rompe Flyway, §3.3) y no `ON *.*`.
El host tiene que ser el real de los puestos (la subred de Tailscale si se conecta por ahí),
no `%`.

### 10.3 TLS

Ya resuelto en el código: las dos URLs JDBC llevan `sslMode=REQUIRED`, así que la app no
conecta si el servidor no ofrece TLS, en vez de caer a texto plano en silencio como hace el
default `PREFERRED` de Connector/J 8.x.

**Pendiente conocido (decisión, no defecto):** `REQUIRED` cifra pero **no valida** el
certificado del servidor, así que no protege por sí solo contra un MITM activo — hoy eso lo
cubre el túnel de Tailscale. Subir a `VERIFY_CA` exige distribuir un truststore a cada puesto.

### 10.4 Permisos de archivos (como Administrador)

```powershell
icacls C:\Aptium /grant:r "Administrators:F" "SYSTEM:F" /inheritance:r
icacls C:\Aptium\config.properties /inheritance:r /grant:r "$env:USERNAME:F"
```

> Ojo: si la carpeta del JAR queda sin permiso de escritura para el usuario, el
> auto-update va a pedir UAC en cada actualización. Es un camino soportado y probado, pero
> hay que saber que el prompt es esperado.

---

## 11. Actualizaciones futuras

A partir de esta versión, el ciclo normal es:

1. `git tag vX.Y.Z && git push origin vX.Y.Z` → el workflow publica el release.
2. En cada puesto: **Ajustes → Buscar actualizaciones**.
3. Si la nueva versión trae migraciones, **el primer puesto que abra migra la base**, y
   los demás quedan bloqueados con "Actualización requerida" hasta que se actualicen.
   Ese bloqueo es la red de seguridad que este deploy estrena — conviene igual seguir
   actualizando todos los puestos el mismo día.

Antes de cada release con migraciones: **dump de la base** (§3.4).

---

## 12. Backups

> **Esto no es parte del deploy.** Lo único que el deploy necesita de acá es el dump del
> §3.4 y que haya espacio en disco (§3.5). El resto es trabajo aparte, para después.

La PC servidor es la única que tiene los datos: si se pierde su disco, se perdió todo.

### 12.1 Lo que hay hoy (pendiente de revisar)

Hay dos tareas programadas en la PC servidor, hechas hace tiempo: una manda una copia por
mail y otra deja una copia local. La local **probablemente no tiene política de retención**
y está llenando el disco.

Los comandos para inventariarlas están en §3.7. Las preguntas a responder son dos:

1. **¿Sigue corriendo?** `LastTaskResult = 0` y un `LastRunTime` reciente. Una tarea que
   viene fallando hace meses es peor que no tenerla, porque uno cree que está cubierto.
2. **¿Tiene retención?** Si la cantidad de archivos crece de a uno por día desde el
   principio de los tiempos, no la tiene.

Hasta no ver **dónde escribe, con qué nombres y con qué frecuencia**, cualquier limpieza
automática es peligrosa: un borrado "por antigüedad" sobre archivos cuyo nombre no
conocés puede no borrar nada (y no resolver el problema) o borrar el histórico entero.
Lo que sí se puede hacer sin riesgo es liberar espacio a mano para que la migración tenga
aire.

### 12.2 Criterios para cuando se resuelva

- **Comprimir.** Un dump SQL comprime entre 5x y 10x. Sólo eso ya cambia el orden de
  magnitud del problema.
- **La limpieza corre después del backup, y sólo si el backup salió bien.** Una tarea de
  limpieza independiente que corre igual cuando el backup falló termina, con el tiempo,
  sin backups y sin historial.
- **Verificar que el dump esté completo** antes de darlo por bueno: `mysqldump` cierra
  siempre con `-- Dump completed`. Un dump truncado por disco lleno es un archivo que
  existe y no sirve.
- **Nunca borrar por fecha un archivo cuyo origen no conocés.** El `LastWriteTime` cambia
  si alguien copió o restauró la carpeta.
- **Credenciales fuera de la línea de comandos** (quedan visibles en la lista de procesos):
  usar un archivo de opciones estilo `my.cnf` con `--defaults-extra-file`, y un usuario de
  MySQL sólo para backups (`SELECT, LOCK TABLES, SHOW VIEW, EVENT, TRIGGER`).

### 12.3 La tarea que manda la copia por mail

Esa **no** conviene tocarla más que para confirmar que sigue funcionando: es el único
backup que vive **fuera** de la PC servidor, y por eso es el que salva de un disco muerto,
un ransomware o un incendio. Un backup local no cubre ninguno de esos casos.

Dos cosas que vale la pena mirar:

- Que el adjunto que llega **no esté vacío ni truncado** — abrir el último mail y mirar el
  tamaño. Los proveedores de correo cortan adjuntos grandes (10–25 MB), y con Lavadero la
  base va a crecer: es probable que en algún momento empiece a rebotar o a llegar cortado.
  El reemplazo natural es sincronizar la carpeta a OneDrive/Drive en vez del mail.
- Que mande el comprimido y no el `.sql` crudo, por lo mismo.

### 12.4 Un backup sin restaurar no es un backup

Al menos una vez, probar la restauración completa contra una base con otro nombre
(`CREATE DATABASE prueba_restore`, restaurar ahí, verificar que las tablas tienen las filas
esperadas, `DROP DATABASE prueba_restore`). Hasta que eso no se hizo una vez, no se sabe
si los backups sirven.

---

## Referencia técnica

| | |
|---|---|
| Lenguaje | Java 17 (bytecode 17; el workflow compila con Temurin 17) |
| Base de datos | MySQL 8.0+ |
| UI | Swing (FlatLaf) — escritorio, una instancia por puesto |
| Build | Maven + `maven-shade-plugin` → `target\aptium.jar` (fat JAR) |
| Migraciones | Flyway 8.5.13, `baselineOnMigrate` + `outOfOrder` + chequeo de esquema adelantado |
| Pool | HikariCP 5.x — 10 conexiones máx., 5 idle, **por puesto** |
| Transacciones | `TransactionalConnection` (commit/rollback manual, sin framework) |
| Logging | Logback / SLF4J, rotación diaria + por tamaño |
| Tests | JUnit 5 + Mockito + H2 en memoria — 1138 tests |
| Concurrencia | Bloqueo optimista: guardas por fila; `version` en `equipos`/`equipo_otros` |
| DI | Manual (`AppContext`), sin Spring |

**Memoria recomendada por puesto**: 1–2 GB. **Disco**: 50 MB de app + logs.
