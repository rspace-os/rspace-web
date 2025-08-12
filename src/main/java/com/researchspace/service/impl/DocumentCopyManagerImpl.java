package com.researchspace.service.impl;

import com.researchspace.dao.EcatCommentDao;
import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.dao.FieldDao;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.InternalLinkDao;
import com.researchspace.dao.RSMathDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.InternalLink;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.service.DocumentCopyManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RequiresActiveLicense;
import com.researchspace.service.StoichiometryManager;
import com.researchspace.service.ThumbnailManager;
import com.researchspace.service.exceptions.RecordCopyException;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("DocumentCopyManagerImpl")
public class DocumentCopyManagerImpl implements DocumentCopyManager {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private @Autowired EcatCommentDao ecatCommentDao;
  private @Autowired EcatImageAnnotationDao imageAnnotationDao;
  private @Autowired RSChemElementManager rsChemElementManager;
  private @Autowired StoichiometryManager stoichiometryManager;
  private @Autowired RSMathDao mathDao;
  private @Autowired FieldDao fieldDao;
  private @Autowired FieldManager fieldMgr;
  private @Autowired ThumbnailManager thumbMgr;
  private @Autowired BaseRecordAdaptable recordAdapter;
  private @Autowired RichTextUpdater updater;
  private @Autowired FieldParser fieldParser;
  private @Autowired RecordDao recordDao;
  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired FolderDao folderDao;
  private @Autowired InternalLinkDao internalLinkDao;
  private @Autowired @Qualifier("compositeFileStore") FileStore fileStore;

  private String copyAnnotationsToField(
      Long destParentId,
      Record record,
      String content,
      List<EcatImageAnnotation> imageAnns,
      User user) {
    String updatedContent = content;

    Map<Long, Long> oldParent2NewParent = new HashMap<>();
    Map<Long, Long> oldAnnoId2NewAnnoId = new HashMap<>();
    Map<Long, Long> oldSketchId2NewSketchId = new HashMap<>();

    for (EcatImageAnnotation origAnn : imageAnns) {
      boolean permitted = canUserCopyElement(origAnn, user);
      if (permitted) {
        EcatImageAnnotation copyAnn = origAnn.shallowCopy();
        copyAnn.setParentId(destParentId);
        copyAnn.setRecord(record);
        imageAnnotationDao.save(copyAnn);
        if (origAnn.isSketch()) {
          oldSketchId2NewSketchId.put(origAnn.getId(), copyAnn.getId());
        } else {
          oldParent2NewParent.put(origAnn.getParentId(), destParentId);
          oldAnnoId2NewAnnoId.put(origAnn.getId(), copyAnn.getId());
        }
      }
    }
    // we could use 1 map, but using 2 should avoid unnecessary
    // parsing of the text field.
    if (!oldParent2NewParent.isEmpty()) {
      updatedContent =
          updater.updateImageIdsAndAnnoIdsInCopy(
              oldParent2NewParent, oldAnnoId2NewAnnoId, updatedContent);
    }
    if (!oldSketchId2NewSketchId.isEmpty()) {
      updatedContent = updater.updateSketchIdsInCopy(oldSketchId2NewSketchId, updatedContent);
    }
    return updatedContent;
  }

  private String copyCommentsToField(
      Long destFieldId,
      Record destRecord,
      String content,
      List<EcatComment> originalComments,
      User user) {
    Map<Long, Long> oldKey2NewKey = new HashMap<>();
    for (EcatComment comment : originalComments) {
      boolean permitted = canUserCopyElement(comment, user);
      if (permitted) {
        EcatComment commentCopy = comment.getCopyWithCopiedCommentItems();
        commentCopy.setParentId(destFieldId);
        commentCopy.setRecord(destRecord);
        ecatCommentDao.addComment(commentCopy);
        oldKey2NewKey.put(comment.getComId(), commentCopy.getComId());
      }
    }
    return updater.updateCommentIdsInCopy(oldKey2NewKey, content);
  }

