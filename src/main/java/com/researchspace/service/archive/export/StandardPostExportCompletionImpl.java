package com.researchspace.service.archive.export;

import static java.util.stream.Collectors.toSet;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.ArchiveExportNotificationData;
import com.researchspace.model.comms.data.ArchiveExportNotificationData.ExportedNfsLinkData;
import com.researchspace.model.comms.data.ArchiveExportNotificationData.ExportedRecordData;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Record;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.service.aws.S3Utilities;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ui.velocity.VelocityEngineUtils;
import software.amazon.awssdk.http.SdkHttpResponse;

@Slf4j
public class StandardPostExportCompletionImpl implements PostArchiveCompletion {

  private @Autowired CommunicationManager commMgr;
  private @Autowired ApplicationEventPublisher publisher;
  private @Autowired ExportRemovalPolicy removalPolicy;
  private @Autowired VelocityEngine velocity;
  private @Autowired S3Utilities s3Utilities;

  @Value("${server.urls.prefix}")
  private String serverURLPrefix;

  @Value("${aws.s3.hasS3Access}")
  private boolean hasS3Access;

  @Override
  public void postArchiveCompletionOperations(
      IArchiveExportConfig expCfg, User user, ArchiveResult result) throws Exception {
    File file = result.getExportFile();
    String fileSize = FileUtils.byteCountToDisplaySize(file.length());
    handleS3Export(expCfg, result.getExportFile());
    notifyUserArchiveComplete(expCfg, user, result, file, fileSize);
    publisher.publishEvent(new GenericEvent(user, result, AuditAction.EXPORT));
  }

  private void handleS3Export(IArchiveExportConfig expCfg, File fileToExport) {
    if (hasS3Access) {

      SdkHttpResponse response = s3Utilities.getS3Uploader(fileToExport).apply(fileToExport);
      if (response == null || response.statusCode() != 200) {

        log.error(
            "Error exporting file: {} to S3 - received non-200 status code: {}",
            fileToExport.getName(),
            response == null ? "Unknown" : response.statusCode());
      } else if (!expCfg.isDeposit()) {
        // Delete archive file from local storage only if external repository is not configured
        fileToExport.delete();
      }
    }
  }

  private void notifyUserArchiveComplete(
      IArchiveExportConfig expCfg, User user, ArchiveResult result, File file, String fileSize) {
    String name = "";
    if (!StringUtils.isEmpty(expCfg.getDescription())) {
      name = "[" + StringEscapeUtils.escapeHtml(expCfg.getDescription()) + "]";
    }
    String link = ArchiveUtils.getExportDownloadLink(serverURLPrefix, result);
    String linkText = link;

    link = handlePresignedS3Url(result, file);

    Map<String, Object> config = new HashMap<>();
    config.put("link", link);
    config.put("linkText", linkText);
    config.put("name", name);
    config.put("size", fileSize);
    config.put("exportedRecordsSummary", getExportedRecordsSummary(expCfg, result));
    config.put("removalPolicyMessage", removalPolicy.getRemovalCircumstancesMsg());
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "exportCompleteNotification.vm", "UTF-8", config);
    ArchiveExportNotificationData data = createExportNotificationData(link, expCfg, result);
    Notification notification =
        commMgr.systemNotify(
            NotificationType.ARCHIVE_EXPORT_COMPLETED, data, msg, user.getUsername(), true);

