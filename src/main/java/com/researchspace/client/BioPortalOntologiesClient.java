package com.researchspace.client;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BioPortalOntologiesClient {
  // Hold up to MAX_SIZE and then evict
  private static class OntologyCache extends LinkedHashMap<String, String> {
    private final int MAX_SIZE;

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
      return size() > MAX_SIZE;
    }

    public OntologyCache(int size) {
      MAX_SIZE = size;
    }
  }

  private OntologyCache localOntologyCache = new OntologyCache(100);
  private RestTemplate restTemplate = new RestTemplate();

  public String getBioOntologyData(String searchTerm) {
    if (localOntologyCache.get(searchTerm) != null) {
      return localOntologyCache.get(searchTerm);
    }
    String json =
        restTemplate
            .exchange(
                "https://bioportal.bioontology.org/search/json_search?q="
                    + searchTerm
                    + "&subtreerootconceptid=&response=json",
                HttpMethod.GET,
                new HttpEntity<>(null, null),
                String.class)
            .getBody();
    json = json.replace("({data:\"", "");
    json = json.replace("~!~\"})", "");
    localOntologyCache.put(searchTerm, json);
    return json;
  }
}
