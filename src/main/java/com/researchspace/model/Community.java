package com.researchspace.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.permissions.CommunityPermissionsAdapter;
import com.researchspace.model.record.PermissionsAdaptable;

/**
 * A community is an aggregation of LabGroups and forms an administrative unit
 * organised by an RSpaceAdmin role.<br/>
 * Equality is based on a unique business id. Class invariants are
 * <ul>
 * <li>Must have an administrator with role community admin
 * <li>Groups set only contains LabGroups
 * <li>Creation date is set and immutable once created
 * </ul>
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@AuditTrailData(auditDomain = AuditDomain.COMMUNITY)
@XmlType
@XmlAccessorType(XmlAccessType.NONE)
public class Community implements Serializable, PermissionsAdaptable {

	private static final long serialVersionUID = 1391521524212706687L;

	/**
	 * Database ID of default community.
	 */
	public static final Long DEFAULT_COMMUNITY_ID = -1L;

	protected static final int MAX_FIELD_LENG = 255;
	protected static final int MAX_DESC_LENGTH = 255;
	private static final int MAX_ID_LENGTH = 100;

	private Long id;
	private String displayName;
	private String uniqueName;
	private Date creationDate;
	private Set<User> admins = new TreeSet<>();
	private Set<Group> labGroups = new HashSet<>();
	private List<User> availableAdmins;
	private List<Long> adminIds;
	private List<Group> availableGroups = new ArrayList<>();
	private List<Long> groupIds;
	private String profileText;

	/**
	 * Default constructor. At least unique name should be set and admin admin
	 * added for Community to be valid.
	 */
	public Community() {
		this.creationDate = new Date();
	}

	/**
	 * Copy constructor for basic properties of the Community. <br>
	 * Doesn't copy database ids or collections, but <em> DOES </em> copy the
	 * unique name, which is used for equals(). So, care should be taken that
	 * copies are not added to collections of the originals, as they are
	 * considered equal. <br/>
	 * The creation data of the copy is the date of this object creation, not
	 * the original.
	 * 
	 * @param admin
	 * @param toCopy
	 *            the community to copy
	 */
	public Community(User admin, Community toCopy) {
		Validate.notNull(admin);
		Validate.notNull(toCopy);
		Validate.notEmpty(toCopy.getUniqueName(), "Community should have uniqueID");

		addAdmin(admin);
		this.displayName = toCopy.getDisplayName();
		this.uniqueName = toCopy.getUniqueName();
		this.profileText = toCopy.getProfileText();
	}

	@XmlElement
	public String getProfileText() {
		return profileText;
	}

	/**
	 * Is abbreviated to fit in DB table
	 * 
	 * @param profileText
	 */
	public void setProfileText(String profileText) {
		this.profileText = StringUtils.abbreviate(profileText, MAX_FIELD_LENG);
	}

	/**
	 * Database PK
	 * 
	 * @return a long
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "community_gen")
	@TableGenerator(name = "community_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	@AuditTrailIdentifier
	public Long getId() {
		return id;
	}

	/**
	 * for hibernate/Spring only
	 * 
	 * @param id
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * Display name for UI purposes.
	 * 
	 * @return
	 */
	@Column(length = 255)
	@AuditTrailProperty(name = "displayName")
	@XmlElement
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Sets the display name - will abbreviate to MAX_DESC_LENGTH chars if the
	 * input is too long.
	 * 
	 * @param displayName
	 */
	public void setDisplayName(String displayName) {
		this.displayName = StringUtils.abbreviate(displayName, MAX_DESC_LENGTH);
	}

	@Column(unique = true, length = 100)
	@AuditTrailProperty(name = "uniqueName")
	@XmlElement
	@XmlID
	public String getUniqueName() {
		return uniqueName;
	}

	/**
	 * For hibernate; this should be set at creation time in the constructor.
	 */
	public void setUniqueName(String uniqueName) {
		this.uniqueName = StringUtils.abbreviate(uniqueName, MAX_ID_LENGTH);
	}

	/**
	 * Only sets unique name if not already set; constructs a new name from
	 * display name and a random suffix Or,
	 */
	public void createAndSetUniqueName() {
		if (uniqueName == null) {
			uniqueName = createUniqueName(displayName);
		}
	}

	/**
	 * Returns unique name generated from display name (if passed).
	 * 
	 * @param displayName,
	 *            may be null
	 * @return unique name
	 */
	public static String createUniqueName(String displayName) {
		String uniqueName = null;
		if (displayName != null) {
			uniqueName = displayName.replaceAll("[^A-Za-z0-9]", "") + RandomStringUtils.randomAlphanumeric(4);
		} else {
			uniqueName = "Community" + RandomStringUtils.randomAlphanumeric(4);
		}
		return uniqueName;
	}

	/**
	 * Returns a copy of the encapsulated date; the returned date can be
	 * manipulated without affecting the value stored in this object.
	 * 
	 * @return the creationDate of this community.
	 */
	@Temporal(TemporalType.TIMESTAMP)
	public Date getCreationDate() {
		if (creationDate != null) { // for hibernate
			return new Date(creationDate.getTime());
		} else {
			return null;
		}
	}

	/**
	 * For hibernate, should not be a public setter - should be set once in API
	 * constructor
	 */
	void setCreationDate(Date creationDate) {
		if (creationDate != null) {
			this.creationDate = new Date(creationDate.getTime());
		}
	}

	/**
	 * Store mappings in a mapping table rather than in user table. One
	 * community can have several admins; an admin can only be admin for 1
	 * community (unless is the sysadmin - for this reason is many-to-many
	 * mapping).
	 * 
	 * @return
	 */
	@ManyToMany()
	@JoinTable(name = "community_admin", joinColumns = {
			@JoinColumn(name = "community_id") }, inverseJoinColumns = @JoinColumn(name = "user_id"))
	@XmlElementWrapper
	@XmlElement(name = "admin")
	@XmlIDREF
	public Set<User> getAdmins() {
		return admins;
	}

	/**
	 * For hibernate only -use add/remove to add/remove users in code.
	 */
	void setAdmins(Set<User> admins) {
		this.admins = admins;
	}

	/**
	 * Adds an admin user as an admin of this community.
	 * 
	 * @param admin
	 *            A non-null RSpaceAdmin or SysAdmin user.
	 * @return <code>true</code> if admin user was added, <code>false</code>
	 *         otherwise.
	 * @throws IllegalArgumentException
	 *             if <code>admin</code> is null or does not have an admin role.
	 */
	public boolean addAdmin(User admin) {
		assertAdminHasAdminRole(admin);
		return this.admins.add(admin);
	}

	/**
	 * Removes an admin user from the list of this community admins.<br/>
	 * Will not remove the admin if they are the sole admin.
	 * 
	 * @param admin
	 * @return <code>true</code> if admin was removed; <code>false</code>
	 *         otherwise.
	 */
	public boolean removeAdmin(User admin) {
		if (this.admins.size() == 1) {
			return false;
		}
		return this.admins.remove(admin);
	}

	/**
	 * Gets the LabGroups that belong to this community.<br/>
	 * Although this is a ManyToMany mapping, a LabGroup should only belong to
	 * one community; this mapping just allows an add/remove operation in a
	 * single atomic transaction.
	 * 
	 * @return A possibly empty but non-null set of LabGroups that belong to
	 *         this community
	 */
	@ManyToMany()
	@JoinTable(name = "community_labGroups", joinColumns = {
			@JoinColumn(name = "community_id") }, inverseJoinColumns = @JoinColumn(name = "group_id"))
	@XmlElementWrapper
	@XmlElement(name = "labGroup")
	@XmlIDREF
	public Set<Group> getLabGroups() {
		return labGroups;
	}

	/**
	 * For hibernate - clients should use add/remove group
	 */
	void setLabGroups(Set<Group> labGroups) {
		this.labGroups = labGroups;
	}

	/**
	 * Adds a group to this community, managing both sides of the relationship
	 * 
	 * @param group
	 *            where isLabGroup() = true
	 * @return <code>true</code> if group was added successfully;
	 *         <code>false</code> if group is not a LabGroup
	 */
	public boolean addLabGroup(Group group) {
		if (!group.isLabGroup()) {
			return false;
		}
		group.addCommunity(this);
		return this.labGroups.add(group);
	}

	/**
	 * Removes a LabGroup from this Community's collection of LabGroups
	 * 
	 * @param group
	 * @return <code>true</code> if was removed, <code>false</code> otherwise
	 */
	public boolean removeLabGroup(Group group) {
		group.removeCommunity(this);
		return this.labGroups.remove(group);
	}

	private void assertAdminHasAdminRole(User admin) {
		if (!admin.hasRole(Role.ADMIN_ROLE, Role.SYSTEM_ROLE)) {
			throw new IllegalArgumentException("A community admin must have  an admin role");
		}
	}

	@Override
	public String toString() {
		return "Community [id=" + id + ", displayName=" + displayName + ", uniqueId=" + uniqueName + ", creationDate="
				+ creationDate + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((uniqueName == null) ? 0 : uniqueName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Community other = (Community) obj;

		if (uniqueName == null) {
			if (other.uniqueName != null)
				return false;
		} else if (!uniqueName.equals(other.uniqueName))
			return false;
		return true;
	}

	/**
	 * Just for config in UI, not persisted
	 * 
	 * @return
	 */
	@Transient
	public List<User> getAvailableAdmins() {
		return availableAdmins;
	}

	/**
	 * Used for config in the UI
	 * 
	 * @return
	 */
	@Transient
	public List<Long> getAdminIds() {
		return adminIds;
	}

	public void setAdminIds(List<Long> adminIds) {
		this.adminIds = adminIds;
	}

	public void setAvailableAdmins(List<User> availableAdmins) {
		this.availableAdmins = availableAdmins;
	}

	@Transient
	public List<Group> getAvailableGroups() {
		return availableGroups;
	}

	public void setAvailableGroups(List<Group> availableGroups) {
		this.availableGroups = availableGroups;
	}

	@Transient
	public List<Long> getGroupIds() {
		return groupIds;
	}

	public void setGroupIds(List<Long> groupIds) {
		this.groupIds = groupIds;
	}

	@Override
	@Transient
	public AbstractEntityPermissionAdapter getPermissionsAdapter() {
		return new CommunityPermissionsAdapter(this);
	}
}
