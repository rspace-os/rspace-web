package com.researchspace.service.inventory;

import com.researchspace.model.core.GlobalIdPrefix;
import java.net.URI;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * The addresses RSpace builds for an inventory record, and the recognition of addresses it wrote
 * itself rather than a user typing them.
 *
 * <p>One home deliberately. Two decisions have to agree about which addresses RSpace authored:
 * whether registering may write an identifier's public landing page into an instrument's Landing
 * page field, and whether deleting that identifier may clear it again (ADR 0006 items 4 and 5). A
 * second copy of either path segment, or of the normalisation below, would be able to disagree with
 * this one, and the two halves of that invariant would silently stop matching.
 *
 * <p>Replaces {@code GlobalIdUrls}, whose builder went with the retired auto-fill (ADR 0006 item
 * 3), leaving only a path segment RSpace had to recognise. RSDEV-1253 gave that segment a composer
 * again, for a different purpose: PIDINST related identifiers (ADR 0007). RSDEV-1528 added the
 * inverse, {@link #globalIdOfOwnPage}, so import and registration agree about which addresses are
 * ours.
 */
public final class InventoryUrls {

  /** Path segment of the anonymous public identifier page. */
  private static final String PUBLIC_PAGE_PATH = "/public/inventory/";

  /**
   * The globalId lookup route, consumed by {@code GlobalLookupController}'s request mapping so the
   * route and the addresses composed here cannot move apart: a renamed route would silently strand
   * every already-registered address (they are permanent), so the two halves share this constant.
   */
  public static final String GLOBAL_ID_MAPPING = "/globalId";

  /**
   * Path segment marking an address as a record's globalId page. Composed by {@link
   * #globalIdPageUrl} for PIDINST related identifiers, and recognised by {@link
   * #namesGlobalIdPage}, which must still spot the ones the retired auto-fill wrote so they read as
   * an empty Landing page field rather than as something a user chose.
   */
  private static final String GLOBAL_ID_PATH = GLOBAL_ID_MAPPING + "/";

  /**
   * The record types whose version-suffixed globalId ({@code SA42v4}) resolves to that version's
   * read-only view. Consumed by {@code GlobalLookupController} (which routes those addresses) and
   * by the PIDINST related-identifier mapping (which must not register a version suffix the route
   * cannot resolve), so the router and the composer cannot drift: a drift here registers a
   * permanently wrong address. SD and GL resolve version suffixes too, but to an audit view and a
   * file stream rather than the record's page, so they are deliberately not in this set.
   */
  public static final Set<GlobalIdPrefix> VERSIONED_PAGE_PREFIXES =
      Set.copyOf(
          EnumSet.of(
              GlobalIdPrefix.SA,
              GlobalIdPrefix.SS,
              GlobalIdPrefix.IC,
              GlobalIdPrefix.IT,
              GlobalIdPrefix.IN,
              GlobalIdPrefix.NT));

  private InventoryUrls() {}

  /**
   * The public landing page address for a suffix, or empty when either part is missing.
   *
   * <p>Used for both the address registered with a provider and the LOCAL_URL stored on the
   * identifier, so the two cannot be normalised differently. Empty rather than site-relative or
   * {@code "null/public/inventory/..."}: a wrong absolute URL registered with a provider cannot be
   * repaired once a curator accepts the record, and a missing property can.
   */
  public static Optional<String> publicLandingPageUrl(String serverUrl, String suffix) {
    String trimmed = StringUtils.trimToEmpty(serverUrl);
    if (trimmed.isEmpty() || StringUtils.isBlank(suffix)) {
      return Optional.empty();
    }
    // stripEnd, not removeEnd: repeated trailing slashes have to go too, because the recogniser
    // normalises them away and a builder that kept them would stop recognising its own output.
    return Optional.of(StringUtils.stripEnd(trimmed, "/") + PUBLIC_PAGE_PATH + suffix);
  }

  /**
   * The globalId page address for a record, used as the RelatedIdentifier value when PIDINST
   * registration maps the instrument's link fields (RSDEV-1253, ADR 0007). Empty when either part
   * is blank, for the same reason as {@link #publicLandingPageUrl}: a missing related identifier is
   * recoverable, a wrong registered one is not.
   *
   * <p>Reuses the same path segment {@link #namesGlobalIdPage} recognises, so builder and
   * recogniser cannot drift apart. This deliberately revives a globalId-page builder after the
   * auto-fill's one was retired: unlike a LandingPage, a RelatedIdentifier is not required to be
   * anonymously resolvable, and the linked record generally has no public page to point at.
   */
  public static Optional<String> globalIdPageUrl(String serverUrl, String globalId) {
    String trimmed = StringUtils.trimToEmpty(serverUrl);
    if (trimmed.isEmpty() || StringUtils.isBlank(globalId)) {
      return Optional.empty();
    }
    return Optional.of(StringUtils.stripEnd(trimmed, "/") + GLOBAL_ID_PATH + globalId.trim());
  }

  /**
   * The Global ID named by an address when that address is this deployment's own globalId page,
   * else empty: the inverse of {@link #globalIdPageUrl}, deciding whether an address read back by
   * an import names a local record, for the PIDINST import's related identifiers (RSDEV-1528, ADR
   * 0009 decision 9) and the CSV link import alike.
   *
   * <p>Only a web address counts, http or https alike, because http and https on one host are one
   * deployment. Same server means same host (case-insensitive), port (a scheme's default port is
   * the same whether written or left out) and path before the segment; the segment itself is
   * matched exactly, because the route is case-sensitive and {@code /GLOBALID/} answers 404. A
   * version suffix is returned as it is, so the caller keeps the pin the address named. A server
   * URL that has since changed, like a differently cased segment, is deliberately not recognised,
   * unlike in {@link #namesGlobalIdPage}.
   */
  public static Optional<String> globalIdOfOwnPage(String address, String serverUrl) {
    URI page = parseOrNull(address);
    URI server = parseOrNull(serverUrl);
    if (page == null
        || server == null
        || !StringUtils.equalsAnyIgnoreCase(page.getScheme(), "http", "https")
        || page.getHost() == null
        || server.getHost() == null) {
      return Optional.empty();
    }
    String path = StringUtils.stripEnd(StringUtils.defaultString(page.getPath()), "/");
    int segment = path.lastIndexOf(GLOBAL_ID_PATH);
    if (segment < 0) {
      return Optional.empty();
    }
    String globalId = path.substring(segment + GLOBAL_ID_PATH.length());
    boolean sameServer =
        page.getHost().equalsIgnoreCase(server.getHost())
            && portOf(page) == portOf(server)
            && path.substring(0, segment)
                .equals(StringUtils.stripEnd(StringUtils.defaultString(server.getPath()), "/"));
    return sameServer && !globalId.contains("/") ? Optional.of(globalId) : Optional.empty();
  }

  /**
   * The port, or -1 when it is the scheme's own default, so {@code https://h:443} and {@code
   * https://h} compare equal, as they are one origin; a port left out on both sides still matches
   * across http and https.
   */
  private static int portOf(URI uri) {
    int port = uri.getPort();
    boolean schemeDefault =
        (port == 80 && "http".equalsIgnoreCase(uri.getScheme()))
            || (port == 443 && "https".equalsIgnoreCase(uri.getScheme()));
    return schemeDefault ? -1 : port;
  }

  /** The value as a normalised URI, or null when blank or unparseable. */
  private static URI parseOrNull(String value) {
    String trimmed = StringUtils.trimToNull(value);
    if (trimmed == null) {
      return null;
    }
    try {
      return URI.create(trimmed).normalize();
    } catch (IllegalArgumentException unparseable) {
      return null;
    }
  }

  /**
   * Whether an address names the public landing page of the identifier with this suffix, i.e.
   * whether RSpace itself wrote it. Lets a caller undo that write without touching an address a
   * user chose.
   *
   * <p>Matched on the tail rather than by equality with the address {@link #publicLandingPageUrl}
   * would build today: the deployment's server URL may have changed since, and the question is what
   * RSpace wrote, not what it would write now. A blank suffix matches nothing rather than
   * everything.
   */
  public static boolean namesPublicLandingPage(String address, String suffix) {
    return StringUtils.isNotBlank(suffix)
        && endsWithPathTail(address, PUBLIC_PAGE_PATH, suffix, false);
  }

  /**
   * Whether an address names a record's globalId page, which is what the retired auto-fill wrote.
   *
   * <p>Matched on the tail, not on equality with the currently configured address: the tail names
   * this one record, while the host part is whatever the server URL said at fill time. Comparing
   * whole addresses would stop recognising the fill as soon as the deployment was renamed or lost
   * its server URL setting, and RSpace would then register the login-walled default, irreversibly
   * once a curator accepts.
   *
   * <p>Case is folded here, unlike the public-page suffix: a differently-cased global id either
   * resolves to the same sign-in-walled page or to nothing, and neither is fit to register, so
   * erring towards treating it as auto-filled errs towards omission.
   */
  public static boolean namesGlobalIdPage(String address, String globalId) {
    return StringUtils.isNotBlank(globalId)
        && endsWithPathTail(address, GLOBAL_ID_PATH, globalId, true);
  }

  /**
   * Whether the address's path ends with {@code pathSegment + token}, comparing normalised paths so
   * that forms naming the same page compare equal.
   *
   * <p>Without that normalisation an address a user had since edited to carry a trailing slash or a
   * {@code ?from=...} would stop being recognised as one RSpace wrote, and the decision depending
   * on that recognition would silently invert.
   *
   * <p>The path segment is always compared case-insensitively, because a URL path is not where the
   * identity lives. The token is a separate question, hence {@code foldToken}.
   *
   * @param foldToken whether the trailing token is case-insensitive. False for a base64url suffix,
   *     which <em>is</em> the identifier's {@code publicLink} and so case-significant; true for a
   *     global id, where folding errs towards the recoverable outcome.
   */
  private static boolean endsWithPathTail(
      String address, String pathSegment, String token, boolean foldToken) {
    String path = StringUtils.stripEnd(comparablePath(StringUtils.trimToEmpty(address)), "/");
    if (path.length() < pathSegment.length() + token.length()) {
      return false;
    }
    String trailingToken = path.substring(path.length() - token.length());
    String upToToken = path.substring(0, path.length() - token.length());
    boolean tokenMatches =
        foldToken ? trailingToken.equalsIgnoreCase(token) : trailingToken.equals(token);
    return tokenMatches && StringUtils.endsWithIgnoreCase(upToToken, pathSegment);
  }

  /**
   * The address's path, normalised: query and fragment gone, dot segments resolved, percent-escapes
   * decoded. {@link URI#getPath()} does the last two ({@code getPath} decodes, unlike {@code
   * getRawPath}).
   *
   * <p>An address the URI parser rejects falls back to the raw text with query and fragment
   * stripped, so a malformed value is still checked rather than waved through. The field's own
   * validation makes that rare but not impossible, since it runs at save time and says nothing
   * about rows written before it existed.
   */
  private static String comparablePath(String address) {
    try {
      String path = URI.create(address).normalize().getPath();
      return path == null ? address : path;
    } catch (IllegalArgumentException unparseable) {
      return StringUtils.substringBefore(StringUtils.substringBefore(address, "#"), "?");
    }
  }
}
