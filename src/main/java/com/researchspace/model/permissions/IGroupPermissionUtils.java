package com.researchspace.model.permissions;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;

/**
 * Extends permission utils to include more complex permission checking relating 
 *  to group editing
 */
public interface IGroupPermissionUtils {
	
	/**
	 * Does subject have permission to alter roles in a group?
	 * @param group
	 * @param subject
	 * @param toChange optional argument of the user to change
	 * @return <code>true</code> if authorised, <code>false</code> otherwise
	 */
	boolean subjectCanAlterGroupRole(Group group, User subject, User toChange);
	
	/**
	 * Sets the read/edit global permissions for PIs
	 * @param group
	 * @param subject
	 * @param canPIEditAll
	 */
	UserGroup setReadOrEditAllPermissionsForPi (Group group, User subject, boolean canPIEditAll);

	/**
	 * Boolean test as to whether <code>exporter</code> has permissions to export the given group.
	 * @param exporter
	 * @param group
	 * @return <code>true</code> if permitted, <code>false</code> otherwise
	 */
	boolean userCanExportGroup(User exporter, Group group);
	
	/**
	 * Asserts whether <code>subject</code> has permission to remove <code>leaveCandidateUsername</code>
	 *  from group <code>grp</code>
	 * @param leaveCandidateUsername
	 * @param subject
	 * @param grp the group
	 * @return The User to be removed, if subject is authorized.
	 * @throws AuthorizationException if subject is not authorized
	 */
	User assertLeaveGroupPermissions(String leaveCandidateUsername, User subject, Group grp);

	/**
	 * Whether PI can edit all work in labGroup
	 */
	boolean piCanEditAllWorkInLabGroup(Group group);

}
