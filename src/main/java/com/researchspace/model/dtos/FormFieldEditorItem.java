package com.researchspace.model.dtos;

import com.researchspace.model.field.ElnFieldRules;
import com.researchspace.model.field.FieldForm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A saved field as the form field editor reads it: the {@link FormFieldSaveItem} shape plus whether
 * the field may be deleted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldEditorItem {

  private Long id;

  /** The field type, as {@code FieldType#getType()} spells it, for example {@code Radio}. */
  private String type;

  private String fieldKey;

  /** The display rule, or null when the field is always shown. */
  private FieldDisplayRuleDTO displayRule;

  /** False for fields copied into a draft from the published version, which cannot be deleted. */
  private boolean canDelete;

  /** Every setting of the field, as the legacy DTO for {@link #type}. */
  private AbstractFormFieldDTO<?> source;

  public static FormFieldEditorItem from(FieldForm field, boolean canDelete) {
    return new FormFieldEditorItem(
        field.getId(),
        field.getType().getType(),
        field.getFieldKey(),
        FieldDisplayRuleDTO.from(ElnFieldRules.storedRule(field)),
        canDelete,
        LegacyFormFieldDTOs.fromFieldForm(field));
  }
}
