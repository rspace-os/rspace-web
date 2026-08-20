package com.researchspace.model;

import org.apache.shiro.authz.Permission;

/**
 * Abstract representation of a User or a Group. <br/>
 * This largely corresponds to a '<em>Subject</em>' but is named differently to
 * avoid confusion with the security infrastructure.
 */
public interface UserOrGroup {

	/**
	 * Get a unique identifier for the object. In the cases of a User, this is
	 * likely to be a username or other string used for authentication.
	 * 
	 * @return
	 */
	String getUniqueName();

	/**
	 * implies may have subgroups
	 */
	boolean isGroup();

	/**
	 * implies leaf-node
	 * 
	 * @return
	 */
	boolean isUser();

	/**
	 * Permission test to see if this user or group's permissions imply the
	 * argument permission
	 * 
	 * @param p
	 *            A {@link Permission} to test.
	 * @param inherit
	 *            If <code>true</code>, this object's ancestors' groups'
	 *            permissions will also be tested; and if any return
	 *            <code>true</code>, this method will return <code>true</code>.
	 *            If set to <code>false</code>, only this object's permissions
	 *            will be used.
	 * @return
	 */
	boolean isPermitted(Permission p, boolean inherit);

}
