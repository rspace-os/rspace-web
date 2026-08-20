package com.researchspace.model.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.Period;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.TestFactory;

public class AuditedRecordTest {

	AuditedRecord ar1, ar2, ar3;
	Folder r1, r2, r3;
	User u;

	@BeforeEach
	public void setUp() throws Exception {
		u = TestFactory.createAnyUser("any");
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void equalsAndHashCode() throws InterruptedException {
		r1 = TestFactory.createAFolder("any", u);
		Thread.sleep(1); // ensure records are unequal
		r2 = TestFactory.createAFolder("any2", u);
		r1.setId(1L);
		r2.setId(2L);
		ar1 = new AuditedRecord(r1, 1);
		ar2 = new AuditedRecord(r2, 1);
		ar3 = new AuditedRecord(r2, 1);

		assertTrue(ar2.equals(ar3));
		assertTrue(ar2.hashCode() == ar3.hashCode());

		assertFalse(ar2.equals(ar1));
		assertFalse(ar2.hashCode() == ar1.hashCode());
		// test set semantics
		Set<AuditedRecord> set = new HashSet<>();
		set.add(ar1);
		set.add(ar2);
		set.add(ar3);
		assertEquals(2, set.size());

		AuditedRecord ar4 = new AuditedRecord(r1, 2);
		assertFalse(ar4.equals(ar1));
	}
	
	@Test
	public void sortByDeletionDate() throws InterruptedException {
		r1 = TestFactory.createAFolder("any", u);
		r1.setId(1L);
		Thread.sleep(1); // ensure records are unequal
		r2 = TestFactory.createAFolder("any2", u);
		r2.setId(2L);
		Thread.sleep(1); // ensure records are unequal
		r3 = TestFactory.createAFolder("any3", u);
		r3.setId(2L);
		
		// sort when all deletion dates are null does not throw NPE
		ar1 = new AuditedRecord(r1, 1, RevisionType.MOD, null);
		ar2 = new AuditedRecord(r2, 1,RevisionType.MOD, null);
		ar3 = new AuditedRecord(r3,1, RevisionType. MOD, null);
		
		List<AuditedRecord> ars = TransformerUtils.toList(ar1, ar2, ar3);
		Collections.sort(ars, AuditedRecord.DELETED_COMPARATOR);
		Instant now = Instant.now();
		
		Date middle = new Date (now.toEpochMilli());
		Date earliest = new Date (now.minus(Period.ofDays(1)).toEpochMilli());
		Date latest = new Date ((now.plus(Period.ofDays(1)).toEpochMilli()));
		
		ar1 = new AuditedRecord(r1, 1, RevisionType.MOD, middle);
		ar2 = new AuditedRecord(r2, 1, RevisionType.MOD, earliest);
		List<AuditedRecord> ars2 = TransformerUtils.toList(ar1, ar2, ar3);

		// should be null (ar3), ar2, ar1
		Collections.sort(ars2, AuditedRecord.DELETED_COMPARATOR);
		assertEquals(TransformerUtils.toList(ar3, ar2, ar1), ars2);

		// now all are non-null
		ar1 = new AuditedRecord(r1, 1, RevisionType.MOD, middle);
		ar2 = new AuditedRecord(r2, 1, RevisionType.MOD, earliest);
		ar3 = new AuditedRecord(r3,1, RevisionType. MOD, latest);
		List<AuditedRecord> ars3 = TransformerUtils.toList(ar1, ar2, ar3);
		Collections.sort(ars3, AuditedRecord.DELETED_COMPARATOR);
		assertEquals(TransformerUtils.toList(ar2,ar1,ar3), ars3);
	}

}
