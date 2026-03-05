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
        steps.sh 'echo "TEST IN PROGRESS: ${env.INFLUXDB_BUCKET_NAME}"'
    }
  }

  /**
   * Execute test on a single node.
   */
  void executeSingleNodeTest(Map config) {
    steps.echo "Running single-node test execution on: ${env.NODE_NAME}"
    executeRunScript(serviceName: config.serviceName)
  }

}
