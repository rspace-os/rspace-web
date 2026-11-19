package com.researchspace.model.field;

import com.researchspace.model.field.rule.ElnChoiceValues;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.field.rule.FieldDisplayRuleEvaluator;
import com.researchspace.model.field.rule.FieldSnapshot;
import com.researchspace.model.field.rule.SourceKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/** Builds the display-rule engine's view of ELN form fields and document fields. */
@Slf4j
public final class ElnFieldRules {

  /**
   * Form fields whose unreadable rule has been logged at WARN by this process. An unreadable rule
   * is read on every view of each affected document, so later reads log at DEBUG. Only corrupt
   * rules are recorded, so the set stays small.
   */
  private static final Set<Long> REPORTED_UNREADABLE_RULES = ConcurrentHashMap.newKeySet();

  private ElnFieldRules() {}

  /**
   * The rule engine's view of a form field definition, with nothing selected and no value.
   *
   * @param field a form field
   */
  public static FieldSnapshot forDefinition(IFieldForm field) {
    return new FieldSnapshot(
        field.getFieldKey(),
        field.getType(),
        storedRule(field),
        allowedOptions(field),
        List.of(),
        sourceKind(field),
        "");
  }

  /**
   * The rule engine's view of a document field: its form field's key, rule and options, and the
   * options its data selects or, for a text or number source, its data.
   */
  public static FieldSnapshot forDocumentField(Field field) {
    IFieldForm definition = field.getFieldForm();
    SourceKind kind = sourceKind(definition);
    return new FieldSnapshot(
        definition.getFieldKey(),
        definition.getType(),
        storedRule(definition),
        allowedOptions(definition),
        selectedOptions(definition.getType(), field.getFieldData()),
        kind,
        sourceValue(kind, field.getFieldData()));
  }

  /**
   * What a form field offers to the rules that read it: radio and choice fields their options,
   * number fields their number and String fields their text, unless they are password fields. A
   * password field's value would appear in rule summaries and API responses, so it is never a
   * source.
   */
  public static SourceKind sourceKind(IFieldForm field) {
    return sourceKind(
        field.getType(), field instanceof StringFieldForm string && string.isIfPassword());
  }

  /**
   * The source kind of a field of this type, as {@link #sourceKind(IFieldForm)} decides it, for
   * callers that hold the type and password flag rather than a form field.
   */
  public static SourceKind sourceKind(FieldType type, boolean password) {
    return switch (type) {
      case RADIO, CHOICE -> SourceKind.OPTIONS;
      case NUMBER -> SourceKind.NUMBER;
      case STRING -> password ? SourceKind.NONE : SourceKind.TEXT;
      default -> SourceKind.NONE;
    };
  }

  /** The data that rules read as the value of a text or number source; empty for other kinds. */
  public static String sourceValue(SourceKind kind, String data) {
    return kind == SourceKind.TEXT || kind == SourceKind.NUMBER ? data : "";
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
   * corrupt rule never hides data. It is logged by field id and exception type only, because parser
   * messages can quote the stored rule.
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
      String message = "Ignoring unreadable display rule on form field {} ({})";
      String reason = e.getClass().getSimpleName();
      if (field.getId() == null || REPORTED_UNREADABLE_RULES.add(field.getId())) {
        log.warn(message, field.getId(), reason);
      } else {
        log.debug(message, field.getId(), reason);
      }
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
