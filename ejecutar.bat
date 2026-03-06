@echo off
setlocal

cd /d "C:\Sistema\app"

if not exist "aptium.jar" (
    echo Error: No se encuentra C:\Sistema\app\aptium.jar
    pause
    exit /b 1
)

java -jar "aptium.jar"

if errorlevel 1 (
    echo.
    echo La aplicacion termino con error. Revisar logs.
    pause
    exit /b 1
)

endlocal