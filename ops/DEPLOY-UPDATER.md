# Deploy del updater automático (`ops/updater.ps1`)

Walkthrough para reemplazar el script de la Tarea Programada que corre en cada boot de la PC
de producción y actualiza `aptium.jar` desde el último release de GitHub.

## Qué cambió respecto a la versión anterior

- **Token de GitHub ya no está hardcodeado** en el script — se lee de la variable de entorno
  `APTIUM_GITHUB_TOKEN`. El token viejo quedó expuesto en texto plano y debe rotarse.
- **Lock file compartido** (`aptium.jar.lock`, sibling del jar) para coordinarse con el botón
  "Buscar actualizaciones" de la app: si cualquiera de los dos ya está actualizando, el otro
  aborta esa corrida en vez de pisarlo. Se libera solo, incluso si el script falla a mitad de
  camino; si queda un lock de más de 15 min (ej. corte de luz), se recicla automáticamente.
- **Control de versión real**: antes de matar Java y reinstalar, lee `app.version` directamente
  del `version.properties` embebido en el jar instalado (sin archivo de registro aparte) y no
  hace nada si ya coincide con el último release. Antes reinstalaba en cada boot sin chequear.

## Pasos

### 1. Rotar el token de GitHub

El token anterior quedó expuesto en texto plano dentro del script. Andá a GitHub →
**Settings → Developer settings → Personal access tokens**, revocá el viejo y generá uno nuevo
con permiso de lectura sobre este repo (alcanza con `repo` de solo lectura, o un fine-grained
token con `Contents: Read-only` restringido a `Administracion-Aptium`).

### 2. Configurar la variable de entorno en la PC de producción

En PowerShell **como Administrador**:

```powershell
setx APTIUM_GITHUB_TOKEN "el-token-nuevo-acá" /M
```

El flag `/M` es obligatorio: sin él la variable queda solo para tu usuario, y la tarea
programada (que corre como SYSTEM/Administrador) no la va a ver.

### 3. Reiniciar la PC

El Programador de tareas no relee variables de entorno del sistema en caliente para procesos
que lanza — hace falta un reinicio para que la tome. Como la tarea corre en cada boot de
todas formas, este mismo reinicio sirve para probar todo el flujo junto (pasos 5-7).

### 4. Backup del script actual

Antes de pisarlo, renombrá el que está en producción — no lo borres, es la forma más rápida
de volver atrás si algo sale mal:

```powershell
Rename-Item -Path "C:\Sistema\updater.ps1" -NewName "updater.ps1.old"
```

### 5. Copiar el nuevo script

Copiá [`ops/updater.ps1`](updater.ps1) de este repo a `C:\Sistema\updater.ps1`. La Tarea
Programada ya apunta a esa ruta fija — no hace falta tocar la definición de la tarea en sí
(acción, desencadenador, cuenta), solo el contenido del archivo.

### 6. Verificar la configuración de la tarea (opcional, si no se conoce)

Si hay dudas sobre cómo quedó armada la tarea originalmente:

```powershell
Get-ScheduledTask | Where-Object { $_.Actions.Arguments -like "*updater.ps1*" } |
    Format-List TaskName, TaskPath
schtasks /query /tn "<nombre-de-la-tarea>" /v /fo list
```

Confirmar:

| Campo | Debería ser | Por qué |
|---|---|---|
| Trigger | "At startup" | "At log on" no dispara hasta que alguien inicia sesión — no es lo mismo que "cada boot" en una pantalla de login sin nadie logueado. |
| Run As User | `SYSTEM` | No depende de contraseña de cuenta guardada, que si cambia deja la tarea rota en silencio. |
| Run whether user is logged on or not | Tildado | Necesario para correr sin sesión interactiva (automático si es SYSTEM). |
| Run with highest privileges | Tildado | El script mata procesos y reemplaza el jar; sin esto, si la cuenta no es SYSTEM, vuelve el error de Acceso Denegado. |

### 7. Probar manualmente antes de confiar en el próximo boot

Programador de tareas → click derecho sobre la tarea → **Ejecutar**. Esto pasa por el mismo
camino que un boot real (a diferencia de correr el `.ps1` a mano en una consola, que no usa el
contexto de la tarea). Después revisar `C:\Sistema\updater.log`:

- `[ERROR] La variable de entorno APTIUM_GITHUB_TOKEN no está configurada` → el paso 2 y/o el
  reinicio del paso 3 no surtieron efecto.
- `[INFO] Ya está instalada la última versión (...)` → funcionó, no había nada para actualizar.
- `[SUCCESS] Sistema actualizado con éxito a la versión ...` → funcionó y actualizó.
- `[INFO] Ya hay una actualización en curso (lock: ...)` → el botón de la app estaba
  actualizando al mismo tiempo; es el comportamiento esperado, no un error.

### 8. Confirmar que el lock se liberó

```powershell
Test-Path "C:\Sistema\app\aptium.jar.lock"
```

Debería devolver `False` apenas termina la corrida del paso 7. Si queda en `True` por más de
15 minutos después de que el script terminó, algo no liberó el lock correctamente — revisar
`updater.log` para ver en qué paso se cortó.

### 9. (Opcional) Confirmar en un boot real

Si el paso 7 salió bien, el próximo boot real va a funcionar igual — "Ejecutar" desde el
Programador ejercita exactamente el mismo mecanismo que el disparador de inicio del sistema.
