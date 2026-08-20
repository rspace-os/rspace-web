package com.researchspace.model.record;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.RecordGroupSharing;
import org.junit.jupiter.api.Test;

import com.researchspace.Constants;
import com.researchspace.model.EcatImage;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintPermissionResolver;

public class BaseRecordTest {
	private final User anyUser = TestFactory.createAnyUser("any");

	@Test
	public void identifiedByGlobalIdCheck() {
		Folder folder = TestFactory.createAFolder("testFolder", anyUser);
		folder.setId(11L);
		assertTrue(folder.isIdentifiedByOid(folder.getOid()));

		StructuredDocument sd = TestFactory.createAnySD();
		sd.setId(12L);
		assertTrue(sd.isIdentifiedByOid(sd.getOid()));
		assertTrue(sd.isIdentifiedByOid(sd.getOidWithVersion()));
		assertFalse(sd.isIdentifiedByOid(folder.getOid()));

		EcatImage img = TestFactory.createEcatImage(11L);
		assertTrue(img.isIdentifiedByOid(img.getOid()));
		assertTrue(img.isIdentifiedByOid(img.getOidWithVersion()));
		assertFalse(img.isIdentifiedByOid(folder.getOid()));
	}

	@Test
	public void isPublished() {
		User pi = TestFactory.createAnyUserWithRole("pi", Constants.PI_ROLE);
		Group group = TestFactory.createAnyGroup(pi, anyUser);
		Record record = TestFactory.createAnyRecord(anyUser);

		ConstraintPermissionResolver parser = new ConstraintPermissionResolver();

		// Initially the ACL is empty
		assertFalse(record.isShared());

		record.getSharingACL().addACLElement(
				anyUser, parser.resolvePermission("RECORD:WRITE:"));

		// Unshared records have ACLs with different permissions for the owner
		assertFalse(record.isShared());

		// sharing with anonymous user does not count towards isShared status
		ACLElement anonymousSharedElement = new ACLElement(
				RecordGroupSharing.ANONYMOUS_USER,
				parser.resolvePermission("RECORD:READ:"));
		record.getSharingACL().addACLElement(anonymousSharedElement);
		assertFalse(record.isShared());
		assertTrue(record.isPublished());

	}

	@Test
	public void isShared() {
		User pi = TestFactory.createAnyUserWithRole("pi", Constants.PI_ROLE);
		Group group = TestFactory.createAnyGroup(pi, anyUser);
		Record record = TestFactory.createAnyRecord(anyUser);

		ConstraintPermissionResolver parser = new ConstraintPermissionResolver();

		// Initially the ACL is empty
		assertFalse(record.isShared());

		record.getSharingACL().addACLElement(
				anyUser, parser.resolvePermission("RECORD:WRITE:"));

		// Unshared records have ACLs with different permissions for the owner
		assertFalse(record.isShared());
		// sharing with anonymous user does not count towards isShared status
		ACLElement anonymousSharedElement = new ACLElement(
				RecordGroupSharing.ANONYMOUS_USER,
				parser.resolvePermission("RECORD:READ:"));
		record.getSharingACL().addACLElement(anonymousSharedElement);
		assertFalse(record.isShared());

		ACLElement element = new ACLElement(
				group.getUniqueName(),
				parser.resolvePermission("RECORD:READ:"));
		record.getSharingACL().addACLElement(element);

		// Shared records have ACLs with multiple groups or users with permissions
		assertTrue(record.isShared());
	}
	
	@Test
	public void invariants() {
		Record record = TestFactory.createAnyRecord(anyUser);
		assertNotNull(record.getModificationDate());
		assertNotNull(record.getCreationDate());
		assertFalse(record.isFromImport());

	}
}
