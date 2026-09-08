package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of {@code GET /api/records?q=...}: InvenioRDM's Elasticsearch-shaped envelope. Each hit
 * is a full published record, so it reuses {@link B2instDraftRecord}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instSearchResult {

  @JsonProperty("hits")
  private B2instSearchHits hits = new B2instSearchHits();

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class B2instSearchHits {

    @JsonProperty("hits")
    private List<B2instDraftRecord> hits = new ArrayList<>();

    /** The provider's total for the query, which may exceed the page returned. */
    @JsonProperty("total")
    private Integer total;
  }
}