  private String copyChemElementsToField(
      Long destFieldId,
      Record destRecord,
      String fieldData,
      List<RSChemElement> originalChemElements,
      User user) {
    String updatedContent = fieldData;
    Map<Long, Long> chemOldKey2NewKey = new HashMap<>();
    for (RSChemElement origChem : originalChemElements) {
      boolean permitted = canUserCopyElement(origChem, user);
      if (permitted) {
        RSChemElement copyChem = origChem.shallowCopy();
        copyChem.setParentId(destFieldId);
        copyChem.setRecord(destRecord);
        try {
          rsChemElementManager.save(copyChem, user);
        } catch (IOException e) {
          log.error("Problem saving chemical in document with id {}.", destRecord.getId(), e);
        }

        if (destFieldId != null) { // skip for snippets
          try {
            if (stoichiometryManager.findByParentReactionId(origChem.getId()).isPresent()) {
              stoichiometryManager.copyForReaction(origChem.getId(), copyChem, user);
            }
          } catch (Exception e) {
            log.error(
                "Problem copying stoichiometry for reaction {} -> {} in document {}.",
                origChem.getId(),
                copyChem.getId(),
                destRecord.getId(),
                e);
          }
        }

        chemOldKey2NewKey.put(origChem.getId(), copyChem.getId());
      }
    }

    if (!chemOldKey2NewKey.isEmpty()) {
      updatedContent = updater.updateChemIdsInCopy(chemOldKey2NewKey, fieldData);
    }
    return updatedContent;
  }

  private String copyMathElementsToField(
      Long destFieldId,
      Record destRecord,
      String fieldData,
      List<RSMath> originalMathElements,
      User user) {
    String updatedContent = fieldData;
    Map<Long, Long> mathOldKey2NewKey = new HashMap<>();
    for (RSMath origMath : originalMathElements) {
      boolean permitted = canUserCopyElement(origMath, user);
      if (permitted) {
        RSMath copyMath = origMath.shallowCopy();
        // will be null if snippet
        if (destFieldId != null) {
          Field field = fieldDao.load(destFieldId);
          copyMath.setField(field);
        }
        copyMath.setRecord(destRecord);
        mathDao.save(copyMath);
        mathOldKey2NewKey.put(origMath.getId(), copyMath.getId());
      }
    }

    if (!mathOldKey2NewKey.isEmpty()) {
      updatedContent = updater.updateMathIdsInCopy(mathOldKey2NewKey, fieldData);
    }
    return updatedContent;
  }

  private String copyThumbnailsToField(
      Long newFieldId, String content, Set<Thumbnail> contentThumbs, User user) {

    boolean thumbsUpdated = false;
    for (Thumbnail thumb : contentThumbs) {
      try {
        boolean permitted = canUserCopyElement(thumb, user);
        if (permitted) {
          Thumbnail copy = thumbMgr.getThumbnail(thumb, user).getCopy();
          copy.setSourceParentId(newFieldId);
          thumbMgr.save(copy);
          thumbsUpdated = true;
        }
      } catch (IllegalArgumentException | IOException | URISyntaxException e) {
        log.error("error on updating Thumbnail copy", e);
      }
    }

    String newContent = content;
    if (thumbsUpdated) { // if no thumbs updated than don't bother
      newContent = updater.updateThumbnailParentIds(content, newFieldId);
    }
    return newContent;
  }

  /**
   * Parses content to find all annotations/sketches/comments/chem/math elements, copies the
   * elements found in content as children of destination record, creates relevant Field/Record
   * Attachments, and returns updated content (with links pointing to copied elements).
   *
   * <p>If destFieldId is null (i.e. for snippets) field ids won't be updated in the content, so
   * links inside snippets will still references original field id.
   *
   * @param destFieldId may be null
   * @param destRecord
   * @param content
   * @param user
   * @return content with updated links
   */
  @Override
  public String copyElementsInContent(
      Long destFieldId, Record destRecord, String content, User user) {
    FieldContents elementsFromContent = fieldParser.findFieldElementsInContent(content);

    // copy image annotations and sketches
    List<EcatImageAnnotation> annotationsAndSketches = new ArrayList<>();
    annotationsAndSketches.addAll(elementsFromContent.getImageAnnotations().getElements());
    annotationsAndSketches.addAll(elementsFromContent.getSketches().getElements());
    String updatedContent =
        copyAnnotationsToField(destFieldId, destRecord, content, annotationsAndSketches, user);

    // copy comments
    List<EcatComment> originalComments =
        elementsFromContent.getElements(EcatComment.class).getElements();
    updatedContent =
        copyCommentsToField(destFieldId, destRecord, updatedContent, originalComments, user);

    // copy chem elements
    List<RSChemElement> originalChemElements =
        elementsFromContent.getElements(RSChemElement.class).getElements();
    updatedContent =
        copyChemElementsToField(
            destFieldId, destRecord, updatedContent, originalChemElements, user);

    List<RSMath> mathElements = elementsFromContent.getElements(RSMath.class).getElements();
    updatedContent =
        copyMathElementsToField(destFieldId, destRecord, updatedContent, mathElements, user);

    if (destRecord instanceof StructuredDocument) {
      updatedContent =
          addFieldAttachmentsAndUpdateLinksInContent(destFieldId, updatedContent, user);
    } else if (destRecord instanceof Snippet) {
      addRecordAttachmentsForContent(destRecord, updatedContent, user);
    }
    return updatedContent;
  }

