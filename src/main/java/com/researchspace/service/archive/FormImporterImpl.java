package com.researchspace.service.archive;

import static com.researchspace.core.util.imageutils.ImageUtils.getBufferedImageFromInputImageStream;
import static com.researchspace.model.record.IconEntity.createIconEntityFromImage;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.researchspace.archive.ArchivalDocumentParserRef;
import com.researchspace.archive.ArchivalFieldForm;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.dao.IconImgDao;
import com.researchspace.model.User;
import com.researchspace.model.Version;
import com.researchspace.model.dtos.ChoiceFieldDTO;
import com.researchspace.model.dtos.DateFieldDTO;
import com.researchspace.model.dtos.NumberFieldDTO;
import com.researchspace.model.dtos.RadioFieldDTO;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.dtos.TextFieldDTO;
import com.researchspace.model.dtos.TimeFieldDTO;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.TimeFieldForm;
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
          NumberFieldDTO<NumberFieldForm> ndto =
              new NumberFieldDTO<>(
                  aff.getMin(),
                  aff.getMax(),
                  aff.getDecimalPlace(),
                  aff.getDefaultValue(),
                  FieldType.NUMBER,
                  aff.getName());
          NumberFieldForm nfm = formManager.createFieldForm(ndto, form.getId(), user);
          form.addFieldForm(nfm);
          break;
        case STRING:
          String tr = aff.isPassword() ? "true" : "false";
          StringFieldDTO<StringFieldForm> sdto =
              new StringFieldDTO<>(aff.getName(), tr, aff.getDefaultValue());
          StringFieldForm sfm = formManager.createFieldForm(sdto, form.getId(), user);
          form.addFieldForm(sfm);
          break;
        case RADIO:
          RadioFieldDTO<RadioFieldForm> rdto =
              new RadioFieldDTO<>(
                  aff.getOptions(),
                  aff.getSelection(),
                  aff.getName(),
                  aff.isDisplayAsPickList(),
                  aff.isSortAlphabetic());
          RadioFieldForm radfm = formManager.createFieldForm(rdto, form.getId(), user);
          form.addFieldForm(radfm);
          break;
        case CHOICE:
          ChoiceFieldDTO<ChoiceFieldForm> cdto =
              new ChoiceFieldDTO<>(
                  aff.getOptions(), aff.getMultipleChoice(), aff.getSelection(), aff.getName());
          ChoiceFieldForm cfm = formManager.createFieldForm(cdto, form.getId(), user);
          form.addFieldForm(cfm);
          break;
        case DATE:
          DateFieldDTO<DateFieldForm> ddto =
              new DateFieldDTO<DateFieldForm>(
                  aff.getDefaultValue(),
                  aff.getMin(),
                  aff.getMax(),
                  aff.getDateFormat(),
                  aff.getName());
          DateFieldForm dfm = formManager.createFieldForm(ddto, form.getId(), user);
          break;
        case TIME:
          TimeFieldDTO<TimeFieldForm> mdto =
              new TimeFieldDTO<TimeFieldForm>(
                  aff.getDefaultValue(),
                  aff.getMin(),
                  aff.getMax(),
                  aff.getDateFormat(),
                  aff.getName());
          TimeFieldForm mfm = formManager.createFieldForm(mdto, form.getId(), user);
          break;

        default: // Text
          TextFieldDTO<TextFieldForm> tdto =
              new TextFieldDTO<>(aff.getName(), aff.getDefaultValue());
          TextFieldForm tfm = formManager.createFieldForm(tdto, form.getId(), user);
          setFieldForm(tfm, aff);
      }
    }
    return form;
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
