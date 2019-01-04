package com.datengaertnerei.testng;

import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.model.Build;
import br.eti.kinoshita.testlinkjavaapi.model.TestPlan;
import br.eti.kinoshita.testlinkjavaapi.model.TestProject;
import org.testng.annotations.Test;

public class TestLinkTest {

	@Test
	public void testCase() {
		TestlinkCase testCase = new TestlinkCase("NewTestCase");
		testCase.setDuration("1 Min.");
		testCase.setStatus(ExecutionStatus.BLOCKED);
	}
	
	@Test
	public void testProject() {
		TestlinkProject testProject = new TestlinkProject(new TestProject());
		testProject.setBuild(new Build());
		testProject.setPlan(new TestPlan());
	}
	
}
