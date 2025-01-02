package com.researchspace.service.listeners;

import com.researchspace.model.User;
import com.researchspace.model.events.ImportCompleted;
import com.researchspace.model.events.RecordCopyEvent;
import com.researchspace.model.events.RecordCreatedEvent;
import com.researchspace.model.events.RestoreDeletedEvent;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.AutoshareManager;
import com.researchspace.service.UserManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles events that trigger autosharing. Processes events and decides what should be autoshared
 * or not.
 */
@Component
@Slf4j
public class RecordAutoshareListener {

  private @Autowired AutoshareManager autoshareMgr;
  private @Autowired UserManager userMgr;
  Predicate<BaseRecord> isTemplate = BaseRecord::isTemplate;

  @TransactionalEventListener()
  public void recordCreated(RecordCreatedEvent recordCreatedEvent) {
    if (!recordCreatedEvent.getCreatedItem().isTemplate()) {
      autoshareMgr.shareRecord(
          recordCreatedEvent.getCreatedItem(), recordCreatedEvent.getSubject());
    }
  }

  @TransactionalEventListener()
  public void recordCopied(RecordCopyEvent recordCopiedEvent) {
    User subject = recordCopiedEvent.getSubject();
    if (!subject.hasAutoshareGroups()) {
      log.debug("no autoshare groups setup for {}, skipping copy handler", subject.getUsername());
      return;
    }
    log.info(
        "Listening to recordcopy event for copied item {}",
        recordCopiedEvent.getCopiedItem().getUniqueCopy().getGlobalIdentifier());
    RecordCopyResult result = recordCopiedEvent.getCopiedItem();

    // ignore copied templates

    List<BaseRecord> toShareCandidates =
        result.getOriginalToCopy().values().stream()
            .filter(isTemplate.negate())
            .collect(Collectors.toList());

    // it's one or more documents
    if (!result.isFolderCopy()) {
      for (BaseRecord copied : toShareCandidates) {
        autoshareMgr.shareRecord(copied, recordCopiedEvent.getSubject());
      }
    } else {
      // we have a folder copy. We have to order the sharing so that notebooks get shared first
      // to avoid unnecessarily sharing documents that are in a notebook.
      doSharing(recordCopiedEvent.getSubject(), toShareCandidates);
    }
  }

  // might or might not be in an existing transaction
  @TransactionalEventListener(fallbackExecution = true)
  public void importArchiveComplete(ImportCompleted importCompleteEvent) {
    List<BaseRecord> importedItems = new ArrayList<>();
    importedItems.addAll(importCompleteEvent.getReport().getImportedNotebooks());
    importCompleteEvent.getReport().getImportedRecords().stream()
        .filter(isTemplate.negate())
        .forEach(importedItems::add);
    User importingUser = userMgr.getUserByUsername(importCompleteEvent.getImporter());
    doSharing(importingUser, importedItems);
  }

  @TransactionalEventListener()
  public void recordRestored(RestoreDeletedEvent restoreEvent) {
    List<BaseRecord> noTemplatesList =
        restoreEvent.getRestored().getRestoredItems().stream()
            .filter(isTemplate.negate())
            .collect(Collectors.toList());
    doSharing(restoreEvent.getSubject(), noTemplatesList);
  }

  private void doSharing(User subject, Collection<BaseRecord> toProcess) {
    // share notebooks and get  list of them.
    List<BaseRecord> sharedNotebooks = shareNotebooks(subject, toProcess);

    // just share documents not in the shared notebooks above
    // this is needed as the doc will be stale after updating the parent notebook and won't know
    // their parent notebooks are now shared. This code avoids the need to reload objects
    shareDocs(subject, toProcess, sharedNotebooks);
  }

  private void shareDocs(
      User subject, Collection<BaseRecord> copied, List<BaseRecord> sharedNotebooks) {
    copied.stream()
        .filter(BaseRecord::isStructuredDocument)
        // no intersection means a doc is not in a notebook that was shared, so needs to be
        // explicitly shared
        .filter(
            doc ->
                CollectionUtils.intersection(sharedNotebooks, doc.getParentNotebooks()).size() == 0)
        .forEach(doc -> autoshareMgr.shareRecord(doc, subject));
  }

  private List<BaseRecord> shareNotebooks(User subject, Collection<BaseRecord> toProcess) {
    return toProcess.stream()
        .filter(BaseRecord::isNotebook)
        .map(br -> autoshareMgr.shareRecord(br, subject))
        .filter(ServiceOperationResult::isSucceeded)
        .map(sor -> sor.getEntity().getShared())
        .collect(Collectors.toList());
  }
}
