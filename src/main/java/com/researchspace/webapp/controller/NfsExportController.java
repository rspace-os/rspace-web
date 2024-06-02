package com.researchspace.webapp.controller;

import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.export.ExportArchiveDialogConfigDTO;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsExportPlan;
import com.researchspace.service.NfsExportManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.export.ArchiveExportPlanner;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Handles requests dealing with nfs file export */
@Controller
@RequestMapping("/nfsExport")
public class NfsExportController extends BaseController {

  protected static final String SESSION_NFS_EXPORT_PLANS = "SESSION_NFS_EXPORT_PLANS";

  @Autowired private ExportImport exportManager;

  @Autowired private NfsExportManager nfsExportManager;

  private @Autowired ArchiveExportPlanner archivePlanner;

  @Autowired private NfsController nfsController;

  @PostMapping("/ajax/createQuickExportPlan")
  @ResponseBody
  public NfsExportPlan createQuickExportPlan(
      @Valid @RequestBody ExportArchiveDialogConfigDTO exportDialogConfig,
      HttpServletRequest request,
      Principal principal)
      throws IOException, URISyntaxException {

    ExportSelection exportSelection = exportDialogConfig.getExportSelection();
    User exporter = getUserByUsername(principal.getName());
    ArchiveExportConfig exportCfg = exportDialogConfig.toArchiveExportConfig();
    exportCfg.setExporter(exporter);
    ImmutableExportRecordList exportRecordList =
        archivePlanner.createExportRecordList(exportCfg, exportSelection);

    List<GlobalIdentifier> recordsToExport = exportRecordList.getRecordsToExport();
    log.info("generating plan for records: [{}]", StringUtils.join(recordsToExport, ","));

    NfsExportPlan plan = nfsExportManager.generateQuickExportPlan(recordsToExport);
    log.info(
        "found [{}] link(s) from [{}] file system(s)",
        plan.getFoundNfsLinks().size(),
        plan.getFoundFileSystems().size());

    updatePlanWithFileSystemRequiringLogin(request, exporter, plan);
    log.info("[{}] file system(s) require login", plan.countFileSystemsRequiringLogin());

    plan.setPlanId("" + (new Date()).getTime());
    getNfsExportPlansFromSession(request).put(plan.getPlanId(), plan);

    return plan;
  }

  private void updatePlanWithFileSystemRequiringLogin(
      HttpServletRequest request, User exporter, NfsExportPlan plan) {
    Map<Long, NfsClient> nfsClients = nfsController.retrieveNfsClientsMapFromSession(request);
    nfsExportManager.checkLoggedAsStatusForFileSystemsInExportPlan(plan, nfsClients, exporter);
  }

  @PostMapping("/ajax/createFullExportPlan")
  @ResponseBody
  public NfsExportPlan createFullExportPlan(
      @RequestParam(value = "planId") String quickPlanId,
      @Valid @RequestBody ExportArchiveDialogConfigDTO exportDialogConfig,
      HttpServletRequest request,
      Principal principal) {

    Map<String, NfsExportPlan> plansFromSession = getNfsExportPlansFromSession(request);
    if (!plansFromSession.containsKey(quickPlanId)) {
      throw new IllegalArgumentException("No plan for id " + quickPlanId);
    }
    NfsExportPlan plan = plansFromSession.get(quickPlanId);

    // let's refresh list of system to login, as it could have changed since plan was generated
    User exporter = getUserByUsername(principal.getName());
    updatePlanWithFileSystemRequiringLogin(request, exporter, plan);
    log.info("[{}] file system(s) require login", plan.countFileSystemsRequiringLogin());

    Map<Long, NfsClient> nfsClients = nfsController.retrieveNfsClientsMapFromSession(request);
    IArchiveExportConfig archiveExportConfig = exportDialogConfig.toArchiveExportConfig();
    nfsExportManager.scanFileSystemsForFoundNfsLinks(plan, nfsClients, archiveExportConfig);

    return plan;
  }

  @SuppressWarnings("unchecked")
  protected Map<String, NfsExportPlan> getNfsExportPlansFromSession(HttpServletRequest request) {
    Map<String, NfsExportPlan> exportPlans =
        (Map<String, NfsExportPlan>) request.getSession().getAttribute(SESSION_NFS_EXPORT_PLANS);
    if (exportPlans == null) {
      exportPlans = new HashMap<>();
      request.getSession().setAttribute(SESSION_NFS_EXPORT_PLANS, exportPlans);
    }
    return exportPlans;
  }

  /*
   * ===============
   *  for tests
   * ===============
   */

  protected void setNfsController(NfsController nfsController) {
    this.nfsController = nfsController;
  }
}
