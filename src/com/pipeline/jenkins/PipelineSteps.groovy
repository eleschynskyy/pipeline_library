package com.pipeline.jenkins

import com.cloudbees.groovy.cps.NonCPS
import com.cloudbees.hudson.plugins.folder.AbstractFolder
import hudson.model.Job
import jenkins.model.Jenkins
import org.apache.commons.lang3.StringUtils

import java.text.SimpleDateFormat
import java.util.Collections
import java.util.HashSet

class PipelineSteps extends AbstractSteps {

  PipelineSteps(final steps, final currentBuild, final env) {
    super(steps, currentBuild, env)
  }

  void executeRunScript(Map runConfig) {
    steps.withEnv([
      "INFLUXDB_BUCKET_NAME=${runConfig.serviceName}"
    ]) {
        steps.sh 'echo "TEST IN PROGRESS: $INFLUXDB_BUCKET_NAME"'
        steps.sh './run.sh'
    }
  }

  void executeSingleNodeTest(Map config) {
    steps.sh "Running in container ${config.dockerImage}"
    steps.sh "Running with args ${config.dockerArgs}"
    steps.echo ">>>>>>>>>>>>"
    steps.withEnv(["JMETER_EXTRA_ARGS=${config.dockerArgs ?: ''}"]) {
      steps.container("${config.dockerImage}") {
        steps.echo "Running single-node test execution on: ${env.NODE_NAME}"
        steps.sh 'chmod +x run.sh'
        executeRunScript(serviceName: config.serviceName)
      }
    }
  }

  void executeMultiNodeTest(Map config) {
    def numNodes = config.numNodes
    def serviceName = config.serviceName
    def dockerImage = config.dockerImage
    def dockerArgs = config.dockerArgs
    def nodeReadyStatus = config.nodeReadyStatus
    def nodeStartStatus = config.nodeStartStatus
    def nodeValidationStatus = config.nodeValidationStatus

    // Stash workspace for other nodes
    steps.stash name: 'workspace', includes: '**/*'

    // Initialize synchronization
    def buildKey = "${env.JOB_NAME}-${env.BUILD_NUMBER}".toString()
    nodeReadyStatus.put(buildKey, Collections.synchronizedSet(new HashSet<Integer>()))
    nodeStartStatus.put(buildKey, Collections.synchronizedSet(new HashSet<Integer>()))
    nodeValidationStatus.put(buildKey, 'PENDING')
    steps.echo "Running multi-node test execution: ${numNodes}/${serviceName}/${buildKey}"
    steps.sh 'chmod +x run.sh'

    // Reference to this for use in closures
    def self = this

    // Helper closure for node synchronization and test execution
    def waitForValidationSuccess = { int nodeIdx ->
      steps.timeout(time: 20, unit: 'MINUTES') {
          steps.waitUntil(initialRecurrencePeriod: 5000) {
            def status = nodeValidationStatus.get(buildKey)
            if ('FAILED'.equals(status)) {
              steps.error "Node ${nodeIdx}: Validation failed on master node; aborting execution"
            }
            return 'SUCCESS'.equals(status)
          }
      }
    }

    def executeNodeTest = { int nodeIdx, int totalNodes, String bKey,
                            String dImage, String dArgs,
                              String svcName, boolean isMaster ->
      steps.sh "docker pull ${dImage}"
      steps.withDockerContainer(image: dImage, args: dArgs) {
        steps.echo "Running test execution ${nodeIdx + 1}/${totalNodes} on node: ${env.NODE_NAME}"

        if (isMaster) {
          steps.echo "Node ${nodeIdx}: Running validation phase"
          try {
            self.executeRunScript(serviceName: svcName)
            nodeValidationStatus.put(bKey, 'SUCCESS')
            steps.echo "Node ${nodeIdx}: Validation successful"
          } catch (Exception e) {
            nodeValidationStatus.put(bKey, 'FAILED')
            throw e
          }
        } else {
          steps.echo "Node ${nodeIdx}: Waiting for master validation result"
        }

        // Ensure validation passed before proceeding
        waitForValidationSuccess(nodeIdx)

        // Phase 1: Signal this node is ready after validation is complete
        nodeReadyStatus.get(bKey).add(nodeIdx)
        steps.echo "Node ${nodeIdx}: Phase 1 - Ready for execute (${nodeReadyStatus.get(bKey).size()}/${totalNodes})"

        // Phase 1: Wait for all nodes to be ready
        steps.timeout(time: 20, unit: 'MINUTES') {
              steps.waitUntil(initialRecurrencePeriod: 10000) {
                def readyCount = nodeReadyStatus.get(bKey)?.size() ?: 0
                steps.echo "Node ${nodeIdx}: Phase 1 - Waiting for all nodes (${readyCount}/${totalNodes})..."
                return readyCount >= totalNodes
              }
        }
        steps.echo "Node ${nodeIdx}: Phase 1 complete - All nodes ready to execute"

        // Phase 2: Signal ready to start
        nodeStartStatus.get(bKey).add(nodeIdx)
        steps.echo "Node ${nodeIdx}: Phase 2 - Signaling execute start (${nodeStartStatus.get(bKey).size()}/${totalNodes})"

        // Phase 2: Wait for all nodes to signal start
        steps.timeout(time: 5, unit: 'MINUTES') {
              steps.waitUntil(initialRecurrencePeriod: 1000) {
                def startCount = nodeStartStatus.get(bKey)?.size() ?: 0
                steps.echo "Node ${nodeIdx}: Phase 2 - Waiting for synchronized execute start (${startCount}/${totalNodes})..."
                return startCount >= totalNodes
              }
        }
        steps.echo "Node ${nodeIdx}: All nodes synchronized, starting execute phase at ${new Date()}"

        try {
          self.executeRunScript(serviceName: svcName)
        } finally {
          steps.echo '✅ FINALLY DONE'
        }
      }
    }

    // Build parallel stages map
    def parallelStages = [:]

    // Node 0: runs on main agent (no new node needed)
    parallelStages['Test Execution 0'] = {
      executeNodeTest(0, numNodes, buildKey, dockerImage, dockerArgs, serviceName, true)
    }

    // Nodes 1, 2, ... : run on separate nodes, unstash workspace
    for (int i = 1; i < numNodes; i++) {
      def nodeIndex = i  // Capture for closure

      parallelStages["Test Execution ${nodeIndex}"] = {
        steps.node('nft') {
          steps.ws("${env.WORKSPACE}-node${nodeIndex}") {
            try {
              steps.unstash 'workspace'
              executeNodeTest(nodeIndex, numNodes, buildKey, dockerImage, dockerArgs, serviceName, false)
            } finally {
              steps.cleanWs()
            }
          }
        }
      }
    }

    // Execute all test nodes in parallel (failFast: stop all if one fails)
    steps.parallel parallelStages + ([failFast: true])
  }

}
