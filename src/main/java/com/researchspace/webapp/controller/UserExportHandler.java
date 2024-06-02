package com.researchspace.webapp.controller;

import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.service.UserManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.PostArchiveCompletion;
import java.net.URI;
import java.util.concurrent.Future;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Handles export of user's work to XML/HTML */
@Component
public class UserExportHandler {

  private @Autowired ExportImport exportManager;
  private @Autowired UserManager userManager;

  @Qualifier("standardPostExportCompletionImpl")
  private @Autowired PostArchiveCompletion standardPostExport;

  /**
   * Asynchronously launches an export job and returns a Future.
   *
   * @throws AuthorizationException if not permitted to export this user's work
   */
  public Future<ArchiveResult> doUserArchive(
      ExportSelection exportSelection, ArchiveExportConfig exportCfg, User exporter, URI baseUri)
      throws Exception {

    Future<ArchiveResult> archive;
    User userToExport = userManager.getUserByUsername(exportSelection.getUsername());

    exportCfg.configureUserExport(userToExport);

    if (!userToExport.getUsername().equals(exporter.getUsername())) {
      exportManager.assertExporterCanExportUsersWork(userToExport, exporter);
    }
    archive =
        exportManager.exportArchiveAsyncUserWork(
            exportCfg, userToExport, baseUri, exporter, standardPostExport);
    return archive;
  }
}
