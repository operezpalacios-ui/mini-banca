// =====================================================================================
// Lección 16: la MISMA idea que .github/workflows/ci.yml, pero para JENKINS (CI + CD).
//
//   CI  -> tests de los 3 proyectos (en paralelo) + buscar secretos + construir y publicar imágenes
//   CD  -> desplegar en PRUEBAS -> comprobar que funciona -> pedir APROBACIÓN -> desplegar en PRODUCCIÓN
//
// Jenkins lee este fichero (Groovy) de la raíz del repositorio. Funciona en agentes Linux (lo normal
// en una empresa) y también en tu Windows: los pasos que necesitan Docker se SALTAN si no hay Docker.
//
// Para volver atrás (ROLLBACK): "Build with Parameters" y escribe en VERSION la etiqueta de una
// versión anterior. Se salta los tests y la construcción y despliega directamente esa imagen.
// =====================================================================================

// ---------- Funciones de ayuda ----------

/** Ejecuta un comando con "sh" en Linux/Mac o con "bat" en Windows. */
def ejecutar(String comando) {
    if (isUnix()) {
        sh comando
    } else {
        bat comando
    }
}

/** ¿Hay Docker en esta máquina? (en tu portátil no; en un agente de la empresa, sí) */
def hayDocker() {
    if (isUnix()) {
        return sh(script: 'docker info > /dev/null 2>&1', returnStatus: true) == 0
    }
    return bat(script: '@docker info >NUL 2>&1', returnStatus: true) == 0
}

/** ¿Estamos en la rama principal? (GIT_BRANCH vale "origin/master") */
def esRamaPrincipal() {
    def rama = env.GIT_BRANCH ?: ''
    return rama.endsWith('master') || rama.endsWith('main')
}

/**
 * DESPLEGAR un entorno con docker compose usando las IMÁGENES ya construidas y probadas.
 * Los secretos de cada entorno (JWT_SECRET, INTERNAL_API_KEY, LOCAL_DB_PASSWORD...) NO están en Git:
 * son un fichero .env guardado como "credencial" en Jenkins (Manage Jenkins -> Credentials),
 * con el id "minibanca-pruebas-env" o "minibanca-produccion-env".
 */
def desplegar(String entorno, String puerto) {
    withCredentials([file(credentialsId: "minibanca-${entorno}-env", variable: 'ENV_FILE')]) {
        withEnv(["WEB_PORT=${puerto}"]) {
            sh """
                set -e
                FICHEROS="-p minibanca-${entorno} --env-file \$ENV_FILE -f docker-compose.yml -f docker-compose.despliegue.yml"
                docker compose \$FICHEROS pull
                docker compose \$FICHEROS up -d --no-build --wait --wait-timeout 300
            """
            // Prueba de humo DESPUÉS de desplegar: si el sistema no responde como debe, la pipeline
            // falla y NO se pasa a producción.
            sh """
                set -e
                test "\$(curl -s -o /dev/null -w '%{http_code}' http://localhost:${puerto}/api/v1/cuentas)" = "401"
                curl -fsS http://localhost:${puerto}/ | grep -q '<app-root>'
                echo "✅ ${entorno} desplegado y respondiendo en el puerto ${puerto} (versión \$TAG)"
            """
        }
    }
}

