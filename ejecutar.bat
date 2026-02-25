@echo off
REM Detectar entorno: desarrollo o producción
set "JAR_PATH="
if exist "C:\Aptium\app\aptium.jar" (
    set "JAR_PATH=C:\Aptium\app\aptium.jar"
    cd /d "C:\Aptium\app"
)
if exist "target\aptium.jar" (
    set "JAR_PATH=target\aptium.jar"
    cd /d "%~dp0"
)
if not defined JAR_PATH (
    echo Error: No se encuentra aptium.jar ni en C:\Aptium\app ni en target\aptium.jar
    echo Compile el proyecto con mvn package y verifique la ubicación del JAR.
    pause
    exit /b 1
)

REM Ejecutar el JAR
java -jar "%JAR_PATH%"

REM Mostrar logs de error si falla
if errorlevel 1 (
    echo.
    echo Error: Verifique que Java esté instalado correctamente o revise C:\Logs\Aptium\error.log
    pause
)