  private void copyRecordLinks(Long originalDocId, Long copiedDocId) {
    List<InternalLink> sourceDocLinks = internalLinkDao.getLinksFromRecordContent(originalDocId);
    for (InternalLink link : sourceDocLinks) {
      internalLinkDao.saveInternalLink(copiedDocId, link.getTarget().getId());
    }
  }

  /* copies media files / thumbnails / av but doesn't update content,
   * so the links still point to original field id */
  private void addRecordAttachmentsForContent(Record record, String content, User user) {
    FieldContents linkedItems = fieldParser.findFieldElementsInContent(content);
    List<EcatMediaFile> linkedFiles = linkedItems.getAllMediaFiles().getElements();
    for (EcatMediaFile media : linkedFiles) {
      boolean permitted = canUserCopyElement(media, user);
      if (permitted) {
        addMediaFileLink(media.getId(), record, user);
      }
    }
  }

  private void addMediaFileLink(Long ecatMediaFileId, Record record, User subject) {
    Record mediaFile = recordDao.get(ecatMediaFileId);
    if (!mediaFile.isMediaRecord()) {
      throw new IllegalArgumentException("Can't add non media-file link to field");
    }

    // can we edit the document by adding an attachment link to it?
    if (!permissionUtils.isPermitted(record, PermissionType.WRITE, subject)) {
      throw new AuthorizationException(
          "Unauthorised attempt by  "
              + subject.getUsername()
              + " to link medial file ["
              + mediaFile.getId()
              + "] to record ["
              + record.getId()
              + "]");
    }
    record.addMediaFileLink((EcatMediaFile) mediaFile);
    recordDao.save(record);
  }

  @Override
  @RequiresActiveLicense
  public RecordCopyResult copy(Record original, String newName, User user, Folder targetFolder) {

    RecordCopyResult result = new RecordCopyResult(targetFolder, false);

    Record copy = original.copy();
    copy.setName(newName);
    copy.setOwner(user);
    copy.setSharingACL(null); // wipe sharing
    copy = recordDao.save(copy);

    if (targetFolder != null && targetFolder.isFolder()) {
      targetFolder.addChild(copy, user, true); // check this
      copy = recordDao.save(copy);
    }

    if (original.hasType(RecordType.MEDIA_FILE)) {
      copy = copyMediaFile(original, newName, user, copy);
    } else if (original.isStructuredDocument()) {
      copy = copyStructuredDocument((StructuredDocument) original, user, copy);
    }

    recordDao.save(copy);
    result.add(original, copy);
    return result;
  }

  private Record copyStructuredDocument(StructuredDocument original, User user, Record copy) {
    // copy image annotations & sketches & comments & internal links
    StructuredDocument origSD = original;
    StructuredDocument copySD = (StructuredDocument) copy;
    assert (copySD.getFieldCount() == origSD.getFieldCount());

    List<Field> origFldList = origSD.getFields();
    List<Field> copyFldList = copySD.getFields();
    // make sure fields are in same order (col index of template)
    Collections.sort(origFldList);
    Collections.sort(copyFldList);
    for (int i = 0; i < copySD.getFieldCount(); i++) {

      Field fieldCopy = copySD.getFields().get(i);
      if (!(FieldType.TEXT.equals(fieldCopy.getType()))) {
        continue;
      }
      String updatedContent =
          copyElementsInContent(fieldCopy.getId(), copySD, fieldCopy.getFieldData(), user);
      fieldCopy.setFieldData(updatedContent);
    }
    List<Field> listFields = copySD.getFields();
    copySD.setAllFieldsValid(listFields.stream().allMatch(f -> f.isMandatoryStateSatisfied()));

    copyRecordLinks(origSD.getId(), copySD.getId());
    // RSPAC-178: the copy is unsigned so gallery links should again point to latest
    if (origSD.isSigned()) {
      updater.updateLinksWithRevisions(copySD, null);
    }
    recordDao.save(copySD);

    // refreshing entity as some fields could have changed i.e. FieldAttachments
    copy = recordDao.get(copySD.getId());
    return copy;
  }

