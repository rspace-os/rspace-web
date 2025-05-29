package com.researchspace.auth;

import static org.apache.commons.lang.StringUtils.join;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.permissions.AbstractEntityPermissionAdapter;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.IdConstraint;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.PermissionsAdaptable;
import com.researchspace.session.SessionAttributeUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PermissionUtils implements IPermissionUtils {

  private static final Logger log = LoggerFactory.getLogger(PermissionUtils.class);
  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);
  private ConstraintPermissionResolver parser = new ConstraintPermissionResolver();

  @Autowired protected BaseRecordAdaptable recordAdapter;

  public PermissionUtils() {}

  private Set<String> usersToNotify =
      Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

  /**
   * Refreshes shiro cache for logged in subject. Needs to do some hacky casting due to spring AOP
   * proxy objects
   */
  public void refreshCache() {
    PrincipalCollection principals = SecurityUtils.getSubject().getPrincipals();
    log.info(
        "Refreshing authorisation cache for [{}]",
        principals == null ? "" : principals.getPrimaryPrincipal());
    try {
      SecurityUtils.getSecurityManager();
    } catch (UnavailableSecurityManagerException e) {
      log.error("No security manager registered", e);
      return;
    }
    for (Realm r : ((DefaultSecurityManager) SecurityUtils.getSecurityManager()).getRealms()) {
      RSpaceRealm sr = null;
      if (AopUtils.isJdkDynamicProxy(r)) {
        try {
          sr = (RSpaceRealm) ((Advised) r).getTargetSource().getTarget();
        } catch (Exception e) {
          log.warn("exception during refreshing permission utils cache: {}", e.getMessage());
        }
      } else {
        // expected to be cglib proxy then, which is simply a specialized class
        sr = (RSpaceRealm) r;
      }
      if (sr != null) {
        sr.clearCache(SecurityUtils.getSubject().getPrincipals());
      }
    }
  }

  public void notifyUserOrGroupToRefreshCache(AbstractUserOrGroupImpl userOrGroup) {
    if (userOrGroup.isGroup()) {
      log.info("Notifying group {} to refresh permissions cache", userOrGroup.getUniqueName());
      for (User u : userOrGroup.asGroup().getMembers()) {
        log.info("Notifying user {} to refresh permissions cache", u.getUsername());
        usersToNotify.add(u.getUsername());
      }
    } else {
      log.info(
          "Notifying user {} to refresh permissions cache", userOrGroup.asUser().getUsername());
      usersToNotify.add(userOrGroup.asUser().getUsername());
    }
  }

  public boolean refreshCacheIfNotified() {

    String subjectUserName = getSubjectUserName();
    boolean refreshCache = false;
    if (subjectUserName != null) {
      synchronized (usersToNotify) {
        if (usersToNotify.contains(subjectUserName)) {
          log.info("User {} has been notified to refresh permission cache", subjectUserName);
          refreshCache = true;
          usersToNotify.remove(subjectUserName);
          log.info(
              "After removing {}, Users to notify now contains users: {}",
              subjectUserName,
              join(usersToNotify, ","));
        }
      }
      // this doesn't need to be synchronized.
      if (refreshCache) {
        refreshCache();
      }
    }
    return refreshCache;
  }

  protected String getSubjectUserName() {
    return (String) SecurityUtils.getSubject().getPrincipal();
  }

  /**
   * Finds a permission from a set matching search criteria.
   *
   * @param permissions
   * @param domain
   * @param idConstraint
   * @return The first permission matching the criteria, or null if not found.
   */
  public ConstraintBasedPermission findBy(
      Set<Permission> permissions, PermissionDomain domain, IdConstraint idConstraint) {
    return findBy(permissions, domain, idConstraint, new ArrayList<>());
  }

  @Override
  public ConstraintBasedPermission findBy(
      Set<Permission> permissions,
      PermissionDomain domain,
      IdConstraint idConstraint,
      List<PermissionType> orderedActions) {
    List<ConstraintBasedPermission> candidates = new ArrayList<>();
    for (Permission p : permissions) {
      ConstraintBasedPermission cbp = (ConstraintBasedPermission) p;
      if (cbp.getDomain().equals(domain)
          && cbp.getIdConstraint() != null
          && cbp.getIdConstraint().isEquivalentTo(idConstraint)) {
        if (orderedActions.isEmpty()) {
          return cbp;
        } else {
          candidates.add(cbp);
        }
      }
    }
    if (!candidates.isEmpty()) {
      for (PermissionType pt : orderedActions) {
        for (ConstraintBasedPermission cbp : candidates) {
          if (cbp.getActions().contains(pt)) {
            return cbp;
          }
        }
      }
    }
    return null;
  }

  public PermissionType createFromString(String type) {
    if (type.equalsIgnoreCase("read") || type.equalsIgnoreCase("view")) {
      return PermissionType.READ;
    } else if (type.equalsIgnoreCase("write") || type.equalsIgnoreCase("edit")) {
      return PermissionType.WRITE;
    }
    throw new IllegalArgumentException("unknown permission type: {}" + type);
  }

  @Override
  public <T extends PermissionsAdaptable> ISearchResults<T> filter(
      ISearchResults<T> toFilter, PermissionType permissionType, User authUser) {
    if (toFilter == null || toFilter.getResults().isEmpty()) {
      return toFilter;
    }
    filter(toFilter.getResults(), permissionType, authUser);
    return toFilter;
  }

  @Override
  public <U extends Collection<T>, T extends PermissionsAdaptable> U filter(
      U toFilter, PermissionType permissionType, User authUser) {
    if (toFilter == null || toFilter.isEmpty()) {
      return toFilter;
    }
    Iterator<T> it = toFilter.iterator();
    log.info("filtering {} items", toFilter.size());
    while (it.hasNext()) {
      T next = it.next();
      if (next == null) {
        log.warn("Null item in toFilter list!");
        continue;
      }
      AbstractEntityPermissionAdapter adapter = next.getPermissionsAdapter();
      adapter.setAction(permissionType);
      if (!checkPermissions(adapter, authUser)) {
        it.remove();
      }
    }
    log.info("finished filtering {} items", toFilter.size());
    return toFilter;
  }

  @Override
  public <T extends PermissionsAdaptable> boolean isPermitted(
      T toTest, PermissionType permissionType, User user) {
    if (toTest == null) {
      return false;
    } // There is code that uses 'new PermissionUtils()' so we need to check that autowired beans
    // are not null.
    // ANY user is allowed to READ a published document
    if (toTest instanceof BaseRecord && permissionType == PermissionType.READ) {
      BaseRecord baseRecord = ((BaseRecord) toTest);
      if (hasPublicLink(baseRecord)) {
        return true;
      } // entries in published folders don't have public links (unless published independently), so
      // check the parent (if there is one) for a public link. Notebooks can only contain documents
      // (not folders or other notebooks)
      // so we needn't iterate any further than checking the parent to see if an entry in a folder
      // ought to be READ permitted.
      else {
        if (baseRecord.getOwnerParent().map(f -> hasPublicLink(f)).orElse(false)) {
          return true;
        }
      }
    }
    AbstractEntityPermissionAdapter adapter = toTest.getPermissionsAdapter();
    if (adapter == null) {
      throw new IllegalArgumentException(
          toTest.getClass() + " can't be adapted to the Permissions interface");
    }
    adapter.setAction(permissionType);
    return checkPermissions(adapter, user);
  }

  private boolean hasPublicLink(BaseRecord record) {
    String acl = record.getSharingACL().getAcl();
    return !StringUtils.isEmpty(acl) && acl.contains(RecordGroupSharing.ANONYMOUS_USER);
  }

  /*
   * Package scoped for overriding in tests
   */
  protected boolean checkPermissions(AbstractEntityPermissionAdapter adapter, User user) {
    boolean permissionResult = SecurityUtils.getSubject().isPermitted(adapter);
    if (!permissionResult) {
      log.trace("Checking ACL, user permissions didn't match");
      permissionResult = adapter.checkACL(user);
    }
    return permissionResult;
  }

  @Override
  public boolean isPermitted(String permission) {
    if (StringUtils.isEmpty(permission)) {
      throw new IllegalArgumentException(
          "permission is null, should be a valid permission String!");
    }
    ConstraintBasedPermission cbp = parser.resolvePermission(permission);
    return SecurityUtils.getSubject().isPermitted(cbp);
  }

  @Override
  public boolean isUserInRole(User user, Role... roles) {
    for (Role role : roles) {
      if (SecurityUtils.getSubject().hasRole(role.getName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isPermittedViaMediaLinksToRecords(
      IFieldLinkableElement fieldLinkableElement, PermissionType permType, User authUser) {
    // else we need to see if this object can be authorised by its containing record.
    if (fieldLinkableElement instanceof NfsElement) {
      return true; // 1.48 ignore Nfs permission concepts for now - there is no NfsEntity anyway
    }
    Set<BaseRecord> br = recordAdapter.getAsBaseRecord(fieldLinkableElement, true);
    Set<BaseRecord> containingRecords = new HashSet<>(br);
    if (fieldLinkableElement instanceof BaseRecord) {
      containingRecords.addAll(recordAdapter.getAsBaseRecord(fieldLinkableElement, false));
    }
    return containingRecords.stream()
        .anyMatch(baseRcd -> baseRcd != null && isPermitted(baseRcd, permType, authUser));
  }

  /** checks permission and throws AuthorizationException if failed */
  @Override
  public void assertIsPermitted(
      PermissionsAdaptable toTest, PermissionType permType, User subject, String actionMsg) {
    if (!isPermitted(toTest, permType, subject)) {
      throw new AuthorizationException(
          String.format(
              "Unauthorised attempt by user %s to %s [%s]",
              subject.getUsername(), actionMsg, toTest.getPermissionsAdapter().getId()));
    }
  }

  public void assertIsPermitted(String permission, String actionMsg) {
    if (!isPermitted(permission)) {
      throw new AuthorizationException(
          String.format("Unauthorised permission [%s] to %s", permission, actionMsg));
    }
  }

  @Override
  public void doRunAs(HttpSession session, User adminUser, User targetUser) {
    Subject subject = SecurityUtils.getSubject();
    String realmName = ShiroRealm.DEFAULT_USER_PASSWD_REALM;
    // RSPAC-1498
    Set<String> currRealmNames = subject.getPrincipals().getRealmNames();
    if (currRealmNames.isEmpty()) {
      log.warn("There are no Shiro realms set for current subject {}", subject.getPrincipal());
    } else {
      realmName = currRealmNames.iterator().next();
    }
    SimplePrincipalCollection pc = new SimplePrincipalCollection();
    pc.add(targetUser.getUsername(), realmName);
    subject.runAs(pc);
    SECURITY_LOG.info(
        "Admin [{}] running as user [{}] using security realms [{}]",
        adminUser.getUsername(),
        targetUser.getUsername(),
        realmName);
    session.setAttribute(SessionAttributeUtils.IS_RUN_AS, Boolean.TRUE);
  }

  @Override
  public boolean isRecordAccessPermitted(
      User user, BaseRecord objectToCheck, PermissionType permType) {
    return (isPermitted(objectToCheck, permType, user)
        || isPermittedViaMediaLinksToRecords(objectToCheck, permType, user));
  }

  @Override
  public void assertRecordAccessPermitted(
      BaseRecord toTest, PermissionType permType, User subject, String actionMsg) {
    if (!isRecordAccessPermitted(subject, toTest, permType)) {
      throw new AuthorizationException(
          String.format(
              "Unauthorised attempt by user %s to %s [%s]",
              subject.getUsername(), actionMsg, toTest.getPermissionsAdapter().getId()));
    }
  }
}
