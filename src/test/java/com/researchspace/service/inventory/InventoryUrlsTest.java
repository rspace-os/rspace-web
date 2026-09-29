package com.researchspace.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The single definition of which addresses RSpace authored. Two decisions read it and must never
 * disagree: whether registering may write an identifier's public landing page into a Landing page
 * field, and whether deleting that identifier may clear it again (ADR 0006 items 4 and 5).
 */
class InventoryUrlsTest {

  private static final String SERVER = "https://rspace.example.com";
  private static final String SUFFIX = "abc123XYZ_-456789";
  private static final String OTHER_SERVER =
      "https://rsdev-1253-map-clibration-and-measurement-f50c365a-11.researchspace.com";

  @Test
  void publicLandingPageUrlBuiltFromServerUrlAndSuffix() {
    assertEquals(
        Optional.of(SERVER + "/public/inventory/" + SUFFIX),
        InventoryUrls.publicLandingPageUrl(SERVER, SUFFIX));
    assertEquals(
        Optional.of(SERVER + "/public/inventory/" + SUFFIX),
        InventoryUrls.publicLandingPageUrl(SERVER + "/", SUFFIX),
        "a trailing slash on the server URL must not double up");
    assertEquals(
        Optional.of(SERVER + "/public/inventory/" + SUFFIX),
        InventoryUrls.publicLandingPageUrl(SERVER + "//", SUFFIX),
        "nor may repeated slashes: this string is both persisted as LOCAL_URL and registered with"
            + " the provider, and the recogniser normalises repeated slashes away, so a builder"
            + " that kept them would stop recognising its own output");
  }

  /** Empty rather than site-relative or the literal "null/public/inventory/...". */
  @Test
  void publicLandingPageUrlIsEmptyWhenEitherPartIsMissing() {
    assertThat(InventoryUrls.publicLandingPageUrl(" ", SUFFIX)).as("no server URL").isEmpty();
    assertThat(InventoryUrls.publicLandingPageUrl(null, SUFFIX)).as("null server URL").isEmpty();
    assertThat(InventoryUrls.publicLandingPageUrl(SERVER, " ")).as("no suffix").isEmpty();
  }

  @Test
  void globalIdPageUrlJoinsServerUrlAndGlobalId() {
    assertEquals(
        Optional.of(SERVER + "/globalId/IN114"), InventoryUrls.globalIdPageUrl(SERVER, "IN114"));
    assertEquals(
        Optional.of(SERVER + "/globalId/IN114"),
        InventoryUrls.globalIdPageUrl(SERVER + "//", "IN114"),
        "trailing slashes are stripped, same normalisation as publicLandingPageUrl");
    assertEquals(
        Optional.of(SERVER + "/globalId/IN114"),
        InventoryUrls.globalIdPageUrl(SERVER, "  IN114  "),
        "a padded global id must not reach the provider as part of the address");
  }

  /** Empty rather than "null/globalId/IN114", for the same reason as the public page. */
  @Test
  void globalIdPageUrlIsEmptyWhenEitherPartIsMissing() {
    assertThat(InventoryUrls.globalIdPageUrl(null, "IN114")).as("null server URL").isEmpty();
    assertThat(InventoryUrls.globalIdPageUrl("  ", "IN114")).as("no server URL").isEmpty();
    assertThat(InventoryUrls.globalIdPageUrl(SERVER, "  ")).as("blank global id").isEmpty();
    assertThat(InventoryUrls.globalIdPageUrl(SERVER, null)).as("null global id").isEmpty();
  }

