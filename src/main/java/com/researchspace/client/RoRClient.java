package com.researchspace.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RoRClient {
  private RestTemplate restTemplate = new RestTemplate();

  @Value("${ror.api.url}")
  private String rorApiUrl;

  public JsonNode getRoRDetailsForID(String rorID) {
    String toUse = rorID.replaceAll("https://", "");
    return restTemplate.getForEntity(rorApiUrl + "/" + toUse, JsonNode.class).getBody();
  }
}
