package com.researchspace.dao.hibernate;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.AbstractFormDaoImpl;
import com.researchspace.dao.FormDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.GroupConstraint;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.PropertyConstraint;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.FormType;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.views.FormSearchCriteria;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.Permission;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository("formDao")
public class FormDaoHibernate extends AbstractFormDaoImpl<RSForm> implements FormDao {

  private static final EditInfo INFO = new EditInfo();
  private static final Collection<PermissionType> RW =
      Arrays.asList(PermissionType.READ, PermissionType.WRITE);

  public FormDaoHibernate() {
    super(RSForm.class);
  }

  public List<RSForm> getAllVisibleNormalForms() {
    return getAllVisibleFormOfType(FormType.NORMAL);
  }

  public List<RSForm> getAllVisibleFormsByType(FormType formType) {
    return getAllVisibleFormOfType(formType);
  }

  public boolean hasUserPublishedFormsUsedinOtherRecords(User user) {
    Session session = getSession();
    Object result =
        session
            .createQuery(
                "select count (sd.id) from StructuredDocument sd where sd.form.id in "
                    + "(select id from RSForm form where form.owner=:owner) and sd.owner!=:owner")
            .setParameter("owner", user)
            .uniqueResult();
    Long count = (Long) result;
    return count > 0;
  }

  private List<RSForm> getAllVisibleFormOfType(FormType formType) {
    Query<RSForm> q =
        getSession()
            .createQuery(
                "from RSForm where publishingState =:state and current=:curr and formType=:type ",
                RSForm.class);
    q.setParameter("state", FormState.PUBLISHED);
    q.setParameter("curr", true);
    q.setParameter("type", formType);
    return q.list();
  }

  @Override
  public ISearchResults<RSForm> getAllFormsByPermission(
      User user, FormSearchCriteria sc, PaginationCriteria<RSForm> pg) {
    return getForms(user, sc.getRequestedAction(), sc, pg);
  }

