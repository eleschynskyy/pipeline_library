import com.pipeline.jenkins.JenkinsHost
import com.pipeline.jenkins.PipelineSteps

def call(
  Map inputParams = [:]
) {
    pipeline {
        agent {
            node {
                label 'nft'
                customWorkspace "${env.JOB_NAME}@${env.BUILD_NUMBER}"
            }
        }
        parameters {
            booleanParam(
                defaultValue: false,
                description: 'Dummy',
                name: 'Run_build'
            )
        }
        stages {
            stage('Preparation') {
                steps {
                    script {
                        env.JENKINS_USER_EMAIL = (JenkinsHost.getCurrentUserEmail() ?: 'test@test.com').toLowerCase()
                        env.SERVICE_NAME = inputParams.SERVICE_NAME?.trim()
                        if (!env.SERVICE_NAME) {
                            throw new Exception("Missing required parameter: SERVICE_NAME. Please add it to your service Jenkinsfile.")
                        }
                        echo "DEBUG: ${env.SERVICE_NAME}"
                        echo "Running inside dynamic Kubernetes agent: ${NODE_NAME}"
                        sh 'ls -l'
                    }
                }
            }
        }

        post {
            always {
                echo "✅ DONE"
                cleanWs()
            }
        }
    }
}