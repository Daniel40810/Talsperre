@echo off
setlocal enabledelayedexpansion

REM Projektordner
set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"

REM Projektname = Ordnername
for %%A in ("%ROOT%") do set "PROJECT=%%~nxA"

REM JAR suchen
set "JAR="
for %%A in ("%ROOT%\dist\*.jar") do set "JAR=%%~fA"
if "%JAR%"=="" (
    echo Keine JAR-Datei im dist-Ordner gefunden.
    pause
    exit /b 1
)

REM ICO suchen
set "ICO="
for %%A in ("%ROOT%\icon\*.ico") do set "ICO=%%~fA"
if "%ICO%"=="" (
    echo Keine ICO-Datei im icon-Ordner gefunden.
    pause
    exit /b 1
)

REM Verknuepfung.ps1 erzeugen
> "%ROOT%\Verknuepfung.ps1" (
    echo # Automatisch generiert
    echo $ErrorActionPreference = 'Stop'
    echo $root = "%ROOT%"
    echo $jar  = "%JAR%"
    echo $ico  = "%ICO%"
    echo.
    echo if ^(-not ^(Test-Path $jar^)^) { throw "JAR fehlt: $jar" }
    echo if ^(-not ^(Test-Path $ico^)^) { throw "ICO fehlt: $ico" }
    echo.
    echo $javaw = $null
    echo if ^($env:JAVA_HOME -and ^(Test-Path ^(Join-Path $env:JAVA_HOME 'bin\javaw.exe'^)^)^) {
    echo ^    $javaw = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
    echo } else {
    echo ^    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    echo ^    if ^($cmd^) { $javaw = $cmd.Source }
    echo }
    echo if ^(-not $javaw^) { throw "javaw.exe nicht gefunden - JAVA_HOME setzen oder Java in den PATH aufnehmen." }
    echo.
    echo $desktop = [Environment]::GetFolderPath^('Desktop'^)
    echo $lnk = Join-Path $desktop "%PROJECT%.lnk"
    echo.
    echo $sh = New-Object -ComObject WScript.Shell
    echo $s = $sh.CreateShortcut^($lnk^)
    echo $s.TargetPath       = $javaw
    echo $s.Arguments        = '-Xmx4g -jar "' + $jar + '"'
    echo $s.WorkingDirectory = $root
    echo $s.IconLocation     = $ico
    echo $s.Description      = "%PROJECT% - Java Anwendung"
    echo $s.Save^(^)
    echo.
    echo Write-Host "Verknuepfung angelegt: $lnk"
)

REM Verknuepfung.bat erzeugen
> "%ROOT%\Verknuepfung.bat" (
    echo @echo off
    echo rem Legt auf dem Desktop eine Verknuepfung "%PROJECT%" mit dem Programmsymbol an.
    echo cd /d "%%~dp0"
    echo powershell -NoProfile -ExecutionPolicy Bypass -File "%%~dp0Verknuepfung.ps1"
    echo if errorlevel 1 ^(
    echo ^  echo.
    echo ^  echo Die Verknuepfung konnte nicht angelegt werden - siehe Meldung oben.
    echo ^)
    echo pause
)

echo.
echo Dateien erfolgreich erzeugt:
echo   Verknuepfung.ps1
echo   Verknuepfung.bat
echo.
pause