  /** Handles permissions at database level. */
  private ISearchResults<RSForm> getForms(
      User user,
      PermissionType requestedAction,
      FormSearchCriteria searchCriteria,
      PaginationCriteria<RSForm> pagCriteria) {

    long start = System.currentTimeMillis();

    Set<Permission> usersPermissions = user.getAllPermissions(false, true);
    for (Role r : user.getRoles()) {
      usersPermissions.addAll(r.getPermissions());
    }

    String searchTerm = null;
    if (searchCriteria != null && StringUtils.isNotEmpty(searchCriteria.getSearchTerm())) {
      searchTerm = '%' + searchCriteria.getSearchTerm() + '%';
    }

    boolean actionMatch = false;
    List<String> clausesToOr = new ArrayList<>();
    for (Permission permission : usersPermissions) {
      ConstraintBasedPermission constraintBasedPermission = (ConstraintBasedPermission) permission;
      if (!constraintBasedPermission.isEnabled()) {
        continue;
      }

      List<String> andClauses = new ArrayList<>();
      andClauses.add(" form.current=true ");

      if (searchCriteria != null) {
        if (searchCriteria.isPublishedOnly()) {
          andClauses.add(" form.publishingState = 'PUBLISHED' ");
        }
        if (searchCriteria.isInUserMenu()) {
          andClauses.add(" menu.user_id=" + user.getId());
        }
        if (searchCriteria.isUserFormsOnly()) {
          andClauses.add(" owner.username = '" + user.getUsername() + "' ");
        }

        if (searchCriteria.getFormType() != null) {
          andClauses.add(" form.formType='" + searchCriteria.getFormType() + "' ");
        }
      }

      andClauses.add(createSearchClause(searchCriteria, searchTerm));

      if (searchCriteria != null) {
        // exclude system forms from results if need be, otherwise ignore
        if (!searchCriteria.isIncludeSystemForm()) {
          andClauses.add(" form.systemForm=false ");
        }
      }

      if (constraintBasedPermission.getActions().contains(requestedAction)
          && constraintBasedPermission.getDomain().equals(PermissionDomain.FORM)) {
        actionMatch = true;
        if (constraintBasedPermission.getIdConstraint() != null) {
          String idConstraints = join(constraintBasedPermission.getIdConstraint().getId(), false);
          String sqlIdSub = " form.id in (" + idConstraints + ")";
          andClauses.add(sqlIdSub);
        }
        for (String propName : constraintBasedPermission.getPropertyConstraints().keySet()) {
          PropertyConstraint pc = constraintBasedPermission.getPropertyConstraints().get(propName);
          if (pc.getName().equals("global")) {
            if (requestedAction.equals(PermissionType.READ)) {
              String access1 = " form.worldPermissionType in (" + join(RW, true) + ")";
              andClauses.add(access1);
            } else if (requestedAction.equals(PermissionType.WRITE)) {
              String access1 = " form.worldPermissionType = " + requestedAction;
              andClauses.add(access1);
            }
          } else if (pc.getName().equals("group")) {
            if (requestedAction.equals(PermissionType.READ)) {
              String access1 = " form.groupPermissionType in (" + join(RW, true) + ")";
              andClauses.add(access1);
            } else if (requestedAction.equals(PermissionType.WRITE)) {
              String access1 = " form.groupPermissionType = " + requestedAction;
              andClauses.add(access1);
            }
          } else if (pc.getName().equals("owner")) {
            if (requestedAction.equals(PermissionType.READ)) {
              String access1 = " form.ownerPermissionType in (" + join(RW, true) + ")";
              andClauses.add(access1);
            } else if (requestedAction.equals(PermissionType.WRITE)) {
              String access1 = " form.ownerPermissionType = " + requestedAction;
              andClauses.add(access1);
            }

            String ownerRestriction = null;
            if (pc.getValue().equals("${self}")) {
              ownerRestriction = user.getUsername();
            } else if (!(pc.getValue().equals("*"))) {
              ownerRestriction = pc.getValue();
            }
            if (ownerRestriction != null) {
              andClauses.add(" owner.username= " + "'" + ownerRestriction + "'");
            }
          } else if (isEditInfoProperty(pc.getName())) {
            andClauses.add(" form." + pc.getName() + "=" + "'" + pc.getValue() + "'");
          }
        }
        if (constraintBasedPermission.getGroupConstraint() != null) {
          GroupConstraint gc = constraintBasedPermission.getGroupConstraint();
          String grpName = gc.getGroupName();
          andClauses.add("grp.uniqueName=" + "'" + grpName + "'");
        }
      } else { // end if perm match
        continue;
      }

      StringBuilder baseQuery = new StringBuilder(" select distinct form.id");
      // for compatibility with older MySQL
      if (isSortOrderSet(pagCriteria)) {
        baseQuery.append(", form.").append(pagCriteria.getOrderBy()).append(" ");
      }
      baseQuery.append(
          " from RSForm form left join User owner on form.owner_id=owner.id"
              + " left join UserGroup up on up.user_id=owner.id"
              + " left join rsGroup grp on grp.id=up.group_id");
      if (searchCriteria != null && searchCriteria.isInUserMenu()) {
        baseQuery.append(" inner join FormUserMenu menu on form.stableId=menu.formStableId");
      }
      if (!andClauses.isEmpty()) {
        baseQuery.append(" where ");
      }

      Iterator<String> it = andClauses.iterator();
      while (it.hasNext()) {
        String next = it.next();
        if (StringUtils.isBlank(next)) {
          continue;
        }
        baseQuery.append(next);
        if (it.hasNext()) {
          baseQuery.append(" and ");
        }
      }
      // remove any trailing 'and'
      if (baseQuery.lastIndexOf(" and ") == baseQuery.length() - 5) {
        baseQuery.replace(baseQuery.length() - 5, baseQuery.length(), "");
      }
      clausesToOr.add(baseQuery.toString());
    } // end permission loop

    if (!actionMatch) {
      return new SearchResultsImpl<>(Collections.emptyList(), 0, 0L);
    }

    Session session = sessionFactory.getCurrentSession();
    String allQuery;

    StringBuilder sb = new StringBuilder();
    Iterator<String> unions = clausesToOr.iterator();
    while (unions.hasNext()) {
      sb.append(unions.next());
      if (unions.hasNext()) {
        sb.append(" union ");
      }
    }
    if (pagCriteria != null && pagCriteria.getOrderBy() != null) {
      sb.append(" order by ").append(pagCriteria.getOrderBy()).append(" ");
      if (pagCriteria.getSortOrder() != null) {
        sb.append(pagCriteria.getSortOrder());
      }
    }
    allQuery = sb.toString();
    log.debug(" all clause is [{}]", allQuery);

    String sqlCount = "select count(x.id) from (" + allQuery + ") as x";
    NativeQuery<?> countQuery = session.createNativeQuery(sqlCount);
    if (searchTerm != null) {
      countQuery.setParameter("searchTerm", searchTerm);
    }
    BigInteger count = (BigInteger) countQuery.uniqueResult();

    NativeQuery query = session.createNativeQuery(allQuery);
    if (searchTerm != null) {
      query.setParameter("searchTerm", searchTerm);
    }
    if (pagCriteria != null) {
      query.setFirstResult(pagCriteria.getFirstResultIndex());
      query.setMaxResults(pagCriteria.getResultsPerPage());
    }

    long end = System.currentTimeMillis();
    log.info("time taken for form permission queries is: {}", (end - start));

    List<RSForm> forms = new ArrayList<>();
    if (!isSortOrderSet(pagCriteria)) {
      // there is only a single result column
      List<BigInteger> ids = query.list(); // preserve order
      for (BigInteger id : ids) { // for debug purpose
        RSForm fmx = get(id.longValue());
        forms.add(fmx);
      }
    } else {
      // there are two result columns
      List<Object> results = query.list();
      for (Object row : results) {
        Object[] rowData = (Object[]) row;
        RSForm fmx = get(((BigInteger) rowData[0]).longValue());
        forms.add(fmx);
      }
    }

    return new SearchResultsImpl<>(forms, pagCriteria, count.longValue());
  }

