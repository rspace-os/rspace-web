package com.researchspace.webapp.integrations.snapgene;

import static com.researchspace.model.preference.HierarchicalPermission.ALLOWED;
import static org.apache.commons.lang3.StringUtils.join;

import com.researchspace.apiutils.ApiError;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Record;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.snapgene.wclient.SnapgeneWSClient;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.zmq.snapgene.requests.EnzymeSet;
import com.researchspace.zmq.snapgene.requests.ExportDnaFileConfig;
import com.researchspace.zmq.snapgene.requests.ExportFilter;
import com.researchspace.zmq.snapgene.requests.GeneratePngMapConfig;
import com.researchspace.zmq.snapgene.requests.ReadingFrame;
import com.researchspace.zmq.snapgene.requests.ReportEnzymesConfig;
import com.researchspace.zmq.snapgene.requests.ReportORFsConfig;
import com.researchspace.zmq.snapgene.responses.SnapgeneResponse;
import io.vavr.control.Either;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for DNA file visualizations and annotations. Current implementation uses snapgene but
 * URL paths and method names are implementation-agnostic in case we change provider in future.
 */
@Controller
@RequestMapping("/molbiol/dna")
public class DNAViewerController extends BaseController {

  @Autowired SnapgeneWSClient snapgeneClient;
  @Autowired SystemPropertyManager systemPropertyManagerImpl;

  /**
   * Max file size accepted by Snapgene server ( at least version 0.0.4). improvement todo - set
   * this dynamically by querying the snapgene server.
   */
  public static final int MAX_SNAPGENE_FILE_SIZE = 20_000_000;

  @GetMapping("/serviceStatus")
  public ResponseEntity<String> status() {
    if (ALLOWED
        .name()
        .equals(
            systemPropertyManagerImpl
                .findByName(SystemPropertyName.SNAPGENE_AVAILABLE)
                .getValue())) {
      Either<ApiError, String> result = snapgeneClient.status();
      ResponseEntity<String> statusResponse =
          result
              .map(json -> ResponseEntity.ok().body(json))
              .getOrElse(() -> errorMessageResponse(result.getLeft()));
      return statusResponse;
    }
    return new ResponseEntity<>("Snapgene not allowed", HttpStatus.OK);
  }

