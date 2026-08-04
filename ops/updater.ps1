# =========================================================================================
# SYSTEMA APTIUM - SCRIPT DE ACTUALIZACIÓN AUTOMÁTICA
# =========================================================================================
# Este script se ejecuta mediante una Tarea Programada de Windows (como SYSTEM o Administrador).
# Busca la última release en GitHub, compara contra la versión ya instalada, detiene el proceso
# Java de forma segura, realiza un respaldo transaccional (Rollback) y descarga la nueva versión
# evitando el error de 'Acceso Denegado'.
#
# Coordina con el botón manual "Buscar actualizaciones" de la app vía un lock file compartido
# (mismo protocolo que ScriptDeReemplazoGenerator usa del lado de la app): antes de tocar el
# JAR, ambos mecanismos intentan crear el mismo archivo .lock de forma atómica; el que llega
# segundo aborta esta corrida en vez de pisar al primero.
#
# Deploy manual en la PC de producción: copiar este archivo a C:\Sistema\updater.ps1
# (la Tarea Programada apunta a esa ruta fija). Requiere la variable de entorno de sistema
# APTIUM_GITHUB_TOKEN configurada — ver README-DEPLOY.md.
# =========================================================================================
Start-Transcript -Path "C:\Sistema\updater.log" -Append

# --- CONFIGURACIÓN DE VARIABLES ---
$repo = "AgustinCoup/Administracion-Aptium"
$token = $env:APTIUM_GITHUB_TOKEN   # Ya no se hardcodea: setx APTIUM_GITHUB_TOKEN "..." /M
$jarDestino = "C:\Sistema\app\aptium.jar"
$jarBackup = "$jarDestino.bak"
$jarLock = "$jarDestino.lock"
$lockObsoletoMinutos = 15
$directorioTrabajo = Split-Path $jarDestino

