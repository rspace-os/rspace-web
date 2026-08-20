package com.researchspace.model.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.record.TestFactory;

public class ACLElementTest {
	
	ACLElement el1, el2, el3,el4;
	ConstraintPermissionResolver permResolver;

	@Before
	public void setUp() throws Exception {
		permResolver = new ConstraintPermissionResolver();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetAsString() {
		el1 = new ACLElement("G1", permResolver.resolvePermission("RECORD:CREATE:"));
		assertEquals("G1=RECORD:CREATE:",el1.getAsString());
	}
	
	@Test
	public void testConstructorDoesNotAllowNullEmptyOrBlankValues() {
		String [] invalidGrpNames = new String []{null,""," "};
		
		for (String grp: invalidGrpNames){
			
				try {
					ACLElement acl=new ACLElement(grp, permResolver.resolvePermission("RECORD:CREATE:"));
					fail("Should not allow construction of " + acl);
				}catch(IllegalArgumentException e){
				    // this is what we want	
				}
			}
			
		
	}

	@Test
	public void testEqualsObject() {
		ConstraintBasedPermission cbp1=permResolver.resolvePermission("RECORD:CREATE");
		ConstraintBasedPermission cbp2=permResolver.resolvePermission("RECORD:COPY");
		el1 = new ACLElement("G1",cbp1 );
		el2 = new ACLElement("G1", cbp1);
		el3= new ACLElement("G1", cbp2);
		el4= new ACLElement("G2", cbp2);
		assertEquals(el1,el2);
		assertFalse(el1.equals(el3));
		assertFalse(el3.equals(el4));
	}
	
	@Test
	public void testRoleRestrictedACLElement (){
		ConstraintBasedPermission cbp=permResolver.resolvePermission("RECORD:CREATE:");
		
		Group g = new Group("any",TestFactory.createAnyUser("any"));
		ACLElement el=ACLElement.createRoleRestrictedGroupACL(g, cbp);
		assertEquals("any=RECORD:CREATE:",el.getAsString());
		
		ACLElement el2=ACLElement.createRoleRestrictedGroupACL(g,cbp, RoleInGroup.PI);
		assertEquals("any[PI]=RECORD:CREATE:",el2.getAsString());
		
		ACLElement el3=ACLElement.createRoleRestrictedGroupACL(g, cbp, RoleInGroup.PI,RoleInGroup.RS_LAB_ADMIN);
		assertEquals("any[PI,RS_LAB_ADMIN]=RECORD:CREATE:",el3.getAsString());
		
	}

}
