package com.researchspace.conversion;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping
final class ConversionController {

  private static final Logger LOG = LoggerFactory.getLogger(ConversionController.class);

  private static final Set<String> PDF_INPUTS =
      Set.of(
          "csv", "doc", "docx", "md", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf",
          "txt");
  private static final Set<String> WORD_INPUTS = Set.of("doc", "docx", "odt", "ott", "rtf", "txt");
  private static final Set<String> PACKAGED_WORD_INPUTS = Set.of("docx", "odt", "ott");
  private static final Set<String> PACKAGED_PDF_INPUTS =
      Set.of("docx", "odt", "xlsx", "pptx", "ods", "odp");

  private final ConverterProperties properties;
  private final ArchiveValidator archiveValidator;
  private final OfficeConversionRunner officeRunner;
  private final GotenbergProxy gotenbergProxy;
  private final OfficeConversionLimiter limiter;

  ConversionController(
      ConverterProperties properties,
      ArchiveValidator archiveValidator,
      OfficeConversionRunner officeRunner,
      GotenbergProxy gotenbergProxy,
      OfficeConversionLimiter limiter) {
    this.properties = properties;
    this.archiveValidator = archiveValidator;
    this.officeRunner = officeRunner;
    this.gotenbergProxy = gotenbergProxy;
    this.limiter = limiter;
  }

  @GetMapping("/v1/capabilities")
  Map<String, Object> capabilities() {
    return Map.of(
        "protocol", "rspace-conversion-sidecar", "version", 1, "roles", List.of("pdf", "word"));
  }

  @PostMapping(path = "/v1/convert/html", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<StreamingResponseBody> toHtml(MultipartHttpServletRequest request) {
    MultipartFile file = requireOnlyFile(request, "file");
    String extension = extension(file);
    if (!WORD_INPUTS.contains(extension)) {
      unsupported();
    }
    return convert(file, extension, "html", false, PACKAGED_WORD_INPUTS.contains(extension));
  }

  @PostMapping(path = "/v1/convert/docx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<StreamingResponseBody> toDocx(MultipartHttpServletRequest request) {
    MultipartFile file = requireOnlyFile(request, "file");
    String extension = extension(file);
    if (!Set.of("html", "htm").contains(extension)) {
      unsupported();
    }
    return convert(file, extension, "docx", false, false);
  }

  @PostMapping(path = "/forms/libreoffice/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<StreamingResponseBody> toPdf(MultipartHttpServletRequest request) {
    rejectGotenbergControls(request);
    MultipartFile file = requireOnlyFile(request, "files");
    String extension = extension(file);
    if (!PDF_INPUTS.contains(extension)) {
      unsupported();
    }
    return convert(file, extension, "pdf", true, PACKAGED_PDF_INPUTS.contains(extension));
  }

  private ResponseEntity<StreamingResponseBody> convert(
      MultipartFile file, String extension, String target, boolean pdf, boolean packaged) {
    OfficeConversionLimiter.Permit permit = pdf ? limiter.acquirePdf() : limiter.acquireWord();
    ConversionDeadline deadline = new ConversionDeadline(properties.conversionTimeout());
    try {
      ConvertedFile result =
          withUpload(
                  file,
                  upload -> {
                    if (packaged) {
                      archiveValidator.validate(upload, extension);
                    }
                    deadline.check();
                    return pdf
                        ? gotenbergProxy.convert(
                            upload, extension, UUID.randomUUID().toString(), deadline)
                        : officeRunner.convert(upload, extension, target, deadline);
                  })
              .withDeadline(deadline)
              .withCloseAction(
                  () -> {
                    deadline.close();
                    deadline.releaseAfterCleanup(permit::close);
                  });
      try {
        deadline.handoff(() -> closeExpired(result));
        return stream(result, "output." + target);
      } catch (RuntimeException e) {
        closeExpired(result);
        throw e;
      }
    } catch (RuntimeException e) {
      deadline.close();
      deadline.releaseAfterCleanup(permit::close);
      LOG.warn("Conversion failed after capacity was acquired", e);
      throw e;
    }
  }

  private void closeExpired(ConvertedFile result) {
    try {
      result.close();
    } catch (IOException e) {
      LOG.warn("Could not remove an expired conversion result", e);
    }
  }

  private MultipartFile requireOnlyFile(MultipartHttpServletRequest request, String name) {
    if (request.getQueryString() != null || !request.getParameterMap().isEmpty()) {
      badRequest("Conversion routes do not accept parameters");
    }
    var files = request.getMultiFileMap();
    if (files.size() != 1 || files.get(name) == null || files.get(name).size() != 1) {
      badRequest("Exactly one file part is required");
    }
    MultipartFile file = files.getFirst(name);
    if (file == null || file.isEmpty()) {
      badRequest("The file part is empty");
    }
    return file;
  }

  private void rejectGotenbergControls(HttpServletRequest request) {
    var names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      if (names.nextElement().toLowerCase(Locale.ROOT).startsWith("gotenberg-")) {
        badRequest("Caller-supplied Gotenberg headers are not allowed");
      }
    }
  }

  private String extension(MultipartFile file) {
    String name = file.getOriginalFilename();
    int dot = name == null ? -1 : name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private Path transfer(MultipartFile file) {
    Path upload = null;
    try {
      upload = Files.createTempFile("conversion-upload-", ".part");
      file.transferTo(upload);
      return upload;
    } catch (IOException | RuntimeException e) {
      if (upload != null) {
        try {
          Files.deleteIfExists(upload);
        } catch (IOException cleanupFailure) {
          LOG.warn("Could not remove a partial conversion upload", cleanupFailure);
        }
      }
      LOG.error("Could not store the conversion upload", e);
      throw new ConversionException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ConversionError.FAILED,
          "The upload could not be stored");
    }
  }

  private ConvertedFile withUpload(MultipartFile file, Function<Path, ConvertedFile> conversion) {
    Path upload = transfer(file);
    try {
      return conversion.apply(upload);
    } catch (RuntimeException e) {
      try {
        Files.deleteIfExists(upload);
      } catch (IOException cleanupFailure) {
        LOG.warn("Could not remove a failed conversion upload", cleanupFailure);
      }
      throw e;
    }
  }

  private ResponseEntity<StreamingResponseBody> stream(ConvertedFile converted, String filename) {
    StreamingResponseBody body =
        output -> {
          try (converted) {
            converted.transferTo(output);
          }
        };
    long contentLength;
    try {
      contentLength = Files.size(converted.file());
    } catch (IOException e) {
      LOG.warn("Could not read the converted response size", e);
      throw new ConversionException(
          HttpStatus.BAD_GATEWAY,
          ConversionError.OUTPUT_INVALID,
          "The converted file is missing",
          e);
    }
    return ResponseEntity.ok()
        .contentLength(contentLength)
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType(converted.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(filename).build().toString())
        .body(body);
  }

  private void unsupported() {
    throw new ConversionException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        ConversionError.UNSUPPORTED,
        "The uploaded file format is not supported");
  }

  private void badRequest(String message) {
    throw new ConversionException(HttpStatus.BAD_REQUEST, ConversionError.INPUT_INVALID, message);
  }
}
