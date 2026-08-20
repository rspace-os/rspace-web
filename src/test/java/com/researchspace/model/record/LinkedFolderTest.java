package com.researchspace.model.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;

class LinkedFolderTest {
	User u1,u2;
	Folder u1homeFolder,u2HomeFolder;
	@BeforeEach
	void before (){
		 u1 = TestFactory.createAnyUser("u1");
		 u1homeFolder = TestFactory.createAFolder("f1", u1);
		 u2 = TestFactory.createAnyUser("u2");
		 u2HomeFolder = TestFactory.createAFolder("f2", u2);
		 u1homeFolder.addType(RecordType.ROOT);
		 u2HomeFolder.addType(RecordType.ROOT);
	}
	
	@Test
	void BasicCyclePrevented() {
		u1homeFolder.addChild(u2HomeFolder, u1, true);
		Assertions.assertThrows(IllegalAddChildOperation.class, ()->u2HomeFolder.addChild(u1homeFolder, u2, true));
	}
	
	@Test
	void linkedFolder () {
		DecoratedFolder linked1 = new DecoratedFolder(u1homeFolder);
		DecoratedFolder linked2 = new DecoratedFolder(u2HomeFolder);
		
		u1homeFolder.getSharingACL().addACLElement(readACLForUser(u1));
		u2HomeFolder.getSharingACL().addACLElement(readACLForUser(u2));
		u1homeFolder.addChild(linked2, u1);
		u2HomeFolder.addChild(linked1, u2);
		
		assertTrue(u1homeFolder.getSharingACL().isPermitted(u2, PermissionType.READ));
		assertTrue(u2HomeFolder.getSharingACL().isPermitted(u1, PermissionType.READ));
		assertEquals(u2HomeFolder, linked1.getParent());
		assertEquals(linked1, u2HomeFolder.getChildrens().iterator().next());

		u1homeFolder.removeChild(linked2);
		u2HomeFolder.removeChild(linked1);
		
		assertFalse(u1homeFolder.getSharingACL().isPermitted(u2, PermissionType.READ));
		assertFalse(u2HomeFolder.getSharingACL().isPermitted(u1, PermissionType.READ));
	}

	private ACLElement readACLForUser (User user) {
		return new ACLElement(user.getUsername(), readPer());
	}
	private ConstraintBasedPermission readPer() {
		return new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
	}

}
