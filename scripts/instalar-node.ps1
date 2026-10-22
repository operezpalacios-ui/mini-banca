# Lección 10: instala Node.js 24 (LTS) SIN permisos de administrador.
#
# Qué hace:
#   1. Busca en nodejs.org la última versión 24.x (LTS = versión de soporte largo, la estable).
#   2. Descarga el ZIP "portable" (no hay instalador, así que no pide administrador).
#   3. Comprueba la huella SHA-256 del ZIP con la lista oficial: si alguien lo hubiera
#      manipulado por el camino, la huella no coincidiría y el script se para.
#   4. Lo descomprime en  %LOCALAPPDATA%\Programs\nodejs  (tu carpeta de usuario).
#   5. Lo añade a TU Path de usuario (no al del sistema, para eso sí haría falta admin).
#
# Uso (desde C:\dev\mini-banca):
#   powershell -ExecutionPolicy Bypass -File .\scripts\instalar-node.ps1

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # sin la barra de progreso, la descarga va MUCHO más rápida
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$base = Join-Path $env:LOCALAPPDATA 'Programs\nodejs'
$arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'arm64' } else { 'x64' }

Write-Host "Buscando la última versión 24 LTS de Node.js..."
$ultima = Invoke-RestMethod 'https://nodejs.org/dist/index.json' |
        Where-Object { $_.version -like 'v24.*' -and $_.lts } |
        Select-Object -First 1
if (-not $ultima) { throw "No encuentro ninguna versión 24 LTS en nodejs.org" }
$ver = $ultima.version
$nombre = "node-$ver-win-$arch"
$carpeta = Join-Path $base $nombre
Write-Host "Versión elegida: $ver ($arch)"

if (Test-Path (Join-Path $carpeta 'node.exe')) {
    Write-Host "Ya estaba descargada en $carpeta"
} else {
    $zip = Join-Path $env:TEMP "$nombre.zip"
    Write-Host "Descargando $nombre.zip (unos 30 MB)..."
    Invoke-WebRequest "https://nodejs.org/dist/$ver/$nombre.zip" -OutFile $zip

    Write-Host "Comprobando la huella SHA-256..."
    $sumas = (Invoke-WebRequest "https://nodejs.org/dist/$ver/SHASUMS256.txt" -UseBasicParsing).Content
    $linea = ($sumas -split "`n") | Where-Object { $_ -match "\s$([regex]::Escape("$nombre.zip"))\s*$" } | Select-Object -First 1
    if (-not $linea) { throw "No encuentro la huella oficial de $nombre.zip" }
    $esperada = ($linea.Trim() -split '\s+')[0].ToLower()
    $real = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
    if ($real -ne $esperada) {
        Remove-Item $zip -Force
        throw "¡La huella NO coincide! El fichero descargado no es el oficial. No se instala nada."
    }
    Write-Host "Huella correcta."

    Write-Host "Descomprimiendo en $base ..."
    New-Item -ItemType Directory -Force -Path $base | Out-Null
    # tar viene con Windows 10/11 y es mucho más rápido que Expand-Archive
    tar -xf $zip -C $base
    if ($LASTEXITCODE -ne 0) { Expand-Archive $zip -DestinationPath $base -Force }
    Remove-Item $zip -Force
}

# Path de USUARIO: quitamos versiones anteriores instaladas por este script y ponemos la nueva
$pathUsuario = [Environment]::GetEnvironmentVariable('Path', 'User')
$partes = @()
if ($pathUsuario) {
    $partes = $pathUsuario -split ';' | Where-Object { $_ -and ($_ -notlike "$base\node-*") }
}
$partes = @($carpeta) + $partes
[Environment]::SetEnvironmentVariable('Path', ($partes -join ';'), 'User')
$env:Path = "$carpeta;$env:Path"   # y en ESTA ventana, para probarlo ya

Write-Host ""
Write-Host "Node instalado:"
& (Join-Path $carpeta 'node.exe') -v
Write-Host "npm:"
& (Join-Path $carpeta 'npm.cmd') -v
Write-Host ""
Write-Host "LISTO. Cierra y vuelve a abrir PowerShell (y el terminal de IntelliJ) para que vean el Path nuevo."
