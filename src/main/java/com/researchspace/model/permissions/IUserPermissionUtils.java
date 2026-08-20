package com.researchspace.model.permissions;

import com.researchspace.model.User;

/**
 * Extends permission utils to include more complex permission checking relating 
 *  to altering user roles
 */
public interface IUserPermissionUtils {
	
	/**
	 * Does subject have permission to alter roles in a group?
	 * @param admin
	 * @param newPI
	 * @param msgOnFailure
	 * @return <code>true</code> if authorised, <code>false</code> otherwise
	 */
	public void assertHasPermissionsOnTargetUser(User admin, User newPI, String msgOnFailure) ;

	public boolean isTargetUserValidForSubjectRole(User admin, String targetUser) ;

}
