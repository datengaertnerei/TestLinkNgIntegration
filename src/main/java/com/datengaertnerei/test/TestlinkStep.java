package com.datengaertnerei.test;

public class TestlinkStep {
  enum TestStatus {
    PASSED,
    FAILED,
    BLOCKED;
  }

  private String testStepName;
  private String parameters;
  private String stackTrace;
  private TestStatus status;

  public TestlinkStep(String testStepName, String parameters) {
    this.testStepName = testStepName;
    this.parameters = parameters;
  }

  public String getTestStepName() {
    return testStepName;
  }

  public void setTestStepName(String testStepName) {
    this.testStepName = testStepName;
  }

  public String getParameters() {
    return parameters;
  }

  public void setParameters(String parameters) {
    this.parameters = parameters;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(String stackTrace) {
    this.stackTrace = stackTrace;
  }

  public TestStatus getStatus() {
    return status;
  }

  public void setStatus(TestStatus status) {
    this.status = status;
  }
}
