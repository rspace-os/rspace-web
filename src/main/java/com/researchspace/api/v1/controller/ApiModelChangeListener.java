package com.researchspace.api.v1.controller;

import com.researchspace.CacheNames;
import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.model.events.RenameEvent;
import com.researchspace.model.record.BaseRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Listens to events that should trigger a cache eviction for {@link ApiFile} objects */
@Slf4j
@Component
@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS) // for spring 5.2?
public class ApiModelChangeListener {

  /**
   * Listens to RenameEvent<BaseRecord> events and evicts {@link ApiFile}. <br>
   * The method is only invoked if the event listener condition matches. If this is not the case,
   * then cache eviction does not occur.
   *
   * @param docRenameEvent
   */
  @TransactionalEventListener(condition = "#docRenameEvent.renamedItem.mediaRecord")
  @CacheEvict(cacheNames = CacheNames.APIFILE, key = "#docRenameEvent.renamedItem.id")
  public void handleFileRenamedEvent(RenameEvent<BaseRecord> docRenameEvent) {
    log.debug(
        "Record with id {} evicted from {}",
        docRenameEvent.getRenamedItem().getId(),
        CacheNames.APIFILE);
  }
}
