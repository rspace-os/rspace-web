package com.researchspace.webapp.integrations.fieldmark;

import com.google.common.io.Files;
import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.webapp.integrations.fieldmark.model.FieldmarkNotebook;
import com.researchspace.webapp.integrations.fieldmark.model.FieldmarkRecordsJsonExport;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.client.RestTemplate;

@WebAppConfiguration
public class FieldmarkMVCIT extends API_MVC_TestBase {

  private static final String COMMA_DELIMITER = ",";

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  @Ignore(
      "This test was used for the Fieldmark POC. "
          + "We leave the test Ignored so we can potentially run it manually by the bearer token")
  public void testCreateDatasetAndPushFile() throws IOException, URISyntaxException {
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    /* ************
     ///// SET THE ACCESS TOKEN MANUALLY /////
    **************** */
    String ACCESS_TOKEN = "-----------PASTE_TOKEN_HERE----------";
    headers = addAuthorizationHeaders(ACCESS_TOKEN);
    headers.setContentType(MediaType.APPLICATION_JSON);

    ////////// get the notebooks //////////
    String jsonResult =
        restTemplate
            .exchange(
                new URL("https://conductor.fieldmark.app/api/notebooks/").toURI(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class)
            .getBody();
    System.out.println("Notebooks in JSON: " + jsonResult);

    List<FieldmarkNotebook> fieldmarkNotebooks =
        restTemplate
            .exchange(
                new URL("https://conductor.fieldmark.app/api/notebooks/").toURI(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                List.class)
            .getBody();
    System.out.println("Notebooks in Object: " + fieldmarkNotebooks);

    ///////// get the notebook ID ///////////
    jsonResult =
        restTemplate
            .exchange(
                new URL(
                        "https://conductor.fieldmark.app/api/notebooks/1726126204618-rspace-igsn-demo/")
                    .toURI(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class)
            .getBody();
    System.out.println("Notebook in JSON: " + jsonResult);

    FieldmarkNotebook fieldmarkNotebook =
        restTemplate
            .exchange(
                new URL(
                        "https://conductor.fieldmark.app/api/notebooks/1726126204618-rspace-igsn-demo/")
                    .toURI(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                FieldmarkNotebook.class)
            .getBody();
    System.out.println("Notebook in Object: " + fieldmarkNotebook);

    ////////// get the records of the notebook //////////
    jsonResult =
        restTemplate
            .exchange(
                new URL(
                        "https://conductor.fieldmark.app/api/notebooks/1726126204618-rspace-igsn-demo/records")
                    .toURI(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class)
            .getBody();
    System.out.println("Records in JSON: " + jsonResult);

    FieldmarkRecordsJsonExport fieldmarkRecords =
        restTemplate
            .exchange(
                new URL(
                        "https://conductor.fieldmark.app/api/notebooks/1726126204618-rspace-igsn-demo/records")
                    .toURI(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                FieldmarkRecordsJsonExport.class)
            .getBody();
    System.out.println("Records in Object: " + fieldmarkRecords);

    ////////// get the CSV  //////////
    jsonResult =
        restTemplate
            .exchange(
                new URL(
                        "https://conductor.fieldmark.app/api/notebooks/1726126204618-rspace-igsn-demo/Primary.csv")
                    .toURI(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class)
            .getBody();
    System.out.println("CSV: " + jsonResult);

    byte[] csvFileBytes =
        restTemplate
            .exchange(
                new URL(
                        "https://conductor.fieldmark.app/api/notebooks/1726126204618-rspace-igsn-demo/Primary.csv")
                    .toURI(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class)
            .getBody();
    System.out.println("CSV bytes: " + csvFileBytes);
    File csvFileFromBytes = null;

    ///// read the CSV and put into a List
    try {
      csvFileFromBytes = new File("file.csv");
      FileUtils.writeByteArrayToFile(csvFileFromBytes, csvFileBytes);

      List<String> lines = Files.readLines(csvFileFromBytes, Charset.defaultCharset());

      List<List<String>> records =
          lines.stream()
              .map(line -> Arrays.asList(line.split(COMMA_DELIMITER)))
              .collect(Collectors.toList());
      System.out.println("CSV List of List: " + records);

    } finally {
      if (csvFileFromBytes != null) {
        FileUtils.delete(csvFileFromBytes);
      }
    }

    ////////// get the ZIP file //////////
    byte[] zipFileBytes =
        restTemplate
            .exchange(
                new URL(
                        "https://conductor.fieldmark.app/api/notebooks/1726126204618-rspace-igsn-demo/Primary.zip")
                    .toURI(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class)
            .getBody();
    System.out.println("ZIP bytes: " + zipFileBytes);
    File fileFromBytes = null;

    ///// save the photos (that were entries in the zip file) to the filesystem
    try {
      fileFromBytes = new File("file.zip");
      FileUtils.writeByteArrayToFile(fileFromBytes, zipFileBytes);

      try (ZipFile zipFile = new ZipFile(fileFromBytes)) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          // Check if entry is a directory
          if (!entry.isDirectory()) {
            FileUtils.copyInputStreamToFile(
                zipFile.getInputStream(entry), new File(entry.getName()));

            System.out.println("File \"" + entry.getName() + "\" correctly saved");
          }
        }
      }
    } finally {
      if (fileFromBytes != null) {
        FileUtils.delete(fileFromBytes);
      }
    }
  }

  private HttpHeaders addAuthorizationHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    return addAuthorizationHeaders(headers, accessToken);
  }

  private HttpHeaders addAuthorizationHeaders(HttpHeaders headers, String accessToken) {
    headers.add("Authorization", String.format("Bearer %s", accessToken));
    return headers;
  }
}
