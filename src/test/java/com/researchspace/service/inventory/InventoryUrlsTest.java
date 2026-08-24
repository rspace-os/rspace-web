package com.researchspace.service.inventory;

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
    assertTrue(InventoryUrls.publicLandingPageUrl(" ", SUFFIX).isEmpty(), "no server URL");
    assertTrue(InventoryUrls.publicLandingPageUrl(null, SUFFIX).isEmpty(), "null server URL");
    assertTrue(InventoryUrls.publicLandingPageUrl(SERVER, " ").isEmpty(), "no suffix");
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
}
