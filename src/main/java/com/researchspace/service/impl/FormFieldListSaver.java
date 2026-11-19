package com.researchspace.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.dao.FieldFormDao;
import com.researchspace.model.dtos.AbstractFormFieldDTO;
import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.dtos.FormFieldSource;
import com.researchspace.model.dtos.LegacyFormFieldDTOs;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.ElnFieldRules;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.FieldUtils;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.field.rule.FieldDisplayRuleValidator;
import com.researchspace.model.field.rule.FieldDisplayRuleValidator.RuleError;
import com.researchspace.model.field.rule.FieldSnapshot;
import com.researchspace.model.record.AbstractForm;
import com.researchspace.service.FormFieldsValidationException;
import com.researchspace.service.MessageSourceUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * Applies a form's full field list in one step: creates, updates, reorders and deletes fields, sets
 * their keys and display rules, validates the whole result and only then persists it. Must run
 * inside the calling manager's transaction; every rejection is a {@link
 * FormFieldsValidationException}, which is unchecked, so the transaction rolls back whatever had
 * already been applied.
 */
@Component
@Slf4j
public class FormFieldListSaver {

  /** Legacy validator errors on options; re-checked here only for new or changed options. */
  private static final Set<String> OPTION_PROPERTIES = Set.of("radioValues", "choiceValues");

  private static final Set<String> OPTION_FORMAT_CODES =
      Set.of("form.choiceOptions.invalidFormat", "errors.maxLength");

  private final ObjectMapper fingerprintMapper = new ObjectMapper();

  private @Autowired FieldFormDao fieldFormDao;
  private @Autowired MessageSourceUtils messages;

  /** A field as this save will leave it, with what is needed to judge what changed. */
  private record PlannedField(
      FieldForm field,
      boolean isNew,
      String optionsBefore,
      List<String> optionListBefore,
      JsonNode settingsBefore,
      Long modifiedBefore) {}

  /**
   * Applies {@code items} to {@code form}.
   *
   * @param form the form being edited: a draft, or a form that has never been published
   * @param items every field the form should have, in order
   * @param copiedFieldIds ids of draft fields copied from the published version; omitting one is an
   *     error, not a deletion
   * @return the form's active fields, in their new order
   * @throws FormFieldsValidationException if anything in the save is invalid
   */
  public List<FieldForm> apply(
      AbstractForm form, List<FormFieldSaveItem> items, Set<Long> copiedFieldIds) {
    List<String> errors = new ArrayList<>();
    Map<Long, FieldForm> activeById = new LinkedHashMap<>();
    for (FieldForm field : form.getFieldForms()) {
      if (!field.isDeleted()) {
        activeById.put(field.getId(), field);
      }
    }
    Set<String> keysInForm = new HashSet<>(fieldFormDao.getFieldKeysForForm(form.getId()));
    Set<String> newKeys = new HashSet<>();
    Set<Long> keptIds = new HashSet<>();
    List<PlannedField> planned = new ArrayList<>();

    for (int i = 0; i < items.size(); i++) {
      FormFieldSaveItem item = items.get(i);
      if (item == null) {
        errors.add(messages.getMessage("form.errors.fieldMissing", args("#" + (i + 1))));
        continue;
      }
      String label = label(item, i);
      FieldType type =
          item.getType() == null ? null : FieldType.getFieldTypeForString(item.getType());
      if (type == null) {
        errors.add(
            messages.getMessage("form.errors.fieldTypeUnknown", args(label, item.getType())));
        continue;
      }
      FormFieldSource<? extends FieldForm> source = item.getSource();
      if (source == null) {
        errors.add(messages.getMessage("form.errors.fieldSettingsMissing", args(label)));
        continue;
      }
      LegacyFormFieldDTOs.normalise(source);
      if (!validateLegacySource(source, label, errors)) {
        continue;
      }
      FieldForm prototype;
      try {
        prototype = source.createFieldForm();
      } catch (RuntimeException e) {
        log.warn("Could not read the settings of form field {}: {}", label, e.getMessage());
        errors.add(messages.getMessage("form.errors.fieldSettingsMissing", args(label)));
        continue;
      }
      if (prototype.getType() != type) {
        errors.add(messages.getMessage("form.errors.fieldSettingsMissing", args(label)));
        continue;
      }

      PlannedField plan;
      if (item.getId() != null) {
        plan = planSavedField(item, type, source, label, activeById, keptIds, errors);
      } else {
        plan = planNewField(item, prototype, label, keysInForm, newKeys, errors);
      }
      if (plan == null) {
        continue;
      }
      if (!(item.isKeepStoredDisplayRule() && !plan.isNew())) {
        applyRule(item, plan.field(), label, errors);
      }
      checkOptions(plan, source, label, errors);
      planned.add(plan);
    }

    List<FieldForm> toDelete = new ArrayList<>();
    for (FieldForm field : activeById.values()) {
      if (keptIds.contains(field.getId())) {
        continue;
      }
      if (copiedFieldIds.contains(field.getId())) {
        errors.add(messages.getMessage("form.errors.fieldNotDeletable", args(field.getName())));
      } else {
        toDelete.add(field);
      }
    }

    if (errors.isEmpty()) {
      validateRules(planned, errors);
    }
    if (!errors.isEmpty()) {
      throw new FormFieldsValidationException(errors);
    }
    return persist(form, planned, toDelete);
  }

