# Leccion 8: crea core/http/http-client.private.env.json con la clave interna del .env,
# para poder seguir llamando al core directamente desde los ficheros .http de IntelliJ.
# Ese fichero esta en .gitignore: NUNCA se sube a Git.
# Uso (desde C:\dev\mini-banca):
#   powershell -ExecutionPolicy Bypass -File .\scripts\generar-http-env.ps1

$raiz = Split-Path $PSScriptRoot -Parent
$linea = Get-Content (Join-Path $raiz '.env') | Where-Object { $_ -like 'INTERNAL_API_KEY=*' } | Select-Object -First 1
if (-not $linea) {
    Write-Host "No encuentro INTERNAL_API_KEY en el .env. Haz antes el paso 1 de la leccion 8." -ForegroundColor Red
    exit 1
}
$clave = $linea.Substring('INTERNAL_API_KEY='.Length).Trim()
$json = @{ dev = @{ internalKey = $clave } } | ConvertTo-Json
$destino = Join-Path $raiz 'core\http\http-client.private.env.json'
Set-Content -Path $destino -Value $json -Encoding UTF8
Write-Host "Listo: $destino creado. En IntelliJ elige el entorno 'dev' en 'Run with'." -ForegroundColor Green
