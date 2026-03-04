package com.pipeline.jenkins

import com.cloudbees.groovy.cps.NonCPS

class DebugLogger {

  private boolean debugLogging
  private static DebugLogger logger
  private steps

  private DebugLogger(final debugLogging, final steps) {
    this.debugLogging = debugLogging
    this.steps = steps
  }

  @NonCPS
  static DebugLogger initializeInstance(final debugLogging, final steps) {
    if (logger == null) {
      logger = new DebugLogger(debugLogging, steps)
    }
    return logger
  }

  @NonCPS
  static DebugLogger retrieveInstance() {
    if (logger == null) {
      throw new IllegalStateException("DebugLogger is not initialized")
    }
    return logger
  }

  @NonCPS
  static void updateInstance(final debugLogging, final steps) {
    logger.steps = steps
    logger.debugLogging = debugLogging
  }

  static void resetInstance() {
    logger = null
  }

  void debug(String message) {
    if (debugLogging) {
      log("[DEBUG] " + message)
    }
  }

  void info(String message) {
    log("[INFO] " + message)
  }

  void warn(String message) {
    log("[WARN] " + message)
  }

  void error(String message) {
    log("[ERROR] " + message)
  }

  void printStackTrace(Throwable e) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
    PrintStream printStream = new PrintStream(outputStream)
    e.printStackTrace(printStream)
    log(outputStream.toString())
  }

  private void log(String message) {
    if (steps != null) {
      steps.echo(message)
    }
  }
}
