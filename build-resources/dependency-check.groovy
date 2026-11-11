#!groovy
@Library('rspace-shared') _

pipeline {
    agent any

    triggers {
        cron(env.BRANCH_NAME == 'main' ? 'H 9 * * *' : '')
    }

    options { disableConcurrentBuilds() }

    parameters {
        booleanParam(name: 'DEPENDENCY_CHECK', defaultValue: false, description: 'Run dependency check on a non-main branch (always runs on main)')
    }

    environment {
        APP_VERSION = gradleBaseVersion(readFile('build.gradle'))
        // Gradle cannot read the non-ASCII test resource file names under a POSIX/ASCII locale
        LANG = 'C.UTF-8'
    }

    stages {
        stage ('Package Java') {
            when {
                anyOf {
                    branch 'main'
                    expression { return params.DEPENDENCY_CHECK }
                }
            }
            steps {
                echo "Analysing branch"
                sh '''
                    echo "PATH = ${PATH}"
                    echo "App version: ${APP_VERSION}"
                    ./gradlew --version
                '''
                /* we build the package, to figure actually-bundled runtime Java deps (WEB-INF/lib)
                   and the vendored/legacy non-react JS bundled as static resources */
                sh './gradlew clean war'
            }
        }

        stage ('Dependency Check') {
            when {
                anyOf {
                    branch 'main'
                    expression { return params.DEPENDENCY_CHECK }
                }
            }
            steps {
                withCredentials([string(credentialsId: 'dbd7e93e-36f3-4ca0-8f01-2b142585abcc', variable: 'NVD_API_KEY')]) {

                    /* Fail fast if a scan input is missing. dependency-check completes
                       successfully with an empty report when --scan matches nothing, and the
                       publisher's failedNew* thresholds are baseline-relative, so an empty scan
                       passes green and coverage silently disappears. Guard the inputs explicitly. */
                    sh '''
                        ls ./build/libs/*.war
                        test -f ./pnpm-lock.yaml
                    '''

                    /* Scan the specific dependency sources, not the whole tree (which double-counts):
                       - './build/libs/*.war' is what ships to production. It carries the runtime Java
                         deps (WEB-INF/lib) and the vendored/legacy JS bundled as static resources,
                         so RetireJS still catches those (e.g. lodash.js). Scanning the WAR alone
                         avoids reporting each dependency twice (once from the WAR, once from the
                         on-disk exploded/source copy under './').
                       - './pnpm-lock.yaml' is the source of truth for the modern React/TS
                         frontend dependency tree, so scan it directly. An accurate frontend
                         dependency scan reads the lockfile, not the bundled build output.
                         --nodeAuditSkipDevDependencies keeps coverage to deps that ship to
                         production, mirroring how the WAR excludes test-scope Java deps. */
                    dependencyCheck additionalArguments: '''
                    --nvdApiKey ${NVD_API_KEY}
                    --nvdApiDelay 2000
                    --nvdMaxRetryCount 10                    
                    -o './'
                    -s './build/libs/*.war'
                    -s './pnpm-lock.yaml'
                    --nodeAuditSkipDevDependencies
                    -f 'XML'
                    --prettyPrint''', odcInstallation: 'OWASP'
                    dependencyCheckPublisher pattern: 'dependency-check-report.xml', failedNewCritical: 1, failedNewHigh: 1
                }
            }
        }
    }

    post {
        failure {
            notifySlackOfFailure()
        }
    }
}

// The version declared in build.gradle (e.g. '2.29.0-SNAPSHOT'), without the per-build timestamp suffix.
@NonCPS
def gradleBaseVersion (String buildGradle) {
    def m = buildGradle =~ /(?m)^version\s*=\s*["']([^"'$]*)/
    return m.find() ? m.group(1).replaceAll(/-$/, '') : 'unknown'
}

def notifySlackOfFailure() {
    def info = "OWASP dependency check has failed due to new critical or high level vulnerabilities. Check the report here:"
    def msg = "FAILURE: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${info}\n${env.BUILD_URL}\n"

    slackSend(color: '#FF9FA1', message: msg)
}
