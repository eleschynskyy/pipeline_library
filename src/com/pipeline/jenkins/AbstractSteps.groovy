package com.pipeline.jenkins

import com.cloudbees.groovy.cps.NonCPS
import hudson.triggers.TimerTrigger

abstract class AbstractSteps {
  protected steps
  protected currentBuild
  protected env
  protected DebugLogger logger

  AbstractSteps(final steps, final currentBuild, final env) {
    this.steps = steps
    this.currentBuild = currentBuild
    this.env = env
    setUpLogger()
  }

  @NonCPS
  void setUpLogger() {
    this.logger = DebugLogger.initializeInstance(false, steps)
  }

}
