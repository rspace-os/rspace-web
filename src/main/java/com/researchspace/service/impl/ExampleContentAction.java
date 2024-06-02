package com.researchspace.service.impl;

import static com.researchspace.session.SessionAttributeUtils.EXAMPLECONTENT_HANDLED;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.User;
import com.researchspace.service.InitializedContent;
import com.researchspace.service.PostFirstLoginAction;
import com.researchspace.service.UserFolderSetup;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/** Imports example content from XML zip files into Examples folder. */
@Slf4j
public class ExampleContentAction extends AbstractPostFirstLoginHelper
    implements PostFirstLoginAction {

  private @Autowired ExportImport importer;
  private @Autowired ResourceLoader resourceLoader;
  private @Autowired @Qualifier("importRecordsOnly") ImportStrategy importRecordsOnly;

  // package scoped for testing
  @Value("${example.import.files}")
  String resourcesToLoad;

  public String doFirstLoginAfterContentInitialisation(
      User user, HttpSession session, InitializedContent inititalizedContent) {
    try {
      addZipImports(inititalizedContent.getFolder(), user);
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    setCompleted(user, session, getSessionAttributeName());
    return null;
  }

  private String[] getItemsToImport() {
    if (!StringUtils.isBlank(resourcesToLoad)) {
      return resourcesToLoad.split(",");
    } else {
      return new String[0];
    }
  }

  private void addZipImports(UserFolderSetup folders, User user) {
    String[] itemsToImport = getItemsToImport();
    for (String resourcePath : itemsToImport) {
      // do in try catch so 1 failure doesn't prevent others importing
      try {
        Resource res = resourceLoader.getResource(resourcePath);
        if (!res.exists()) {
          log.error("Resource {} does not exist", resourcePath);
          continue;
        }
        ArchivalImportConfig cfg = new ArchivalImportConfig();
        cfg.setTargetFolderId(folders.getExamples().getId());
        // move into example folders
        ImportArchiveReport report =
            importer.importArchive(
                res.getFile(),
                user.getUsername(),
                cfg,
                ProgressMonitor.NULL_MONITOR,
                importRecordsOnly::doImport);
        if (!report.isSuccessful()) {
          log.error(
              "Import of example did not succeed - {}",
              report.getErrorList().getAllErrorMessagesAsStringsSeparatedBy(","));
        }

      } catch (Exception e) {
        log.error("Could not import file {} : {}", resourcePath, e.getMessage());
      }
    }
  }

  @Override
  protected String getSessionAttributeName() {
    return EXAMPLECONTENT_HANDLED;
  }
}
