package com.researchspace.licensews.client;

import com.researchspace.core.util.cache.TimeLimitedMemoryCache;
import com.researchspace.licenseserver.model.License;
import com.researchspace.service.RemoteLicenseService;
import org.springframework.web.client.RestTemplate;

/** Extends regular interface to enable configuration of internal state */
public interface RemoteTestLicenseWS extends RemoteLicenseService {

  /**
   * Sets REST-template implementation
   *
   * @param restTemplate
   */
  void setRestTemplate(RestTemplate restTemplate);

  /**
   * Set a cache for the license, if the server goes down.
   *
   * @param licenseCache
   */
  void setCache(TimeLimitedMemoryCache<License> licenseCache);
}
