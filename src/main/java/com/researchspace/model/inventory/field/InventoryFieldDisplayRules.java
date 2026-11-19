package com.researchspace.model.inventory.field;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.LocalizedIllegalArgumentException;
import com.researchspace.model.field.LocalizedIllegalStateException;
import com.researchspace.model.field.rule.FieldDisplayRuleEvaluator;
import com.researchspace.model.field.rule.FieldDisplayRuleValidator;
import com.researchspace.model.field.rule.FieldDisplayRuleValidator.RuleError;
import com.researchspace.model.field.rule.FieldSnapshot;
import com.researchspace.model.field.rule.SourceKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Display rules applied to Inventory template and record fields: rule validation for templates, and
 * the "required only while shown" check for samples and instruments.
 *
 * <p>A field without a display rule keeps the mandatory check in {@link
 * InventoryEntityField#validate}, run whenever its value is set. A field with a rule skips that
 * check, because whether it is shown depends on its siblings; one of the record-level checks here
 * replaces it, depending on what changed the record. A field counts as filled when {@link
 * InventoryEntityField#isValidValueForMandatoryField} accepts its value, so a link field is judged
 * on its link's target and relation rather than on its (always empty) data column.
 */
public final class InventoryFieldDisplayRules {

  private InventoryFieldDisplayRules() {}

  /**
   * Visibility of each field, in input order, using each field's current value.
   *
   * @param fields the active fields of one template or record
   */
  public static List<Boolean> visibility(List<InventoryEntityField> fields) {
    List<FieldSnapshot> snapshots = new ArrayList<>(fields.size());
    for (InventoryEntityField field : fields) {
      snapshots.add(toSnapshot(field, FieldContent.of(field)));
    }
    return FieldDisplayRuleEvaluator.visibility(snapshots);
  }

  /**
   * Visibility of each field, in input order, when the fields hold the given contents rather than
   * their current values. Used to judge a request against template fields.
   *
   * @param contents for each field, what it would hold
   */
  public static List<Boolean> visibility(
      List<InventoryEntityField> fields, List<FieldContent> contents) {
    List<FieldSnapshot> snapshots = new ArrayList<>(fields.size());
    for (int i = 0; i < fields.size(); i++) {
      snapshots.add(toSnapshot(fields.get(i), contents.get(i)));
    }
    return FieldDisplayRuleEvaluator.visibility(snapshots);
  }

  /**
   * What a field holds, as display rules read it: the selected options of a radio or choice field,
   * or the stored text of any other field. Each kind of field reads only its own part.
   *
   * @param selectedOptions the selected options, null read as none
   * @param content the stored text, null read as blank
   */
  public record FieldContent(List<String> selectedOptions, String content) {

    /** The field's current value. */
    public static FieldContent of(InventoryEntityField field) {
      return new FieldContent(
          field.isOptionsStoringField() ? field.getSelectedOptions() : List.of(),
          field.getFieldData());
    }
  }

  /**
   * What a field offers to the rules that read it: a Plain text ({@code string}) field its text, a
   * number field its number, a radio or choice field its options. Formatted text and every other
   * type cannot be a source.
   */
  public static SourceKind sourceKind(InventoryEntityField field) {
    return switch (field.getType()) {
      case STRING -> SourceKind.TEXT;
      case NUMBER -> SourceKind.NUMBER;
      case RADIO, CHOICE -> SourceKind.OPTIONS;
      default -> SourceKind.NONE;
    };
  }

  /**
   * Checks the field keys and display rules of a template after a create or an edit has been
   * applied in full, so rules that reference fields added, changed or deleted in the same request
   * are judged on the result.
   *
   * <p>Keys must be unique among all of the template's fields, deleted ones included, because
   * records keep a deleted template field when the deletion is not applied to them. Rules are
   * checked against the active fields only, so a rule whose source has been deleted is rejected.
   *
   * @param allFields every field of the template, including deleted ones
   * @throws LocalizedIllegalArgumentException naming the first field with a problem
   */
  public static void assertTemplateRulesValid(List<InventoryEntityField> allFields) {
    Set<String> keys = new HashSet<>();
    for (InventoryEntityField field : allFields) {
      if (field.getFieldKey() != null && !keys.add(field.getFieldKey())) {
        throw new LocalizedIllegalArgumentException(
            FieldDisplayRuleValidator.KEY_DUPLICATE, field.getName());
      }
    }
    List<InventoryEntityField> active =
        allFields.stream().filter(field -> !field.isDeleted()).toList();
    List<FieldSnapshot> snapshots = new ArrayList<>(active.size());
    for (InventoryEntityField field : active) {
      snapshots.add(toSnapshot(field, FieldContent.of(field)));
    }
    List<RuleError> errors = FieldDisplayRuleValidator.validate(snapshots);
    if (!errors.isEmpty()) {
      RuleError first = errors.get(0);
      throw new LocalizedIllegalArgumentException(
          first.code(), active.get(first.fieldIndex()).getName());
    }
  }

  /**
   * Rejects a new record with a shown, mandatory, empty field that has a display rule. Run once the
   * request's values and the template's defaults are in place.
   *
   * @param fields the record's active fields
   * @param exempt fields whose emptiness is allowed for another reason
   * @throws LocalizedIllegalArgumentException the error {@code setFieldData} raises for a missing
   *     mandatory value
   */
  public static void assertShownMandatoryFieldsFilled(
      List<InventoryEntityField> fields, Predicate<InventoryEntityField> exempt) {
    List<Boolean> visible = visibility(fields);
    for (int i = 0; i < fields.size(); i++) {
      InventoryEntityField field = fields.get(i);
      if (visible.get(i) && isRuledMandatoryAndEmpty(field) && !exempt.test(field)) {
        throw mandatoryContentMissing(field);
      }
    }
  }

  /**
   * Remembers whether each field was filled and shown before an update, for {@link
   * RecordBeforeUpdate#assertShownMandatoryFieldsFilled}. Call it before the request touches the
   * record, links included.
   *
   * @param fields the record's active fields before the update is applied
   */
  public static RecordBeforeUpdate beforeUpdate(List<InventoryEntityField> fields) {
    List<Boolean> visible = visibility(fields);
    Map<InventoryEntityField, Boolean> filled = new IdentityHashMap<>();
    Map<InventoryEntityField, Boolean> shown = new IdentityHashMap<>();
    for (int i = 0; i < fields.size(); i++) {
      InventoryEntityField field = fields.get(i);
      filled.put(field, isFilled(field));
      shown.put(field, visible.get(i));
    }
    return new RecordBeforeUpdate(filled, shown);
  }

  /** Whether a record's fields were filled and shown before an update. */
  public static final class RecordBeforeUpdate {
    private final Map<InventoryEntityField, Boolean> filled;
    private final Map<InventoryEntityField, Boolean> shown;

    private RecordBeforeUpdate(
        Map<InventoryEntityField, Boolean> filled, Map<InventoryEntityField, Boolean> shown) {
      this.filled = filled;
      this.shown = shown;
    }

    /**
     * Rejects the update if it leaves a field with a display rule shown, mandatory and empty, and
     * either emptied that field or revealed it (it was hidden before).
     *
     * <p>A field that was already empty, and stays empty, is not changed by the update, whatever
     * blank value the request wrote over it: clients send {@code ""} for empty content, which
     * replaces the {@code null} an update to the latest template version leaves in a field it adds.
     * So an empty mandatory field that was already shown and is left empty does not block an
     * unrelated edit.
     *
     * @param fields the record's active fields after the update is applied
     * @param exempt fields whose emptiness is allowed for another reason
     */
    public void assertShownMandatoryFieldsFilled(
        List<InventoryEntityField> fields, Predicate<InventoryEntityField> exempt) {
      List<Boolean> visible = visibility(fields);
      for (int i = 0; i < fields.size(); i++) {
        InventoryEntityField field = fields.get(i);
        if (!visible.get(i) || !isRuledMandatoryAndEmpty(field) || exempt.test(field)) {
          continue;
        }
        boolean emptied = !Boolean.FALSE.equals(filled.get(field));
        boolean revealed = !Boolean.TRUE.equals(shown.get(field));
        if (emptied || revealed) {
          throw mandatoryContentMissing(field);
        }
      }
    }
  }

  /**
   * Rejects an update to the latest template version that makes an existing field mandatory while
   * it is shown and empty. Run after every field has been synced, so visibility reflects the latest
   * keys, rules and values. Fields added by the update are not checked: they start empty.
   *
   * @param fields the record's active fields after the sync
   * @param becameMandatory the existing fields whose mandatory flag the sync turned on
   * @throws LocalizedIllegalStateException {@code mandatoryInLatestTemplate} for the first such
   *     field
   */
  public static void assertMandatoryInLatestTemplateFilled(
      List<InventoryEntityField> fields, List<InventoryEntityField> becameMandatory) {
    if (becameMandatory.isEmpty()) {
      return;
    }
    List<Boolean> visible = visibility(fields);
    // Identity, not equals: these are the same entity instances, possibly still unsaved.
    Set<InventoryEntityField> flipped = Collections.newSetFromMap(new IdentityHashMap<>());
    flipped.addAll(becameMandatory);
    for (int i = 0; i < fields.size(); i++) {
      InventoryEntityField field = fields.get(i);
      if (visible.get(i)
          && flipped.contains(field)
          && field.requiresValueWhenBecomingMandatoryOnTemplateUpdate()
          && !field.isValidValueForMandatoryField(field.getFieldData())) {
        throw new LocalizedIllegalStateException(
            "validation.inventoryField.mandatoryInLatestTemplate", field.getName());
      }
    }
  }

  private static boolean isRuledMandatoryAndEmpty(InventoryEntityField field) {
    return field.getDisplayRule() != null && field.isMandatory() && !isFilled(field);
  }

  /** A link field ignores the argument and reads its link's target and relation. */
  private static boolean isFilled(InventoryEntityField field) {
    return field.isValidValueForMandatoryField(field.getFieldData());
  }

  private static LocalizedIllegalArgumentException mandatoryContentMissing(
      InventoryEntityField field) {
    ErrorList errors = new ErrorList();
    errors.addErrorMsgCode("validation.inventoryField.mandatoryContentMissing", field.getName());
    return new LocalizedIllegalArgumentException(
        "validation.inventoryField.invalidForFieldType",
        errors,
        field.getFieldData(),
        field.getType().getType());
  }

  private static FieldSnapshot toSnapshot(InventoryEntityField field, FieldContent content) {
    SourceKind kind = sourceKind(field);
    boolean options = kind == SourceKind.OPTIONS;
    boolean scalar = kind == SourceKind.TEXT || kind == SourceKind.NUMBER;
    return new FieldSnapshot(
        field.getFieldKey(),
        field.getType(),
        field.getDisplayRule(),
        options ? field.getAllOptions() : List.of(),
        options ? content.selectedOptions() : List.of(),
        kind,
        scalar ? content.content() : "");
  }
}
