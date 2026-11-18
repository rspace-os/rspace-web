package com.researchspace.model.field;

import com.researchspace.model.field.rule.ElnChoiceValues;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.field.rule.FieldDisplayRuleEvaluator;
import com.researchspace.model.field.rule.FieldSnapshot;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/** Builds the display-rule engine's view of ELN form fields and document fields. */
@Slf4j
public final class ElnFieldRules {

  private ElnFieldRules() {}

  /**
   * The rule engine's view of a form field definition, with nothing selected.
   *
   * @param field a form field
   */
  public static FieldSnapshot forDefinition(IFieldForm field) {
    return new FieldSnapshot(
        field.getFieldKey(), field.getType(), storedRule(field), allowedOptions(field), List.of());
  }

  /**
   * The rule engine's view of a document field: its form field's key, rule and options, and the
   * options its data selects.
   */
  public static FieldSnapshot forDocumentField(Field field) {
    IFieldForm definition = field.getFieldForm();
    return new FieldSnapshot(
        definition.getFieldKey(),
        definition.getType(),
        storedRule(definition),
        allowedOptions(definition),
        selectedOptions(definition.getType(), field.getFieldData()));
  }

  /**
   * Whether every mandatory field that is shown, given the document's current values, has data.
   * Hidden mandatory fields are never missing. This is what enables the Sign button and clears the
   * workspace warning icon.
   *
   * @param fields a document's fields
   */
  public static boolean everyShownMandatoryFieldHasData(List<Field> fields) {
    List<FieldSnapshot> snapshots = new ArrayList<>(fields.size());
    for (Field field : fields) {
      snapshots.add(forDocumentField(field));
    }
    List<Boolean> shown = FieldDisplayRuleEvaluator.visibility(snapshots);
    for (int i = 0; i < fields.size(); i++) {
      if (shown.get(i) && !fields.get(i).isMandatoryStateSatisfied()) {
        return false;
      }
    }
    return true;
  }

  /**
   * The field's stored rule. A stored rule that cannot be parsed is treated as no rule, so a
   * corrupt rule never hides data; it is logged.
   *
   * @return the rule, or null if the field has none or it cannot be parsed
   */
  public static FieldDisplayRule storedRule(IFieldForm field) {
    String json = field.getDisplayRule();
    if (json == null) {
      return null;
    }
    try {
      return FieldDisplayRule.fromJson(json);
    } catch (IllegalArgumentException e) {
      log.warn(
          "Ignoring unreadable display rule on form field {}: {}", field.getId(), e.getMessage());
      return null;
    }
  }

  /** The options a radio or choice field offers; empty for other types. */
  public static List<String> allowedOptions(IFieldForm field) {
    if (field instanceof RadioFieldForm radio) {
      return ElnChoiceValues.parse(radio.getRadioOption());
    }
    if (field instanceof ChoiceFieldForm choice) {
      return ElnChoiceValues.parse(choice.getChoiceOptions());
    }
    return List.of();
  }

  /** Radio data is the plain option; choice data is {@code fieldChoices=a&fieldChoices=b}. */
  private static List<String> selectedOptions(FieldType type, String data) {
    if (type == FieldType.RADIO) {
      return StringUtils.isEmpty(data) ? List.of() : List.of(data);
    }
    if (type == FieldType.CHOICE) {
      return ElnChoiceValues.parse(data);
    }
    return List.of();
  }
}
