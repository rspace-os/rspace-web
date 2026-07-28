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
        booleanParam(name: 'ONLY_BUILD_WAR', defaultValue: false, description: 'It only build the WAR file without deploying in AWS')
        booleanParam(name: 'AWS_DEPLOY', defaultValue: false, description: 'Deploy branch build to AWS')
        booleanParam(name: 'AWS_DEPLOY_PROD_RELEASE', defaultValue: false, description: 'Deploy main branch build created in prodRelease mode to AWS')
        booleanParam(name: 'FULL_JAVA_TESTS', defaultValue: false, description: 'Run all Java tests')
        booleanParam(name: 'LIQUIBASE', defaultValue: false, description: 'Run tests on persistent liquibaseTest database')
    }

    //tools {
    //    // this is the JDK used to run maven itself
    //    // the toolchain settings just affect compilation
    //    jdk 'OPEN-JDK-17'
    //}

    environment {
        BUILD_FAILURE_EMAIL_LIST = 'dev@researchspace.com'
        CI = 'true'
        // BRANCH_NAME may contain '/' (e.g. feature/foo); sanitise before using it in filesystem paths or resource names
        SAFE_BRANCH_NAME = branchToSafeName("${BRANCH_NAME}")
        RS_FILE_BASE = "/var/lib/jenkins/userContent/${SAFE_BRANCH_NAME}-filestore"
        SANITIZED_DBNAME = branchToDbName("${BRANCH_NAME}")
        AWS_TOMCAT_AMI = 'ami-04a32018c63fb81e9'
        APP_VERSION = readMavenPom().getVersion()

        NODE_OPTIONS="--max-old-space-size=5120 --conditions=require"
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
                sh "mkdir -p $RS_FILE_BASE"
                echo "Workspace jenkins var is $WORKSPACE"
            }
        }

        stage('Fast JUnit tests') {
            when {
                not {
                    anyOf {
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
        stage('Build feature branch') {
            when {
                anyOf {
                    expression { return params.AWS_DEPLOY }
                    expression { return params.ONLY_BUILD_WAR }
                    changeset '**/*.js'
                    changeset '**/*.ts'
                    changeset '**/*.tsx'
                    changeset '**/*.jsp'
                    changeset '**/*.css'
                    changeset '**/*.json'
                }
                not {
                    anyOf {
                        expression { return params.FULL_JAVA_TESTS }
                        expression { return params.LIQUIBASE }
                        expression { return params.AWS_DEPLOY_PROD_RELEASE }
                    }
                }
            }
            steps {
                echo 'Building feature branch'
                sh '''
                ./mvnw clean package -DgenerateReactDist -DskipTests=true \
                -Denvironment=keepdbintact -Dspring.profiles.active=prod -DRS.logLevel=INFO \
                -Djava-version=${MAVEN_TOOLCHAIN_JAVA_VERSION} -Djava-vendor=${MAVEN_TOOLCHAIN_JAVA_VENDOR} \
                -DpropertyFileDirPlaceholder=\\$\\{propertyFileDir\\}
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
                 ./mvnw clean package -DgenerateReactDist -DskipTests=true \
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

        stage('Deploy package to AWS') {
            when {
                anyOf {
                    expression { return params.AWS_DEPLOY }
                    expression { return params.AWS_DEPLOY_PROD_RELEASE }
                }
            }

            steps {
                build(
                        job: 'aws-deploy',
                        parameters: [
                                [
                                        $class: 'StringParameterValue',
                                        name: 'SERVER_NAME',
                                        value: "$SAFE_BRANCH_NAME-$BUILD_ID"
                                ],
                                [
                                        $class: 'StringParameterValue',
                                        name: 'AMI',
                                        value: "$AWS_TOMCAT_AMI"
                                ],
                                [
                                        $class: 'StringParameterValue',
                                        name: 'WAR',
                                        value: "$WORKSPACE/target/*.war"
                                ],
                                [
                                        $class: 'StringParameterValue',
                                        name: 'DEPLOYMENT_PROPERTY_OVERRIDE',
                                        value: "$WORKSPACE/${SAFE_BRANCH_NAME}.properties"
                                ]
                        ],
                        wait: false
                )
            }
        }
        stage('Liquibase tests') {
            when {
                expression { return params.LIQUIBASE }
                branch 'main'
            }
            steps {
                echo 'Running liquibase tests on main branch...'
                sh "./mvnw -e clean test -Djava-version=${params.MAVEN_TOOLCHAIN_JAVA_VERSION} \
                  -Djava-vendor=${params.MAVEN_TOOLCHAIN_JAVA_VENDOR} \
                  -Dlog4j2.configurationFile=log4j2-dev.xml \
                  -Djdbc.url=jdbc:mysql://localhost:3306/testLiquibaseUpdate \
                  -Dliquibase.context=run \
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
                sh "./mvnw clean verify -DgenerateReactDist -Djava-version=${params.MAVEN_TOOLCHAIN_JAVA_VERSION} \
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
                    sh "mysql -h 127.0.0.1 -P 3306 -urspacedbuser -prspacedbpwd  -e 'drop database if exists ${SANITIZED_DBNAME}' "
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

// Replaces characters that are unsafe in filesystem paths or AWS/host resource names (notably '/') with '-',
// keeping dots, underscores and hyphens, and appends a short hash of the original name so that distinct
// branches that would otherwise collide (e.g. 'feature/foo' and 'feature-foo') stay unique.
def branchNameHash (String name) {
    byte[] digest = java.security.MessageDigest.getInstance('MD5')
        .digest(name.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    StringBuilder hash = new StringBuilder()
    for (byte b : digest) {
        hash.append(String.format('%02x', b & 0xff))
    }
    return hash.substring(0, 8)
}

def branchToSafeName (String name) {
    String hash = branchNameHash(name)
    String newname = name.replaceAll('[^A-Za-z0-9._-]', '-')
    String candidate = "${newname}-${hash}"
    if (candidate.length() > 63) {
        newname = newname.substring(0, 63 - 1 - hash.length())
        candidate = "${newname}-${hash}"
    }
    return candidate
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
