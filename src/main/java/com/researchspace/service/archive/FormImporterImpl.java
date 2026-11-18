package com.researchspace.service.archive;

import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromInputImageStream;
import static com.researchspace.model.record.IconEntity.createIconEntityFromImage;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalFieldForm;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.dao.FieldFormDao;
import com.researchspace.dao.IconImgDao;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.dtos.ChoiceFieldDTO;
import com.researchspace.model.dtos.DateFieldDTO;
import com.researchspace.model.dtos.FormFieldSource;
import com.researchspace.model.dtos.NumberFieldDTO;
import com.researchspace.model.dtos.RadioFieldDTO;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.dtos.TimeFieldDTO;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.rule.ElnChoiceValues;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.field.rule.FieldDisplayRuleValidator;
import com.researchspace.model.field.rule.FieldDisplayRuleValidator.RuleError;
import com.researchspace.model.field.rule.RuleField;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.service.FormManager;
import com.researchspace.service.archive.export.FormIconWriter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class FormImporterImpl implements FormImporter {

  private @Autowired FormManager formManager;
  private @Autowired IconImgDao iconDao;
  private @Autowired FieldFormDao fieldFormDao;

  @Override
  public RSForm makeRSForm(ArchivalDocumentParserRef parserRef, User user) {
    ArchivalForm archiveForm = parserRef.getArchivalForm();

    RSForm form = formManager.create(user);
    try {
      createFormIcon(parserRef, form.getId())
          .ifPresent(
              formIcon -> {
                form.setIconId(formIcon.getId());
                formManager.save(form, user);
              });

    } catch (IOException e) {
      log.warn(
          "Couldn't insert formIcon for new form {} parsed from exported form {}:{}",
          form.getId(),
          archiveForm.getFormId(),
          e.getMessage());
    }
    form.setFormType(FormType.valueOf(archiveForm.getType()));
    form.setName(archiveForm.getName());
    form.setModificationDate(new Date(archiveForm.getModificationDate()));
    form.setPublishingState(FormState.valueOf(archiveForm.getPublishingState()));
    form.setVersion(new Version(Long.parseLong(archiveForm.getFormVersion())));

    List<ArchivalFieldForm> flds1 = archiveForm.getFieldFormList();
    ArchivedFieldRules archivedRules = ArchivedFieldRules.check(archiveForm);
    for (ArchivalFieldForm aff : flds1) {
      switch (FieldType.getFieldTypeForString(aff.getType())) {
        case NUMBER:
          persistFieldForm(
              archivedRules,
              aff,
              form,
              new NumberFieldDTO<>(
                  aff.getMin(),
                  aff.getMax(),
                  aff.getDecimalPlace(),
                  aff.getDefaultValue(),
                  FieldType.NUMBER,
                  aff.getName(),
                  aff.isMandatory()));
          break;
        case STRING:
          String tr = aff.isPassword() ? "yes" : "no";
          persistFieldForm(
              archivedRules,
              aff,
              form,
              new StringFieldDTO<>(aff.getName(), aff.isMandatory(), tr, aff.getDefaultValue()));
          break;
        case RADIO:
          persistFieldForm(
              archivedRules,
              aff,
              form,
              new RadioFieldDTO<>(
                  aff.getOptions(),
                  aff.getSelection(),
                  aff.getName(),
                  aff.isDisplayAsPickList(),
                  aff.isSortAlphabetic(),
                  aff.isMandatory()));
          break;
        case CHOICE:
          persistFieldForm(
              archivedRules,
              aff,
              form,
              new ChoiceFieldDTO<>(
                  aff.getOptions(),
                  aff.getMultipleChoice(),
                  aff.getSelection(),
                  aff.getName(),
                  aff.isMandatory()));
          break;
        case DATE:
          persistFieldForm(
              archivedRules,
              aff,
              form,
              new DateFieldDTO<>(
                  aff.getDefaultValue(),
                  aff.getMin(),
                  aff.getMax(),
                  aff.getDateFormat(),
                  aff.getName(),
                  aff.isMandatory()));
          break;
        case TIME:
          persistFieldForm(
              archivedRules,
              aff,
              form,
              new TimeFieldDTO<>(
                  aff.getDefaultValue(),
                  aff.getMin(),
                  aff.getMax(),
                  aff.getDateFormat(),
                  aff.getName(),
                  aff.isMandatory()));
          break;

        default: // Text
          TextFieldForm tfm =
              persistFieldForm(
                  archivedRules,
                  aff,
                  form,
                  new TextFieldDTO<>(aff.getName(), aff.isMandatory(), aff.getDefaultValue()));
          setFieldForm(tfm, aff);
      }
    }
    return form;
  }

  /**
   * Builds a field form from {@code dto} and persists it against {@code form}, keeping the
   * <em>managed</em> field form in the form's collection.
   *
   * <p>RSDEV-1140: the previous code went through {@code FormManager.createFieldForm}, whose
   * Hibernate {@code merge} leaves the passed field form transient on the form's {@code
   * cascade=ALL, orphanRemoval=true} collection. Because archive import rebuilds a whole form's
   * fields in one transaction and then merges the form again, those transients were re-cascaded and
   * intermittently orphan-removed - silently dropping field content from imported documents.
   * Persisting the field form directly and storing the managed copy keeps the form's collection
   * consistent with the database, so later merges are no-ops. (The form is owned by the importing
   * user, so no permission check is needed here.)
   */
  private <F extends FieldForm> F persistFieldForm(
      ArchivedFieldRules archivedRules,
      ArchivalFieldForm archived,
      RSForm form,
      FormFieldSource<F> dto) {
    F fieldForm = dto.createFieldForm();
    fieldForm.setFieldKey(archivedRules.fieldKey(archived));
    fieldForm.setDisplayRule(archivedRules.displayRule(archived));
    fieldForm.setColumnIndex(form.getNumActiveFields());
    fieldForm.setForm(form);
    FieldForm managed = fieldFormDao.save(fieldForm);
    form.addFieldForm(managed);
    return checkedManagedForm(fieldForm, managed);
  }

  @SuppressWarnings("unchecked") // The persisted value is checked against the concrete F instance.
  private static <F extends FieldForm> F checkedManagedForm(F original, FieldForm managed) {
    if (!original.getClass().isInstance(managed)) {
      throw new IllegalStateException("Persisted field form changed type");
    }
    return (F) managed;
  }

  Optional<IconEntity> createFormIcon(ArchivalDocumentParserRef parserRef, Long newFormId)
      throws FileNotFoundException, IOException {
    ArchivalForm archiveForm = parserRef.getArchivalForm();
    Long oldId = archiveForm.getFormId();
    IconEntity toSave = null;
    Optional<File> iconFileOpt = findIconFile(parserRef, oldId);
    if (iconFileOpt.isPresent()) {
      File iconFile = iconFileOpt.get();
      log.info(
          "Found form icon file {}, setting as form icon for new form [{}]",
          iconFile.getName(),
          newFormId);
      Optional<BufferedImage> im =
          getBufferedImageFromInputImageStream(new FileInputStream(iconFile));
      if (im.isPresent()) {
        toSave = createIconEntityFromImage(newFormId, im.get(), getExtension(iconFile.getName()));
        toSave = iconDao.saveIconEntity(toSave, true);
      } else {
        log.warn("Couldn't create buffered image from file {}", iconFile.getName());
      }
    }
    return Optional.ofNullable(toSave);
  }

  Optional<File> findIconFile(ArchivalDocumentParserRef parserRef, Long oldId) {
    return parserRef.getFileList().stream()
        .filter(
            f -> f.getName().matches(String.format(FormIconWriter.FORM_ICON_REGEX_TEMPLATE, oldId)))
        .findAny();
  }

  /**
   * Which archived field keys and display rules a form can be imported with. Keys are copied with
   * fields, so imported rules need no rewriting; but an archive is untrusted input, so the archived
   * keys and rules are validated as a whole first. Malformed or duplicated keys are dropped with
   * every rule (each field then gets a new key); otherwise rules that fail validation are all
   * dropped and the keys kept. Either way the form is imported, and a warning logged.
   */
  static final class ArchivedFieldRules {
    private final boolean keepKeys;
    private final boolean keepRules;

    private ArchivedFieldRules(boolean keepKeys, boolean keepRules) {
      this.keepKeys = keepKeys;
      this.keepRules = keepRules;
    }

    static ArchivedFieldRules check(ArchivalForm archiveForm) {
      List<RuleField> ruleFields = new ArrayList<>();
      boolean rulesReadable = true;
      for (ArchivalFieldForm field : archiveForm.getFieldFormList()) {
        FieldDisplayRule rule = null;
        if (field.getDisplayRule() != null) {
          try {
            rule = FieldDisplayRule.fromJson(field.getDisplayRule());
          } catch (IllegalArgumentException e) {
            rulesReadable = false;
          }
        }
        FieldType type = FieldType.getFieldTypeForString(field.getType());
        List<String> options =
            type == FieldType.RADIO || type == FieldType.CHOICE
                ? ElnChoiceValues.parse(field.getOptions())
                : List.of();
        ruleFields.add(new RuleField(field.getFieldKey(), type, rule, options, List.of()));
      }
      List<RuleError> errors = FieldDisplayRuleValidator.validate(ruleFields);
      boolean keysValid =
          errors.stream()
              .noneMatch(
                  e ->
                      FieldDisplayRuleValidator.KEY_INVALID.equals(e.code())
                          || FieldDisplayRuleValidator.KEY_DUPLICATE.equals(e.code()));
      boolean rulesValid = keysValid && rulesReadable && errors.isEmpty();
      if (!keysValid) {
        log.warn(
            "Archived form {} has malformed or duplicated field keys; importing it with new keys"
                + " and without display rules",
            archiveForm.getFormId());
      } else if (!rulesValid) {
        log.warn(
            "Archived form {} has invalid display rules; importing it without display rules",
            archiveForm.getFormId());
      }
      return new ArchivedFieldRules(keysValid, rulesValid);
    }

    /** The archived key, or null to have a new key assigned. */
    String fieldKey(ArchivalFieldForm field) {
      return keepKeys ? field.getFieldKey() : null;
    }

    /** The archived rule JSON, or null. */
    String displayRule(ArchivalFieldForm field) {
      return keepRules ? field.getDisplayRule() : null;
    }
  }

  private void setFieldForm(FieldForm cff, ArchivalFieldForm aff) {
    cff.setName(aff.getName());
    cff.setColumnIndex(aff.getColumnIndex());
    cff.setModificationDate(aff.getModificationDate());
    cff.setType(FieldType.getFieldTypeForString(aff.getType()));
  }
}
