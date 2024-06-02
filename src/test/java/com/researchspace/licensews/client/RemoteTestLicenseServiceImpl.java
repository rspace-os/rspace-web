package com.researchspace.licensews.client;

import com.researchspace.core.util.cache.TimeLimitedMemoryCache;
import com.researchspace.licenseserver.model.License;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("RemoteTestLicenseServiceImpl")
public class RemoteTestLicenseServiceImpl extends LicenseWSClient implements RemoteTestLicenseWS {

  @Override
  public void setRestTemplate(RestTemplate restTemplate) {
    super.setRestTemplate(restTemplate);
  }

  @Override
  public void setCache(TimeLimitedMemoryCache<License> licenseCache) {
    super.setCache(licenseCache);
  }
}
