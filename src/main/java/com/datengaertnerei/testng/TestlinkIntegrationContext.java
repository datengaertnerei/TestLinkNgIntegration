package com.datengaertnerei.testng;

import br.eti.kinoshita.testlinkjavaapi.TestLinkAPI;
import br.eti.kinoshita.testlinkjavaapi.constants.ActionOnDuplicate;
import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionType;
import br.eti.kinoshita.testlinkjavaapi.constants.TestImportance;
import br.eti.kinoshita.testlinkjavaapi.constants.TestLinkMethods;
import br.eti.kinoshita.testlinkjavaapi.constants.TestLinkParams;
import br.eti.kinoshita.testlinkjavaapi.model.Build;
import br.eti.kinoshita.testlinkjavaapi.model.ReportTCResultResponse;
import br.eti.kinoshita.testlinkjavaapi.model.TestCase;
import br.eti.kinoshita.testlinkjavaapi.model.TestCaseStep;
import br.eti.kinoshita.testlinkjavaapi.model.TestPlan;
import br.eti.kinoshita.testlinkjavaapi.model.TestProject;
import br.eti.kinoshita.testlinkjavaapi.model.TestSuite;
import br.eti.kinoshita.testlinkjavaapi.util.TestLinkAPIException;
import br.eti.kinoshita.testlinkjavaapi.util.Util;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;

/**
 * Singleton integration context for TestLink API calls.
 *
 * @author Jens Dibbern
 */
public class TestlinkIntegrationContext {

  private static final String EXIST = "exist";
  private static final String CONSTANT_NAME = "TestNGAutomation";
  private static final String CREATED_BY_TLNGI = "Created by TestLinkNGIntegration.";
  private static final String CFG_REMOTE_URL = "TestlinkIntegrationContext.RemoteURL";
  private static final String CFG_API_KEY = "TestlinkIntegrationContext.APIKey";
  private static final String CFG_USER = "TestlinkIntegrationContext.AutomationUser";

  private static Log log = LogFactory.getLog(TestlinkIntegrationContext.class);
  private static TestlinkIntegrationContext singletonInstance;
  private String apiKey;
  private String remoteUrl;
  private String automationUser;
  private TestLinkAPI remoteApi;

  /** Private ctor initializes singleton instance. */
  private TestlinkIntegrationContext() {
    apiKey = System.getProperty(CFG_API_KEY);
    remoteUrl = System.getProperty(CFG_REMOTE_URL);
    automationUser = System.getProperty(CFG_USER);

    if (null != apiKey && null != remoteUrl) {
      try {
        remoteApi = new TestLinkAPI(new URL(remoteUrl), apiKey);
        if (!remoteApi.doesUserExist(automationUser)) {
          log.error("Automation user does not exist: " + automationUser);
        }
      } catch (TestLinkAPIException | MalformedURLException initException) {
        log.fatal("Cannot establish remote TestLink connection", initException);
      }
    }
  }

  /**
   * Access method to get the singleton instance.
   *
   * @return the singleton instance of the integration context
   */
  public static synchronized TestlinkIntegrationContext getInstance() {
    if (null == singletonInstance) {
      singletonInstance = new TestlinkIntegrationContext();
      log.debug("TestlinkIntegrationContext initialized.");
    }

    return singletonInstance;
  }

  /**
   * Provides an existing or newly created TestLink project with the given name.
   *
   * @param projectName the name of the TestLink project
   * @return proxy project object as reference for future calls
   */
  public TestlinkProject getProject(String projectName) {
    checkConnection();
    TestProject project;
    try {
      project = remoteApi.getTestProjectByName(projectName);
    } catch (TestLinkAPIException notFoundException) {
      checkApiExceptionNotFound(notFoundException);
      // create project prefix by shortening project name
      String[] projectNameParts = projectName.split("\\.");
      String projectPrefix =
          projectNameParts[projectNameParts.length - 1].replaceAll("[AEIOUaeiou]", "");

      project =
          remoteApi.createTestProject(
              projectName, projectPrefix, CREATED_BY_TLNGI, true, true, true, false, true, true);
    }

    TestlinkProject result = new TestlinkProject(project);
    result.setPlan(getPlan(project));
    return result;
  }

