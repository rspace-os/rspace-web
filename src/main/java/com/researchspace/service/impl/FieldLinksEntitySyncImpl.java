package com.researchspace.service.impl;

import com.researchspace.dao.FieldDao;
import com.researchspace.dao.InternalLinkDao;
import com.researchspace.linkedelements.FieldContentDelta;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldLinksEntitiesSynchronizer;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.FieldManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class FieldLinksEntitySyncImpl implements FieldLinksEntitiesSynchronizer {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private @Autowired FieldDao fieldDao;
  private @Autowired FieldManager fieldManager;
  private @Autowired FieldParser fieldParser;
  private @Autowired InternalLinkDao internalLinkDao;

  @Override
  public FieldContentDelta syncFieldWithEntitiesOnautosave(
      Field permanentField, Field tempField, String newFieldData, User subject) {

    // measure the time it takes to sync entities (RSPAC-1688)
    StopWatch sw = new StopWatch();
    sw.start();

    // we're comparing incoming changes with the temp field, to detect
    // changes between incoming data and previous autosave
    FieldContentDelta incomingChangesRelativeToTempField =
        fieldParser.findFieldElementChanges(tempField.getFieldData(), newFieldData);
    logSyncStageTime(sw, "calculating field changes");

    if (incomingChangesRelativeToTempField.isUnchanged()) {
      return incomingChangesRelativeToTempField;
    }

    updateFieldAttachments(
        permanentField, subject, incomingChangesRelativeToTempField, newFieldData);
    logSyncStageTime(sw, "updating field attachments");

    updateInternalLinks(permanentField, subject, incomingChangesRelativeToTempField, newFieldData);
    logSyncStageTime(sw, "updating internal links");

    return incomingChangesRelativeToTempField;
  }

  private static final int SYNC_STAGE_TIME_LOG_THRESHOLD = 1000; // 1 second per stage

  private void logSyncStageTime(StopWatch stopWatch, String stageDesc) {
    stopWatch.suspend();
    long stageTime = stopWatch.getTime();
    if (stageTime > SYNC_STAGE_TIME_LOG_THRESHOLD) {
      log.warn("Field sync stage '{}' took {} millis", stageDesc, stageTime);
    } else {
      log.debug("Field sync stage '{}' took {} millis", stageDesc, stageTime);
    }
    stopWatch.reset();
    stopWatch.start();
  }

  private void updateInternalLinks(
      Field persistentField, User subject, FieldContentDelta delta, String newFieldData) {
    updateAddedLinks(persistentField, delta);
    updateRemovedLinks(persistentField, delta);
  }

  private void updateRemovedLinks(Field persistentField, FieldContentDelta delta) {
    /*
     * internal links are currently only relation we track on document level, rather
     * than field level
     */
    List<RecordInformation> removedLinksFromText = findInternalLinksRemovedFromText(delta);
    // set of candidates for deletion. This is in a separate collection so we can
    // remove items from this set
    // while iterating over removedLinks. Items remaining in
    // candidatesToRemoveFromDb after examining whole document
    // for links are then deleted.
    Set<RecordInformation> candidatesToRemoveFromDb = new HashSet<>(removedLinksFromText);

    if (removedLinksFromText.size() > 0) {
      // inspect all fields or temp fields for other occurrences of the link. There
      // may be other tempFields
      // containing updated links, so we analyse tempFields in preference to the
      // 'persisted' field.
      StructuredDocument srcDoc = persistentField.getStructuredDocument();
      for (Field fld : persistentField.getStructuredDocument().getFields()) {

        // get all links , in temp field if there is one.
        String data =
            fld.getTempField() != null ? fld.getTempField().getFieldData() : fld.getFieldData();
        FieldContents remainingElementsInField = fieldParser.findFieldElementsInContent(data);
        List<RecordInformation> remainingLinksInField =
            remainingElementsInField.getLinkedRecordsWithRelativeUrl().getElements();
        for (RecordInformation removedFromText : removedLinksFromText) {
          if (remainingLinksInField.contains(removedFromText)) {
            // removed link exists in another field, so we know now that we don't want to
            // delete the document level association.
            if (!fld.equals(persistentField)) {
              candidatesToRemoveFromDb.remove(removedFromText);
              // or else, in the field we are removing from, if there are more instances of
              // the
              // internal link than are being deleted, we want to keep the association as well
            } else if (someMoreLinksRemainInText(
                removedLinksFromText, remainingLinksInField, removedFromText)) {
              candidatesToRemoveFromDb.remove(removedFromText);
            }
            // else the 'removedFromText' is still a candidate for deletion
          }
          // else there are no other links to this candidate in this field
        }
      }
      // now we can delet
      for (RecordInformation toDelete : candidatesToRemoveFromDb) {
        internalLinkDao.deleteInternalLink(srcDoc.getId(), toDelete.getId());
      }
    }
  }

  private List<RecordInformation> findInternalLinksRemovedFromText(FieldContentDelta delta) {
    List<RecordInformation> removedLinks = new ArrayList<>();
    /* let's find all removed internal links */
    FieldContents removedContent = delta.getRemoved();
    if (removedContent.hasElements(RecordInformation.class)) {
      for (RecordInformation targetRecordInfo :
          removedContent.getLinkedRecordsWithRelativeUrl().getElements()) {
        removedLinks.add(targetRecordInfo);
      }
    }
    return removedLinks;
  }

  private void updateAddedLinks(Field persistentField, FieldContentDelta delta) {
    FieldContents addedContent = delta.getAdded();
    // add new link, if there is one.
    if (addedContent.hasAnyElements()) {
      if (addedContent.hasElements(RecordInformation.class)) {
        for (RecordInformation targetRecordInfo :
            addedContent.getLinkedRecordsWithRelativeUrl().getElements()) {
          internalLinkDao.saveInternalLink(
              persistentField.getStructuredDocument().getId(), targetRecordInfo.getId());
        }
      }
    }
  }

  private boolean someMoreLinksRemainInText(
      List<RecordInformation> removedLinks,
      List<RecordInformation> remainingLinksInField,
      RecordInformation removedFromText) {
    return Collections.frequency(remainingLinksInField, removedFromText)
        > Collections.frequency(removedLinks, removedFromText);
  }

  private boolean someMoreemfsRemainInField(
      List<EcatMediaFile> removedemfsFromField,
      List<EcatMediaFile> remainingemfsInField,
      EcatMediaFile removedFromField) {
    return Collections.frequency(remainingemfsInField, removedFromField)
        > Collections.frequency(removedemfsFromField, removedFromField);
  }

  private void updateFieldAttachments(
      final Field persistentField, User subject, FieldContentDelta delta, String newFieldData) {
    FieldContents addedContent = delta.getAdded();
    if (addedContent.hasAnyElements()) {
      for (EcatMediaFile emf : addedContent.getAllMediaFiles().getElements()) {
        fieldManager.addMediaFileLink(emf.getId(), subject, persistentField.getId(), false);
      }
    }
    FieldContents removedContent = delta.getRemoved();
    if (removedContent.hasAnyElements()) {

      // are the removed links in the persistent field?
      FieldContentDelta newChangesRelativeToFixedField =
          fieldParser.findFieldElementChanges(persistentField.getFieldData(), newFieldData);

      List<EcatMediaFile> removedemfsFromField = removedContent.getAllMediaFiles().getElements();
      // set of candidates for deletion. This is in a separate collection so we can remove items
      // from this set
      // while iterating over removedemfs. Items remaining in candidatesToRemoveFromDb
      // after examining whole document for links are then mark as deleted or removed from db.
      Set<EcatMediaFile> candidatesToRemoveFromDb = new HashSet<>(removedemfsFromField);

      String data =
          persistentField.getTempField() != null
              ? persistentField.getTempField().getFieldData()
              : persistentField.getFieldData();

      FieldContents remainingElementsInField = fieldParser.findFieldElementsInContent(data);
      List<EcatMediaFile> remainingemfsInField =
          remainingElementsInField.getAllMediaFiles().getElements(); // check here
      if (!candidatesToRemoveFromDb.isEmpty()) {
        for (EcatMediaFile removedFromField : removedemfsFromField) {

          if (someMoreemfsRemainInField(
              removedemfsFromField, remainingemfsInField, removedFromField)) {
            candidatesToRemoveFromDb.remove(removedFromField);
          }
        }
      }
      for (EcatMediaFile emf : candidatesToRemoveFromDb) {
        // if the attachment was already saved into the fixed,persistent field
        // (rather than autosaved into temp field), we jsut mark it as deleted, so
        // revision history still works
        if (removedItemIsInFixedField(newChangesRelativeToFixedField, emf)) {
          persistentField.setMediaFileLinkDeleted(emf, true);
          // else the association was just a temporary one in the temp field, we can
          // delete it.
        } else {
          persistentField
              .removeMediaFileLink(emf)
              .ifPresent(fa -> fieldDao.deleteFieldAttachment(fa));
        }
        fieldDao.save(persistentField);
      }
    }
  }

  private boolean removedItemIsInFixedField(
      FieldContentDelta newChangesRelativeToFixedField, EcatMediaFile emf) {
    return newChangesRelativeToFixedField
        .getRemoved()
        .getAllMediaFiles()
        .getElements()
        .contains(emf);
  }

  private Set<RecordInformation> getAllInternalLinksInDoc(StructuredDocument doc) {
    Set<RecordInformation> remainingLinks = new HashSet<>();
    for (Field field : doc.getTextFields()) {
      FieldContents remainingElements = fieldParser.findFieldElementsInContent(field.getData());
      remainingLinks.addAll(remainingElements.getLinkedRecordsWithRelativeUrl().getElements());
    }
    return remainingLinks;
  }

  @Override
  public FieldContentDelta revertSyncFieldWithEntitiesOnCancel(Field field, Field newFieldField) {
    FieldContentDelta delta = fieldParser.findFieldElementChanges(field, newFieldField);
    if (delta.isUnchanged()) {
      return delta;
    }
    // if we've added some links, we should delete the fieldMedia items, since we're
    // reverting to previous state - this won't be in the revision history, so we
    // can delete the row from the DB.
    FieldContents addedContent = delta.getAdded();
    if (addedContent.hasAnyElements()) {
      for (EcatMediaFile emf : addedContent.getAllMediaFiles().getElements()) {
        field.removeMediaFileLink(emf).ifPresent(fa -> fieldDao.deleteFieldAttachment(fa));
        fieldDao.save(field);
      }
    }
    FieldContents removedContent = delta.getRemoved();
    if (removedContent.hasAnyElements()) {
      for (EcatMediaFile emf : removedContent.getAllMediaFiles().getElements()) {
        field.addMediaFileLink(emf);
        fieldDao.save(field);
      }
    }
    return delta;
  }

  @Override
  public void revertSyncDocumentWithEntitiesOnCancel(
      StructuredDocument doc, List<FieldContentDelta> fieldChanges) {
    // get all added links
    /*
     * internal links are currently only relation we track on document level, rater
     * than field level
     */
    Set<RecordInformation> addedLinks = new HashSet<>();

    /* let's find all *added* internal links */
    for (FieldContentDelta delta : fieldChanges) {
      FieldContents addedContent = delta.getAdded();
      if (addedContent.hasElements(RecordInformation.class)) {
        for (RecordInformation targetRecordInfo :
            addedContent.getLinkedRecordsWithRelativeUrl().getElements()) {
          addedLinks.add(targetRecordInfo);
        }
      }
    }

    // if we have some added links in text, which already exist, then we don't do
    // anything.
    // but if they are not duplicated, then we delete them.
    if (addedLinks.size() > 0) {
      Set<RecordInformation> totalLinks = getAllInternalLinksInDoc(doc);

      /* if removed link is no longer present, remove the relation */
      for (RecordInformation linkTarget : addedLinks) {
        if (!totalLinks.contains(linkTarget)) {
          internalLinkDao.deleteInternalLink(doc.getId(), linkTarget.getId());
        }
      }
    }
  }
}
