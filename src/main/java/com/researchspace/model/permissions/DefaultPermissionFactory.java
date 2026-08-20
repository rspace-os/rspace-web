package com.researchspace.model.permissions;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.apache.shiro.authz.AuthorizationException;

import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;

/**
 * Hard-coded default permissions for groups and group roles. <br/>
 * Shouldn't use DB/ Spring so can be used throughout application wherever
 * permissions need to be added in a consistent manner.
 */
public class DefaultPermissionFactory implements PermissionFactory {

	@Override
	public Set<ConstraintBasedPermission> createDefaultPermissionsForGroupPI(Group group) {
		GroupConstraint groupConstraint = createGroupConstraintForGroup(group);
		Set<ConstraintBasedPermission> rc = new HashSet<>();

		// Read share export all docs associated with the group.b
		ConstraintBasedPermission readAllRecords = new ConstraintBasedPermission(PermissionDomain.RECORD,
				PermissionType.READ);
		readAllRecords.addPermissionType(PermissionType.EXPORT);
		readAllRecords.addPermissionType(PermissionType.SHARE);
		readAllRecords.setGroupConstraint(groupConstraint);
		rc.add(readAllRecords);

		addGroupLeaderPermissions(rc, groupConstraint);
		// Request Join LabGroup
		ConstraintBasedPermission inviteNewUser = addRequestJoinLabGroupPermission(rc);
		inviteNewUser.setGroupConstraint(groupConstraint);
		rc.add(inviteNewUser);
		// Request Join Existing CollabGroup
		addRequestJoinExistingCollabGroupPermission(rc);

		return rc;
	}

	/*
	 *	default permissions for lab and collab groups pis, and project group owners
	 */
	private void addGroupLeaderPermissions(Set<ConstraintBasedPermission> rc, GroupConstraint groupConstraint) {
		// Read, write, export, share, delete forms
		ConstraintBasedPermission accessAllForms = createAccessAllForms(rc);
		accessAllForms.setGroupConstraint(groupConstraint);
		rc.add(accessAllForms);

		// Create forms
		ConstraintBasedPermission createForms = createFormPermission();
		rc.add(createForms);

		// Edit group members.
		ConstraintBasedPermission editGroup = createEditGroupPermissions();
		editGroup.setGroupConstraint(groupConstraint);
		rc.add(editGroup);

		// Request external share
		addRequestExternalSharePermission(rc);
	}

	@Override
	public Set<ConstraintBasedPermission> createDefaultPermissionsForGroupAdmin(Group group) {
		GroupConstraint groupConstraint = createGroupConstraintForGroup(group);
		Set<ConstraintBasedPermission> rc = new HashSet<>();

		addGroupLeaderPermissions(rc, groupConstraint);
		// Request Join LabGroup
		ConstraintBasedPermission inviteNewUser = addRequestJoinLabGroupPermission(rc);
		inviteNewUser.setGroupConstraint(groupConstraint);
		rc.add(inviteNewUser);
		return rc;
	}

	@Override
	public Set<ConstraintBasedPermission> createDefaultPermissionsForCollabGroupPI(Group group) {
		GroupConstraint groupConstraint = createGroupConstraintForGroup(group);
		Set<ConstraintBasedPermission> rc = new HashSet<>();

		// Edit group members.
		ConstraintBasedPermission editGroup = createEditGroupPermissions();
		editGroup.setGroupConstraint(groupConstraint);
		rc.add(editGroup);

		return rc;
	}

	@Override
	public Set<ConstraintBasedPermission> createDefaultPermissionsForCollabGroupAdmin(Group group) {
		GroupConstraint groupConstraint = createGroupConstraintForGroup(group);
		Set<ConstraintBasedPermission> rc = new HashSet<>();

		// Edit group members.
		ConstraintBasedPermission editGroup = createEditGroupPermissions();
		editGroup.setGroupConstraint(groupConstraint);
		rc.add(editGroup);

		// Request external share
		addRequestExternalSharePermission(rc);
		// Request Join LabGroup
		addRequestJoinLabGroupPermission(rc);
		// Request Join Existing CollabGroup
		addRequestJoinExistingCollabGroupPermission(rc);

		return rc;
	}

