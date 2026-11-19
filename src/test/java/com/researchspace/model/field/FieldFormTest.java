package com.researchspace.model.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class FieldFormTest {

  @Test
  public void getSummaryDoesNotThrowNPEForUnspecifiedValuesTest() {
    FieldForm[] fts = createArrayOfEmptyFieldForms();

    for (FieldForm ft : fts) {
      ft.getSummary();
    }
  }

  private FieldForm[] createArrayOfEmptyFieldForms() {
    FieldForm[] fts = new FieldForm[8];

    fts[0] = new ChoiceFieldForm();
    fts[1] = new DateFieldForm();
    fts[2] = new NumberFieldForm();
    fts[3] = new RadioFieldForm();
    fts[4] = new TextFieldForm();
    fts[5] = new TimeFieldForm();
    fts[6] = new StringFieldForm();
    fts[7] = new ReferenceFieldForm();
    return fts;
  }

  @Test
  public void testEqualsHashCodeConsistentWithComparable() {

    FieldForm[] fts1 = createArrayOfEmptyFieldForms();
    FieldForm[] fts2 = createArrayOfEmptyFieldForms();
    for (int i = 0; i < fts1.length; i++) {
      // ensure modification dates are the same
      fts2[i].setModificationDate(fts1[i].getModificationDate());
      assertEquals(fts1[i], fts2[i]);
      assertEquals(0, fts1[i].compareTo(fts2[i]));
      assertEquals(fts1[i].hashCode(), fts2[i].hashCode());
    }
  }

  @Test
  public void copiesKeepTheFieldKeyAndDisplayRule() {
    String rule = "{\"sourceFieldKey\":\"k\",\"operator\":\"HAS_VALUE\"}";
    FieldForm[] fields = createArrayOfEmptyFieldForms();
    FieldForm[] all = new FieldForm[fields.length + 2];
    System.arraycopy(fields, 0, all, 0, fields.length);
    all[fields.length] = new URIFieldForm();
    all[fields.length + 1] = new AttachmentFieldForm();
    for (FieldForm field : all) {
      String key = UUID.randomUUID().toString();
      field.setFieldKey(key);
      field.setDisplayRule(rule);
      FieldForm copy = field.shallowCopy();
      assertEquals(key, copy.getFieldKey(), field.getType().name());
      assertEquals(rule, copy.getDisplayRule(), field.getType().name());
    }
  }

  @Test
  public void aKeyIsAssignedOnPersistOnlyWhenMissing() {
    FieldForm keyless = new StringFieldForm();
    assertNull(keyless.getFieldKey());
    keyless.assignFieldKeyIfMissing();
    assertNotNull(keyless.getFieldKey());

    FieldForm keyed = new StringFieldForm();
    keyed.setFieldKey("6f1c2e0a-0000-4000-8000-000000000000");
    keyed.assignFieldKeyIfMissing();
    assertEquals("6f1c2e0a-0000-4000-8000-000000000000", keyed.getFieldKey());
  }
}
