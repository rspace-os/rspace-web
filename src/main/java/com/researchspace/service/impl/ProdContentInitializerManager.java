package com.researchspace.service.impl;

import com.axiope.model.record.init.AntibodySampleTemplate;
import com.axiope.model.record.init.BacterialSampleTemplate;
import com.axiope.model.record.init.Elisa;
import com.axiope.model.record.init.Experiment;
import com.axiope.model.record.init.FFPESampleTemplate;
import com.axiope.model.record.init.IBuiltinContent;
import com.axiope.model.record.init.LabProtocol;
import com.axiope.model.record.init.RtPCR;
import com.axiope.model.record.init.SampleTemplateBuiltIn;
import com.axiope.model.record.init.SyntheticWaxSampleTemplate;
import com.researchspace.Constants;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.FolderManager;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.UserFolderSetup;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.ImportArchiveReport;
import com.researchspace.service.archive.ImportStrategy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Default initializer for example content in user folders, for production releases. */
@Service("contentInitializer")
@Profile({"prod", "prod-test"})
public class ProdContentInitializerManager extends AbstractContentInitializer
    implements IContentInitializer {

  private @Autowired ExportImport importer;

  public void setImportRecordsOnly(ImportStrategy importRecordsOnly) {
    this.importRecordsOnly = importRecordsOnly;
  }

  private @Autowired @Qualifier("importRecordsOnly") ImportStrategy importRecordsOnly;
  private @Autowired FolderManager fMger;

  // this class is supposed to be a singleton but spring creates > 1 instance, so
  // this is static for now till it gets sorted.
  private static IBuiltinContent[] builtins =
      null; // this will be available to all users, created once.

  private static SampleTemplateBuiltIn[] sampleBuiltins =
      null; // this will be available to all users, created once.

  @Override
  protected void addCustomForms(User user) {
    List<RSForm> forms = new ArrayList<>();
    createBuiltIns();
    log.info("No forms in system; adding builtin forms");
    for (IBuiltinContent builtin : builtins) {
      RSForm form = builtin.createForm(user);

      log.info("Created form {}", form.getName());
      try {
        createAndSaveIconEntity(builtin.getFormIconName(), form);
      } catch (IOException e) {
        log.error(
            "Could not add icon [{}] to form [{}]. Message: {}",
            builtin.getFormIconName(),
            form.getName(),
            e.getMessage());
      }
      forms.add(form);
    }
  }

  @Override
  protected void addCustomSampleTemplates(User u) {
    List<Sample> templates = new ArrayList<>();
    createBuiltIns();
    log.info("No sample templates in system; adding builtin sample templates");
    for (SampleTemplateBuiltIn builtin : sampleBuiltins) {
      Optional<Sample> sampleTemplateOpt = builtin.createSampleTemplate(u);
      if (!sampleTemplateOpt.isPresent()) {
        log.warn("Couldn't create template with name : {}", builtin.getClass().getName());
        continue;
      }
      Sample template = sampleTemplateOpt.get();

      log.info("Created sample template {}", template.getName());
      try {
        createAndSaveIconEntity(builtin.getFormIconName(), template);
        createAndSavePreviewImage(builtin.getPreviewImageName(), u, template);
      } catch (IOException e) {
        log.error(
            "Could not add icon [{}] to form [{}]. Message: {}",
            builtin.getFormIconName(),
            template.getName(),
            e.getMessage());
      }
      templates.add(template);
    }
  }

  @Override
  protected Folder doInit(User user, UserFolderSetup folders) {

    createBuiltIns();

    StructuredDocument protocol = null;
    log.info(
        "Adding new examples in system; adding builtin examples for user [{}]", user.getFullName());
    for (IBuiltinContent builtin : builtins) {

      List<StructuredDocument> examples;
      if (builtin.isForm("Document")) {
        examples = builtin.createExamples(user, folders, protocol);
      } else {

        examples = builtin.createExamples(user, folders);
        if (builtin.isForm("Lab Protocol")) {
          if (examples.size() != 0) {
            protocol = examples.get(0); // save for Document
          }
        }
      }
      Set<RSForm> addedtoMenu = new HashSet<>();
      for (StructuredDocument example : examples) {
        addChild(folders.getExamples(), example, user);
        if (!addedtoMenu.contains(example.getForm())) {
          addedtoMenu.add(example.getForm());
          log.info("Example form - {}", example.getForm().getId());
          RSForm mostRecent = formDao.getMostRecentVersionForForm(example.getForm().getStableID());
          if (mostRecent == null) {
            log.warn(
                "Couldn't find most recent version of this form [{}], not adding to menu",
                example.getForm().getName());
          } else {
            formMgr.addFormToUserCreateMenu(user, mostRecent.getId(), user);
          }
        }
      }
    }

    log.info("Adding Notebook example in system; for user [{}]", user.getFullName());
    Locale locale = LocaleContextHolder.getLocale();
    ResourceBundle resources = ResourceBundle.getBundle(Constants.BUNDLE_KEY, locale);
    Notebook notebookFolder =
        recordFactory.createNotebook(resources.getString("form.notebookE1.folder"), user);
    addChild(folders.getExamples(), notebookFolder, user);

    try {
      addZipImports(folders, user);
    } catch (Exception e) {
      log.error("Error adding zip imports: ", e);
    }
    // don't change this
    return folderDao.get(folders.getUserRoot().getId());
  }

  private void createBuiltIns() {
    if (builtins == null) {
      BuiltInPersistorFacade persistor = new BuiltInPersistorFacade(this);
      builtins =
          new IBuiltinContent[] {
            new LabProtocol(persistor),
            new Experiment(persistor),
            new RtPCR(persistor),
            new Elisa(persistor)
          };
    }

    if (sampleBuiltins == null) {
      BuiltInPersistorFacade persistor = new BuiltInPersistorFacade(this);
      sampleBuiltins =
          new SampleTemplateBuiltIn[] {
            new AntibodySampleTemplate(persistor),
            new BacterialSampleTemplate(persistor),
            new FFPESampleTemplate(persistor),
            new SyntheticWaxSampleTemplate(persistor)
          };
    }
  }

  void addZipImports(UserFolderSetup folders, User user) throws Exception {
    ImportArchiveReport report = addChemicalDataSheets(folders, user);
    for (EcatMediaFile emf : report.getImportedMedia()) {

      int updated =
          recordDao.updateRecordToFolder(
              emf.getParents().iterator().next(), folders.getMediaImgExamples().getId());
      log.info(updated > 0 ? "Success" : "Failed");
    }
  }

  ImportArchiveReport addChemicalDataSheets(UserFolderSetup folders, User user)
      throws Exception, IOException {
    ClassPathResource res = new ClassPathResource("StartUpData/chemical-data-sheet.zip");
    ArchivalImportConfig cfg = new ArchivalImportConfig();
    // move into example folders
    ImportArchiveReport report =
        importer.importArchive(
            res.getFile(),
            user.getUsername(),
            cfg,
            ProgressMonitor.NULL_MONITOR,
            importRecordsOnly::doImport);
    if (report.getImportedRecords().size() > 0) {
      BaseRecord imported = report.getImportedRecords().iterator().next();
      Folder chemFolder = imported.getParent();
      fMger.move(
          chemFolder.getId(), folders.getExamples().getId(), chemFolder.getParent().getId(), user);
    }

    return report;
  }

  /*
   * for tests
   */
  void setImporter(ExportImport importer) {
    this.importer = importer;
  }

  void setFolderManager(FolderManager fMger) {
    this.fMger = fMger;
  }
}
