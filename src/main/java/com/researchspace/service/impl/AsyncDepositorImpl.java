package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.dataverse.rspaceadapter.DataverseRSpaceRepository.IGSN_INVENTORY_LINKED_ITEMS;
import static com.researchspace.dataverse.rspaceadapter.DataverseRSpaceRepository.RAID_METADATA_PROPERTY;
import static com.researchspace.model.apps.App.APP_DATAVERSE;
import static com.researchspace.model.apps.App.APP_ZENODO;
import static com.researchspace.service.dmp.DmpPlatformStatusResolver.platformNameKey;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

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
import com.researchspace.model.dtos.RaidUpdateResult;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.model.repository.RepoDepositTag;
import com.researchspace.model.repository.UserDepositorAdapter;
import com.researchspace.repository.spi.ControlledVocabularyTerm;
import com.researchspace.repository.spi.IDepositor;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.repository.spi.LicenseDefs;
import com.researchspace.repository.spi.RepositoryConfig;
import com.researchspace.repository.spi.RepositoryOperationResult;
import com.researchspace.repository.spi.SubmissionMetadata;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.DMPManager;
import com.researchspace.service.IAsyncArchiveDepositor;
import com.researchspace.service.IntegrationsHandler;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.UserManager;
import com.researchspace.service.archive.export.ExportEcatDocumentResult;
import com.researchspace.service.archive.export.ExportFileResult;
import com.researchspace.service.dmp.DepositReport;
import com.researchspace.service.dmp.PlanReportResult;
import com.researchspace.service.dmp.ValueOutcome;
import com.researchspace.service.raid.RaIDDoiNotFoundException;
import com.researchspace.service.raid.RaIDServiceClientAdapter;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.spring.VelocityEngineUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

public class AsyncDepositorImpl implements IAsyncArchiveDepositor {

  @Value("${aws.s3.hasS3Access}")
  private boolean hasS3Access;

  private @Autowired UserExternalIdResolver extIdResolver;
  private @Autowired UserManager userManager;

  private @Autowired IntegrationsHandler integrationsHandler;
  private @Autowired DMPManager dmpManager;

  @Setter(value = AccessLevel.PROTECTED) /* test purposes */
  private @Autowired RaIDServiceClientAdapter raIDServiceClientAdapter;

  private Logger log = LoggerFactory.getLogger(AsyncDepositorImpl.class);
  private static String NOT_ALLOWED_FILENAME_CHARS = "[?*/\\\\]+";

  private @Autowired CommunicationManager commMgr;

  void setCommMgr(CommunicationManager commMgr) {
    this.commMgr = commMgr;
  }

  private @Autowired MessageSourceUtils messages;

  void setMessages(MessageSourceUtils messages) {
    this.messages = messages;
  }

  private @Autowired VelocityEngine velocity;

  void setVelocity(VelocityEngine velocity) {
    this.velocity = velocity;
  }

  private @Autowired DMPUpdateHandler dmpUpdateHandler;

  void setDmpUpdateHandler(DMPUpdateHandler dmpUpdateHandler) {
    this.dmpUpdateHandler = dmpUpdateHandler;
  }

  private RepositoryOperationResult doDepositArchive(
      User subject,
      IRepository repository,
      RepoDepositConfig repoDepositCfg,
      RepositoryConfig repoCfg,
      ArchiveResult archiveResult)
      throws IOException {
    RepositoryOperationResult result = null;
    File file = archiveResult.getExportFile();
    try {
      SubmissionMetadata metadata =
          generateArchiveSubmissionMetaData(subject, archiveResult, repoDepositCfg);
      result = repository.submitDeposit(new UserDepositorAdapter(subject), file, metadata, repoCfg);
    } catch (Exception e) {
      result =
          new RepositoryOperationResult(
              false, "Submitting archive deposit failed:" + e.getMessage(), null, null);
    }
    return result;
  }

  private RepositoryOperationResult doDepositEcatDocument(
      User subject,
      IRepository repository,
      RepoDepositConfig repoDepositCfg,
      RepositoryConfig repoCfg,
      ExportFileResult exportFileResult)
      throws IOException {
    RepositoryOperationResult result = null;
    try {
      SubmissionMetadata metadata =
          generateSubmissionMetaData(subject, exportFileResult, repoDepositCfg);
      result =
          repository.submitDeposit(
              new UserDepositorAdapter(subject), exportFileResult.getFile(), metadata, repoCfg);
    } catch (Exception e) {
      result =
          new RepositoryOperationResult(
              false, "Submitting document deposit failed:" + e.getMessage(), null, null);
    }
    return result;
  }

