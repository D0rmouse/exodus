pipeline {
    agent any
    options {
        timeout(time: 90, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
    }
    tools{
        jdk 'jdk8'
    }
    environment {
        AUTOMATION_MASTER_KEY = credentials("AUTOMATION_MASTER_KEY")
        BAZEL_FLAGS = '''|--strategy=Scalac=worker \\
                         |--experimental_sandbox_base=/dev/shm \\
                         |--sandbox_tmpfs_path=/tmp \\
                         |--test_output=errors \\
                         |--test_filter=${TEST_CLASS} \\
                         |--test_arg=--jvm_flags=-Dcom.google.testing.junit.runner.shouldInstallTestSecurityManager=false \\
                         |--test_arg=--jvm_flags=-Dwix.environment=CI \\
                         |--test_env=AUTOMATION_MASTER_KEY'''.stripMargin()
        BAZEL_HOME = tool name: 'bazel', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
        PATH = "$BAZEL_HOME/bin:$JAVA_HOME/bin:$PATH"
    }
    stages {
        stage('checkout') {
            steps {
                deleteDir()
                git branch: "${env.BRANCH_NAME}", url: "${env.repo_url}"
            }
        }
        stage('Test') {
            steps {
                script {
                    if (env.IT == "false") {
                        unstable_by_exit_code("""|#!/bin/bash
                                             |bazel test \\
                                             |${env.BAZEL_FLAGS} \\
                                             |${env.ADDITIONAL_FLAGS_BAZEL_SIXTEEN_UP_LOCAL} \\
                                             |${TEST_TARGET_NAME}
                                             |""".stripMargin())
                    } else {
                        unstable_by_exit_code("""|#!/bin/bash
                                             |bazel test \\
                                             |--strategy=TestRunner=standalone \\
                                             |${env.BAZEL_FLAGS} \\
                                             |${env.ADDITIONAL_FLAGS_BAZEL_SIXTEEN_UP_LOCAL} \\
                                             |--test_env=HOST_NETWORK_NAME \\
                                             |--jobs=1 \\
                                             |${TEST_TARGET_NAME}
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
                    archiveArtifacts 'bazel-testlogs/**,bazel-out/**/test.outputs/outputs.zip'
                    junit "bazel-testlogs/**/test.xml"
                }
            }
        }
    }
}

@SuppressWarnings("GroovyUnusedDeclaration")
def unstable_by_exit_code(some_script) {
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
        echo "***NO TESTS WERE FOUND! IF YOU HAVE SUCH TESTS PLEASE DEBUG THIS WITH THE BAZEL PEOPLE***"
            break
        default:
            currentBuild.result = 'FAILURE'
    }
}
