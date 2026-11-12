#!groovy

@Library('rspace-shared') _

// Standalone pnpm needs no Node.js; it downloads the runtime pinned in package.json devEngines.
def withFrontendTools(Closure body) {
    def pnpmHome = "${env.WORKSPACE}/node/pnpm"
    withEnv(["PNPM_HOME=${pnpmHome}", "PATH+PNPM=${pnpmHome}/bin"]) {
        sh '''
            if [ ! -x "$PNPM_HOME/bin/pnpm" ]; then
                PNPM_VERSION=$(grep -o '"pnpm@[0-9][^"+]*' package.json | cut -d@ -f2)
                test -n "$PNPM_VERSION"
                mkdir -p "$PNPM_HOME"
                curl -fsSL https://get.pnpm.io/install.sh -o "$PNPM_HOME/install.sh"
                PNPM_VERSION="$PNPM_VERSION" ENV="$PNPM_HOME/shell.sh" SHELL=/bin/sh sh "$PNPM_HOME/install.sh"
            fi
        '''
        body()
    }
}

echo 'Building rspace-web'

/*
 This is the main build script for Jenkins to run tests.
 In Jenkins, it is called from 'rspace-web' project as a multi branch build
 For the main branch, it runs 'quick' JUnit tests
 When FULL_JAVA_TESTS is set, it runs full Java tests (i.e. with IT tests) instead.
 The script takes several parameters; there are other Jenkins jobs to run nightly test-suites on other JDKs and also
 with 'nightly' tests which are more long running tests.
 The build runs through the Gradle wrapper (./gradlew). Every -D flag below is forwarded to Gradle's resource
 filtering and to the test JVMs, as it was with Maven.
 Builds use the Java 21 toolchain by default; the bytecode still targets 17 (options.release in build.gradle),
 and 17 can still be picked per build. It is passed as -Djava-version, which selects a
 Gradle Java toolchain, so the requested JDK must be one Gradle can find on the agent (auto-detected, or listed in
 org.gradle.java.installations.paths in the agent's ~/.gradle/gradle.properties). Gradle itself runs on the
 agent's JAVA_HOME (Jenkins Global properties). The parameters keep their
 MAVEN_TOOLCHAIN_ names because other Jenkins jobs (the nightly JDK runs) pass them by name.
*/

