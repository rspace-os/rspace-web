package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.field.FieldType;
import org.junit.Test;

public class InventoryLinkFieldTest {

  @Test
  public void fromFieldTypeStringCreatesLinkField() {
    InventoryEntityField field = InventoryEntityField.fromFieldTypeString("link");
    assertTrue(field instanceof InventoryLinkField);
    assertEquals(FieldType.LINK, field.getType());
  }

  @Test
  public void shallowCopyCopiesAllowedRelationTypesAndDeepCopiesLink() {
    InventoryLinkField field = new InventoryLinkField();
    field.setName("Related items");
    field.setAllowedRelationTypes("References|IsDerivedFrom");
    InventoryLink link = new InventoryLink();
    link.setTargetGlobalId("SA10");
    link.setTargetPrefix(GlobalIdPrefix.SA);
    link.setTargetDbId(10L);
    link.setRelationType("References");
    link.setVersionPin(3L);
    link.setTargetRevisionId(99L);
    link.setDeleted(true);
    field.setLink(link);

    InventoryLinkField copy = field.shallowCopy();

    assertEquals("References|IsDerivedFrom", copy.getAllowedRelationTypes());
    // deep copy: a new InventoryLink row, never the same instance
    assertNotSame(link, copy.getLink());
    assertNull(copy.getLink().getId());
    assertEquals("SA10", copy.getLink().getTargetGlobalId());
    assertEquals(GlobalIdPrefix.SA, copy.getLink().getTargetPrefix());
    assertEquals(Long.valueOf(10), copy.getLink().getTargetDbId());
    assertEquals("References", copy.getLink().getRelationType());
    assertEquals(Long.valueOf(3), copy.getLink().getVersionPin());
    assertEquals(Long.valueOf(99), copy.getLink().getTargetRevisionId());
    // deletion state must survive the clone (same path as InventoryLink.shallowCopy())
    assertTrue(copy.getLink().isDeleted());
  }

  @Test
  public void mandatoryValidationRequiresNonBlankTargetGlobalIdAndRelationType() {
    InventoryLinkField field = new InventoryLinkField();

    // no link at all -> not valid
    assertFalse(field.isValidValueForMandatoryField(null));

    InventoryLink link = new InventoryLink();
    field.setLink(link);

    // link with neither target nor relation type -> not valid
    assertFalse(field.isValidValueForMandatoryField(null));

    // blank target (whitespace only) is not a real target -> not valid
    link.setTargetGlobalId("   ");
    link.setRelationType("References");
    assertFalse(field.isValidValueForMandatoryField(null));

    // target present but blank relation type -> not valid: relation_type is NOT NULL, so this
    // would otherwise fail at flush with a low-level DB constraint violation
    link.setTargetGlobalId("SA10");
    link.setRelationType("");
    assertFalse(field.isValidValueForMandatoryField(null));

    // both present and non-blank -> valid
    link.setRelationType("References");
    assertTrue(field.isValidValueForMandatoryField(null));
  }

  @Test
  public void becomingMandatoryDuringTemplateUpdateDoesNotRejectAnEmptyLink() {
    // A sample's link field synced to a template version that newly marks the link mandatory must
    // not abort the "update samples to latest template version" run just because the link is still
    // empty: a mandatory link is legitimately filled by a later, separate link update. (A
    // data-column field in the same situation does still throw - see InventoryEntityFieldTest.)
    InventoryLinkField templateField = new InventoryLinkField();
    templateField.setName("related");
    templateField.setMandatory(true);

    InventoryLinkField field = new InventoryLinkField();
    field.setName("related");
    field.setTemplateField(templateField);

    // the empty link is still reported "not yet valid" for an actual submission ...
    assertFalse(field.isValidValueForMandatoryField(null));
    // ... but syncing to the now-mandatory template definition must not throw
    boolean updated = field.updateToLatestTemplateDefinition();
    assertTrue(updated);
    assertTrue(field.isMandatory());
  }

  @Test
  public void clearValueClearsLinkAssociation() {
    InventoryLinkField field = new InventoryLinkField();
    InventoryLink link = new InventoryLink();
    link.setTargetGlobalId("SA10");
    link.setRelationType("References");
    field.setLink(link);

    field.clearValue();

    // the link field holds its value in the association, not the data column, so clearing the
    // value must drop the link too (otherwise a template's link leaks into a newly added field)
    assertNull(field.getLink());
    assertNull(field.getData());
  }

  @Test
  public void shallowCopyLeavesLinkNullWhenSourceHasNoLink() {
    InventoryLinkField field = new InventoryLinkField();
    field.setName("Related items");
    field.setAllowedRelationTypes("References");

    InventoryLinkField copy = field.shallowCopy();

    assertNull(copy.getLink());
    assertEquals("References", copy.getAllowedRelationTypes());
  }
}
