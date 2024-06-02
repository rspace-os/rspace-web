package com.researchspace.dao.hibernate;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.SearchUtils;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
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

  public InventoryDaoHibernate(Class<T> persistentClass) {
    super(persistentClass);
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
                + "sharingMode='0' and "
                + relatedItemPrefix
                + "owner.username in (:userGroupMembers)) ";
      }
      for (int i = 0; i < userGroupsUniqueNames.size(); i++) {
        ownedAndPermittedItemsFragment +=
            "or ("
                + relatedItemPrefix
                + "sharingMode='1' and "
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
