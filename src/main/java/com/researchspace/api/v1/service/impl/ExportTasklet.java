package com.researchspace.api.v1.service.impl;

import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.core.util.progress.ProgressMonitorImpl;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.PostArchiveCompletion;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

/** Runs inside SpringBatch job to execute an export task. */
@Slf4j
public class ExportTasklet implements Tasklet {

  public static final String EXPORT_JOB_NAME = "exportDataJob";

  public static final String EXPORT_RESULT_KEY = "result";
  public static final String EXPORT_ID = "CRC32_CHECKSUM";

  /**
   * Used to type the batch job. Each batch job should put an identifier into the
   * JobExecutionContext
   */
  public static final String JOB_TYPE_KEY = "job.type";

  public static final String JOB_TYPE = "rs.export";
  /*
   * Batch job parameter name of username doing export
   */
  static final String EXPORT_USER = "export.user";

  private @Autowired ExportImport exportService;
  private @Autowired IPropertyHolder properties;
  private @Autowired UserManager mgr;

  private @Autowired PostArchiveCompletion postArchiveCompleter;
  private @Autowired ExportApiStateTracker exportApiState;

  Supplier<ExportRecordList> exportListSupplier(String id) {
    return () -> exportApiState.getById(id).get();
  }

  /*
   * Can asume basic validation of input already done in handler.
   *
   * @see org.springframework.batch.core.step.tasklet.Tasklet#execute(org.
   * springframework.batch.core.StepContribution,
   * org.springframework.batch.core.scope.context.ChunkContext)
   */
  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    JobParameters jobParams = chunkContext.getStepContext().getStepExecution().getJobParameters();
    addToContext(chunkContext, JOB_TYPE_KEY, JOB_TYPE);
    log.info("In ExportTasklet {}", toString());
    User user = mgr.getUserByUsernameNoSession(jobParams.getString(EXPORT_USER));
    ArchiveExportConfig config = new ArchiveExportConfig();
    config.setExportScope(ExportScope.valueOf(jobParams.getString("export.scope").toUpperCase()));
    config.setArchiveType(jobParams.getString("export.format"));
    config.setHasAllVersion(
        Boolean.parseBoolean(jobParams.getString("export.includeRevisionHistory")));
    String externalExportId = jobParams.getString("export.id");
    Supplier<ExportRecordList> exportIdSupplier = exportListSupplier(externalExportId);
    ProgressMonitor pm = createProgressMonitor(exportIdSupplier);
    config.setProgressMonitor(pm);
    exportApiState.addProgressMonitor(externalExportId, pm);

    if (config.isUserScope()) {
      config.setUserOrGroupId(user.getOid());
      Long optionalUserToExportId = jobParams.getLong("export.userOrGroupId");
      User toExport = user;
      if (exportingAnotherUser(user, optionalUserToExportId)) {
        toExport = mgr.get(optionalUserToExportId);
      }
      try {
        ArchiveResult result =
            exportService.exportArchiveSyncUserWork(
                config, toExport, serverUrl(), user, postArchiveCompleter, exportIdSupplier);

        updateContext(chunkContext, result);
      } catch (Exception e) {
        log.error("Error syncing user work: ", e);
      } finally {
        clearUp(externalExportId);
      }
    } else if (config.isGroupScope()) {
      Long groupId = jobParams.getLong("export.userOrGroupId");
      if (groupId != null) {
        try {
          ArchiveResult result =
              exportService.exportSyncGroup(
                  config, user, groupId, serverUrl(), postArchiveCompleter, exportIdSupplier);
          updateContext(chunkContext, result);
        } catch (Exception e) {
          log.error("Error syncing group: ", e);
        } finally {
          clearUp(externalExportId);
        }

      } else throw new IllegalStateException("Group export must specify a group ID to export");
    } else if (config.isSelectionScope()) {
      try {
        ArchiveResult result =
            exportService.exportSyncRecordSelection(
                config, user, serverUrl(), postArchiveCompleter, exportIdSupplier);
        updateContext(chunkContext, result);
      } catch (Exception e) {
        log.error("Error syncing record selection: ", e);
      } finally {
        clearUp(externalExportId);
      }
    }

    return RepeatStatus.FINISHED;
  }

  private void clearUp(String externalExportId) {
    exportApiState.clear(externalExportId);
  }

  private void updateContext(ChunkContext chunkContext, ArchiveResult result) {
    addToContext(chunkContext, EXPORT_RESULT_KEY, result.getExportFile().getName());
    addToContext(chunkContext, EXPORT_ID, result.getChecksum().getUid());
  }

  private ProgressMonitorImpl createProgressMonitor(Supplier<ExportRecordList> exportIdSupplier) {
    // maybe we have no records to export, pm should always show some work.
    int tickCount = Math.max(2, exportIdSupplier.get().getRecordsToExportSize() + 1);
    // shouldn't show 100 % if still zipping archive after record iteration is complete.
    final float SCALE_FACTOR_FOR_ZIPPING = 1.1f;
    tickCount = (int) (tickCount * SCALE_FACTOR_FOR_ZIPPING);

    ProgressMonitorImpl pm =
        new ProgressMonitorImpl(Math.max(1, tickCount), "number of items to export");
    pm.worked(1); // initialises and shows initial progress to date
    return pm;
  }

  private boolean exportingAnotherUser(User user, Long optionalUserToExportId) {
    return optionalUserToExportId != null
        && optionalUserToExportId != 0L
        && !user.getId().equals(optionalUserToExportId);
  }

  private URI serverUrl() throws URISyntaxException {
    return new URI(properties.getServerUrl());
  }

  private void addToContext(ChunkContext chunkContext, String key, Object value) {
    chunkContext
        .getStepContext()
        .getStepExecution()
        .getJobExecution()
        .getExecutionContext()
        .put(key, value);
  }
}