	@Override
	public Set<ConstraintBasedPermission> createDefaultPermissionsForProjectGroupOwner(Group group){
		GroupConstraint groupConstraint = createGroupConstraintForGroup(group);
		Set<ConstraintBasedPermission> rc = new HashSet<>();

		addGroupLeaderPermissions(rc, groupConstraint);

		// Request another user joins a project group
		ConstraintBasedPermission inviteNewUser = createRequestPermissionForMsgType(MessageType.REQUEST_JOIN_PROJECT_GROUP);
		inviteNewUser.setGroupConstraint(groupConstraint);
		rc.add(inviteNewUser);
		return rc;
	}

	/**
	 * @return
	 */
	private ConstraintBasedPermission createFormPermission() {
		ConstraintBasedPermission createForms = new ConstraintBasedPermission(PermissionDomain.FORM,
				PermissionType.CREATE);
		return createForms;
	}

	/**
	 * @return
	 */
	private ConstraintBasedPermission createEditGroupPermissions() {
		ConstraintBasedPermission editGroup = new ConstraintBasedPermission(PermissionDomain.GROUP,
				PermissionType.READ);
		editGroup.addPermissionType(PermissionType.WRITE);
		return editGroup;
	}

	/**
	 * @param rc
	 * @return
	 */
	private ConstraintBasedPermission createAccessAllForms(Set<ConstraintBasedPermission> rc) {
		ConstraintBasedPermission accessAllForms = new ConstraintBasedPermission(PermissionDomain.FORM,
				PermissionType.READ);
		accessAllForms.addPermissionType(PermissionType.WRITE);
		accessAllForms.addPermissionType(PermissionType.SHARE);
		accessAllForms.addPermissionType(PermissionType.DELETE);
		rc.add(accessAllForms);
		return accessAllForms;
	}

	/**
	 * Adds permission to send invitation
	 * 
	 * @param rc
	 */
	private void addRequestExternalSharePermission(Set<ConstraintBasedPermission> rc) {
		ConstraintBasedPermission requestExternalShare = createRequestPermissionForMsgType(
				MessageType.REQUEST_EXTERNAL_SHARE);
		rc.add(requestExternalShare);
	}

	/**
	 * Adds permission to invite another user to join the lab group.
	 * 
	 * @param rc
	 */
	private ConstraintBasedPermission addRequestJoinLabGroupPermission(Set<ConstraintBasedPermission> rc) {
		ConstraintBasedPermission requestJoinLabGroup = createRequestPermissionForMsgType(
				MessageType.REQUEST_JOIN_LAB_GROUP);
		return requestJoinLabGroup;
	}

	/**
	 * Adds permission to invite another user to join an existing collaboration
	 * group.
	 * 
	 * @param rc
	 */
	private void addRequestJoinExistingCollabGroupPermission(Set<ConstraintBasedPermission> rc) {
		ConstraintBasedPermission requestCollabGpJoin = createRequestPermissionForMsgType(
				MessageType.REQUEST_JOIN_EXISTING_COLLAB_GROUP);
		rc.add(requestCollabGpJoin);
	}


	/**
	 * @param type
	 * @return
	 */
	private ConstraintBasedPermission createRequestPermissionForMsgType(MessageType type) {
		ConstraintBasedPermission perm = new ConstraintBasedPermission(PermissionDomain.COMMS, PermissionType.READ);
		perm.addPropertyConstraint(new PropertyConstraint("name", type.name().replaceAll("_", "")));
		return perm;
	}

	/**
	 * @param group
	 * @return
	 */
	private GroupConstraint createGroupConstraintForGroup(Group group) {
		GroupConstraint groupConstraint = new GroupConstraint(group.getUniqueName());
		return groupConstraint;
	}

	@Override
	public Set<ConstraintBasedPermission> createDefaultGlobalGroupPermissions(Group group) {
		Set<ConstraintBasedPermission> rc = new HashSet<>();
		ConstraintBasedPermission perm = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
		perm.addPermissionType(PermissionType.WRITE);
		perm.addPermissionType(PermissionType.SHARE);
		perm.addPropertyConstraint(new PropertyConstraint("owner", "${self}"));
		perm.setGroupConstraint(createGroupConstraintForGroup(group));
		rc.add(perm);
		// only delete own documents by default
		ConstraintBasedPermission perm2 = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.DELETE);
		perm2.addPropertyConstraint(new PropertyConstraint("owner", "${self}"));
		rc.add(perm);

