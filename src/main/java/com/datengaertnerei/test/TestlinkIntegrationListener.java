package com.datengaertnerei.test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import com.datengaertnerei.test.TestlinkStep.TestStatus;

import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionType;
import br.eti.kinoshita.testlinkjavaapi.model.TestCase;
import br.eti.kinoshita.testlinkjavaapi.model.TestCaseStep;
import br.eti.kinoshita.testlinkjavaapi.model.TestSuite;
import jakarta.xml.soap.AttachmentPart;

public class TestlinkIntegrationListener implements ITestListener {
	private static Log log = LogFactory.getLog(TestlinkIntegrationListener.class);

	private TestlinkIntegrationContext tlContext;
	private TestlinkProject tlProject;
	private Map<String, TestlinkCase> testCases;
	private Map<String, AttachmentPart> attachments;

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
		testCases.computeIfAbsent(result.getInstanceName(), t -> new TestlinkCase(result.getInstanceName()))
				.setStatus(ExecutionStatus.PASSED);

		transferAttachments(result);
	}

	@Override
	public void onTestFailure(ITestResult result) {
		if (tlProject == null) {
			return;
		}
		TestlinkStep step = createTestStep(result);
		step.setStatus(TestStatus.FAILED);
		step.setStackTrace(ExceptionUtils.getStackTrace(result.getThrowable()));
		testCases.computeIfAbsent(result.getInstanceName(), t -> new TestlinkCase(result.getInstanceName()))
				.setStatus(ExecutionStatus.FAILED);

		transferAttachments(result);
	}

	private void transferAttachments(ITestResult result) {
		if (result.getInstance() instanceof ContainsAttachmentsTestCase) {
			ContainsAttachmentsTestCase attachmentContainer = (ContainsAttachmentsTestCase) result.getInstance();
			List<AttachmentPart> attachmentList = attachmentContainer
					.getAttachments(result.getMethod().getMethodName());
			attachmentList
					.forEach(attachment -> attachments.put(attachment.getContentId() == null ? attachment.toString() // workaround
																														// if
																														// not
																														// content
																														// id
																														// is
																														// given
							: attachment.getContentId(), attachment));
		}
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
		step.setStackTrace(ExceptionUtils.getStackTrace(result.getThrowable()));
		testCases.computeIfAbsent(result.getInstanceName(), t -> new TestlinkCase(result.getInstanceName()))
				.setStatus(ExecutionStatus.PASSED);

		transferAttachments(result);
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
		tlProject.setBuild(tlContext.getBuild(tlProject.getPlan(), buildName));

		testCases = new ConcurrentHashMap<>();
		attachments = new ConcurrentHashMap<>();

		log.info(new StringBuilder("TestlinkIntegrationListener starting for TestSuite ")
				.append(context.getSuite().getName()).append(" and Context ").append(context.getName()));
	}

	@Override
	public void onFinish(ITestContext context) {
		if (tlProject == null) {
			return;
		}
		TestSuite suite = tlContext.getSuite(tlProject.getProject(), context.getSuite().getName());
		List<TestCase> testLinkCases = new LinkedList<>();
		List<TestCase> existingCases = tlContext.getTestCases(tlProject.getPlan(), tlProject.getBuild());
		Map<TestCase, ExecutionStatus> statusMap = new HashMap<>();
		Map<TestCase, String> protocolMap = new HashMap<>();

		boolean newPlan = false;

		for (TestlinkCase tlCase : testCases.values()) {
			List<TestCaseStep> steps = new ArrayList<>();
			StringBuilder testCaseExecutionProtocol = new StringBuilder();
			fillTestStepExecutionProtocol(tlCase, testCaseExecutionProtocol);
			int number = 0;
			for (TestlinkStep tlStep : tlCase.getSteps()) {
				TestCaseStep step = createTestCaseStep(tlStep);
				step.setNumber(number++);
				steps.add(step);
				fillTestStepExecutionProtocol(testCaseExecutionProtocol, tlStep);
			}

			TestCase testCase = tlContext.createTestCase(tlCase.getTestCaseName(), suite, tlProject.getProject(),
					steps);
			testLinkCases.add(testCase);
			statusMap.put(testCase, tlCase.getStatus());
			protocolMap.put(testCase, testCaseExecutionProtocol.toString());
			for (TestCase tc : existingCases) {
				if (testCase.getId().equals(tc.getId()) && testCase.getVersion() > tc.getVersion()) {
					newPlan = true; // new testcase version of already assigned test - new test plan needed
				}
			}
		}

		// it is not possible to change the existing testcase in the plan, so we create
		// a new plan
		if (newPlan) {
			tlProject.setPlan(tlContext.createPlan(tlProject.getProject()));
			tlProject.setBuild(tlContext.getBuild(tlProject.getPlan(), tlProject.getBuild().getName()));
		}

		for (TestCase testCase : testLinkCases) {
			tlContext.addTestCaseToPlan(testCase, tlProject.getPlan(), tlProject.getBuild(), tlProject.getProject());
			Integer executionId = tlContext.setTestResult(testCase, tlProject.getPlan(), tlProject.getBuild(),
					statusMap.get(testCase), protocolMap.get(testCase), null);
			attachments.values().forEach(attachment -> tlContext.saveAttachment(executionId, attachment));
		}
	}

	private void fillTestStepExecutionProtocol(TestlinkCase tlCase, StringBuilder testCaseExecutionProtocol) {
		testCaseExecutionProtocol.append(tlCase.getTestCaseName()).append(": ").append(tlCase.getDuration())
				.append(System.lineSeparator());
	}

	private void fillTestStepExecutionProtocol(StringBuilder testCaseExecutionProtocol, TestlinkStep tlStep) {
		testCaseExecutionProtocol.append(tlStep.getTestStepName()).append(": ").append(tlStep.getStatus())
				.append(System.lineSeparator()).append(System.lineSeparator()).append("Parameters:")
				.append(System.lineSeparator()).append(tlStep.getParameters()).append(System.lineSeparator())
				.append(tlStep.getStackTrace());
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
		TestlinkCase testCase = testCases.computeIfAbsent(result.getInstanceName(),
				t -> new TestlinkCase(result.getInstanceName()));
		TestlinkStep step = new TestlinkStep(result.getMethod().getMethodName(),
				printParameters(result.getParameters()));
		testCase.addStep(step);
		Duration elapsedTime = Duration.ofMillis(result.getEndMillis() - result.getStartMillis());
		testCase.setDuration(String.format("%d hours, %d mins, %d seconds", elapsedTime.toHours(),
				elapsedTime.toMinutesPart(), elapsedTime.toSecondsPart()));
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
