package com.researchspace.model.permissions;

import java.util.HashSet;
import java.util.Set;

import com.researchspace.model.Group;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import org.apache.shiro.SecurityUtils;

/**
 * Adapts a Record to a permission system.
 */
public class RecordPermissionAdapter extends AbstractEntityPermissionAdapter {

	private BaseRecord record;

	public RecordPermissionAdapter(BaseRecord record) {
		this.record = record;
	}

	@Override
	public Long getId() {
		return record.getId();
	}

	@Override
	protected  Object getEntity() {
		return record;
	}

	protected @Override
	PropertyConstraint handleSpecialProperties(String propertyName) {
		if (propertyName.equals(FORM_PROP_NAME) && record.isStructuredDocument()) {
			return new PropertyConstraint(FORM_PROP_NAME, ((StructuredDocument) record).getForm().getName());
		}

		User owner = record.getOwner();
		if (propertyName.equals("owner")) {
			return new PropertyConstraint("owner", owner.getUsername());
		}

		if (propertyName.equals("sharingACL")) {
			return allowGroupPIToPublishIfSharedWithThemOrWithTheirGroup(propertyName, owner);
		}

		return null;
	}

	private PropertyConstraint allowGroupPIToPublishIfSharedWithThemOrWithTheirGroup(String propertyName, User owner) {
		StringBuffer sb = new StringBuffer();
		String acl = record.getSharingACL().getAcl();
		String[] userACls = acl.split("&");
		String shareeName = (String) SecurityUtils.getSubject().getPrincipal();
		boolean shareeIsPIOfOwnersGroup = false;
		String sharingGroupName = "";
		for (Group aGroup : owner.getGroups()) {
			if (aGroup.isLabGroup() && aGroup.getOwner().getUsername().equals(shareeName)) {
				shareeIsPIOfOwnersGroup = true;
				sharingGroupName = aGroup.getUniqueName();
				break;
			}
		}
		if (shareeIsPIOfOwnersGroup) {
			for (String userAcl : userACls) {
				String userOrGrpName = userAcl.split("=")[0];
				if (userOrGrpName.indexOf("=") == -1 && userOrGrpName.indexOf("_") == -1 &&
						(shareeName.equals(userOrGrpName) || sharingGroupName.equals(userOrGrpName))) {
					sb.append(shareeName);
					break;
				}
			}
		}
		return new PropertyConstraint("sharingACL", sb.toString());
	}

	@Override
	public Set<GroupConstraint> getGroupConstraints() {
		Set<GroupConstraint> grps = new HashSet<>();
		User u = record.getOwner();
		for (Group group : u.getGroups()) {
			//something to do with PIs in other groups being private??
			if (PermissionType.READ.equals(getAction()) && u.hasRole(Role.PI_ROLE)
					&& !u.hasRoleInGroup(group, RoleInGroup.PI)) {
				continue;
			}
			grps.add(new GroupConstraint(group.getUniqueName()));
		}
		return grps;
	}

	@Override
	public Set<CommunityConstraint> getCommunityConstraints() {
		Set<CommunityConstraint> communities = new HashSet<>();
		User owner = record.getOwner();
		for (Group group : owner.getGroups()) {
			if (group.getCommunityId() != null) {
				communities.add(new CommunityConstraint(group.getCommunityId()));
			}
		}
		return communities;
	}

	public String toString() {
		return super.toString() + ", " + record.getId() + " record name: " + record.getName();
	}

	public boolean checkACL(User user) {
		RecordSharingACL acl = record.getSharingACL();
		return acl != null && acl.isPermitted(user, getAction());
	}

}
