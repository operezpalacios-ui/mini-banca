# Lección 16: arranca un Jenkins DE PRUEBA en tu ordenador, sin administrador y sin instalar nada.
# Jenkins es un programa Java: un solo fichero (jenkins.war) que se ejecuta con "java -jar".
#
# Uso (desde C:\dev\mini-banca):
#   powershell -ExecutionPolicy Bypass -File .\scripts\arrancar-jenkins.ps1
# Luego abre http://localhost:8090 en el navegador. Para pararlo: Ctrl + C en esta ventana.
#
# Todo se guarda en C:\dev\tools\jenkins (fuera del proyecto: nunca se sube a Git).

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$base = 'C:\dev\tools\jenkins'
$war = Join-Path $base 'jenkins.war'
New-Item -ItemType Directory -Force -Path $base | Out-Null

# 1. Buscar Java: JAVA_HOME si existe; si no, el JDK más nuevo que descargó IntelliJ (~\.jdks)
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $jdk = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
    if (-not $jdk) { throw "No encuentro Java. Indica JAVA_HOME o descarga un JDK desde IntelliJ." }
    $env:JAVA_HOME = $jdk.FullName
}
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
Write-Host "Java: $env:JAVA_HOME"

# 2. Descargar Jenkins (versión LTS = estable) la primera vez, comprobando su huella SHA-256
if (-not (Test-Path $war)) {
    Write-Host "Descargando Jenkins LTS (unos 90 MB)..."
    Invoke-WebRequest 'https://get.jenkins.io/war-stable/latest/jenkins.war' -OutFile $war
    try {
        $esperada = ((Invoke-WebRequest 'https://get.jenkins.io/war-stable/latest/jenkins.war.sha256' -UseBasicParsing).Content.Trim() -split '\s+')[0].ToLower()
        $real = (Get-FileHash $war -Algorithm SHA256).Hash.ToLower()
        if ($esperada -ne $real) { Remove-Item $war; throw "La huella de jenkins.war NO coincide. No lo uso." }
        Write-Host "Huella SHA-256 correcta."
    } catch [System.Net.WebException] {
        Write-Host "Aviso: no he podido descargar la huella para comprobarla." -ForegroundColor Yellow
    }
}

# 3. Arrancar
$env:JENKINS_HOME = Join-Path $base 'home'
Write-Host ""
Write-Host "Arrancando Jenkins en http://localhost:8090  (la primera vez tarda 1-2 minutos)" -ForegroundColor Green
Write-Host "La contraseña inicial saldrá aquí abajo entre asteriscos y está en:"
Write-Host "  $env:JENKINS_HOME\secrets\initialAdminPassword"
Write-Host ""
# --httpPort=8090: el 8080 es del core y el 8081 del orquestador
# ALLOW_LOCAL_CHECKOUT: deja a Jenkins leer el repositorio de tu disco (file:///C:/dev/mini-banca)
# --enable-future-java: permite usar una versión de Java más nueva que las que Jenkins tiene probadas
& $java "-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true" -jar $war --httpPort=8090 --enable-future-java
