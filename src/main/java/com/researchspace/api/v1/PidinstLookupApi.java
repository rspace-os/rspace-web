package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * PID lookup on the deployment's enabled PIDINST provider (RSDEV-1326). Available only while a
 * PIDINST provider is enabled, like the rest of the PIDINST feature.
 */
@RequestMapping("/api/inventory/v1/pidinst")
public interface PidinstLookupApi {

  /**
   * Free text, or a DOI / Handle (bare or as a resolver address) for a direct lookup. At most 50
   * hits, sorted by name, with the provider's total.
   */
  @GetMapping("/search")
  ApiPidinstSearchResult search(String query, User user);
}
