package com.axiope.dao.hibernate.audit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TemporaryCopyLinkedToOriginalCopyPolicy;
import com.researchspace.model.record.TestFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class PermanentEntityFilterTest {

  ObjectAuditFilter filter = new PermanentEntityFilter();
  private User u;

  @Before
  public void setUp() throws Exception {
    u = TestFactory.createAnyUser("user");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testUnattachedFieldFilteredOut() {
    Field tempField = TestFactory.createDateFieldForm().createNewFieldFromForm();
    assertFalse(filter.filter(tempField));
  }

  @Test
  public void testUnattachedFieldFormFilteredOut() {
    FieldForm ft = TestFactory.createDateFieldForm();
    ft.setTemporary(true);
    assertFalse(filter.filter(ft));
  }

  @Test
  public void testUnattachedTemporaryFormFilteredOut() {
    RSForm t = TestFactory.createAnyForm();
    RSForm t2 = t.copy(new TemporaryCopyLinkedToOriginalCopyPolicy());
    assertFalse(filter.filter(t2));
  }

  @Test
  public void testTemporarySDFilteredOut() {
    StructuredDocument tempDoc = new StructuredDocument(new RSForm("temp", "", u));
    tempDoc.setTemporaryDoc(true);
    assertFalse(filter.filter(tempDoc));

    // audit events for non-temp row should be still processed though (RSPAC-1577)
    StructuredDocument mainDoc = new StructuredDocument(new RSForm("main", "", u));
    mainDoc.setTempRecord(tempDoc);
    assertTrue(filter.filter(mainDoc));
  }

  @Test
  public void testAttachedSDWithNoTempFieldSDOK() throws IllegalAddChildOperation {
    StructuredDocument okDoc = new StructuredDocument(new RSForm("name", "", u));
    Folder folder = new Folder();
    folder.addChild(okDoc, new User("any"));
    okDoc.setName("NEWNAME"); // triggers an audit
    assertTrue(filter.filter(okDoc));
    StructuredDocument tempSD = new StructuredDocument(new RSForm("name", "", u));
    tempSD.setTemporaryDoc(true);
    // do not audit if tempfield is set
    okDoc.setTempRecord(tempSD);
    assertFalse(filter.filter(tempSD));
  }

  @Test
  public void testAttachedFieldWithNoTempFieldSDOK() throws IllegalAddChildOperation {
    RSForm form = TestFactory.createAnyForm("t1");
    DateFieldForm dft = TestFactory.createDateFieldForm();
    form.addFieldForm(TestFactory.createDateFieldForm());

    StructuredDocument tempsd1 =
        new RecordFactory()
            .createStructuredDocument("any", TestFactory.createAnyUser("user"), form);
    Folder folder = new Folder();
    folder.addChild(tempsd1, new User("any"));
    Field tempField = dft.createNewFieldFromForm();
    tempsd1.getFields().get(0).setFieldData("1970-23-12"); // need this to trigger an udit
    assertTrue(filter.filter(tempsd1.getFields().get(0)));
    // do not audit if tempfield is set
    tempsd1.getFields().get(0).setTempField(tempField);
    assertFalse(filter.filter(tempField));
  }

  @Test
  public void testDeltaTypeNoRevisionIsFalse() {
    StructuredDocument doc = TestFactory.createAnySD();
    doc.notifyDelta(DeltaType.NOREVISION_UPDATE);
    assertFalse(filter.filter(doc));
    // no delta is ok
    doc.clearDelta();
    assertTrue(filter.filter(doc));
    // any other delta is OK
    doc.notifyDelta(DeltaType.COMMENT);
    assertTrue(filter.filter(doc));
  }
}
