package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the invariant the on-save external PIDINST push depends on (RSDEV-1251, ADR 0008).
 *
 * <p>{@code InventoryIdentifierExternalUpdateService} skips reading the record when an unfiltered
 * response already lists no identifiers, and trusts a non-empty list as complete. Both halves rest
 * on one property of this class: a filtered view <em>blanks</em> the identifier list rather than
 * trimming it. If a limited view ever carried a subset instead, that service would silently push a
 * subset and nothing over there would fail, so the invariant is asserted here, where it lives.
 */
class ApiInstrumentLimitedViewTest {

  private ApiInstrument instrumentWithAnIdentifier() {
    ApiInstrument instrument = new ApiInstrument();
    instrument.setId(1L);
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.setId(7L);
    doi.setDoi("10.82316/abcd-1234");
    instrument.setIdentifiers(List.of(doi));
    return instrument;
  }

  @Test
  void aLimitedViewCarriesNoIdentifiersAtAll() {
    ApiInstrument instrument = instrumentWithAnIdentifier();
    instrument.setPermittedActions(List.of(ApiInventoryRecordPermittedAction.LIMITED_READ));

    instrument.clearPropertiesForLimitedView();

    assertTrue(
        instrument.getIdentifiers() == null || instrument.getIdentifiers().isEmpty(),
        "a limited view must blank the identifier list, not trim it");
  }

  @Test
  void aPublicViewCarriesNoIdentifiersAtAll() {
    ApiInstrument instrument = instrumentWithAnIdentifier();

    instrument.clearPropertiesForPublicView();

    assertTrue(
        instrument.getIdentifiers() == null || instrument.getIdentifiers().isEmpty(),
        "a public view must blank the identifier list, not trim it");
  }

  /** Control: an unfiltered view keeps them, which is what makes a non-empty list trustworthy. */
  @Test
  void anUnfilteredViewKeepsItsIdentifiers() {
    ApiInstrument instrument = instrumentWithAnIdentifier();
    instrument.setPermittedActions(
        List.of(ApiInventoryRecordPermittedAction.READ, ApiInventoryRecordPermittedAction.UPDATE));

    assertNotNull(instrument.getIdentifiers());
    assertTrue(instrument.getIdentifiers().size() == 1);
  }
}
