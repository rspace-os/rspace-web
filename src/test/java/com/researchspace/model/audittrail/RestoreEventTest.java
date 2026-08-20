package com.researchspace.model.audittrail;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.researchspace.model.core.Person;

public class RestoreEventTest {
	
	RestoreEvent event;
	Person user;
	AuditTrailTestObject record;

	@BeforeEach
	public void setUp() {
		user = TestFactory.createAnyUser("any");
		record = new AuditTrailTestObject();
				
	}

	@AfterEach
	public void tearDown() {
	}

	@Test
	public void testGetAuditAction() {
		event = new RestoreEvent(user, record);
		Assertions.assertEquals(AuditAction.RESTORE, event.getAuditAction());
	}

	@Test
	public void testRestoreEventUserObject() {
		event = new RestoreEvent(user, record);
		Assertions.assertEquals(RestoreType.DELETION, event.getRestoreType());
	}

	@Test
	public void testRestoreEventUserObjectInt() {
		event = new RestoreEvent(user, record,34);
		Assertions.assertEquals(RestoreType.REVISION, event.getRestoreType());
	}

}
