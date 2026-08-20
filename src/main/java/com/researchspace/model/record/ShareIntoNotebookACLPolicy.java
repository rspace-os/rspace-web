package com.researchspace.model.record;

import java.util.List;
import java.util.stream.Collectors;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.RecordSharingACL;

/**
 * Based on RSPAC-913. Propagates read/write ACL from parent notebook to all the
 * entries. Doesn't propagate group entries, so PI/Lab Admin doesn't get extra
 * permissions.
 *
 */
public class ShareIntoNotebookACLPolicy implements ACLPropagationPolicy {

	@Override
	public void onAdd(BaseRecord notebook, BaseRecord child) {
		RecordSharingACL notebookPermissions = notebook.getSharingACL().copy();
		// PI/Lab Admins don't get any extra permissions to entries in shared
		// notebook
		notebookPermissions.removeACLsforRolesInGroup(RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN);
		child.getSharingACL().unionWith(notebookPermissions);
	}

	@Override
	public void onRemove(BaseRecord notebook, BaseRecord removed) {
		RecordSharingACL parentACL = notebook.getSharingACL();
		RecordSharingACL removedACL = removed.getSharingACL();
		//DO NOT REMOVE SHARING WITH ANONYMOUS USER
		List<ACLElement> aclsInCommonToParentAndChild = parentACL.intersectionWith(removedACL);
		aclsInCommonToParentAndChild = aclsInCommonToParentAndChild.stream().
				filter(el->!el.getUserOrGrpUniqueName().equals(RecordGroupSharing.ANONYMOUS_USER)).collect(Collectors.toList());
		for (ACLElement el : aclsInCommonToParentAndChild) {
			removedACL.removeACLElement(el);
		}
	}

	@Override
	public void propagate(BaseRecord propagationRoot) {

	}

}
