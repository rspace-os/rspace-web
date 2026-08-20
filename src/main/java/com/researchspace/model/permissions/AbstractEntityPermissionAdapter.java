package com.researchspace.model.permissions;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.shiro.authz.Permission;

import com.researchspace.model.IEntityPermission;
import com.researchspace.model.User;

public abstract class AbstractEntityPermissionAdapter implements IEntityPermission {

	public static final String FORM_PROP_NAME = "form";

	@Getter
	@Setter
	private PermissionDomain domain = PermissionDomain.RECORD;

	@Getter
	@Setter
	private PermissionType action;

	@Override
	public boolean implies(Permission p) {
		throw new UnsupportedOperationException();
	}

	@Override
	public LocationConstraint getLocationConstraint() {
		return null;
	}

	/**
	 * Default implementation returns <code>null</code>; subclasses can
	 * override.
	 */
	@Override
	public Set<CommunityConstraint> getCommunityConstraints() {
		return null;
	}

	protected abstract Object getEntity();

	@Override
	public boolean hasProperty(String propertyName) {
		try {
			BeanUtils.getProperty(getEntity(), propertyName);

		} catch (NoSuchMethodException|IllegalAccessException|InvocationTargetException e) {
			return false;
		}
		return true;
	}

	@Override
	public PropertyConstraint getPropertyConstraintForProperty(String propertyName) {
		PropertyConstraint pc = handleSpecialProperties(propertyName);
		String value = null;
		if (pc != null) {
			return pc;
		}

		try {
			value = BeanUtils.getProperty(getEntity(), propertyName);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			return null;
		}
		return new PropertyConstraint(propertyName, value);
	}

	protected abstract PropertyConstraint handleSpecialProperties(String propertyName);

	/**
	 * This performs a permissions check in the 'reverse' way that regular
	 * permissions are checked. The regular way consults an object's properties
	 * to see if they match permissions assigned to act a user.
	 * <p/>
	 * The ACL, though, is set on the entity, and queries the properties of the
	 * user to see if they match the ACL list. This default implementation
	 * returns <code>false</code>, subclasses can override if they support ACLs.
	 * See {@link RecordSharingACL} as an example.
	 * 
	 * @param user
	 *            - the current authenticated user.
	 */
	public boolean checkACL(User user) {
		return false;
	}

	@Override
	public String toString() {
		return "AbstractEntityPermissionAdapter [domain=" + domain + ", action=" + action + "]";
	}

}
