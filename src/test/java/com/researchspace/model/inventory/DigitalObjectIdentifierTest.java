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
}
