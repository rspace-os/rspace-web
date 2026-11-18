package com.researchspace.model.field.rule;

import com.researchspace.model.field.FieldType;
import java.util.List;

/**
 * The view of a field that display rules need. ELN forms and Inventory templates each build these
 * from their own storage, so the rule logic does not depend on either.
 *
 * @param key the field key, or null for a legacy field that has none
 * @param type the field's type
 * @param rule the field's display rule, or null when it is always shown
 * @param allowedOptions the options a radio or choice field offers, empty for other types
 * @param selectedOptions the options currently selected, empty when the field has no value
 */
public record RuleField(
    String key,
    FieldType type,
    FieldDisplayRule rule,
    List<String> allowedOptions,
    List<String> selectedOptions) {

  public RuleField {
    allowedOptions = allowedOptions == null ? List.of() : List.copyOf(allowedOptions);
    selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
  }

  /** Whether a field of this type can be the source of a display rule. */
  public boolean canBeSource() {
    return type == FieldType.RADIO || type == FieldType.CHOICE;
  }
}
