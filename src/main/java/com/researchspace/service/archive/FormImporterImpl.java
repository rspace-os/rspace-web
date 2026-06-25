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
    for (ArchivalFieldForm aff : flds1) {
      switch (FieldType.getFieldTypeForString(aff.getType())) {
        case NUMBER:
          persistFieldForm(
              form,
              new NumberFieldDTO<>(
                  aff.getMin(),
                  aff.getMax(),
                  aff.getDecimalPlace(),
                  aff.getDefaultValue(),
                  FieldType.NUMBER,
                  aff.getName()));
          break;
        case STRING:
          String tr = aff.isPassword() ? "true" : "false";
          persistFieldForm(form, new StringFieldDTO<>(aff.getName(), tr, aff.getDefaultValue()));
          break;
        case RADIO:
          persistFieldForm(
              form,
              new RadioFieldDTO<>(
                  aff.getOptions(),
                  aff.getSelection(),
                  aff.getName(),
                  aff.isDisplayAsPickList(),
                  aff.isSortAlphabetic()));
          break;
        case CHOICE:
          persistFieldForm(
              form,
              new ChoiceFieldDTO<>(
                  aff.getOptions(), aff.getMultipleChoice(), aff.getSelection(), aff.getName()));
          break;
        case DATE:
          persistFieldForm(
              form,
              new DateFieldDTO<>(
                  aff.getDefaultValue(),
                  aff.getMin(),
                  aff.getMax(),
                  aff.getDateFormat(),
                  aff.getName()));
          break;
        case TIME:
          persistFieldForm(
              form,
              new TimeFieldDTO<>(
                  aff.getDefaultValue(),
                  aff.getMin(),
                  aff.getMax(),
                  aff.getDateFormat(),
                  aff.getName()));
          break;

        default: // Text
          TextFieldForm tfm =
              persistFieldForm(form, new TextFieldDTO<>(aff.getName(), aff.getDefaultValue()));
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
  @SuppressWarnings("unchecked")
  private <F extends FieldForm> F persistFieldForm(RSForm form, FormFieldSource<F> dto) {
    F fieldForm = dto.createFieldForm();
    fieldForm.setColumnIndex(form.getNumActiveFields());
    fieldForm.setForm(form);
    F managed = (F) fieldFormDao.save(fieldForm);
    form.addFieldForm(managed);
    return managed;
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

  private void setFieldForm(FieldForm cff, ArchivalFieldForm aff) {
    cff.setName(aff.getName());
    cff.setColumnIndex(aff.getColumnIndex());
    cff.setModificationDate(aff.getModificationDate());
    cff.setType(FieldType.getFieldTypeForString(aff.getType()));
  }
}
