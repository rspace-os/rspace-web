package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One page of PID lookup hits, merged across the registries the caller asked for (ADR 0011). */
@Data
@NoArgsConstructor
public class ApiPidinstSearchResult {

  /** The registries searched, as asked: {@code PIDINST_DATACITE} and/or {@code PIDINST_B2INST}. */
  @JsonProperty("providers")
  private List<String> providers = new ArrayList<>();

  /** 0-based. */
  @JsonProperty("pageNumber")
  private int pageNumber;

  /** Always {@link com.researchspace.service.inventory.PidinstLookupManager#PAGE_SIZE}. */
  @JsonProperty("pageSize")
  private int pageSize;

  /** The sum of the registries' own totals for the query. */
  @JsonProperty("totalHits")
  private int totalHits;

  /** Each registry's own total, keyed as in {@code providers}. */
  @JsonProperty("totalsByProvider")
  private Map<String, Integer> totalsByProvider = new LinkedHashMap<>();

  /** This page, newest update first, then newest creation, then PID. */
  @JsonProperty("hits")
  private List<ApiPidinstRecord> hits = new ArrayList<>();
}