  // handles notifications of deposits
  void postDeposit(
      RepositoryOperationResult depositResult,
      App app,
      User subject,
      File archive,
      RepoDepositConfig repoDepositConfig) {
    notifyRepoDepositComplete(depositResult, app, subject, archive);
    updateDMPS(depositResult, app, subject, repoDepositConfig);
  }

  private RaidUpdateResult reportDoiToRaid(
      RepositoryOperationResult result,
      App app,
      User subject,
      RepoDepositConfig repoDepositConfig) {
    boolean updateSucceed = false;
    boolean doiNotFound = false;
    String doiLink = "";
    if (result.isSucceeded()) {
      try { // only supports DATAVERSE and ZENODO
        if (APP_DATAVERSE.equals(repoDepositConfig.getAppName())
            || APP_ZENODO.equals(repoDepositConfig.getAppName())) {
          doiLink = result.getDoiUrl().toString();
        }
        log.info(
            "Updating DOI link: \""
                + doiLink
                + "\" to the RelatedObject section of the RAiD \""
                + repoDepositConfig.getRaidAssociated().getRaid().getRaidIdentifier()
                + "\"");
        updateSucceed =
            raIDServiceClientAdapter.addRaIDRelatedObject(
                subject.getUsername(), repoDepositConfig.getRaidAssociated().getRaid(), doiLink);
      } catch (Exception e) {
        log.error(
            "Impossible to report the RelatedObject link \""
                + doiLink
                + "\" to the RAiD \""
                + repoDepositConfig.getRaidAssociated().getRaid().getRaidIdentifier()
                + "\" relatedObjects because of the following error: "
                + e);
        doiNotFound = e instanceof RaIDDoiNotFoundException;
      } finally {
        if (updateSucceed) {
          log.info(
              "The following DOI link: \""
                  + doiLink
                  + "\" has been successfully reported to the RelatedObject section of the RAiD \""
                  + repoDepositConfig.getRaidAssociated().getRaid().getRaidIdentifier()
                  + "\"");
        } else {
          log.warn(
              "RelatedObject to the RAiD \""
                  + repoDepositConfig.getRaidAssociated().getRaid().getRaidIdentifier()
                  + "\" has not been updated correctly.");
        }
      }
    }
    return new RaidUpdateResult(
        updateSucceed,
        app.getLabel(),
        repoDepositConfig.getRaidAssociated().getRaid().getRaidIdentifier(),
        repoDepositConfig.getRaidAssociated().getRaid().getRaidAgencyUrl(),
        doiLink,
        doiNotFound);
  }

  /**
   * Writes the deposit's link onto each ticked plan, then notifies the user of each plan's outcome.
   * Never throws, so write-back cannot fail the deposit.
   *
   * @param result the result of the deposit to the repository
   * @param app the repository app deposited to
   * @param subject the user who made the deposit
   * @param repoDepositConfig the configuration of the deposit
   */
  void updateDMPS(
      RepositoryOperationResult result,
      App app,
      User subject,
      RepoDepositConfig repoDepositConfig) {
    if (!result.isSucceeded() || !repoDepositConfig.hasDMPs()) {
      return;
    }
    try {
      DepositReport deposit =
          buildDepositReport(result, app.getLabel(), repoDepositConfig.getAppName());
      List<PlanReportResult> planResults =
          dmpUpdateHandler.report(deposit, subject, repoDepositConfig.getSelectedDMPs());
      URL repositoryLink =
          deposit.identifier() == null
              ? null
              : repositoryLink(result, repoDepositConfig.getAppName());
      notifyDmpUpdateComplete(planResults, deposit, repositoryLink, subject);
    } catch (RuntimeException e) {
      log.error("Updating plans after a deposit to {} failed", app.getLabel(), e);
    }
  }

  /** The DOI link if there is one, otherwise the repository link (Dryad's made public). */
  DepositReport buildDepositReport(
      RepositoryOperationResult result, String repositoryName, String appName) {
    if (result.getDoiUrl() != null) {
      return new DepositReport(repositoryName, result.getDoiUrl(), null);
    }
    return new DepositReport(repositoryName, null, repositoryLink(result, appName));
  }

  /** The deposit's repository link, with Dryad's edit link made public. */
  private URL repositoryLink(RepositoryOperationResult result, String appName) {
    URL link = result.getUrl();
    if (link != null && App.APP_DRYAD.equals(appName)) {
      return getDryadPublicUrl(link);
    }
    return link;
  }