  private static final String NAME_TAG_WC_SEARCH =
      " form.name like :searchTerm or form.tmpTag like :searchTerm";

  // RSPAC-1749
  private String createSearchClause(FormSearchCriteria searchCriteria, String searchTerm) {
    StringBuilder sb = new StringBuilder();
    if (searchTerm != null) {
      sb.append("(").append(NAME_TAG_WC_SEARCH);
      if (!searchCriteria.isUserFormsOnly()) {
        // case 3
        sb.append(" or owner.username like :searchTerm");
      }
      // else case 1
      sb.append(")");
    }

    return sb.toString();
  }

  private boolean isSortOrderSet(PaginationCriteria<RSForm> pagCriteria) {
    return pagCriteria != null
        && pagCriteria.getOrderBy() != null
        && !(pagCriteria.getOrderBy().equals("id"));
  }

  private String join(Collection<?> ids, boolean quote) {
    Collection<?> collectionToJoin;
    if (quote) {
      Set<String> quoted = new LinkedHashSet<>();
      for (Object id : ids) {
        quoted.add("'" + id + "'");
      }
      collectionToJoin = quoted;
    } else {
      collectionToJoin = ids;
    }
    return StringUtils.join(collectionToJoin, ",");
  }

  private boolean isEditInfoProperty(String name) {
    try {
      BeanUtils.getProperty(INFO, name);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      return false;
    }
    return true;
  }

  public List<RSForm> getAllCurrentNormalForms() {
    return getAllCurrentFormsByType(FormType.NORMAL);
  }

  public List<RSForm> getAllCurrentFormsByType(FormType type) {
    Session session = getSessionFactory().getCurrentSession();
    return session
        .createQuery("from RSForm where current =:curr and formType=:type ", RSForm.class)
        .setParameter("curr", true)
        .setParameter("type", type)
        .list();
  }

  @Override
  public RSForm getOriginalFromTemporaryForm(Long tempFormId) {
    Query<RSForm> q =
        getSession()
            .createQuery("from RSForm where tempForm.id =:id ", RSForm.class)
            .setParameter("id", tempFormId);
    List<RSForm> rc = q.list();
    return rc.isEmpty() ? null : rc.get(0);
  }

  public RSForm getMostRecentVersionForForm(String stableid) {
    Query<RSForm> q =
        getSession()
            .createQuery(
                "from RSForm form where form.stableID=:id "
                    + "and form.publishingState='PUBLISHED' order by form.version desc",
                RSForm.class)
            .setParameter("id", stableid)
            .setMaxResults(1);
    return getFirstResultOrNull(q);
  }

