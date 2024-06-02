package com.researchspace.api.v1.service.impl;

import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.api.v1.service.ExportApiHandler;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.GroupManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.export.ArchiveExportPlanner;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class ExportApiSpringBatchHandlerImpl implements ExportApiHandler {
  /**
   * A maximum limit of the number of IDs that can be submitted in one go in a selection request.
   * This is fairly arbitrary, it's to prevent abuse - there has to be some limit. RSPAC-2148.
   * <em>Note</em> this value is exposed in API so update the Swagger docs if this needs to change.
   */
  public static final int MAX_IDS_ALLOWED = 2000;

  public void setIdStore(ExportApiStateTracker idStore) {
    this.idStore = idStore;
  }

  private @Autowired JobLauncher launcher;
  private @Autowired Job job;
  private @Autowired IGroupPermissionUtils grpPermUtils;
  private @Autowired UserManager userMgr;
  private @Autowired GroupManager grpMgr;
  private @Autowired ExportImport exportManager;
  private @Autowired OperationFailedMessageGenerator authGen;
  private @Autowired JobExplorer explorer;
  private @Autowired ExportApiStateTracker idStore;
  private @Autowired ArchiveExportPlanner planner;
  private @Autowired BaseRecordManager baseRecMgr;
  private @Autowired RecordManager recMgr;
  private @Autowired DiskSpaceChecker diskSpaceChecker;

  @Override
  public Optional<ApiJob> export(ExportApiConfig apiCfg, User apiClient) {
    ArchiveExportConfig config = new ArchiveExportConfig();
    config.setExportScope(ExportScope.valueOf(apiCfg.getScope().toUpperCase()));
    config.setArchiveType(apiCfg.getFormat().toLowerCase());
    config.setExporter(apiClient);
    if (apiCfg.getMaxLinkLevel() != null) {
      config.setMaxLinkLevel(apiCfg.getMaxLinkLevel());
    }
    config.setHasAllVersion(apiCfg.isIncludeRevisionHistory());
    String exportId = preLaunch(config, apiCfg, apiClient);
    JobParameters params = ExportJobParamFactory.createJobParams(apiCfg, apiClient, exportId);

    try {
      JobExecution exe = launcher.run(job, params);
      return Optional.of(new ApiJob(exe.getId(), exe.getStatus().toString()));
    } catch (JobExecutionAlreadyRunningException
        | JobRestartException
        | JobInstanceAlreadyCompleteException
        | JobParametersInvalidException e) {
      return Optional.empty();
    }
  }

  // some preliminary checking. no point launching a job doomed to immediate failure.
  // if ok then we get list of permitted Ids
  private String preLaunch(
      ArchiveExportConfig internalConfig, ExportApiConfig clientCfg, User apiClient) {
    ExportSelection exportSelection = checkGlobalPermissions(internalConfig, clientCfg, apiClient);
    checkQueue(internalConfig, clientCfg, apiClient);
    if (!diskSpaceChecker.canStartArchiveProcess()) {
      throw new IllegalStateException("Insufficient disk space to begin export");
    }
    return checkItemPermissions(internalConfig, apiClient, exportSelection);
  }

  private String checkItemPermissions(
      ArchiveExportConfig internalConfig, User apiClient, ExportSelection exportSelection) {
    StopWatch sw = new StopWatch();
    sw.start();
    log.info("export-prelaunch: Getting IDs to export...", sw.getTime());
    ImmutableExportRecordList toExport =
        planner.createExportRecordList(internalConfig, exportSelection);
    log.info(
        "export-prelaunch: Planning apiExport took {} ms for {} items",
        sw.getTime(),
        toExport.getRecordsToExportSize());
    List<GlobalIdentifier> toFilterIds = toExport.getRecordsToExport();
    List<BaseRecord> filteredByPerm = baseRecMgr.getByIdAndReadPermission(toFilterIds, apiClient);
    ExportRecordList filtered = filterItemsToExportByPermission(toExport, filteredByPerm);
    sw.stop();
    log.info(
        "export-prelaunch: Filtering by permission  took {} ms for {} items",
        sw.getTime(),
        toExport.getRecordsToExportSize());
    String id = generateExportId(apiClient);
    idStore.addExportRecordList(id, filtered);
    return id;
  }

  private ExportRecordList filterItemsToExportByPermission(
      ImmutableExportRecordList toExport, List<BaseRecord> filteredByPerm) {
    ExportRecordList filtered = new ExportRecordList();
    filtered.getFolderTree().addAll(toExport.getFolderTree());
    filtered.addAll(filteredByPerm.stream().map(BaseRecord::getOid).collect(Collectors.toList()));
    filtered.addAllFieldAttachments(toExport.getAssociatedFieldAttachments());
    return filtered;
  }

  // don't allow concurrent requests per user.
  private void checkQueue(
      ArchiveExportConfig internalConfig, ExportApiConfig clientCfg, User apiClient) {
    Set<JobExecution> executions = explorer.findRunningJobExecutions(ExportTasklet.EXPORT_JOB_NAME);
    boolean alreadyRunning =
        executions.stream().anyMatch(e -> runningExecutionsForUser(apiClient, e));
    if (alreadyRunning) {
      throw new TooManyRequestsException("There is already a running export job");
    }
  }

  private boolean runningExecutionsForUser(User client, JobExecution jobExe) {
    JobExecutionFacade facade = new JobExecutionFacade(jobExe);
    String exportRunner = facade.getStringValueFromJobParams(ExportTasklet.EXPORT_USER);
    return client.getUsername().equals(exportRunner);
  }

  private ExportSelection checkGlobalPermissions(
      ArchiveExportConfig internalConfig, ExportApiConfig clientCfg, User apiClient) {
    boolean permOK = false;
    ExportSelection exportSelection = null;
    if (ExportScope.GROUP.equals(internalConfig.getExportScope())) {
      if (clientCfg.getId() != null) {
        Group grp = grpMgr.getGroup(clientCfg.getId());
        permOK = grpPermUtils.userCanExportGroup(apiClient, grp); // 6 if fail
        if (permOK) {
          exportSelection = ExportSelection.createGroupExportSelection(clientCfg.getId());
        }
        // 5 if ok
        // else  no group specified, we'll choose 1st group, if none then is a fail
      } else {
        Group toExport = apiClient.getPrimaryLabGroupWithPIRole();
        if (toExport != null) {
          clientCfg.setId(toExport.getId());
          exportSelection = ExportSelection.createGroupExportSelection(toExport.getId());
          permOK = true; // 7
        } // else 8
      } // else 8

    } else if (ExportScope.USER.equals(internalConfig.getExportScope())) {
      if (exportingAnotherUser(clientCfg, apiClient)) {
        User userToExport = userMgr.get(clientCfg.getId());
        exportManager.assertExporterCanExportUsersWork(userToExport, apiClient); // 2 if fail
        exportSelection = ExportSelection.createUserExportSelection(userToExport.getUsername());
        // 1
      } else {
        exportSelection = ExportSelection.createUserExportSelection(apiClient.getUsername());
      }
      permOK = true; // 1,3,(4)
    } else if (ExportScope.SELECTION.equals(internalConfig.getExportScope())) {
      Set<Long> selections = clientCfg.getSelections();
      if (selections.isEmpty()) {
        throw new IllegalArgumentException("Please include one or more IDs to export");
      }
      if (selections.size() > MAX_IDS_ALLOWED) {
        throw new IllegalArgumentException(
            String.format(
                "Maximum number of Ids to export is %d, request contains %d Ids",
                MAX_IDS_ALLOWED, selections.size()));
      }
      // may be empty if no matching ids
      List<RSpaceDocView> recordViews = recMgr.getAllFrom(selections);
      if (recordViews.isEmpty()) {
        log.warn(
            "Export selection: ids were %s, but these did not exist",
            StringUtils.join(selections, ","));
        throw new AuthorizationException(
            authGen.getFailedMessage(apiClient, "Export a selection of "));
      }
      // these will be the same size
      String[] types = recordViews.stream().map(RSpaceDocView::getType).toArray(String[]::new);
      Long[] ids = recordViews.stream().map(RSpaceDocView::getId).toArray(Long[]::new);

      exportSelection = ExportSelection.createRecordsExportSelection(ids, types);
      permOK = true; // ok so far, will check read permissions later
    }
    if (!permOK) {
      throw new AuthorizationException(
          authGen.getFailedMessage(apiClient, "Export a user or group"));
    }

    return exportSelection;
  }

  private String generateExportId(User apiClient) {
    return apiClient.getUsername() + "-" + Instant.now().toEpochMilli();
  }

  private boolean exportingAnotherUser(ExportApiConfig clientCfg, User apiClient) {
    return clientCfg.getId() != null && !clientCfg.getId().equals(apiClient.getId());
  }
}
