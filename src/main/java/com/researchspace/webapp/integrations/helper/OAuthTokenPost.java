package com.researchspace.webapp.integrations.helper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;

/** Sends cloud OAuth token parameters in the POST body, keeping secrets out of request URLs. */
public final class OAuthTokenPost {
  private OAuthTokenPost() {}

  public static String exchange(String url, String clientId, String clientSecret)
      throws IOException {
    URL supplied = new URL(url);
    URL endpoint =
        new URL(supplied.getProtocol(), supplied.getHost(), supplied.getPort(), supplied.getPath());
    HttpURLConnection urlConn = (HttpURLConnection) endpoint.openConnection();
    urlConn.setRequestMethod("POST");
    urlConn.setInstanceFollowRedirects(false);
    urlConn.setDoOutput(true);
    String userpass = clientId + ":" + clientSecret;
    String basicAuth =
        "Basic " + Base64.encodeBase64String(userpass.getBytes(StandardCharsets.UTF_8));
    urlConn.setRequestProperty("Authorization", basicAuth);
    urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    byte[] body = supplied.getQuery().getBytes(StandardCharsets.UTF_8);
    urlConn.setFixedLengthStreamingMode(body.length);
    try {
      try (var output = urlConn.getOutputStream()) {
        output.write(body);
      }
      try (var input = urlConn.getInputStream()) {
        return IOUtils.toString(input, StandardCharsets.UTF_8);
      }
    } finally {
      urlConn.disconnect();
    }
  }
}