  @Override
  public RSForm get(Long id) {
    return get(id, false);
  }

  @Override
  public RSForm get(Long id, boolean includeDeletedFields) {
    if (!includeDeletedFields) {
      getSession().enableFilter("notdeleted");
    }
    return super.get(id);
  }

  @Override
  public RSForm getBasicDocumentForm() {
    Optional<RSForm> rc = getCurrentSystemFormByName(RecordFactory.BASIC_DOCUMENT_FORM_NAME);
    return rc.orElse(null);
  }

  @Override
  public Optional<RSForm> getCurrentSystemFormByName(String formName) {
    Query<RSForm> q =
        getSession()
            .createQuery(
                "from RSForm r where r.editInfo.name=:name"
                    + " and r.systemForm=:systemForm and current = true",
                RSForm.class)
            .setParameter("name", formName)
            .setParameter("systemForm", true);
    RSForm rc = getFirstResultOrNull(q);
    return Optional.ofNullable(rc);
  }

  /** Gets the first, current form which is an exact match to the supplied name. */
  @Override
  public RSForm findOldestFormByName(String name) {
    Query<RSForm> q =
        getSession()
            .createQuery(
                "from RSForm r where r.editInfo.name=:name"
                    + " and r.current = true order by r.editInfo.creationDateMillis asc",
                RSForm.class);
    q.setParameter("name", name).setMaxResults(1);
    return getOldestRsForm(q, name);
  }

  /** Gets the first, current form which is an exact match to the supplied name. */
  @Override
  public RSForm findOldestFormByNameForCreator(String name, String username) {
    Query<RSForm> q =
        getSession()
            .createQuery(
                "from RSForm r where r.editInfo.name=:name and r.current = true and"
                    + " r.editInfo.createdBy=:username order by r.editInfo.creationDateMillis asc",
                RSForm.class); // todo: should this be the owner username instead?
    q.setParameter("name", name).setParameter("username", username).setMaxResults(1);
    return getOldestRsForm(q, name);
  }

  @Nullable
  private RSForm getOldestRsForm(Query<RSForm> q, String name) {
    List<RSForm> rc = q.list();
    if (rc.isEmpty()) {
      log.info("No matching current form of name {}", name);
      return null;
    }
    RSForm oldest = rc.get(0);
    log.info("Oldest form with name {} is {}", name, oldest.getId());
    return getMostRecentVersionForForm(oldest.getStableID());
  }

  @Override
  public boolean removeFieldsFromForm(Long formId) {
    Query<?> q = getSession().createQuery("delete from FieldForm where form_id=:formId");
    q.setParameter("formId", formId);
    q.executeUpdate();
    return true;
  }

  @Override
  public Long countDocsCreatedFromForm(RSForm form) {
    List<Long> formIds =
        getSession()
            .createQuery(" select id from RSForm where stableId=:stableId", Long.class)
            .setParameter("stableId", form.getStableID())
            .list();
    if (formIds.isEmpty()) {
      return 0L;
    } else {
      return getSession()
          .createQuery(
              "select count(*) from StructuredDocument where form.id in :formIds", Long.class)
          .setParameterList("formIds", formIds)
          .uniqueResult();
    }
  }

  @Override
  public void transferOwnershipOfForms(User toBeDeleted, User newOwner, List<Long> ids) {
    Query<?> query =
        getSession()
            .createQuery(
                "UPDATE RSForm SET owner=:newOwner, editInfo.createdBy=:createdByWithDeleted WHERE"
                    + " id IN :ids")
            .setParameter("newOwner", newOwner)
            .setParameter("ids", ids)
            .setParameter("createdByWithDeleted", toBeDeleted.getUsername() + "(Deleted)");
    query.executeUpdate();
  }

  @Override
  public List<RSForm> getFormsUsedByOtherUsers(User formOwner) {
    return getSession()
        .createQuery(
            "select distinct form from StructuredDocument sd where sd.form.id in "
                + "(select id from RSForm form where form.owner=:owner) and sd.owner!=:owner",
            RSForm.class)
        .setParameter("owner", formOwner)
        .list();
  }
}