  /** RSDEV-1528: an RSpace link can only name an item in this deployment. */
  @Test
  void globalIdOfOwnPageRecognisesTheDeploymentsOwnAddresses() {
    assertEquals(
        Optional.of("IC65536"),
        InventoryUrls.globalIdOfOwnPage(SERVER + "/globalId/IC65536", SERVER));
    assertEquals(
        Optional.of("SA32768v3"),
        InventoryUrls.globalIdOfOwnPage(SERVER + "/globalId/SA32768v3", SERVER),
        "a version suffix is kept: the new link must pin the version the address named");
    assertEquals(
        Optional.of("IC65536"),
        InventoryUrls.globalIdOfOwnPage("http://rspace.example.com/globalId/IC65536", SERVER),
        "http against an https server URL is the same server");
    assertEquals(
        Optional.of("IC65536"),
        InventoryUrls.globalIdOfOwnPage("https://RSPACE.Example.com/globalId/IC65536", SERVER),
        "host names are case-insensitive");
    assertEquals(
        Optional.of("IC65536"),
        InventoryUrls.globalIdOfOwnPage(SERVER + "/globalId/IC65536/", SERVER + "/"),
        "trailing slashes on either side are ignored");
    assertEquals(
        Optional.of("IC65536"),
        InventoryUrls.globalIdOfOwnPage(SERVER + "/globalId/IC65536?from=registry", SERVER),
        "a query string does not change which page is named");
    assertEquals(
        Optional.of("IC65536"),
        InventoryUrls.globalIdOfOwnPage(SERVER + "/rspace/globalId/IC65536", SERVER + "/rspace"),
        "a deployment under a context path recognises its own addresses");
  }

