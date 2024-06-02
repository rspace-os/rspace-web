package com.researchspace.webapp.integrations.protocolsio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.RestTemplate;

@ContextConfiguration(classes = PIOComponentDiscoverabilityIT.class)
@Slf4j
@RunWith(ConditionalTestRunner.class)
public class PIOComponentDiscoverabilityIT {

  private final String PIO_API_KEY =
      "586bc6074e8672fb12cbb7ddc85af7e5d50b45c51562cc12675e9b25431fc308";

  private RestTemplate restTemplate;
  private ObjectMapper objectMapper;

  @Before
  public void setUp() throws Exception {
    objectMapper = new ObjectMapper();
    objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    HttpComponentsClientHttpRequestFactory gzipSupportingRequestFactory =
        new HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create().build());
    restTemplate = new RestTemplate(gzipSupportingRequestFactory);
  }

  /**
   * This is weekly test based on RSPAC-2406 which looks for un-modeled step components via the
   * Protocols IO API, it essentially loops through all the public protocols looking for
   * unrecongnised step component type ids and logs any it finds.
   */
  @Test
  @SneakyThrows
  @RunIfSystemPropertyDefined("weekly")
  public void testForUnknownPIOObjects() {
    String basePIOApiUrl = "https://www.protocols.io/api/v3/protocols?filter=public&key=*";
    HttpHeaders headers = new HttpHeaders();
    headers.add("Authorization", String.format("Bearer %s", PIO_API_KEY));

    Map<Integer, JSONObject> unknownComponents = new HashMap<>();
    // Make first request to /protocols and assign pagination params
    ResponseEntity<String> response =
        restTemplate.exchange(
            basePIOApiUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    JSONObject protocolsResponseJSON =
        objectMapper.convertValue(response.getBody(), JSONObject.class);
    // Get the pagination object and set parameters
    JSONObject paginationJSON = protocolsResponseJSON.getJSONObject("pagination");
    int currentPage = paginationJSON.getInt("current_page");
    int totalPages = paginationJSON.getInt("total_pages");

    String paginationParams = "&page_size=%d&page_id=%d";
    // The list of currently known and modelled step component type ids
    int[] knownStepCompIds = {
      1, 3, 4, 6, 7, 8, 9, 13, 14, 15, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 30, 33
    };
    int numberOfItemsPerPage = 50;

    for (int h = currentPage; h < totalPages; h++) {
      String paginationQueryParams =
          String.format(paginationParams, numberOfItemsPerPage, currentPage);
      log.warn(
          "Making request for protocols page number: {} of {} pages with size: {}",
          currentPage,
          totalPages,
          numberOfItemsPerPage);
      response =
          restTemplate.exchange(
              basePIOApiUrl + paginationQueryParams,
              HttpMethod.GET,
              new HttpEntity<>(headers),
              String.class);
      protocolsResponseJSON = objectMapper.convertValue(response.getBody(), JSONObject.class);

      // Get protocols on this page
      JSONArray protocolsOnThisPage = protocolsResponseJSON.getJSONArray("items");

      for (int i = 0; i < protocolsOnThisPage.length(); i++) {
        int currentProtocolId = (Integer) protocolsOnThisPage.getJSONObject(i).get("id");
        String protocolUrl = basePIOApiUrl + "/" + currentProtocolId;
        // Send request for each protocol to get their step components
        ResponseEntity<String> protocolResp =
            restTemplate.exchange(
                protocolUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        // Convert the response to a JSONObject
        JSONObject respProtocol =
            objectMapper.convertValue(protocolResp.getBody(), JSONObject.class);
        JSONObject protocol = respProtocol.getJSONObject("protocol");
        // Extract the steps array from the protocol
        JSONArray steps = protocol.getJSONArray("steps");
        for (int j = 0; j < steps.length(); j++) {
          JSONArray components = steps.getJSONObject(j).getJSONArray("components");
          // Check the Id of each component and whether it matches current list
          // If not add whole step component to unknown component map, else carry on.
          for (int k = 0; k < components.length(); k++) {
            JSONObject component = components.getJSONObject(k);
            if (!ArrayUtils.contains(knownStepCompIds, component.getInt("type_id"))) {
              unknownComponents.put(protocol.getInt("id"), component);
            }
          }
        }
      }
      currentPage++;
    }
    if (!unknownComponents.isEmpty()) {
      unknownComponents.forEach(
          (protocolId, component) -> {
            log.warn(
                "Unknown Id found in Protocol {} with type_id: {}",
                protocolId,
                component.getInt("type_id"));
            log.warn("Unknown Component: {}", component);
          });
    }
    assertTrue(unknownComponents.isEmpty());
  }
}
