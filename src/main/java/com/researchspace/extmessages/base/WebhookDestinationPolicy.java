package com.researchspace.extmessages.base;

import com.google.common.net.InternetDomainName;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The origins a webhook sender may post to (RSDEV-1340), read from a deployment property holding a
 * comma-separated list of {@code scheme://host[:port]} entries. A host starting with {@code *.}
 * matches any subdomain (one or more labels) but not the parent domain itself.
 *
 * <p>An exact entry is an explicit administrator decision, so it may name an internal host or
 * address. A wildcard entry must name a DNS zone that a single organisation controls, such as the
 * provider's own domain: hosts it matches are not resolved or checked for internal addresses.
 */
public final class WebhookDestinationPolicy {

  private static final String WILDCARD = "*.";

  private record Origin(String scheme, String host, int port, boolean wildcard) {
    boolean matches(String otherScheme, String otherHost, int otherPort) {
      if (!scheme.equals(otherScheme) || port != otherPort) {
        return false;
      }
      return wildcard ? otherHost.endsWith("." + host) : host.equals(otherHost);
    }
  }

  private final List<Origin> origins;

  private WebhookDestinationPolicy(List<Origin> origins) {
    this.origins = List.copyOf(origins);
  }

  /**
   * Parses the allowed origins.
   *
   * @param propertyName the deployment property the value came from, named in errors
   * @param allowedOrigins comma-separated {@code scheme://host[:port]} entries; blank allows none
   * @throws IllegalStateException if an entry has no host, or a wildcard covers a public suffix or
   *     an IP address, so the configuration stops startup
   */
  public static WebhookDestinationPolicy parse(String propertyName, String allowedOrigins) {
    List<Origin> origins = new ArrayList<>();
    if (allowedOrigins != null) {
      for (String entry : allowedOrigins.split(",")) {
        if (!entry.isBlank()) {
          origins.add(parseEntry(propertyName, entry.trim()));
        }
      }
    }
    return new WebhookDestinationPolicy(origins);
  }

  private static Origin parseEntry(String propertyName, String entry) {
    // the value is not echoed: an administrator could paste a full webhook URL by mistake
    IllegalStateException invalid =
        new IllegalStateException(
            "Invalid entry in " + propertyName + ": expected scheme://host[:port]");
    int hostStart = entry.indexOf("://") + 3;
    boolean wildcard = hostStart > 2 && entry.startsWith(WILDCARD, hostStart);
    URI uri;
    try {
      // java.net.URI cannot parse a "*" in a host, so the wildcard is removed before parsing
      uri =
          new URI(
              wildcard
                  ? entry.substring(0, hostStart) + entry.substring(hostStart + WILDCARD.length())
                  : entry);
    } catch (URISyntaxException e) {
      throw invalid;
    }
    if (uri.getHost() == null) {
      throw invalid;
    }
    // anything else malformed (scheme, port, host name) can never match a real URL: it fails closed
    String scheme = Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT);
    String host = normaliseHost(uri.getHost());
    if (wildcard && !isWildcardableDomain(host)) {
      throw invalid;
    }
    int port = uri.getPort() == -1 ? defaultPort(scheme) : uri.getPort();
    return new Origin(scheme, host, port, wildcard);
  }

  /**
   * A wildcard must sit below a registrable domain, so one organisation controls every host it
   * matches: not over an IP address ("*.0.0.1" would match 10.0.0.1) and not over a public suffix
   * such as {@code com} or {@code azurewebsites.net}, where anyone can register a matching host and
   * point it at an internal address.
   */
  private static boolean isWildcardableDomain(String host) {
    return InternetDomainName.isValid(host) && InternetDomainName.from(host).isUnderPublicSuffix();
  }

  /**
   * Checks the URL's form and origin without any network access; used when a webhook is saved and
   * before every send.
   *
   * @return the checked URL, to send to
   * @throws WebhookUrlNotAllowedException if the URL is malformed or its origin matches no entry
   */
  public URI check(String url) {
    URI uri = parseUrl(url);
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    String host = normaliseHost(uri.getHost());
    int port = uri.getPort() == -1 ? defaultPort(scheme) : uri.getPort();
    if (origins.stream().noneMatch(o -> o.matches(scheme, host, port))) {
      throw new WebhookUrlNotAllowedException("origin is not in the allowlist");
    }
    return uri;
  }

  private static URI parseUrl(String url) {
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException | NullPointerException e) {
      throw new WebhookUrlNotAllowedException("not a valid URL");
    }
    if (!uri.isAbsolute() || uri.getHost() == null) {
      throw new WebhookUrlNotAllowedException("not an absolute URL with a host");
    }
    return uri;
  }

  private static int defaultPort(String scheme) {
    return "https".equals(scheme) ? 443 : "http".equals(scheme) ? 80 : -1;
  }

  /** {@link URI#getHost()} is already ASCII, so only case and a trailing dot need normalising. */
  private static String normaliseHost(String host) {
    String h = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    return h.toLowerCase(Locale.ROOT);
  }
}
