package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.SearchUtils;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.dao.query.RsqlCollectionQuery.Predicate;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Returns one REST API v2 collection page of the records this user may read.
   *
   * <p>The database applies the caller's filter, the sort, the permission rules, and the page
   * together, so the page and the total agree and no caller reads the collection to filter it.
   */
  protected ResourcePage<T> readableResourcePage(
      CollectionQueryExecutor<T> collectionQuery, ResourceRequest request, User user) {
    return collectionQuery.page(
        criteriaBuilderFactory,
        getSession(),
        request,
        inventoryReadRestriction(collectionQuery.alias(), user));
  }

  /** Counts the records this user may read that match a REST API v2 collection request. */
  protected long countReadableResources(
      CollectionQueryExecutor<T> collectionQuery, ResourceRequest request, User user) {
    return collectionQuery.count(
        criteriaBuilderFactory,
        getSession(),
        request,
        inventoryReadRestriction(collectionQuery.alias(), user));
  }

  /**
   * Builds the inventory read restriction for a Blaze-Persistence query.
   *
   * <p>This is the predicate form of {@link #getOwnedByAndPermittedItemsSqlQueryFragment}, which
   * the legacy searches build as HQL text. The two must stay in step, so the disjuncts appear in
   * the same order and rest on the same three inputs: the owners this role can see, the members of
   * this user's groups, and the unique name of each of those groups. Enum values are bound as
   * parameters rather than written as literals, because the Blaze expression parser does not accept
   * the HQL literal form.
   *
   * @return {@code null} for a system administrator, who reads every record
   */
  protected Predicate inventoryReadRestriction(String alias, User user) {
    if (user.hasSysadminRole()) {
      return null;
    }
    List<String> groupMembers =
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(user);
    List<String> groupNames = user.getGroups().stream().map(Group::getUniqueName).toList();
    List<String> visibleOwners = invPermissionUtils.getOwnersVisibleWithUserRole(user);

    Map<String, Object> parameters = new LinkedHashMap<>();
    List<String> disjuncts = new ArrayList<>();
    parameters.put("invPermUser", user.getUsername());
    disjuncts.add(alias + ".owner.username = :invPermUser");
    if (CollectionUtils.isNotEmpty(visibleOwners)) {
      parameters.put("invPermVisibleOwners", visibleOwners);
      disjuncts.add(alias + ".owner.username IN :invPermVisibleOwners");
    }
    if (CollectionUtils.isNotEmpty(groupMembers)) {
      parameters.put("invPermSharedWithGroups", InventorySharingMode.OWNER_GROUPS);
      parameters.put("invPermGroupMembers", groupMembers);
      disjuncts.add(
          "("
              + alias
              + ".sharingMode = :invPermSharedWithGroups AND "
              + alias
              + ".owner.username IN :invPermGroupMembers)");
    }
    for (int index = 0; index < groupNames.size(); index++) {
      parameters.put("invPermWhitelisted", InventorySharingMode.WHITELIST);
      parameters.put("invPermGroupAcl" + index, "%" + groupNames.get(index) + "%");
      disjuncts.add(
          "("
              + alias
              + ".sharingMode = :invPermWhitelisted AND "
              + alias
              + ".sharingACL.acl LIKE :invPermGroupAcl"
              + index
              + ")");
    }
    return new Predicate("(" + String.join(" OR ", disjuncts) + ")", parameters);
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
    if (user.hasSysadminRole()) {
      ownedAndPermittedItemsFragment += ""; // for sysadmin there is no permission-limiting query
    } else {
      ownedAndPermittedItemsFragment +=
          "and (" + relatedItemPrefix + "owner.username=:currentUser ";
      if (CollectionUtils.isNotEmpty(visibleOwners)) {
        ownedAndPermittedItemsFragment +=
            " or (" + relatedItemPrefix + "owner.username in (:visibleOwners)) ";
      }
      if (CollectionUtils.isNotEmpty(userGroupMembers)) {
        ownedAndPermittedItemsFragment +=
            " or ("
                + relatedItemPrefix
                + "sharingMode=com.researchspace.model.inventory.InventoryRecord$InventorySharingMode.OWNER_GROUPS"
                + " and "
                + relatedItemPrefix
                + "owner.username in (:userGroupMembers)) ";
      }
      for (int i = 0; i < userGroupsUniqueNames.size(); i++) {
        ownedAndPermittedItemsFragment +=
            "or ("
                + relatedItemPrefix
                + "sharingMode=com.researchspace.model.inventory.InventoryRecord$InventorySharingMode.WHITELIST"
                + " and "
                + relatedItemPrefix
                + "sharingACL.acl LIKE :userGroupUniqueName"
                + i
                + ") ";
      }
      ownedAndPermittedItemsFragment += ") ";
    }
    return ownedAndPermittedItemsFragment;
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
