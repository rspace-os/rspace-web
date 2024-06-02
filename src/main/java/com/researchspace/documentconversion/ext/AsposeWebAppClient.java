package com.researchspace.documentconversion.ext;

import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.rest.utils.SimpleResilienceFacade;
import com.researchspace.core.util.IoUtils;
import com.researchspace.core.util.ZipUtils;
import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.core.util.version.Versionable;
import com.researchspace.documentconversion.spi.AbstractDocumentConversionService;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.ConvertibleFile;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import io.vavr.control.Either;
import io.vavr.control.Try;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class AsposeWebAppClient extends AbstractDocumentConversionService
    implements DocumentConversionService, Versionable {

  private static final String UNDEFINED_CUSTOMER = "undefined-customer";

  private URI serviceURI;
  ConversionChecker conversionChecker;
  private RestTemplate template;

  private Supplier<String> customerIDSupplier;
  private String customerID = UNDEFINED_CUSTOMER;
  private SimpleResilienceFacade apiClientResilientFacade;

  public AsposeWebAppClient(
      URI uri, ConversionChecker conversionChecker, Supplier<String> customerIDSupplier) {
    this(
        uri,
        conversionChecker,
        customerIDSupplier,
        new RestTemplate(),
        new SimpleResilienceFacade(1000, 50));
  }

  AsposeWebAppClient(
      URI uri,
      ConversionChecker conversionChecker,
      Supplier<String> customerIDSupplier,
      RestTemplate template,
      SimpleResilienceFacade simpleResilienceFacade) {
    this.serviceURI = uri;
    this.conversionChecker = conversionChecker;
    this.customerIDSupplier = customerIDSupplier;
    this.template = template;
    this.apiClientResilientFacade = simpleResilienceFacade;
  }

  @PostConstruct
  void init() {
    if (StringUtils.isBlank(customerID) || customerID.equals(UNDEFINED_CUSTOMER)) {
      this.customerID = customerIDSupplier.get();
    }
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension) {
    try {
      Path secureTempDir = IoUtils.createOrGetSecureTempDirectory();
      File outFile =
          File.createTempFile(
              FilenameUtils.getBaseName(toConvert.getName()),
              "." + outputExtension,
              secureTempDir.toFile());
      return convert(toConvert, outputExtension, outFile);
    } catch (IOException ie) {
      return new ConversionResult("Couldn't create outfile for " + toConvert.getFileUri());
    }
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension, File outFile) {
    init();
    URI url = buildUrl(outputExtension);
    if (isHtmlToWord(toConvert, outputExtension)) {
      try {
        Path secureTempDir = IoUtils.createOrGetSecureTempDirectory();
        File zipOfHtmlAndImages =
            File.createTempFile(
                FilenameUtils.getBaseName(toConvert.getName()), ".zip", secureTempDir.toFile());
        File htmlFileToConvert = new File(getFilePathFromURI(toConvert));
        // zip the folder containing RSpace HTML + any images
        ZipUtils.createZip(zipOfHtmlAndImages, htmlFileToConvert.getParentFile());
        // we will send the zip file to Aspose-web
        toConvert = new ConvertibleFile(zipOfHtmlAndImages);
      } catch (IOException ie) {
        log.warn(
            "Could not generate zip of HTML content for HTML->word export for file {}",
            toConvert.getName());
        return new ConversionResult(ie.getMessage());
      }
    }
    LinkedMultiValueMap<String, Object> map =
        createFileMap(new File(getFilePathFromURI(toConvert)));
    HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity =
        createFilePostRequestEntity(map);

    Either<ApiError, byte[]> resp =
        apiClientResilientFacade.makeApiCall(
            () -> this.template.exchange(url, HttpMethod.POST, requestEntity, byte[].class));

    return resp.map(
            bytes ->
                Try.ofCallable(() -> saveResponseToFile(outFile, bytes, outputExtension))
                    .getOrElseGet(ie -> new ConversionResult(ie.getMessage())))
        .getOrElseGet(apiError -> new ConversionResult(apiError.getMessage()));
  }

  private boolean isHtmlToWord(Convertible toConvert, String outputExtension) {
    return FilenameUtils.getExtension(toConvert.getName()).equalsIgnoreCase("html")
        && outputExtension.toLowerCase().contains("doc");
  }

  private ConversionResult saveResponseToFile(File outFile, byte[] bytes, String outputExt)
      throws IOException {
    if (outputExt.equalsIgnoreCase("html")) {
      // we are getting HTML + images as a zip file.

      ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes));
      unzipWordToHtmlResponse(zis, outFile);

    } else {
      try (FileOutputStream fos = new FileOutputStream(outFile)) {
        IOUtils.write(bytes, fos);
      }
    }
    return new ConversionResult(outFile, getContentTypeForExtension(outputExt));
  }

  // unzips entries into a flat folder structure.
  void unzipWordToHtmlResponse(ZipInputStream zis, File outFile) throws IOException {

    File destDir = outFile.getParentFile();
    ZipEntry zipEntry = null;
    while ((zipEntry = zis.getNextEntry()) != null) {
      // don't write directories. Write all zip files in flat list.
      if (zipEntry.isDirectory()) {
        zis.closeEntry();
        continue;
      }

      File newFile = newFile(destDir, zipEntry);
      // this html file is the main content and is set to be the defined outfile
      if (zipEntry.getName().endsWith(".html")) {
        newFile = outFile;
      }
      try (FileOutputStream fos = new FileOutputStream(newFile)) {
        IOUtils.copy(zis, fos, 4096);
      }
      zis.closeEntry();
    }
    zis.close();
  }

  File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
    String zipname = FilenameUtils.getName(zipEntry.getName());
    File destFile = new File(destinationDir, zipname);

    String destDirPath = destinationDir.getCanonicalPath();
    String destFilePath = destFile.getCanonicalPath();
    // zip slip protection in case zip entry has path outside zip file
    if (!destFilePath.startsWith(destDirPath + File.separator)) {
      throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
    }

    return destFile;
  }

  @Override
  public boolean supportsConversion(Convertible toBeConverted, String toFormat) {
    String fromFormat = FilenameUtils.getExtension(toBeConverted.getName());
    return conversionChecker.supportsConversion(fromFormat, toFormat);
  }

  @Override
  public SemanticVersion getVersion() {
    URI infoUri = infoURI();

    return Try.ofCallable(() -> this.template.getForEntity(infoUri, Map.class))
        .toEither()
        .map(ResponseEntity::getBody)
        .map(m -> ((Map<String, Object>) m.get("app")).get("version"))
        .map(s -> s != null ? new SemanticVersion(s.toString()) : SemanticVersion.UNKNOWN_VERSION)
        .getOrElse(SemanticVersion.UNKNOWN_VERSION);
  }

  @Override
  public String getVersionMessage() {
    return getVersion().toString();
  }

  @Override
  public String getDescription() {
    return "Aspose Document converter web service";
  }

  private URI infoURI() {
    return UriComponentsBuilder.fromUri(serviceURI).path("/actuator/info").build().toUri();
  }

  private URI buildUrl(String format) {
    return UriComponentsBuilder.fromUri(serviceURI)
        .path("/aspose/files")
        .queryParam("format", format)
        .queryParam("customerId", customerID)
        .build()
        .encode()
        .toUri();
  }

  private HttpEntity<LinkedMultiValueMap<String, Object>> createFilePostRequestEntity(
      LinkedMultiValueMap<String, Object> map) {
    return new HttpEntity<>(map);
  }

  private LinkedMultiValueMap<String, Object> createFileMap(File file) {
    LinkedMultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
    map.add("file", new FileSystemResource(file.getAbsolutePath()));
    return map;
  }
}
