package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.field.FieldType;

class ExtraLinkFieldTest {

	@Test
	void getTypeReturnsLink() {
		ExtraLinkField field = new ExtraLinkField();
		assertEquals(FieldType.LINK, field.getType());
	}

	@Test
	void validateNewDataAcceptsAnyString() {
		ExtraLinkField field = new ExtraLinkField();
		assertNull(field.validateNewData(""));
		assertNull(field.validateNewData("anything"));
		assertNull(field.validateNewData(null));
	}

	@Test
	void shallowCopyProducesIndependentInstanceWithSamePayload() {
		ExtraLinkField original = new ExtraLinkField();
		original.setName("related items");
		original.setData("SA123");
		original.setId(42L);

		InventoryLink link = new InventoryLink();
		link.setTargetGlobalId("SA123");
		link.setTargetPrefix(GlobalIdPrefix.SA);
		link.setTargetDbId(123L);
		link.setRelationType("IsCalibratedBy");
		original.setLink(link);

		ExtraLinkField copy = original.shallowCopy();

		assertNotSame(original, copy);
		assertNull(copy.getId());
		assertEquals(original.getName(), copy.getName());
		assertEquals(original.getData(), copy.getData());
		assertFalse(copy.isDeleted());
	}

	@Test
	void shallowCopyCopiesLinkAssociationAsIndependentInstance() {
		ExtraLinkField original = new ExtraLinkField();
		InventoryLink link = new InventoryLink();
		link.setId(7L);
		link.setTargetGlobalId("SA123v3");
		link.setTargetPrefix(GlobalIdPrefix.SA);
		link.setTargetDbId(123L);
		link.setVersionPin(3L);
		link.setTargetRevisionId(99L);
		link.setRelationType("IsCalibratedBy");
		original.setLink(link);

		ExtraLinkField copy = original.shallowCopy();

		assertNotNull(copy.getLink());
		assertNotSame(original.getLink(), copy.getLink());
		assertNull(copy.getLink().getId());
		assertEquals("SA123v3", copy.getLink().getTargetGlobalId());
		assertEquals(GlobalIdPrefix.SA, copy.getLink().getTargetPrefix());
		assertEquals(123L, copy.getLink().getTargetDbId());
		assertEquals(3L, copy.getLink().getVersionPin());
		assertEquals(99L, copy.getLink().getTargetRevisionId());
		assertEquals("IsCalibratedBy", copy.getLink().getRelationType());
	}

	@Test
	void shallowCopyWithNullLinkProducesNullLink() {
		ExtraLinkField original = new ExtraLinkField();
		assertNull(original.shallowCopy().getLink());
	}

	@Test
	void linkAssociationRoundTrips() {
		ExtraLinkField field = new ExtraLinkField();
		InventoryLink link = new InventoryLink();
		link.setTargetGlobalId("SA99");
		link.setRelationType("References");
		field.setLink(link);

		assertNotNull(field.getLink());
		assertEquals("SA99", field.getLink().getTargetGlobalId());
		assertEquals("References", field.getLink().getRelationType());
	}

}
