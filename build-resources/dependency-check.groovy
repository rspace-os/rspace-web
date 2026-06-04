#!groovy
@Library('rspace-shared') _

pipeline {
    agent any

    triggers {
        cron(env.BRANCH_NAME == 'main' ? 'H 9 * * *' : '')
    }

    options { disableConcurrentBuilds() }

    environment {
        APP_VERSION = readMavenPom().getVersion()
    }

    stages {
        stage ('Package Java') {
            steps {
                echo "Analysing branch"
                sh '''
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                    echo "Using Java: `java -version`"
                    echo "App version: ${APP_VERSION}"
                '''
                /* '-DreactDevMode=true' is a temporary fix to omit npm build part 
                   which that generates node_modules that are unnecessarily scanned */
                sh 'mvn clean package -Dmaven.test.skip=true -DreactDevMode=true'
            }
        }

        stage ('Dependency Check') {
            steps {
                withCredentials([string(credentialsId: 'dbd7e93e-36f3-4ca0-8f01-2b142585abcc', variable: 'NVD_API_KEY')]) {

                    /* Fail fast if a scan input is missing. dependency-check completes
                       successfully with an empty report when --scan matches nothing, and the
                       publisher's failedNew* thresholds are baseline-relative, so an empty scan
                       passes green and coverage silently disappears. Guard the inputs explicitly. */
                    sh '''
                        ls ./target/*.war
                        test -f ./src/main/webapp/ui/package-lock.json
                    '''

                    /* Scan the specific dependency sources, not the whole tree (which double-counts):
                       - './target/*.war' is what ships to production. It carries the runtime Java
                         deps (WEB-INF/lib) and the vendored/legacy JS bundled as static resources,
                         so RetireJS still catches those (e.g. lodash.js). Scanning the WAR alone
                         avoids reporting each dependency twice (once from the WAR, once from the
                         on-disk exploded/source copy under './').
                       - './src/main/webapp/ui/package-lock.json' is the source of truth for the
                         modern React/TS npm dependency tree, so scan it directly. An accurate
                         frontend dependency scan reads the lockfile, not the bundled build output.
                         --nodeAuditSkipDevDependencies keeps coverage to deps that ship to
                         production, mirroring how the WAR excludes test-scope Java deps. */
                    dependencyCheck additionalArguments: '''
                    --nvdApiKey ${NVD_API_KEY}
                    -o './'
                    -s './target/*.war'
                    -s './src/main/webapp/ui/package-lock.json'
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

def notifySlackOfFailure() {
    def info = "OWASP dependency check has failed due to new critical or high level vulnerabilities. Check the report here:"
    def msg = "FAILURE: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${info}\n${env.BUILD_URL}\n"

    slackSend(color: '#FF9FA1', message: msg)
}
