@echo off
setlocal enabledelayedexpansion

REM Cambiar al directorio del script
cd /d "%~dp0"

REM Ejecutar el JAR
java -jar "target\Administracion-Aptium-1.0-SNAPSHOT-jar-with-dependencies.jar"

REM Mantener la ventana abierta si hay error
if errorlevel 1 (
    echo.
    echo Error: Verifique que Java esté instalado correctamente.
    pause
)