  /**
   * The version of the form's field list: a hash of every active field's id, position, type, key,
   * display rule and settings. Anything a save can change changes it.
   */
  public String version(AbstractForm form) {
    ArrayNode fields = fingerprintMapper.createArrayNode();
    for (FieldForm field : form.getFieldForms()) {
      if (!field.isDeleted()) {
        ObjectNode node = settingsFingerprint(field);
        node.put("id", field.getId());
        node.put("type", field.getType().getType());
        fields.add(node);
      }
    }
    return DigestUtils.sha256Hex(fields.toString());
  }

  private PlannedField planSavedField(
      FormFieldSaveItem item,
      FieldType type,
      FormFieldSource<? extends FieldForm> source,
      String label,
      Map<Long, FieldForm> activeById,
      Set<Long> keptIds,
      List<String> errors) {
    if (!keptIds.add(item.getId())) {
      errors.add(messages.getMessage("form.errors.fieldIdDuplicate", args(item.getId())));
      return null;
    }
    FieldForm field = activeById.get(item.getId());
    if (field == null) {
      errors.add(messages.getMessage("form.errors.fieldIdUnknown", args(item.getId())));
      return null;
    }
    if (field.getType() != type) {
      errors.add(
          messages.getMessage(
              "form.errors.fieldTypeImmutable",
              args(field.getName(), field.getType().getType(), type.getType())));
      return null;
    }
    if (item.getFieldKey() != null && !item.getFieldKey().equals(field.getFieldKey())) {
      errors.add(messages.getMessage("form.errors.fieldKeyImmutable", args(field.getName())));
      return null;
    }
    PlannedField plan =
        new PlannedField(
            field,
            false,
            optionsOf(field),
            ElnFieldRules.allowedOptions(field),
            settingsFingerprint(field),
            field.getModificationDate());
    copySettings(source, field);
    return plan;
  }

  private PlannedField planNewField(
      FormFieldSaveItem item,
      FieldForm field,
      String label,
      Set<String> keysInForm,
      Set<String> newKeys,
      List<String> errors) {
    String key = item.getFieldKey() == null ? UUID.randomUUID().toString() : item.getFieldKey();
    if (keysInForm.contains(key) || !newKeys.add(key)) {
      errors.add(messages.getMessage("form.errors.fieldKeyInUse", args(label)));
      return null;
    }
    field.setFieldKey(key);
    return new PlannedField(field, true, null, null, null, null);
  }

  private void applyRule(
      FormFieldSaveItem item, FieldForm field, String label, List<String> errors) {
    if (item.isDisplayRuleUnreadable()) {
      errors.add(messages.getMessage("form.errors.fieldRuleInvalid", args(label)));
      return;
    }
    if (item.getDisplayRule() == null) {
      field.setDisplayRule(null);
      return;
    }
    try {
      FieldDisplayRule rule = item.getDisplayRule().toRule();
      field.setDisplayRule(rule.toJson());
    } catch (IllegalArgumentException e) {
      log.debug("Rejected display rule on form field {}: {}", label, e.getMessage());
      errors.add(messages.getMessage("form.errors.fieldRuleInvalid", args(label)));
    }
  }

  /**
   * Options stored through the forms API were never checked, so the option format and length checks
   * apply only to options that are new or changed in this save. Options submitted one by one (the
   * forms API) are checked one by one, before joining could hide a '&amp;' or '=' inside an option;
   * they count as unchanged when they equal the options the field offered before.
   */
  private void checkOptions(
      PlannedField plan,
      FormFieldSource<? extends FieldForm> source,
      String label,
      List<String> errors) {
    if (!(plan.field() instanceof RadioFieldForm || plan.field() instanceof ChoiceFieldForm)) {
      return;
    }
    String options = optionsOf(plan.field());
    List<String> submitted = source.submittedOptions();
    if (submitted != null) {
      if (!plan.isNew() && submitted.equals(plan.optionListBefore())) {
        return;
      }
      if (submitted.stream().anyMatch(FormFieldListSaver::isInvalidOption)) {
        errors.add(messages.getMessage("form.errors.fieldOptionInvalid", args(label)));
        return;
      }
    } else if (!plan.isNew() && Objects.equals(plan.optionsBefore(), options)) {
      return;
    }
    if (FieldUtils.isTooLong(options)) {
      String problem =
          messages.getMessage(
              new DefaultMessageSourceResolvable(
                  new String[] {"errors.maxLength"},
                  new Object[] {
                    new DefaultMessageSourceResolvable("form.choiceOptions.label"),
                    FieldUtils.MAX_OPTIONS_LENGTH
                  }));
      errors.add(messages.getMessage("form.errors.fieldInvalid", args(label, problem)));
    } else if (!FieldUtils.isValidRadioOrChoiceString(options)) {
      String problem = messages.getMessage("form.choiceOptions.invalidFormat");
      errors.add(messages.getMessage("form.errors.fieldInvalid", args(label, problem)));
    }
  }

