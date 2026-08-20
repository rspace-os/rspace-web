package com.researchspace.model.record;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.RecordSharingACL;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Propagates copies of an ACL from the parent to all descendants in tree
 * hierarchy
 */
public class InheritACLFromParentsPropagationPolicy implements ACLPropagationPolicy {

	@Override
	public void onAdd(BaseRecord propagationRoot, BaseRecord child) {
		child.unionACL(removeAnonymousFromAcl(propagationRoot.getSharingACL()));
		recursiveOnAdd(child);
	}
	//do not propagate the published state of a notebook to entries (for example -  when they are moved into the notebook)
	private RecordSharingACL removeAnonymousFromAcl(RecordSharingACL source){
		RecordSharingACL copy = new RecordSharingACL();
		 source.getAclElements().stream().filter(aclElement ->
				!aclElement.getUserOrGrpUniqueName().equals(RecordGroupSharing.ANONYMOUS_USER)).map(copy::addACLElement).collect(Collectors.toList());
		return copy;
	}

	private void recursiveOnAdd(BaseRecord parent) {
		RecordSharingACL parentACL = parent.getSharingACL().copy();
		if (parent.isNotebook()) {
			// PI/Lab Admins don't get any extra permissions to entries in
			// shared notebook (RSPAC-993)
			parentACL.removeACLsforRolesInGroup(RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN);
		}

		for (BaseRecord child : parent.getChildrens()) {
			child.unionACL(parentACL);
			recursiveOnAdd(child);
		}
	}

	/**
	 * Removes the ACL (permissions) that the "parent" record (usually a folder) and child record
	 * had in common from the child record.
	 * <p>
	 * WARNING!!! This means that the child record may lose some ACL groups (permissions) that it
	 * should rightfully have from some of its other parents! Usually this is not a problem because
	 * each parent will have different ACLs (lab group, other user, original user), but might be
	 * an issue when testing / in the future!
	 *
	 * DO NOT REMOVE SHARING WITH ANONYMOUS USER
	 *
	 * @param propagationRoot (usually parent folder) from which the child record inherited its ACL
	 *                        from.
	 * @param removed         the child record to remove the ACLs from.
	 */
	@Override
	public void onRemove(BaseRecord propagationRoot, BaseRecord removed) {
		RecordSharingACL parentACL = propagationRoot.getSharingACL();
		RecordSharingACL removedACL = removed.getSharingACL();
		List<ACLElement> aclsInCommonToParentAndChild = parentACL.intersectionWith(removedACL);
		//DO NOT REMOVE SHARING WITH ANONYMOUS USER
		aclsInCommonToParentAndChild = aclsInCommonToParentAndChild.stream().
				filter(el->!el.getUserOrGrpUniqueName().equals(RecordGroupSharing.ANONYMOUS_USER)).collect(Collectors.toList());
		recursivelyRemoveACLs(removed, aclsInCommonToParentAndChild);
	}

	void recursivelyRemoveACLs(BaseRecord removed, final List<ACLElement> aclsInCommonToParentAndChild) {
		removed.removeACLs(aclsInCommonToParentAndChild);
		for (BaseRecord child : removed.getChildrens()) {
			recursivelyRemoveACLs(child, aclsInCommonToParentAndChild);
		}
	}

	@Override
	public void propagate(BaseRecord propagationRoot) {
		for (BaseRecord child : propagationRoot.getChildrens()) {
			onAdd(propagationRoot, child);
		}
	}
}
