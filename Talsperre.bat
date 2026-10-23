@echo off
cd /d "%~dp0"

set JAR_PATH=dist

if not exist "%JAR_PATH%\Talsperre.jar" (
    echo Fehler: Talsperre.jar nicht gefunden!
    timeout /t 5
    exit /b 1
)

start "" java -jar "%JAR_PATH%\Talsperre.jar"
exit
