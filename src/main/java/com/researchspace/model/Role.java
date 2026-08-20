package com.researchspace.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.shiro.authz.Permission;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.researchspace.Constants;
import com.researchspace.model.permissions.ConstraintBasedPermission;

@Entity
@Table(name = "roles", indexes = { @Index(columnList = "name", name = "idx_roles_name") })
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@XmlType()
@XmlAccessorType(XmlAccessType.NONE)
public class Role implements Serializable, Permissable {

	private static final long serialVersionUID = 8435707597327125561L;

	private static final String[] VALID_ROLES = new String[] { Constants.USER_ROLE, Constants.PI_ROLE,
			Constants.ADMIN_ROLE, Constants.SYSADMIN_ROLE, Constants.ANONYMOUS_ROLE, Constants.GROUP_OWNER_ROLE };

	/**
	 * MUST be declared after valid roles
	 */
	public static final Role USER_ROLE = new Role(Constants.USER_ROLE);

	/** */
	public static final Role PI_ROLE = new Role(Constants.PI_ROLE);

	/**
	 * Role for RSpace community admin
	 */
	public static final Role ADMIN_ROLE = new Role(Constants.ADMIN_ROLE);

	/**
	 * Role for Project Group owner
	 */
	public static final Role GROUP_OWNER = new Role(Constants.GROUP_OWNER_ROLE);

	/**
	 * Global sysadmin role
	 */
	public static final Role SYSTEM_ROLE = new Role(Constants.SYSADMIN_ROLE);

	/**
	 * Anonymous role used for public document sharing outside RSpace.
	 */
	public static final Role ANONYMOUS_ROLE = new Role(Constants.ANONYMOUS_ROLE);

	/**
	 * Valid role names
	 */
	public static String[] getValidRoles() {
		return Arrays.copyOf(VALID_ROLES, VALID_ROLES.length);
	}

	/**
	 * Checks supplied string against list of valid roles, case-sensitive.
	 * 
	 * @param roleString
	 * @return <code>true</code> if the argument string is a defined role
	 */
	public static boolean isRoleStringIdentifiable(String roleString) {
		return ArrayUtils.contains(VALID_ROLES, roleString);
	}

	private Long id;

	private String name;

	private String description;

	private PermissionHandler permHandler;

	protected Role() {
		permHandler = new PermissionHandler();
	}

	/**
	 * @param roleName
	 *            A valid role name as determined by isRoleStringIdentifiable()
	 * @throws IllegalArgumentException
	 *             if <code>roleName</code> is invalid
	 */
	public Role(String roleName) {
		this();
		if (!isRoleStringIdentifiable(roleName)) {
			throw new IllegalArgumentException("[" + name + "] is not a valid role name]");
		}
		this.name = roleName;
	}

	@Override
	public String toString() {
		return "Role [name=" + name + ", description=" + description + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Role other = (Role) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	@Basic(optional = false)
	@Column(length = 100)
	@XmlID
	@XmlAttribute
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	@Basic(optional = false)
	@Column(length = 255)
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@ElementCollection
	@org.hibernate.annotations.Cache(
		    usage = CacheConcurrencyStrategy.READ_WRITE
		)
	Set<String> getPermissionStrings() {
		return permHandler.getPermissionStrings();
	}

	@Transient
	public Set<Permission> getPermissions() {
		return permHandler.getPermissions();
	}

	void setPermissionStrings(Set<String> permissionStrings) {
		permHandler.setPermissionStrings(permissionStrings);
	}

	/**
	 * Public API to add a {@link ConstraintBasedPermission} to this user-group.
	 * 
	 * @param p
	 *            A {@link Permission} object
	 */
	public void addPermission(ConstraintBasedPermission p) {
		permHandler.addPermission(p);
	}

	/**
	 * Public API to remove a {@link Permission} to htis group.
	 * 
	 * @param p
	 *            A {@link Permission} object
	 */
	public void removePermission(Permission p) {
		permHandler.removePermission(p);
	}

}
