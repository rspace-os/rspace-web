package com.researchspace.model.permissions;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpSession;

import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.Permission;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.PermissionsAdaptable;

/**
 * Collection of useful utility methods for interacting with Permissions
 */
public interface IPermissionUtils {

	/**
	 * Cleans out the cache used to store permissions used in memory for the subject. This will
	 * force a lookup from the database next time the permission is requested.
	 */
	void refreshCache();

	/**
	 * Given a set of permissions, this method will retrieve  the <em>first</em>Permission
	 * that matches the specified {@link PermissionDomain} and
	 * {@link IdConstraint}.
	 * 
	 * @param permissions
	 * @param domain
	 * @param idConstraint
	 * @return The found ConstraintBasedPermission, or <code>null</code> if no
	 *         such Permission was found.
	 */
	ConstraintBasedPermission findBy(Set<Permission> permissions, PermissionDomain domain, IdConstraint idConstraint);
	
	/**
	 * Given a set of permissions, this method will retrieve  the <em>first</em>Permission
	 * that matches the specified {@link PermissionDomain} and
	 * {@link IdConstraint}.
	 * 
	 * @param permissions
	 * @param domain
	 * @param idConstraint
	 * @param orderedActions List<PermissionType> order in which to match if there are multiple hits, in descending order of preference. 
	 * E.g. to preferentially return Write permission, orderedActions should be [write, read].
	 * @return The found ConstraintBasedPermission, or <code>null</code> if no
	 *         such Permission was found.
	 */
	ConstraintBasedPermission findBy(Set<Permission> permissions, PermissionDomain domain, IdConstraint idConstraint, List<PermissionType> orderedActions);

	/**
	 * Calling this method will send a notification that other subjects will
	 * refresh the permissions cache by calling refreshCacheIfNotified (). This
	 * method is needed as there is no way for a Subject to force cache
	 * refreshes of other Subjects explicitly.
	 * <p/>
	 * This method should be called for infrequently changed permissions on a
	 * user, group, or group's reosurces, that nevertheless should be instantly
	 * available to the group's members.
	 * 
	 * @param userOrGroup
	 *            A User or Group whose members should force refresh this cache/
	 *            <p/>
	 * 
	 */
	void notifyUserOrGroupToRefreshCache(AbstractUserOrGroupImpl userOrGroup);

	/**
	 * Will force a refesh of the cache, if a notification was made by
	 * <code>notifyGroupToRefreshCache (Group grp)</code>; otherwise will leave
	 * the Cache unchanged. This method should be called when Permissions may be
	 * coming from a group.<br/>
	 * If this method is not called, the Cache will expire as normal as
	 * configured in ehcache.xml.
	 * 
	 * @return true if cache was refreshed
	 */
	boolean refreshCacheIfNotified();

	/**
	 * Utility method to create a PermissionType from a String that is slightly
	 * more flexible than Enum.valueOf(string) - it will interpret Read/View and
	 * Edit/Write as synonyms in a case insensitive manner.
	 * <p/>
	 * E.g.,
	 * 
	 * <pre>
	 * // both of these return PermissionType.READ
	 * createFromString("read"); // ok
	 * createFromString("view"); // ok
	 * createFromString("xxxx"); // not ok , will throw IllegalArgumentException
	 * </pre>
	 * 
	 * @param type
	 * @return The {@link PermissionType}
	 * @throws IllegalArgumentException
	 *             if <code>type</code> is not one of the literals described
	 *             above.
	 */
	PermissionType createFromString(String type);

	/**
	 * Removes results from an {@link ISearchResults} where permissions are not
	 * satisfied. This method removes the objects in place and returns the same
	 * instance as was passed in as an argument.<br/>
	 * This method <em>only</em> removes items from the getResults() list; it
	 * does not update the properties pageNumber, totalHits etc., . It is the
	 * responsibility of the calling code to ensure that the object is still
	 * consistent.
	 * 
	 * @param toFilter
	 *            An {@link ISearchResults}
	 * @param permissionType
	 *            A {@link PermissionType} which needs to be tested.
	 * @return The {@link ISearchResults} object, with unauthorized objects
	 *         removed from the getResults() collection.
	 */
	<T extends PermissionsAdaptable> ISearchResults<T> filter(ISearchResults<T> toFilter,
			PermissionType permissionType, User authUser);

