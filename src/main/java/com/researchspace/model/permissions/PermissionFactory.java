package com.researchspace.model.permissions;

import java.util.Set;

import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;

/**
 * Programmatically creates permissions for groups/users/ shared objects
 */
public interface PermissionFactory {

	/**
	 * Creates a set of default permissions that a PI will have in a group.
	 * 
	 * @param group
	 * @return
	 */
	Set<ConstraintBasedPermission> createDefaultPermissionsForGroupPI(Group group);

	/**
	 * Default set of permissions for a group administrator. Creates a set of
	 * default permissions that an Administrator will have in a group.
	 * 
	 * @param group
	 * @return
	 */
	Set<ConstraintBasedPermission> createDefaultPermissionsForGroupAdmin(Group group);

	/**
	 * Creates a set of default permissions that a PI will have in a
	 * collaboration group. At the moment, it returns a set of permissions to be
	 * able to manage the collaboration group.
	 * 
	 * @param group
	 * @return
	 */
	Set<ConstraintBasedPermission> createDefaultPermissionsForCollabGroupPI(Group group);

	/**
	 * Default set of permissions for a collaboration group administrator. It
	 * returns a set of permissions to be able to manage the collaboration group
	 * and be able to send requests :
	 * <ul>
	 * REQUEST_EXTERNAL_SHARE
	 * </ul>
	 * <ul>
	 * REQUEST_JOIN_LAB_GROUP
	 * </ul>
	 * <ul>
	 * REQUEST_JOIN_EXISTING_COLLAB_GROUP
	 * </ul>
	 * 
	 * 
	 * @param group
	 * @return
	 */
	Set<ConstraintBasedPermission> createDefaultPermissionsForCollabGroupAdmin(Group group);

	/**
	 * Default set of permissions for everyone in the group.
	 * 
	 * @param group
	 * @return
	 */
	Set<ConstraintBasedPermission> createDefaultGlobalGroupPermissions(Group group);

	/**
	 * Default permissions for Project Group Owners. Permissions to manage the group and
	 * be able to send requests for other users to join the project group.
	 * @param group Project group to create the permissions for
	 * @return the set of permissions for the project group owner
	 */
	Set<ConstraintBasedPermission> createDefaultPermissionsForProjectGroupOwner(Group group);

	/**
	 * Creates an ID-based permission for the given domain and action type
	 * 
	 * @param domain
	 * @param type
	 * @param id
	 * @return The created permission.
	 */
	ConstraintBasedPermission createIdPermission(PermissionDomain domain, PermissionType type, Long id);

	/**
	 * Sets up AccessControlList for a new shared group folder.
	 * 
	 * @param grp
	 * @param grpFolder
	 */
	void setUpACLForGroupSharedRootFolder(Group grp, Folder grpFolder);

	/**
	 * Sets up ACL for a user's root folder that is now shared with a PI.
	 * 
	 * @param grp
	 * @param userRoot
	 */
	void setUpACLForUserRootInPIFolder(Group grp, Folder userRoot);

	/**
	 * Sets up ACL for a folder that will be a subfolder of individual shared
	 * folder i.e., the root of user-pair sharing tree.
	 * 
	 * @param sharer
	 * @param sharee
	 * @param grpFolder
	 */
	void setUpACLForIndividSharedFolder(User sharer, User sharee, Folder grpFolder);

	/**
	 * Sets up ACLs on a user's root folder.
	 * 
	 * @param user
	 * @param rootForUser
	 */
	void setUpACLForUserRoot(User user, Folder rootForUser);

	/**
	 * Creates a community-edit permissions and record-read permissions
	 * 
	 * @param admin
	 *            The user to grant the permission to
	 * @param community
	 *            the community the admin can edit
	 * @return the newly created permissions
	 */
	Set<ConstraintBasedPermission> createCommunityPermissionsForAdmin(User admin, Community community);

	/**
	 * Sets permissions on template folder
	 * 
	 * @param templateFolder
	 * @param any
	 */
	void setUpACLForIndividualTemplateFolder(Folder templateFolder, User any);

	/**
	 * Adds template sub-folder permissions to folder/template in Templates
	 * folder
	 * 
	 * @param templateChild
	 * @param subject
	 */
	void setUpACLForTemplateFolderChildPermissions(BaseRecord templateChild, User subject);
	
	/**
	 * Sets permissions on an Inbox folder
	 * 
	 * @param inboxFolder
	 * @param subject
	 */
	void setUpAclForIndividualInboxFolder(Folder inboxFolder, User subject);
	
	/**
	 * Sets permissions sub-folder permissions to folder in ApiInbox or Imports
	 * 
	 * @param childRecord
	 * @param subject
	 */
	void setUpAclForInboxFolderChildPermissions (BaseRecord childRecord, User subject);

}
