package com.researchspace.search.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.collection.RuntimeFieldNamespaces;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.ExtraFieldIdentity;
import org.junit.jupiter.api.Test;

class RuntimeFieldIndexNamesTest {

  @Test
  void namesACustomFieldTheSameFromAnEntityAsFromItsSelector() {
    String fromWriter =
        RuntimeFieldIndexNames.valueField(
            RuntimeFieldNamespaces.CUSTOM_FIELDS,
            new GlobalIdentifier(GlobalIdPrefix.SF, 104L).getIdString());
    String fromReader = RuntimeFieldIndexNames.fieldForSelector("customFields.SF104");

    assertEquals(fromReader, fromWriter);
    assertEquals("rtFieldValue_customFields_SF104", fromWriter);
  }

  @Test
  void namesAnExtraFieldTheSameFromAnEntityAsFromItsSelector() {
    String id = ExtraFieldIdentity.encode("Notes", FieldType.TEXT);
    String fromWriter = RuntimeFieldIndexNames.valueField(RuntimeFieldNamespaces.EXTRA_FIELDS, id);
    String fromReader = RuntimeFieldIndexNames.fieldForSelector("extraFields." + id);

    assertEquals(fromReader, fromWriter);
    assertEquals("rtFieldValue_extraFields_XFt4e6f746573", fromWriter);
  }

  @Test
  void refusesANameThatIsNotSafeForAnIndexField() {
    assertNull(RuntimeFieldIndexNames.valueField("custom fields", "SF104"));
    assertNull(RuntimeFieldIndexNames.valueField(RuntimeFieldNamespaces.CUSTOM_FIELDS, null));
    assertNull(RuntimeFieldIndexNames.fieldForSelector("customFields"));
    assertNull(RuntimeFieldIndexNames.fieldForSelector("customFields."));
  }
}
