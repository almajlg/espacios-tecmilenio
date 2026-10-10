@echo off
chcp 65001 > nul
echo ===================================================================
echo     CAMPUS ESPACIOS - SISTEMA DE AGENDAMIENTO UNIVERSITARIO
echo ===================================================================
echo.
echo 1. Compilando el backend en Java...
cd /d "%~dp0backend"
if not exist "bin" mkdir bin
javac -encoding UTF-8 -d bin -sourcepath src src/com/universidad/espacios/Main.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Ocurrio un error al compilar el proyecto Java.
    pause
    exit /b %ERRORLEVEL%
)

echo [OK] Compilacion completada con exito.
echo.
echo 2. Iniciando servidor HTTP en puerto 8080...
echo Abra su navegador en: http://localhost:8080
echo.
java -Dfile.encoding=UTF-8 -cp bin com.universidad.espacios.Main
pause
