package com.researchspace.archive.model;

import com.researchspace.archive.ArchivalDocument;
import com.researchspace.archive.ArchivalField;
import com.researchspace.archive.ArchivalFieldForm;
import com.researchspace.archive.ArchivalForm;
import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveComment;
import com.researchspace.archive.ArchiveCommentItem;
import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ArchiveUser;
import com.researchspace.archive.elninventory.ArchivalListOfMaterials;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.field.ChoiceField;
import com.researchspace.model.field.ChoiceFieldForm;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldForm;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.NumberFieldForm;
import com.researchspace.model.field.RadioFieldForm;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.field.TimeFieldForm;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.StructuredDocument;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang.Validate;

/** Factory class that will create Java objects for XML persistence from entity objects. */
public class ArchiveModelFactory {

  /**
   * Takes required information for export from EcatMediaFile
   *
   * @param mediaFile
   */
  public ArchivalGalleryMetadata createGalleryMetadata(EcatMediaFile mediaFile) {
    ArchivalGalleryMetadata rc = new ArchivalGalleryMetadata();
    rc.setId(mediaFile.getId());
    rc.setFileName(mediaFile.getFileName());
    rc.setName(mediaFile.getName());
    rc.setContentType(mediaFile.getContentType());
    rc.setExtension(mediaFile.getExtension());
    rc.setDescription(mediaFile.getDescription());
    rc.setCreationDate(mediaFile.getCreationDate());
    rc.setModificationDate(mediaFile.getModificationDateAsDate());
    rc.setCreatedBy(mediaFile.getCreatedBy());
    rc.setVersion(mediaFile.getVersion());
    rc.setParentGalleryFolderId(mediaFile.getOwnerParent().get().getId());

    if (mediaFile.isImage()) {
      EcatImage image = (EcatImage) mediaFile;
      if (image.getOriginalImage() != null) {
        rc.setOriginalId(image.getOriginalImage().getId());
        rc.setOriginalVersion(image.getOriginalImageVersion());
      }
    }
    return rc;
  }

  ArchivalFieldForm createArchivalFieldForm(FieldForm fm) {
    ArchivalFieldForm rc = new ArchivalFieldForm();
    rc.setFormFieldId(fm.getId());
    rc.setName(fm.getName());
    rc.setColumnIndex(fm.getColumnIndex());
    rc.setModificationDate(fm.getModificationDate());
    rc.setType(fm.getType().getType());
    rc.setSummary(fm.getSummary());
    switch (FieldType.getFieldTypeForString(rc.getType())) {
      case NUMBER: // "Number"
        NumberFieldForm nfm = (NumberFieldForm) fm;
        rc.setMin((nfm.getMinNumberValue() != null) ? nfm.getMinNumberValue() + "" : "");
        rc.setMax((nfm.getMaxNumberValue() != null) ? nfm.getMaxNumberValue() + "" : "");
        rc.setDefaultValue(
            (nfm.getDefaultNumberValue() != null) ? nfm.getDefaultNumberValue() + "" : "");
        Byte by = nfm.getDecimalPlaces();
        int pos = 1;
        if (by != null) pos = by.intValue();
        if (pos < 1) pos = 1;
        rc.setDecimalPlace(Integer.toString(pos));
        break;
      case STRING: // "String"
        // <summary>Type: [STRING], Is password: [false], Default : [string]
        // </summary>
        StringFieldForm sfm = (StringFieldForm) fm;
        rc.setPassword(sfm.isIfPassword());
        rc.setDefaultValue(sfm.getDefaultStringValue());
        break;
      case RADIO: // "Radio"
        // <summary>Type: [RADIO], Choices: [[a, b]], Default selection: [b]
        RadioFieldForm radfm = (RadioFieldForm) fm;
        rc.setOptions(radfm.getRadioOption());
        rc.setSelection(radfm.getDefaultRadioOption());
        break;
      case CHOICE: // "Choice")
        // <summary>Type: [CHOICE], Choices: [[a, b, c]], Default selection:
        // [[a, c]] </summary>
        ChoiceFieldForm cfm = (ChoiceFieldForm) fm;
        rc.setOptions(cfm.getChoiceOptions());
        rc.setSelection(cfm.getDefaultChoiceOption());
        rc.setMultipleChoice("no"); // why din't cfm set.
        break;
      case DATE: // Date
        // <summary>Type: [DATE], Min date: [Unspecified], Max date:
        // [2013-08-30], Default date:
        // [1970-01-01]</summary>
        DateFieldForm dfm = (DateFieldForm) fm;
        rc.setMin(dfm.getMinDateAsString());
        rc.setMax(dfm.getMaxDateAsString());
        rc.setDefaultValue(dfm.getDefaultDateAsString());
        rc.setDateFormat(dfm.getFormat());
        break;
      case TIME: // Time;
        TimeFieldForm mfm = (TimeFieldForm) fm;
        rc.setMin(mfm.getMinTimeAsString());
        rc.setMax(mfm.getmaxTimeAsString());
        rc.setDefaultValue(mfm.getDefaultTimeAsString());
        rc.setDateFormat(mfm.getTimeFormat());
        break;
      default: // Text
        TextFieldForm tfm = (TextFieldForm) fm;
        rc.setDefaultValue(tfm.getDefaultValue());
    }
    return rc;
  }

