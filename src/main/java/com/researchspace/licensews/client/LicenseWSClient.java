package com.researchspace.licensews.client;

import com.researchspace.core.util.cache.CacheState;
import com.researchspace.core.util.cache.TimeLimitedMemoryCache;
import com.researchspace.licenseserver.model.CustomerInfo;
import com.researchspace.licenseserver.model.License;
import com.researchspace.licenseserver.model.ServerInfo;
import com.researchspace.licensews.LicenseServerUnavailableException;
import com.researchspace.model.Role;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.service.LicenseRequestResult;
import com.researchspace.service.RemoteLicenseService;
import com.researchspace.service.UserStatisticsManager;
import io.vavr.control.Option;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Web-service client to query license server. */
public class LicenseWSClient implements RemoteLicenseService {

  private Logger log = LoggerFactory.getLogger(LicenseWSClient.class);

  private @Autowired UserStatisticsManager userStatisticsManager;
  private @Autowired LicenseRequestProcessor licenseRequestHandler;

  private @Autowired TimeLimitedMemoryCache<License> cache;

  void setCache(TimeLimitedMemoryCache<License> cache) {
    this.cache = cache;
  }

  void setRestTemplate(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  // these are defined in the deployment.properties file
  @Value("${license.server.location}")
  private String licenseServerURL;

  @Value("${license.key}")
  private String licenseKey;

  // this is thread-safe
  RestTemplate restTemplate;

  public LicenseWSClient() {
    this.restTemplate = new RestTemplate();
  }

  @Override
  public synchronized LicenseRequestResult requestUserLicenses(int userCount, Role role) {
    UserStatistics userStats = userStatisticsManager.getUserStats(7);

    // we really need to get an updated license here, so if cache is unavailable and server is down
    // we can't tell if license is OK or not,
    License license = cache.getCachedItem();
    if (license == null || CacheState.STALE.equals(cache.getState())) {
      license = tryToUpdateLicenseFromServer();
      if (license == null) {
        return LicenseRequestResult.getServerUnavailableResult();
      }
    }
    cache.cache(license);
    return licenseRequestHandler.processLicenseRequest(userCount, role, userStats, license);
  }

  @Override
  public License getLicense() {
    return getLicenseFromCacheOrServer();
  }

  private ResponseEntity<License> doGetLicense() throws RestClientException {
    HttpEntity<String> entity = createHttpEntityWithLicenseKeyHeader();
    ResponseEntity<License> resp =
        restTemplate.exchange(getURL("license"), HttpMethod.GET, entity, License.class);
    return resp;
  }

  private HttpEntity<String> createHttpEntityWithLicenseKeyHeader() {
    HttpHeaders headers = addAPIKeyToHeader();
    HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);
    return entity;
  }

  String getURL(String suffix) {
    if (licenseServerURL == null || isInvalidURL()) {
      throw new IllegalStateException(
          String.format(
              "Could not resolve server URL with value [%s]. "
                  + "Please check the property license.server.location is set.",
              licenseServerURL));
    }
    if (licenseServerURL.endsWith("/")) {
      return licenseServerURL + suffix;
    } else {
      return licenseServerURL + "/" + suffix;
    }
  }

  private boolean isInvalidURL() {
    try {
      new URI(licenseServerURL);
    } catch (URISyntaxException e) {
      return true;
    }
    return false;
  }

  private HttpHeaders addAPIKeyToHeader() {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    headers.add(License.APIKEY_HEADER, licenseKey);
    return headers;
  }

  @Override
  public boolean uploadServerData(String serverData, String macCode, int numUsers) {
    HttpHeaders headers = addAPIKeyToHeader();
    HttpEntity<String> macentity = new HttpEntity<>(macCode, headers);
    HttpEntity<String> dataentity = new HttpEntity<>(serverData, headers);
    HttpEntity<Integer> numusersentity = new HttpEntity<>(numUsers, headers);
    try {
      restTemplate.exchange(getURL("license/macId"), HttpMethod.PUT, macentity, License.class);
    } catch (RestClientException e) {
      log.error("Could not upload  macid to server!: " + e.getMessage());
      return false;
    }
    try {
      restTemplate.exchange(
          getURL("license/serverData"), HttpMethod.PUT, dataentity, License.class);
    } catch (RestClientException e) {
      log.error("Could not upload  server data to server!: " + e.getMessage());
      return false;
    }
    try {
      restTemplate.exchange(
          getURL("license/usedSeats"), HttpMethod.PUT, numusersentity, License.class);
    } catch (RestClientException e) {
      log.error("Could not upload  seat count to server!: " + e.getMessage());
      return false;
    }
    return true;
  }

