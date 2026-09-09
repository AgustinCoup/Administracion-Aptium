# Conexión remota a MySQL vía Tailscale — runbook

Cómo hacer que una PC alcance el MySQL de otra, estando las dos en el mismo tailnet.

**Quién aplica qué:**

| Rol | Máquina | Secciones |
|---|---|---|
| **Servidor** | La PC que hoy tiene la app y la base (pasa a alojar MySQL para todos) | **§1** completa |
| **Cliente** | Cada puesto nuevo | **§2** (y §4 si algo no conecta) |

La PC servidor sigue conectando por `localhost` — no necesita la §2. Su IP de Tailscale
es la que van a usar los clientes.

> Para el procedimiento de deploy completo (migraciones, orden de actualización, rollback),
> ver [../README-DEPLOY.md](../README-DEPLOY.md). Este runbook cubre solo la conectividad.

## 1. PC servidor (la que aloja MySQL)

### 1.1 Ver los peers del tailnet y sus IPs
```bash
tailscale status
```

### 1.2 Verificar que el servicio MySQL está corriendo
```powershell
Get-Service -Name "*mysql*"
```

### 1.3 Verificar bind-address en la config de MySQL
```powershell
$paths = @("C:\ProgramData\MySQL\MySQL Server 8.0\my.ini", "C:\Program Files\MySQL\MySQL Server 8.0\my.ini")
foreach ($p in $paths) { if (Test-Path $p) { Write-Output "FOUND: $p"; Select-String -Path $p -Pattern "bind-address|port\s*=" } }
```
Debe ser `0.0.0.0` (o la IP de Tailscale puntual). Si está en `127.0.0.1`, cambiarlo
y reiniciar el servicio.

### 1.4 Verificar que el servidor ofrece TLS (obligatorio desde `aad4dfc`)
```sql
SHOW GLOBAL VARIABLES LIKE 'have_ssl';   -- tiene que decir YES
```
La app arma las URLs JDBC con `sslMode=REQUIRED`, así que **no conecta** si el
servidor no ofrece TLS — falla en el primer paso del arranque, en todos los puestos
a la vez. No es un problema de red ni de credenciales, y el síntoma no lo aclara.

Si da `DISABLED`, hay que habilitar TLS **en el servidor** (`ssl_cert` / `ssl_key`
en `my.ini` y reiniciar el servicio), nunca bajar el modo en el cliente. MySQL 8
genera certificados autofirmados al inicializar el datadir, así que normalmente ya
viene en `YES`.

Sobre una sesión ya conectada desde el puesto, la línea `SSL:` de `STATUS;` muestra
el cifrado en uso.

### 1.5 Verificar grants del usuario MySQL
```powershell
$mysqlExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
& $mysqlExe -u<ADMIN> -p<PASSWORD_ADMIN> -N -e "SELECT user, host FROM mysql.user WHERE user='<USUARIO>';"
```
`<ADMIN>`/`<PASSWORD_ADMIN>` es quien tenga acceso administrativo al MySQL de
producción (hoy usamos `root`/`root` porque es el único usuario que existe en
este entorno de dev). Necesita una fila con host `%` (o la subred del
cliente). Si no existe, crear un usuario dedicado en vez de usar `root`:
```sql
CREATE USER '<USUARIO>'@'<SUBRED_O_HOST_CLIENTE>' IDENTIFIED BY '<PASSWORD>';
GRANT ALL PRIVILEGES ON sistema_empresa.* TO '<USUARIO>'@'<SUBRED_O_HOST_CLIENTE>';
FLUSH PRIVILEGES;
```
`100.64.0.0/255.192.0.0` (`/10`) es el CGNAT completo de Tailscale — sirve
solo si en producción la DB sigue siendo accedida a través del tailnet. Si el
deploy real usa otra red (LAN, VPC, otra VPN), reemplazar por la subred/host
que corresponda a ese entorno.

⚠️ **`ALL PRIVILEGES` acotado al schema es el mínimo, no un exceso.** La app hace
`CREATE DATABASE IF NOT EXISTS` y Flyway ejecuta DDL (CREATE TABLE, ALTER, índices)
en cada arranque con migraciones pendientes. Un usuario con solo
SELECT/INSERT/UPDATE/DELETE conecta bien y después **muere en la migración**. Lo que
no hay que hacer es `ON *.*`.

### 1.6 Abrir el puerto en el firewall (requiere PowerShell como Administrador)
```powershell
New-NetFirewallRule -DisplayName "MySQL (Tailscale)" -Direction Inbound -Protocol TCP -LocalPort 3306 -RemoteAddress 100.64.0.0/10 -Action Allow -Profile Any
```
`100.64.0.0/10` es el rango CGNAT de Tailscale para todo el tailnet — restringe el
acceso a esa subred en vez de abrir el puerto a cualquier IP.

Verificar que quedó creada:
```powershell
Get-NetFirewallRule -DisplayName "MySQL (Tailscale)" | Select-Object DisplayName, Enabled, Direction, Action
```

### 1.7 Probar que el puerto responde
```powershell
Test-NetConnection -ComputerName <IP_TAILSCALE_DE_ESTA_PC> -Port 3306
```
`TcpTestSucceeded` debe dar `True`.

### 1.8 Permanencia del nodo (hacerlo una vez, antes de producción)

Dos cosas que no fallan el día del deploy sino meses después, y cuyo síntoma no
apunta a Tailscale:

- **Desactivar la expiración de clave del nodo servidor** en la consola de Tailscale
  (Machines → el nodo → *Disable key expiry*). Por defecto la clave expira a los
  ~180 días: ese día **todos los puestos pierden la base a la vez**, sin que haya
  cambiado nada en la app ni en MySQL. Conviene hacerlo también en los puestos.
