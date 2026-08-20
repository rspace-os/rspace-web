package com.researchspace.model.permissions;

import static com.researchspace.model.permissions.AbstractEntityPermissionAdapter.FORM_PROP_NAME;
import static com.researchspace.model.permissions.FormPermissionAdapter.GLOBAL_PROPERTY_NAME;
import static com.researchspace.model.permissions.FormPermissionAdapter.GROUP_PROPERTY_NAME;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.researchspace.model.record.RSForm;

import com.researchspace.model.record.TestFactory;

public class FormPermissionAdapterTest {
	private static final String TEMPLATE_NAME = "t1";
	FormPermissionAdapter adapter;
	RSForm anyForm;
	@Before
	public void setUp() throws Exception {
		anyForm = TestFactory.createAnyForm(TEMPLATE_NAME);
		adapter = new FormPermissionAdapter(anyForm);
		adapter.setAction(PermissionType.READ);
		adapter.setDomain(PermissionDomain.FORM);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testHasProperty() {
	
		assertTrue(adapter.hasProperty(FORM_PROP_NAME));
		assertTrue(adapter.hasProperty(GLOBAL_PROPERTY_NAME));
		assertTrue(adapter.hasProperty(GROUP_PROPERTY_NAME));
	}
	
	@Test
	public void testpermissions() {
		
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.FORM,PermissionType.READ);
		cbp.addPropertyConstraint(new PropertyConstraint("template", TEMPLATE_NAME));
		assertTrue(cbp.implies(adapter));
	}
	
	@Test
	public void testGlobalAccess() {
		
		
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.FORM,PermissionType.READ);
		cbp.addPropertyConstraint(new PropertyConstraint(GLOBAL_PROPERTY_NAME, "true"));
		// by default ,template is not wrl-accessible
		assertFalse(cbp.implies(adapter));
		// now allow world access
		anyForm.getAccessControl().setWorldPermissionType(PermissionType.READ);
		
		assertTrue(cbp.implies(adapter));
	}
	
	@Test
	public void testGroupAccess() {
		
		
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.FORM,PermissionType.WRITE);
		cbp.addPropertyConstraint(new PropertyConstraint(GROUP_PROPERTY_NAME, "true"));
		// by default ,template is not grp-accessible
		assertFalse(cbp.implies(adapter));
		// now allow world access
		anyForm.getAccessControl().setGroupPermissionType(PermissionType.WRITE);
		adapter.setAction(PermissionType.WRITE);
		assertTrue(cbp.implies(adapter));
	}
	
	

	

}