pipeline {
    agent any

    parameters {
        string(name: 'VERSION', defaultValue: '',
               description: 'Vacío = construir y desplegar este commit. Pon una etiqueta anterior para hacer ROLLBACK.')
    }

    options {
        timestamps()                                     // hora en cada línea del log
        disableConcurrentBuilds()                        // nunca dos despliegues a la vez
        buildDiscarder(logRotator(numToKeepStr: '20'))   // guardar solo las 20 últimas ejecuciones
        timeout(time: 2, unit: 'HOURS')                  // incluye el tiempo esperando la aprobación
    }

    environment {
        REGISTRO = 'ghcr.io/operezpalacios-ui'           // dónde se publican las imágenes
    }

    stages {
        stage('Preparar') {
            steps {
                script {
                    def elegida = params.VERSION?.trim()
                    env.ROLLBACK = elegida ? 'true' : 'false'
                    // La etiqueta de la imagen = los 12 primeros caracteres del commit:
                    // así siempre sabemos EXACTAMENTE qué código hay desplegado.
                    env.TAG = elegida ?: (env.GIT_COMMIT ? env.GIT_COMMIT.substring(0, 12) : 'local')
                    env.CON_DOCKER = hayDocker() ? 'true' : 'false'
                    echo "Versión: ${env.TAG} | Rollback: ${env.ROLLBACK} | Docker: ${env.CON_DOCKER} | Rama: ${env.GIT_BRANCH}"
                }
            }
        }

        // ---------------------------------------------------------------- CI
        stage('Tests') {
            when { environment name: 'ROLLBACK', value: 'false' }
            parallel {
                stage('Core') {
                    steps {
                        dir('core') {
                            ejecutar(isUnix() ? 'sh ./mvnw -B verify' : 'mvnw.cmd -B verify')
                        }
                    }
                    // junit: Jenkins lee los informes de los tests y enseña gráficas y fallos
                    post { always { junit allowEmptyResults: true, testResults: 'core/target/surefire-reports/*.xml' } }
                }
                stage('Orquestador') {
                    steps {
                        dir('orquestador') {
                            ejecutar(isUnix() ? 'sh ./mvnw -B verify' : 'mvnw.cmd -B verify')
                        }
                    }
                    post { always { junit allowEmptyResults: true, testResults: 'orquestador/target/surefire-reports/*.xml' } }
                }
                stage('Frontend') {
                    steps {
                        dir('frontend') {
                            ejecutar('npm ci')
                            ejecutar('npx ng test --watch=false')
                            ejecutar('npm run build')
                        }
                    }
                }
            }
        }

        stage('Buscar secretos (gitleaks)') {
            when { allOf { environment name: 'ROLLBACK', value: 'false'; environment name: 'CON_DOCKER', value: 'true' } }
            steps {
                sh 'docker run --rm -v "$PWD":/repo ghcr.io/gitleaks/gitleaks:latest git /repo --config /repo/.gitleaks.toml -v'
            }
        }

        stage('Construir y publicar imágenes') {
            when {
                allOf {
                    environment name: 'ROLLBACK', value: 'false'
                    environment name: 'CON_DOCKER', value: 'true'
                    expression { esRamaPrincipal() }
                }
            }
            steps {
                // Usuario y token del registro guardados como credencial "ghcr" en Jenkins
                withCredentials([usernamePassword(credentialsId: 'ghcr', usernameVariable: 'USUARIO', passwordVariable: 'TOKEN')]) {
                    sh '''
                        set -e
                        echo "$TOKEN" | docker login ghcr.io -u "$USUARIO" --password-stdin
                        for s in core orquestador frontend; do
                            docker build -t "$REGISTRO/mini-banca-$s:$TAG" "$s"
                            docker push "$REGISTRO/mini-banca-$s:$TAG"
                        done
                    '''
                }
            }
        }

        // ---------------------------------------------------------------- CD
        stage('Desplegar en PRUEBAS') {
            when { allOf { environment name: 'CON_DOCKER', value: 'true'; expression { esRamaPrincipal() } } }
            steps { script { desplegar('pruebas', '8091') } }   // cada entorno en su puerto
        }

        stage('Aprobación') {
            when { allOf { environment name: 'CON_DOCKER', value: 'true'; expression { esRamaPrincipal() } } }
            steps {
                // La pipeline se PARA aquí hasta que una persona pulse el botón (o pasa 1 hora).
                // Antes de aprobar, esa persona prueba la versión en el entorno de pruebas.
                timeout(time: 1, unit: 'HOURS') {
                    input message: "¿Desplegar la versión ${env.TAG} en PRODUCCIÓN?", ok: 'Sí, desplegar'
                }
            }
        }

        stage('Desplegar en PRODUCCIÓN') {
            when { allOf { environment name: 'CON_DOCKER', value: 'true'; expression { esRamaPrincipal() } } }
            steps { script { desplegar('produccion', '80') } }
        }
    }

    post {
        success { echo "✅ Pipeline terminada. Versión ${env.TAG}" }
        failure { echo "❌ Algo ha fallado: mira la etapa en rojo. Producción NO se ha tocado si falló antes de su etapa." }
    }
}
