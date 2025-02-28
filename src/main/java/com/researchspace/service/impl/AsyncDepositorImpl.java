package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.argos.model.ArgosDMP;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dto.IntegrationInfo;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.model.repository.RepoDepositTag;
import com.researchspace.model.repository.UserDepositorAdapter;
import com.researchspace.repository.spi.ControlledVocabularyTerm;
import com.researchspace.repository.spi.IDepositor;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.repository.spi.LicenseDefs;
import com.researchspace.repository.spi.RepositoryConfig;
import com.researchspace.repository.spi.RepositoryDepositException;
import com.researchspace.repository.spi.RepositoryOperationResult;
import com.researchspace.repository.spi.SubmissionMetadata;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.DMPManager;
import com.researchspace.service.IAsyncArchiveDepositor;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.UserManager;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.velocity.app.VelocityEngine;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.ui.velocity.VelocityEngineUtils;

public class AsyncDepositorImpl implements IAsyncArchiveDepositor {

  @Value("${aws.s3.hasS3Access}")
  private boolean hasS3Access;

  private @Autowired UserExternalIdResolver extIdResolver;
  private @Autowired UserManager userManager;

  private @Autowired IntegrationsHandler integrationsHandler;
  private @Autowired DMPManager dmpManager;

  private Logger log = LoggerFactory.getLogger(AsyncDepositorImpl.class);
  private static String NOT_ALLOWED_FILENAME_CHARS = "[?*/\\\\]+";

  private @Autowired CommunicationManager commMgr;

  void setCommMgr(CommunicationManager commMgr) {
    this.commMgr = commMgr;
  }

  private @Autowired VelocityEngine velocity;

  void setVelocity(VelocityEngine velocity) {
    this.velocity = velocity;
  }

  private @Autowired DMPUpdateHandler dmpUpdateHandler;

  private RepositoryOperationResult doDeposit(
      User subject,
      IRepository repository,
      RepoDepositConfig repoDepositCfg,
      RepositoryConfig repoCfg,
      File file)
      throws IOException {
    RepositoryOperationResult result = null;
    try {
      SubmissionMetadata metadata = generateSubmissionMetaData(subject, repoDepositCfg);
      result = repository.submitDeposit(new UserDepositorAdapter(subject), file, metadata, repoCfg);
    } catch (Exception e) {
      result =
          new RepositoryOperationResult(false, "Submitting deposit failed:" + e.getMessage(), null);
    }
    return result;
  }

  // handles notifications of deposits
  void postDeposit(
      RepositoryOperationResult result,
      App app,
      User subject,
      File archive,
      RepoDepositConfig repoDepositConfig) {
    notifyDepositComplete(result, app, subject, archive);
    updateDMPS(result, subject, repoDepositConfig);
  }

  /**
   * Updates the DMPs after a deposit has been made.
   *
   * @param result the result of the deposit to the repository
   * @param subject the user who made the deposit
   * @param repoDepositConfig the configuration of the deposit
   */
  void updateDMPS(
      RepositoryOperationResult result, User subject, RepoDepositConfig repoDepositConfig) {
    if (result.isSucceeded() && repoDepositConfig.hasDMPs()) {
      // Dryad exports return an in progress dataset edit link, so we create the future public link
      // here to send to DMP
      if (repoDepositConfig.getAppName().equals(App.APP_DRYAD)) {
        RepositoryOperationResult newResultWithPublicLink =
            new RepositoryOperationResult(true, result.getMessage(), getDryadPublicUrl(result));
        dmpUpdateHandler.updateDMPS(
            newResultWithPublicLink::getUrl, subject, repoDepositConfig.getSelectedDMPs());
      } else {
        dmpUpdateHandler.updateDMPS(result::getUrl, subject, repoDepositConfig.getSelectedDMPs());
      }
    }
  }

  private URL getDryadPublicUrl(RepositoryOperationResult result) {
    String baseUrl = result.getUrl().toString();
    URL publicUrl = null;
    try {
      publicUrl = new URL(baseUrl.replace("/stash/edit/", "/stash/dataset/"));
    } catch (MalformedURLException e) {
      log.error("Failed to create public URL for Dryad", e);
      throw new RepositoryDepositException("Failed to create public URL for Dryad DMP Update");
    }
    return publicUrl;
  }

