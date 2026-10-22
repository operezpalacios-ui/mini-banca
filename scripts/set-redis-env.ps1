# Lección 11: añade (o sustituye) REDIS_URL en el .env SIN mostrar la contraseña en pantalla.
# Uso (desde C:\dev\mini-banca):
#   powershell -ExecutionPolicy Bypass -File .\scripts\set-redis-env.ps1
#
# Pega lo que te da Upstash. Vale cualquiera de estas formas (el script busca la URL dentro):
#   rediss://default:CONTRASEÑA@algo.upstash.io:6379
#   redis-cli --tls -u redis://default:CONTRASEÑA@algo.upstash.io:6379
# El resto de líneas del .env (BBDD, JWT, clave interna) NO se tocan.

$secure = Read-Host "Pega la URL de Upstash (se verá con asteriscos)" -AsSecureString
$texto = [System.Net.NetworkCredential]::new('', $secure).Password

if ($texto -notmatch "rediss?://[^\s'`"]+") {
    Write-Host "No encuentro ninguna URL redis:// o rediss:// en lo que has pegado. No he cambiado nada." -ForegroundColor Red
    exit 1
}
$url = $Matches[0]
# Upstash SIEMPRE exige conexión cifrada: forzamos "rediss" (con dos eses) aunque el comando diga "redis"
$url = $url -replace '^redis://', 'rediss://'

$u = [Uri]($url -replace '^rediss://', 'http://')
if (-not $u.UserInfo -or -not $u.UserInfo.Contains(':')) {
    Write-Host "La URL no lleva usuario:contraseña (default:...). Copia la URL completa de Upstash." -ForegroundColor Red
    exit 1
}

$envPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
$lineas = @()
if (Test-Path $envPath) {
    $lineas = Get-Content $envPath | Where-Object { $_ -notmatch '^\s*REDIS_URL\s*=' }
}
$lineas += "REDIS_URL=$url"
Set-Content $envPath -Encoding ascii -Value $lineas

Write-Host "Listo: REDIS_URL guardada en $envPath para el servidor $($u.Host) (puerto $($u.Port))." -ForegroundColor Green
Write-Host "Las demás líneas del .env siguen igual." -ForegroundColor Green
