# Genera el fichero .env a partir de la cadena de conexion de Neon.
# Uso (desde C:\dev\mini-banca):
#   powershell -ExecutionPolicy Bypass -File .\scripts\set-neon-env.ps1
# Lo que pegues se muestra con asteriscos: la contrasena nunca aparece en pantalla.

$secure = Read-Host "Pega la cadena de conexion de Neon (se vera con asteriscos)" -AsSecureString
$texto = [System.Net.NetworkCredential]::new('', $secure).Password

if ($texto -notmatch "postgres(ql)?://[^\s'`"]+") {
    Write-Host "No encuentro ninguna cadena postgresql:// en lo que has pegado. No he cambiado nada." -ForegroundColor Red
    exit 1
}

$u = [Uri]($Matches[0] -replace '^postgres(ql)?://', 'http://')
$user, $pass = $u.UserInfo.Split(':', 2)
$db = $u.AbsolutePath.TrimStart('/')

$envPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
Set-Content $envPath -Encoding ascii -Value @(
    "DB_URL=jdbc:postgresql://$($u.Host)/$($db)?sslmode=require",
    "DB_USER=$user",
    "DB_PASSWORD=$([Uri]::UnescapeDataString($pass))"
)
Write-Host "Listo: $envPath actualizado para $($u.Host)" -ForegroundColor Green