  private void notifyDepositComplete(
      RepositoryOperationResult result, App app, User subject, File archive) {
    Map<String, Object> config = new HashMap<>();
    config.put("result", result);
    config.put("app", app);

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "repoDepositCompleteNotification.vm", "UTF-8", config);
    commMgr.systemNotify(NotificationType.PROCESS_COMPLETED, msg, subject.getUsername(), true);
    // If S3 is configured it should now be safe to delete the file from the local filestore
    if (hasS3Access && archive != null) {
      try {
        archive.delete();
      } catch (Exception e) {
        log.error("Error Deleting file after S3 Export: {}", archive.getName(), e);
      }
    }
  }

  @Override
  public Future<RepositoryOperationResult> depositDocument(
      App app,
      User subject,
      IRepository repository,
      RepoDepositConfig repoDepositConfig,
      RepositoryConfig repoCfg,
      Future<EcatDocumentFile> documentFuture)
      throws InterruptedException, ExecutionException {

    RepositoryOperationResult result;
    EcatDocumentFile document = documentFuture.get();
    try {
      if (document != null) {
        File fileInFileStore = new File(new URI(document.getFileProperty().getAbsolutePathUri()));

        // A symbolic link is created, so that the file name in Dataverse matches export name and
        // not UUID of the export.
        Path tempDir = Files.createTempDirectory(null);
        Path symbolicLinkPath =
            Paths.get(
                tempDir.toString(), document.getName().replaceAll(NOT_ALLOWED_FILENAME_CHARS, ""));
        Files.createSymbolicLink(symbolicLinkPath, fileInFileStore.toPath());
        result =
            doDeposit(subject, repository, repoDepositConfig, repoCfg, symbolicLinkPath.toFile());
        Files.delete(symbolicLinkPath);
        Files.delete(tempDir);
      } else {
        result = new RepositoryOperationResult(false, "No file to deposit", null);
      }
    } catch (IOException | URISyntaxException e) {
      log.error("Submitting deposit failed: {}", e.getMessage());
      result = new RepositoryOperationResult(false, e.getMessage(), null);
    }
    postDeposit(result, app, subject, null, repoDepositConfig);
    return new AsyncResult<>(result);
  }

  @Override
  public Future<RepositoryOperationResult> depositArchive(
      App app,
      User subject,
      IRepository repository,
      RepoDepositConfig repoDepositConfig,
      RepositoryConfig repoCfg,
      Future<ArchiveResult> archive)
      throws InterruptedException, ExecutionException {

    RepositoryOperationResult result;
    try {
      result =
          doDeposit(subject, repository, repoDepositConfig, repoCfg, archive.get().getExportFile());
    } catch (Exception e) {
      log.error("Submitting deposit failed: {}", e.getMessage());
      result = new RepositoryOperationResult(false, e.getMessage(), null);
    }
    postDeposit(result, app, subject, archive.get().getExportFile(), repoDepositConfig);
    return new AsyncResult<>(result);
  }

  @Autowired
  @Qualifier("compositeFileStore")
  private FileStore fileStore;

  private SubmissionMetadata setDmpOnlineDmpToolOnSubmissionMetadata(
      User subject, SubmissionMetadata metadata, RepoDepositConfig archiveConfig)
      throws IOException, IllegalStateException {
    if (archiveConfig.hasDMPs()) {
      IntegrationInfo dmpOnlineInfo =
          integrationsHandler.getIntegration(subject, IntegrationsHandler.DMPONLINE_APP_NAME);

      if (dmpOnlineInfo.isEnabled() && dmpOnlineInfo.isAvailable()) {
        List<DMPUser> dmpsForUser = dmpManager.findDMPsForUser(subject);
        if (dmpsForUser.isEmpty()) {
          return metadata;
        }

        List<Long> dmpUserIds = archiveConfig.getSelectedDMPs();
        List<DMPUser> dmpsToUpdate =
            dmpsForUser.stream()
                .filter(dmpUser -> dmpUserIds.contains(dmpUser.getId()))
                .collect(Collectors.toList());
        if (dmpsToUpdate.size() != 1) {
          throw new NoSuchElementException("Could not find DMP with selected ID.");
        }

        DMPUser selectedDMP = dmpsToUpdate.get(0);
        EcatDocumentFile jsonFile = selectedDMP.getDmpDownloadPdf();
        File file = fileStore.findFile(jsonFile.getFileProperty());

        // read json from file
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode dmpFile = objectMapper.readValue(file, JsonNode.class);
        Optional<String> url = extractUrlFromDmpFile(dmpFile);
        if (url.isPresent()) {
          metadata.setDmpDoi(url);
        } else {
          log.warn("it is not been possible to attach the Dmp DOI to the metadata");
        }
      }
    }
    return metadata;
  }

  @Nullable
  private static Optional<String> extractUrlFromDmpFile(JsonNode dmpFile) {
    String url = null;
    if (dmpFile.get("dmp_id") != null) { // DMPOnline json file
      url = dmpFile.get("dmp_id").get("identifier").asText().replace("/api/v1", "");
    } else { // DMPTool json file
      Iterator<JsonNode> nodeIt = dmpFile.get("items").iterator();
      while (nodeIt.hasNext() && url == null) {
        JsonNode dmpNode = nodeIt.next();
        if (dmpNode.get("dmp") != null) {
          url = dmpNode.get("dmp").get("dmp_id").get("identifier").asText().replace("/api/v1", "");
        }
      }
    }
    UrlValidator urlValidator = new UrlValidator();
    return urlValidator.isValid(url) ? Optional.of(url) : Optional.empty();
  }

  private SubmissionMetadata setArgosDmpDoiOnSubmissionMetadata(
      User subject, SubmissionMetadata metadata, RepoDepositConfig archiveConfig)
      throws IOException, IllegalStateException {
    if (!archiveConfig.hasDMPs()) {
      return metadata;
    }

    IntegrationInfo argosInfo =
        integrationsHandler.getIntegration(subject, IntegrationsHandler.ARGOS_APP_NAME);
    if (argosInfo.isEnabled() && argosInfo.isAvailable()) {

      IntegrationInfo dmpToolInfo =
          integrationsHandler.getIntegration(subject, IntegrationsHandler.DMPTOOL_APP_NAME);
      if (dmpToolInfo.isEnabled()) {
        throw new IllegalStateException("DMPTool and Argos cannot both be enabled");
      }

      List<DMPUser> dmpsForUser = dmpManager.findDMPsForUser(subject);
      if (dmpsForUser.isEmpty()) {
        return metadata;
      }

      List<Long> dmpUserIds = archiveConfig.getSelectedDMPs();
      List<DMPUser> dmpsToUpdate =
          dmpsForUser.stream()
              .filter(dmpUser -> dmpUserIds.contains(dmpUser.getId()))
              .collect(Collectors.toList());
      if (dmpsToUpdate.size() != 1) {
        throw new NoSuchElementException("Could not find DMP with selected ID.");
      }

      DMPUser selectedDMP = dmpsToUpdate.get(0);
      EcatDocumentFile jsonFile = selectedDMP.getDmpDownloadPdf();
      File file = fileStore.findFile(jsonFile.getFileProperty());

      ObjectMapper objectMapper = new ObjectMapper();
      ArgosDMP dmp = objectMapper.readValue(file, ArgosDMP.class);
      Optional<String> doi = dmp.getDoi();
      if (doi.isPresent()) {
        metadata.setDmpDoi(doi);
      } else {
        throw new NoSuchElementException("DMP doesn't have a DOI");
      }
    }
    return metadata;
  }

  private SubmissionMetadata generateSubmissionMetaData(
      User subject, RepoDepositConfig archiveConfig) throws IOException, IllegalStateException {
    SubmissionMetadata metadata = new SubmissionMetadata();
    addOrcidIdsForAuthorsIfAvailable(archiveConfig);
    List<IDepositor> authors = new ArrayList<>(archiveConfig.getMeta().getAuthors());
    metadata.setAuthors(authors);
    List<IDepositor> contacts = new ArrayList<>(archiveConfig.getMeta().getContacts());
    metadata.setContacts(contacts);
    metadata.setDescription(archiveConfig.getMeta().getDescription());
    metadata.setSubjects(getSubjects(archiveConfig));
    if (!LicenseDefs.NO_LICENSE.equals(archiveConfig.getMeta().getLicenseUrl())) {
      metadata.setLicenseName(Optional.of(archiveConfig.getMeta().getLicenseName()));
      try {
        metadata.setLicense(Optional.of(new URL(archiveConfig.getMeta().getLicenseUrl())));
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            String.format("%s is not a valid URL", archiveConfig.getMeta().getLicenseUrl()));
      }
    } else {
      log.info("No license chosen.");
    }
    metadata.setTitle(archiveConfig.getMeta().getTitle());
    metadata.setPublish(archiveConfig.getMeta().isPublish());
    for (Map.Entry<String, String> entry :
        archiveConfig.getMeta().getOtherProperties().entrySet()) {
      metadata.addProperty(entry.getKey(), entry.getValue());
    }

    List<ControlledVocabularyTerm> terms = new ArrayList<>();
    for (RepoDepositTag tag : archiveConfig.getMeta().getTags()) {
      terms.add(
          ControlledVocabularyTerm.builder()
              .value(tag.getValue())
              .vocabulary(tag.getVocabulary())
              .uri(tag.getUri())
              .build());
    }
    metadata.setTerms(terms);

    return setDmpOnlineDmpToolOnSubmissionMetadata(
        subject,
        setArgosDmpDoiOnSubmissionMetadata(subject, metadata, archiveConfig),
        archiveConfig);
  }

  private List<String> getSubjects(RepoDepositConfig archiveConfig) {
    String subject = archiveConfig.getMeta().getSubject();
    return toList(subject);
  }

  private void addOrcidIdsForAuthorsIfAvailable(RepoDepositConfig archiveConfig) {
    archiveConfig
        .getMeta()
        .getAuthors()
        .forEach(
            author ->
                userManager
                    .getUserByEmail(author.getEmail())
                    .forEach(
                        user ->
                            extIdResolver
                                .getExternalIdForUser(user, IdentifierScheme.ORCID)
                                .ifPresent(extId -> author.getExternalIds().add(extId))));
  }
}
