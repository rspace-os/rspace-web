package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class FieldManagerTest extends SpringTransactionalTest {

  private Logger log = LoggerFactory.getLogger(FieldManagerTest.class);
  @Autowired private FieldManager mgr;

  @Test
  public void linkMediaFileToField() throws Exception {

    User user = createInitAndLoginAnyUser();
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(user, "any");

    Field textField = sdoc.getFields().get(0);
    EcatMediaFile file = addImageToFieldButNoFieldAttachment(textField, user, null);

    file = mgr.addMediaFileLink(file.getId(), user, textField.getId(), false).get().getMediaFile();
    textField = mgr.get(textField.getId(), user).get();
    assertEquals(1, textField.getLinkedMediaFiles().size());
    assertEquals(1, file.getLinkedFields().size());

    User otherUser = createInitAndLoginAnyUser();
    StructuredDocument otherDoc = createBasicDocumentInRootFolderWithText(otherUser, "any");

    Field othertextField = otherDoc.getFields().get(0);
    EcatMediaFile otherfile = addImageToFieldButNoFieldAttachment(othertextField, otherUser, null);

    assertFalse(
        mgr.addMediaFileLink(otherfile.getId(), user, textField.getId(), false).isPresent());

    logoutAndLoginAs(user);

    // now try unlinking, both collections should be empty
    textField.setMediaFileLinkDeleted(file, true);
    mgr.save(textField, user);
    // now reload:
    assertTrue(
        mgr.get(textField.getId(), user).get().getLinkedMediaFiles().iterator().next().isDeleted());
  }
}
