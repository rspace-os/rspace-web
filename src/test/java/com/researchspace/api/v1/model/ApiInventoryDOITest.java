package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventoryDOI.ApiExternalMetadataUpdate;
import com.researchspace.api.v1.model.ApiInventoryDOI.ApiExternalMetadataUpdate.Outcome;
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

  /**
   * The provider record id is the ADDRESS of the external record RSpace writes to, so an existing
   * identifier must not be retargeted by a payload. The id check in {@code
   * ApiInventoryRecordInfo.applyChangesToDatabaseIdentifiers} stops a client naming someone else's
   * identifier row, but not a client pointing its OWN row at someone else's record: without this
   * guard, one instrument PUT carrying a foreign RID or DOI made the RSDEV-1251 on-save push
   * overwrite that record with this instrument's metadata, under the deployment's own provider
   * credentials.
   *
   * <p>Guarded here rather than with {@code Access.READ_ONLY} on the field, because the value is
   * part of every identifier response and READ_ONLY would also stop a Java client reading it back
   * out of one.
   */
  @Test
  void providerRecordIdNotMutatedOnExistingIdentifier() {
    DigitalObjectIdentifier existing = new DigitalObjectIdentifier("10.12345/ours-1234", "t");
    existing.setId(1L);

    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.setDoi("10.12345/someone-elses");

    boolean changed = apiDoi.applyChangesToDatabaseDOI(existing);

    assertEquals(
        "10.12345/ours-1234",
        existing.getIdentifier(),
        "an existing identifier must not be retargeted at another provider record");
    assertFalse(changed);
  }

  /** Registration must still work: it applies the provider's own response to a new identifier. */
  @Test
  void providerRecordIdIsAppliedWhileTheIdentifierIsStillBeingCreated() {
    DigitalObjectIdentifier brandNew = new DigitalObjectIdentifier(null, null, "aSuffix");

    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.setDoi("10.12345/minted-by-the-provider");

    boolean changed = apiDoi.applyChangesToDatabaseDOI(brandNew);

    assertEquals("10.12345/minted-by-the-provider", brandNew.getIdentifier());
    assertTrue(changed);
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

  /**
   * The suffix is generated by an explicit call, never by the no-args constructor: that constructor
   * backs Jackson deserialization and the sparse update DTOs, where a self-generated value could
   * leak into existing identifiers via applyChangesToDatabaseDOI (see ADR 0006).
   */
  @Test
  void publicLinkSuffixGeneratedOnDemandNeverByTheNoArgsConstructor() {
    ApiInventoryDOI doi = new ApiInventoryDOI();
    assertNull(doi.getPublicLinkSuffix(), "sparse update DTOs must not carry a suffix");

    doi.generatePublicLinkSuffix();

    String first = doi.getPublicLinkSuffix();
    assertNotNull(first);
    // 16 random bytes, base64url-encoded without padding. Length alone is only a proxy, so the two
    // properties that actually matter are asserted directly.
    assertEquals(22, first.length());
    assertTrue(
        first.matches("[A-Za-z0-9_-]+"),
        "must be safe as a URL path segment: it becomes /public/inventory/<suffix>, is persisted as"
            + " publicLink and is registered with a provider; got: "
            + first);

    ApiInventoryDOI second = new ApiInventoryDOI();
    second.generatePublicLinkSuffix();
    assertNotEquals(
        first,
        second.getPublicLinkSuffix(),
        "a constant would collide on publicLink's UNIQUE key, and only after the provider call in"
            + " the same transaction had already created a draft");
  }

  /** Server-internal: rsPublicId is the client-facing copy of the entity value. */
  @Test
  void publicLinkSuffixIsNeitherSerializedNorDeserializable() throws Exception {
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.generatePublicLinkSuffix();
    assertFalse(new ObjectMapper().writeValueAsString(doi).contains("publicLinkSuffix"));

    ApiInventoryDOI incoming =
        new ObjectMapper()
            .readValue(
                "{\"id\":1,\"publicLinkSuffix\":\"attacker-chosen\"}", ApiInventoryDOI.class);

    assertNull(incoming.getPublicLinkSuffix(), "clients must not choose the suffix");
  }

  /**
   * publicLinkSuffix means "the suffix to give a brand-new entity", so a DTO built from an existing
   * identifier must not carry one. The two readers of the suffix
   * (ApiIdentifiersHelper.createDoiToSave and addRecordIdentifierForRegisteredApiIdentifier) both
   * hand it to a new DigitalObjectIdentifier; carrying an already-persisted publicLink here would
   * let one identifier's public page be assigned to a second row, and publicLink has UNIQUE KEY
   * isPublicLink, so that surfaces as a constraint violation at flush - after the provider call in
   * the same transaction has already created a draft. rsPublicId carries the entity value for every
   * read purpose. See ADR 0006.
   */
  @Test
  void entityConstructorDoesNotCopyPublicLinkAsSuffix() {
    DigitalObjectIdentifier entity = new DigitalObjectIdentifier(null, null);

    ApiInventoryDOI api = new ApiInventoryDOI(entity);

    assertNull(api.getPublicLinkSuffix(), "an entity-derived DTO must not carry a suffix");
    assertEquals(entity.getPublicLink(), api.getRsPublicId());
  }

  /**
   * The Inventory UI switches on these exact strings, and the API spec documents them, so the enum
   * constant names are the wire contract rather than an implementation detail: renaming one would
   * silently make the frontend report every outcome as a failure.
   *
   * <p>Pinned here as well as in {@code InstrumentExternalMetadataUpdateMVCIT} because that test
   * needs a database and a real Spring context, so it does not run in the fast unit suite that
   * guards an ordinary change to this class.
   */
  @Test
  void externalMetadataUpdateOutcomeSerializesAsTheLiteralTokenTheUiSwitchesOn() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    assertTrue(
        mapper
            .writeValueAsString(new ApiExternalMetadataUpdate(Outcome.UPDATED, "any reason"))
            .contains("\"outcome\":\"UPDATED\""));
    assertTrue(
        mapper
            .writeValueAsString(new ApiExternalMetadataUpdate(Outcome.FAILED, "any reason"))
            .contains("\"outcome\":\"FAILED\""));
    assertTrue(
        mapper
            .writeValueAsString(new ApiExternalMetadataUpdate(Outcome.NOT_UPDATABLE, "any reason"))
            .contains("\"outcome\":\"NOT_UPDATABLE\""));
  }

  /**
   * RSDEV-1251's {@code succeeded} boolean was removed in RSDEV-1356: it restated {@code outcome},
   * nothing consumed it, and two fields for one fact could be made to disagree. Asserted so it
   * cannot come back by accident, for instance as a convenience getter on this class.
   */
  @Test
  void externalMetadataUpdateCarriesOnlyTheOutcomeAndNotARedundantBoolean() throws Exception {
    String json =
        new ObjectMapper()
            .writeValueAsString(new ApiExternalMetadataUpdate(Outcome.UPDATED, "any reason"));

    assertFalse(json.contains("succeeded"), json);
  }
}
