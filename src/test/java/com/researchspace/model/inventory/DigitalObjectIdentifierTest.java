package com.researchspace.model.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;

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
    assertEquals(3, IdentifierType.values().length);
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
  public void constructorGeneratesPublicLinkWhenGivenNoSuffix() {
    DigitalObjectIdentifier withNull = new DigitalObjectIdentifier("10.12345/test", "t", null);
    DigitalObjectIdentifier withBlank = new DigitalObjectIdentifier("10.12345/test", "t", " ");
    DigitalObjectIdentifier twoArg = new DigitalObjectIdentifier("10.12345/test", "t");
    assertNotNull(withNull.getPublicLink());
    assertNotNull(withBlank.getPublicLink());
    assertNotNull(twoArg.getPublicLink());
    // 16 random bytes, base64url-encoded without padding: pins the entropy, not the char count
    assertEquals(22, twoArg.getPublicLink().length());
  }
}
