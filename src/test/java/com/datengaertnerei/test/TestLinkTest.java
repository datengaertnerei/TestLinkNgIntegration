package com.datengaertnerei.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.sun.xml.messaging.saaj.soap.AttachmentPartImpl;

import br.eti.kinoshita.testlinkjavaapi.constants.ExecutionStatus;
import br.eti.kinoshita.testlinkjavaapi.model.Build;
import br.eti.kinoshita.testlinkjavaapi.model.TestPlan;
import br.eti.kinoshita.testlinkjavaapi.model.TestProject;
import jakarta.xml.soap.AttachmentPart;

public class TestLinkTest implements ContainsAttachmentsTestCase {

  private List<AttachmentPart> attachments;
  
  @BeforeClass
  public void init() {
	  attachments = Collections.synchronizedList(new LinkedList<>());
  }

  @Test
  public void testCase() {
    TestlinkCase testCase = new TestlinkCase("NewTestCase");
    testCase.setDuration("1 Min.");
    testCase.setStatus(ExecutionStatus.PASSED);
  }
  
  @Test
  public void testProject() {
    TestlinkProject testProject = new TestlinkProject(new TestProject());
    testProject.setBuild(new Build());
    testProject.setPlan(new TestPlan());
    try {
      BufferedImage image = ImageIO.read(ClassLoader.getSystemResourceAsStream("test.png"));
      AttachmentPart att = new AttachmentPartImpl();
      att.setContent(image, "image/png");
      att.setContentId("test.png");
      att.setContentLocation("test.png");
      attachments.add(att);
    } catch (IOException e) { 
      e.printStackTrace();
    }
  }

  @Override
  public List<AttachmentPart> getAttachments() {
    return attachments;
  }

  @Override
  public List<AttachmentPart> getAttachments(String testMethod) {
    return attachments;
  }
}