- **Confirmar que Tailscale arranca solo al bootear**, en el servidor y en cada
  puesto — si no, alcanza un reinicio de la PC servidor para dejar a todos afuera:
  ```powershell
  Get-Service Tailscale | Select-Object Name, Status, StartType   # StartType: Automatic
  ```

## 2. PC cliente (la que se conecta remotamente)

Dos formas de apuntar la app al servidor remoto — **no mezclar las claves de una
con la otra**, tienen nombres distintos y eso fue justo el bug de hoy.

### Opción A — `config.properties` (recomendada para dev)
Crear/editar `config.properties` en la raíz del repo, o mejor en
`C:\Aptium\config.properties` (se busca antes que cualquier ruta relativa al
`cwd`, así que no depende de con qué directorio de trabajo lance VSCode el
proceso):
```properties
db.ip=<IP_TAILSCALE_DEL_SERVIDOR>
db.port=3306
db.name=sistema_empresa
db.user=<USUARIO>
db.pass=<PASSWORD>
```
`root`/`root` son solo los valores por defecto de este entorno de dev — en
producción usar el usuario dedicado creado en el paso 1.5, nunca `root`.

⚠️ Las claves son `db.ip`, `db.port`, `db.name`, `db.user`, `db.pass`, en
minúscula con puntos. **No** `DB_HOST` / `DB_USER` / etc. — esos son los
nombres de las variables de entorno (Opción B). Si se usan por error dentro
del archivo, `ConnectionPool` los ignora en silencio y cae a los defaults
(`localhost` / `root` / `root`) sin ningún error visible.

### Opción B — Variables de entorno (tienen prioridad sobre el archivo)
```powershell
[System.Environment]::SetEnvironmentVariable("DB_HOST", "<IP_TAILSCALE_DEL_SERVIDOR>", "User")
[System.Environment]::SetEnvironmentVariable("DB_PORT", "3306", "User")
[System.Environment]::SetEnvironmentVariable("DB_NAME", "sistema_empresa", "User")
[System.Environment]::SetEnvironmentVariable("DB_USER", "<USUARIO>", "User")
[System.Environment]::SetEnvironmentVariable("DB_PASS", "<PASSWORD>", "User")
```
En producción, esta es la forma preferida de pasar las credenciales (ver nota
de deploy más abajo) — nunca hardcodear el usuario/password real en un
`config.properties` que pueda terminar commiteado.
Reiniciar VSCode/la terminal después — las env vars solo se heredan al abrirse
el proceso. Estas variables ganan siempre sobre `config.properties`: si ya hay
algo seteado (aunque sea viejo), va a pisar lo que pongas en el archivo.

Para chequear si ya hay algo seteado antes de asumir que manda el archivo:
```powershell
[System.Environment]::GetEnvironmentVariable("DB_HOST","User")
[System.Environment]::GetEnvironmentVariable("DB_HOST","Machine")
```

## 3. VSCode — cwd del launch *(solo máquina de desarrollo)*

Si se lanza desde VSCode, agregar `"cwd": "${workspaceFolder}"` a las
configuraciones de `.vscode/launch.json` para que la búsqueda relativa de
`config.properties` sea determinística (ya aplicado en este repo). Aun así, el
botón "Run" genérico del editor (▷ arriba a la derecha del tab) puede no
respetar `launch.json` — para asegurarse de qué config usa, lanzar desde el
panel Run and Debug (Ctrl+Shift+D) eligiendo la configuración por nombre.

## 4. Diagnóstico si algo no conecta

```powershell
# ¿Hay un config.properties "de prod" pisando el de dev?
Test-Path "C:\Aptium\config.properties"
Get-Content "C:\Aptium\config.properties" -ErrorAction SilentlyContinue

# ¿Hay más de un config.properties dando vueltas?
Get-ChildItem -Path "$env:USERPROFILE" -Filter "config.properties" -Recurse -ErrorAction SilentlyContinue | Select-Object FullName
```
Mirar la consola al arrancar la app: debe loguear
`"config.properties cargado desde: <ruta>"` o
`"DB_HOST cargado desde variable de entorno"`. Si no aparece ninguna de las
dos líneas, está usando los defaults hardcodeados.

## Notas para el día del deploy

> El procedimiento completo (release, migraciones, orden de actualización de los
> puestos, backup y rollback) está en [../README-DEPLOY.md](../README-DEPLOY.md).
> Acá quedan solo los puntos de red y configuración.

- **`have_ssl = YES` verificado antes de mañana** (§1.4). Es lo único de esta lista
  que deja la app sin arrancar en todos los puestos a la vez, y nunca corrió así en
  producción.
- **Grants con `ALL PRIVILEGES ON sistema_empresa.*`** (§1.5): menos que eso rompe
  Flyway; `ON *.*` es de más.
- No dejar `root@'%'` en producción — usuario específico con grants acotados
  al host/subred real del cliente.
- La regla de firewall no persiste entre migraciones de servidor: hay que
  recrearla en la máquina real de producción con el rango que corresponda.
- Confirmar que no hay `DB_HOST`/`DB_USER`/etc. viejas seteadas en la máquina
  de producción antes de confiar en que `config.properties` manda.
- Para producción, usar `C:\Aptium\config.properties` (o `/etc/aptium/` en
  Linux) en vez de depender del `cwd` con el que se lance el proceso. La ruta está
  **fija en el código**: el JAR puede vivir en cualquier carpeta, el config no.
- Expiración de clave del nodo desactivada y Tailscale en arranque automático (§1.8).