pipeline {
    agent any

    options { disableConcurrentBuilds() }

    parameters {
        string(name: 'MAVEN_TOOLCHAIN_JAVA_VERSION', defaultValue: '21', description: 'Java version of the Gradle toolchain (17 or 21); the name is kept from the Maven build')
        string(name: 'NIGHTLY_BUILD', defaultValue: '', description: 'optional extra Gradle arguments for the full Java tests, e.g. -DincludedTestGroups=nightly')
        booleanParam(name: 'ONLY_BUILD_WAR', defaultValue: false, description: 'It only build the WAR file without deploying in AWS')
        booleanParam(name: 'STORYBOOK', defaultValue: false, description: 'Build and bundle Storybook in the WAR; serving requires dev.storybook.preview.enabled=true')
        booleanParam(name: 'AWS_DEPLOY', defaultValue: false, description: 'Deploy branch build to AWS')
        booleanParam(name: 'AWS_DEPLOY_PROD_RELEASE', defaultValue: false, description: 'Deploy main branch build created in prodRelease mode to AWS')
        booleanParam(name: 'ACCESS_OUTSIDE_VPN', defaultValue: false, description: 'Only used with AWS deploy. Tick if the deployed instance should be accessible outside of VPN (HTTPS access from 0.0.0.0), e.g. to test on a mobile device or share the instance with external users')
        booleanParam(name: 'FULL_JAVA_TESTS', defaultValue: false, description: 'Run all Java tests')
        booleanParam(name: 'LIQUIBASE', defaultValue: false, description: 'Run tests on persistent liquibaseTest database')
    }

    environment {
        BUILD_FAILURE_EMAIL_LIST = 'dev@researchspace.com'
        CI = 'true'
        // BRANCH_NAME may contain '/' (e.g. feature/foo); sanitise before using it in filesystem paths or resource names
        SAFE_BRANCH_NAME = branchToSafeName("${BRANCH_NAME}")
        RS_FILE_BASE = "/var/lib/jenkins/userContent/${SAFE_BRANCH_NAME}-filestore"
        SANITIZED_DBNAME = branchToDbName("${BRANCH_NAME}")
        // scratch database for the HibernateTest classes; the hash of the full branch name comes before the readable
        // part so the 63-char cap can never cut it, keeping names unique even for branches that sanitise or truncate alike
        HIBTEST_DBNAME = branchToDbName("hibtest_${branchNameHash(BRANCH_NAME)}_${BRANCH_NAME}")
        CHEMISTRY_CONTAINER_NAME = "rspace-chemistry-${SAFE_BRANCH_NAME}-${BUILD_ID}"
        AWS_TOMCAT_AMI = 'ami-0a3f8106db17a5218'
        APP_VERSION = gradleBaseVersion(readFile('build.gradle'))
        // Gradle cannot read the non-ASCII test resource file names under a POSIX/ASCII locale
        LANG = 'C.UTF-8'

        NODE_OPTIONS="--max-old-space-size=5120 --conditions=require"
    }

    stages {
        stage('Initialize') {
            // just echo some info about the build
            steps {
                echo "Building branch - ${BRANCH_NAME}"
                sh '''
                    echo "PATH = ${PATH}"
                    ./gradlew --version
                '''
                echo 'Cleaning out filestore'
                sh "rm -rf $RS_FILE_BASE"
                sh "mkdir -p $RS_FILE_BASE"
                echo "Workspace jenkins var is $WORKSPACE"
                sh 'cp -f src/main/resources/deployments/dev/deployment.properties.example src/main/resources/deployments/dev/deployment.properties'
            }
        }

        stage("Fast JUnit tests ('main' only)") {
            when {
                not {
                    anyOf {
                        expression { return params.FULL_JAVA_TESTS }
                        expression { return params.LIQUIBASE }
                    }
                }
                changeset '**/*.java'
                branch 'main'
            }

            steps {
                echo 'This is the main branch, running fast, non Spring tests only'
                sh "./gradlew clean test -Dfast=true -DRS_FILE_BASE=${RS_FILE_BASE} \
                   -Djava-version=${params.MAVEN_TOOLCHAIN_JAVA_VERSION}"
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
                    junit 'build/test-results/**/*.xml'
                }
            }
        }
        stage('Build feature branch') {
            when {
                anyOf {
                    expression { return params.AWS_DEPLOY }
                    expression { return params.ONLY_BUILD_WAR }
                    expression { return params.STORYBOOK }
                    allOf {
                        branch 'main'
                        anyOf {
                            changeset '**/*.js'
                            changeset '**/*.ts'
                            changeset '**/*.tsx'
                            changeset '**/*.jsp'
                            changeset '**/*.css'
                            changeset '**/*.json'
                            changeset 'pnpm-lock.yaml'
                            changeset 'pnpm-workspace.yaml'
                        }
                    }
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
                withFrontendTools {
                    sh '''
                    ./gradlew clean testClasses war -DgenerateReactDist -DgenerateStorybook=${STORYBOOK} -Dtimestamp=now \
                    -Denvironment=keepdbintact -Dspring.profiles.active=prod -DRS.logLevel=INFO \
                    -Djava-version=${MAVEN_TOOLCHAIN_JAVA_VERSION} \
                    -DpropertyFileDirPlaceholder=\\$\\{propertyFileDir\\}
                    '''
                }
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
                withFrontendTools {
                     sh '''
                     ./gradlew clean testClasses war -DgenerateReactDist -DgenerateStorybook=${STORYBOOK} -Dtimestamp=now \
                    -Denvironment=prodRelease -Dspring.profiles.active=prod -DRS.logLevel=WARN -Ddeployment=production \
                    -Djava-version=${MAVEN_TOOLCHAIN_JAVA_VERSION} \
                    -DpropertyFileDirPlaceholder=\\$\\{propertyFileDir\\}
                    '''
                }
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
                                        value: "$WORKSPACE/build/libs/*.war"
                                ],
                                [
                                        $class: 'StringParameterValue',
                                        name: 'DEPLOYMENT_PROPERTY_OVERRIDE',
                                        value: "$WORKSPACE/${SAFE_BRANCH_NAME}.properties"
                                ],
                                [
                                        $class: 'BooleanParameterValue',
                                        name: 'ACCESS_OUTSIDE_VPN',
                                        value: params.ACCESS_OUTSIDE_VPN
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
                sh "./gradlew --stacktrace clean test -Djava-version=${params.MAVEN_TOOLCHAIN_JAVA_VERSION} \
                  -Dlog4j2.configurationFile=log4j2-dev.xml \
                  -Djdbc.url=jdbc:mysql://localhost:3306/testLiquibaseUpdate \
                  -Dliquibase.context=run \
                  -Denvironment=keepdbintact \
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
                withFrontendTools {
                    // --continue runs every test task even after one fails, so all suites report and get coverage
                    sh "./gradlew --continue clean verify -DgenerateReactDist -DgenerateStorybook=${params.STORYBOOK} -Djava-version=${params.MAVEN_TOOLCHAIN_JAVA_VERSION} \
                      -Djavax.xml.accessExternalDTD=all\
                      -Dlog4j2.configurationFile=log4j2-dev.xml -Dsurefire.rerunFailingTestsCount=2\
                      -Djdbc.server.db=${SANITIZED_DBNAME} -Djdbc.url=jdbc:mysql://localhost:3306/${SANITIZED_DBNAME} -Dhibtest.db=${HIBTEST_DBNAME}\
                      -Denvironment=drop-recreate-db \
                      -DRS.devlogLevel=INFO  -DRS_FILE_BASE=${RS_FILE_BASE} \
                      -DenableTestCoverage  ${params.NIGHTLY_BUILD}"
                }
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
                    echo "dropping hibernate scratch database ${HIBTEST_DBNAME}"
                    sh "mysql -h 127.0.0.1 -P 3306 -urspacedbuser -prspacedbpwd  -e 'drop database if exists ${HIBTEST_DBNAME}' "
                }

                success {
                    junit 'build/test-results/**/*.xml'
                    jacoco(
                            execPattern: 'build/jacoco/*.exec',
                            classPattern: 'build/classes/java/main',
                            sourcePattern: 'src/main/java'
                    )
                }
            } // end post test handler
        }

        stage('Chemistry tests') {
            when {
                expression { return params.FULL_JAVA_TESTS && !params.LIQUIBASE }
            }

            steps {
                sh '''
                    mkdir -p "${RS_FILE_BASE}"
                    docker run --detach --rm \
                      --name "${CHEMISTRY_CONTAINER_NAME}" \
                      --publish 127.0.0.1::8090 \
                      rspaceops/oss-chemistry:latest
                    CHEMISTRY_PORT=$(docker port "${CHEMISTRY_CONTAINER_NAME}" 8090/tcp | sed 's/.*://')
                    export CHEMISTRY_URL="http://127.0.0.1:${CHEMISTRY_PORT}"
                    export JDBC_DB="${SANITIZED_DBNAME}"
                    export JDBC_URL="jdbc:mysql://localhost:3306/${SANITIZED_DBNAME}"
                    if ! scripts/run-chemistry-tests.sh; then
                      docker logs "${CHEMISTRY_CONTAINER_NAME}"
                      exit 1
                    fi
                '''
            }

            post {
                always {
                    script {
                        try {
                            junit testResults: 'build/test-results/**/*.xml', allowEmptyResults: true
                        } finally {
                            sh 'docker stop "${CHEMISTRY_CONTAINER_NAME}" || true'
                            sh 'rm -rf "${RS_FILE_BASE}"'
                            sh 'mysql -h 127.0.0.1 -P 3306 -urspacedbuser -prspacedbpwd -e "drop database if exists ${SANITIZED_DBNAME}"'
                        }
                    }
                }
                failure {
                    notify currentBuild.result
                    notifySlack('FAILURE', 'Chemistry tests failed')
                }
                fixed {
                    notify currentBuild.result
                    notifySlack('SUCCESS', 'Chemistry tests fixed')
                }
            }
        }
    }
}

// The version declared in build.gradle (e.g. '2.29.0-SNAPSHOT'), without the per-build timestamp suffix.
@NonCPS
def gradleBaseVersion (String buildGradle) {
    def m = buildGradle =~ /(?m)^version\s*=\s*["']([^"'$]*)/
    return m.find() ? m.group(1).replaceAll(/-$/, '') : 'unknown'
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
