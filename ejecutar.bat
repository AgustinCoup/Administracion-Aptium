@echo off
setlocal

cd /d "C:\Sistema\app"

if not exist "aptium.jar" (
    echo Error: No se encuentra C:\Sistema\app\aptium.jar
    pause
    exit /b 1
)

where javaw >nul 2>&1
if errorlevel 1 (
    echo Error: no se encuentra javaw.exe en el PATH. Instalar Java 17 o superior.
    pause
    exit /b 1
)

REM javaw = sin ventana de consola. Los logs van igual a logs\app.log (el cd /d de
REM arriba es lo que los deja al lado del JAR). El "start" hace que este .bat
REM termine en el acto en vez de quedarse esperando a la app con la consola abierta.
REM Un fallo de arranque lo avisa la propia app con un dialogo y sale con codigo 1,
REM asi que no se pierde nada por no poder leer el errorlevel aca.
start "" javaw -jar "aptium.jar"

endlocal