  /**
   * Creates an ArchivalForm for XML persistence from an {@link RSForm}
   *
   * @param rfm
   * @param code
   * @return
   */
  public ArchivalForm createArchivalForm(RSForm rfm, String code) {
    ArchivalForm rc = new ArchivalForm();
    rc.setFormId(rfm.getId());
    rc.setCode(code);
    rc.setType(rfm.getFormType().name());
    rc.setName(rfm.getName());
    rc.setCreateDate(rfm.getCreationDate());
    rc.setModificationDate(rfm.getModificationDate().getTime());
    rc.setPublishingState(rfm.getPublishingState().name());
    rc.setFormVersion(rfm.getVersion().asString());
    List<FieldForm> frms = rfm.getFieldForms();
    int k = 0;
    for (FieldForm fm : frms) {
      if (fm.isDeleted()) {
        continue; //
      }
      ArchivalFieldForm afm = createArchivalFieldForm(fm);
      afm.setCode(code + "_field_" + k);
      rc.getFieldFormList().add(afm);
      k++;
    }
    return rc;
  }

  /**
   * Generates a valid {@link ArchiveFolder} from the supplied {@link Folder} object.
   *
   * <p>This method copies values across from the supplied folder; it does not store a reference to
   * the Folder argument
   *
   * @param folder A persistent {@link Folder}
   */
  public ArchiveFolder createArchiveFolder(Folder folder) {
    ArchiveFolder archiveFolder = new ArchiveFolder();
    archiveFolder.setId(folder.getId());
    Optional<Folder> parent = folder.getOwnerParent();
    if (parent.isPresent()) {
      archiveFolder.setParentId(parent.get().getId());
    }
    if (folder.hasAncestorOfType(RecordType.ROOT_MEDIA, true)) {
      RSPath path = folder.getShortestPathToParent(f -> f.hasType(RecordType.ROOT_MEDIA));
      // i.e this flder is a user-created folder in media gallery
      if (path.size() >= 2) {
        // 'Images', 'Documents' etc used to identify the target folder on import
        BaseRecord mediaFolderType = path.get(1).get();
        archiveFolder.setMedia(true);
        archiveFolder.setMediaType(mediaFolderType.getName());
      }
    }
    archiveFolder.setType(folder.getType());
    archiveFolder.setName(folder.getName());
    archiveFolder.setTag(folder.getDocTag());
    archiveFolder.setTagMetaData(folder.getTagMetaData());
    archiveFolder.setOwner(createArchiveUser(folder.getOwner()));
    archiveFolder.setModificationDate(new Date(folder.getModificationDateAsDate().getTime()));
    archiveFolder.setCreationDate(new Date(folder.getCreationDate().getTime()));
    archiveFolder.setGlobalIdentifier(folder.getGlobalIdentifier());
    return archiveFolder;
  }

  public ArchiveUser createArchiveUser(User u) {
    ArchiveUser rc = new ArchiveUser();
    rc.setEmail(u.getEmail());
    rc.setFullName(u.getFullName());
    rc.setUniqueName(u.getUniqueName());
    return rc;
  }

