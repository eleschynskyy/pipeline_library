import groovy.transform.Field
import com.pipeline.jenkins.JenkinsHost
import com.pipeline.jenkins.PipelineSteps

import java.util.concurrent.ConcurrentHashMap

// Global maps for multi-node synchronization across parallel branches
@Field ConcurrentHashMap<String, Set<Integer>> nodeReadyStatus = new ConcurrentHashMap<>()
@Field ConcurrentHashMap<String, Set<Integer>> nodeStartStatus = new ConcurrentHashMap<>()
@Field ConcurrentHashMap<String, String> nodeValidationStatus = new ConcurrentHashMap<>()

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
                defaultValue: true,
                description: 'run_test',
                name: 'run_test'
            )
            string(
                defaultValue: '1',
                description: 'Number of agents',
                name: 'num_agents',
                trim: true
            )
        }
        stages {
            stage('Preparation') {
                steps {
                    script {
                        env.JENKINS_USER_EMAIL = (JenkinsHost.getCurrentUserEmail() ?: 'test@test.com').toLowerCase()
                        env.SERVICE_NAME = inputParams.SERVICE_NAME?.trim()
                        if (!env.SERVICE_NAME) {
                            throw new Exception('Missing required parameter: SERVICE_NAME. Please add it to your service Jenkinsfile.')
                        }
                        echo "DEBUG[SERVICE_NAME]: ${env.SERVICE_NAME}"
                        echo "DEBUG[JENKINS_USER_EMAIL]: ${env.JENKINS_USER_EMAIL}"
                        echo "Running inside dynamic Kubernetes agent: ${NODE_NAME}"
                        sh 'ls -l'
                    }
                }
            }

            stage('Test Execution') {
                when {
                    expression { params.run_test }
                }
                steps {
                    script {
                        def perfSteps = new PipelineSteps(this, currentBuild, env)
                        def num_agents = params.num_agents
                        if (num_agents == '1') {
                            echo 'Test Execution on 1 agent'
                            perfSteps.executeSingleNodeTest(
                                serviceName: env.SERVICE_NAME
                            )
                        } else {
                            echo "Test Execution on ${num_agents} agents"
                            perfSteps.executeMultiNodeTest(
                                numNodes: num_agents.toInteger(),
                                serviceName: env.SERVICE_NAME,
                                nodeReadyStatus: nodeReadyStatus,
                                nodeStartStatus: nodeStartStatus,
                                nodeValidationStatus: nodeValidationStatus
                            )
                        }
                    }
                }
            }
        }

        post {
            always {
                echo '✅ DONE'
                cleanWs()
            }
        }
    }
}
