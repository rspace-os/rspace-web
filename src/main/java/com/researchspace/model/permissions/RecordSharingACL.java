package com.researchspace.model.permissions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Transient;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserOrGroup;

/**
 * Represents an AccessControl list.
 */
@Embeddable
public class RecordSharingACL implements Serializable {

	private static final long serialVersionUID = 4031957954851273292L;

	final static String ACL_ELEMENT_DELIMITER = "&";

	private static ConstraintPermissionResolver pr = new ConstraintPermissionResolver();

	private List<ACLElement> aclElements = new ArrayList<>();

	private boolean isRegeneratingList = false;

	// to persist
	private String acl;

	public RecordSharingACL() {
		acl = "";
	}

	/**
	 * Creates an independent copy of this ACL.
	 * 
	 * @return
	 */
	public RecordSharingACL copy() {
		RecordSharingACL copy = new RecordSharingACL();
		copy.setAcl(getAcl());
		return copy;
	}

	/**
	 * Static factory method to create a new {@link RecordSharingACL} for a
	 * given user/group and permission type.
	 * 
	 * @param userOrGrp
	 * @param permType
	 * @return
	 */
	public static RecordSharingACL createACLForUserOrGroup(UserOrGroup userOrGrp, PermissionType permType) {
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.RECORD, permType);
		ACLElement el = new ACLElement(userOrGrp.getUniqueName(), cbp);
		RecordSharingACL acl = new RecordSharingACL();
		acl.addACLElement(el);
		return acl;
	}

	/**
	 * Adds an ACL element based on the combination of arguments provided
	 * 
	 * @param userOrGrp
	 * @param cbp
	 * @return
	 */
	public boolean addACLElement(UserOrGroup userOrGrp, ConstraintBasedPermission cbp) {
		ACLElement el = new ACLElement(userOrGrp.getUniqueName(), cbp);
		return addACLElement(el);
	}

	/**
	 * Adds a new {@link ACLElement} if it doesn't already exist
	 * 
	 * @param el
	 * @return <code>true</code> if added, <code>false</code> if already exists
	 */
	public boolean addACLElement(ACLElement el) {
		boolean added = false;
		if (!aclElements.contains(el)) {
			added = aclElements.add(el);
		}
		if (added && !isRegeneratingList) { // prevents infinite cycles)
			regenerateACLString();
		}
		return added;
	}

	/**
	 * REmoves the specified ACLElement, if it exists in this ACL
	 * 
	 * @param toRemove
	 * @return
	 */
	public boolean removeACLElement(ACLElement toRemove) {
		if (aclElements.remove(toRemove)) {
			regenerateACLString();
			return true;
		}
		return false;
	}

	public boolean removeACLElement(AbstractUserOrGroupImpl userOrGrp, ConstraintBasedPermission cbp) {
		ACLElement el = new ACLElement(userOrGrp.getUniqueName(), cbp);
		return removeACLElement(el);
	}

	/**
	 * Clears this ACL of all elements.
	 */
	public void clear() {
		aclElements.clear();
		this.acl = null;
	}

	/**
	 * Gets the number of permissions held in this ACL.
	 * 
	 * @return
	 */
	@Transient
	public int getNumPermissions() {
		return aclElements.size();
	}

	/**
	 * Returns <code>true</code> if there are ACL elements defined - this is
	 * equivalent to testing that :<br/>
	 * <code>getNumPermissions() ==0 </code>
	 * 
	 * @return
	 */
	@Transient
	public boolean isACLPopulated() {
		return getNumPermissions() > 0;
	}

	/**
	 * Permissions check as to whether the specified user meets the assigned
	 * permissions in the ACL.
	 * 
	 * @param user
	 * @param requestedAction
	 * @return <code>true</code> if user is authorised, <code>false</code>
	 *         otherwise.
	 */
	public boolean isPermitted(User user, PermissionType requestedAction) {

		for (ACLElement el : aclElements) {
			String uOrGrp = el.getUserOrGrpUniqueName();
			// we have a straight match -
			if (user.getUsername().equals(uOrGrp)) {
				if (checkRequestedActionMatchesACL(requestedAction, el)) {
					return true;
				}
			}
			// otherwise it might be a group permission
			for (Group group : user.getGroups()) {
				if (!uOrGrp.startsWith(group.getUniqueName())) {
					continue;
				}
				// now e check if group permissions is restricted by role in
				// group
				Set<RoleInGroup> rig = el.getRoles();
				if (rig.isEmpty() || rig.contains(group.getRoleForUser(user))) {
					if (checkRequestedActionMatchesACL(requestedAction, el)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Removes all ACLelements for a particular user
	 * 
	 * @param userOrGroup
	 * @return
	 */
	public Set<ACLElement> removeACLsforUserOrGroup(AbstractUserOrGroupImpl userOrGroup) {
		Set<ACLElement> toRemove = new HashSet<>();
		for (ACLElement element : aclElements) {
			if (element.getUserOrGrpUniqueName().equals(userOrGroup.getUniqueName())) {
				toRemove.add(element);
			}
		}
		aclElements.removeAll(toRemove);
		regenerateACLString();
		return toRemove;
	}

	public Set<ACLElement> removeACLsforRolesInGroup(RoleInGroup... roles) {
		ConstraintBasedPermission emptyPermission = new ConstraintBasedPermission();
		ACLElement groupAdminACL = ACLElement.createRoleRestrictedGroupACL(null, emptyPermission, roles);
		Set<ACLElement> toRemove = new HashSet<>();
		for (ACLElement element : aclElements) {
			if (element.getUserOrGrpUniqueName().endsWith(groupAdminACL.getUserOrGrpUniqueName())) {
				toRemove.add(element);
			}
		}
		aclElements.removeAll(toRemove);
		regenerateACLString();
		return toRemove;
	}

	private boolean checkRequestedActionMatchesACL(PermissionType requestedAction, ACLElement el) {
		ConstraintBasedPermission cbp = pr.resolvePermission(el.getPermString());
		return cbp.matchActions(requestedAction);
	}

	/**
	 * Gets String representation of this ACL for persistence. The syntax is
	 * asfollows: user1=RECORD:READ:&user2=RECORD:WRITE:&group1=RECORD:COPY:
	 * <p/>
	 * I.e., individual elements are separated by an ampersand.
	 * 
	 * @return
	 */
	@FullTextField(analyzer = "aclAnalyzer")
	@Column(length = 2500)
	public String getAcl() {
		if (acl == null) {
			this.acl = "";
		}
		return acl;
	}

	private void regenerateACLString() {
		this.acl = getString();
	}

	private void regenerateACLList() {
		aclElements.clear();
		if (acl != null) {
			String[] aclStrings = StringUtils.split(acl, ACL_ELEMENT_DELIMITER);
			for (String aclString : aclStrings) {
				String[] nameValuePair = StringUtils.split(aclString, "=");
				ConstraintBasedPermission perm = pr.resolvePermission(nameValuePair[1]);
				ACLElement aclE = new ACLElement(nameValuePair[0], perm);
				aclElements.add(aclE);
			}
		}
	}

	/**
	 * UNSAFE!
	 *
	 * getAclElements should only be used with displaying share icons in the workspace
	 * home page.
	 *
	 * Unsafe for performance reasons -> we do not want to perform a deep copy
	 * of aclElements for each record shown in the workspace, that is potentially
	 * cloning hundreds of objects.
	 */
	@Transient
	public List<ACLElement> getAclElements() {
		return this.aclElements;
	}

	/**
	 * Gets a String representation of this ACL
	 * 
	 * @return
	 */
	@Transient
	public String getString() {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < aclElements.size(); i++) {
			ACLElement el = aclElements.get(i);
			sb.append(el.getAsString());
			if (i < aclElements.size() - 1) {
				sb.append("&");
			}
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return "RecordSharingACL [acl=" + acl + "]";
	}

	// for Hibernate & internal usage
	void setAcl(String acl) {
		this.acl = acl;
		try {
			isRegeneratingList = true;
			regenerateACLList();
		} finally {
			// always reset even if exception thrown
			isRegeneratingList = false;
		}
	}

	/**
	 * Calculates ACLElements in common between this ACL and the
	 * <code>other</code>ACL and returns a List of the share ACLs.
	 * 
	 * @param other
	 * @return
	 */
	@Transient
	public List<ACLElement> intersectionWith(RecordSharingACL other) {
		return ListUtils.intersection(this.aclElements, other.aclElements);
	}

	/**
	 * Joins the content of this ACL with the <code>other</code> ACL, ignoring
	 * duplicates.
	 * 
	 * @param other
	 */
	public void unionWith(RecordSharingACL other) {
		List<ACLElement> sum = ListUtils.sum(this.aclElements, other.aclElements);
		this.aclElements = sum;
		regenerateACLString();
	}

}
