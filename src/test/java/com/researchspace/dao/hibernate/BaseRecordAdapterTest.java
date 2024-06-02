package com.researchspace.dao.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BaseRecordAdapterTest extends SpringTransactionalTest {

  private @Autowired BaseRecordAdaptable adapter;
  User user;

  @Before
  public void setup() {
    user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
  }

  @Test
  public void testGetAsBaseRecordCacheStategy() throws IOException {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any");
    assertEquals(doc, adapter.getAsBaseRecord(doc).get());
    // returns itself
    assertTrue(doc == adapter.getAsBaseRecord(doc).get());
    Field field = doc.getFields().get(0);

    RSChemElement el = addChemStructureToField(field, user);
    // to test cache strategy let's make record field empty
    el.setRecord(null);
    BaseRecord adapted = adapter.getAsBaseRecord(el).get();
    assertEquals(doc, adapted);
    // gets object from DB, a new instance
    assertFalse(doc == adapted);
    // now call again, should get cached value, which should be the same object instance
    assertEquals(adapted, adapter.getAsBaseRecord(el).get());
    assertTrue(doc == adapter.getAsBaseRecord(doc).get());

    // add image annotation
    EcatImageAnnotation ann = addImageAnnotationToField(field, user);
    assertEquals(adapted, adapter.getAsBaseRecord(ann).get());

    // a delted record also return null
    doc.setRecordDeleted(true);
    assertFalse(adapter.getAsBaseRecord(doc).isPresent());
  }
}
