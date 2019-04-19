pipeline {
    agent any
    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
    }
    tools{
        jdk 'jdk8'
    }
    environment {
        CODOTA_TOKEN = credentials("codota-token")
        ARTIFACTORY_TOKEN = credentials("artifactory-token")
        REPO_NAME = find_repo_name()
        MANAGED_DEPS_REPO_NAME = "core-server-build-tools"
        MANAGED_DEPS_REPO_URL = "git@github.com:wix-private/core-server-build-tools.git"
        BRANCH_NAME = "bazel-dry-mig-${env.BUILD_ID}"
        bazel_log_file = "bazel-build.log"
        BUILDOZER_HOME = tool name: 'buildozer', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
        BUILDIFIER_HOME = tool name: 'buildifier', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
        PATH = "$BAZEL_HOME/bin:$BUILDOZER_HOME/bin:$BUILDIFIER_HOME/bin:$JAVA_HOME/bin:$PATH"
        COMMIT_HASH = "${env.COMMIT_HASH}"
    }
    stages {
        stage('checkout') {
            steps {
                echo "got commit hash: ${env.COMMIT_HASH}"
                dir("wix-bazel-migrator") {
                    copyArtifacts flatten: true, projectName: "${MIGRATOR_BUILD_JOB}", selector: upstream(allowUpstreamDependencies: false, fallbackToLastSuccessful: true, upstreamFilterStrategy: 'UseGlobalSetting')
                }
                dir("${env.REPO_NAME}") {
                    checkout([$class: 'GitSCM', branches: [[name: env.COMMIT_HASH ]],
                              userRemoteConfigs: [[url: "${env.repo_url}"]]])
                }
            }
        }
        stage('checkout-managed-deps-repo') {
            steps {
                echo "checkout of: ${env.MANAGED_DEPS_REPO_NAME}"
                dir("${env.MANAGED_DEPS_REPO_NAME}") {
                    checkout([$class: 'GitSCM', branches: [[name: 'master' ]],
                              userRemoteConfigs: [[url: "${env.MANAGED_DEPS_REPO_URL}"]]])
                }
            }
        }
        stage('migrate') {
            steps {
                dir("${env.REPO_NAME}") {
                    sh 'rm -rf third_party'
                    sh 'find . -path "*/*BUILD" -exec rm -f {} \\;'
                    sh 'find . -path "*/*BUILD.bazel" -exec rm -f {} \\;'
                }
                dir("wix-bazel-migrator") {
                    sh """|stdbuf -i0 -o0 -e0 \\
                          |   java -Xmx12G \\
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
            }
        }
        stage('post-migrate') {
            steps {
                dir("${env.REPO_NAME}") {
                    sh script: "buildozer 'add tags manual' //third_party/...:%scala_import", returnStatus: true
                    sh 'buildifier $(find . -iname BUILD.bazel -type f)'
                    sh 'touch .gitignore'
                    sh 'grep -q -F "/bazel-*" .gitignore || echo "\n/bazel-*" >> .gitignore'
                    script{
                        if (fileExists('bazel_migration/post-migration.sh')){
                            sh "sh bazel_migration/post-migration.sh"
                        }
                    }
                }
            }
        }
        stage('push-to-git') {
            steps {
                dir("${env.REPO_NAME}"){
                   sh """|git checkout -b ${env.BRANCH_NAME}
                         |git add .
                         |git reset -- bazel-build.log
                         |git commit --allow-empty -m "bazel migrator created by ${env.BUILD_URL}"
                         |git push origin ${env.BRANCH_NAME}
                         |""".stripMargin()
                }
            }
        }
    }
    post {
        always {
            script {
                try {
                    dir("wix-bazel-migrator") {
                        echo "[INFO] creating tar.gz files for migration artifacts..."
                        sh """|tar czf classpathModules.cache.tar.gz classpathModules.cache
                              |tar czf cache.tar.gz cache
                              |tar czf dag.bazel.tar.gz dag.bazel
                              |tar czf local-repo.tar.gz resolver-repo""".stripMargin()
                    }
                } catch (err) {
                    echo "[WARN] could not create all tar.gz files ${err}"
                } finally {
                    archiveArtifacts "wix-bazel-migrator/classpathModules.cache.tar.gz,wix-bazel-migrator/dag.bazel.tar.gz,wix-bazel-migrator/cache.tar.gz,wix-bazel-migrator/local-repo.tar.gz"
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

