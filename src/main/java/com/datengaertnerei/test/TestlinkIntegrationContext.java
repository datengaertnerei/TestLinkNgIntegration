package com.datengaertnerei.test;

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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;
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
  private static final String CONSTANT_NAME = "TestNGAutomation-";
  private static final String CREATED_BY_TLNGI = "Created by TestLinkNGIntegration.";
  private static final String CFG_REMOTE_URL = "TestlinkIntegrationContext.RemoteURL";
  private static final String CFG_API_KEY = "TestlinkIntegrationContext.APIKey";
  private static final String CFG_USER = "TestlinkIntegrationContext.AutomationUser";

  private static Log log = LogFactory.getLog(TestlinkIntegrationContext.class);
  private static TestlinkIntegrationContext singletonInstance;
  private String automationUser;
  private TestLinkAPI remoteApi;

  /** Private ctor initializes singleton instance. */
  private TestlinkIntegrationContext() {
    String apiKey = System.getProperty(CFG_API_KEY);
    String remoteUrl = System.getProperty(CFG_REMOTE_URL);
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
   * Provides an existing or newly created execution plan. There will be only one execution plan
   * with the constant name TestNGAutomation.
   *
   * @param project the TestLink project for the plan
   * @return the execution plan
   */
  private TestPlan getPlan(TestProject project) {
    checkConnection();
    TestPlan plan = null;
    try {
      TestPlan[] plans = remoteApi.getProjectTestPlans(project.getId());
      for (TestPlan existingPlan : plans) {
        if (existingPlan.getName().startsWith(CONSTANT_NAME)
            && (plan == null || plan.getId() < existingPlan.getId())) {
          plan = existingPlan;
        }
      }

      if (null == plan) {
        plan = createPlan(project);
      }

    } catch (TestLinkAPIException notFoundException) {
      checkApiExceptionNotFound(notFoundException);
    }
    return plan;
  }

  /**
   * Create a new test plan for TestNG automation.
   *
   * @param project the TestProject the plan will refer to
   * @return the new TestPlan object
   */
  protected TestPlan createPlan(TestProject project) {
    LocalDateTime now = LocalDateTime.now();
    return remoteApi.createTestPlan(
        CONSTANT_NAME + now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        project.getName(),
        CREATED_BY_TLNGI,
        true,
        true);
  }

  /**
   * Provides an existing or newly created build for test execution.
   *
   * @param plan the TestLink execution plan for the build
   * @param buildName the name for the new build, usually the Maven version
   * @return the build
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
   * Provides a TestLink suite for the given name.
   *
   * @param project the TestLink project
   * @param suiteName the name of the test suite (usually from testng.xml)
   * @return the TestLink test suite
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
   * Fetches all TestLink test cases for plan/build combination.
   *
   * @param plan the execution plan
   * @param build the build, usually the Maven version
   * @return a list of TestCase objects from TestLink
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
   * Creates a new TestLink test case.
   *
   * @param caseName the name of the test case
   * @param suite the TestLink test suite
   * @param project the TestLink project
   * @param steps the list of test steps
   * @return the new TestLink testcase
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
   * Compares test steps of test cases to determine if a new version is needed.
   *
   * @param steps list of current steps
   * @param existingSteps list of previously created steps
   * @return true if both lists contain the same steps.
   */
  private boolean compareSteps(List<TestCaseStep> steps, List<TestCaseStep> existingSteps) {
    if (existingSteps.size() != steps.size()) {
      return false;
    }
    Set<String> actions = new HashSet<>();
    existingSteps.forEach(step -> actions.add(step.getActions()));
    for (TestCaseStep step : steps) { // no lambda removeIf here because of "else return false"
      if (actions.contains(step.getActions())) {
        actions.remove(step.getActions());
      } else {
        return false;
      }
    }
    return actions.isEmpty();
  }

  /**
   * Adds a test case to an execution plan in TestLink.
   *
   * @param testCase the TestLink test case
   * @param plan the TestLink execution plan
   * @param build the TestLink build
   * @param project the TestLink project
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
   * Saves file attachment to existing test execution.
   *
   * @param executionId identifier for existing test execution
   * @param attachment the file attachment
   */
  public void saveAttachment(Integer executionId, AttachmentPart attachment) {
    try {
      String content =
          new BufferedReader(new InputStreamReader(attachment.getBase64Content()))
              .lines()
              .collect(Collectors.joining("\n"));
      remoteApi.uploadExecutionAttachment(
          executionId,
          attachment.getContentId() == null ? attachment.toString() : attachment.getContentId(),
          CREATED_BY_TLNGI,
          attachment.getContentLocation() == null
              ? attachment.toString()
              : attachment.getContentLocation(),
          attachment.getContentType(),
          content);
    } catch (SOAPException transferException) {
      log.error("Could not save attachment.", transferException);
    }
  }

  /**
   * Wraps reportTestCaseResult and returns just the execution id.
   *
   * @param testCase the TestLink test case
   * @param plan the TestLink execution plan
   * @param build the TestLink build
   * @param status the ExecutionStatus (passed, failed etc.)
   * @param notes the test execution protocol
   * @param stepResults the detailed results of each test step
   * @return the execution id (for additional uploads)
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
   * Saves test case execution result and protocol to TestLink.
   *
   * @param testCaseId the TestLink test case id
   * @param testPlanId the TestLink execution plan id
   * @param status the ExecutionStatus (passed, failed etc.)
   * @param buildId the TestLink build id
   * @param notes the test execution protocol
   * @param steps the detailed results of each test step
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
   * @param notFoundException the exception not related to missing objects
   */
  private void checkApiExceptionNotFound(TestLinkAPIException notFoundException) {
    if (!notFoundException.getMessage().contains(EXIST)) {
      throw notFoundException;
    }
  }
}
