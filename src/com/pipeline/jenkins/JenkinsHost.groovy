package com.pipeline.jenkins

import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud
import hudson.model.ParametersAction
import hudson.model.Run
import hudson.model.User
import hudson.tasks.Mailer
import io.jenkins.plugins.casc.SchemaGeneration
import io.jenkins.plugins.util.JenkinsFacade
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.json.JSONObject

class JenkinsHost extends AbstractSteps {

  JenkinsHost(final steps, final currentBuild, final env) {
    super(steps, currentBuild, env)
  }

  static String getCurrentUserEmail() {
    return User.current().getProperty(Mailer.UserProperty.class).getAddress()
  }

}
