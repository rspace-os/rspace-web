package com.researchspace.extmessages.base;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import com.google.common.primitives.Ints;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The origins a webhook sender may post to (RSDEV-1340), read from a deployment property holding a
 * comma-separated list of {@code scheme://host[:port]} entries. A host starting with {@code *.}
 * matches any subdomain (one or more labels) but not the parent domain itself.
 *
 * <p>An exact entry is an explicit administrator decision, so it may name an internal host or
 * address. A wildcard entry is meant for provider-controlled domains: at send time a host it
 * matches must resolve only to public addresses.
 */
public final class WebhookDestinationPolicy {

  /** Resolves a host name; a seam so tests need no DNS. */
  @FunctionalInterface
  public interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  public static final HostResolver DNS = InetAddress::getAllByName;

  private static final String WILDCARD = "*.";
  private static final byte[] IPV4_MAPPED_PREFIX = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1};
  private static final byte[] NAT64_PREFIX = {0, 0x64, -1, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0};

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
    // exact entries first, so an explicitly named host is never resolved
    this.origins = origins.stream().sorted(Comparator.comparing(Origin::wildcard)).toList();
  }

  /**
   * Parses the allowed origins.
   *
   * @param propertyName the deployment property the value came from, named in errors
   * @param allowedOrigins comma-separated {@code scheme://host[:port]} entries; blank allows none
   * @throws IllegalStateException if an entry is malformed, so a bad configuration stops startup
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
    String scheme = Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT);
    int port = uri.getPort() == -1 ? defaultPort(scheme) : uri.getPort();
    String host = uri.getHost() == null ? null : normaliseHost(uri.getHost());
    if (!(scheme.equals("http") || scheme.equals("https"))
        || !isValidHost(host)
        || port < 1
        || port > 65535
        || !isOriginOnly(uri)
        || (wildcard && !isWildcardableDomain(host))) {
      throw invalid;
    }
    return new Origin(scheme, host, port, wildcard);
  }

  /** A DNS name within the label and length limits, or an IP literal. */
  private static boolean isValidHost(String host) {
    return host != null
        && (InternetDomainName.isValid(host) || InetAddresses.isUriInetAddress(host));
  }

  /** No user info, path, query or fragment: an entry names an origin, never a webhook URL. */
  private static boolean isOriginOnly(URI uri) {
    return uri.getRawUserInfo() == null
        && (uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
        && uri.getRawQuery() == null
        && uri.getRawFragment() == null;
  }

  /**
   * A wildcard must sit below a registrable domain: not over an IP address ("*.0.0.1" would match
   * 10.0.0.1) and not over a public suffix such as {@code com} or {@code azurewebsites.net}, where
   * anyone can register a matching host.
   */
  private static boolean isWildcardableDomain(String host) {
    return InternetDomainName.isValid(host) && InternetDomainName.from(host).isUnderPublicSuffix();
  }

  /**
   * Checks the URL's form and origin without any network access; used when a webhook is saved.
   *
   * @throws WebhookUrlNotAllowedException if the URL is malformed, has user info, or its origin
   *     matches no entry
   */
  public void checkSyntax(String url) {
    match(parseUrl(url));
  }

  /**
   * Runs {@link #checkSyntax(String)} and, when only a wildcard entry matches, also rejects a host
   * that resolves to any loopback, private, link-local or otherwise non-public address. Used just
   * before sending.
   *
   * @return the checked URL, to send to
   * @throws WebhookUrlNotAllowedException if the URL is not allowed
   */
  public URI checkResolved(String url, HostResolver resolver) {
    URI uri = parseUrl(url);
    if (match(uri).wildcard()) {
      InetAddress[] addresses;
      try {
        addresses = resolver.resolve(normaliseHost(uri.getHost()));
      } catch (UnknownHostException e) {
        throw new WebhookUrlNotAllowedException("host does not resolve");
      }
      if (addresses.length == 0
          || Arrays.stream(addresses).anyMatch(WebhookDestinationPolicy::isNonPublic)) {
        throw new WebhookUrlNotAllowedException("host resolves to a non-public address");
      }
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
    if (uri.getRawUserInfo() != null) {
      throw new WebhookUrlNotAllowedException("URL has user info");
    }
    return uri;
  }

  private Origin match(URI uri) {
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    String host = normaliseHost(uri.getHost());
    int port = uri.getPort() == -1 ? defaultPort(scheme) : uri.getPort();
    return origins.stream()
        .filter(o -> o.matches(scheme, host, port))
        .findFirst()
        .orElseThrow(() -> new WebhookUrlNotAllowedException("origin is not in the allowlist"));
  }

  private static int defaultPort(String scheme) {
    return "https".equals(scheme) ? 443 : "http".equals(scheme) ? 80 : -1;
  }

  /** {@link URI#getHost()} is already ASCII, so only case and a trailing dot need normalising. */
  private static String normaliseHost(String host) {
    String h = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
    return h.toLowerCase(Locale.ROOT);
  }

  private static boolean isNonPublic(InetAddress address) {
    if (address instanceof Inet4Address) {
      byte[] b = address.getAddress();
      // 0.0.0.0/8, and 100.64.0.0/10 (carrier-grade NAT, also some cloud metadata endpoints)
      if (b[0] == 0 || (b[0] == 100 && (b[1] & 0xc0) == 0x40)) {
        return true;
      }
    }
    if (address instanceof Inet6Address v6) {
      // IPv4-compatible (::a.b.c.d), 6to4 and Teredo addresses carry an IPv4 address
      if (InetAddresses.hasEmbeddedIPv4ClientAddress(v6)) {
        return isNonPublic(InetAddresses.getEmbeddedIPv4ClientAddress(v6));
      }
      byte[] b = v6.getAddress();
      // so do IPv4-mapped (::ffff:a.b.c.d) and NAT64 (64:ff9b::/96) addresses, in the last 4 bytes
      if (Arrays.equals(b, 0, 12, IPV4_MAPPED_PREFIX, 0, 12)
          || Arrays.equals(b, 0, 12, NAT64_PREFIX, 0, 12)) {
        return isNonPublic(InetAddresses.fromInteger(Ints.fromBytes(b[12], b[13], b[14], b[15])));
      }
      if ((b[0] & 0xfe) == 0xfc) { // unique local, fc00::/7
        return true;
      }
    }
    return address.isLoopbackAddress()
        || address.isAnyLocalAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress();
  }
}
