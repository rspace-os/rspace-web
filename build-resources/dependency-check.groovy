#!groovy
@Library('rspace-shared') _

pipeline {
    agent any

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
                sh 'mvn clean package -Dmaven.test.skip=true'
            }
        }

        stage ('Dependency Check') {
            steps {
                withCredentials([string(credentialsId: 'dbd7e93e-36f3-4ca0-8f01-2b142585abcc', variable: 'NVD_API_KEY')]) {

                    dependencyCheck additionalArguments: '''
                    --nvdApiKey ${NVD_API_KEY}
                    -o './'
                    -s './'
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
