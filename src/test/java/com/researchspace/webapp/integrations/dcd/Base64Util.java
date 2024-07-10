package com.researchspace.webapp.integrations.dcd;

import java.util.UUID;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class Base64Util {

  private Base64Util() {}

  public static String generateFilename(String base64String) {
    if (StringUtils.isBlank(base64String)) {
      return base64String;
    }
    String[] arrayString = base64String.split(",");
    String header = arrayString[0].split(";")[0];
    String uuid = UUID.randomUUID().toString();
    String filename = String.format("%s.%s", uuid, header.split("/")[1]);
    return filename;
  }

  public static String stripStartBase64(String base64String) {
    if (StringUtils.isBlank(base64String)) {
      return base64String;
    }
    return base64String.replaceAll("^data:image/[^;]*;base64,?", "");
  }

  public static HttpEntity<byte[]> convertToHttpEntity(String filename, String base64) {
    byte[] imageByte = Base64.decodeBase64(base64);
    ContentDisposition contentDisposition =
        ContentDisposition.builder("form-data").name("myImage").filename(filename).build();

    MultiValueMap<String, String> fileMap = new LinkedMultiValueMap<>();
    fileMap.add(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
    return new HttpEntity<>(imageByte, fileMap);
  }
}