		// a grop member can read or write a form if it has been shared with the group
		ConstraintBasedPermission useForms = new ConstraintBasedPermission(PermissionDomain.FORM, PermissionType.SHARE);
		useForms.addPermissionType(PermissionType.WRITE);
		useForms.addPermissionType(PermissionType.READ);
		useForms.setGroupConstraint(createGroupConstraintForGroup(group));
		useForms.addPropertyConstraint(new PropertyConstraint(FormPermissionAdapter.GROUP_PROPERTY_NAME, "true"));
		rc.add(useForms);

		ConstraintBasedPermission shareingroup = new ConstraintBasedPermission(PermissionDomain.GROUP,
				PermissionType.SHARE);
		shareingroup.addPermissionType(PermissionType.SHARE);
		rc.add(shareingroup);
		return rc;
	}

	@Override
	public ConstraintBasedPermission createIdPermission(PermissionDomain domain, PermissionType type, Long id) {
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(domain, type);
		cbp.setIdConstraint(new IdConstraint(id));
		return cbp;
	}

	/**
	 * Sets up the ACL for a shared group folder, or does nothing if
	 * <code>grpFolder</code> is not a root shared group folder.
	 * 
	 * @param grp
	 *            group the shared folder belongs to.
	 * @param grpFolder
	 *            A Shared group folder
	 * @see PermissionFactory
	 * 
	 */
	@Override
	public void setUpACLForGroupSharedRootFolder(Group grp, Folder grpFolder) {
		if (!grpFolder.hasType(RecordType.SHARED_GROUP_FOLDER_ROOT)) {
			return;
		}
		// everyone in group can read, and create folders.
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ,
				PermissionType.CREATE_FOLDER);
		ACLElement el = new ACLElement(grp.getUniqueName(), cbp);
		grpFolder.getSharingACL().addACLElement(el);

		// delete and rename only apply to children of this folder. This permission is overridden in DeleteManager and
		// Workspace permissionsDTOBuilder (RSPAC-1636) for the lab group folder
		ConstraintBasedPermission folderManipulationPermission = new ConstraintBasedPermission(PermissionDomain.RECORD,
				PermissionType.DELETE, PermissionType.SEND, PermissionType.FOLDER_RECEIVE, PermissionType.RENAME);
		ACLElement folderManipulationElement;
		if(!grp.isProjectGroup()){
			// only PI/Lab Admin for Lab Groups
			folderManipulationElement = ACLElement.createRoleRestrictedGroupACL(grp, folderManipulationPermission, RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN);
		} else {
			// all members of a project group can manipulate folders and docs within the shared group folder
			folderManipulationElement = new ACLElement(grp.getUniqueName(), folderManipulationPermission);
		}
		grpFolder.getSharingACL().addACLElement(folderManipulationElement);
	}

	public void setUpACLForUserRootInPIFolder(Group grp, Folder userRoot) {
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
		ACLElement el = new ACLElement(grp.getUniqueName(), cbp);
		userRoot.getSharingACL().addACLElement(el);
	}

	@Override
	public Set<ConstraintBasedPermission> createCommunityPermissionsForAdmin(User admin, Community community) {
		Set<ConstraintBasedPermission> rc = new HashSet<>();
		if (!admin.hasRole(Role.ADMIN_ROLE)) {
			throw new IllegalArgumentException("Can't add these permissions to a non-admin role");
		}
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.COMMUNITY, PermissionType.WRITE);
		cbp.setIdConstraint(new IdConstraint(community.getId()));
		admin.addPermission(cbp);

		ConstraintBasedPermission cbp2 = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
		cbp2.setCommunityConstraint(new CommunityConstraint(community.getId()));
		admin.addPermission(cbp2);

		ConstraintBasedPermission cbp3 = new ConstraintBasedPermission(PermissionDomain.GROUP, PermissionType.WRITE);
		cbp3.setCommunityConstraint(new CommunityConstraint(community.getId()));
		admin.addPermission(cbp3);
		
		ConstraintBasedPermission cbp4 = new ConstraintBasedPermission(PermissionDomain.GROUP, PermissionType.CREATE);
		admin.addPermission(cbp4);
		rc.add(cbp);
		rc.add(cbp2);
		rc.add(cbp3);
		rc.add(cbp4);
		return rc;
	}

	public void setUpACLForIndividSharedFolder(User sharer, User sharee, Folder sharedFolder) {
		// everyone in group can read and delete
		ConstraintBasedPermission folderManips = new ConstraintBasedPermission(PermissionDomain.RECORD,
				PermissionType.DELETE, PermissionType.READ);
		ACLElement el = new ACLElement(sharer.getUniqueName(), folderManips);
		ACLElement el2 = new ACLElement(sharee.getUniqueName(), folderManips);
		sharedFolder.getSharingACL().addACLElement(el);
		sharedFolder.getSharingACL().addACLElement(el2);
	}

	@Override
	public void setUpACLForUserRoot(User u, Folder rootForUser) {
		RecordSharingACL acl = RecordSharingACL.createACLForUserOrGroup(u, PermissionType.WRITE);
		ConstraintBasedPermission createRecordPerm = new ConstraintBasedPermission(PermissionDomain.RECORD,
				PermissionType.CREATE);
		acl.addACLElement(new ACLElement(u.getUsername(), createRecordPerm));
		ConstraintBasedPermission createFolderPerm = new ConstraintBasedPermission(PermissionDomain.RECORD,
				PermissionType.CREATE_FOLDER);
		acl.addACLElement(new ACLElement(u.getUsername(), createFolderPerm));
		ConstraintBasedPermission deleteRecordPerm = new ConstraintBasedPermission(PermissionDomain.RECORD,
				PermissionType.DELETE);
		acl.addACLElement(new ACLElement(u.getUsername(), deleteRecordPerm));
		ConstraintBasedPermission sendPerm = new ConstraintBasedPermission(PermissionDomain.RECORD,
				PermissionType.SEND);
		acl.addACLElement(new ACLElement(u.getUsername(), sendPerm));
		ConstraintBasedPermission receive = new ConstraintBasedPermission(PermissionDomain.RECORD,
				PermissionType.FOLDER_RECEIVE);
		acl.addACLElement(new ACLElement(u.getUsername(), receive));
		ConstraintBasedPermission rename = new ConstraintBasedPermission(PermissionDomain.RECORD,
				PermissionType.RENAME);
		acl.addACLElement(new ACLElement(u.getUsername(), rename));
		ConstraintBasedPermission copy = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.COPY);
		acl.addACLElement(new ACLElement(u.getUsername(), copy));
		rootForUser.setSharingACL(acl);
	}

	@Override
	public void setUpACLForIndividualTemplateFolder(Folder templateFolder, User u) {
		Validate.isTrue(templateFolder.isTemplateFolder(), "Folder must be the Template folder");
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ,
				PermissionType.CREATE_FOLDER, PermissionType.FOLDER_RECEIVE);
		ACLElement acl = new ACLElement(u.getUsername(), cbp);
		templateFolder.getSharingACL().addACLElement(acl);
	}

	/**
	 * Adds template sub-folder permissions to folder/template in Templates
	 * folder
	 * 
	 * @param templateChild
	 * @param subject
	 */
	@Override
	public void setUpACLForTemplateFolderChildPermissions(BaseRecord templateChild, User subject) {
		ConstraintBasedPermission cbp = standardFolderPermissions();
		templateChild.getSharingACL().addACLElement(new ACLElement(subject.getUsername(), cbp));
	}

	@Override
	public void setUpAclForIndividualInboxFolder(Folder inboxFolder, User subject) {
		Validate.isTrue(inboxFolder.isImportedContentFolder(), "Folder must be  an ApiInbox or Imports folder");
		if(!inboxFolder.getOwner().equals(subject)) {
			throw new AuthorizationException(String.format("Subject %s must be the owner of inbox folder but is %s",
					subject.getUsername(),inboxFolder.getOwner().getUsername()));
		}
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE,
				PermissionType.CREATE_FOLDER, PermissionType.FOLDER_RECEIVE, 
				PermissionType.CREATE);
		ACLElement acl = new ACLElement(subject.getUsername(), cbp);
		inboxFolder.getSharingACL().addACLElement(acl);		
	}

	@Override
	public void setUpAclForInboxFolderChildPermissions(BaseRecord child, User subject) {
		ConstraintBasedPermission cbp = standardFolderPermissions();
		child.getSharingACL().addACLElement(new ACLElement(subject.getUsername(), cbp));
		
	}
   // for subfolders of template/apiinbox which should act as normal folders.
	private ConstraintBasedPermission standardFolderPermissions() {
		return new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.COPY,
				PermissionType.DELETE, PermissionType.FOLDER_RECEIVE, PermissionType.SEND, PermissionType.RENAME,
				PermissionType.WRITE);
	}

}
