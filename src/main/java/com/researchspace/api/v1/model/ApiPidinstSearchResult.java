package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One page of PID lookup hits from the enabled provider (RSDEV-1326). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiPidinstSearchResult {

  @JsonProperty("provider")
  private String provider;

  /** The provider's total for the query; may exceed {@code hits.size()}, which is capped. */
  @JsonProperty("total")
  private int total;

  @JsonProperty("hits")
  private List<ApiPidinstRecord> hits = new ArrayList<>();
}