  /**
   * Dryad returns an edit link holding a secret edit code; turns it into the dataset's landing page
   * on the link's own host, with the code removed. A link with no edit segment is returned
   * unchanged; an edit link that cannot be rewritten gives null, so nothing is sent.
   */
  private URL getDryadPublicUrl(URL repositoryUrl) {
    String path = repositoryUrl.getPath();
    if (!path.contains(DRYAD_EDIT_PATH)) {
      return repositoryUrl;
    }
    String landing;
    if (path.startsWith(DRYAD_OLD_EDIT_PATH)) {
      landing = landingPath("/stash/dataset/", path.substring(DRYAD_OLD_EDIT_PATH.length()));
    } else if (path.startsWith(DRYAD_EDIT_PATH)) {
      landing = landingPath("/dataset/", path.substring(DRYAD_EDIT_PATH.length()));
    } else {
      return null;
    }
    if (landing == null) {
      return null;
    }
    try {
      return new URL(
          repositoryUrl.getProtocol(), repositoryUrl.getHost(), repositoryUrl.getPort(), landing);
    } catch (MalformedURLException e) {
      // the link holds a secret edit code, so it is not logged
      log.warn("Couldn't make a Dryad landing page from its edit link");
      return null;
    }
  }

  private static final String DRYAD_OLD_EDIT_PATH = "/stash/edit/";
  private static final String DRYAD_EDIT_PATH = "/edit/";

  // Dryad URL-encodes the DOI into one path segment; what follows it is the edit code
  private static String landingPath(String landingPrefix, String doiAndEditCode) {
    String doi = StringUtils.substringBefore(doiAndEditCode, "/");
    return doi.isEmpty() ? null : landingPrefix + doi;
  }

  private void notifyRepoDepositComplete(
      RepositoryOperationResult result, App app, User subject, File archive) {
    Map<String, Object> config = new HashMap<>();
    config.put("result", result);
    config.put("app", app);
    config.put("msg", messages);

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

  private void notifyRaidUpdateComplete(RaidUpdateResult result, User subject) {
    Map<String, Object> config = new HashMap<>();
    config.put("succeed", result.isSucceed());
    config.put("repo", result.getRepoName());
    config.put("identifier", result.getRaidIdentifier());
    config.put("url", result.getRaidUrl());
    config.put("doi", result.getDoi());
    config.put("doiNotFound", result.isDoiNotFound());
    config.put("msg", messages);

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "raidUpdateCompleteNotification.vm", "UTF-8", config);
    commMgr.systemNotify(NotificationType.PROCESS_COMPLETED, msg, subject.getUsername(), true);
  }

  private void notifyDmpUpdateComplete(
      List<PlanReportResult> planResults, DepositReport deposit, URL repositoryLink, User subject) {
    if (planResults.isEmpty()) {
      return;
    }
    List<Map<String, String>> plans = new ArrayList<>();
    for (PlanReportResult planResult : planResults) {
      String platformName = messages.getMessage(platformNameKey(planResult.platform()));
      plans.add(
          Map.of(
              "heading",
              messages.format(
                  DMP_NOTIFICATION_KEY + "planHeading",
                  List.of(escapeHtml(planResult.planTitle()), platformName)),
              "outcome",
              planOutcomeLines(planResult, platformName, deposit)));
    }
    Map<String, Object> config = new HashMap<>();
    config.put("repo", escapeHtml(deposit.repositoryApp()));
    config.put(
        "doi", deposit.identifier() == null ? null : escapeHtml4(deposit.identifier().toString()));
    config.put("repoLink", repositoryLink == null ? null : escapeHtml4(repositoryLink.toString()));
    config.put("msg", messages);

    // drop trailing plans until the message fits, so it is never cut off mid-tag
    int shown = plans.size();
    String msg = mergeDmpNotification(config, plans, shown);
    while (msg.length() > DMP_NOTIFICATION_BUDGET && shown > 0) {
      shown--;
      msg = mergeDmpNotification(config, plans, shown);
    }
    commMgr.systemNotify(NotificationType.PROCESS_COMPLETED, msg, subject.getUsername(), true);
  }

  // Notification cuts messages at 2,000 characters; this keeps a margin below that
  private static final int DMP_NOTIFICATION_BUDGET = 1700;

