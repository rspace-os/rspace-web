package com.researchspace.model;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.dto.GroupPublicInfo;
import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.GroupPermissionsAdapter;
import com.researchspace.model.raid.UserRaid;
import com.researchspace.model.record.PermissionsAdaptable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlIDREF;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.shiro.authz.Permission;
import org.hibernate.annotations.Formula;

/**
 * A Group of users.
 */
@Entity
//prevent keyword clash with MySQL
@Table(name = "rsGroup")
@AuditTrailData(auditDomain = AuditDomain.GROUP)
public class Group extends AbstractUserOrGroupImpl implements Comparable<Group>, Serializable, PermissionsAdaptable {

	public static final int GROUP_UNIQUE_NAME_SUFFIX_LENGTH = 4;
	private static final long serialVersionUID = -5940412254501469794L;
	static final int MAX_PROFILE_LENGTH = 255;
	public static final String DEFAULT_ORDERBY_FIELD = "displayName";
	private String uniqueName;
	private User owner;
	private boolean groupFolderWanted = true;
	private boolean autoshareEnabled = false;
	private boolean publicationAllowed;
	private boolean seoAllowed;
	private boolean enforceOntologies;
	private boolean allowBioOntologies;
	private Long communalGroupFolderId;
	private Long sharedSnippetGroupFolderId;
	private String displayName;
	private GroupType groupType;
	private String admins;
	private String pis;
	private String groupOwners;
	private String profileText;
	private boolean groupFolderCreated;
	private Set<UserGroup> userGroups = new HashSet<>();
	private List<String> memberString = new ArrayList<>();
	private Long communityId = Community.DEFAULT_COMMUNITY_ID;
	private Set<Community> communities = new HashSet<>();
	private boolean selfService = false;
	private int memberCount = 0;
  private UserRaid raid;

	@Formula("(select count(*) from UserGroup ug where ug.group_id = id)")
	public int getMemberCount() {
		return memberCount;
	}

