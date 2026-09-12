package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.SearchUtils;
import com.blazebit.persistence.CommonQueryBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryDaoHibernate<T extends InventoryRecord, PK extends Serializable>
    extends GenericDaoHibernate<T, PK> {

  @Autowired protected InventoryPermissionUtils invPermissionUtils;

  @Autowired private CriteriaBuilderFactory criteriaBuilderFactory;

  public InventoryDaoHibernate(Class<T> persistentClass) {
    super(persistentClass);
  }

  protected InventoryReadQueryContext readQueryContext(User user) {
    return new InventoryReadQueryContext(
        user,
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(user),
        user.getGroups().stream().map(group -> group.getUniqueName()).toList(),
        invPermissionUtils.getOwnersVisibleWithUserRole(user));
  }

  protected CriteriaBuilderFactory criteriaBuilderFactory() {
    return criteriaBuilderFactory;
  }

  protected record InventoryReadQueryContext(
      User user, List<String> groupMembers, List<String> groupNames, List<String> visibleOwners) {

    String permissionPredicate(InventoryDaoHibernate<?, ?> dao, String prefix) {
      return dao.getInventoryReadPermissionSqlPredicate(
          user, groupMembers, groupNames, visibleOwners, prefix);
    }

    String readableContainerPredicate(InventoryDaoHibernate<?, ?> dao, String alias) {
      return dao.readableContainerPredicate(user, groupMembers, groupNames, visibleOwners, alias);
    }

    String ownedByAndPermitted(InventoryDaoHibernate<?, ?> dao, String ownedBy) {
      return dao.getOwnedByAndPermittedItemsSqlQueryFragment(
          ownedBy, user, groupMembers, groupNames, visibleOwners);
    }

    <Q> Query<Q> bind(Query<Q> query, String ownedBy) {
      return addQueryParams(ownedBy, user, query, visibleOwners, groupMembers, groupNames);
    }

    void bind(CommonQueryBuilder<?> query, String ownedBy) {
      if (StringUtils.isNotEmpty(ownedBy)) {
        query.setParameter("ownedBy", ownedBy);
      }
      if (!user.hasSysadminRole()) {
        query.setParameter("currentUser", user.getUsername());
        if (CollectionUtils.isNotEmpty(visibleOwners)) {
          query.setParameter("visibleOwners", visibleOwners);
        }
        if (CollectionUtils.isNotEmpty(groupMembers)) {
          query.setParameter("userGroupMembers", groupMembers);
        }
        for (int i = 0; i < groupNames.size(); i++) {
          query.setParameter("userGroupUniqueName" + i, "%" + groupNames.get(i) + "%");
        }
      }
    }
  }

  /**
   * Returns one REST API v2 collection page of the records this user may read.
   *
   * <p>The database applies the caller's filter, the sort, the permission rules, and the page
   * together, so the page and the total agree and no caller reads the collection to filter it.
   */
  protected ResourcePage<T> readableResourcePage(
      CollectionQueryExecutor<T> collectionQuery, ResourceRequest request, AccessResult access) {
    if (access.isDenied()) {
      return new ResourcePage<>(List.of(), 0);
    }
    return collectionQuery.page(
        criteriaBuilderFactory,
        getSession(),
        request,
        access.constraintOrEmpty().map(collectionQuery::compileConstraint).orElse(null));
  }

  /** Counts the records this user may read that match a REST API v2 collection request. */
  protected long countReadableResources(
      CollectionQueryExecutor<T> collectionQuery, ResourceRequest request, AccessResult access) {
    if (access.isDenied()) {
      return 0;
    }
    return collectionQuery.count(
        criteriaBuilderFactory,
        getSession(),
        request,
        access.constraintOrEmpty().map(collectionQuery::compileConstraint).orElse(null));
  }

  protected String getOwnedByAndPermittedItemsSqlQueryFragment(
      String ownedBy,
      User user,
      List<String> userGroupMembers,
      List<String> userGroupsUniqueNames,
      List<String> visibleOwners) {
    return getOwnedByAndPermittedItemsSqlQueryFragment(
        ownedBy, user, userGroupMembers, userGroupsUniqueNames, visibleOwners, "");
  }

  protected String getOwnedByAndPermittedItemsSqlQueryFragment(
      String ownedBy,
      User user,
      List<String> userGroupMembers,
      List<String> userGroupsUniqueNames,
      List<String> visibleOwners,
      String relatedItemPrefix) {

    String ownedAndPermittedItemsFragment =
        StringUtils.isEmpty(ownedBy) ? "" : "and " + relatedItemPrefix + "owner.username=:ownedBy ";
    if (!user.hasSysadminRole()) {
      ownedAndPermittedItemsFragment +=
          "and "
              + getInventoryReadPermissionSqlPredicate(
                  user, userGroupMembers, userGroupsUniqueNames, visibleOwners, relatedItemPrefix)
              + " ";
    }
    return ownedAndPermittedItemsFragment;
  }

  /** The inventory read rule without a leading {@code and}, for use inside larger predicates. */
  protected String getInventoryReadPermissionSqlPredicate(
      User user,
      List<String> userGroupMembers,
      List<String> userGroupsUniqueNames,
      List<String> visibleOwners,
      String relatedItemPrefix) {
    if (user.hasSysadminRole()) {
      return "1=1";
    }
    StringBuilder predicate =
        new StringBuilder("(").append(relatedItemPrefix).append("owner.username=:currentUser ");
    if (CollectionUtils.isNotEmpty(visibleOwners)) {
      predicate
          .append(" or (")
          .append(relatedItemPrefix)
          .append("owner.username in (:visibleOwners)) ");
    }
    if (CollectionUtils.isNotEmpty(userGroupMembers)) {
      predicate
          .append(" or (")
          .append(relatedItemPrefix)
          .append(
              "sharingMode=com.researchspace.model.inventory.InventoryRecord$InventorySharingMode.OWNER_GROUPS")
          .append(" and ")
          .append(relatedItemPrefix)
          .append("owner.username in (:userGroupMembers)) ");
    }
    for (int i = 0; i < userGroupsUniqueNames.size(); i++) {
      predicate
          .append("or (")
          .append(relatedItemPrefix)
          .append(
              "sharingMode=com.researchspace.model.inventory.InventoryRecord$InventorySharingMode.WHITELIST")
          .append(" and ")
          .append(relatedItemPrefix)
          .append("sharingACL.acl LIKE :userGroupUniqueName")
          .append(i)
          .append(") ");
    }
    return predicate.append(")").toString();
  }

  /** Permission predicate for a container and any readable descendant. */
  protected String readableContainerPredicate(
      User caller,
      List<String> groupMembers,
      List<String> groupNames,
      List<String> visibleOwners,
      String alias) {
    String direct =
        getInventoryReadPermissionSqlPredicate(
            caller, groupMembers, groupNames, visibleOwners, alias + ".");
    String childContainer =
        getInventoryReadPermissionSqlPredicate(
            caller, groupMembers, groupNames, List.of(), "childContainer.");
    String childInstrument =
        getInventoryReadPermissionSqlPredicate(
            caller, groupMembers, groupNames, List.of(), "childInstrument.");
    String childSubSample =
        getInventoryReadPermissionSqlPredicate(
            caller, groupMembers, groupNames, List.of(), "childSubSample.sample.");
    return "("
        + direct
        + " or exists (select childLocation.id from ContainerLocation childLocation "
        + "join childLocation.storedContainer childContainer where childLocation.container="
        + alias
        + " and childContainer.deleted=false and "
        + childContainer
        + ") or exists (select childLocation.id from ContainerLocation childLocation "
        + "join childLocation.storedInstrument childInstrument where childLocation.container="
        + alias
        + " and childInstrument.deleted=false and "
        + childInstrument
        + ") or exists (select childLocation.id from ContainerLocation childLocation "
        + "join childLocation.storedSubSample childSubSample where childLocation.container="
        + alias
        + " and childSubSample.deleted=false and "
        + childSubSample
        + "))";
  }

  protected String getOrderBySqlFragmentForInventoryRecord(
      PaginationCriteria<? extends InventoryRecord> pgCrit) {
    String orderByColumn;
    if (SearchUtils.ORDER_BY_GLOBAL_ID.equals(pgCrit.getOrderBy())) {
      /* querying a single type table here, so 'global id' ordering is same as 'id' ordering */
      orderByColumn = "id";
    } else if (SearchUtils.ORDER_BY_CREATION_DATE.equals(pgCrit.getOrderBy())
        || SearchUtils.ORDER_BY_MODIFICATION_DATE.equals(pgCrit.getOrderBy())) {
      /* creation/modificationDates are editInfo fields */
      orderByColumn = "editInfo." + pgCrit.getOrderBy() + "Millis";
    } else {
      /* name/type/unknown order defaults to name ordering */
      orderByColumn = "editInfo." + SearchUtils.ORDER_BY_NAME;
    }
    return " order by " + orderByColumn + " " + pgCrit.getSortOrder();
  }

  protected String getDeletedSqlFragmentForInventoryRecord(
      InventorySearchDeletedOption deletedItemsOption) {
    if (deletedItemsOption == null
        || InventorySearchDeletedOption.EXCLUDE.equals(deletedItemsOption)) {
      return "deleted=false ";
    }
    if (InventorySearchDeletedOption.DELETED_ONLY.equals(deletedItemsOption)) {
      return "deleted=true ";
    }
    return ""; // INCLUDE option == no result filtering
  }

  protected String connectSqlConditionsWithAnd(String... condition) {
    return Stream.of(condition)
        .filter(s -> !StringUtils.isBlank(s))
        .collect(Collectors.joining(" and "));
  }

  protected static <T> Query<T> addQueryParams(
      String ownedBy,
      User user,
      Query<T> baseQuery,
      List<String> visibleOwners,
      List<String> userGroupMembers,
      List<String> userGroupsUniqueNames) {
    if (StringUtils.isNotEmpty(ownedBy)) {
      baseQuery.setParameter("ownedBy", ownedBy);
    }
    if (!user.hasSysadminRole()) {
      baseQuery.setParameter("currentUser", user.getUsername());
      if (CollectionUtils.isNotEmpty(visibleOwners)) {
        baseQuery.setParameterList("visibleOwners", visibleOwners);
      }
      if (CollectionUtils.isNotEmpty(userGroupMembers)) {
        baseQuery.setParameterList("userGroupMembers", userGroupMembers);
      }
      for (int i = 0; i < userGroupsUniqueNames.size(); i++) {
        baseQuery.setParameter("userGroupUniqueName" + i, "%" + userGroupsUniqueNames.get(i) + "%");
      }
    }
    return baseQuery;
  }
}
