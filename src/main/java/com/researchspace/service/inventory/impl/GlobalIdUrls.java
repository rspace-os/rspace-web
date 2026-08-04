package com.researchspace.service.inventory.impl;

import com.researchspace.properties.IPropertyHolder;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Builds the public RSpace address of an inventory record from its global id.
 *
 * <p>Shared deliberately: the landing page RSpace stores on an instrument and the LandingPage it
 * registers with a PID provider describe the same page, so they must not be able to drift apart.
 */
final class GlobalIdUrls {

  private GlobalIdUrls() {}

  /**
   * {@code <serverUrl>/globalId/<globalId>}, or empty when no server URL is configured.
   *
   * <p>Empty rather than a site-relative {@code /globalId/IN123}, because both callers would do the
   * wrong thing with that string and neither could undo it. Persisting it fills the instrument's
   * Landing page field, which then stops being blank and can never be repaired by the default-fill.
   * Sending it to B2INST bakes a broken address into a citable PID the moment a curator accepts,
   * and RSpace has no path to update a published record. An {@link Optional} makes the caller
   * decide what to do with nothing, rather than silently handing them something unusable.
   */
  static Optional<String> globalIdUrl(IPropertyHolder properties, String globalId) {
    String serverUrl = StringUtils.trimToEmpty(properties.getServerUrl());
    if (serverUrl.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(StringUtils.removeEnd(serverUrl, "/") + "/globalId/" + globalId);
  }
}
