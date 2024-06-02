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
import com.researchspace.service.RecordManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.app.VelocityEngine;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;

@Component
@Slf4j
public class ProtocolsIOToDocumentConverterImpl extends AbstractExternalDocImporter
    implements ProtocolsIOToDocumentConverter {

  private static final int DOWNLOAD_TIMEOUT_MILLIS = 10_000;

  private @Autowired VelocityEngine velocity;
  private @Autowired RecordManager recordMgr;
  private @Autowired ApplicationEventPublisher publisher;
  private @Autowired FormManager formMgr;
  private @Autowired RecordFactory recordFactory;
  private @Autowired ApplicationContext context;
  private @Autowired IconImageManager imgMgr;

  public void setVelocity(VelocityEngine velocity) {
    this.velocity = velocity;
  }

  public void setRecordFactory(RecordFactory recordFactory) {
    this.recordFactory = recordFactory;
  }

  @Override
  public StructuredDocument generateFromProtocol(Protocol toImport, User subject) {
    String html = generateHtml(toImport);

    return saveDocument(toImport, html, subject);
  }

  private StructuredDocument saveDocument(Protocol toImport, String html, User subject) {
    StructuredDocument newDocument = createAPioDocument(toImport, subject);
    newDocument = processHtml(html, newDocument, subject);

    publisher.publishEvent(new GenericEvent(subject, newDocument, AuditAction.CREATE));
    return newDocument;
  }

  private StructuredDocument createAPioDocument(Protocol toImport, User subject) {
    RSForm form =
        formMgr.getCurrentSystemForm("ProtocolsIO").orElseGet(() -> createProtocolsIOForm(subject));

    Folder importFolder = folderMgr.getImportsFolder(subject);
    return recordMgr.createNewStructuredDocument(
        importFolder.getId(),
        form.getId(),
        toImport.getTitle(),
        subject,
        new DefaultRecordContext());
  }

  StructuredDocument processHtml(String html, StructuredDocument rspaceDocument, User subject) {
    org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);
    Elements imgElements = jsoupDocument.select("img");
    downloadImagesToTempFiles(imgElements);

    Folder imageTargetFolder = createTargetImageFolderIfImages(rspaceDocument.getName(), subject);
    // this has to get called even if no images
    return updateFieldContent(
        subject,
        jsoupDocument,
        imgElements,
        imageTargetFolder,
        rspaceDocument,
        el -> el.attr("src"));
  }

  // null is valid return option
  private Folder createTargetImageFolderIfImages(String folderName, User subject) {
    return folderMgr.createGallerySubfolder(folderName, IMAGES_MEDIA_FLDER_NAME, subject);
  }

  protected Elements getImages(String html) {
    org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);
    return jsoupDocument.select("img");
  }

  protected Map<String, File> downloadImagesToTempFiles(Elements imgElements) {

    Map<String, File> srcRefToFileMap = new LinkedHashMap<>();
    log.info("There are {} images referenced in Protocol", imgElements.size());
    removeDuplicateElements(imgElements);
    for (Element el : imgElements) {
      String srcString = el.attr("src");

      URL url = null;
      try {
        url = new URL(srcString);
      } catch (MalformedURLException e) {
        log.warn("Couldn't create valid URL from src attribute '{}'", srcString);
        continue;
      }

      try {
        File tempFile = getTempFileFromURL(url);

        if (!srcRefToFileMap.containsKey(srcString)) {
          downloadFile(url, tempFile);
          srcRefToFileMap.put(srcString, tempFile);
          el.attr("src", tempFile.getAbsolutePath());
        } else {
          el.attr("src", srcRefToFileMap.get(srcString).getAbsolutePath());
          log.info(" already downloaded {}, skipping", srcString);
        }
      } catch (IOException e) {
        log.warn("Couldn't retrieve file from URL {} - {}", url, e.getMessage());
      }
    }
    return srcRefToFileMap;
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
    String fNameString = FilenameUtils.getBaseName(url.getFile());
    File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
    return File.createTempFile(
        fNameString, "." + FilenameUtils.getExtension(url.getFile()), secureTmpDir);
  }

  void downloadFile(URL url, File tempFile) throws IOException {
    log.info("Downloading from {}", url);
    FileUtils.copyURLToFile(url, tempFile, DOWNLOAD_TIMEOUT_MILLIS, DOWNLOAD_TIMEOUT_MILLIS);
    log.info("Downloaded file of size {} bytes", tempFile.length());
  }

  String generateHtml(Protocol toImport) {
    Map<String, Object> velocityModel = new HashMap<>();
    velocityModel.put("protocol", toImport);

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
