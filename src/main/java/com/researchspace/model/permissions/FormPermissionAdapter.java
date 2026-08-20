package com.researchspace.model.permissions;

import java.util.HashSet;
import java.util.Set;

import com.researchspace.model.AccessControl;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.AbstractForm;

/**
 * Adapts a RSForm to a permission.
 */
public class FormPermissionAdapter extends AbstractEntityPermissionAdapter {

	public static final String GROUP_PROPERTY_NAME = "group";
	static final String GLOBAL_PROPERTY_NAME = "global";
	private AbstractForm form;

	/**
	 * Public constructor that also sets Permission Domain to 'Template'.
	 * 
	 * @param template
	 */
	public FormPermissionAdapter(AbstractForm template) {
		super();
		this.form = template;
		this.setDomain(PermissionDomain.FORM);
	}

	@Override
	public Long getId() {
		return form.getId();
	}

	@Override
	protected  Object getEntity() {
		return form;
	}

	public boolean hasProperty(String propertyName) {
		boolean rc = super.hasProperty(propertyName);
		if (!rc) {
			if (propertyName.equals(FORM_PROP_NAME)) {
				rc = true;
			} else if (propertyName.equals(GLOBAL_PROPERTY_NAME)) {
				rc = true;
			} else if (propertyName.equals(GROUP_PROPERTY_NAME)) {
				rc = true;
			}
		}
		return rc;
	}

	@Override
	protected  PropertyConstraint handleSpecialProperties(String propertyName) {
		if (propertyName.equals(FORM_PROP_NAME)) {
			return new PropertyConstraint(FORM_PROP_NAME, form.getName());
		} else if (propertyName.equals("owner")) {
			return new PropertyConstraint("owner", form.getOwner().getUsername());
		} else if (propertyName.equals(GLOBAL_PROPERTY_NAME)) {
			AccessControl ac = form.getAccessControl();
			if (ac != null && AccessControl.isAllowed(ac.getWorldPermissionType(), getAction())) {
				return new PropertyConstraint(GLOBAL_PROPERTY_NAME, Boolean.toString(true));
			} else {
				return new PropertyConstraint(GLOBAL_PROPERTY_NAME, Boolean.toString(false));
			}
		} else if (propertyName.equals(GROUP_PROPERTY_NAME)) {
			AccessControl ac = form.getAccessControl();
			if (ac != null && AccessControl.isAllowed(ac.getGroupPermissionType(), getAction())) {
				return new PropertyConstraint(GROUP_PROPERTY_NAME, Boolean.toString(true));
			} else {
				return new PropertyConstraint(GROUP_PROPERTY_NAME, Boolean.toString(false));
			}
		}
		return null;
	}

	@Override
	public Set<GroupConstraint> getGroupConstraints() {
		Set<GroupConstraint> grps = new HashSet<>();
		User u = form.getOwner();
		for (Group ug : u.getGroups()) {
			grps.add(new GroupConstraint(ug.getUniqueName()));
		}
		return grps;
	}

}
