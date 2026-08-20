package com.researchspace.service.inventory.impl;

import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.util.Optional;

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
   * The record's Landing page field. Shared by the fill, the clear and the PIDINST mapping so they
   * can never act on different fields.
   */
  static Optional<InventoryEntityField> landingPage(InstrumentEntity record) {
    return mappedField(record, LANDING_PAGE, FieldType.URI);
  }
}
