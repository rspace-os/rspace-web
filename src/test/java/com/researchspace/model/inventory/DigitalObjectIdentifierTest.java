package com.researchspace.model.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierOtherProperty;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

public class DigitalObjectIdentifierTest {

  /*
   * IdentifierType is mapped ORDINAL to the INT DigitalObjectIdentifier.type
   * column, so values must only ever be appended, never reordered or removed.
   */
  @Test
  public void identifierTypeOrdinalsAreStable() {
    assertEquals(0, IdentifierType.IGSN_DATACITE.ordinal());
    assertEquals(1, IdentifierType.PIDINST_DATACITE.ordinal());
    assertEquals(2, IdentifierType.PIDINST_B2INST.ordinal());
    assertThat(IdentifierType.values()).hasSize(3);
  }

  @Test
  public void identifierTypeDefaultsToIgsnDatacite() {
    DigitalObjectIdentifier doi = new DigitalObjectIdentifier("10.12345/test", "test title");
    assertEquals(IdentifierType.IGSN_DATACITE, doi.getType());
  }

  /*
   * The type column is ordinal-mapped; making @Enumerated(ORDINAL) explicit (rather
   * than relying on the JPA default) guards against an accidental switch to STRING
   * mapping that would silently corrupt persisted values.
   */
  @Test
  public void typeGetterIsExplicitlyOrdinalMapped() throws NoSuchMethodException {
    Enumerated enumerated =
        DigitalObjectIdentifier.class.getMethod("getType").getAnnotation(Enumerated.class);
    assertNotNull(enumerated, "getType() must carry an explicit @Enumerated annotation");
    assertEquals(EnumType.ORDINAL, enumerated.value());
  }

  /*
   * The suffix must exist before an external provider is called, so the public landing
   * page's address can be part of the registration payload (RSDEV-1254). The entity
   * therefore adopts a caller-supplied suffix rather than always minting its own.
   */
  @Test
  public void constructorAdoptsPreGeneratedPublicLinkSuffix() {
    DigitalObjectIdentifier doi =
        new DigitalObjectIdentifier("10.12345/test", "test title", "abc123XYZ_-456789");
    assertEquals("abc123XYZ_-456789", doi.getPublicLink());
  }

  /*
   * The suffix becomes a path segment of the public landing page's URL, so surrounding
   * whitespace from a caller must not survive into the persisted value: it would yield an
   * address that only resolves once percent-encoded, and differs from the one registered
   * with the external provider.
   */
  @Test
  public void constructorTrimsSurroundingWhitespaceFromSuppliedSuffix() {
    DigitalObjectIdentifier doi =
        new DigitalObjectIdentifier("10.12345/test", "test title", "  abc123XYZ_-456789\t\n");
    assertEquals("abc123XYZ_-456789", doi.getPublicLink());
  }

  @Test
  public void isPublishedStateCoversDataCiteFindableAndB2instAccepted() {
    assertTrue(DigitalObjectIdentifier.isPublishedState("findable"));
    assertTrue(DigitalObjectIdentifier.isPublishedState("accepted"));
    assertFalse(DigitalObjectIdentifier.isPublishedState("draft"));
    assertFalse(DigitalObjectIdentifier.isPublishedState("submitted"));
    assertFalse(DigitalObjectIdentifier.isPublishedState("declined"));
    assertFalse(DigitalObjectIdentifier.isPublishedState(null));
  }

  /**
   * The state column is free-form text: RSpace writes it for some transitions and copies it
   * verbatim from a provider response for others, so its case is not guaranteed. This gates the
   * unauthenticated public landing page, and a case-sensitive comparison would have closed that
   * page for a record the provider reported as published - and, through {@code
   * DigitalObjectIdentifierDaoHibernate}, mis-selected which revision the page serves.
   */
  @Test
  public void isPublishedStateIgnoresTheCaseTheProviderUsed() {
    assertTrue(DigitalObjectIdentifier.isPublishedState("Findable"));
    assertTrue(DigitalObjectIdentifier.isPublishedState("FINDABLE"));
    assertTrue(DigitalObjectIdentifier.isPublishedState("Accepted"));
    assertTrue(DigitalObjectIdentifier.isPublishedState("ACCEPTED"));
    // still only these two states, whatever the case
    assertFalse(DigitalObjectIdentifier.isPublishedState("DRAFT"));
    assertFalse(DigitalObjectIdentifier.isPublishedState("Submitted"));
  }

  @Test
  public void constructorGeneratesPublicLinkWhenGivenNoSuffix() {
    DigitalObjectIdentifier withNull = new DigitalObjectIdentifier("10.12345/test", "t", null);
    DigitalObjectIdentifier withBlank = new DigitalObjectIdentifier("10.12345/test", "t", " ");
    DigitalObjectIdentifier twoArg = new DigitalObjectIdentifier("10.12345/test", "t");
    assertNotNull(withNull.getPublicLink());
    assertNotNull(withBlank.getPublicLink());
    assertNotNull(twoArg.getPublicLink());
    // 16 random bytes, base64url-encoded without padding: pins the entropy, not the char count
    assertThat(twoArg.getPublicLink()).hasSize(22);
  }

  @Test
  public void b2instPublicUrlIsTheStoredHandleAndNeverBuiltFromTheRecordId() {
    DigitalObjectIdentifier accepted = new DigitalObjectIdentifier("d4mwx-bfd89", "a title");
    accepted.setType(IdentifierType.PIDINST_B2INST);
    accepted.setState("accepted");
    assertNull(accepted.getPublicUrl());

    accepted.addOtherData(
        IdentifierOtherProperty.PUBLIC_URL, "http://hdl.handle.net/21.T11975/d4mwx-bfd89");
    assertEquals("http://hdl.handle.net/21.T11975/d4mwx-bfd89", accepted.getPublicUrl());
  }

  /*
   * A null type is a row persisted before the type column was populated, which predates PIDINST
   * and is treated as IGSN everywhere else (InventoryIdentifierApiManagerImpl.settingTypeFor).
   */
  @ParameterizedTest
  @NullSource
  @EnumSource(
      value = IdentifierType.class,
      names = {"IGSN_DATACITE", "PIDINST_DATACITE"})
  public void dataCitePublicUrlFallsBackToDoiOrgOnlyWhileTheDoiResolves(IdentifierType type) {
    DigitalObjectIdentifier doi = new DigitalObjectIdentifier("10.82316/abc", "a title");
    doi.setType(type);

    doi.setState("findable");
    assertEquals("https://doi.org/10.82316/abc", doi.getPublicUrl());
    doi.setState("registered");
    assertEquals("https://doi.org/10.82316/abc", doi.getPublicUrl());
    doi.setState("draft");
    assertNull(doi.getPublicUrl());
  }
}
