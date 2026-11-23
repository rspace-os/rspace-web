package com.researchspace.model.field.rule;

import com.researchspace.model.field.FieldType;
import java.util.List;
import java.util.Objects;

/**
 * An immutable copy of the parts of one field that display rules read, taken at a point in time:
 * the result of a save being validated, or the values currently entered. It is not the stored
 * field; the rule itself is persisted on {@code FieldForm} and {@code InventoryEntityField}.
 *
 * <p>ELN forms and Inventory templates each build snapshots from their own storage (and from
 * request DTOs before anything is persisted), so the rule logic depends on neither.
 *
 * @param key the field key, or null for a legacy field that has none
 * @param type the field's type
 * @param rule the field's display rule, or null when it is always shown
 * @param allowedOptions the options a radio or choice field offers, empty for other types
 * @param selectedOptions the options currently selected, empty when the field has no value
 * @param sourceKind what the field offers to rules that read it
 * @param value the raw stored text of a {@link SourceKind#TEXT} or {@link SourceKind#NUMBER} field,
 *     empty when it has none; null is read as empty
 */
public record FieldSnapshot(
    String key,
    FieldType type,
    FieldDisplayRule rule,
    List<String> allowedOptions,
    List<String> selectedOptions,
    SourceKind sourceKind,
    String value) {

  public FieldSnapshot {
    Objects.requireNonNull(sourceKind, "sourceKind");
    allowedOptions = allowedOptions == null ? List.of() : List.copyOf(allowedOptions);
    selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
    value = value == null ? "" : value;
  }

  /**
   * A snapshot whose source kind follows from the type alone ({@link SourceKind#ofType}), with no
   * text or number value.
   */
  public FieldSnapshot(
      String key,
      FieldType type,
      FieldDisplayRule rule,
      List<String> allowedOptions,
      List<String> selectedOptions) {
    this(key, type, rule, allowedOptions, selectedOptions, SourceKind.ofType(type), "");
  }

  /** Whether the field can be the source of a display rule. */
  public boolean canBeSource() {
    return sourceKind != SourceKind.NONE;
  }
}
