package com.datengaertnerei.testng;

import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionType;
import br.eti.kinoshita.testlinkjavaapi.model.TestCase;
import br.eti.kinoshita.testlinkjavaapi.model.TestCaseStep;
import br.eti.kinoshita.testlinkjavaapi.model.TestSuite;
import com.datengaertnerei.testng.TestlinkStep.TestStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class TestlinkIntegrationListener implements ITestListener {
  private static Log log = LogFactory.getLog(TestlinkIntegrationListener.class);

  private TestlinkIntegrationContext tlContext;
  private TestlinkProject tlProject;
  private Map<String, TestlinkCase> testCases;

  @Override
  public void onTestStart(ITestResult result) {
    // just to comply with the interface
  }

  @Override
  public void onTestSuccess(ITestResult result) {
    if (tlProject == null) {
      return;
    }
    TestlinkStep step = createTestStep(result);
    step.setStatus(TestStatus.PASSED);
    getTestCase(result.getInstanceName()).setStatus(ExecutionStatus.PASSED);
  }

  @Override
  public void onTestFailure(ITestResult result) {
    if (tlProject == null) {
      return;
    }
    TestlinkStep step = createTestStep(result);
    step.setStatus(TestStatus.FAILED);
    step.setStackTrace(ExceptionUtils.getFullStackTrace(result.getThrowable()));
    getTestCase(result.getInstanceName()).setStatus(ExecutionStatus.FAILED);
  }

  @Override
  public void onTestSkipped(ITestResult result) {
    if (tlProject == null) {
      return;
    }
    TestlinkStep step = createTestStep(result);
    step.setStatus(TestStatus.BLOCKED);
  }

  @Override
  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    if (tlProject == null) {
      return;
    }
    TestlinkStep step = createTestStep(result);
    step.setStatus(TestStatus.FAILED);
    step.setStackTrace(ExceptionUtils.getFullStackTrace(result.getThrowable()));
    getTestCase(result.getInstanceName()).setStatus(ExecutionStatus.PASSED);
  }

  @Override
  public void onStart(ITestContext context) {
    String buildName = System.getProperty("TestlinkIntegrationListener.Build");
    String projectName = System.getProperty("TestlinkIntegrationListener.Project");

    if (null == projectName || null == buildName) {
      log.error("Project/Build property missing. Could not synchronize to TestLink.");
      return;
    }

    // init TestLink context and project
    tlContext = TestlinkIntegrationContext.getInstance();
    tlProject = tlContext.getProject(projectName);

    testCases = new ConcurrentHashMap<>();

    log.info(
        new StringBuilder("TestlinkIntegrationListener starting for TestSuite ")
            .append(context.getSuite().getName())
            .append(" and Context ")
            .append(context.getName()));
  }

  @Override
  public void onFinish(ITestContext context) {
    if (tlProject == null) {
      return;
    }
    TestSuite suite = tlContext.getSuite(tlProject.getProject(), context.getSuite().getName());

    for (TestlinkCase tlCase : testCases.values()) {
      List<TestCaseStep> steps = new ArrayList<>();
      StringBuilder testCaseExecutionProtocol = new StringBuilder();
      fillTestStepExecutionProtocol(tlCase, testCaseExecutionProtocol);
      for (TestlinkStep tlStep : tlCase.getSteps()) {
        TestCaseStep step = createTestCaseStep(tlStep);
        steps.add(step);
        fillTestStepExecutionProtocol(testCaseExecutionProtocol, tlStep);
      }
      TestCase testCase =
          tlContext.createTestCase(tlCase.getTestCaseName(), suite, tlProject.getProject(), steps);
      tlContext.addTestCaseToPlan(
          testCase, tlProject.getPlan(), tlProject.getBuild(), tlProject.getProject());
      tlContext.setTestResult(
          testCase,
          tlProject.getPlan(),
          tlProject.getBuild(),
          tlCase.getStatus(),
          testCaseExecutionProtocol.toString(),
          null);
    }
  }

  private void fillTestStepExecutionProtocol(
      TestlinkCase tlCase, StringBuilder testCaseExecutionProtocol) {
    testCaseExecutionProtocol
        .append(tlCase.getTestCaseName())
        .append(": ")
        .append(tlCase.getDuration())
        .append(System.lineSeparator());
  }

  private void fillTestStepExecutionProtocol(
      StringBuilder testCaseExecutionProtocol, TestlinkStep tlStep) {
    testCaseExecutionProtocol
        .append(tlStep.getTestStepName())
        .append(": ")
        .append(tlStep.getStatus())
        .append(System.lineSeparator())
        .append(System.lineSeparator())
        .append("Parameters:")
        .append(System.lineSeparator())
        .append(tlStep.getParameters())
        .append(System.lineSeparator())
        .append(tlStep.getStackTrace());
  }

  private TestlinkCase getTestCase(String instanceName) {
    TestlinkCase result = testCases.get(instanceName);
    if (null == result) {
      result = new TestlinkCase(instanceName);
      testCases.put(instanceName, result);
    }
    return result;
  }

  private String printParameters(Object[] parameters) {
    StringBuilder result = new StringBuilder();
    if (parameters.length > 0) {
      result.append("Parameters:\r\n");
      for (Object o : parameters) {
        result.append(o.getClass().getName()).append(": ").append(o.toString());
      }
    } else {
      result.append("No parameters.\r\n");
    }

    return result.toString();
  }

  private TestlinkStep createTestStep(ITestResult result) {
    TestlinkCase testCase = getTestCase(result.getInstanceName());
    TestlinkStep step =
        new TestlinkStep(
            result.getMethod().getMethodName(), printParameters(result.getParameters()));
    testCase.addStep(step);
    Duration elapsedTime = Duration.ofMillis(result.getEndMillis() - result.getStartMillis());
    testCase.setDuration(
        String.format(
            "%d hours, %d mins, %d seconds",
            elapsedTime.toHours(), elapsedTime.toMinutesPart(), elapsedTime.toSecondsPart()));
    return step;
  }

  private TestCaseStep createTestCaseStep(TestlinkStep tlStep) {
    TestCaseStep result = new TestCaseStep();
    result.setActions("Execute method " + tlStep.getTestStepName());
    result.setExpectedResults("Test runs successfully");
    result.setExecutionType(ExecutionType.AUTOMATED);
    return result;
  }
}