  /**
   * Converts a RSpace document field to this class, for XML export
   *
   * @param fd
   */
  public ArchivalField createArchivalField(Field fd) {
    ArchivalField rc = new ArchivalField();
    rc.setFieldId(fd.getId());
    rc.setFieldName(fd.getName());
    rc.setFieldType(fd.getType().name());
    rc.setLastModifiedDate(
        new SimpleDateFormat(ArchivalDocument.DATE_FORMAT).format(fd.getModificationDate()));
    rc.setFieldData(fd.getFieldData());
    rc.setFieldDataPrintable(
        !(fd instanceof ChoiceField)
            ? fd.getFieldData()
            : ((ChoiceField) fd).getChoiceOptionSelectedAsString());
    addListOfMaterialsToArchivalField(fd.getListsOfMaterials(), rc);
    return rc;
  }

  public ArchiveComment createComment(EcatComment comment) {
    ArchiveComment rc = new ArchiveComment();
    rc.setItems(createCommentItems(comment.getItems()));
    rc.setAuthor(comment.getAuthor());
    rc.setLastUpdater(comment.getLastUpdater());
    rc.setCreateDate(comment.getCreateDate());
    rc.setUpdateDate(comment.getUpdateDate());
    return rc;
  }

  private List<ArchiveCommentItem> createCommentItems(List<EcatCommentItem> items) {
    return items.stream()
        .map(
            (item) -> {
              ArchiveCommentItem rc = new ArchiveCommentItem();
              rc.setCreateDate(item.getCreateDate());
              rc.setItemContent(item.getItemContent());
              rc.setLastUpdater(item.getLastUpdater());
              rc.setUpdateDate(item.getUpdateDate());
              return rc;
            })
        .collect(Collectors.toList());
  }

  public ArchivalDocument createArchivalDocument(StructuredDocument std) {
    ArchivalDocument rc = new ArchivalDocument();
    rc.setDocId(std.getId());
    rc.setName(std.getName());
    rc.setType(std.getType());
    rc.setCreatedBy(std.getCreatedBy());
    rc.setDocumentTag(std.getDocTag());
    rc.setTagMetaData(std.getTagMetaData());

    rc.setFormType(std.getForm().getFormType().name());
    rc.setCreationDate(std.getCreationDate());
    rc.setLastModifiedDate(std.getModificationDateAsDate());
    rc.setVersion(std.getUserVersion().getVersion());

    addParentFolderDetails(std.getOwnerParent(), rc);

    for (com.researchspace.model.field.Field fdx : std.getFields()) {
      ArchivalField afdx = createArchivalField(fdx);
      rc.addArchivalField(afdx);
    }
    return rc;
  }

  public void addParentFolderDetails(Optional<Folder> optional, ArchivalDocument doc) {
    optional.ifPresent(f -> doAddParentDetails(f, doc));
  }

  private void doAddParentDetails(Folder f, ArchivalDocument doc) {
    doc.setFolderId(f.getId());
    doc.setFolderName(f.getName());
    doc.setFolderType(f.getType());
  }

  public ArchivalNfsFile createArchivalNfs(NfsFileStore nfs, NfsElement element) {
    Validate.isTrue(
        nfs.getId().equals(element.getFileStoreId()),
        String.format(
            "NfsFileStore id for store %s does not match NfsElement store id %s",
            nfs.getId(), element.getFileStoreId()));
    ArchivalNfsFile file = new ArchivalNfsFile();
    file.setFileStoreId(nfs.getId());
    file.setFileStorePath(nfs.getPath());
    file.setFileSystemId(nfs.getFileSystem().getId());
    file.setFileSystemName(nfs.getFileSystem().getName());
    file.setFileSystemUrl(nfs.getFileSystem().getUrl());
    file.setRelativePath(element.getPath());
    return file;
  }

  private void addListOfMaterialsToArchivalField(List<ListOfMaterials> loms, ArchivalField rc) {
    if (loms == null) {
      return;
    }
    for (ListOfMaterials dbLom : loms) {
      ArchivalListOfMaterials lom = new ArchivalListOfMaterials(dbLom);
      rc.getListsOfMaterials().add(lom);
    }
  }
}
