pipeline {
    agent any
    options {
        timestamps()
        ansiColor('xterm')
    }
    tools{
        jdk 'jdk8'
    }
    environment {
        CODOTA_TOKEN = credentials("codota-token")
        ARTIFACTORY_TOKEN = credentials("artifactory-token")
        AUTOMATION_MASTER_KEY = credentials("AUTOMATION_MASTER_KEY")
        REPO_NAME = find_repo_name()
        MANAGED_DEPS_REPO_NAME = "core-server-build-tools"
        MANAGED_DEPS_REPO_URL = "git@github.com:wix-private/core-server-build-tools.git"
        BAZEL_FLAGS = '''|-k \\
                         |--experimental_sandbox_base=/dev/shm \\
                         |--test_arg=--jvm_flags=-Dwix.environment=CI \\
                         |--test_env=AUTOMATION_MASTER_KEY'''.stripMargin()
        DOCKER_HOST = "${env.TEST_DOCKER_HOST}"
        BUILDOZER_HOME = tool name: 'buildozer', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
        BUILDIFIER_HOME = tool name: 'buildifier', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
        PATH = "$BAZEL_HOME/bin:$BUILDOZER_HOME/bin:$BUILDIFIER_HOME/bin:$JAVA_HOME/bin:$PATH"
    }
    stages {
        stage('checkout') {
            steps {
                dir("migrator") {
                    copyArtifacts flatten: true, projectName: "${MIGRATOR_BUILD_JOB}", selector: upstream(allowUpstreamDependencies: false, fallbackToLastSuccessful: true, upstreamFilterStrategy: 'UseGlobalSetting')
                }
                dir("${env.REPO_NAME}") {
                    git "${env.repo_url}"
                }
                dir("${env.MANAGED_DEPS_REPO_NAME}") {
                    echo "checkout of: ${env.MANAGED_DEPS_REPO_NAME}"
                    checkout([$class: 'GitSCM', branches: [[name: 'master' ]],
                              userRemoteConfigs: [[url: "${env.MANAGED_DEPS_REPO_URL}"]]])
                }
            }
        }
        stage('clean') {
            steps {
                dir("${env.REPO_NAME}") {
                    sh 'rm -rf third_party'
                    sh 'find . -path "*/*BUILD" -exec rm -f {} \\;'
                    sh 'find . -path "*/*BUILD.bazel" -exec rm -f {} \\;'
                }
            }
        }
        stage('migrate') {
            steps {
                dir("migrator") {
                    sh """|java -Xmx12G \\
                      |   -Dcodota.token=${env.CODOTA_TOKEN} \\
                      |   -Dartifactory.token=${env.ARTIFACTORY_TOKEN} \\
                      |   -Dskip.classpath=false \\
                      |   -Dskip.transformation=false \\
                      |   -Dmanaged.deps.repo=../${env.MANAGED_DEPS_REPO_NAME} \\
                      |   -Dfail.on.severe.conflicts=true \\
                      |   -Drepo.root=../${repo_name}  \\
                      |   -Drepo.url=${env.repo_url} \\
                      |   -jar wix-bazel-migrator-0.0.1-SNAPSHOT-jar-with-dependencies.jar""".stripMargin()
                }
                dir("${env.REPO_NAME}") {
                    sh script: "buildozer 'add tags manual' //third_party/...:%scala_import", returnStatus: true
                    script {
                        if (fileExists('bazel_migration/post-migration.sh')) {
                            sh "sh bazel_migration/post-migration.sh"
                        }
                    }
                    sh 'buildifier $(find . -iname BUILD.bazel -type f)'
                }
            }
        }
        stage('build') {
            steps {
                dir("${env.REPO_NAME}") {
                    sh "bazel build ${env.ADDITIONAL_FLAGS_BAZEL_SIXTEEN_UP_LOCAL} -k --strategy=Scalac=worker //..."
                }
            }
        }
        stage('UT') {
            steps {
                dir("${env.REPO_NAME}") {
                    script {
                        unstable_by_exit_code("UNIT", """|#!/bin/bash
                                             |bazel test \\
                                             |      --test_tag_filters=UT,-IT \\
                                             |      --flaky_test_attempts=3 \\
                                             |      ${env.BAZEL_FLAGS} \\
                                             |      ${env.ADDITIONAL_FLAGS_BAZEL_SIXTEEN_UP_LOCAL} \\
                                             |      //...
                                             |""".stripMargin())
                    }
                }
            }
        }
        stage('IT') {
            steps {
                dir("${env.REPO_NAME}") {
                    script {
                        unstable_by_exit_code("IT/E2E", """|#!/bin/bash
                                             |export DOCKER_HOST=$env.TEST_DOCKER_HOST
                                             |bazel test \\
                                             |      --test_tag_filters=IT \\
                                             |      --strategy=TestRunner=standalone \\
                                             |      ${env.BAZEL_FLAGS} \\
                                             |      ${env.ADDITIONAL_FLAGS_BAZEL_SIXTEEN_UP_LOCAL} \\
                                             |      --test_env=DOCKER_HOST \\
                                             |      --jobs=1 \\
                                             |      //...
                                             |""".stripMargin())
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                if (env.FOUND_TEST == "true") {
                    archiveArtifacts "${env.REPO_NAME}/bazel-testlogs/**,${env.REPO_NAME}/bazel-out/**/test.outputs/outputs.zip"
                    junit "${env.REPO_NAME}/bazel-testlogs/**/test.xml"
                }
                try {
                    echo "[INFO] creating tar.gz files for migration artifacts..."
                    dir("migrator") {
                        sh """|tar czf classpathModules.cache.tar.gz classpathModules.cache
                              |tar czf cache.tar.gz cache
                              |tar czf dag.bazel.tar.gz dag.bazel""".stripMargin()
                    }
                } catch (err) {
                    echo "[WARN] could not create all tar.gz files ${err}"
                } finally {
                    dir("migrator") {
                        archiveArtifacts "classpathModules.cache.tar.gz,dag.bazel.tar.gz,wix-bazel-migrator/cache.tar.gz"
                    }
                }
            }
        }
    }
}

@SuppressWarnings("GroovyAssignabilityCheck")
def find_repo_name() {
    name = "${env.repo_url}".split('/')[-1]
    if (name.endsWith(".git"))
        name = name[0..-5]
    return name
}

@SuppressWarnings("GroovyUnusedDeclaration")
def unstable_by_exit_code(phase, some_script) {
    echo "Running " + some_script
    return_code = a = sh(script: some_script, returnStatus: true)
    switch (a) {
        case 0:
            env.FOUND_TEST = "true"
            break
        case 3:
            echo "There were test failures"
            env.FOUND_TEST = "true"
            currentBuild.result = 'UNSTABLE'
            break
        case 4:
            echo "***NO ${phase} TESTS WERE FOUND! IF YOU HAVE SUCH TESTS PLEASE DEBUG THIS WITH THE BAZEL PEOPLE***"
            break
        default:
            currentBuild.result = 'FAILURE'
    }
}
