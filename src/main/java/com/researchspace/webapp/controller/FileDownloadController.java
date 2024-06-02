package com.researchspace.webapp.controller;

import com.researchspace.core.util.IoUtils;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RecordSigningManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** Downloads a files contents via HTTP get request. Sets correct content type. */
@Controller
@RequestMapping({"/Streamfile", "/public/publicView/Streamfile"})
public class FileDownloadController extends BaseController {

  public static final String STREAM_URL = "/Streamfile";

  private static final int BUFFER_SIZE = 8192;

  @Autowired
  @Qualifier("compositeDocumentConverter")
  private DocumentConversionService converter;

  private @Autowired RecordSigningManager recordSharingMgr;

  private @Autowired RSChemElementManager rsChemElementManager;

  /*
   * {nameFile:.+} means the extension (part after dot) is not truncated. but this
   * whole idea of taking filename from client is a bit strange, why not just use
   * ecatFile.getName()?
   */
  /**
   * @deprecated use alternate method without name argument. This method is kept solely for
   *     backwards compatibility with existing links in text fields.
   */
  @Deprecated()
  @GetMapping("/{record_id}/{nameFile:.+}")
  public void getStreamFile(
      @PathVariable("record_id") Long recordId,
      @PathVariable("nameFile") String name,
      @RequestParam(value = "revision", required = false) Long revisionId,
      HttpServletResponse response)
      throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    doStreamFile(recordId, revisionId, null, response, subject);
  }

  @GetMapping("/{record_id}")
  @BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.DAY)
  public void getStreamFileNoName(
      @PathVariable("record_id") Long recordId,
      @RequestParam(value = "revision", required = false) Long revisionId,
      @RequestParam(value = "version", required = false) Long version,
      HttpServletResponse response)
      throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    doStreamFile(recordId, revisionId, version, response, subject);
  }

  private void doStreamFile(
      Long recordId, Long revisionId, Long version, HttpServletResponse response, User subject)
      throws IOException {
    EcatMediaFile ecatMediaFile =
        baseRecordManager.retrieveMediaFile(subject, recordId, revisionId, version, null);
    FileProperty fp = ecatMediaFile.getFileProperty();
    auditService.notify(new GenericEvent(subject, ecatMediaFile, AuditAction.DOWNLOAD));
    doWriteToResponse(response, ecatMediaFile.getFileName(), ecatMediaFile.getContentType(), fp);
  }

  @GetMapping("/chemImage/{chemId}")
  public void getStreamChemImage(
      @PathVariable("chemId") Long chemElemId,
      @RequestParam(value = "revision", required = false) Long revisionId,
      HttpServletResponse response)
      throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    RSChemElement chem = rsChemElementManager.getRevision(chemElemId, revisionId, subject);
    if (chem == null) {
      log.error("Could not retrieve chemImage for {}, {}", chemElemId, revisionId);
      return;
    }

    writeChemImageToResponse(chem, response);
  }

  @GetMapping("/chemFileImage/{ecatChemFileId}")
  public void getStreamChemFileImage(
      @PathVariable("ecatChemFileId") Long ecatChemFileId, HttpServletResponse response)
      throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    List<RSChemElement> chemElements =
        rsChemElementManager.getRSChemElementsLinkedToFile(ecatChemFileId, subject);
    if (chemElements.isEmpty()) {
      log.error("Could not retrieve Image for chemical file: {}", ecatChemFileId);
      return;
    }
    writeChemImageToResponse(chemElements.get(0), response);
  }

  private void writeChemImageToResponse(RSChemElement chemElement, HttpServletResponse response)
      throws IOException {
    FileProperty fp = chemElement.getImageFileProperty();
    if (fp != null) {
      doWriteToResponse(response, fp.getFileName(), MediaType.IMAGE_PNG_VALUE, fp);
    } else {
      String filename = "chem" + chemElement.getId() + ".png";
      ByteArrayInputStream bais = new ByteArrayInputStream(chemElement.getDataImage());
      doWriteInputStreamToResponse(response, filename, MediaType.IMAGE_PNG_VALUE, bais);
    }
  }

  private void doWriteToResponse(
      HttpServletResponse response, String fileName, String contentType, FileProperty fp)
      throws IOException {
    Optional<FileInputStream> fis = fileStore.retrieve(fp);
    if (fis.isPresent()) {
      doWriteInputStreamToResponse(response, fileName, contentType, fis.get());
    } else {
      log.error("Could not retrieve file for FileProperty {}", fp.getId());
    }
  }

  private void doWriteInputStreamToResponse(
      HttpServletResponse response, String fileName, String contentType, InputStream fis)
      throws IOException {
    try (InputStream is = fis;
        ServletOutputStream out = response.getOutputStream(); ) {
      setContentInfo(fileName, contentType, response);
      byte[] outputByte = new byte[BUFFER_SIZE];
      int count = 0;
      while ((count = is.read(outputByte, 0, BUFFER_SIZE)) != -1) {
        out.write(outputByte, 0, count);
      }
    }
  }

  /** Uses underlying filestore to retrieve a file that can be converted */
  @Slf4j
  @EqualsAndHashCode(of = "toConvert")
  static class FileWrapper implements Convertible {

    private EcatMediaFile toConvert;
    private File file = null;
    private FileStore filestore;

    public FileWrapper(FileStore filestore, EcatMediaFile toConvert) {
      super();
      this.toConvert = toConvert;
      this.filestore = filestore;
    }

    @Override
    public String getName() {
      if (file == null) {
        getLocalFile();
      }
      return file != null ? file.getName() : "";
    }

    private void getLocalFile() {
      try {
        this.file = filestore.findFile(toConvert.getFileProperty());
      } catch (IOException e) {
        log.error("Could not access media file contents for media file {}", toConvert.getId(), e);
      }
    }

    @Override
    public String getFileUri() {
      if (file == null) {
        getLocalFile();
      }
      return file != null ? file.toURI().toString() : "";
    }
  }

  /**
   * Converts a document (e.g .text. .doc) to a viewable format in HTML page
   *
   * @param docId url path variable - id of ecat media file
   * @param outputformat One of 'png', 'pdf' or 'html'
   * @param revisionId optional file revision number
   * @param response
   * @throws IOException
   * @throws URISyntaxException
   */
  @GetMapping("/ajax/convert/{record_id}")
  public @ResponseBody AjaxReturnObject<String> convertFile(
      @PathVariable("record_id") Long docId,
      @RequestParam(value = "outputFormat") String outputformat,
      @RequestParam(value = "revision", required = false) Long revisionId,
      HttpServletResponse response)
      throws IOException {

    User subject = userManager.getAuthenticatedUserInSession();
    EcatMediaFile input =
        baseRecordManager.retrieveMediaFile(subject, docId, revisionId, null, null);

    String fName = getFileStoreNameByChecksum(input.getFileProperty(), outputformat);
    FileProperty soughtFileProp = generateFileProperty(outputformat, input, fName, subject);
    // see if converted file exists already.
    if (properties.isAsposeCachingEnabled() && fileStore.exists(soughtFileProp)) {
      return new AjaxReturnObject<>(fileStore.findFile(soughtFileProp).getName(), null);
    }
    File tempFileForConvertedOutput = createOutfile(outputformat, fName);
    ConversionResult result =
        converter.convert(
            new FileWrapper(fileStore, input), outputformat, tempFileForConvertedOutput);
    if (!result.isSuccessful()) {
      return new AjaxReturnObject<>(
          null, ErrorList.createErrListWithSingleMsg(result.getErrorMsg()));
    }
    if (result
        .getConverted()
        .toURI()
        .toString()
        .equals(input.getFileProperty().getAbsolutePathUri())) {
      return new AjaxReturnObject<>(result.getConverted().getName(), null);
    }
    try {
      FileProperty convertedFileProp =
          saveConvertedInFileStore(outputformat, input, result, subject);
      return new AjaxReturnObject<>(convertedFileProp.getFileName(), null);
    } catch (Exception e) {
      return new AjaxReturnObject<>(null, ErrorList.createErrListWithSingleMsg(e.getMessage()));
    }
  }

  protected File createOutfile(String outputformat, String fName) throws IOException {
    fName = FilenameUtils.removeExtension(fName);
    File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
    return File.createTempFile(fName, "." + outputformat, secureTmpDir);
  }

  /**
   * Streams direct from filestore given a unique filename that has been previously returned from
   * the 'Convert' function
   *
   * @param docId
   * @param fileName
   * @param response
   * @param subject
   * @throws IOException
   */
  @GetMapping("/direct/{record_id}")
  public void streamDirect(
      @PathVariable("record_id") Long docId,
      @RequestParam(value = "fileName") String fileName,
      HttpServletResponse response)
      throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    EcatMediaFile input = baseRecordManager.retrieveMediaFile(subject, docId, null, null, null);
    FileProperty fp =
        generateFileProperty(FilenameUtils.getExtension(fileName), input, fileName, subject);
    doWriteToResponseFromFileProperty(response, fp);
  }

  private void doWriteToResponseFromFileProperty(HttpServletResponse response, FileProperty fp)
      throws IOException {
    doWriteToResponse(
        response,
        fp.getFileName(),
        MediaUtils.getContentTypeForFileExtension(fp.getFileCategory()),
        fp);
  }

  /**
   * Streams exports of signed records
   *
   * @param docId The id of the record to which this file property belongs
   * @param filePropertyId the ID of the {@link FileProperty} to download
   * @param response
   * @throws IOException
   * @throws {@link AuthorizationException} if subject not authorised to view the signed record
   * @throws {@link IllegalStateException} if FileProperty doesn't exist
   */
  @GetMapping(value = "/filestore/{signatureId}/{filePropertyId}")
  public void streamFilePropertyDirect(
      @PathVariable("signatureId") Long signatureId,
      @PathVariable("filePropertyId") Long filePropertyId,
      HttpServletResponse response)
      throws IOException {
    User user = userManager.getAuthenticatedUserInSession();
    Optional<FileProperty> optFp =
        recordSharingMgr.getSignedExport(signatureId, user, filePropertyId);
    if (optFp.isPresent()) {
      FileProperty fp = optFp.get();
      doWriteToResponseFromFileProperty(response, fp);
    } else {
      throw new IllegalStateException(getResourceNotFoundMessage("File", filePropertyId));
    }
  }

  /**
   * SAves the converted file using the checksum of the file contents as the file name
   *
   * @param outputformat
   * @param mediaFile
   * @param result
   * @param user
   * @return
   * @throws Exception
   */
  protected FileProperty saveConvertedInFileStore(
      String outputformat, EcatMediaFile mediaFile, ConversionResult result, User user)
      throws IOException {
    FileProperty fp =
        generateFileProperty(
            outputformat,
            mediaFile,
            getFileStoreNameByChecksum(mediaFile.getFileProperty(), outputformat),
            user);
    fileStore.save(fp, result.getConverted(), FileDuplicateStrategy.REPLACE);
    return fp;
  }

  // get filename from an MD5 checksum of contents. We use this to cache converted
  // files and
  // determine if the file has changed ( and hence needs to be converted again)
  private String getFileStoreNameByChecksum(FileProperty fp, String outputSuffix)
      throws IOException {
    String md5;
    Optional<FileInputStream> fisOpt = fileStore.retrieve(fp);
    if (fisOpt.isPresent()) {
      try (FileInputStream fis = fisOpt.get()) {
        md5 = DigestUtils.md5Hex(fis);
        return md5 + "." + outputSuffix;
      }
    } else {
      log.error("Could not retrieve file {} to calculate checksum", fp.getRelPath());
      throw new IllegalStateException(getResourceNotFoundMessage("File", fp.getId()));
    }
  }

  private FileProperty generateFileProperty(
      String outputformat, EcatMediaFile input, String fName, User user) {
    FileProperty fp = new FileProperty();
    fp.setFileCategory("convertedDocs-" + outputformat);
    fp.setFileUser(user.getUsername());
    fp.setFileOwner(input.getOwner().getUsername());
    fp.setFileGroup("research");
    fp.setFileVersion("v1");
    fp.setFileName(fName);
    fp.setRoot(fileStore.getCurrentFileStoreRoot());
    return fp;
  }

  /**
   * Sets the relevant content information in response headers
   *
   * @return
   */
  private void setContentInfo(String name, String contentType, HttpServletResponse response) {
    response.setContentType(contentType);
    response.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
  }
}
