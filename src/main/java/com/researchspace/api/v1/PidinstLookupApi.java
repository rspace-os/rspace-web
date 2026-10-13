package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.model.User;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * PID lookup on the public registries (RSDEV-1518, ADR 0011). Available to every Inventory user,
 * whatever the PIDINST provider settings hold.
 */
@RequestMapping("/api/inventory/v1/pidinst")
public interface PidinstLookupApi {

  /**
   * Free text, or a DOI / Handle (bare or as a resolver address) for a direct lookup, across the
   * registries named in {@code providers} ({@code PIDINST_DATACITE}, {@code PIDINST_B2INST}, one or
   * both, comma-separated or repeated). One merged page of 50, newest update first; {@code
   * pageNumber} is 0-based and defaults to 0. 422 for a short query or no usable registry.
   */
  @GetMapping("/search")
  ApiPidinstSearchResult search(String query, List<String> providers, int pageNumber, User user);
}