  /** Runs the legacy validator for a legacy DTO; other sources were validated by their caller. */
  private boolean validateLegacySource(Object source, String label, List<String> errors) {
    Validator validator = LegacyFormFieldDTOs.validatorFor(source);
    if (validator == null) {
      return true;
    }
    BeanPropertyBindingResult result = new BeanPropertyBindingResult(source, "field");
    ValidationUtils.invokeValidator(validator, source, result);
    boolean valid = true;
    for (ObjectError error : result.getAllErrors()) {
      if (error instanceof FieldError fieldError
          && OPTION_PROPERTIES.contains(fieldError.getField())
          && OPTION_FORMAT_CODES.contains(fieldError.getCode())) {
        continue;
      }
      valid = false;
      errors.add(
          messages.getMessage("form.errors.fieldInvalid", args(label, messages.getMessage(error))));
    }
    return valid;
  }

  private void validateRules(List<PlannedField> planned, List<String> errors) {
    List<FieldSnapshot> snapshots = new ArrayList<>(planned.size());
    for (PlannedField plan : planned) {
      snapshots.add(ElnFieldRules.forDefinition(plan.field()));
    }
    for (RuleError error : FieldDisplayRuleValidator.validate(snapshots)) {
      String name = planned.get(error.fieldIndex()).field().getName();
      errors.add(messages.getMessage(error.code(), args(name)));
    }
  }

  private List<FieldForm> persist(
      AbstractForm form, List<PlannedField> planned, List<FieldForm> toDelete) {
    long now = new Date().getTime();
    for (FieldForm field : toDelete) {
      field.setDeleted(true);
      field.setModificationDate(now);
      fieldFormDao.save(field);
    }
    List<FieldForm> ordered = new ArrayList<>(planned.size());
    for (int index = 0; index < planned.size(); index++) {
      PlannedField plan = planned.get(index);
      FieldForm field = plan.field();
      field.setColumnIndex(index);
      if (plan.isNew()) {
        field.setModificationDate(now);
        field.setForm(form);
        field = fieldFormDao.save(field);
        form.addFieldForm(field);
      } else {
        field.setModificationDate(
            plan.settingsBefore().equals(settingsFingerprint(field)) ? plan.modifiedBefore() : now);
        fieldFormDao.save(field);
      }
      ordered.add(field);
    }
    form.setModificationDate(new Date(now));
    return ordered;
  }

  /**
   * Everything the save can change on a field except its position, with the modification time kept
   * aside, so an untouched field keeps its modification time and gains no audit revision.
   */
  private ObjectNode settingsFingerprint(FieldForm field) {
    ObjectNode node = fingerprintMapper.createObjectNode();
    node.set("settings", fingerprintMapper.valueToTree(LegacyFormFieldDTOs.fromFieldForm(field)));
    node.put("fieldKey", field.getFieldKey());
    node.put("displayRule", field.getDisplayRule());
    return node;
  }

  private static boolean isInvalidOption(String option) {
    return StringUtils.isBlank(option) || option.contains("&") || option.contains("=");
  }

  private static String optionsOf(FieldForm field) {
    if (field instanceof RadioFieldForm radio) {
      return radio.getRadioOption();
    }
    if (field instanceof ChoiceFieldForm choice) {
      return choice.getChoiceOptions();
    }
    return null;
  }

  @SuppressWarnings("unchecked") // createFieldForm() confirmed the source builds this field type
  private static <F extends FieldForm> void copySettings(
      FormFieldSource<F> source, FieldForm field) {
    source.copyValuesIntoFieldForm((F) field);
  }

  private static String label(FormFieldSaveItem item, int index) {
    if (item.getSource() != null) {
      String name = nameOf(item.getSource());
      if (StringUtils.isNotBlank(name)) {
        return name;
      }
    }
    return item.getId() != null ? item.getId().toString() : "#" + (index + 1);
  }

  private static String nameOf(FormFieldSource<? extends FieldForm> source) {
    if (source instanceof AbstractFormFieldDTO<?> dto) {
      return dto.getName();
    }
    try {
      return source.createFieldForm().getName();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static Object[] args(Object... values) {
    return values;
  }
}