if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host "[ERROR] La variable de entorno APTIUM_GITHUB_TOKEN no está configurada. Setearla a nivel de sistema (setx APTIUM_GITHUB_TOKEN `"...`" /M) y reiniciar la tarea programada." -ForegroundColor Red
    Stop-Transcript
    Exit 1
}

# Asegurar que el directorio de trabajo exista
if (-not (Test-Path $directorioTrabajo)) {
    New-Item -ItemType Directory -Path $directorioTrabajo -Force | Out-Null
}

# --- 0. LOCK DE ACTUALIZACIÓN ---
# Archivo sibling del JAR, creación atómica (New-Item falla si ya existe). Se recicla si tiene
# más de $lockObsoletoMinutos: se asume abandonado por una corrida anterior que no llegó a
# limpiarlo (ej. corte de luz a mitad de la actualización).
function Adquirir-LockActualizacion {
    param([string]$Lock, [int]$ObsoletoMinutos)

    if (Test-Path $Lock) {
        $antiguedad = (Get-Date) - (Get-Item $Lock).LastWriteTime
        if ($antiguedad.TotalMinutes -lt $ObsoletoMinutos) {
            return $false
        }
        Remove-Item $Lock -Force -ErrorAction SilentlyContinue
    }
    try {
        New-Item -ItemType File -Path $Lock -ErrorAction Stop | Out-Null
        return $true
    } catch {
        # Alguien más (la app, o esta misma tarea en otra corrida) lo creó justo antes.
        return $false
    }
}

if (-not (Adquirir-LockActualizacion -Lock $jarLock -ObsoletoMinutos $lockObsoletoMinutos)) {
    Write-Host "[INFO] Ya hay una actualización en curso (lock: $jarLock). Se aborta esta corrida del boot." -ForegroundColor Yellow
    Stop-Transcript
    Exit
}

# Lee "app.version" desde el version.properties embebido en el propio JAR instalado, en vez de
# llevar un registro aparte: así no depende de que alguien lo siembre en el primer deploy, y
# nunca queda desactualizado si el JAR se reemplazó por otro medio (ej. el botón de la app).
function Obtener-VersionInstalada {
    param([string]$JarPath)

    if (-not (Test-Path $JarPath)) {
        return $null
    }
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
        try {
            $entry = $zip.Entries | Where-Object { $_.FullName -eq "version.properties" }
            if (-not $entry) {
                return $null
            }
            $reader = New-Object System.IO.StreamReader($entry.Open())
            try {
                $contenido = $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
            }
        } finally {
            $zip.Dispose()
        }
    } catch {
        # JAR corrupto, bloqueado, o formato inesperado: se trata como versión desconocida
        # (fuerza la reinstalación, que es el comportamiento seguro ante la duda).
        return $null
    }

    $match = [regex]::Match($contenido, "app\.version\s*=\s*(.+)")
    if (-not $match.Success) {
        return $null
    }
    return $match.Groups[1].Value.Trim()
}

# Normaliza el prefijo "v" opcional (los tags de GitHub lo llevan, app.version no) para poder
# comparar como texto sin que un simple prefijo distinto dispare una reinstalación de más.
function Normalizar-Version {
    param([string]$Version)
    if ($null -eq $Version) { return $null }
    return $Version.Trim().TrimStart('v', 'V')
}

try {
    # --- 1. CONSULTA DE LA ÚLTIMA RELEASE EN GITHUB ---
    Write-Host "[INFO] Consultando últimas versiones en GitHub..." -ForegroundColor Cyan
    $headers = @{
        "Authorization" = "token $token"
        "Accept"        = "application/vnd.github.v3+json"
    }

    try {
        $urlRelease = "https://api.github.com/repos/$repo/releases/latest"
        $releaseInfo = Invoke-RestMethod -Uri $urlRelease -Headers $headers -ErrorAction Stop
        $tagNuevaVersion = $releaseInfo.tag_name

        # Buscar el asset que termine en .jar
        $asset = $releaseInfo.assets | Where-Object { $_.name -like "*.jar" } | Select-Object -First 1

        if (-not $asset) {
            Write-Host "[ERROR] No se encontró ningún archivo .jar en la última release." -ForegroundColor Red
            Exit
        }

        # El checksum se verifica después de la descarga (paso 5): sin él, un HTTP 200 con
        # contenido corrupto o truncado (proxy, portal cautivo, corte a mitad de descarga que
        # Invoke-WebRequest no siempre reporta como excepción) se instalaría igual y se borraría
        # el único backup disponible, dejando el sistema sin forma de volver atrás.
        $assetChecksum = $releaseInfo.assets | Where-Object { $_.name -like "*.sha256" } | Select-Object -First 1

        if (-not $assetChecksum) {
            Write-Host "[ERROR] No se encontró el archivo .sha256 de la última release; no se puede verificar la integridad de la descarga." -ForegroundColor Red
            Exit
        }
    } catch {
        Write-Host "[ERROR] No se pudo obtener la información de GitHub: $_" -ForegroundColor Red
        Exit
    }

    # --- 2. CONTROL DE VERSIONES ---
    # Evita matar los procesos Java y reinstalar en cada boot cuando ya está la última versión.
    $versionInstalada = Obtener-VersionInstalada -JarPath $jarDestino
    if ((Normalizar-Version $versionInstalada) -eq (Normalizar-Version $tagNuevaVersion)) {
        Write-Host "[INFO] Ya está instalada la última versión ($tagNuevaVersion). No hay nada para hacer." -ForegroundColor Green
        Exit
    }
    $versionInstaladaTexto = if ($versionInstalada) { $versionInstalada } else { "desconocida (primera corrida o JAR no encontrado)" }
    Write-Host "[INFO] Nueva versión detectada: $tagNuevaVersion (instalada: $versionInstaladaTexto). Procediendo con la descarga..." -ForegroundColor Green

    # --- 3. CIERRE SEGURO DEL PROCESO JAVA (ANTI-BLOQUEO) ---
    Write-Host "[INFO] Verificando si existen instancias de Java ejecutándose..." -ForegroundColor Yellow
    $javaProcess = Get-Process -Name java, javaw -ErrorAction SilentlyContinue

    if ($javaProcess) {
        Write-Host "[INFO] Se detectó el programa en ejecución. Deteniendo procesos de forma forzada..." -ForegroundColor Yellow
        $javaProcess | Stop-Process -Force

        # Bucle de espera activa: asegura que Windows libere el handle del archivo físicamente
        $timeout = 10 # Máximo 10 segundos de espera
        while ((Get-Process -Name java, javaw -ErrorAction SilentlyContinue) -and $timeout -gt 0) {
            Start-Sleep -Seconds 1
            $timeout--
        }

        if ($timeout -eq 0) {
            Write-Host "[WARN] El proceso Java tardó demasiado en cerrarse. Podrían ocurrir problemas de bloqueo." -ForegroundColor DarkYellow
        } else {
            Write-Host "[INFO] Todos los procesos Java se cerraron correctamente." -ForegroundColor Green
        }
    }

    # --- 4. RESPALDO TRANSACCIONAL (EVITA EL ACCESO DENEGADO Y ASEGURA ROLLBACK) ---
    if (Test-Path $jarDestino) {
        try {
            # Si quedó un backup viejo de una actualización anterior, se limpia
            if (Test-Path $jarBackup) {
                Remove-Item $jarBackup -Force -ErrorAction SilentlyContinue
            }

            # OPERACIÓN CLAVE: Renombrar en lugar de sobrescribir directa o eliminar.
            # Windows permite renombrar archivos que están en desuso reciente, liberando la ruta original.
            Rename-Item -Path $jarDestino -NewName (Split-Path $jarBackup -Leaf) -Force -ErrorAction Stop
            Write-Host "[INFO] Respaldo temporal creado exitosamente (.bak)." -ForegroundColor Green
        } catch {
            Write-Host "[CRITICAL] Acceso Denegado al intentar manipular el archivo original. $_" -ForegroundColor Red
            Write-Host "[CRITICAL] Asegúrate de que la tarea corre con 'Privilegios más altos' y el archivo no esté abierto." -ForegroundColor Red
            Exit
        }
    }

    # --- 5. DESCARGA E INSTALACIÓN SEGURA CON MANEJO DE ERRORES ---
    try {
        Write-Host "[INFO] Iniciando descarga del nuevo empaquetado desde GitHub..." -ForegroundColor Cyan

        # Forzar el uso de protocolos TLS seguros para la descarga en servidores antiguos
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

        # Headers específicos para descargar el binario (Octet-Stream) desde los assets de GitHub
        $downloadHeaders = @{
            "Authorization" = "token $token"
            "Accept"        = "application/octet-stream"
        }

        # Descarga directa
        Invoke-WebRequest -Uri $asset.url -Headers $downloadHeaders -OutFile $jarDestino -ErrorAction Stop

        # Verificación de integridad: un HTTP 200 no garantiza que el contenido descargado sea
        # el JAR correcto (ver comentario junto a $assetChecksum, paso 1). Comparación de string
        # en PowerShell es case-insensitive por default, así que no hace falta normalizar mayúsculas
        # entre el hex en minúscula de sha256sum y el que devuelve Get-FileHash.
        $hashEsperado = ((Invoke-RestMethod -Uri $assetChecksum.url -Headers $downloadHeaders -ErrorAction Stop) -split '\s+')[0]
        $hashCalculado = (Get-FileHash -Path $jarDestino -Algorithm SHA256).Hash
        if ($hashCalculado -ne $hashEsperado) {
            throw "El checksum del JAR descargado ($hashCalculado) no coincide con el publicado ($hashEsperado)."
        }

        # Si todo salió bien, eliminamos el backup viejo para limpiar el directorio
        if (Test-Path $jarBackup) {
            Remove-Item $jarBackup -Force -ErrorAction SilentlyContinue
        }
        Write-Host "[SUCCESS] Sistema actualizado con éxito a la versión $tagNuevaVersion." -ForegroundColor Green

    } catch {
        Write-Host "[ERROR] La descarga falló, se interrumpió, o el checksum no coincide: $_" -ForegroundColor Red
        Write-Host "[INFO] Iniciando proceso de Rollback para restaurar la versión anterior..." -ForegroundColor Yellow

        # Si se llegó a crear un archivo corrupto o parcial a mitad de la descarga, lo removemos
        if (Test-Path $jarDestino) {
            Remove-Item $jarDestino -Force -ErrorAction SilentlyContinue
        }

        # Restauramos el archivo .bak a su nombre original para que el cliente no se quede sin sistema
        if (Test-Path $jarBackup) {
            Rename-Item -Path $jarBackup -NewName (Split-Path $jarDestino -Leaf) -Force
            Write-Host "[SUCCESS] Rollback completado. La versión anterior fue restaurada con éxito." -ForegroundColor Green
        } else {
            Write-Host "[CRITICAL] No se encontró el archivo de respaldo para restaurar." -ForegroundColor Red
        }
    }
} finally {
    # Se libera pase lo que pase (éxito, error, o cualquiera de los Exit de arriba): un bloqueo
    # sin liberar dejaría tanto a la tarea programada como al botón manual bloqueados para siempre.
    Remove-Item $jarLock -Force -ErrorAction SilentlyContinue
    Stop-Transcript
}
