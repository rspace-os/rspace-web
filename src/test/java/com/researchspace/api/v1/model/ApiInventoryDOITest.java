package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import org.junit.jupiter.api.Test;

class ApiInventoryDOITest {

  /**
   * The guard behind the provider-URL hardening: both fields describe a page RSpace publishes, so a
   * client must not be able to choose them. Without this, a user who can edit the record could PUT
   * an arbitrary URL and every viewer would see a link whose visible text is the harmless
   * identifier value, with {@code publicUrl} additionally rendered on the unauthenticated public
   * page.
   *
   * <p>All three URL properties on this class are server-owned: {@code url} is the DataCite target
   * and is rendered into an {@code <externalLink>}, {@code publicUrl} is rendered on the
   * unauthenticated public page, and {@code providerUrl} on the Inventory page. Asserted at the
   * Jackson layer rather than through an endpoint, because that is where {@code Access.READ_ONLY}
   * acts.
   */
  @Test
  void serverOwnedUrlsCannotBeSetFromAnIncomingPayload() throws Exception {
    String incoming =
        "{\"id\":1,\"doi\":\"k2j9p-7yh21\","
            + "\"providerUrl\":\"https://attacker.example/evil\","
            + "\"publicUrl\":\"https://attacker.example/citable\","
            + "\"url\":\"https://attacker.example/target\"}";

    ApiInventoryDOI deserialized = new ObjectMapper().readValue(incoming, ApiInventoryDOI.class);

    assertNull(deserialized.getProviderUrl(), "providerUrl must be ignored on deserialization");
    assertNull(deserialized.getPublicUrl(), "publicUrl must be ignored on deserialization");
    assertNull(deserialized.getUrl(), "url must be ignored on deserialization");
    assertEquals("k2j9p-7yh21", deserialized.getDoi(), "control: writable fields still bind");
  }

  /** READ_ONLY must block only the inbound direction; the client still needs to read both. */
  @Test
  void serverOwnedUrlsAreStillSerializedOutbound() throws Exception {
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.setProviderUrl("https://b2inst-test.gwdg.de/uploads/k2j9p-7yh21");
    doi.setPublicUrl("https://doi.org/10.1234/abc");
    doi.setUrl("https://rspace.example.com/globalId/IN5");

    String json = new ObjectMapper().writeValueAsString(doi);

    assertTrue(
        json.contains("\"providerUrl\":\"https://b2inst-test.gwdg.de/uploads/k2j9p-7yh21\""));
    assertTrue(json.contains("\"publicUrl\":\"https://doi.org/10.1234/abc\""));
    assertTrue(json.contains("\"url\":\"https://rspace.example.com/globalId/IN5\""));
  }

  @Test
  void typeNotMutatedOnExistingIdentifier() {
    DigitalObjectIdentifier existing = new DigitalObjectIdentifier(null, null);
    existing.setId(1L);
    existing.setType(IdentifierType.IGSN_DATACITE);

    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.setDoiType(IdentifierType.PIDINST_DATACITE.name());

    boolean changed = apiDoi.applyChangesToDatabaseDOI(existing);

    assertEquals(
        IdentifierType.IGSN_DATACITE,
        existing.getType(),
        "an existing identifier's type must not be mutated by an incoming payload");
    assertFalse(changed);
  }

  @Test
  void typeAppliedWhenCreatingNewIdentifier() {
    DigitalObjectIdentifier newDoi =
        new DigitalObjectIdentifier(null, null); // transient, id == null

    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.setDoiType(IdentifierType.PIDINST_DATACITE.name());

    boolean changed = apiDoi.applyChangesToDatabaseDOI(newDoi);

    assertEquals(
        IdentifierType.PIDINST_DATACITE,
        newDoi.getType(),
        "a new identifier must adopt the incoming type");
    assertTrue(changed);
  }
}
