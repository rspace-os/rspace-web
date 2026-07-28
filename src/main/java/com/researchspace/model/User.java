package com.researchspace.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.Person;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.UserPermissionAdapter;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.PermissionsAdaptable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.shiro.authz.Permission;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Sortable;

/**
 * This class represents the basic "user" object.
 *
 * User objects natural sort-order is compatible with equals() and is based on
 * username.
 */
@Entity
@Table(name = "User")
@XmlType
@AuditTrailData(auditDomain = AuditDomain.USER)
@NoArgsConstructor
public class User extends AbstractUserOrGroupImpl
		implements Serializable, UserOrGroup, Person, Comparable<User>, PermissionsAdaptable {

	private static final long serialVersionUID = 3832626162173359411L;

	/**
	 * Max length of default varchar field (255)
	 */
	public static final int DEFAULT_MAXFIELD_LEN = 255;

	/**
	 * Production usernames MUST be alphanumeric and preferably > 6 chars long
	 */
	public static final int MIN_UNAME_LENGTH = 6;

	public static final int MAX_UNAME_LENGTH = 50;

	public static final int MIN_PWD_LENGTH = 8;
    /*
     * This is limited by our use of BCrypt for validation password encoding. This doesn't strictly affect our main
     * password field, which is still using salted SHA256, but for consistency in the frontend we set the limits to be
     * the same.
     * 
     * TODO: RSDEV-894 Remove this limit when we migrate to Argon2
     */
	public static final int MAX_PWD_LENGTH = 50;

	/**
	 * Core regex for dis-allowed username characters
	 */
	public static final String DISALLOWED_USERNAME_CHARS_REGEXP = "[^" + ALLOWED_USERNAME_CHARS + "]";

	/**
	 * Core regex for allowed username characters
	 */
	public static final String ALLOWED_USERNAME_CHARS_SUBREGEX = "[" + ALLOWED_USERNAME_CHARS + "]{" + MIN_UNAME_LENGTH
			+ ",}";

	/**
	 * At least 6 chars, alphanumeric preferably, matching a whole line
	 */
	public static final String ALLOWED_USERNAME_CHARS_REGEX = "^" + ALLOWED_USERNAME_CHARS_SUBREGEX + "$";

	/**
	 * No restriction on length ( other than > 0 characters). For use in SSO
	 * environments where we can't control username, matches whole line.
	 */
	public static final String ALLOWED_USERNAME_CHARS_RELAXED_LENGTH_REGEX = "^"
			+ ALLOWED_USERNAME_CHARS_RELAXED_SUBREGEX + "$";

	public static final String ALLOWED_PWD_CHARS_REGEX = "^[ -~]{" + MIN_PWD_LENGTH + "," + MAX_PWD_LENGTH + "}$";

	private static final Pattern multiUserPattern = Pattern.compile(",?\\s*([^<,]+)(<.+?>)?");

	private Date loginFailure;
	private Date lastLogin;
	@Setter
	private byte numConsecutiveLoginFailures;
	@Setter
	private String username; // required, 6 chars alphanumeric
	private String usernameAlias; // optional
	@Setter
	private String password; // required
	@Setter
	private String verificationPassword; // required
	@Setter
	private String confirmPassword;
	@Setter
	private SignupSource signupSource = SignupSource.MANUAL; // default
	@Setter
	private String sid; // optional security identifier

	@Setter
	private String captcha; // optional signup captcha
	@Setter
	private boolean picreateGroupOnSignup;
	@Setter
	private String signupCode = "";

	private String firstName; // required
	private String lastName; // required
	private String email; // required; unique
	@Setter
	private String salt;
	@Setter
	private String role;
	@Setter
	private boolean enabled = true;
	private boolean accountExpired;
	@Setter
	private boolean accountLocked;
	@Setter
	private boolean tempAccount;
	// optional; mandatory in cloud version
	private String affiliation = "";

	private boolean credentialsExpired;
	@Setter
	private boolean contentInitialized;
	@Setter
	private boolean allowedPiRole;
	@Setter
	private Set<Role> roles = new HashSet<>();
	@Setter(AccessLevel.PACKAGE)
	private Set<UserPreference> userPreferences = new HashSet<>();
	private Set<UserGroup> userGroups = new HashSet<>();
	private Folder rootFolder;
	@Setter
	private String tagsJsonString;

	// transient and optional 
	@Setter
	private String token; // used to back signup form in cloud version
	@Setter
	private UserAuthenticationMethod authenticatedBy;
	@Setter
	private Set<User> connectedUsers;
	@Setter
	private Set<Group> connectedGroups;

	/**
	 * Create a new instance and set the username.
	 * 
	 * @param username
	 *            login name for user.
	 */
	public User(final String username) {
		this.username = username;
	}

	/**
	 * Property used only as a temporary property  for a new User on the signup page, if enabled.
	 * <br/> This property is not persisted and calling this method for a persisted user will always 
	 * return <code>false</code>.
	 * @return <code>true</code> if user should be signed up as a PI with an empty lab group
	 * @see <a href="https://researchspace.atlassian.net/browse/RSPAC-1336">RSPAC-1336</a>
	 */
	@Transient
	public boolean isPicreateGroupOnSignup() {
		return picreateGroupOnSignup;
	}

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	public SignupSource getSignupSource() {
		return signupSource;
	}

	/**
	 * Optional security identifier, can be used to ensure that we deal with the same user in SSO 
	 * deployments where people's usernames are reused after they leave the institution. 
	 */
	@Column(length = DEFAULT_MAXFIELD_LEN, unique = true)
	public String getSid() {
		return sid;
	}

	/**
	 * Getter for verification token; this is used to verify cloud-based signup
	 * and is only relevant in cloud-deployment.
	 */
	@Transient
	public String getToken() {
		return token;
	}

	/**
	 * If this object represents the currently logged-in User, this method may store
	 * info about authentication method used for logging the user into RSpace.
	 */
	@Transient
	public UserAuthenticationMethod getAuthenticatedBy() {
		return authenticatedBy;
	}

	/**
	 * Whether user passed as a parameter is a connection of current users.
	 * Requires prior call to {@link #setConnectedUsers(Set)}.
	 */
	@Transient
	public boolean isConnectedToUser(User user) {
	    if (connectedUsers == null) {
	        throw new IllegalStateException("asked for connected users, but the list was not populated"); 
	    }
        return hasSysadminRole() || connectedUsers.contains(user);
    }

	/**
     * Whether current user is somehow connected to a group passed as a parameter.
     * Requires prior call to {@link #setConnectedGroups(Set)}.
     */
    @Transient
    public boolean isConnectedToGroup(Group group) {
        if (connectedGroups == null) {
            throw new IllegalStateException("asked for connected group, but the list was not populated");
        }
        return hasSysadminRole() || connectedGroups.contains(group);
    }

	/**
	 * For cloud deployment, this is mandatory, but is optional for other
	 * product variants, so must be allowed to be nullable.
	 * 
	 * @return the affiliation of the user.
	 */
	@Column(length = DEFAULT_MAXFIELD_LEN)
	@AuditTrailProperty(name = "affiliation")
	@XmlElement
	public String getAffiliation() {
		return affiliation;
	}

	/**
	 * @param affiliation
	 *            the affiliation to set
	 */
	public void setAffiliation(String affiliation) {
		this.affiliation = StringUtils.abbreviate(affiliation, DEFAULT_MAXFIELD_LEN);
	}

	/**
	 * API method to set affiliation. Performs validation based on the product
	 * type
	 * 
	 * @param affiliation
	 * @param productType
	 * @throws IllegalArgumentException
	 *             if <code>productType</code> is <code>null</code> or if
	 *             <code>affiliation</code> is <code>null</code> when it is
	 *             disallowed by the product variant. See RSPAC-246
	 */
	public void setAffiliation(String affiliation, ProductType productType) {
		validateArgs(affiliation, productType);
		setAffiliation(affiliation);
	}

	private void validateArgs(String affiliation, ProductType productType) {
		if (productType == null) {
			throw new IllegalArgumentException("product type cannot be null!");
		}
		if (ProductType.COMMUNITY.equals(productType) && StringUtils.isBlank(affiliation)) {
			throw new IllegalArgumentException("Affiliation can't be null for product type " + productType);
		}
	}

	/**
	 * @return the tempAccount
	 */
	@Column(nullable = false)
	public boolean isTempAccount() {
		return tempAccount;
	}

	/**
	 * compares by user.getLastName()
	 */
	public static final Comparator<User> LAST_NAME_COMPARATOR = (User o1, User o2) -> {
		if (o1.getLastName() != null && o2.getLastName() != null) {
			return o1.getLastName().compareTo(o2.getLastName());
		} else {
			return 0;
		}
	};

	/**
	 * Utility method to parse out usernames from an auto-complete string formatted as
	 * "user1<Bob Jones>,user 2<Simon Smith>,user3",
	 * <p/>
	 * i.e. a comma separated list of usernames with optional extra information
	 * between angle brackets
	 * 
	 * @param multiuserString
	 * @return extracted usernames
	 */
	public static String[] getUsernamesFromMultiUser(String multiuserString) {
		Matcher m = multiUserPattern.matcher(multiuserString);
		Set<String> uname = new TreeSet<>();
		while (m.find()) {
			if (m.group(1) != null && !StringUtils.isBlank(m.group(1))) {
				uname.add(m.group(1).trim());
			}
		}
		String[] names = new String[uname.size()];
		return uname.toArray(names);
	}

	/**
	 * Date/time of last login attempt. If the last attempt was successful, this
	 * should be the same value as lastLogin
	 * 
	 * @return
	 */
	@Temporal(TemporalType.TIMESTAMP)
	public Date getLoginFailure() {
		return loginFailure == null ? null : new Date(loginFailure.getTime());
	}

	public void setLoginFailure(Date loginFailure) {
		this.loginFailure = loginFailure == null ? null : new Date(loginFailure.getTime());
	}

	@Column
	public byte getNumConsecutiveLoginFailures() {
		return numConsecutiveLoginFailures;
	}

	/**
	 * Date/time of last <b>successful</b> login
	 * 
	 * @return
	 */
	@Temporal(TemporalType.TIMESTAMP)
	public Date getLastLogin() {
		return lastLogin == null ? null : new Date(lastLogin.getTime());
	}

	public void setLastLogin(Date lastLogin) {
		this.lastLogin = lastLogin == null ? null : new Date(lastLogin.getTime());
	}

	/**
	 * Gets salt used to modify passwords to prevent dictionary attacks
	 * 
	 * @return
	 */
	@Column(length = 24)
	// 16 byte salt Base64 encoded
	public String getSalt() {
		return salt;
	}

	/**
	 * Return true if default content has been created for this user;
	 * 
	 * @return
	 */
	public boolean isContentInitialized() {
		return contentInitialized;
	}

	/**
	 * Can user self-declare themselves to be a PI, not a regular user.
	 * 
	 * @see <a href="https://researchspace.atlassian.net/browse/RSPAC-2588">RSPAC-2588</a>
	 */
	@ColumnDefault("false")
	public boolean isAllowedPiRole() {
		return allowedPiRole;
	}

	/**
	 * Gets persisted preferences
	 * 
	 * @return
	 */
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "user", cascade = CascadeType.ALL)
	@JsonIgnore
	public Set<UserPreference> getUserPreferences() {
		return userPreferences;
	}

	@Transient
	@JsonIgnore
	public Set<UserPreference> getAllUserPreferences() {
		return Arrays.stream(Preference.values()).filter(p->isApplicable(p)).map(p -> getValueForPreference(p))
				.collect(Collectors.toCollection(() -> new HashSet<>()));
	}

	private boolean isApplicable(Preference p) {
		if(Preference.NOTIFICATION_DOCUMENT_DELETED_PREF.equals(p)){
			return hasAnyPiOrLabGroupViewAllRole();
		} else {
			return true;
		}
	}

	/**
	 * Test for whether this user has any PI role or ViewAll LabAdmin in a group
	 */
	public boolean hasAnyPiOrLabGroupViewAllRole() {
		return getGroups().stream().anyMatch(g->hasRoleInGroup(g, RoleInGroup.PI)
				   ||  g.getUserGroupForUser(this).isAdminViewDocsEnabled() );
	}

	/**
	 * This is just for signup page, it's not for persisting
	 * 
	 * @return One of Role constants name
	 */
	@Transient
	public String getRole() {
		return role;
	}

	@Transient
	@Override
	public String getUniqueName() {
		return getUsername();
	}

	@Column(nullable = false, length = MAX_UNAME_LENGTH, unique = true)
	@AuditTrailProperty(name = "username")
	@XmlElement(required = true)
	@XmlID
	@KeywordField(normalizer = "structureAnalyzer", projectable = Projectable.NO, sortable = Sortable.NO)
	public String getUsername() {
		return username;
	}

	@Column(length = MAX_UNAME_LENGTH, unique = true)
	public String getUsernameAlias() {
		return usernameAlias;
	}

	public void setUsernameAlias(String usernameAlias) {
		// don't save blank string as an alias, that's surely not intended
		this.usernameAlias = StringUtils.isBlank(usernameAlias) ? null : usernameAlias;
	}

	@Column(nullable = false)
	public String getPassword() {
		return password;
	}
	
	/**
	 * This is an optional column for use in SSO environments. Can be null
	 * @return
	 */
	@Column
	public String getVerificationPassword() {
		return verificationPassword;
	}

	@Transient
	public String getConfirmPassword() {
		return confirmPassword;
	}

	@Transient
	public String getCaptcha() {
		return captcha;
	}

	@Column(name = "first_name", nullable = false, length = MAX_UNAME_LENGTH)
	// @SearchableProperty
	@XmlElement(required = true)
	public String getFirstName() {
		return firstName;
	}

	@Column(name = "last_name", nullable = false, length = MAX_UNAME_LENGTH)
	@XmlElement(required = true)
	public String getLastName() {
		return lastName;
	}

	@Column(nullable = false)
	@AuditTrailProperty(name = "email")
	@XmlElement(required = true)
	public String getEmail() {
		return email;
	}

	@Transient
	public String getFullNameAndUserNameAndRole() {
		String rc = getFullName() + " (" + getUsername() + ")";
		if (hasRole(Role.PI_ROLE)) {
			rc = rc + " - PI ";
		}
		return rc;
	}

	/**
     * Generates a publicly consumable basic user data object
     */
    public UserBasicInfo toBasicInfo() {
        UserBasicInfo ubi = new UserBasicInfo();
        populateBasicInfo(ubi);
        return ubi;
    }

	/**
	 * Generates a publicly consumable user data lacking any sensitive security
	 * info
	 *
	 * @return
	 */
	public UserPublicInfo toPublicInfo() {
		UserPublicInfo pui = new UserPublicInfo();
		populateBasicInfo(pui);
		pui.setLastLogin(getLastLogin());
		pui.setAccountLocked(isAccountLocked());
		pui.setEnabled(isEnabled());
		pui.setTemporary(isTempAccount());
		pui.setUsernameAlias(getUsernameAlias());
		return pui;
	}

	private void populateBasicInfo(UserBasicInfo info) {
		info.setId(getId());
		info.setUsername(username);
		info.setFirstName(firstName);
		info.setLastName(lastName);
		info.setEmail(email);
		List<String> roleList = TransformerUtils.transformToString(getRoles(), "name");
		info.setRole(StringUtils.join(roleList, ","));
		info.setAffiliation(getAffiliation());
	}

	/**
	 * Returns the full name.
	 * 
	 * @return firstName + ' ' + lastName
	 */
	@Transient
	public String getFullName() {
		return firstName + " " + lastName;
	}

	/**
	 * Returns the full name, last name first
	 * 
	 * @return lastName + ', ' + firstName
	 */
	@Transient
	public String getFullNameSurnameFirst() {
		return lastName + ", " + firstName;
	}

	/**
	 * Returns the full name and email.
	 * 
	 * @return firstName + ' ' + lastName + (email)
	 */
	@Transient
	public String getFullNameAndEmail() {
		return getFullName() + " (" + email + ")";
	}

	/**
	 * A display name for the User. This implementation delegates to
	 * getFullName()
	 * 
	 * @return a String for display to human viewers
	 */
	@Transient
	public String getDisplayName() {
		return getFullName();
	}

	@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "user_role", joinColumns = {
			@JoinColumn(name = "user_id") }, inverseJoinColumns = @JoinColumn(name = "role_id"))
	@XmlElementWrapper(name = "listOfRoles")
	@XmlElement(name = "role", required = true)
	public Set<Role> getRoles() {
		return roles;
	}

	@Transient
	public String getRolesNamesAsString() {
		if (CollectionUtils.isEmpty(roles)) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (Role r : roles) {
			sb.append(r.getName() + " ");
		}
		sb.delete(sb.length() - 1, sb.length());
		return sb.toString();
	}

	/**
	 * Adds a role for the user
	 * 
	 * @param role
	 *            the fully instantiated role
	 */
	public void addRole(Role role) {
		if (role == null) {
			throw new IllegalArgumentException("Role cannot be null");
		}
		getRoles().add(role);
	}

	/**
	 * Removes this role if the user has this role and it is not the ONLY role
	 * that the user has.<br/>
	 * I.e., this operation will not succeed and will return <code>false</code>
	 * if removing this role would leave the user with no roles at all.
	 * 
	 * @param role
	 * @return <code>true</code> if this role was removed from this user's
	 *         roles.
	 */
	@Transient
	public boolean removeRole(Role role) {
		if (roles.contains(role) && roles.size() == 1) {
			return false;
		}
		return roles.remove(role);
	}

	/**
	 * Convenience method to tell if user has a particular role
	 * 
	 * @param rolesToTest
	 *            one or more Roles
	 * @return <code>true</code> if this user has any of the specified roles.
	 */
	@Transient
	public boolean hasRole(Role... rolesToTest) {
		if (rolesToTest != null) {
			for (Role toTest: rolesToTest) {
				for (Role role: roles) {
					// this explicit test for name is required because 
					// a simple 'contains(Role) call on underlying Persistent set
					// mysteriously sometimes returns false, see RSPAC-1589
					if(toTest.getName().equals(role.getName())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Column(name = "account_enabled")
	public boolean isEnabled() {
		return enabled;
	}

	@Column(name = "account_expired", nullable = false)
	@Deprecated
	public boolean isAccountExpired() {
		return accountExpired;
	}

	/**
	 * @return true if account is still active
	 */
	@Transient
	@Deprecated
	public boolean isAccountNonExpired() {
		return !isAccountExpired();
	}

	@Column(name = "account_locked", nullable = false)
	public boolean isAccountLocked() {
		return accountLocked;
	}

	/**
	 * @return false if account is locked
	 */
	@Transient
	public boolean isAccountNonLocked() {
		return !isAccountLocked();
	}

	@Column(name = "credentials_expired", nullable = false)
	@Deprecated
	public boolean isCredentialsExpired() {
		return credentialsExpired;
	}

	/**
	 * @return true if credentials haven't expired
	 */
	@Transient
	@Deprecated
	public boolean isCredentialsNonExpired() {
		return !credentialsExpired;
	}

	public void setFirstName(String firstName) {
		this.firstName = StringUtils.abbreviate(firstName, MAX_UNAME_LENGTH);
	}

	public void setLastName(String lastName) {
		this.lastName = StringUtils.abbreviate(lastName, MAX_UNAME_LENGTH);
	}

	/**
	 * Sets email address
	 * 
	 * @param email
	 */
	public void setEmail(String email) {
		if (email != null && email.length() >= DEFAULT_MAXFIELD_LEN) {
			// if too long to fit in DB column
			throw new IllegalArgumentException("Attempt to set  too long an email -should be < 255 characters");
		}
		this.email = email;
	}

	@Deprecated
	public void setAccountExpired(boolean accountExpired) {
		this.accountExpired = accountExpired;
	}

	@Deprecated
	public void setCredentialsExpired(boolean credentialsExpired) {
		this.credentialsExpired = credentialsExpired;
	}

	@Column(length = DEFAULT_MAXFIELD_LEN)
	protected String getTagsJsonString() {
		return tagsJsonString;
	}

	@Transient
	public List<String> getTagsList() {
		if (StringUtils.isEmpty(getTagsJsonString())) {
			return Collections.emptyList();
		}
		return JacksonUtil.fromJson(getTagsJsonString(), List.class);
	}

	@Transient
	public void setTagsList(List<String> tags) {
		setTagsJsonString(JacksonUtil.toJson(tags));
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null) {
			return false;
		}
		if (getClass() != o.getClass() && !getClass().isAssignableFrom(o.getClass())) {
			return false;
		}
		final User user = (User) o;
		return !(username != null ? !username.equals(user.getUsername()) : user.getUsername() != null);
	}

	/**
	 * {@inheritDoc}
	 */
	public int hashCode() {
		return (username != null ? username.hashCode() : 0);
	}

	/**
	 * {@inheritDoc}
	 */
	public String toString() {
		ToStringBuilder sb = new ToStringBuilder(this).append("username", this.username).append("enabled", this.enabled)
				.append("accountExpired", this.accountExpired).append("credentialsExpired", this.credentialsExpired)
				.append("accountLocked", this.accountLocked);

		if (roles != null) {
			sb.append("Roles: ");
			sb.append(StringUtils.join(roles, ","));
		} else {
			sb.append("No Roles");
		}
		return sb.toString();
	}

	@Transient
	@Override
	public boolean isGroup() {
		return false;
	}

	@Transient
	@Override
	public boolean isUser() {
		return true;
	}

	/**
	 * USers can belong to > 1 group
	 * 
	 * @return
	 */
	@OneToMany(fetch = FetchType.EAGER, mappedBy = "user")
	public Set<UserGroup> getUserGroups() {
		return userGroups;
	}

	public void setUserGroups(Set<UserGroup> userGroups) {
		this.userGroups = userGroups;
	}

	/**
	 * Gets an unmodifiable Set of all groups that this user belongs to, both
	 * directly and indirectly
	 * 
	 * @return A possibly empty but non-null Set.
	 */
	@Transient
	public Set<Group> getGroups() {
		Set<Group> allGroups = userGroups.stream().map(ug -> ug.getGroup())
				.collect(Collectors.toCollection(() -> new HashSet<>()));
		return Collections.unmodifiableSet(allGroups);
	}

	/**
	 * Boolean test as to whether user <code>other</code> shares a group with
	 * this user.
	 * 
	 * @param other
	 * @return <code>true</code> if they share a group, <code>false</code>
	 *         otherwise or if <code>other</code> is <code>null</code>.
	 */
	@Transient
	public boolean isInSameGroupAs(User other) {
		if (other == null) {
			return false;
		}
		return userGroups.stream().anyMatch(userGrp -> userGrp.getGroup().hasMember(other));
	}

	/**
	 * Gets all permissions for all groups that this user belongs to.
	 * 
	 * @param includeDisabled
	 *            Boolean choice as to whether to include disabled permissions
	 *            in the returned Collection.
	 * @param inherit
	 *            boolean as to whether to consider inherited permissions from
	 *            Group/UserGroup or not. (<code>true</code>) or not (
	 *            <code>false</code>).
	 * @return A new Set containing all permissions applicable to this user.
	 *         Modifying this collection won't alter the user' underlying
	 *         permissions.
	 */
	@Transient
	public Set<Permission> getAllPermissions(boolean includeDisabled, boolean inherit) {
		Set<Permission> rc = new HashSet<>();

		for (Role role : getRoles()) {
			rc.addAll(role.getPermissions());
		}

		if (!includeDisabled) {
			for (Permission cbp : getPermissions()) {
				if (((ConstraintBasedPermission) cbp).isEnabled()) {
					rc.add(cbp);
				}
			}
		} else {
			rc.addAll(getPermissions());
		}
		if (!inherit) {
			return rc;
		}
		for (UserGroup ug : userGroups) {
			if (!ug.isIncludePermissions()) {
				continue;
			}
			Set<Permission> groupPerms = ug.getGroup().getPermissions();
			if (!includeDisabled) {
				for (Permission cbp : groupPerms) {
					if (((ConstraintBasedPermission) cbp).isEnabled()) {
						rc.add(cbp);
					}
				}
				for (Permission cbp : ug.getPermissions()) {
					if (((ConstraintBasedPermission) cbp).isEnabled()) {
						rc.add(cbp);
					}
				}
			} else {
				rc.addAll(groupPerms);
				rc.addAll(ug.getPermissions());
			}
		}

		return rc;
	}

	/**
	 * Sets whether to include permissions from the specified group or not.
	 * 
	 * @param group
	 * @param include
	 */
	public void setIncludePermissionForGroup(Group group, boolean include) {
		userGroups.stream().filter(ug -> ug.getGroup().equals(group))
				.forEach(ug -> ug.setIncludePermissions(include));
	}

	/**
	 * Boolean test for whether this user is a member of the specified Group.
	 */
	public boolean hasGroup(Group group) {
		for (UserGroup g : userGroups) {
			if (g.getGroup().equals(group)) {
				return true;
			}
		}
		return false;
	}

	@Transient
	@Override
	public boolean isPermitted(Permission p, boolean inherit) {
		Set<Permission> permissionSet = getAllPermissions(false, inherit);
		for (Permission userp : permissionSet) {
			ConstraintBasedPermission cbp = (ConstraintBasedPermission) userp;
			cbp.setUser(this);
			if (cbp.implies(p)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Convenience boolean test for whether this User owns the argument
	 * {@link BaseRecord}
	 * 
	 * @param record
	 * @return
	 */
	public boolean isOwnerOfRecord(BaseRecord record) {
		return this.equals(record.getOwner());
	}

	@OneToOne(fetch = FetchType.LAZY)
	public Folder getRootFolder() {
		return rootFolder;
	}

	public void setRootFolder(Folder rootFolder) {
		this.rootFolder = rootFolder;
	}

	/**
	 * Overwrites existing Preference for user if it exists. This depends on
	 * implementation of equals() for UserPreference, which only takes account
	 * of user and preference, not the value.
	 * 
	 * @param upNew
	 */
	public void setPreference(UserPreference upNew) {
		boolean exists = false;
		for (UserPreference up : userPreferences) {
			if (up.equals(upNew)) {
				up.setValue(upNew.getValue());
				exists = true;
			}
		}
		if (!exists) {
			userPreferences.add(upNew);
		}
	}

	/**
	 * If user has a preference stored, returns the value; else will return the
	 * default value. This means we only need to persist non-default values.
	 * 
	 * @param preference
	 * @return A {@link String} of the value
	 */
	public UserPreference getValueForPreference(Preference preference) {
		return  userPreferences.stream()
				.filter(up -> up.getPreference().equals(preference)).findFirst()
				.orElse(new UserPreference(preference, this, null));

	}

	/**
	 * Boolean test for whether user's preferences are set up for notification
	 * 
	 * @param notificationType
	 * @return
	 */
	public boolean wantsNotificationFor(NotificationType notificationType) {
		//ignore user prefs for background notifications as the the only way to access
		// the export is through the notification RSPAC-1970
		if (NotificationType.ARCHIVE_EXPORT_COMPLETED.equals(notificationType)) {
			return true;
		}
		String type = notificationType.toString();
		type = type + "_PREF";
		Preference pref = Preference.valueOf(type);
		return getValueForPreference(pref).getValue().equalsIgnoreCase(Boolean.TRUE.toString());
	}

	@Override
	public int compareTo(User other) {
		return username.compareTo(other.getUsername());
	}

	/**
	 * GEts the collaboration groups to which this user belongs
	 * 
	 * @return the set of CollaborationGroups, or emtyp set if there are none.
	 */
	@Transient
	public Set<Group> getCollaborationGroups() {
		Set<Group> allGroups = userGroups.stream().map((g) -> g.getGroup()).filter((grp) -> grp.isCollaborationGroup())
				.collect(Collectors.toCollection(() -> new HashSet<>()));
		return Collections.unmodifiableSet(allGroups);
	}

	/**
	 * Gets a set of all users in the groups that this user belongs to.
	 * 
	 * @return
	 */
	@Transient
	public Set<User> getAllGroupMembers() {
		return getUserGroups().stream().flatMap(ug -> ug.getGroup().getMembers().stream()).collect(Collectors.toSet());
	}

	@Override
	@Transient
	public AbstractEntityPermissionAdapter getPermissionsAdapter() {
		UserPermissionAdapter rc = new UserPermissionAdapter(this);
		rc.setDomain(PermissionDomain.USER);
		return rc;
	}

	/**
	 * Convenience method to determine if user has any admin role
	 * 
	 * @return
	 */
	@Transient
	public boolean hasAdminRole() {
		return hasRole(Role.SYSTEM_ROLE, Role.ADMIN_ROLE);
	}
	
	/**
	 * Convenience method to determine if user  is a sysadmin
	 * @return
	 */
	@Transient
	public boolean hasSysadminRole() {
		return hasRole(Role.SYSTEM_ROLE);
	}

	@Override
	@Transient
	protected GlobalIdPrefix getGlobalIdPrefix() {
		return GlobalIdPrefix.US;
	}

	/**
	 * Convenience method to identify the first LabGroup of a PI. This
	 * implementation just uses the first lab group that the user is a PI in.
	 * 
	 * @return A Group or <code>null</code> if:
	 *         <ul>
	 *         <li>the user is not a PI
	 *         <li>The user has no Lab groups that they're PI of.
	 *         </ul>
	 */
	@Transient
	public Group getPrimaryLabGroupWithPIRole() {
		if (!hasRole(Role.PI_ROLE)) {
			return null;
		}
		return userGroups.stream()
				.filter(ug -> RoleInGroup.PI.equals(ug.getRoleInGroup()) && ug.getGroup().isLabGroup())
				.map(ug -> ug.getGroup()).findFirst().orElse(null);

	}

	/**
	 * Boolean test for whether this user is a PI and is associated with a lab
	 * group that they're PI of.
	 * 
	 * @return
	 */
	@Transient
	public boolean isPIOfLabGroup() {
		if (!hasRole(Role.PI_ROLE)) {
			return false;
		}

		for (UserGroup ug : userGroups) {
			if (RoleInGroup.PI.equals(ug.getRoleInGroup()) && ug.getGroup().isLabGroup()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Convenience boolean test for whether this user has a global PI role (
	 * <code>true</code>) or not (<code>false</code>)
	 * 
	 * @return a boolean
	 */
	@Transient
	public boolean isPI() {
		return hasRole(Role.PI_ROLE);
	}

	/**
	 * Boolean test for whether this user has the specified role in the group.
	 * 
	 * @param grp
	 * @param role
	 * @return <code>true</code> if user has the role, false if not.
	 */
	public boolean hasRoleInGroup(Group grp, RoleInGroup role) {
		return grp.getUsersByRole(role).contains(this);
	}
	
	/**
	 * Boolean test for whether this user has PI or Lab Admin role in the group
	 * @param group
	 * @return <code>true</code> if user is PI or LabAdmin of the group, false if not.
	 */
	@Transient
	public boolean isPiOrLabAdminOfGroup(Group group) {
		return group.getUsersByRole(RoleInGroup.PI, RoleInGroup.RS_LAB_ADMIN).contains(this);
	}

	/**
	 * Boolean test for whether login should be blocked, based on internal state
	 * of User.
	 */
	@Transient
	public boolean isLoginDisabled() {
		return isAccountLockedOrDisabled() || isAccountLockedAwaitingAuthorisation() || isAnonymousGuestAccount();
	}

	/**
	 * Boolean convenience method to determine if account is locked or disabled.
	 *
	 * Deprecated - you probably want to check {@link #isLoginDisabled()}
	 */
	@Deprecated
	@Transient
	public boolean isAccountLockedOrDisabled() {
		return isAccountLocked() || !(isEnabled());
	}

	/**
	 * For setups required authorisation of new accounts by an admin, this
	 * method returns if account is locked for this reason
	 */
	@Transient
	public boolean isAccountLockedAwaitingAuthorisation() {
		return isAccountLocked() && !isContentInitialized();
	}

	/**
	 * For setups requiring email verification of new accounts, this
	 * method returns if account is locked for this reason
	 */
	@Transient
	public boolean isAccountAwaitingEmailConfirmation() {
		return !isEnabled() && !isAccountLocked() && !isContentInitialized();
	}

	/**
	 * To determine if account belongs to RSpace anonymous user 
	 * @return
	 */
	@Transient
	public boolean isAnonymousGuestAccount() {
		return RecordGroupSharing.ANONYMOUS_USER.equals(getUsername());
	}

	/**
	 * Gets non-PI users in  groups that this user is a PI or viewAllLabAdmin for.
	 * Can be used to retrieve users that this user has automatic read-permission for.
	 * <br/> If this user is not a PI or labAdmin-viewAll, then returns an empty set.
	 */
	@Transient
	public Set<User> getNonPiLabGroupMembersForPiOrViewAllAdmin() {
		Set<User> rc = new HashSet<>();		
		for (Group grp: getGroups()) {
			if(hasRoleInGroup(grp, RoleInGroup.PI) || grp.getLabAdminsWithViewAllPermission().contains(this)) {
				rc.addAll(grp.getAllNonPIMembers());
			}
		}
		return rc;
	}

	/**
	 * Gets a set of PIs and viewAll lab admins that can see this user's work by default from:
	 * <br>
	 * - a list of groups if groups specified
	 * <br>
	 * - all groups that this user belongs to otherwise
	 *
	 * @return empty set if user is not in a lab group or if user is PI
	 */
	@Transient
	public Set<User> getGroupMembersWithViewAll(Group... groupArgs) {
		Set<User> rc = new HashSet<>();

		if (isPI()) {
			return rc; // no one can see PIs work.
		}

		Set<Group> groups = new HashSet<>(Arrays.asList(groupArgs));

		for (Group group : getGroups()) {
			if (groups.size() == 0 || groups.contains(group)) {
				rc.addAll(group.getMembersWithDefaultViewAllPermissions());
			}
		}

		return rc;
	}
	
	/**
	 * If deployment property 'user.signup.signupCode' is non-blank, then the user signup must
	 *  include a string that matches the code. <br/>. RSPAC-1796
	 *  This  property is only used as additional security on user-signup and is not persisted
	 * @return
	 */
	@Transient
	public String getSignupCode() {
		return signupCode;
	}

	/**
	 * Get a possibly empty set of Groups that this user has chosen to autoshare into
	 * @return
	 */
	@Transient
	public Set<Group> getAutoshareGroups() {
		return getUserGroups().stream()
				.filter(UserGroup::isAutoshareEnabled)
				.map(UserGroup::getGroup)
				.collect(Collectors.toSet());
	}
	/**
	 * Boolean test for whether user has any auto-share groups.
	 * @return
	 */
	@Transient
	public boolean hasAutoshareGroups() {
		return getUserGroups().stream()
				.anyMatch(UserGroup::isAutoshareEnabled);
	}

}