  private Record copyMediaFile(Record original, String newName, User user, Record copy) {
    EcatMediaFile sourceMediaFile = (EcatMediaFile) recordDao.get(original.getId());
    // fail early if not authorised
    permissionUtils.isPermitted(sourceMediaFile, PermissionType.COPY, user);
    FileProperty sourceFileProperty = sourceMediaFile.getFileProperty();
    if (sourceFileProperty != null) {
      FileProperty copyFileProperty = new FileProperty();
      copyFileProperty.setFileCategory(sourceFileProperty.getFileCategory());
      copyFileProperty.setFileGroup(sourceFileProperty.getFileGroup());
      copyFileProperty.setFileOwner(sourceFileProperty.getFileOwner());
      copyFileProperty.setFileUser(sourceFileProperty.getFileUser());
      copyFileProperty.setFileVersion(sourceFileProperty.getFileVersion());
      copyFileProperty.setFileSize(sourceFileProperty.getFileSize());
      copyFileProperty.setRelPath(sourceFileProperty.getRelPath());
      copyFileProperty.setRoot(sourceFileProperty.getRoot());
      String fileName = createNewFileName(sourceFileProperty);
      Optional<FileInputStream> fis = fileStore.retrieve(sourceFileProperty);
      if (fis.isPresent()) {
        try {
          fileStore.save(copyFileProperty, fis.get(), fileName, FileDuplicateStrategy.AS_NEW);
        } catch (IOException e) {
          log.error(
              "Could not perform copy as IOException occurred while saving FileProperty {}",
              sourceFileProperty.getId());
          throw new RecordCopyException("Error while saving file property for copy", e);
        }
        EcatMediaFile ecatMediaFileCopy = (EcatMediaFile) copy;
        ecatMediaFileCopy.setFileName(newName);
        ecatMediaFileCopy.setFileProperty(copyFileProperty);
        ecatMediaFileCopy.setSize(Long.parseLong(sourceFileProperty.getFileSize()));
        copy = recordDao.save(ecatMediaFileCopy);
      } else {
        log.error(
            "Could not perform copy as could not retrieve original fileproperty {}",
            sourceFileProperty.getId());
        throw new RecordCopyException(
            "Could not retrieve original file property for record: " + original.getId());
      }
    }
    return copy;
  }

  private String createNewFileName(FileProperty sourceFileProperty) {
    String originalFileName = sourceFileProperty.getFileName();
    String result = "";
    Date date = new Date();
    // This replaces all _epochMilli timestamps that have been added to the filename previously
    result = originalFileName.replaceAll("_\\d{13,}", "");
    result =
        result.substring(0, result.lastIndexOf("."))
            + "_"
            + date.getTime()
            + result.substring(result.lastIndexOf("."));
    return result;
  }

  /* copies and updates content link to media files / thumbnails / av */
  private String addFieldAttachmentsAndUpdateLinksInContent(
      Long newFieldId, String content, User user) {

    FieldContents linkedItems = fieldParser.findFieldElementsInContent(content);
    List<EcatMediaFile> linkedFiles = linkedItems.getAllMediaFiles().getElements();
    boolean updatedMedia = false;
    for (EcatMediaFile media : linkedFiles) {
      boolean permitted = canUserCopyElement(media, user);
      if (permitted) {
        fieldMgr.addMediaFileLink(media.getId(), user, newFieldId, true);
        updatedMedia = true;
      }
    }

    String newContent = content;
    if (updatedMedia) { // if no media updated than don't bother
      newContent = copyThumbnailsToField(newFieldId, content, linkedItems.getThumbs(), user);
      newContent = updater.updateAVIdsInCopy(newContent, newFieldId);
    }
    return newContent;
  }

  private boolean canUserCopyElement(IFieldLinkableElement element, User user) {
    // check if it's a media file that is directly permitted
    if (element instanceof BaseRecord
        && permissionUtils.isPermitted((BaseRecord) element, PermissionType.COPY, user)) {
      return true;
    }
    // otherwise check access through parent records/templates
    Set<BaseRecord> parentRecords = recordAdapter.getAsBaseRecord(element, true);
    for (BaseRecord parent : parentRecords) {
      boolean canCopy = permissionUtils.isPermitted(parent, PermissionType.COPY, user);
      boolean canReadParentTemplate =
          parent.isTemplate() && permissionUtils.isPermitted(parent, PermissionType.READ, user);
      if (canCopy || canReadParentTemplate) {
        return true;
      }
    }
    return false;
  }
}