    // update notification with link to export report page (requires notification id)
    config.put(
        "exportReportLink",
        ArchiveUtils.getExportReportLink(serverURLPrefix, notification.getId()));
    String updatedMsg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "exportCompleteNotification.vm", "UTF-8", config);
    commMgr.updateNotificationMessage(notification.getId(), updatedMsg);
  }

  private String handlePresignedS3Url(ArchiveResult result, File fileToPresign) {
    if (hasS3Access && s3Utilities.isArchiveInS3(fileToPresign.getName())) {
      URL s3Url = s3Utilities.getPresignedUrlForArchiveDownload(fileToPresign.getName());
      if (s3Url != null) {
        return s3Url.toString();
      } else {
        log.error(
            "Error getting pre-signed url from S3 for {}, URL was null", fileToPresign.getName());
      }
    }
    return ArchiveUtils.getExportDownloadLink(serverURLPrefix, result);
  }

  protected String getExportedRecordsSummary(IArchiveExportConfig expCfg, ArchiveResult result) {
    String exportSummaryMsg;
    int archivedRecordsCount =
        result.getArchivedRecords() == null ? 0 : result.getArchivedRecords().size();
    if (archivedRecordsCount == 0) {
      exportSummaryMsg = "No records were exported";
    } else {
      exportSummaryMsg =
          "The archive includes "
              + archivedRecordsCount
              + " record"
              + (archivedRecordsCount > 1 ? "s" : "");
    }
    if (expCfg.isIncludeNfsLinks()) {
      long includedNfsCount = 0;
      long skippedNfsCount = 0;
      if (result.getArchivedNfsFiles() != null) {
        for (ArchivalNfsFile nfsFile : result.getArchivedNfsFiles()) {
          if (nfsFile.isAddedToArchive()) {
            includedNfsCount++;
          } else {
            skippedNfsCount++;
          }
        }
      }
      if (includedNfsCount == 0) {
        exportSummaryMsg += ". No filestore links were included";
      } else {
        exportSummaryMsg +=
            " and "
                + includedNfsCount
                + " linked filestore item"
                + (includedNfsCount > 1 ? "s" : "");
      }
      if (skippedNfsCount > 0) {
        exportSummaryMsg +=
            ".<br/><br/>"
                + skippedNfsCount
                + " filestore link"
                + (skippedNfsCount > 1 ? "s were" : " was")
                + " not included";
      }
    }
    exportSummaryMsg += ".";
    return exportSummaryMsg;
  }

  protected ArchiveExportNotificationData createExportNotificationData(
      String downloadLink, IArchiveExportConfig expCfg, ArchiveResult result) {

    ArchiveExportNotificationData data = new ArchiveExportNotificationData();
    data.setDownloadLink(downloadLink);

    // config part
    data.setArchiveType(expCfg.getArchiveType());
    if (expCfg.getExportScope() != null) {
      data.setExportScope(expCfg.getExportScope().toString());
    }
    if (expCfg.getUserOrGroupId() != null) {
      data.setExportedUserOrGroupId(expCfg.getUserOrGroupId().toString());
    }
    data.setNfsLinksIncluded(expCfg.isIncludeNfsLinks());
    if (expCfg.getMaxNfsFileSize() > 0) {
      data.setMaxNfsFileSize(expCfg.getMaxNfsFileSize());
    }
    if (!expCfg.getExcludedNfsFileExtensions().isEmpty()) {
      data.setExcludedNfsFileExtensions(expCfg.getExcludedNfsFileExtensions());
    }

    // result part
    List<Record> archivedRecords = result.getArchivedRecords();
    List<ExportedRecordData> exportedRecordIds = new ArrayList<>();

    // lets get a set of exported folder/notebook ids
    Set<Long> archivedFolderIds = new HashSet<>();
    if (result.getArchivedFolders() != null) {
      archivedFolderIds =
          result.getArchivedFolders().stream().map(ArchiveFolder::getId).collect(toSet());
    }

    if (archivedRecords != null) {
      for (Record rec : archivedRecords) {
        ExportedRecordData recData = new ExportedRecordData();
        recData.setGlobalId(rec.getGlobalIdentifier());
        recData.setName(rec.getName());

        // search for a parent included in export, if any
        Set<Folder> parents = rec.getParentFolders();
        if (parents != null) {
          for (Folder p : parents) {
            if (archivedFolderIds.contains(p.getId())) {
              recData.setExportedParentGlobalId(p.getGlobalIdentifier());
              break;
            }
          }
        }
        exportedRecordIds.add(recData);
      }
    }
    data.setExportedRecords(exportedRecordIds);
    if (expCfg.isIncludeNfsLinks()) {
      List<ExportedNfsLinkData> exportedNfsLinkMessages = new ArrayList<>();
      if (result.getArchivedNfsFiles() != null) {
        for (ArchivalNfsFile nfsLink : result.getArchivedNfsFiles()) {
          ExportedNfsLinkData nfsLinkData = new ExportedNfsLinkData();
          nfsLinkData.setFileSystemName(nfsLink.getFileSystemName());
          nfsLinkData.setFileStorePath(nfsLink.getFileStorePath());
          nfsLinkData.setRelativePath(nfsLink.getRelativePath());
          nfsLinkData.setAddedToArchive(nfsLink.isAddedToArchive());
          nfsLinkData.setErrorMsg(nfsLink.getErrorMsg());
          if (nfsLink.isFolderLink()) {
            nfsLinkData.setFolderLink(true);
            nfsLinkData.setFolderExportSummaryMsg(nfsLink.getFolderExportSummaryMsg());
          }
          exportedNfsLinkMessages.add(nfsLinkData);
        }
      }
      data.setExportedNfsLinks(exportedNfsLinkMessages);
    }

    return data;
  }
}