  /**
   * Provides an existing or newly created execution plan.
   *
   * @param project
   * @return
   */
  private TestPlan getPlan(TestProject project) {
    checkConnection();
    TestPlan plan;
    try {
      plan = remoteApi.getTestPlanByName(CONSTANT_NAME, project.getName());
    } catch (TestLinkAPIException notFoundException) {
      checkApiExceptionNotFound(notFoundException);
      plan =
          remoteApi.createTestPlan(CONSTANT_NAME, project.getName(), CREATED_BY_TLNGI, true, true);
    }
    return plan;
  }

  /**
   * Provides an existing or newly created build for test execution.
   *
   * @param plan
   * @param buildName
   * @return
   */
  public Build getBuild(TestPlan plan, String buildName) {
    checkConnection();
    try {
      Build[] buildArray = remoteApi.getBuildsForTestPlan(plan.getId());
      if (null != buildArray) {
        for (Build build : buildArray) {
          if (build.getName().equals(buildName)) {
            return build;
          }
        }
      }
    } catch (TestLinkAPIException notFoundException) {
      checkApiExceptionNotFound(notFoundException);
    }

    return remoteApi.createBuild(plan.getId(), buildName, CREATED_BY_TLNGI);
  }

  /**
   * @param project
   * @param suiteName
   * @return
   */
  public TestSuite getSuite(TestProject project, String suiteName) {
    checkConnection();

    // find existing suite
    TestSuite[] suitesArray = null;
    try {
      suitesArray = remoteApi.getFirstLevelTestSuitesForTestProject(project.getId());
    } catch (TestLinkAPIException notFoundException) {
      checkApiExceptionNotFound(notFoundException);
    }
    if (null != suitesArray) {
      for (TestSuite suite : suitesArray) {
        if (suite.getName().equals(suiteName)) {
          suite.setTestProjectId(project.getId());
          return suite;
        }
      }
    }

    // not found, create new
    TestSuite suite =
        remoteApi.createTestSuite(
            project.getId(), suiteName, CREATED_BY_TLNGI, null, 0, true, ActionOnDuplicate.BLOCK);
    suite.setTestProjectId(project.getId());
    return suite;
  }

  /**
   * @param suite
   * @return
   */
  public List<TestCase> getTestCases(TestSuite suite) {
    checkConnection();
    try {
      TestCase[] casesArray = remoteApi.getTestCasesForTestSuite(suite.getId(), false, null);
      if (null != casesArray) {
        return Arrays.asList(casesArray);
      }
    } catch (TestLinkAPIException notFoundException) {
      checkApiExceptionNotFound(notFoundException);
    }
    return new ArrayList<>();
  }

  /**
   * @param plan
   * @param build
   * @return
   */
  public List<TestCase> getTestCases(TestPlan plan, Build build) {
    checkConnection();

    try {
      TestCase[] casesArray =
          remoteApi.getTestCasesForTestPlan(
              plan.getId(), null, build.getId(), null, null, null, null, null, null, true, null);
      if (null != casesArray) {
        return Arrays.asList(casesArray);
      }
    } catch (TestLinkAPIException notFoundException) {
      checkApiExceptionNotFound(notFoundException);
    }
    return new ArrayList<>();
  }

  /**
   * @param caseName
   * @param suite
   * @param project
   * @param steps
   * @return
   */
  public TestCase createTestCase(
      String caseName, TestSuite suite, TestProject project, List<TestCaseStep> steps) {

    TestCase testCase = null;

    try {
      Integer id =
          remoteApi.getTestCaseIDByName(caseName, suite.getName(), project.getName(), null);
      testCase = remoteApi.getTestCase(id, null, null);
      if (compareSteps(steps, testCase.getSteps())) {
        return testCase;
      }
    } catch (TestLinkAPIException notFoundException) {
      checkApiExceptionNotFound(notFoundException);
    }

    testCase =
        remoteApi.createTestCase(
            caseName,
            suite.getId(),
            suite.getTestProjectId(),
            automationUser,
            CREATED_BY_TLNGI,
            steps,
            null,
            TestImportance.MEDIUM,
            ExecutionType.AUTOMATED,
            null,
            null,
            true,
            ActionOnDuplicate.CREATE_NEW_VERSION);

    // read newly created testcase to populate all fields
    return remoteApi.getTestCase(testCase.getId(), null, null);
  }

