package com.researchspace.model.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.field.FieldForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TemporaryCopyLinkedToOriginalCopyPolicyTest {
  IFormCopyPolicy<RSForm> copier;

  @BeforeEach
  public void setUp() throws Exception {
    copier = new TemporaryCopyLinkedToOriginalCopyPolicy();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testCopy() throws InterruptedException {
    RSForm t1 = TestFactory.createAnyForm("t");
    FieldForm ft = TestFactory.createDateFieldForm();
    t1.addFieldForm(ft);
    Thread.sleep(2);
    RSForm copy = copier.copy(t1);

    // check relations of template
    assertNotNull(t1.getTempForm());
    assertNull(copy.getTempForm());
    assertEquals(t1.getTempForm(), copy);

    // and of fields
    FieldForm ftCpy = copy.getFieldForms().get(copy.getFieldForms().size() - 1);
    assertNull(ftCpy.getTempFieldForm());
    assertEquals(ftCpy, ft.getTempFieldForm());

    assertTrue(copy.getCreationDateAsDate().after(t1.getCreationDateAsDate()));

    assertEquals(t1.getNumActiveFields(), copy.getNumActiveFields());
    assertEquals(t1.getNumAllFields(), copy.getNumAllFields());
  }
}