  @Test
  void globalIdOfOwnPageIsEmptyForAnyOtherAddress() {
    assertThat(InventoryUrls.globalIdOfOwnPage(OTHER_SERVER + "/globalId/IC65536", SERVER))
        .as("another host")
        .isEmpty();
    assertThat(
            InventoryUrls.globalIdOfOwnPage(
                "https://rspace.example.com:8443/globalId/IC65536", SERVER))
        .as("another port")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage(SERVER + "/other/globalId/IC65536", SERVER))
        .as("another path before the segment")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage(SERVER + "/GLOBALID/IC65536", SERVER))
        .as("the segment cased differently: the route is case-sensitive, so it answers 404")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage(SERVER + "/globalId/IC65536", SERVER + "/rspace"))
        .as("the server URL has a context path the address lacks")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage(SERVER + "/public/inventory/" + SUFFIX, SERVER))
        .as("this server, but not a globalId page")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage(SERVER + "/globalId/IC65536/extra", SERVER))
        .as("more path after the global id")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage(SERVER + "/globalId/", SERVER))
        .as("no global id at all")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage("ftp://rspace.example.com/globalId/IC65536", SERVER))
        .as("this server's host, but not a web address")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage("//rspace.example.com/globalId/IC65536", SERVER))
        .as("no scheme at all")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage("10.1000/manual", SERVER))
        .as("a bare DOI")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage("http://[broken", SERVER))
        .as("unparseable")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage(SERVER + "/globalId/IC65536", " "))
        .as("no server URL configured: nothing can be recognised as ours")
        .isEmpty();
    assertThat(InventoryUrls.globalIdOfOwnPage(null, SERVER)).as("null address").isEmpty();
  }

  @Test
  void namesPublicLandingPageRecognisesRSpacesOwnAddressForThatSuffix() {
    assertTrue(
        InventoryUrls.namesPublicLandingPage(SERVER + "/public/inventory/" + SUFFIX, SUFFIX));
    assertTrue(
        InventoryUrls.namesPublicLandingPage(
            "https://renamed.example.org/public/inventory/" + SUFFIX, SUFFIX),
        "a renamed deployment still names the same page: the tail is what identifies it");
  }

  @Test
  void namesPublicLandingPageRejectsAnythingElse() {
    assertFalse(
        InventoryUrls.namesPublicLandingPage("https://lab.example.org/aws-42", SUFFIX),
        "a landing page the user typed must never be mistaken for ours");
    assertFalse(
        InventoryUrls.namesPublicLandingPage(SERVER + "/public/inventory/someoneElse", SUFFIX),
        "another identifier's public page belongs to that identifier, not this one");
    assertFalse(InventoryUrls.namesPublicLandingPage(null, SUFFIX), "null address");
    assertFalse(
        InventoryUrls.namesPublicLandingPage(SERVER + "/public/inventory/" + SUFFIX, " "),
        "no suffix means nothing to recognise, never a blanket match");
  }

  /**
   * The suffix is the identifier's {@code publicLink}, a base64url token where case is significant,
   * so it is compared exactly. Folding it would let one identifier's stored address be recognised
   * as another's, and the clear on deletion would then blank a field the other identifier still
   * needs. Contrast {@link #namesGlobalIdPageFoldsCaseOfTheGlobalId}.
   */
  @Test
  void namesPublicLandingPageComparesTheSuffixCaseSensitively() {
    assertFalse(
        InventoryUrls.namesPublicLandingPage(
            SERVER + "/public/inventory/ABC123xyz_-456789", SUFFIX),
        "a suffix differing only by case is a different token");
    assertTrue(
        InventoryUrls.namesPublicLandingPage(SERVER + "/PUBLIC/INVENTORY/" + SUFFIX, SUFFIX),
        "the path segment around it is still folded: only the token is case-significant");
  }

  /**
   * RSpace writes this address into a field the user can then edit, so the forms that name the same
   * page have to compare equal. Without this, appending {@code ?from=email} would silently stop the
   * clear on deletion from recognising RSpace's own value (ADR 0006 item 5).
   */
  @Test
  void namesPublicLandingPageNormalisesFormsThatNameTheSamePage() {
    String base = SERVER + "/public/inventory/" + SUFFIX;
    assertTrue(InventoryUrls.namesPublicLandingPage(base + "/", SUFFIX), "trailing slash");
    assertTrue(InventoryUrls.namesPublicLandingPage(base + "//", SUFFIX), "repeated slashes");
    assertTrue(InventoryUrls.namesPublicLandingPage(base + "?from=email", SUFFIX), "query string");
    assertTrue(InventoryUrls.namesPublicLandingPage(base + "#top", SUFFIX), "fragment");
    assertTrue(InventoryUrls.namesPublicLandingPage("  " + base + "  ", SUFFIX), "whitespace");
    assertTrue(
        InventoryUrls.namesPublicLandingPage(SERVER + "/public/inventory/./" + SUFFIX, SUFFIX),
        "dot segment");
  }

  @Test
  void namesGlobalIdPageRecognisesTheRetiredAutoFill() {
    assertTrue(InventoryUrls.namesGlobalIdPage(SERVER + "/globalId/IN5", "IN5"));
    assertTrue(
        InventoryUrls.namesGlobalIdPage("https://old-name.example.com/globalId/IN5", "IN5"),
        "written under a deployment name since changed, still recognised");
    assertTrue(
        InventoryUrls.namesGlobalIdPage(SERVER + "/globalId/IN5?from=email", "IN5"),
        "same normalisation as the public-page tail");

    assertFalse(
        InventoryUrls.namesGlobalIdPage(SERVER + "/globalId/IN999", "IN5"),
        "a link to another record's page is something the user chose");
    assertFalse(InventoryUrls.namesGlobalIdPage("https://lab.example.org/aws-42", "IN5"));
    assertFalse(InventoryUrls.namesGlobalIdPage(null, "IN5"));
    assertFalse(InventoryUrls.namesGlobalIdPage(SERVER + "/globalId/IN5", " "), "blank global id");
  }

  /**
   * Unlike the suffix, a global id is folded: a differently-cased one either resolves to the same
   * sign-in-walled page or to nothing, and neither is fit to register, so treating it as
   * auto-filled errs towards omitting the property, which is the recoverable direction.
   */
  @Test
  void namesGlobalIdPageFoldsCaseOfTheGlobalId() {
    assertTrue(InventoryUrls.namesGlobalIdPage(SERVER + "/globalId/in5", "IN5"));
  }

  /**
   * An address {@code URI.create} rejects still gets checked, rather than being waved through as
   * "not ours" and left in place. A space is unencodable, so this exercises the fallback path.
   */
  @Test
  void unparseableAddressIsStillCheckedRatherThanWavedThrough() {
    assertTrue(
        InventoryUrls.namesGlobalIdPage("https://old name.example.com/globalId/IN5", "IN5"),
        "the raw text still ends with the tail");
    assertFalse(
        InventoryUrls.namesGlobalIdPage("https://old name.example.com/globalId/IN999", "IN5"));
  }

  /** Both importers read back what the builder wrote, so the two must agree. */
  @Test
  void globalIdOfOwnPageRecognisesWhatTheBuilderWrites() {
    String serverUrl = SERVER + "/rspace//";
    assertEquals(
        Optional.of("SA1v2"),
        InventoryUrls.globalIdOfOwnPage(
            InventoryUrls.globalIdPageUrl(serverUrl, "SA1v2").orElseThrow(), serverUrl));
  }
}