  /**
   * @param steps
   * @param existingSteps
   * @return
   */
  private boolean compareSteps(List<TestCaseStep> steps, List<TestCaseStep> existingSteps) {
    if (existingSteps.size() != steps.size()) {
      return false;
    }
    Set<String> actions = new HashSet<>();
    for (TestCaseStep step : existingSteps) {
      actions.add(step.getActions());
    }
    for (TestCaseStep step : steps) {
      if (actions.contains(step.getActions())) {
        actions.remove(step.getActions());
      } else {
        return false;
      }
    }
    return actions.isEmpty();
  }

  /**
   * @param testCase
   * @param plan
   * @param build
   * @param project
   */
  public void addTestCaseToPlan(
      TestCase testCase, TestPlan plan, Build build, TestProject project) {

    List<TestCase> testCases = getTestCases(plan, build);
    for (TestCase tc : testCases) {
      if (testCase.getId().equals(tc.getId()) && testCase.getVersion() <= tc.getVersion()) {
        return;
      }
    }

    remoteApi.addTestCaseToTestPlan(
        project.getId(), plan.getId(), testCase.getId(), testCase.getVersion(), null, null, null);
  }

  /**
   * @param testCase
   * @param plan
   * @param build
   * @param status
   * @param notes
   * @param stepResults
   * @return
   */
  public Integer setTestResult(
      TestCase testCase,
      TestPlan plan,
      Build build,
      ExecutionStatus status,
      String notes,
      List<Map<String, Object>> stepResults) {

    ReportTCResultResponse result =
        reportTestCaseResult(
            testCase.getId(), plan.getId(), status, build.getId(), notes, stepResults);

    return result == null ? null : result.getExecutionId();
  }

  /**
   * @param testCaseId
   * @param testPlanId
   * @param status
   * @param buildId
   * @param notes
   * @param steps
   * @return
   */
  private ReportTCResultResponse reportTestCaseResult(
      Integer testCaseId,
      Integer testPlanId,
      ExecutionStatus status,
      Integer buildId,
      String notes,
      List<Map<String, Object>> steps) {
    ReportTCResultResponse reportTestCaseResultResponse = null;

    try {
      Map<String, Object> executionData = new HashMap<>();
      executionData.put(TestLinkParams.TEST_CASE_ID.toString(), testCaseId);
      executionData.put(TestLinkParams.TEST_CASE_EXTERNAL_ID.toString(), null);
      executionData.put(TestLinkParams.TEST_PLAN_ID.toString(), testPlanId);
      executionData.put(TestLinkParams.STATUS.toString(), status.toString());
      executionData.put(TestLinkParams.BUILD_ID.toString(), buildId);
      executionData.put(TestLinkParams.BUILD_NAME.toString(), null);
      executionData.put(TestLinkParams.NOTES.toString(), notes);
      executionData.put(TestLinkParams.GUESS.toString(), true);
      executionData.put(TestLinkParams.BUG_ID.toString(), null);
      executionData.put(TestLinkParams.PLATFORM_ID.toString(), null);
      executionData.put(TestLinkParams.PLATFORM_NAME.toString(), null);
      executionData.put(TestLinkParams.CUSTOM_FIELDS.toString(), null);
      executionData.put(TestLinkParams.OVERWRITE.toString(), false);
      executionData.put(TestLinkParams.STEPS.toString(), steps);
      Object response =
          remoteApi.executeXmlRpcCall(TestLinkMethods.REPORT_TC_RESULT.toString(), executionData);
      // the error verification routine is called inside
      // super.executeXml...
      if (response instanceof Object[]) {
        Object[] responseArray = Util.castToArray(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) responseArray[0];

        reportTestCaseResultResponse = Util.getReportTCResultResponse(responseMap);
      }
    } catch (XmlRpcException xmlrpcex) {
      throw new TestLinkAPIException(
          "Error reporting TC result: " + xmlrpcex.getMessage(), xmlrpcex);
    }

    return reportTestCaseResultResponse;
  }

  /**
   * Checks connection to avoid strange NullPointerExceptions. Throws TestLinkAPIException if no
   * connection is established.
   */
  private void checkConnection() {
    if (null == remoteApi) {
      throw new TestLinkAPIException("No TestLink connection.");
    }
  }

  /**
   * Workaround to identify "... does not exist|no ... exists" exception from other TestLink API
   * exceptions.
   *
   * @param notFoundException
   */
  private void checkApiExceptionNotFound(TestLinkAPIException notFoundException) {
    if (!notFoundException.getMessage().contains(EXIST)) {
      throw notFoundException;
    }
  }
}
