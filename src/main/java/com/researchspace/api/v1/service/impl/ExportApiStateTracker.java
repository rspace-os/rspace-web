package com.researchspace.api.v1.service.impl;

import com.researchspace.archive.ExportRecordList;
import com.researchspace.core.util.progress.ProgressMonitor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;

/**
 * Holds state of ExportApi that must be shared between Controller layer and Spring Batch
 * ExportTasklet, based on ConcurrentHashMap This class is needed because: <br>
 * - SpringBatch JobPArameters only store primitives, but we want to pass a list of ids of records
 * to export to the ExportTasklet. <br>
 * - Export is executed as a single Step, and Spring Batch only persists state when a Step is
 * completed. So a progress monitor ( that needs to be consulted by a Job lookup) is not
 * retrievable, as the step is not persisted, so we need a bespoke mechanism
 */
@Slf4j
public class ExportApiStateTracker {

  private Map<String, ExportRecordList> recordList = new ConcurrentHashMap<>();
  private Map<String, ProgressMonitor> progressMonitors = new ConcurrentHashMap<>();

  /**
   * Register a list of Records to export
   *
   * @param id
   * @param itemsToAdd
   * @return
   */
  public ExportApiStateTracker addExportRecordList(String id, ExportRecordList itemsToAdd) {
    Validate.notNull(itemsToAdd, "itemsToAdd cannot be null");
    Validate.notNull(id, "id cannot be null");
    if (recordList.containsKey(id)) {
      throw new IllegalArgumentException(String.format("ID [%s] already exists", id));
    }
    recordList.put(id, itemsToAdd);
    log.info("After adding export id {}, there are {} items", id, recordList.size());
    return this;
  }

  /**
   * Register a progress monitor
   *
   * @param id
   * @param pm
   * @return
   */
  public ExportApiStateTracker addProgressMonitor(String id, ProgressMonitor pm) {
    Validate.notNull(pm, "progress monitor cannot be null");
    Validate.notNull(id, "id cannot be null");
    if (progressMonitors.containsKey(id)) {
      throw new IllegalArgumentException(String.format("ID [%s] already exists", id));
    }
    progressMonitors.put(id, pm);
    log.info(
        "After adding export id {}, there are {} progress monitors", id, progressMonitors.size());
    return this;
  }

  /**
   * Looks up ExportRecordList associated with given export API job
   *
   * @param id
   * @return
   */
  public Optional<ExportRecordList> getById(String id) {
    return Optional.ofNullable(recordList.get(id));
  }

  /**
   * Looks up progress monitor associated with given export API job
   *
   * @param id
   * @return
   */
  public Optional<ProgressMonitor> getProgressMonitorById(String id) {
    return Optional.ofNullable(progressMonitors.get(id));
  }

  /**
   * Clears the state for the export identified by <code>id</code>
   *
   * @param id
   * @return <code>true</code> if all state was successfully cleared.
   */
  public boolean clear(String id) {
    boolean recordsCleared = removeExplortListById(id);
    boolean pmsCleared = removeProgressMonitorById(id);
    return recordsCleared && pmsCleared;
  }

  private boolean removeExplortListById(String id) {
    return recordList.remove(id) != null;
  }

  private boolean removeProgressMonitorById(String id) {
    return progressMonitors.remove(id) != null;
  }
}