  /**
   * @param id ID of an EcatDocumentFile of a DNA file
   * @param pngConfig
   * @return
   * @throws IOException
   */
  @GetMapping("/png/{id}")
  public ResponseEntity<byte[]> getPngView(
      @PathVariable("id") Long id, GeneratePngMapConfig pngConfig) throws IOException {
    File inputFile = getFileFromFileStore(id);
    Either<ApiError, byte[]> conversion = snapgeneClient.uploadAndDownloadPng(inputFile, pngConfig);
    if (conversion.isLeft()) {
      ApiError apiError = conversion.getLeft();
      String messageString =
          String.format("Snapgene conversion to PNG failed - %s", apiError.getMessage());
      log.error(messageString);
      String errorMsg = generateErrorMessageString(apiError);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.TEXT_PLAIN);
      return new ResponseEntity<byte[]>(
          errorMsg.getBytes(Charset.forName("UTF-8")), HttpStatus.valueOf(apiError.getHttpCode()));
    } else {
      ResponseEntity<byte[]> bytesResponse =
          conversion
              .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes))
              .get();
      return bytesResponse;
    }
  }

  /**
   * @param id ID of an EcatDocumentFile of a DNA file
   * @param enzymeSet
   * @return
   * @throws IOException
   */
  @GetMapping("/enzymes/{id}")
  public ResponseEntity<String> getEnzymes(
      @PathVariable("id") Long id,
      @RequestParam(required = false, defaultValue = "UNIQUE_SIX_PLUS") EnzymeSet enzymeSet)
      throws IOException {
    File inputFile = getFileFromFileStore(id);
    Either<ApiError, String> conversion =
        snapgeneClient.enzymes(inputFile, new ReportEnzymesConfig(enzymeSet));
    if (conversion.isLeft()) {
      log.error("Conversion failed - {}", conversion.getLeft().getMessage());
    }
    ResponseEntity<String> bytesResponse = generateResponse(conversion);

    return bytesResponse;
  }

  /**
   * @param id ID of an EcatDocumentFile of a DNA file
   * @param readingFrame
   * @return
   * @throws IOException
   */
  @GetMapping("/orfs/{id}")
  public ResponseEntity<String> getORFs(
      @PathVariable("id") Long id,
      @RequestParam(required = false, defaultValue = "ORFS_ONLY") ReadingFrame readingFrame)
      throws IOException {
    File inputFile = getFileFromFileStore(id);
    Either<ApiError, String> conversion =
        snapgeneClient.orfs(inputFile, new ReportORFsConfig(readingFrame));
    if (conversion.isLeft()) {
      log.error("Conversion failed - {}", conversion.getLeft().getMessage());
    }
    ResponseEntity<String> jsonResponse = generateResponse(conversion);

    return jsonResponse;
  }

  /**
   * Gets FASTA as string
   *
   * @param id
   * @return
   * @throws IOException
   */
  @GetMapping("/fasta/{id}")
  public ResponseEntity<String> getFasta(@PathVariable("id") Long id) throws IOException {
    File inputFile = getFileFromFileStore(id);
    // convert to fasta on backend
    Either<ApiError, SnapgeneResponse> resp =
        snapgeneClient.exportDnaFile(inputFile, new ExportDnaFileConfig(ExportFilter.FASTA));
    if (resp.isRight()) {
      // now retrieve file as string or else error
      return snapgeneClient
          .downloadFile(resp.get().getOutputFileName())
          .map(bytes -> new String(bytes, Charset.forName("UTF-8")))
          .map(str -> ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(str))
          .getOrElseGet(error -> errorMessageResponse(error));

    } else {
      return errorMessageResponse(resp.getLeft());
    }
  }

  private void assertIsEcatDocument(Record file) {
    Validate.isTrue(
        file.isEcatDocument(), "ID must be that of an attachment file of a DNA sequence");
  }

  /**
   * @param id ID of an EcatDocumentFile of a DNA file
   * @param pngConfig
   * @return
   * @throws IOException
   */
  @GetMapping("/png2/{id}")
  public void getPngView2(
      @PathVariable("id") Long id, GeneratePngMapConfig pngConfig, HttpServletResponse response)
      throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    log.info("converting file {}", id);
    Record file = recordManager.get(id);
    validateInput(subject, file);
    EcatDocumentFile docRecord = (EcatDocumentFile) file;
    File inputFile = fileStore.findFile(docRecord.getFileProperty());
    Either<ApiError, byte[]> conversion = snapgeneClient.uploadAndDownloadPng(inputFile, pngConfig);
    if (conversion.isRight()) {
      doWriteInputStreamToResponse(response, conversion.get());
    } else {
      log.error("Error getting png from DNA sequence");
    }
  }

  private ResponseEntity<String> generateResponse(Either<ApiError, String> conversion) {
    return conversion
        .map(json -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json))
        .getOrElse(() -> errorMessageResponse(conversion.getLeft()));
  }

  // throws AuthExc or IAE if too big
  private File getFileFromFileStore(Long id) throws IOException {
    User subject = userManager.getAuthenticatedUserInSession();
    log.info("getting ORFs for  file {}", id);
    Record file = recordManager.get(id);
    validateInput(subject, file);
    EcatDocumentFile docRecord = (EcatDocumentFile) file;
    // fail-fast if file will get rejected by snapgene service.
    if (docRecord.getSize() > MAX_SNAPGENE_FILE_SIZE) {
      throw new IllegalArgumentException(
          String.format(
              "The max file size supported by Snapgene server is %d bytes, but this file is %d"
                  + " bytes",
              MAX_SNAPGENE_FILE_SIZE, docRecord.getSize()));
    }
    if (!MediaUtils.isDNAFile(docRecord.getExtension())) {
      throw new IllegalArgumentException(
          String.format(
              "This file either has incorrect suffix or is not a supported DNA file - suffix must"
                  + " be one of %s",
              StringUtils.join(MediaUtils.supportedDNATypes(), ",")));
    }
    File inputFile = fileStore.findFile(docRecord.getFileProperty());
    return inputFile;
  }

  private void validateInput(User subject, Record file) {
    assertIsEcatDocument(file);
    assertAuthorisation(subject, file, PermissionType.READ);
  }

  private void doWriteInputStreamToResponse(HttpServletResponse response, byte[] toWrite)
      throws IOException {
    try (InputStream is = new ByteArrayInputStream(toWrite);
        ServletOutputStream out = response.getOutputStream(); ) {
      response.setContentType("image/png");
      byte[] outputByte = new byte[4096];
      int count = 0;
      while ((count = is.read(outputByte, 0, 4096)) != -1) {
        out.write(outputByte, 0, count);
      }
    }
  }

  private ResponseEntity<String> errorMessageResponse(ApiError error) {
    if (error == null) {
      log.warn("Snapgene webservice call failed without a specific error code");
      return new ResponseEntity<>("Snapgene webservice call failed", HttpStatus.FAILED_DEPENDENCY);
    }
    String errorMsg = generateErrorMessageString(error);
    log.error(errorMsg);
    return new ResponseEntity<>(errorMsg, HttpStatus.valueOf(error.getHttpCode()));
  }

  private String generateErrorMessageString(ApiError error) {
    return String.format(
        "Snapgene webservice call failed: %s - %s",
        error.getMessage(), join(error.getErrors(), ","));
  }

  private FileProperty generateFileProperty(
      String outputformat, EcatDocumentFile input, String fName, User user) throws IOException {
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
}