  private String mergeDmpNotification(
      Map<String, Object> config, List<Map<String, String>> plans, int shown) {
    config.put("plans", plans.subList(0, shown));
    config.put("morePlans", plans.size() - shown);
    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, "dmpUpdateCompleteNotification.vm", "UTF-8", config);
  }

  private static final String DMP_NOTIFICATION_KEY =
      "email.notification.dmpUpdateCompleteNotification.";

  private String planOutcomeLines(
      PlanReportResult planResult, String platformName, DepositReport deposit) {
    return switch (planResult.status()) {
      case REPORTED ->
          planResult.values().stream()
              .map(this::valueOutcomeLine)
              .collect(Collectors.joining("<br/>"));
      case NOTHING_TO_SEND ->
          messages.format(
              DMP_NOTIFICATION_KEY + "nothingToSend", List.of(escapeHtml(deposit.repositoryApp())));
      case NOT_SUPPORTED ->
          messages.format(DMP_NOTIFICATION_KEY + "notSupported", List.of(platformName));
      case PLATFORM_OFF ->
          messages.format(DMP_NOTIFICATION_KEY + "platformOff", List.of(platformName));
      case NOT_CONNECTED ->
          messages.format(DMP_NOTIFICATION_KEY + "notConnected", List.of(platformName));
    };
  }

  private String valueOutcomeLine(ValueOutcome outcome) {
    if (outcome.value() == null) {
      return messages.format(
          DMP_NOTIFICATION_KEY + "failedNoValue", List.of(escapeHtml(outcome.error())));
    }
    String value = escapeHtml(outcome.value());
    return outcome.written()
        ? messages.format(DMP_NOTIFICATION_KEY + "valueWritten", List.of(value))
        : messages.format(
            DMP_NOTIFICATION_KEY + "valueFailed", List.of(value, escapeHtml(outcome.error())));
  }

  private static String escapeHtml(String text) {
    return escapeHtml4(StringUtils.defaultString(text));
  }

  @Override
  public Future<RepositoryOperationResult> depositDocument(
      App app,
      User subject,
      IRepository repository,
      RepoDepositConfig repoDepositConfig,
      RepositoryConfig repoCfg,
      Future<ExportEcatDocumentResult> documentFuture)
      throws InterruptedException, ExecutionException {

    RepositoryOperationResult repoDepositResult;
    ExportEcatDocumentResult exportResult = documentFuture.get();
    EcatDocumentFile document = exportResult.getEcatDocumentFile();
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
        repoDepositResult =
            doDepositEcatDocument(
                subject,
                repository,
                repoDepositConfig,
                repoCfg,
                new ExportFileResult(
                    symbolicLinkPath.toFile(), exportResult.getIgsnInventoryLinkedItems()));
        Files.delete(symbolicLinkPath);
        Files.delete(tempDir);
      } else {
        repoDepositResult =
            new RepositoryOperationResult(
                false, messages.getMessage("apps.deposit.errors.noFile"), null, null);
      }
    } catch (IOException | URISyntaxException e) {
      log.error("Submitting deposit failed: {}", e.getMessage());
      repoDepositResult = new RepositoryOperationResult(false, e.getMessage(), null, null);
    }
    postDeposit(repoDepositResult, app, subject, null, repoDepositConfig);
    return CompletableFuture.completedFuture(repoDepositResult);
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
    ArchiveResult archiveResult = archive.get();
    try {
      result = doDepositArchive(subject, repository, repoDepositConfig, repoCfg, archiveResult);
    } catch (Exception e) {
      log.error("Submitting deposit failed: {}", e.getMessage());
      result = new RepositoryOperationResult(false, e.getMessage(), null, null);
    }
    postDeposit(result, app, subject, archiveResult.getExportFile(), repoDepositConfig);
    return CompletableFuture.completedFuture(result);
  }

  @Override
  public Future<RaidUpdateResult> reportDoiToRaid(
      App app,
      Future<RepositoryOperationResult> futureDepositResult,
      User user,
      RepoDepositConfig repoDepositConfig)
      throws InterruptedException, ExecutionException {

    RepositoryOperationResult depositResult = futureDepositResult.get();
    if (repoDepositConfig.isExportToRaid() // only supported by DATAVERSE and ZENODO
        && (APP_DATAVERSE.equals(app.getName()) || APP_ZENODO.equals(app.getName()))) {
      RaidUpdateResult raidUpdateResult =
          reportDoiToRaid(depositResult, app, user, repoDepositConfig);
      notifyRaidUpdateComplete(raidUpdateResult, user);
      return CompletableFuture.completedFuture(raidUpdateResult);
    }
    return null;
  }

  @Autowired
  @Qualifier("compositeFileStore")
  private FileStore fileStore;

  private SubmissionMetadata setDmpOnlineDmpToolOnSubmissionMetadata(
      User subject, SubmissionMetadata metadata, RepoDepositConfig archiveConfig)
      throws IOException, IllegalStateException {
    if (archiveConfig.hasDMPs()) {
      if (isDmpOnlineEnabledAndAvailable(subject) || isDmpToolEnabledAndAvailable(subject)) {
        List<DMPUser> dmpsForUser = dmpManager.findDMPsForUser(subject);
        if (!dmpsForUser.isEmpty()) {

          List<Long> dmpUserIds = archiveConfig.getSelectedDMPs();
          List<DMPUser> dmpsToUpdate =
              dmpsForUser.stream()
                  .filter(dmpUser -> dmpUserIds.contains(dmpUser.getId()))
                  .collect(Collectors.toList());
          if (dmpsToUpdate.size() != 1) {
            throw new NoSuchElementException("Could not find DMP with selected ID.");
          }

          DMPUser selectedDMP = dmpsToUpdate.get(0);
          EcatDocumentFile jsonFile = selectedDMP.getDmpDownloadFile();
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
    }
    return metadata;
  }

  private boolean isDmpOnlineEnabledAndAvailable(User subject) {
    IntegrationInfo dmpOnlineInfo =
        integrationsHandler.getIntegration(subject, IntegrationsHandler.DMPONLINE_APP_NAME);
    return dmpOnlineInfo.isEnabled() && dmpOnlineInfo.isAvailable();
  }

  private boolean isDmpToolEnabledAndAvailable(User subject) {
    IntegrationInfo dmpToolInfo =
        integrationsHandler.getIntegration(subject, IntegrationsHandler.DMPTOOL_APP_NAME);
    return dmpToolInfo.isEnabled() && dmpToolInfo.isAvailable();
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
        throw new IllegalStateException(
            messages.getMessage("apps.dmp.errors.bothArgosAndDmpToolEnabled"));
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
      EcatDocumentFile jsonFile = selectedDMP.getDmpDownloadFile();
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
      User subject, ExportFileResult exportFileResult, RepoDepositConfig archiveConfig)
      throws IOException, IllegalStateException {
    return generateArchiveSubmissionMetaData(
        subject, exportFileResult.getIgsnInventoryLinkedItems(), archiveConfig);
  }

  private SubmissionMetadata generateArchiveSubmissionMetaData(
      User subject, ArchiveResult archiveResult, RepoDepositConfig archiveConfig)
      throws IOException, IllegalStateException {
    return generateArchiveSubmissionMetaData(
        subject, archiveResult.getIgsnInventoryLinkedItems(), archiveConfig);
  }

  private SubmissionMetadata generateArchiveSubmissionMetaData(
      User subject, Set<String> igsnInventoryLinkedItems, RepoDepositConfig archiveConfig)
      throws IOException, IllegalStateException {
    SubmissionMetadata metadata = new SubmissionMetadata();
    addOrcidIdsForAuthorsIfAvailable(archiveConfig);
    List<IDepositor> authors = new ArrayList<>(archiveConfig.getMeta().getAuthors());
    metadata.setAuthors(authors);
    List<IDepositor> contacts = new ArrayList<>(archiveConfig.getMeta().getContacts());
    metadata.setContacts(contacts);
    metadata.setDescription(archiveConfig.getMeta().getDescription());
    metadata.setSubjects(getSubjects(archiveConfig));
    if (LicenseDefs.NO_LICENSE_URL.equals(archiveConfig.getMeta().getLicenseUrl())) {
      log.info("No license chosen.");
    } else {
      metadata.setLicenseName(Optional.of(archiveConfig.getMeta().getLicenseName()));
      try {
        metadata.setLicense(Optional.of(new URL(archiveConfig.getMeta().getLicenseUrl())));
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(
            messages.getMessage(
                "apps.dmp.errors.invalidLicenseUrl",
                new Object[] {archiveConfig.getMeta().getLicenseUrl()}));
      }
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
    if (archiveConfig.getRaidAssociated() != null) {
      metadata
          .getOtherProperties()
          .put(
              RAID_METADATA_PROPERTY,
              archiveConfig.getRaidAssociated().getRaid().getRaidIdentifier());
    }
    if (igsnInventoryLinkedItems != null && !igsnInventoryLinkedItems.isEmpty()) {
      metadata
          .getOtherProperties()
          .put(IGSN_INVENTORY_LINKED_ITEMS, String.join(",", igsnInventoryLinkedItems));
    }

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
