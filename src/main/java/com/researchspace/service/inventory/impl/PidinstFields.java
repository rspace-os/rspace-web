package com.researchspace.service.inventory.impl;

import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.net.URI;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves the structured fields of an instrument that carry PIDINST meaning.
 *
 * <p>Shared deliberately, in the same spirit as {@link GlobalIdUrls}: an instrument's fields are
 * matched by display name, so any two places that disagree about how a name is matched disagree
 * about which field they are looking at. The service layer's landing-page rules and the PIDINST
 * mapping both depend on picking the same field, so they resolve it here rather than each carrying
 * their own copy of the predicate.
 *
 * <p>That leaves the Inventory UI's copy of the landing-page rule (see {@code InstrumentModel.tsx})
 * as the only remaining duplicate, which nothing here can keep in step; a rename must be applied
 * there by hand.
 */
final class PidinstFields {

  /**
   * Canonical spelling from the default PIDINST template; see CONTEXT.md ("PIDINST-mapped field").
   */
  static final String LANDING_PAGE = "Landing page";

  private PidinstFields() {}

  /**
   * The record's field with the given canonical name and type, matched case-insensitively and
   * ignoring surrounding whitespace, so a field is recognised however it was created.
   *
   * <p>Only active fields are considered, so a soft-deleted field is never treated as the mapped
   * one.
   */
  static Optional<InventoryEntityField> mappedField(
      InstrumentEntity record, String canonicalName, FieldType expectedType) {
    return record.getActiveFields().stream()
        .filter(f -> f.getType() == expectedType)
        .filter(f -> f.getName() != null && canonicalName.equalsIgnoreCase(f.getName().trim()))
        .findFirst();
  }

  /**
   * The record's Landing page field. Shared by the clear, the registration-time write and the
   * PIDINST mapping so they can never act on different fields.
   */
  static Optional<InventoryEntityField> landingPage(InstrumentEntity record) {
    return mappedField(record, LANDING_PAGE, FieldType.URI);
  }

  /**
   * The Landing page the *user* chose, or empty when the field is blank, absent, or still carrying
   * a landing page the retired auto-fill wrote.
   *
   * <p>The single definition of "the user typed something here", relied on by two decisions that
   * must agree: which address is registered with a PID provider, and whether registering may write
   * the identifier's public landing page into the field. If they disagreed, RSpace would either
   * overwrite a value a user chose or register an address the field does not show. See ADR 0006.
   */
  static Optional<String> userTypedLandingPage(InstrumentEntity record) {
    return landingPage(record)
        .map(InventoryEntityField::getFieldData)
        .filter(StringUtils::isNotBlank)
        .map(String::trim)
        .filter(value -> !isLegacyAutoFilledLandingPage(value, record));
  }

  /**
   * Whether a typed Landing page is an address a resolver could actually follow. The field's own
   * validation is only {@code new URI(...)} parsing (core-model's InventoryUriField), which accepts
   * a bare host, a relative path, and non-web schemes such as {@code javascript:} and {@code
   * data:}. None of those identify the instrument to someone resolving the PID, and a LandingPage
   * is baked into a citable PID once a curator accepts, so anything that is not an absolute http(s)
   * address falls back to the identifier's public page rather than being registered.
   *
   * <p>A scheme prefix is deliberately enough: the field's validation does reject a scheme with no
   * authority ({@code new URI("http://")} throws "Expected authority"), so there is no reachable
   * input of that shape left to guard against here.
   */
  static boolean isResolvableAddress(String fieldValue) {
    return StringUtils.startsWithIgnoreCase(fieldValue, "http://")
        || StringUtils.startsWithIgnoreCase(fieldValue, "https://");
  }

  /**
   * Whether the Landing page field is holding a landing page the retired auto-fill wrote rather
   * than a value the user typed. Matched on the {@code /globalId/<globalId>} tail alone, not on
   * equality with the currently configured address: the tail is what the retired auto-fill produced
   * and names this one record, while the host part is whatever the server URL said at fill time.
   * Comparing whole addresses would stop recognising the fill as soon as the deployment was renamed
   * or lost its server URL setting, and would then register the login-walled default —
   * irreversibly, once a curator accepts. See {@link GlobalIdUrls} and ADR 0006.
   *
   * <p>The tail is compared against the address's path with any query and fragment dropped, any
   * trailing slash removed, and case folded, because none of those change which page the address
   * names. Without that normalisation a default a user had since edited to carry a trailing slash
   * or a {@code ?from=...} would read as user-typed and be registered.
   *
   * <p>Two accepted consequences, both erring towards omission because a missing property is
   * recoverable and a wrong published one is not. A user who deliberately types some other RSpace's
   * {@code /globalId/<same id>} address has it discarded in favour of this identifier's public
   * page. And a differently-cased global id is treated as the default even though it may resolve to
   * nothing; an address that resolves to nothing is no more fit to register.
   */
  private static boolean isLegacyAutoFilledLandingPage(String fieldValue, InstrumentEntity source) {
    String globalIdTail = GlobalIdUrls.GLOBAL_ID_PATH + source.getGlobalIdentifier();
    return StringUtils.endsWithIgnoreCase(
        StringUtils.stripEnd(comparablePath(fieldValue), "/"), globalIdTail);
  }

  /**
   * The address's path, normalised so that forms which name the same page compare equal: query and
   * fragment gone, dot segments resolved, percent-escapes decoded. {@link URI#getPath()} does the
   * last two ({@code getPath} decodes, unlike {@code getRawPath}).
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
