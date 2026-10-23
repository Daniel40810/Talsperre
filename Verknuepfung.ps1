# Automatisch generiert
$ErrorActionPreference = 'Stop'
$root = "C:\Users\Dan40810\Documents\NetBeansProjects\Talsperre"
$jar  = "C:\Users\Dan40810\Documents\NetBeansProjects\Talsperre\dist\Talsperre.jar"
$ico  = "C:\Users\Dan40810\Documents\NetBeansProjects\Talsperre\icon\talsperre.ico"

if (-not (Test-Path $jar)) { throw "JAR fehlt: $jar" }
if (-not (Test-Path $ico)) { throw "ICO fehlt: $ico" }

$javaw = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javaw.exe'))) {
    $javaw = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
} else {
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javaw = $cmd.Source }
}
if (-not $javaw) { throw "javaw.exe nicht gefunden - JAVA_HOME setzen oder Java in den PATH aufnehmen." }

$desktop = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop "Talsperre.lnk"

$sh = New-Object -ComObject WScript.Shell
$s = $sh.CreateShortcut($lnk)
$s.TargetPath       = $javaw
$s.Arguments        = '-Xmx4g -jar "' + $jar + '"'
$s.WorkingDirectory = $root
$s.IconLocation     = $ico
$s.Description      = "Talsperre - Java Anwendung"
$s.Save()

Write-Host "Verknuepfung angelegt: $lnk"
