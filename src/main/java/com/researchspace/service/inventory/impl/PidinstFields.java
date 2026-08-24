package com.researchspace.service.inventory.impl;

import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.service.inventory.InventoryUrls;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Resolves the structured fields of an instrument that carry PIDINST meaning.
 *
 * <p>Shared deliberately, in the same spirit as {@link InventoryUrls}: an instrument's fields are
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
   * The live link held by the record's link field with this canonical name: present only when the
   * field exists (name matched as in {@link #mappedField}, type LINK), holds a link that is not
   * soft-deleted, and that link names a target. Anything else reads as an empty field.
   */
  static Optional<InventoryLink> mappedLink(InstrumentEntity record, String canonicalName) {
    return mappedField(record, canonicalName, FieldType.LINK)
        .filter(InventoryLinkField.class::isInstance)
        .map(field -> ((InventoryLinkField) field).getLink())
        .filter(Objects::nonNull)
        .filter(link -> !link.isDeleted())
        .filter(link -> StringUtils.isNotBlank(link.getTargetGlobalId()));
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
   * than a value the user typed, recognised by its {@code /globalId/<globalId>} tail through {@link
   * InventoryUrls#namesGlobalIdPage}. Shared with the public-page recogniser so the two cannot
   * disagree about which addresses RSpace authored; see that class for the normalisation and for
   * the consequences deliberately accepted.
   */
  private static boolean isLegacyAutoFilledLandingPage(String fieldValue, InstrumentEntity source) {
    return InventoryUrls.namesGlobalIdPage(fieldValue, source.getGlobalIdentifier());
  }
}