	/**
	 * Removes results from a collection of objects where permissions are not
	 * satisfied. This method removes the objects in place and returns the same
	 * instance as was passed in as an argument.<br/>
	 * 
	 * @param toFilter
	 *            An {@link Collection} of objects that implement
	 *            PermissionsAdaptable
	 * @param permissionType
	 *            A {@link PermissionType} which needs to be tested.
	 * @return The {@link Collection} object, with unauthorized objects removed.
	 */
	<U extends Collection<T>, T extends PermissionsAdaptable> U filter(U toFilter, PermissionType permissionType,
			User authUser);

	/**
	 * Single test for whether the permissionType is allowed for instance
	 * <code>toTest</code> If this request is coming direct from a client HTTP
	 * request, then an {@link AuthorizationException} should be thrown if this
	 * method returns <code>false</code>. It need not be especially logged, as
	 * it is logged elsewhere.
	 * <p>
	 * This method only tests permissions set on an individual object. <br>
	 * To look up transient associations for linked items in records, call
	 * isPermittedViaLinkedRecords.
	 * 
	 * @param toTest
	 *            An entity class annotated with the @PermissionAdaptable
	 *            annotation. Can be <code>null</code> ( in which case returns
	 *            false)
	 * @param permissionType
	 * @param authUser
	 *            subject
	 * @return <code>true</code> if the specified permission is authorized for
	 *         this object, <code>false</code> if <code>toTest</code> is
	 *         <code>null</code> or is not authorised
	 */
	<T extends PermissionsAdaptable> boolean isPermitted(T toTest, PermissionType permissionType, User authUser);

	/**
	 * Permissions test for whether an {@link IFieldLinkableElement} such as an
	 * image, annotation etc can be viewed, based on its containing document.
	 * 
	 * @param fieldLinkableElement
	 * @param permType
	 * @param authUser
	 * @return <code>true</code> if access permitted, <code>false</code>
	 *         otherwise.
	 */
	boolean isPermittedViaMediaLinksToRecords(IFieldLinkableElement fieldLinkableElement, PermissionType permType,
			User authUser);

	/**
	 * Alternative permission check when isPermitted(T toTest, PermissionType
	 * permissionType,User authUser) is inappropriate - for example, if a global
	 * permission that is not related to a specific test object is being
	 * checked. For example 'GROUP:CREATE'
	 * 
	 * @param permission
	 * @return
	 */
	boolean isPermitted(String permission);

	/**
	 * Boolean test for whether user has any of the given roles
	 * 
	 * @param user
	 * @param roles
	 * @return <code>true</code> if user has at least one role,
	 *         <code>false</code> otherwise.
	 */
	boolean isUserInRole(User user, Role... roles);

	/**
	 * Asserts the given permission, throwing an {@link AuthorizationException}
	 * if not permitted
	 * 
	 * @param toTest
	 * @param permType
	 * @param subject
	 * @param actionMsg
	 * @throws AuthorizationException
	 *             if no permission
	 */
	void assertIsPermitted(PermissionsAdaptable toTest, PermissionType permType, User subject, String actionMsg);

	
	/**
	 *  checks permission and throws AuthorizationException if failed 
	 * @param permission A permission String
	 * @param unauthorisedMsg
	 */
	void assertIsPermitted(String permission,  String unauthorisedMsg);
	
	
	/**
	 * Performs 'RunAs' operation for an admin to act as another user.
	 * <b>Note</b> See SysadminController for changes to session/active users that may be needed.
	 * <h3> Implementation note</h3>
	 * The default implementation is a no-op. 
	 * 
	 * @param session
	 * @param adminUser
	 * @param targetUser
	 */
	public default void doRunAs(HttpSession session, User adminUser, User targetUser) {}
	
	/**
	 * Convenience method to test for authorisation to a BaseRecord. Also checks if recordToCheck
	 *  has permissions via links.
	 * @param user
	 * @param recordToCheck
	 * @param permType
	 * @return <code>true</code> if authorised, <code>false</code> if not
	 */
	boolean isRecordAccessPermitted(User user, BaseRecord recordToCheck, PermissionType permType);
	
	/**
	 * Asserts permissions for authorisation to BaseRecord. Also checks if recordToCheck
	 *  has permissions via links.
	 * @param toTest
	 * @param permType
	 * @param subject
	 * @param actionMsg
	 * @throws AuthorizationException if record access not permitted
	 */
	void assertRecordAccessPermitted(BaseRecord toTest, PermissionType permType, User subject, 
			String actionMsg);

}
