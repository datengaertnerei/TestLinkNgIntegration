package com.datengaertnerei.testng;

import br.eti.kinoshita.testlinkjavaapi.model.Build;
import br.eti.kinoshita.testlinkjavaapi.model.TestPlan;
import br.eti.kinoshita.testlinkjavaapi.model.TestProject;

public class TestlinkProject {
  private TestProject project;
  private TestPlan plan;
  private Build build;

  public Build getBuild() {
    return build;
  }

  public void setBuild(Build build) {
    this.build = build;
  }

  public TestPlan getPlan() {
    return plan;
  }

  public void setPlan(TestPlan plan) {
    this.plan = plan;
  }

  public TestProject getProject() {
    return project;
  }

  public void setProject(TestProject project) {
    this.project = project;
  }

  public TestlinkProject(TestProject project) {
    this.project = project;
  }
}
