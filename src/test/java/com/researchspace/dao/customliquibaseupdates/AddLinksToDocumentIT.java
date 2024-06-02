package com.researchspace.dao.customliquibaseupdates;

import static org.junit.Assert.assertEquals;

import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.FileNotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AddLinksToDocumentIT extends RealTransactionSpringTestBase {

  AddLinksToDocumentsInFields fieldLinkerTask;

  @Before
  public void before() {
    // created outside spring, as it will be created by liquibase in reality
    fieldLinkerTask = new AddLinksToDocumentsInFields();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testCreateEntityRelationsFromTextFieldAttachmentLinks()
      throws IllegalAddChildOperation, FileNotFoundException, Exception {
    User u = createAndSaveUser(getRandomAlphabeticString("any"));

    initUser(u);
    logoutAndLoginAs(u);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u, "any");
    Field toAdd = doc.getFields().get(0);
    // setup
    openTransaction();
    EcatDocumentFile attachment =
        addAttachmentDocumentToField(RSpaceTestUtils.getResource("genFilesi.txt"), toAdd, u);
    assertEquals(1, attachment.getLinkedFields().size());
    commitTransaction();
    // create links
    openTransaction();
    fieldLinkerTask.setUp();
    fieldLinkerTask.execute(null); // databse not used in method
    commitTransaction();
    // assert links created
    openTransaction();
    attachment = (EcatDocumentFile) recordMgr.get(attachment.getId());
    int fieldSize = attachment.getLinkedFields().size();
    commitTransaction(); // commit transaction before assertion
    assertEquals(1, fieldSize); // lazy loaded ,needs to be in session
  }
}
