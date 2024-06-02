package com.researchspace.dao.customliquibaseupdates.v47;

import static com.researchspace.dao.customliquibaseupdates.v47.UpdateFilestoreLinksInFieldContent.OLD_LINK_MATCHER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.dao.customliquibaseupdates.AbstractDBHelpers;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.TextField;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RecordManager;
import java.io.IOException;
import java.util.List;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UpdateFilestoreLinksInFieldContentIT extends AbstractDBHelpers {

  @Autowired private RecordManager recordManager;

  private UpdateFilestoreLinksInFieldContent updater;
  private User user;

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void checkFilestoreLinksUpdate()
      throws IOException, SetupException, CustomChangeException {

    updater = new UpdateFilestoreLinksInFieldContent();
    updater.setUp();

    user = createInitAndLoginAnyUser();

    // check number of initial links
    openTransaction();
    List<TextField> initTextFields = getAllTextFieldsWithLinks(OLD_LINK_MATCHER);
    commitTransaction();

    // create two records
    StructuredDocument doc1 = recordManager.createBasicDocument(user.getRootFolder().getId(), user);
    StructuredDocument doc2 = recordManager.createBasicDocument(user.getRootFolder().getId(), user);

    // add one link to first doc, and two links to second doc

    addOldFilestoreLink(doc1);
    addOldFilestoreLink(doc2);
    addOldFilestoreLink(doc2);

    // assert filestore links have old format
    openTransaction();
    doc1 = (StructuredDocument) recordManager.get(doc1.getId());
    doc2 = (StructuredDocument) recordManager.get(doc2.getId());
    assertOldStyleLink(doc1.getFields().get(0).getData());
    assertOldStyleLink(doc2.getFields().get(0).getData());
    commitTransaction();

    // check the updater is finding the links
    openTransaction();
    List<TextField> textFieldsWithLinks = getAllTextFieldsWithLinks(OLD_LINK_MATCHER);
    commitTransaction();
    assertEquals(initTextFields.size() + 2, textFieldsWithLinks.size());

    // call the updater
    updater.setUp();
    updater.execute(null);

    // check link format is updated now
    openTransaction();
    doc1 = (StructuredDocument) recordManager.get(doc1.getId());
    doc2 = (StructuredDocument) recordManager.get(doc2.getId());
    assertNewStyleLink(doc1.getFields().get(0).getData());
    assertNewStyleLink(doc2.getFields().get(0).getData());
    commitTransaction();
  }

  private void addOldFilestoreLink(Record record) throws IOException {
    String oldFilestoreLink = "<a class=\"nfs_file\" href=\"#\" rel=\"test\">test</a>";
    Field field = ((StructuredDocument) record).getFields().get(0);
    field.setFieldData(field.getFieldData() + " " + oldFilestoreLink);
    recordManager.save(record, record.getOwner());
  }

  private void assertOldStyleLink(String fieldContent) {
    assertTrue(fieldContent, fieldContent.contains("<a class=\"nfs_file\""));
    assertFalse(fieldContent, fieldContent.contains("<a class=\"nfs_file mceNonEditable\""));
  }

  private void assertNewStyleLink(String fieldContent) {
    assertFalse(fieldContent, fieldContent.contains("<a class=\"nfs_file\""));
    assertTrue(fieldContent, fieldContent.contains("<a class=\"nfs_file mceNonEditable\""));
  }
}