  @Override
  public boolean isAvailable() {
    try {
      Health health = restTemplate.getForObject(getURL("/manage/health"), Health.class);
      return health.getStatus().equalsIgnoreCase("UP");
    } catch (RestClientException e) {
      log.error("Could not access license server: " + e.getMessage());
      return false;
    }
  }

  /*
   * This will be called quite frequently so implements a caching policy.
   * The web-service only needs to be called occasionally as most of the time there will be a cached license.
   * Synchronisation is needed as the cache is not thread safe.
   * (non-Javadoc)
   * @see com.researchspace.service.LicenseService#isLicenseActive()
   */
  @Override
  public synchronized boolean isLicenseActive() {
    License license = getLicenseFromCacheOrServer();
    return license.isActive();
  }

  @Override
  public synchronized Optional<String> getServerUniqueId() {
    License license = getLicenseFromCacheOrServer();
    return Option.of(license.getServerInfo()).map(ServerInfo::getUniqueId).toJavaOptional();
  }

  @Override
  public Optional<String> getCustomerName() {
    License license = getLicenseFromCacheOrServer();
    return Option.of(license.getCustomerInfo())
        .map(CustomerInfo::getOrganisationName)
        .toJavaOptional();
  }

  private License getLicenseFromCacheOrServer() {
    License license = cache.getCachedItem();
    if (license != null) {
      // we have a cached license
      if (cache.isStale()) {
        // it's stale - need to refresh
        log.info("Cached license is stale.. refreshing");
        License updated = tryToUpdateLicenseFromServer();
        if (updated != null) {
          // update from server was successful, we'll cache it and use it.
          cache.cache(updated);
          log.info("Recached license.");
          license = updated;
        } else {
          // could not update - recache old, stale license for now and we'll try again later.
          log.warn(
              "Could not update cache from server - re-caching. Maybe license server is down?");
          cache.cache(license);
        }
      } else {
        // we're ok to use cached item
      }

    } else { // license is null, we have to get from server or throw exception.
      license = tryToUpdateLicenseFromServer();
      if (license != null) {
        // update from server was successful, we'll cache it and use it.
        cache.cache(license);
      } else {
        throw new LicenseServerUnavailableException(
            "Could not get license from server, and license is null! Maybe license server is"
                + " down?");
      }
    }
    return license;
  }

  /* returns license or null if license server can't be connected */
  private License tryToUpdateLicenseFromServer() {
    log.debug("Trying to refresh license from server...");
    try {
      ResponseEntity<License> resp = doGetLicense();
      if (resp.getStatusCode() != HttpStatus.OK) {
        log.warn("License not obtainable - error code is " + resp.getStatusCode());
        return null;
      }
      log.debug("License refreshed from server");
      return resp.getBody();
    } catch (RestClientException e) {
      log.error("Client exception calling license server : " + e.getMessage());
      return null;
    }
  }

  @Override
  public int getAvailableSeatCount(UserStatistics userStats) {
    return getLicense().getTotalUserSeats() - userStats.getTotalEnabledUsers();
  }

  @Override
  public boolean forceRefreshLicense() {
    log.info("Forcibly polling license server for new license");
    License updated = tryToUpdateLicenseFromServer();
    if (updated == null) {
      log.warn(
          "Couldn't updated license, keeping current cached version: {} in cache {}",
          cache.getCachedItem().toString(),
          cache.toString());
      return false;
    } else {
      log.info("retrieved new license");
      cache.cache(updated);
      log.info("put license object {} in cache: {}", updated.toString(), cache.toString());
      return true;
    }
  }
}
