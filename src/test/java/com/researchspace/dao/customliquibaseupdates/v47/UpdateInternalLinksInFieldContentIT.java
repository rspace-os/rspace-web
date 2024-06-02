package com.researchspace.dao.customliquibaseupdates.v47;

import static com.researchspace.dao.customliquibaseupdates.v47.UpdateInternalLinksInFieldContent.OLD_LINK_MATCHER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.dao.customliquibaseupdates.AbstractDBHelpers;
import com.researchspace.model.InternalLink;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.TextField;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.InternalLinkManager;
import com.researchspace.service.RecordManager;
import java.io.IOException;
import java.util.List;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UpdateInternalLinksInFieldContentIT extends AbstractDBHelpers {

  private @Autowired RecordManager recordManager;
  private @Autowired InternalLinkManager internalLinkManager;

  private UpdateInternalLinksInFieldContent updater;
  private User user;

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void checkInternalLinkCreation()
      throws IOException, SetupException, CustomChangeException {

    updater = new UpdateInternalLinksInFieldContent();
    updater.setUp();

    user = createInitAndLoginAnyUser();

    // check number of initial links
    openTransaction();
    List<TextField> initTextFields = getAllTextFieldsWithLinks(OLD_LINK_MATCHER);
    commitTransaction();

    // create three records
    StructuredDocument doc1 = recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    StructuredDocument doc2 = recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    StructuredDocument targetDoc =
        recordManager.createBasicDocument(user.getRootFolder().getId(), user);

    List<InternalLink> initialLinks =
        internalLinkManager.getLinksPointingToRecord(targetDoc.getId());
    assertTrue(initialLinks.isEmpty());

    // add links to target record to first two records

    addOldInternalLink(doc1, targetDoc);
    addOldInternalLink(doc2, targetDoc);

    // check the internal links are not created yet
    List<InternalLink> initialLinks2 =
        internalLinkManager.getLinksPointingToRecord(targetDoc.getId());
    assertTrue(initialLinks2.isEmpty());

    // assert internal links have old format
    openTransaction();
    doc1 = (StructuredDocument) recordManager.get(doc1.getId());
    doc2 = (StructuredDocument) recordManager.get(doc2.getId());
    assertOldStyleLink(doc1.getFields().get(0).getData(), targetDoc.getId());
    assertOldStyleLink(doc2.getFields().get(0).getData(), targetDoc.getId());
    commitTransaction();

    // check the updater is finding the links
    openTransaction();
    List<TextField> textFieldsWithLinks = getAllTextFieldsWithLinks(OLD_LINK_MATCHER);
    commitTransaction();
    assertEquals(initTextFields.size() + 2, textFieldsWithLinks.size());

    // call the updater
    updater.setUp();
    updater.execute(null);

    // check internal links created from doc1 and doc2
    List<InternalLink> createdLinks =
        internalLinkManager.getLinksPointingToRecord(targetDoc.getId());
    assertEquals(2, createdLinks.size());

    // check link format is updated now
    openTransaction();
    doc1 = (StructuredDocument) recordManager.get(doc1.getId());
    doc2 = (StructuredDocument) recordManager.get(doc2.getId());
    assertNewStyleLink(doc1.getFields().get(0).getData(), targetDoc.getId());
    assertNewStyleLink(doc2.getFields().get(0).getData(), targetDoc.getId());
    commitTransaction();
  }

  private void addOldInternalLink(Record record, BaseRecord linkTarget) throws IOException {
    String oldInternalLink =
        "<a id=\""
            + linkTarget.getId()
            + "\" class=\"linkedRecord\" "
            + "href=\"/workspace/editor/structuredDocument/"
            + linkTarget.getId()
            + "\" "
            + "data-name=\"record1\">record1</a>";
    Field field = ((StructuredDocument) record).getFields().get(0);
    field.setFieldData(field.getFieldData() + " " + oldInternalLink);
    recordMgr.save(record, record.getOwner());
  }

  private void assertOldStyleLink(String fieldContent, Long targetDocId) {
    assertFalse(fieldContent, fieldContent.contains("data-globalid"));
    assertTrue(fieldContent, fieldContent.contains("class=\"linkedRecord\""));
    assertTrue(
        fieldContent, fieldContent.contains("/workspace/editor/structuredDocument/" + targetDocId));
  }

  private void assertNewStyleLink(String fieldContent, Long targetDocId) {
    assertTrue(fieldContent, fieldContent.contains("data-globalid=\"SD" + targetDocId + "\""));
    assertTrue(fieldContent, fieldContent.contains("class=\"linkedRecord mceNonEditable\""));
    assertTrue(fieldContent, fieldContent.contains("href=\"/globalId/SD" + targetDocId + "\""));
  }
}
