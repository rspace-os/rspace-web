package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLinkField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Matches operation-generated fields to the identically named fields a new sample inherits from its
 * template. Creating the sample writes each matched value into the inherited field; validating the
 * request reads the same matches, so a mandatory or display-rule check sees the values the sample
 * will actually hold.
 *
 * <p>Only fields carrying an {@code operationFieldKey} are matched; a user's own extra field on
 * POST /samples has none. Link and options fields are never targets, and nor is a field whose type
 * would reject the generated value.
 */
public final class InheritedFieldMerge {

  /** A generated field whose content is written into the inherited {@code target}. */
  public record Merge(ApiExtraField generated, InventoryEntityField target) {}

  private InheritedFieldMerge() {}

  /** The merges, in the order the generated fields are given; empty when nothing matches. */
  public static List<Merge> plan(
      List<ApiExtraField> extraFields, List<InventoryEntityField> inheritedFields) {
    if (extraFields == null
        || extraFields.isEmpty()
        || inheritedFields == null
        || inheritedFields.isEmpty()) {
      return List.of();
    }
    Map<String, InventoryEntityField> targets = new HashMap<>();
    for (InventoryEntityField inherited : inheritedFields) {
      if (inherited instanceof InventoryLinkField || inherited.isOptionsStoringField()) {
        continue;
      }
      String name = inherited.getName();
      if (StringUtils.isNotBlank(name)) {
        targets.putIfAbsent(name.trim().toLowerCase(Locale.ROOT), inherited);
      }
    }
    List<Merge> merges = new ArrayList<>();
    for (ApiExtraField field : extraFields) {
      if (field == null
          || StringUtils.isBlank(field.getOperationFieldKey())
          || ExtraFieldTypeEnum.LINK.equals(field.getType())
          || StringUtils.isBlank(field.getName())) {
        continue;
      }
      InventoryEntityField target = targets.get(field.getName().trim().toLowerCase(Locale.ROOT));
      // A name match doesn't guarantee the inherited field's type matches (e.g. a NUMBER template
      // field vs. a generated text value). setFieldData throws on a mismatch, so such a field is
      // left unmatched and falls back to the ordinary duplicate-name rejection.
      if (target != null && !target.validate(field.getContent()).hasErrorMessages()) {
        merges.add(new Merge(field, target));
      }
    }
    return merges;
  }

  /**
   * {@code templateFields} as a sample created from them will hold them once the generated values
   * are merged in. A merged field is replaced by a copy, so the template itself is not changed;
   * when nothing merges, the given list is returned.
   */
  public static List<InventoryEntityField> withMergedValues(
      List<ApiExtraField> extraFields, List<InventoryEntityField> templateFields) {
    List<Merge> merges = plan(extraFields, templateFields);
    if (merges.isEmpty()) {
      return templateFields;
    }
    Map<InventoryEntityField, InventoryEntityField> copies = new IdentityHashMap<>();
    for (Merge merge : merges) {
      copies
          .computeIfAbsent(merge.target(), InventoryEntityField::shallowCopy)
          .setFieldData(merge.generated().getContent());
    }
    List<InventoryEntityField> merged = new ArrayList<>(templateFields.size());
    for (InventoryEntityField field : templateFields) {
      merged.add(copies.getOrDefault(field, field));
    }
    return merged;
  }
}
