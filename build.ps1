param(
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = "Stop"

if (-not $JdkHome -or -not (Test-Path "$JdkHome\bin\javac.exe")) {
    Write-Error "Define JAVA_HOME apuntando a un JDK 8+ o pasalo con -JdkHome"
}

$Javac = "$JdkHome\bin\javac.exe"
$Jar   = "$JdkHome\bin\jar.exe"

$Root      = $PSScriptRoot
$Src       = "$Root\src"
$Lib       = "$Root\WebContent\WEB-INF\lib"
$Classes   = "$Root\WebContent\WEB-INF\classes"
$DistDir   = "$Root\dist"
$ClientDir = "$DistDir\cliente"

# Limpiar
Remove-Item -Recurse -Force $Classes, $DistDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Classes, $DistDir, $ClientDir, "$ClientDir\lib" | Out-Null

# Classpath con todos los jars de Jersey
$Cp = (Get-ChildItem "$Lib\*.jar" | ForEach-Object { $_.FullName }) -join ";"

# Compilar todas las fuentes
Write-Host "[1/3] Compilando..." -ForegroundColor Cyan
$Sources = Get-ChildItem -Recurse -Path $Src -Filter *.java | ForEach-Object { $_.FullName }
& $Javac -classpath $Cp -d $Classes @Sources
if ($LASTEXITCODE -ne 0) { throw "Fallo compilacion" }

# Empaquetar WAR (servidor)
Write-Host "[2/3] Generando PBFT.war..." -ForegroundColor Cyan
Push-Location "$Root\WebContent"
& $Jar -cf "$DistDir\PBFT.war" .
Pop-Location

# Empaquetar JAR cliente (Interfaz)
Write-Host "[3/3] Generando cliente..." -ForegroundColor Cyan
$Manifest = "$DistDir\manifest.txt"
"Main-Class: obligatoria.Interfaz`r`n" | Out-File -Encoding ASCII $Manifest
& $Jar -cfm "$ClientDir\Interfaz.jar" $Manifest -C $Classes obligatoria/Interfaz.class
Copy-Item "$Lib\*.jar" "$ClientDir\lib\"

# run.bat para el cliente
@"
@echo off
java -cp "Interfaz.jar;lib\*" obligatoria.Interfaz
"@ | Out-File -Encoding ASCII "$ClientDir\run.bat"

Write-Host ""
Write-Host "Listo. Resultado en $DistDir" -ForegroundColor Green
Write-Host "  - PBFT.war        -> copiar a <tomcat>\webapps\ en cada PC servidor"
Write-Host "  - cliente\run.bat -> ejecutar en el PC cliente"
