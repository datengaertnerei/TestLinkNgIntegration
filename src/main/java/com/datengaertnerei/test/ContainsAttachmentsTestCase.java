package com.datengaertnerei.test;

import java.util.List;

import jakarta.xml.soap.AttachmentPart;

public interface ContainsAttachmentsTestCase {

  public List<AttachmentPart> getAttachments();

  public List<AttachmentPart> getAttachments(String testMethod);
}
