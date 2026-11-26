package com.researchspace.webapp.integrations.protocolsio;

import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;

import com.researchspace.core.util.IoUtils;
import com.researchspace.document.importer.AbstractExternalDocImporter;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.protocolsio.Protocol;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.FormManager;
import com.researchspace.service.IconImageManager;
import com.researchspace.service.LocaleBoundMessages;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserLocaleService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.spring.VelocityEngineUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProtocolsIOToDocumentConverterImpl extends AbstractExternalDocImporter
    implements ProtocolsIOToDocumentConverter {

  private static final int DOWNLOAD_TIMEOUT_MILLIS = 10_000;

  // Hosts protocols.io serves protocol images from. The shared S3 host is limited to
  // protocols.io's own buckets.
  private static final Map<String, List<String>> ALLOWED_IMAGE_PATH_PREFIXES_BY_HOST =
      Map.of(
          "content.protocols.io", List.of("/"),
          "protocols-files.s3.amazonaws.com", List.of("/"),
          "pr-journal.s3.amazonaws.com", List.of("/"),
          "s3.amazonaws.com", List.of("/protocols-files/", "/pr-journal/"));

  private @Autowired VelocityEngine velocity;
  private @Autowired RecordManager recordMgr;
  private @Autowired ApplicationEventPublisher publisher;
  private @Autowired FormManager formMgr;
  private @Autowired RecordFactory recordFactory;
  private @Autowired ApplicationContext context;
  private @Autowired IconImageManager imgMgr;
  private @Autowired MessageSourceUtils messages;
  private @Autowired UserLocaleService userLocaleService;

  public void setVelocity(VelocityEngine velocity) {
    this.velocity = velocity;
  }

  public void setRecordFactory(RecordFactory recordFactory) {
    this.recordFactory = recordFactory;
  }

  @Override
  public StructuredDocument generateFromProtocol(
      Protocol toImport, User subject, Long parentFolderId) {
    String html = generateHtml(toImport);

    return saveDocument(toImport, html, subject, parentFolderId);
  }

  private StructuredDocument saveDocument(
      Protocol toImport, String html, User subject, Long parentFolderId) {
    StructuredDocument newDocument = createAPioDocument(toImport, subject, parentFolderId);
    newDocument = processHtml(html, newDocument, subject);

    publisher.publishEvent(new GenericEvent(subject, newDocument, AuditAction.CREATE));
    return newDocument;
  }

  private StructuredDocument createAPioDocument(
      Protocol toImport, User subject, Long parentFolderId) {
    RSForm form =
        formMgr.getCurrentSystemForm("ProtocolsIO").orElseGet(() -> createProtocolsIOForm(subject));

    Long importFolderId =
        parentFolderId != null ? parentFolderId : folderMgr.getImportsFolder(subject).getId();
    return recordMgr.createNewStructuredDocument(
        importFolderId, form.getId(), toImport.getTitle(), subject, new DefaultRecordContext());
  }

  StructuredDocument processHtml(String html, StructuredDocument rspaceDocument, User subject) {
    org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);
    Elements imgElements = jsoupDocument.select("img");
    Map<String, File> downloaded = new LinkedHashMap<>();
    try {
      downloadImagesToTempFiles(imgElements, subject, downloaded);
      // only files downloaded above may be read from disk, never a path taken from the HTML
      Set<String> downloadedPaths = new HashSet<>();
      downloaded.values().forEach(f -> downloadedPaths.add(f.getAbsolutePath()));
      Elements downloadedImgs = new Elements();
      for (Element el : imgElements) {
        if (downloadedPaths.contains(el.attr("src"))) {
          downloadedImgs.add(el);
        }
      }

      Folder imageTargetFolder = createTargetImageFolderIfImages(rspaceDocument.getName(), subject);
      // this has to get called even if no images
      return updateFieldContent(
          subject,
          jsoupDocument,
          downloadedImgs,
          imageTargetFolder,
          rspaceDocument,
          el -> el.attr("src"));
    } finally {
      downloaded.values().forEach(FileUtils::deleteQuietly);
    }
  }

  // null is valid return option
  private Folder createTargetImageFolderIfImages(String folderName, User subject) {
    return folderMgr.createGallerySubfolder(folderName, IMAGES_MEDIA_FLDER_NAME, subject);
  }

  protected Elements getImages(String html) {
    org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);
    return jsoupDocument.select("img");
  }

  /**
   * Downloads images hosted by protocols.io to temp files, and points each image's src at its temp
   * file. Images hosted anywhere else are removed: the protocol comes from the browser, so its
   * image links could point at server files or internal addresses.
   *
   * @param srcRefToFileMap filled as each file downloads, so the caller can delete them even if a
   *     later download throws
   */
  protected void downloadImagesToTempFiles(
      Elements imgElements, User subject, Map<String, File> srcRefToFileMap) {
    log.info("There are {} images referenced in Protocol", imgElements.size());
    removeDuplicateElements(imgElements);
    for (Element el : imgElements) {
      String srcString = el.attr("src");

      URL url = toAllowedImageUrl(srcString);
      if (url == null) {
        log.warn(
            "Skipped protocols.io image not hosted by protocols.io, for user {}: {}",
            subject.getUsername(),
            forLog(srcString));
        el.remove();
        continue;
      }

      if (srcRefToFileMap.containsKey(srcString)) {
        el.attr("src", srcRefToFileMap.get(srcString).getAbsolutePath());
        log.info(" already downloaded {}, skipping", srcString);
        continue;
      }
      File tempFile = null;
      try {
        tempFile = getTempFileFromURL(url);
        downloadFile(url, tempFile);
        srcRefToFileMap.put(srcString, tempFile);
        el.attr("src", tempFile.getAbsolutePath());
      } catch (IOException e) {
        FileUtils.deleteQuietly(tempFile);
        log.warn("Couldn't retrieve file from URL {} - {}", url, e.getMessage());
      }
    }
  }

  /**
   * @return the URL if it is https on a protocols.io image host, else null
   */
  static URL toAllowedImageUrl(String src) {
    try {
      URI uri = new URI(src).normalize();
      String host = uri.getHost();
      String path = uri.getPath();
      List<String> allowedPathPrefixes =
          host == null
              ? List.of()
              : ALLOWED_IMAGE_PATH_PREFIXES_BY_HOST.getOrDefault(
                  host.toLowerCase(Locale.ROOT), List.of());
      boolean allowed =
          "https".equalsIgnoreCase(uri.getScheme())
              && uri.getRawUserInfo() == null
              && uri.getPort() == -1
              && path != null
              && !path.contains("..")
              && allowedPathPrefixes.stream().anyMatch(path::startsWith);
      return allowed ? uri.toURL() : null;
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
      return null;
    }
  }

  private static String forLog(String value) {
    return value.replaceAll("[\\r\\n]", "_");
  }

  private void removeDuplicateElements(Elements imgElements) {
    Set<String> refs = new HashSet<>();
    for (Element el : imgElements) {
      if (refs.contains(el.attr("src"))) {
        el.remove();
      } else {
        refs.add(el.attr("src"));
      }
    }
  }

  File getTempFileFromURL(URL url) throws IOException {
    String fNameString = FilenameUtils.getBaseName(url.getPath());
    File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
    return File.createTempFile(
        fNameString, "." + FilenameUtils.getExtension(url.getPath()), secureTmpDir);
  }

  void downloadFile(URL url, File tempFile) throws IOException {
    log.info("Downloading from {}", url);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    try {
      // a redirect could lead to an internal address
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(DOWNLOAD_TIMEOUT_MILLIS);
      connection.setReadTimeout(DOWNLOAD_TIMEOUT_MILLIS);
      int status = connection.getResponseCode();
      if (status != HttpURLConnection.HTTP_OK) {
        throw new IOException("Unexpected HTTP status " + status);
      }
      try (InputStream in = connection.getInputStream()) {
        FileUtils.copyInputStreamToFile(in, tempFile);
      }
    } finally {
      connection.disconnect();
    }
    log.info("Downloaded file of size {} bytes", tempFile.length());
  }

  String generateHtml(Protocol toImport) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("protocol", toImport);
    velocityModel.put("msg", new LocaleBoundMessages(messages, userLocaleService.getLocale()));

    return VelocityEngineUtils.mergeTemplateIntoString(
        velocity, "protocols_io.vm", "UTF-8", velocityModel);
  }

  private RSForm createProtocolsIOForm(User subject) {
    RSForm pioForm = recordFactory.createBasicDocumentForm(subject);
    pioForm.setName("ProtocolsIO");
    pioForm.getAccessControl().setWorldPermissionType(PermissionType.READ);
    pioForm = formMgr.save(pioForm, subject); // need to get ID here to save into icon entity
    try {
      createAndSaveIconEntity("protocols_io.png", pioForm);
    } catch (IOException e) {
      log.error("Could not load Protocols.io form icon, will use default icon.");
      return pioForm;
    }
    pioForm = formMgr.save(pioForm, subject);
    return pioForm;
  }

  private IconEntity createAndSaveIconEntity(String fileName, RSForm form) throws IOException {
    Resource resource = context.getResource("classpath:formIcons/" + fileName);
    try (InputStream is = resource.getInputStream()) {
      byte[] bytes = IOUtils.toByteArray(is);

      IconEntity ice = new IconEntity();
      ice.setImgType("png");
      ice.setIconImage(bytes);
      String imageName = fileName;
      ice.setImgName(imageName);
      ice.setParentId(form.getId());
      IconEntity iet = imgMgr.saveIconEntity(ice, true);
      form.setIconId(iet.getId());
      return ice;
    }
  }
}
