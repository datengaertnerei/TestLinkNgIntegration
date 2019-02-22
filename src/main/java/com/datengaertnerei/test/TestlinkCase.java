package com.datengaertnerei.test;

import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import java.util.LinkedList;
import java.util.List;

public class TestlinkCase {

  private String testCaseName;
  private ExecutionStatus status;
  private String duration;
  private List<TestlinkStep> steps;

  public TestlinkCase(String testCaseName) {
    this.testCaseName = testCaseName;
    this.steps = new LinkedList<>();
  }

  public String getTestCaseName() {
    return testCaseName;
  }

  public void addStep(TestlinkStep newStep) {
    steps.add(newStep);
  }

  public List<TestlinkStep> getSteps() {
    return steps;
  }

  public String getDuration() {
    return duration;
  }

  public void setDuration(String duration) {
    this.duration = duration;
  }

  public ExecutionStatus getStatus() {
    return status;
  }

  public void setStatus(ExecutionStatus status) {
    this.status = status;
  }
}
