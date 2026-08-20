package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.researchspace.model.core.GlobalIdPrefix;

class InventoryLinkTest {

	@Test
	void settersAndGettersRoundTrip() {
		InventoryLink link = new InventoryLink();
		link.setId(7L);
		link.setTargetGlobalId("SA123v3");
		link.setTargetPrefix(GlobalIdPrefix.SA);
		link.setTargetDbId(123L);
		link.setVersionPin(3L);
		link.setRelationType("IsCalibratedBy");
		link.setDeleted(true);

		assertEquals(7L, link.getId());
		assertEquals("SA123v3", link.getTargetGlobalId());
		assertEquals(GlobalIdPrefix.SA, link.getTargetPrefix());
		assertEquals(123L, link.getTargetDbId());
		assertEquals(3L, link.getVersionPin());
		assertEquals("IsCalibratedBy", link.getRelationType());
		assertTrue(link.isDeleted());
	}

	@Test
	void versionPinMayBeNullForLatest() {
		InventoryLink link = new InventoryLink();
		link.setTargetGlobalId("SA123");
		assertNull(link.getVersionPin());
	}

	@Test
	void shallowCopyCopiesPayloadButNotIdentityOrTimestamps() {
		InventoryLink original = new InventoryLink();
		original.setId(7L);
		original.setTargetGlobalId("SA123v3");
		original.setTargetPrefix(GlobalIdPrefix.SA);
		original.setTargetDbId(123L);
		original.setVersionPin(3L);
		original.setTargetRevisionId(55L);
		original.setRelationType("IsCalibratedBy");
		original.prePersist();

		InventoryLink copy = original.shallowCopy();

		assertNotSame(original, copy);
		assertNull(copy.getId());
		assertNull(copy.getCreatedAt());
		assertNull(copy.getModifiedAt());
		assertEquals("SA123v3", copy.getTargetGlobalId());
		assertEquals(GlobalIdPrefix.SA, copy.getTargetPrefix());
		assertEquals(123L, copy.getTargetDbId());
		assertEquals(3L, copy.getVersionPin());
		assertEquals(55L, copy.getTargetRevisionId());
		assertEquals("IsCalibratedBy", copy.getRelationType());
	}

	@Test
	void shallowCopyPreservesDeletedFlag() {
		InventoryLink original = new InventoryLink();
		original.setTargetGlobalId("SA123");
		original.setTargetPrefix(GlobalIdPrefix.SA);
		original.setTargetDbId(123L);
		original.setRelationType("References");
		original.setDeleted(true);

		assertTrue(original.shallowCopy().isDeleted());
	}

	@Test
	void prePersistSetsCreatedAtAndModifiedAt() {
		InventoryLink link = new InventoryLink();
		assertNull(link.getCreatedAt());
		assertNull(link.getModifiedAt());

		link.prePersist();

		assertNotNull(link.getCreatedAt());
		assertNotNull(link.getModifiedAt());
		assertEquals(link.getCreatedAt(), link.getModifiedAt());
	}

	@Test
	void prePersistPreservesExplicitlySetCreatedAt() {
		InventoryLink link = new InventoryLink();
		Date originalCreatedAt = new Date(1_700_000_000_000L);
		link.setCreatedAt(originalCreatedAt);

		link.prePersist();

		assertEquals(originalCreatedAt, link.getCreatedAt());
		assertNotNull(link.getModifiedAt());
	}

	@Test
	void preUpdateAdvancesModifiedAtWithoutTouchingCreatedAt() throws InterruptedException {
		InventoryLink link = new InventoryLink();
		link.prePersist();
		Date createdAt = link.getCreatedAt();
		Date firstModifiedAt = link.getModifiedAt();

		Thread.sleep(5);
		link.preUpdate();

		assertEquals(createdAt, link.getCreatedAt());
		assertFalse(link.getModifiedAt().before(firstModifiedAt));
	}

}