	public void setMemberCount(int memberCount) {
		this.memberCount = memberCount;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public static int getMaxProfileLength() {
		return MAX_PROFILE_LENGTH;
	}

	@AuditTrailProperty(name = "type")
	@XmlElement
	public GroupType getGroupType() {
		return groupType;
	}

	public void setGroupType(GroupType groupType) {
		this.groupType = groupType;
	}

	/**
	 * A display name for the group. Can be any String < 255 chars
	 *
	 */
	@AuditTrailProperty(name = "displayName")
	@XmlElement
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Sets displayName, truncating if necessary to 255 characters max
	 *
	 */
	public void setDisplayName(String displayName) {
		if (displayName != null && displayName.length() > 255) {
			displayName = StringUtils.abbreviate(displayName, 255);
		}
		this.displayName = displayName;
	}

	/**
	 * Stored as ID rather than object reference to avoid cycles in object
	 * dependencies. <br/>
	 * This may not always be set for a group so may be <code>null</code>.
	 *
	 */
	public Long getCommunalGroupFolderId() {
		return communalGroupFolderId;
	}

	public void setCommunalGroupFolderId(Long communalGroupFolderId) {
		this.communalGroupFolderId = communalGroupFolderId;
	}

	/**
	 * Whether a group folder was chosen when the group was set up
	 *
	 */
	public boolean isGroupFolderWanted() {
		return groupFolderWanted;
	}

	public void setGroupFolderWanted(boolean groupFolderWanted) {
		this.groupFolderWanted = groupFolderWanted;
	}

	/**
	 * Is all work autoshared by current and future members of this group
	 */
	public boolean isAutoshareEnabled() {
		return this.autoshareEnabled;
	}

	/**
	 * Is publication of own documents by group non PI members allowed
	 */
	public boolean isPublicationAllowed() {
		return this.publicationAllowed;
	}

	public void setPublicationAllowed(boolean publicationAllowed) {
		this.publicationAllowed = publicationAllowed;
	}

	public void setAutoshareEnabled(boolean autoshareEnabled) {
		this.autoshareEnabled = autoshareEnabled;
	}

	/**
	 * The owner of the group, should never be <code>null</code>. This will be:
	 * <ul>
	 * <li>For a LabGroup - The PI
	 * <li>For a CollabGroup - The PI who initiated the request to create a
	 * collaboration group
	 * <li>For a ProjectGroup - the creator of the group
	 * </ul>
	 * This is annotated ManyToOne as one user can be owner of many groups.
	 *
	 * @return The owner of this group
	 */
	@ManyToOne()
	@XmlElement
	@XmlIDREF
	public User getOwner() {
		return owner;
	}

	public void setOwner(User owner) {
		this.owner = owner;
	}

	/**
	 * Users can belong to > 1 group
	 *
	 */
	@OneToMany(cascade = CascadeType.ALL, mappedBy = "group", orphanRemoval = true, fetch = FetchType.EAGER)
	public Set<UserGroup> getUserGroups() {
		return userGroups;
	}

	@Transient
	public List<String> getMemberString() {
		return memberString;
	}

	/**
	 * Transient string representation for UI
	 *
	 */
	@Transient
	public String getAdmins() {
		return admins;
	}

	/**
	 * Collected from UI at creation time. This is recorded so that the
	 * community id can be obtained for permissions checking, without having to
	 * load up the community collections.
	 * <p>
	 * This method should be called whenever a group is being moved to a new
	 * community
	 *
	 */
	public Long getCommunityId() {
		return communityId;
	}

	public void setCommunityId(Long communityId) {
		this.communityId = communityId;
	}

	public void setAdmins(String admins) {
		this.admins = admins;
	}

	/**
	 * Transient string representation for UI
	 *
	 */
	@Transient
	public String getPis() {
		return pis;
	}

	public void setPis(String pis) {
		this.pis = pis;
	}

	@Transient
	public String getGroupOwners() { return groupOwners; }

	public void setGroupOwners(String groupOwners) { this.groupOwners = groupOwners; }


	public void setMemberString(List<String> memberString) {
		this.memberString = memberString;
	}

	boolean addMember(User member) {
		return addMember(member,  RoleInGroup.DEFAULT);
	}

	public boolean addMember(User member, RoleInGroup role,
	                         Set<ConstraintBasedPermission> groupRolePermission) {
		/*
		 * Don't add a user twice to a group
		 */
		for (UserGroup ug : userGroups) {
			if (ug.getUser().equals(member)) {
				return false;
			}
		}
		if (RoleInGroup.PI.equals(role) && !member.hasRole(Role.PI_ROLE)) {
			throw new IllegalArgumentException("Attempting to add a non-PI user to a PI role in the group");
		}

		if (RoleInGroup.GROUP_OWNER.equals(role) && !isProjectGroup()) {
			throw new IllegalArgumentException("Attempting to add group owner to group other than Project Group");
		}

		UserGroup ug = new UserGroup(member, this, role);
		for (ConstraintBasedPermission cbp : groupRolePermission) {
			ug.addPermission(cbp);
		}

		boolean added = userGroups.add(ug);
		if (added) {
			member.getUserGroups().add(ug);
		}

		return added;
	}

	public boolean addMember(User member, RoleInGroup role) {
		return addMember(member, role, Collections.emptySet());
	}

	@Transient
	public UserGroup getUserGroupForUser(User user) {
		return findUserGroup(ug -> ug.getUser().equals(user));
	}

	private UserGroup findUserGroup(Predicate<? super UserGroup> filter) {
		return getUserGroups().stream().filter(filter).findFirst().orElse(null);
	}

	public boolean hasMember(UserOrGroup subject) {
		if (subject.isUser()) {
			for (UserGroup ug : userGroups) {
				if (ug.getUser().equals(subject)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean removeMember(User subject) {
		boolean removed = false;
		UserGroup toRemove = null;
		for (UserGroup ug : userGroups) {
			if (ug.getUser().equals(subject)) {
				toRemove = ug;
				break;
			}
		}
		removed = userGroups.remove(toRemove);
		if (removed) {
			subject.getUserGroups().remove(toRemove);
			toRemove.setUser(null);
			toRemove.setGroup(null);
			toRemove.setPermissionStrings(Collections.emptySet());
		}

		return removed;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((uniqueName == null) ? 0 : uniqueName.hashCode());
		return result;
	}

	/*
	 * Equality based on unique name (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass() && !getClass().isAssignableFrom(obj.getClass()))
			return false;
		Group other = (Group) obj;
		if (uniqueName == null) {
			if (other.getUniqueName() != null)
				return false;
		} else if (!uniqueName.equals(other.getUniqueName()))
			return false;
		return true;
	}

	/**
	 * For Hibernate object creation and also for testing. Creates a LabGroup
	 * with the current time as its creation time
	 */
	public Group() {
		super();
		this.groupType = GroupType.LAB_GROUP;// default setting
	}

	Group(String uniqueName) {
		this();
		this.uniqueName = uniqueName;
	}

	/**
	 * Creates a group with specified name and owner
	 *
	 */
	public Group(String groupName, User user) {
		this(groupName);
		setOwner(user);
	}

	public void setUniqueName(String uniqueName) {
		this.uniqueName = uniqueName;
	}

	@Column(nullable = false, unique = true, length = Organisation.MAX_INDEXABLE_UTF_LENGTH)
	@XmlElement
	@XmlID
	public String getUniqueName() {
		return uniqueName;
	}

	@Override
	@Transient
	public boolean isGroup() {
		return true;
	}

	@Override
	@Transient
	public boolean isUser() {
		return false;
	}

	@Override
	public boolean isPermitted(Permission p, boolean include) {
		if (!include) {
			return false;
		}
		for (Permission groupP : getPermissions()) {
			if (groupP.implies(p)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets all group members
	 *
	 * @return
	 */
	@Transient
	@AuditTrailProperty(name = "members", properties = "username")
	public Set<User> getMembers() {
		Set<User> rc = new TreeSet<>();
		for (UserGroup ug : userGroups) {
				rc.add(ug.getUser());
		}
		return Collections.unmodifiableSet(rc);
	}

	/**
	 * Gets group members with admin role
	 *
	 * @return
	 */
	@Transient
	public Set<User> getAdminUsers() {
		return getUsersByRole(RoleInGroup.RS_LAB_ADMIN);
	}

	/**
	 * Gets group members with PI role
	 *
	 * @return
	 */
	@Transient
	public Set<User> getPiusers() {
		return getUsersByRole(RoleInGroup.PI);
	}

	/**
	 * Gets group members with Group Owner role
	 *
	 * @return A set containing all users with the role Group Owner
	 */
	@Transient
	public Set<User> getGroupOwnerUsers() {
		return getUsersByRole(RoleInGroup.GROUP_OWNER);
	}

	/**
	 * Gets member groups for collaboration groups.
	 */
	@Transient
	public Set<Group> getMemberGroupsForCollabGroup() {
		if (!isCollaborationGroup()) {
			throw new IllegalStateException("Not a collaboration group");
		}

		Set<User> piUsers = getPiusers();
		Map<User, Set<Group>> piGroups = new HashMap<>();

		for (User pi : piUsers) {
			Set<Group> groups = new HashSet<>();

			for (Group group : pi.getGroups()) {
				if (!group.getUniqueName().equals(getUniqueName()) && group.getOwner().equals(pi)) {
					groups.add(group);
				}
			}
			piGroups.put(pi, groups);
		}

		Set<User> collabGroupMembers = getUsersByRole(RoleInGroup.DEFAULT, RoleInGroup.RS_LAB_ADMIN);

		Set<Group> memberGroups = piGroups
				.values()
				.stream()
				.flatMap(Set::stream)
				.filter(g -> g.getUsersByRole(RoleInGroup.DEFAULT, RoleInGroup.RS_LAB_ADMIN)
						.stream()
						.anyMatch(collabGroupMembers::contains))
				.collect(Collectors.toSet());

		// If there are no default/admin users in the collaborating group yet,
		// display groups of PIs that only have one group to prevent showing nothing.
		if (memberGroups.isEmpty()) {
			return piGroups
					.values()
					.stream()
					.filter(s -> s.size() == 1)
					.flatMap(Set::stream)
					.collect(Collectors.toSet());
		}
		return memberGroups;
	}

	/**
	 * Gets group members with default role
	 *
	 * @return
	 */
	@Transient
	public Set<User> getDefaultUsers() {
		return getUsersByRole(RoleInGroup.DEFAULT);
	}

	public boolean hasPIs() {
		return getPiusers().size() > 0;
	}

	/**
	 * Gets union of users in the group with the specified roles
	 *
	 * @param roles 1 or more {@link RoleInGroup}s
	 */
	@Transient
	public Set<User> getUsersByRole(RoleInGroup... roles) {
		Set<User> rc = new TreeSet<>();
		for (UserGroup ug : userGroups) {
			for (RoleInGroup roleInGrp : roles) {
				if (roleInGrp.equals(ug.getRoleInGroup()))
					rc.add(ug.getUser());
			}
		}
		return rc;
	}

	@Transient
	public Set<User> getLabAdminsWithViewAllPermission() {
		Set<User> rc = new TreeSet<>();
		for (UserGroup ug : userGroups) {
			if (ug.getRoleInGroup().equals(RoleInGroup.RS_LAB_ADMIN)
					&& ug.isAdminViewDocsEnabled()) {
				rc.add(ug.getUser());
			}
		}
		return Collections.unmodifiableSet(rc);
	}

	/**
	 * Gets a set of users in the group who have default read access to the group's documents regardless
	 * of whether document is shared or unshared. E.g. PIs and
	 * LabAdmins with ViewAll permissions
	 *
	 * @return a possibly empty but non-null, unmodifiable set of users (may be empty e.g. for collaboration groups)
	 */
	@Transient
	public Set<User> getMembersWithDefaultViewAllPermissions() {
		Set<User> rc = new TreeSet<>();
		rc.addAll(getLabAdminsWithViewAllPermission());
		rc.addAll(getPiusers());
		return Collections.unmodifiableSet(rc);
	}

	/**
	 * Gets the role for the user in this group
	 *
	 * @param u the user
	 * @return A {@link RoleInGroup} or <code>null</code> if this user is not a
	 * member of this group.
	 */
	@Transient
	public RoleInGroup getRoleForUser(User u) {
		return getUserGroups().stream().filter(ug -> ug.getUser().equals(u)).map(ug -> ug.getRoleInGroup()).findFirst()
				.orElse(null);
	}

	@Override
	public String toString() {
		return "Group [uniqueName=" + uniqueName + ", displayName=" + displayName + ", groupType=" + groupType
				+ ", memberCount=" + memberCount +", raid=" + raid + "]";
	}

	/*
	 * hibernate
	 */
	public void setUserGroups(Set<UserGroup> userGroups) {
		this.userGroups = userGroups;
	}

	public boolean isGroupFolderCreated() {
		return groupFolderCreated;
	}

	/**
	 * Records a one-off operation that group folder was created.
	 *
	 */
	public void setGroupFolderCreated() {
		this.groupFolderCreated = true;
	}

	/*
	 * for hibernate
	 */
	void setGroupFolderCreated(boolean groupFolderCreated) {
		this.groupFolderCreated = groupFolderCreated;
	}

	/**
	 * @param groupsAndUsers
	 * @param userOrder      an optional comparator, can be null. If is null, list of users
	 *                       is sorted by natural ordering.
	 * @param userstoExclude open-ended list of users to exclude from the final results
	 * @return
	 */
	public static List<User> getUniqueUsersInGroups(Collection<? extends AbstractUserOrGroupImpl> groupsAndUsers,
	                                                Comparator<User> userOrder, User... userstoExclude) {
		Set<User> users = new HashSet<>();
		for (AbstractUserOrGroupImpl grp : groupsAndUsers) {
			if (grp.isGroup())
				users.addAll(grp.asGroup().getMembers());
			else if (grp.isUser()) {
				users.add(grp.asUser());
			}
		}
		for (User u : userstoExclude) {
			users.remove(u);
		}

		List<User> asList = new ArrayList<>(users);
		if (userOrder != null) {
			Collections.sort(asList, userOrder);
		}
		return asList;
	}

	/**
	 * Only sets unique name if not already set; constructs a new name from
	 * display name and a random suffix Or,
	 */
	public void createAndSetUniqueGroupName() {
		if (uniqueName != null) {
			return;
		}
		if (displayName != null) {
			uniqueName = displayName.replaceAll("[^A-Za-z0-9]", "") + randomAlphanumeric(GROUP_UNIQUE_NAME_SUFFIX_LENGTH);
		} else
			uniqueName = "Group" + randomAlphanumeric(GROUP_UNIQUE_NAME_SUFFIX_LENGTH);
	}

	/**
	 * Boolean test for whether this is a collaboration Group or not. Groups can
	 * only have 1 group type.
	 *
	 * @return <code>true</code> if this group is a CollaborationGroup,
	 * <code>false</code> otherwise
	 */
	@Transient
	public boolean isCollaborationGroup() {
		return GroupType.COLLABORATION_GROUP.equals(groupType);
	}

	/**
	 * Boolean test for whether this is a project group or not.
	 * Groups can only have 1 group type.
	 *
	 * @return <code>true</code> if this group is a ProjectGroup,
	 * <code>false</code> otherwise
	 */
	@Transient
	public boolean isProjectGroup() {
		return GroupType.PROJECT_GROUP.equals(groupType);
	}

	/**
	 * Boolean test for whether this is a LabGroup or not. Groups can only have
	 * 1 group type.
	 *
	 * @return <code>true</code> if this group is a standard LabGroup, <code>false</code>
	 * otherwise
	 */
	@Transient
	public boolean isLabGroup() {
		return GroupType.LAB_GROUP == groupType;
	}

	/**
	 * Gets optional profile text for the group
	 *
	 * @return
	 */
	@XmlElement
	public String getProfileText() {
		return profileText;
	}

	/**
	 * Sets profile text, abbreviating to MAX_PROFILE_LENGTH if profile text is
	 * longer than this.
	 *
	 * @param profileText
	 */
	public void setProfileText(String profileText) {
		this.profileText = StringUtils.abbreviate(profileText, MAX_PROFILE_LENGTH);
	}

	@Override
	@Transient
	public AbstractEntityPermissionAdapter getPermissionsAdapter() {
		return new GroupPermissionsAdapter(this);
	}

	/**
	 * Returns <code>true</code> if this group has no members,
	 * <code>false</code> otherwise.
	 *
	 * @return
	 */
	@Transient
	public boolean isEmpty() {
		return getSize() == 0;
	}

	/**
	 * The total number of members in the group, including those whose accounts
	 * are disabled.
	 *
	 * @return an integer &gt;= 0
	 */
	@Transient
	public int getSize() {
		return getMembers().size();
	}

	/**
	 * The number of group members who have enabled accounts.
	 *
	 * @return an integer &gt;= 0 and &lt;= getSize()
	 */
	@Transient
	public int getEnabledMemberSize() {
		return (int) getMembers().stream().filter(User::isEnabled).count();
	}

	/**
	 * The number of group members who have disabled accounts.
	 * <br>
	 * Invariant:  The sum of this + getEnabledMemberSize() must
	 * equal getSize()
	 *
	 * @return an integer &gt;= 0 and &lt;= getSize()
	 */
	@Transient
	public int getDisabledMemberSize() {
		return getSize() - (int) getMembers().stream().filter(User::isEnabled).count();
	}

	/**
	 * Convenience method to determine if the specified username is the only PI
	 * in the group
	 *
	 * @param username
	 * @return
	 */
	@Transient
	public boolean isOnlyGroupPi(String username) {
		return getPiusers().size() == 1 && getPiusers().iterator().next().getUsername().equals(username);
	}

	/**
	 * Convenience method to determine if the specified username is the only group owner
	 * in the group
	 *
	 * @param username
	 * @return
	 */
	@Transient
	public boolean isOnlyGroupOwner(String username) {
		return getGroupOwnerUsers().size() == 1 &&
				getGroupOwnerUsers().iterator().next().getUsername().equals(username);
	}

	/**
	 * Although this is a ManyToMany mapping, a LabGroup should only belong to
	 * one community; this mapping just allows an add/remove operation in a
	 * single atomic transaction.
	 *
	 * @return A possibly empty but non-null set of LabGroups that belong to
	 * this community
	 */
	@ManyToMany(mappedBy = "labGroups")
	Set<Community> getCommunities() {
		return communities;
	}

	// for hibernate only
	void setCommunities(Set<Community> communities) {
		this.communities = communities;
	}

	// manages reverse of relationship; clients should through
	// public Community adddGroup
	void addCommunity(Community community) {
		if (isLabGroup())
			if (!communities.isEmpty() && !communities.contains(community)) {
				throw new IllegalArgumentException("Can't add 2nd community to this lab group");
			}
		this.communities.add(community);
	}

	// manages reverse of relationship; clients should through Community to
	// remove a group
	boolean removeCommunity(Community community) {
		return this.communities.remove(community);
	}

	/**
	 * Gets the single community that this LabGroup belongs to
	 *
	 * @return The {@link Community}, or <code>null</code> if belongs to 0
	 * communities
	 * @throws IllegalStateException if this group belongs to > 1 Community.
	 */
	@Transient
	public Community getCommunity() {
		if (communities.size() > 1) {
			throw new IllegalStateException("Cannot belong to > 1 community");
		}
		if (communities.size() > 0)
			return communities.iterator().next();
		else {
			return null;
		}
	}

	@Override
	@Transient
	protected GlobalIdPrefix getGlobalIdPrefix() {
		return GlobalIdPrefix.GP;
	}

	@Override
	public int compareTo(Group other) {
		return this.uniqueName.compareTo(other.uniqueName);
	}

	public void setLabAdminViewAll(User labAdmin, boolean isViewAll) {
		Validate.isTrue(labAdmin.hasRoleInGroup(this, RoleInGroup.RS_LAB_ADMIN), "User must be a lab admin but was %s",
				labAdmin.getUsername());
		getUserGroupForUser(labAdmin).setAdminViewDocsEnabled(isViewAll);

	}

	/**
	 * Gets all group members that are not PIs.
	 *
	 * @return a possibly empty but non-null set
	 */
	@Transient
	public Set<User> getAllNonPIMembers() {
		return getUsersByRole(RoleInGroup.DEFAULT, RoleInGroup.RS_LAB_ADMIN);
	}

	public GroupPublicInfo toPublicInfo() {
		GroupPublicInfo groupInfo = new GroupPublicInfo();
		groupInfo.setDisplayName(displayName);
		groupInfo.setUniqueName(uniqueName);
		groupInfo.setPi(pis);

		List<String> membersWithoutPI = new ArrayList<>(memberString);
		membersWithoutPI.remove(pis);
		groupInfo.setOtherMembers(membersWithoutPI);

		return groupInfo;
	}
	/*
	 * return true if this Group was created by a user other than a sysadmin, RSPAC-2482
	 */
	@XmlElement
	public boolean isSelfService() {
		return selfService;
	}

	public void setSelfService(boolean selfService) {
		if(!selfService || this.isLabGroup()) {
			this.selfService = selfService;
		} else {
			throw new IllegalArgumentException("Only Lab Groups can be self service");
		}
	}

	public boolean isSeoAllowed() {
		return seoAllowed;
	}

	public void setSeoAllowed(boolean seoAllowed) {
		this.seoAllowed = seoAllowed;
	}

	public boolean isEnforceOntologies() {
		return enforceOntologies;
	}

	public void setEnforceOntologies(boolean enforceOntologies) {
		this.enforceOntologies = enforceOntologies;
	}

	public Long getSharedSnippetGroupFolderId() {
		return sharedSnippetGroupFolderId;
	}

	public void setSharedSnippetGroupFolderId(Long sharedSnippetGroupFolderId) {
		this.sharedSnippetGroupFolderId = sharedSnippetGroupFolderId;
	}

	public boolean isAllowBioOntologies() {
		return allowBioOntologies;
	}

	public void setAllowBioOntologies(boolean allowBioOntologies) {
		this.allowBioOntologies = allowBioOntologies;
	}

	public void setRaid(UserRaid raid) {
		this.raid = raid;
	}

	@OneToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "raid_id", referencedColumnName = "id")
	public UserRaid getRaid() {
		return this.raid;
	}

}
