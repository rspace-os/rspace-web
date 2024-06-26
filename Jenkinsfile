#!groovy

@Library('rspace-shared') _

echo 'Building rspace-web'

/*
 This is the main build script for Jenkins to run tests.
 In Jenkins, it is called from 'rspace-web' project as a multi branch build
 For feature branches, it runs 'quick' JUnit tests
 For master/dev branches, it runs full Java tests (i.e. with IT tests).
 The script takes several parameters; there are other Jenkins jobs to run nightly test-suites using JDK 11 and also
 with 'nightly' tests which are more long running tests.
*/

pipeline {
    agent any

    options { disableConcurrentBuilds() }

    parameters {
        string(name: 'MAVEN_TOOLCHAIN_JAVA_VERSION', defaultValue: '17', description: 'Java version Maven toolchain')
        string(name: 'MAVEN_TOOLCHAIN_JAVA_VENDOR', defaultValue: 'openjdk', description: 'Java vendor Maven toolchain')
        string(name: 'NIGHTLY_BUILD', defaultValue: '', description: 'optional nightly build configuration')
        booleanParam(name: 'AWS_DEPLOY', defaultValue: false, description: 'Deploy branch build to AWS')
        booleanParam(name: 'AWS_DEPLOY_PROD_RELEASE', defaultValue: false, description: 'Deploy main branch build created in prodRelease mode to AWS')
        booleanParam(name: 'DOCKER_AWS_DEPLOY', defaultValue: false, description: 'Deploy branch build to Docker on AWS - see the README in build/ folder for more details')
        booleanParam(name: 'FRONTEND_TESTS', defaultValue: false, description: 'Run Flow/Jest tests (runs after changes to frontend files by default)')
        booleanParam(name: 'FULL_JAVA_TESTS', defaultValue: false, description: 'Run all Java tests (runs on master/develop by default)')
        booleanParam(name: 'LIQUIBASE', defaultValue: false, description: 'Run tests on persistent liquibaseTest database')
    }

    // these are defined in Jenkins global tool configurations. The JDK is the one used to run the Jenkins build, it does
    // not set the maven toolchain, this is set in the ./mvnw command line

    //tools {
        // maven 'maven3.8.1'
        // this is the JDK used to run maven itself
        // the toolchain settings just affect compilation
        //jdk 'OPEN-JDK-11'
    //  }

    environment {
        BUILD_FAILURE_EMAIL_LIST = 'dev@researchspace.com'
        RS_FILE_BASE = "/var/lib/jenkins/userContent/${BRANCH_NAME}-filestore"
        SANITIZED_DBNAME = branchToDbName("${BRANCH_NAME}")
        DOCKER_AMI = 'ami-069082aeb2787a3ba'
        APP_VERSION = readMavenPom().getVersion()
        DOCKERHUB_PWD = credentials('DOCKER_HUB_RSPACEOPS')
        DOCKERHUB_REPO = 'rspaceops/rspace-services'
    }

    stages {
        stage('Initialize') {
            // just echo some info about the build
            steps {
                echo "Building branch - ${BRANCH_NAME}"
                sh '''
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                    echo "Using Java: `java -version`"
                '''
                echo 'Cleaning out filestore'
                sh "rm -rf $RS_FILE_BASE"
                sh "mkdir $RS_FILE_BASE"
                echo "Workspace jenkins var is $WORKSPACE"
            }
        }

        stage('Fast JUnit tests') {
            when {
                not {
                    anyOf {
                        branch 'master'; branch 'develop'
                        expression { return params.FULL_JAVA_TESTS }
                        expression { return params.LIQUIBASE }
                    }
                }
                changeset '**/*.java'
            }

            steps {
                echo 'This is a feature branch, running fast, non Spring tests only'
                sh "./mvnw clean  test -Dfast=true -DRS_FILE_BASE=${RS_FILE_BASE} \
                   -Djava-version=${params.MAVEN_TOOLCHAIN_JAVA_VERSION} \
                   -Djava-vendor=${params.MAVEN_TOOLCHAIN_JAVA_VENDOR}"
            }

            post {
                failure {
                    notify currentBuild.result
                    notifySlack('FAILURE', "Fast JUnit tests failed: ${currentBuild.result}")
                }
                fixed {
                    notify currentBuild.result
                }
                success {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        stage('NPM Install') {
            when {
                anyOf {
                    expression { return params.FRONTEND_TESTS }
                    changeset '**/*.js'
                    changeset '**/*.jsp'
                    changeset '**/*.css'
                    changeset '**/*.json'
                }
            }
            steps {
                dir('src/main/webapp/ui') {
                    echo 'Installing npm packages'
                    sh 'npm ci --force'
                }
            }
        }
        stage('Flow Check') {
            when {
                anyOf {
                    expression { return params.FRONTEND_TESTS }
                    changeset '**/*.js'
                    changeset '**/*.jsp'
                    changeset '**/*.css'
                    changeset '**/*.json'
                }
            }
            steps {
                dir('src/main/webapp/ui') {
                    echo 'Running flow check'
                    sh 'npm run flow check 2>/dev/null | awk \'{ print $0 } /^Found 0 errors$/ { pass = 1 } END { exit !pass }\''
                }
            }
        }
        stage('Jest Tests') {
            when {
                anyOf {
                    expression { return params.FRONTEND_TESTS }
                    changeset '**/*.js'
                    changeset '**/*.jsp'
                    changeset '**/*.css'
                    changeset '**/*.json'
                }
            }
            steps {
                echo 'Running Jest tests'
                dir('src/main/webapp/ui') {
                    sh 'npm run test -- --maxWorkers=2'
                }
            }
            post {
                failure {
                    notify currentBuild.result
                    notifySlack('FAILURE', "Jest tests failed: ${currentBuild.result}")
                }
                fixed {
                    notify currentBuild.result
                }
                success {
                    junit checksName: 'Jest Tests', testResults: '**/ui/junit.xml'
                }
            }
        }
        stage('Build feature branch') {
            when {
                anyOf {
                    expression { return params.AWS_DEPLOY }
                    expression { return params.DOCKER_AWS_DEPLOY }
                    expression { return params.FRONTEND_TESTS }
                    changeset '**/*.js'
                    changeset '**/*.jsp'
                    changeset '**/*.css'
                    changeset '**/*.json'
                }
                not {
                    anyOf {
                        branch 'master'; branch 'develop'
                        expression { return params.FULL_JAVA_TESTS }
                        expression { return params.LIQUIBASE }
                        expression { return params.AWS_DEPLOY_PROD_RELEASE }
                    }
                }
            }
            steps {
                echo 'Building feature branch'
                sh '''
                ./mvnw clean package -DskipTests=true -DgenerateReactDist=clean -DrenameResourcesMD5=true \
                -Denvironment=keepdbintact -Dspring.profiles.active=prod -DRS.logLevel=INFO
                -Djava-version=${MAVEN_TOOLCHAIN_JAVA_VERSION} -Djava-vendor=${MAVEN_TOOLCHAIN_JAVA_VENDOR}
                -Dliquibase.context=run,dev-test -DpropertyFileDirPlaceholder=\\$\\{propertyFileDir\\}
                '''
            }

            post {
                failure {
                    notify currentBuild.result
                    notifySlack('FAILURE', "Feature branch build failed: ${currentBuild.result}")
                }
                fixed {
                    notify currentBuild.result
                }
            }
        }

        stage ('Build prodRelease-like package') {
            when {
                anyOf {
                    expression { return params.AWS_DEPLOY_PROD_RELEASE }
                }
            }
            steps {
                echo "Building prodRelease .war package"
                 sh '''
                 ./mvnw clean package -DskipTests=true -DgenerateReactDist=clean -DrenameResourcesMD5=true \
                -Denvironment=prodRelease -Dspring.profiles.active=prod -DRS.logLevel=WARN -Ddeployment=production \
                -Djava-version=${MAVEN_TOOLCHAIN_JAVA_VERSION} \
                -Djava-vendor=${MAVEN_TOOLCHAIN_JAVA_VENDOR} \
                -DpropertyFileDirPlaceholder=\\$\\{propertyFileDir\\}
                '''
            }

            post {
                failure {
                    notify currentBuild.result
                    notifySlack('FAILURE', "Master build failed: ${currentBuild.result}")
                }
                fixed {
                    notify currentBuild.result
                }
            }
        }

        stage('Deploy feature/prodRelease branch to AWS') {
            when {
                anyOf {
                    expression { return params.AWS_DEPLOY }
                    expression { return params.AWS_DEPLOY_PROD_RELEASE }
                }
                not {
                    anyOf {
                        branch 'master'; branch 'develop'
                    }
                }
            }

            steps {
                build(
                        job: 'aws-deploy',
                        parameters: [
                                [
                                        $class: 'StringParameterValue',
                                        name: 'SERVER_NAME',
                                        value: "$BRANCH_NAME-$BUILD_ID"
                                ],
                                [
                                        $class: 'StringParameterValue',
                                        name: 'WAR',
                                        value: "$WORKSPACE/target/*.war"
                                ],
                                [
                                        $class: 'StringParameterValue',
                                        name: 'DEPLOYMENT_PROPERTY_OVERRIDE',
                                        value: "$WORKSPACE/${BRANCH_NAME}.properties"
                                ]
                        ]
                )
            }
        }
        // currently this requires a feature branch build( to compile the war file correctly for deployment)
        stage('Deploy FeatureBranch to Docker on AWS') {
            when {
                expression { return params.DOCKER_AWS_DEPLOY }
                not {
                    anyOf {
                        branch 'master'; branch 'develop'
                    }
                }
            }
            steps {
                dir('./build/packer/web') {
                    echo 'Building docker image and pushing to DockerHub'
                    sh '''
                   builtWarVersion=$(ls ../../../target | grep war | awk -F ".war" '{print $1}')
                   docker login --username rspaceops --password ${DOCKERHUB_PWD}
                   echo "CWD is $(pwd)"
                   packer build --var app_version="$builtWarVersion" --var docker_image_tag="rspace-web-$BRANCH_NAME" packer-docker.json
                  '''
                }
                build(
                  job: 'aws-deploy',
                      parameters: [
                         [
                            $class: 'StringParameterValue',
                            name: 'SERVER_NAME',
                            value: "$BRANCH_NAME-docker-$BUILD_ID"
                         ],
                         [
                            $class: 'StringParameterValue',
                            name: 'AMI',
                            value: "$DOCKER_AMI"
                         ],
                         [
                            $class: 'BooleanParameterValue',
                            name: 'DOCKER_AWS_DEPLOY',
                            value: 'true'
                         ],
                         [
                          $class: 'StringParameterValue',
                           name: 'WAR',
                           value: "$WORKSPACE/target/*.war"
                         ],
                         [
                           $class: 'StringParameterValue',
                           name: 'DEPLOYMENT_PROPERTY_OVERRIDE',
                           value: "$WORKSPACE/${BRANCH_NAME}.properties"
                         ],
                         [
                           $class: 'StringParameterValue',
                           name: 'PARENT_WORKSPACE',
                           value: "$WORKSPACE"
                         ],
                         [
                           $class: 'StringParameterValue',
                           name: 'BRANCH_NAME',
                           value: "${BRANCH_NAME}"
                         ]
                    ]
                )
            }
        }
        stage('Liquibase tests') {
            when {
                expression { return params.LIQUIBASE }
                branch 'develop'
            }
            steps {
                echo 'Running liquibase tests on develop branch...'
                sh "./mvnw -e clean test -PtestLiquibase -Djava-version=${params.MAVEN_TOOLCHAIN_JAVA_VERSION} \
                  -Djava-vendor=${params.MAVEN_TOOLCHAIN_JAVA_VENDOR} \
                  -Dlog4j2.configurationFile=log4j2-dev.xml \
                  -Djdbc.url=jdbc:mysql://localhost:3306/testLiquibaseUpdate \
                  -Dmaven.test.failure.ignore=false   -Denvironment=keepdbintact  \
                  -DRS.devlogLevel=INFO -DRS_FILE_BASE=/var/lib/jenkins/userContent/RS_FileRepoLiquibase"
            }
            post {
                failure {
                    notify currentBuild.result
                    notifySlack('FAILURE', 'Liquibase tests failed')
                }

                fixed {
                    notify currentBuild.result
                    notifySlack('SUCCESS', 'Liquibase tests fixed')
                }
            }
        }

        stage('Full Java tests') {
            when {
                anyOf {
                    branch 'master'; branch 'develop'
                    expression { return params.FULL_JAVA_TESTS }
                }
                not {
                    expression { return params.LIQUIBASE }
                }
            }

            steps {
              // put in a script tag so we can execute branchToDbName

                echo 'This branch or build is marked for full tests run'
                // this is to create a valid datbase name from the branch name

                echo "sanitised DB Name is $SANITIZED_DBNAME"
                sh "./mvnw  clean verify -Djava-version=${params.MAVEN_TOOLCHAIN_JAVA_VERSION} \
                  -Djava-vendor=${params.MAVEN_TOOLCHAIN_JAVA_VENDOR} \
                  -Djavax.xml.accessExternalDTD=all\
                  -Dlog4j2.configurationFile=log4j2-dev.xml -Dsurefire.rerunFailingTestsCount=2\
                  -Djdbc.db.maven=${SANITIZED_DBNAME} -Djdbc.url=jdbc:mysql://localhost:3306/${SANITIZED_DBNAME}\
                  -Dmaven.test.failure.ignore=false   -Denvironment=drop-recreate-db  \
                  -DRS.devlogLevel=INFO  -DRS_FILE_BASE=${RS_FILE_BASE} \
                  -DenableTestCoverage  ${params.NIGHTLY_BUILD}"
            }

            post {
                failure {
                    notify currentBuild.result
                    notifySlack('FAILURE', 'Full tests failed')
                }

                fixed {
                    notify currentBuild.result
                    notifySlack('SUCCESS', 'Full tests fixed')
                }

                always {
                    sh "rm -rf ${RS_FILE_BASE}"
                    echo "dropping test database ${SANITIZED_DBNAME}"
                    sh "mysql -urspacedbuser -prspacedbpwd  -e 'drop database if exists ${SANITIZED_DBNAME}' "
                }

                success {
                    junit 'target/surefire-reports/*.xml'
                    jacoco(
                            execPattern: '**/**.exec',
                            classPattern: '**/classes',
                            sourcePattern: '**/src/main/java',
                            exclusionPattern: '**/src/test*'
                    )
                }
            } // end post test handler
        }
    }
}

def branchToDbName (String name) {
    String newname =  name.replaceAll('[^A-Za-z0-9]', '_')
    if (newname.length() > 63) {
        newname = newname.substring(0, 63)
    }
    return newname
}

def notifySlack(String buildStatus = 'STARTED', String info = '') {
    // Build status of null means success.
    buildStatus = buildStatus ?: 'SUCCESS'

    def color

    if (buildStatus == 'STARTED') {
        color = '#D4DADF'
    } else if (buildStatus == 'SUCCESS') {
        color = '#BDFFC3'
    } else if (buildStatus == 'UNSTABLE') {
        color = '#FFFE89'
    } else {
        color = '#FF9FA1'
    }

    def msg = "${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}:\n${env.BUILD_URL}\n${info}"

    slackSend(color: color, message: msg)
}
